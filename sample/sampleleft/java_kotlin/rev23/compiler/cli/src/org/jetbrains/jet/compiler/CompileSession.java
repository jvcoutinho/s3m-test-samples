/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.compiler;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.codegen.ClassBuilderFactories;
import org.jetbrains.jet.codegen.CompilationErrorHandler;
import org.jetbrains.jet.codegen.GenerationState;
import org.jetbrains.jet.codegen.JetTypeMapper;
import org.jetbrains.jet.lang.cfg.pseudocode.JetControlFlowDataTraceFactory;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ClassOrNamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.diagnostics.*;
import org.jetbrains.jet.lang.diagnostics.rendering.DefaultErrorMessages;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM;
import org.jetbrains.jet.lang.resolve.java.CompilerDependencies;
import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver;
import org.jetbrains.jet.utils.Progress;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

/**
 * The session which handles analyzing and compiling a single module.
 *
 * @author yole
 */
public class CompileSession {
    private static final SimpleDiagnosticFactory<PsiErrorElement> SYNTAX_ERROR_FACTORY = SimpleDiagnosticFactory.create(Severity.ERROR);

    private final JetCoreEnvironment environment;
    private final MessageCollector messageCollector;
    private boolean stubs = false;
    private final MessageRenderer messageRenderer;
    private final PrintStream errorStream;
    private final boolean verbose;
    private final CompilerDependencies compilerDependencies;
    private AnalyzeExhaust bindingContext;

    public CompileSession(JetCoreEnvironment environment, MessageRenderer messageRenderer, PrintStream errorStream, boolean verbose,
            @NotNull CompilerDependencies compilerDependencies) {
        this.environment = environment;
        this.messageRenderer = messageRenderer;
        this.errorStream = errorStream;
        this.verbose = verbose;
        this.compilerDependencies = compilerDependencies;
        this.messageCollector = new MessageCollector(this.messageRenderer);
    }

    @NotNull
    public AnalyzeExhaust getBindingContext() {
        return bindingContext;
    }

    public void setStubs(boolean stubs) {
        this.stubs = stubs;
    }

    public boolean analyze() {
        reportSyntaxErrors();
        analyzeAndReportSemanticErrors();

        messageCollector.printTo(errorStream);

        return !messageCollector.hasErrors();
    }

    /**
     * @see JetTypeMapper#getFQName(DeclarationDescriptor)
     * TODO possibly duplicates DescriptorUtils#getFQName(DeclarationDescriptor)
     */
    private static String fqName(ClassOrNamespaceDescriptor descriptor) {
        DeclarationDescriptor containingDeclaration = descriptor.getContainingDeclaration();
        if (containingDeclaration == null || containingDeclaration instanceof ModuleDescriptor || containingDeclaration.getName().equals(JavaDescriptorResolver.JAVA_ROOT)) {
            return descriptor.getName();
        }
        else {
            return fqName((ClassOrNamespaceDescriptor) containingDeclaration) + "." + descriptor.getName();
        }
    }

    private void analyzeAndReportSemanticErrors() {
        Predicate<PsiFile> filesToAnalyzeCompletely =
                stubs ? Predicates.<PsiFile>alwaysFalse() : Predicates.<PsiFile>alwaysTrue();
        bindingContext = AnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                environment.getProject(), environment.getSourceFiles(), filesToAnalyzeCompletely, JetControlFlowDataTraceFactory.EMPTY,
                compilerDependencies);

        for (Diagnostic diagnostic : bindingContext.getBindingContext().getDiagnostics()) {
            reportDiagnostic(messageCollector, diagnostic);
        }

        reportIncompleteHierarchies(messageCollector);
    }

    private void reportIncompleteHierarchies(MessageCollector collector) {
        Collection<ClassDescriptor> incompletes = bindingContext.getBindingContext().getKeys(BindingContext.INCOMPLETE_HIERARCHY);
        if (!incompletes.isEmpty()) {
            StringBuilder message = new StringBuilder("The following classes have incomplete hierarchies:\n");
            for (ClassDescriptor incomplete : incompletes) {
                message.append("    ").append(fqName(incomplete)).append("\n");
            }
            collector.report(Severity.ERROR, message.toString(), null, -1, -1);
        }
    }

    private void reportSyntaxErrors() {
        for (JetFile file : environment.getSourceFiles()) {
            file.accept(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitErrorElement(PsiErrorElement element) {
                    String description = element.getErrorDescription();
                    String message = StringUtil.isEmpty(description) ? "Syntax error" : description;
                    Diagnostic diagnostic = new SyntaxErrorDiagnostic(element, Severity.ERROR, message);
                    reportDiagnostic(messageCollector, diagnostic);
                }
            });
        }
    }

    private static void reportDiagnostic(MessageCollector collector, Diagnostic diagnostic) {
        DiagnosticUtils.LineAndColumn lineAndColumn = DiagnosticUtils.getLineAndColumn(diagnostic);
        VirtualFile virtualFile = diagnostic.getPsiFile().getVirtualFile();
        String path = virtualFile == null ? null : virtualFile.getPath();
        String render;
        if (diagnostic.getFactory() == SYNTAX_ERROR_FACTORY) {
            render = ((SyntaxErrorDiagnostic)diagnostic).message;
        }
        else {
            render = DefaultErrorMessages.RENDERER.render(diagnostic);
        }
        collector.report(diagnostic.getSeverity(), render, path, lineAndColumn.getLine(), lineAndColumn.getColumn());
    }

    @NotNull
    public GenerationState generate(boolean module) {
        Project project = environment.getProject();
        GenerationState generationState = new GenerationState(project, ClassBuilderFactories.binaries(stubs),
                verbose ? new BackendProgress() : Progress.DEAF, bindingContext, environment.getSourceFiles(), compilerDependencies.getCompilerSpecialMode());
        generationState.compileCorrectFiles(CompilationErrorHandler.THROW_EXCEPTION);

        List<CompilerPlugin> plugins = environment.getCompilerPlugins();
        if (!module) {
            if (plugins != null) {
                CompilerPluginContext context = new CompilerPluginContext(project, bindingContext.getBindingContext(), environment.getSourceFiles());
                for (CompilerPlugin plugin : plugins) {
                    plugin.processFiles(context);
                }
            }
        }
        return generationState;
    }

    private class BackendProgress implements Progress {
        @Override
        public void log(String message) {
            errorStream.println(messageRenderer.render(Severity.LOGGING, message, null, -1, -1));
        }
    }

    private static class SyntaxErrorDiagnostic extends SimpleDiagnostic<PsiErrorElement> {
        private String message;

        private SyntaxErrorDiagnostic(@NotNull PsiErrorElement psiElement, @NotNull Severity severity, String message) {
            super(psiElement, SYNTAX_ERROR_FACTORY, severity);
            this.message = message;
        }
    }
}
