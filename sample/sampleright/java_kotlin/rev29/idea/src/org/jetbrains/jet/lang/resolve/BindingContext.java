package org.jetbrains.jet.lang.resolve;

import com.intellij.psi.PsiElement;
import org.jetbrains.jet.lang.JetDiagnostic;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.constants.CompileTimeConstant;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.util.slicedmap.*;

import java.util.Collection;

/**
 * @author abreslav
 */
public interface BindingContext {
    WritableSlice<JetAnnotationEntry, AnnotationDescriptor> ANNOTATION = Slices.createSimpleSlice("ANNOTATION");
    WritableSlice<JetExpression, CompileTimeConstant<?>> COMPILE_TIME_VALUE = Slices.createSimpleSlice("COMPILE_TIME_VALUE");
    WritableSlice<JetTypeReference, JetType> TYPE = Slices.createSimpleSlice("TYPE");
    WritableSlice<JetExpression, JetType> EXPRESSION_TYPE = new BasicWritableSlice<JetExpression, JetType>("EXPRESSION_TYPE", RewritePolicy.DO_NOTHING);

    WritableSlice<JetReferenceExpression, DeclarationDescriptor> REFERENCE_TARGET = new BasicWritableSlice<JetReferenceExpression, DeclarationDescriptor>("REFERENCE_TARGET", RewritePolicy.DO_NOTHING);
    WritableSlice<JetReferenceExpression, Collection<? extends DeclarationDescriptor>> AMBIGUOUS_REFERENCE_TARGET = new BasicWritableSlice<JetReferenceExpression, Collection<? extends DeclarationDescriptor>>("AMBIGUOUS_REFERENCE_TARGET", RewritePolicy.DO_NOTHING);

    WritableSlice<JetExpression, FunctionDescriptor> LOOP_RANGE_ITERATOR = Slices.createSimpleSlice("LOOP_RANGE_ITERATOR");
    WritableSlice<JetExpression, CallableDescriptor> LOOP_RANGE_HAS_NEXT = Slices.createSimpleSlice("LOOP_RANGE_HAS_NEXT");
    WritableSlice<JetExpression, FunctionDescriptor> LOOP_RANGE_NEXT = Slices.createSimpleSlice("LOOP_RANGE_NEXT");

    WritableSlice<JetExpression, JetType> AUTOCAST = Slices.createSimpleSlice("AUTOCAST");
    WritableSlice<JetExpression, JetScope> RESOLUTION_SCOPE = Slices.createSimpleSlice("RESOLUTION_SCOPE");

    WritableSlice<JetExpression, Boolean> VARIABLE_REASSIGNMENT = Slices.createSimpleSetSlice("VARIABLE_REASSIGNMENT");
    WritableSlice<JetExpression, Boolean> PROCESSED = Slices.createSimpleSetSlice("PROCESSED");
    WritableSlice<JetElement, Boolean> STATEMENT = Slices.createRemovableSetSlice("STATEMENT");

    WritableSlice<PropertyDescriptor, Boolean> BACKING_FIELD_REQUIRED = new Slices.SetSlice<PropertyDescriptor>("BACKING_FIELD_REQUIRED", RewritePolicy.DO_NOTHING) {
        @Override
        public Boolean computeValue(SlicedMap map, PropertyDescriptor propertyDescriptor, Boolean backingFieldRequired, boolean valueNotFound) {
            backingFieldRequired = valueNotFound ? false : backingFieldRequired;
            assert backingFieldRequired != null;
            PsiElement declarationPsiElement = map.get(DESCRIPTOR_TO_DECLARATION, propertyDescriptor);
            if (declarationPsiElement instanceof JetParameter) {
                JetParameter jetParameter = (JetParameter) declarationPsiElement;
                return jetParameter.getValOrVarNode() != null ||
                       backingFieldRequired;
            }
            if (propertyDescriptor.getModifiers().isAbstract()) return false;
            PropertyGetterDescriptor getter = propertyDescriptor.getGetter();
            PropertySetterDescriptor setter = propertyDescriptor.getSetter();
            if (getter == null) {
                return true;
            }
            else if (propertyDescriptor.isVar() && setter == null) {
                return true;
            }
            else if (setter != null && !setter.hasBody() && !setter.getModifiers().isAbstract()) {
                return true;
            }
            else if (!getter.hasBody() && !getter.getModifiers().isAbstract()) {
                return true;
            }
            return backingFieldRequired;
        }
    };

    WritableSlice<JetFunctionLiteralExpression, Boolean> BLOCK = new Slices.SetSlice<JetFunctionLiteralExpression>("BLOCK", RewritePolicy.DO_NOTHING) {
        @Override
        public Boolean computeValue(SlicedMap map, JetFunctionLiteralExpression expression, Boolean isBlock, boolean valueNotFound) {
            isBlock = valueNotFound ? false : isBlock;
            assert isBlock != null;
            return isBlock && !expression.getFunctionLiteral().hasParameterSpecification();
        }
    };

    Slices.KeyNormalizer<DeclarationDescriptor> DECLARATION_DESCRIPTOR_NORMALIZER = new Slices.KeyNormalizer<DeclarationDescriptor>() {
        @Override
        public DeclarationDescriptor normalize(DeclarationDescriptor declarationDescriptor) {
            if (declarationDescriptor instanceof VariableAsFunctionDescriptor) {
                VariableAsFunctionDescriptor descriptor = (VariableAsFunctionDescriptor) declarationDescriptor;
                return descriptor.getVariableDescriptor().getOriginal();
            }
            return declarationDescriptor.getOriginal();
        }
    };
    ReadOnlySlice<DeclarationDescriptor, PsiElement> DESCRIPTOR_TO_DECLARATION = Slices.<DeclarationDescriptor, PsiElement>sliceBuilder("DECLARATION_TO_DESCRIPTOR").setKeyNormalizer(DECLARATION_DESCRIPTOR_NORMALIZER).build();

    WritableSlice<PsiElement, NamespaceDescriptor> NAMESPACE = Slices.<PsiElement, NamespaceDescriptor>sliceBuilder("DECLARATION").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<PsiElement, ClassDescriptor> CLASS = Slices.<PsiElement, ClassDescriptor>sliceBuilder("CLASS").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<JetTypeParameter, TypeParameterDescriptor> TYPE_PARAMETER = Slices.<JetTypeParameter, TypeParameterDescriptor>sliceBuilder("TYPE_PARAMETER").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<PsiElement, FunctionDescriptor> FUNCTION = Slices.<PsiElement, FunctionDescriptor>sliceBuilder("FUNCTION").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<PsiElement, ConstructorDescriptor> CONSTRUCTOR = Slices.<PsiElement, ConstructorDescriptor>sliceBuilder("CONSTRUCTOR").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<PsiElement, VariableDescriptor> VARIABLE = Slices.<PsiElement, VariableDescriptor>sliceBuilder("VARIABLE").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<JetParameter, VariableDescriptor> VALUE_PARAMETER = Slices.<JetParameter, VariableDescriptor>sliceBuilder("VALUE_PARAMETER").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<JetPropertyAccessor, PropertyAccessorDescriptor> PROPERTY_ACCESSOR = Slices.<JetPropertyAccessor, PropertyAccessorDescriptor>sliceBuilder("PROPERTY_ACCESSOR").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();

    // normalize value to getOriginal(value)
    WritableSlice<PsiElement, PropertyDescriptor> PRIMARY_CONSTRUCTOR_PARAMETER = Slices.<PsiElement, PropertyDescriptor>sliceBuilder("PRIMARY_CONSTRUCTOR_PARAMETER").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();
    WritableSlice<JetObjectDeclarationName, PropertyDescriptor> OBJECT_DECLARATION = Slices.<JetObjectDeclarationName, PropertyDescriptor>sliceBuilder("OBJECT_DECLARATION").setOpposite((WritableSlice) DESCRIPTOR_TO_DECLARATION).build();

    WritableSlice[] DECLARATIONS_TO_DESCRIPTORS = new WritableSlice[] {
        NAMESPACE, CLASS, TYPE_PARAMETER, FUNCTION, CONSTRUCTOR, VARIABLE, VALUE_PARAMETER, PRIMARY_CONSTRUCTOR_PARAMETER, OBJECT_DECLARATION
    };

    ReadOnlySlice<PsiElement, DeclarationDescriptor> DECLARATION_TO_DESCRIPTOR = Slices.<PsiElement, DeclarationDescriptor>sliceBuilder("DECLARATION_TO_DESCRIPTOR")
            .setFurtherLookupSlices(DECLARATIONS_TO_DESCRIPTORS).build();

    WritableSlice<JetReferenceExpression, PsiElement> LABEL_TARGET = Slices.<JetReferenceExpression, PsiElement>sliceBuilder("LABEL_TARGET").build();
    WritableSlice<JetParameter, PropertyDescriptor> VALUE_PARAMETER_AS_PROPERTY = Slices.<JetParameter, PropertyDescriptor>sliceBuilder("VALUE_PARAMETER_AS_PROPERTY").build();

    Collection<JetDiagnostic> getDiagnostics();

    <K, V> V get(ReadOnlySlice<K, V> slice, K key);
}
