package org.jetbrains.jet.lang.descriptors;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.TypeSubstitutor;
import org.jetbrains.jet.lang.types.Variance;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author abreslav
 */
public class FunctionDescriptorImpl extends DeclarationDescriptorImpl implements FunctionDescriptor {

    private List<TypeParameterDescriptor> typeParameters;
    private List<ValueParameterDescriptor> unsubstitutedValueParameters;
    private JetType unsubstitutedReturnType;
    private JetType receiverType;

    private MemberModifiers modifiers;
    private final Set<FunctionDescriptor> overriddenFunctions = Sets.newHashSet();
    private final FunctionDescriptor original;

    public FunctionDescriptorImpl(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull List<AnnotationDescriptor> annotations,
            @NotNull String name) {
        super(containingDeclaration, annotations, name);
        this.original = this;
    }

    public FunctionDescriptorImpl(
            @NotNull FunctionDescriptor original,
            @NotNull List<AnnotationDescriptor> annotations,
            @NotNull String name) {
        super(original.getContainingDeclaration(), annotations, name);
        this.original = original;
    }

    public FunctionDescriptorImpl initialize(
            @Nullable JetType receiverType,
            @NotNull List<TypeParameterDescriptor> typeParameters,
            @NotNull List<ValueParameterDescriptor> unsubstitutedValueParameters,
            @Nullable JetType unsubstitutedReturnType) {
        this.receiverType = receiverType;
        this.typeParameters = typeParameters;
        this.unsubstitutedValueParameters = unsubstitutedValueParameters;
        this.unsubstitutedReturnType = unsubstitutedReturnType;
        return this;
    }

    public void setReturnType(@NotNull JetType unsubstitutedReturnType) {
        this.unsubstitutedReturnType = unsubstitutedReturnType;
    }

    @Override
    public JetType getReceiverType() {
        return receiverType;
    }

    @NotNull
    @Override
    public Set<? extends FunctionDescriptor> getOverriddenFunctions() {
        return overriddenFunctions;
    }

    public void setModifiers(MemberModifiers modifiers) {
        this.modifiers = modifiers;
    }

    @Nullable
    @Override
    public MemberModifiers getModifiers() {
        return modifiers;
    }

    public void addOverriddenFunction(@NotNull FunctionDescriptor overriddenFunction) {
        overriddenFunctions.add(overriddenFunction);
    }

    @Override
    @NotNull
    public List<TypeParameterDescriptor> getTypeParameters() {
        return typeParameters;
    }

    @Override
    @NotNull
    public List<ValueParameterDescriptor> getValueParameters() {
        return unsubstitutedValueParameters;
    }

    @Override
    @NotNull
    public JetType getReturnType() {
        return unsubstitutedReturnType;
    }

    @NotNull
    @Override
    public FunctionDescriptor getOriginal() {
        return original == this ? this : original.getOriginal();
    }

    @Override
    public final FunctionDescriptor substitute(TypeSubstitutor substitutor) {
        if (substitutor.isEmpty()) {
            return this;
        }
        FunctionDescriptorImpl substitutedDescriptor;
        substitutedDescriptor = createSubstitutedCopy();

        JetType receiverType = getReceiverType();
        JetType substitutedReceiverType = null;
        if (receiverType != null) {
            substitutedReceiverType = substitutor.substitute(receiverType, Variance.IN_VARIANCE);
            if (substitutedReceiverType == null) {
                return null;
            }
        }

        List<ValueParameterDescriptor> substitutedValueParameters = FunctionDescriptorUtil.getSubstitutedValueParameters(substitutedDescriptor, this, substitutor);
        if (substitutedValueParameters == null) {
            return null;
        }

        JetType substitutedReturnType = FunctionDescriptorUtil.getSubstitutedReturnType(this, substitutor);
        if (substitutedReturnType == null) {
            return null;
        }

        substitutedDescriptor.initialize(
                substitutedReceiverType,
                Collections.<TypeParameterDescriptor>emptyList(), // TODO : questionable
                substitutedValueParameters,
                substitutedReturnType
        );
        return substitutedDescriptor;
    }

    protected FunctionDescriptorImpl createSubstitutedCopy() {
        return new FunctionDescriptorImpl(
                this,
                // TODO : safeSubstitute
                getAnnotations(),
                getName());
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitFunctionDescriptor(this, data);
    }

}
