package org.jetbrains.jet;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.ErrorHandler;
import org.jetbrains.jet.lang.ErrorHandlerWithRegions;
import org.jetbrains.jet.lang.JetDiagnostic;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingTrace;
import org.jetbrains.jet.lang.resolve.JetScope;
import org.jetbrains.jet.lang.resolve.constants.CompileTimeConstant;
import org.jetbrains.jet.lang.types.JetType;

import java.util.Collection;

/**
 * @author abreslav
 */
public class JetTestUtils {
    public static final BindingTrace DUMMY_TRACE = new BindingTrace() {

        @Override
        public void recordExpressionType(@NotNull JetExpression expression, @NotNull JetType type) {
        }

        @Override
        public void recordReferenceResolution(@NotNull JetReferenceExpression expression, @NotNull DeclarationDescriptor descriptor) {
        }

        @Override
        public void recordLabelResolution(@NotNull JetReferenceExpression expression, @NotNull PsiElement element) {
        }

        @Override
        public void recordDeclarationResolution(@NotNull PsiElement declaration, @NotNull DeclarationDescriptor descriptor) {
        }

        @Override
        public void recordValueParameterAsPropertyResolution(@NotNull JetParameter declaration, @NotNull PropertyDescriptor descriptor) {

        }

        @Override
        public void recordTypeResolution(@NotNull JetTypeReference typeReference, @NotNull JetType type) {
        }

        @Override
        public void recordAnnotationResolution(@NotNull JetAnnotationEntry annotationEntry, @NotNull AnnotationDescriptor annotationDescriptor) {

        }

        @Override
        public void recordCompileTimeValue(@NotNull JetExpression expression, @NotNull CompileTimeConstant<?> value) {

        }

        @Override
        public void recordBlock(JetFunctionLiteralExpression expression) {
        }

        @Override
        public void recordStatement(@NotNull JetElement statement) {
        }

        @Override
        public void recordVariableReassignment(@NotNull JetExpression expression) {

        }

        @Override
        public void recordResolutionScope(@NotNull JetExpression expression, @NotNull JetScope scope) {
        }

        @Override
        public void removeStatementRecord(@NotNull JetElement statement) {
        }

        @Override
        public void requireBackingField(@NotNull PropertyDescriptor propertyDescriptor) {
        }

        @Override
        public void recordAutoCast(@NotNull JetExpression expression, @NotNull JetType type, @NotNull VariableDescriptor variableDescriptor) {

        }

        @NotNull
        @Override
        public ErrorHandlerWithRegions getErrorHandler() {
            return new ErrorHandlerWithRegions(new ErrorHandler() {
                @Override
                public void unresolvedReference(@NotNull JetReferenceExpression referenceExpression) {
                    throw new IllegalStateException("Unresolved: " + referenceExpression.getText());
                }
            });
        }

        @Override
        public boolean isProcessed(@NotNull JetExpression expression) {
            return false;
        }

        @Override
        public void markAsProcessed(@NotNull JetExpression expression) {

        }

        @Override
        public BindingContext getBindingContext() {
            return new BindingContext() {

                @Override
                public DeclarationDescriptor getDeclarationDescriptor(PsiElement declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public NamespaceDescriptor getNamespaceDescriptor(JetNamespace declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public ClassDescriptor getClassDescriptor(JetClassOrObject declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public TypeParameterDescriptor getTypeParameterDescriptor(JetTypeParameter declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public FunctionDescriptor getFunctionDescriptor(JetNamedFunction declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public ConstructorDescriptor getConstructorDescriptor(JetElement declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public AnnotationDescriptor getAnnotationDescriptor(JetAnnotationEntry annotationEntry) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public CompileTimeConstant<?> getCompileTimeValue(JetExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public VariableDescriptor getVariableDescriptor(JetProperty declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public VariableDescriptor getVariableDescriptor(JetParameter declaration) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public PropertyDescriptor getPropertyDescriptor(JetParameter primaryConstructorParameter) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public PropertyDescriptor getPropertyDescriptor(JetObjectDeclarationName objectDeclarationName) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public JetType getExpressionType(JetExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public DeclarationDescriptor resolveReferenceExpression(JetReferenceExpression referenceExpression) {
                    return null;
                }

                @Override
                public JetType resolveTypeReference(JetTypeReference typeReference) {
                    return null;
                }

                @Override
                public PsiElement resolveToDeclarationPsiElement(JetReferenceExpression referenceExpression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public PsiElement getDeclarationPsiElement(DeclarationDescriptor descriptor) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public boolean isBlock(JetFunctionLiteralExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public boolean isStatement(JetExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public boolean hasBackingField(PropertyDescriptor propertyDescriptor) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public boolean isVariableReassignment(JetExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public ConstructorDescriptor resolveSuperConstructor(JetDelegatorToSuperCall superCall) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public JetType getAutoCastType(@NotNull JetExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public JetScope getResolutionScope(@NotNull JetExpression expression) {
                    throw new UnsupportedOperationException(); // TODO
                }

                @Override
                public Collection<JetDiagnostic> getDiagnostics() {
                    throw new UnsupportedOperationException(); // TODO
                }

            };
        }
    };
}
