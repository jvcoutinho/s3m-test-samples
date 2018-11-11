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

package org.jetbrains.jet.lang.resolve.java;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.di.InjectorForTopDownAnalyzerForJvm;
import org.jetbrains.jet.lang.cfg.pseudocode.JetControlFlowDataTraceFactory;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.diagnostics.DiagnosticUtils;
import org.jetbrains.jet.lang.diagnostics.Errors;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzingUtils;
import org.jetbrains.jet.lang.resolve.BindingTraceContext;
import org.jetbrains.jet.lang.resolve.ObservableBindingTrace;
import org.jetbrains.jet.lang.resolve.TopDownAnalysisParameters;
import org.jetbrains.jet.lang.types.lang.JetStandardLibrary;

import java.util.Collection;
import java.util.Collections;

/**
 * @author abreslav
 */
public class AnalyzerFacadeForJVM {

    private static final Logger LOG = Logger.getInstance("org.jetbrains.jet.lang.resolve.java.AnalyzerFacadeForJVM");

    public static final Function<JetFile, Collection<JetFile>> SINGLE_DECLARATION_PROVIDER = new Function<JetFile, Collection<JetFile>>() {
        @Override
        public Collection<JetFile> fun(JetFile file) {
            return Collections.singleton(file);
        }
    };

    private final static Key<CachedValue<AnalyzeExhaust>> BINDING_CONTEXT = Key.create("BINDING_CONTEXT");
    private static final Object lock = new Object();

    private AnalyzerFacadeForJVM() {
    }

    /**
     * Analyze project with string cache for given file. Given file will be fully analyzed.
     *
     * @param file
     * @param declarationProvider
     * @return
     */
    public static AnalyzeExhaust analyzeFileWithCache(@NotNull final JetFile file,
            @NotNull final Function<JetFile, Collection<JetFile>> declarationProvider,
            @NotNull final CompilerSpecialMode compilerSpecialMode, @NotNull final CompilerDependencies compilerDependencies) {
        // Need lock for getValue(), because parallel threads can start evaluation of compute() simultaneously
        synchronized (lock) {
            CachedValue<AnalyzeExhaust> bindingContextCachedValue = file.getUserData(BINDING_CONTEXT);
            if (bindingContextCachedValue == null) {
                bindingContextCachedValue = CachedValuesManager.getManager(file.getProject()).createCachedValue(new CachedValueProvider<AnalyzeExhaust>() {
                    @Override
                    public Result<AnalyzeExhaust> compute() {
                        try {
                            AnalyzeExhaust bindingContext;
                            bindingContext = analyzeFilesWithJavaIntegration(
                                    file.getProject(),
                                    declarationProvider.fun(file),
                                    Predicates.<PsiFile>equalTo(file),
                                    JetControlFlowDataTraceFactory.EMPTY,
                                    compilerDependencies);
                            return new Result<AnalyzeExhaust>(bindingContext, PsiModificationTracker.MODIFICATION_COUNT);
                        }
                        catch (ProcessCanceledException e) {
                            throw e;
                        }
                        catch (Throwable e) {
                            DiagnosticUtils.throwIfRunningOnServer(e);
                            LOG.error(e);
                            BindingTraceContext bindingTraceContext = new BindingTraceContext();
                            bindingTraceContext.report(Errors.EXCEPTION_WHILE_ANALYZING.on(file, e));
                            AnalyzeExhaust analyzeExhaust = new AnalyzeExhaust(bindingTraceContext.getBindingContext(), null);
                            return new Result<AnalyzeExhaust>(analyzeExhaust, PsiModificationTracker.MODIFICATION_COUNT);
                        }
                    }
                }, false);
                file.putUserData(BINDING_CONTEXT, bindingContextCachedValue);
            }
            return bindingContextCachedValue.getValue();
        }
    }

    /**
     * Analyze project with string cache for the whole project. All given files will be analyzed only for descriptors.
     */
    public static AnalyzeExhaust analyzeProjectWithCache(@NotNull final Project project,
            @NotNull final Collection<JetFile> files,
            @NotNull final CompilerSpecialMode compilerSpecialMode,
            @NotNull final CompilerDependencies compilerDependencies) {
        // Need lock for getValue(), because parallel threads can start evaluation of compute() simultaneously
        synchronized (lock) {
            CachedValue<AnalyzeExhaust> bindingContextCachedValue = project.getUserData(BINDING_CONTEXT);
            if (bindingContextCachedValue == null) {
                bindingContextCachedValue = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<AnalyzeExhaust>() {
                    @Override
                    public Result<AnalyzeExhaust> compute() {
                        try {
                            AnalyzeExhaust analyzeExhaust = analyzeFilesWithJavaIntegration(
                                    project,
                                    files,
                                    Predicates.<PsiFile>alwaysFalse(),
                                    JetControlFlowDataTraceFactory.EMPTY,
                                    compilerDependencies);
                            return new Result<AnalyzeExhaust>(analyzeExhaust, PsiModificationTracker.MODIFICATION_COUNT);
                        }
                        catch (ProcessCanceledException e) {
                            throw e;
                        }
                        catch (Throwable e) {
                            DiagnosticUtils.throwIfRunningOnServer(e);
                            LOG.error(e);
                            BindingTraceContext bindingTraceContext = new BindingTraceContext();
                            AnalyzeExhaust analyzeExhaust = new AnalyzeExhaust(bindingTraceContext.getBindingContext(), null);
                            return new Result<AnalyzeExhaust>(analyzeExhaust, PsiModificationTracker.MODIFICATION_COUNT);
                        }
                    }
                }, false);
                project.putUserData(BINDING_CONTEXT, bindingContextCachedValue);
            }
            return bindingContextCachedValue.getValue();
        }
    }

    public static AnalyzeExhaust analyzeOneFileWithJavaIntegrationAndCheckForErrors(
            JetFile file, JetControlFlowDataTraceFactory flowDataTraceFactory,
            @NotNull CompilerSpecialMode compilerSpecialMode, @NotNull CompilerDependencies compilerDependencies) {
        AnalyzingUtils.checkForSyntacticErrors(file);

        AnalyzeExhaust analyzeExhaust = analyzeOneFileWithJavaIntegration(file, flowDataTraceFactory, compilerDependencies);

        AnalyzingUtils.throwExceptionOnErrors(analyzeExhaust.getBindingContext());

        return analyzeExhaust;
    }

    public static AnalyzeExhaust analyzeOneFileWithJavaIntegration(
            JetFile file, JetControlFlowDataTraceFactory flowDataTraceFactory,
            @NotNull CompilerDependencies compilerDependencies) {
        return analyzeFilesWithJavaIntegration(file.getProject(), Collections.singleton(file),
                Predicates.<PsiFile>alwaysTrue(), flowDataTraceFactory, compilerDependencies);
    }

    public static AnalyzeExhaust analyzeFilesWithJavaIntegration(
            Project project, Collection<JetFile> files, Predicate<PsiFile> filesToAnalyzeCompletely,
            JetControlFlowDataTraceFactory flowDataTraceFactory,
            @NotNull CompilerDependencies compilerDependencies) {
        BindingTraceContext bindingTraceContext = new BindingTraceContext();

        final ModuleDescriptor owner = new ModuleDescriptor("<module>");

        TopDownAnalysisParameters topDownAnalysisParameters = new TopDownAnalysisParameters(
                filesToAnalyzeCompletely, false, false);


        InjectorForTopDownAnalyzerForJvm injector = new InjectorForTopDownAnalyzerForJvm(
                project, topDownAnalysisParameters,
                new ObservableBindingTrace(bindingTraceContext), owner, flowDataTraceFactory,
                compilerDependencies);


        injector.getTopDownAnalyzer().analyzeFiles(files);
        return new AnalyzeExhaust(bindingTraceContext.getBindingContext(), JetStandardLibrary.getInstance());
    }

    public static AnalyzeExhaust shallowAnalyzeFiles(Collection<JetFile> files,
            @NotNull CompilerSpecialMode compilerSpecialMode, @NotNull CompilerDependencies compilerDependencies) {
        assert files.size() > 0;

        Project project = files.iterator().next().getProject();

        return analyzeFilesWithJavaIntegration(project, files, Predicates.<PsiFile>alwaysFalse(),
                JetControlFlowDataTraceFactory.EMPTY, compilerDependencies);
    }
}
