package org.jetbrains.jet.codegen;

import com.intellij.psi.PsiElement;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author max
 * @author yole
 * @author alex.tkachman
 */
public class FunctionCodegen {
    private final CodegenContext owner;
    private final ClassBuilder v;
    private final GenerationState state;

    public FunctionCodegen(CodegenContext owner, ClassBuilder v, GenerationState state) {
        this.owner = owner;
        this.v = v;
        this.state = state;
    }

    public void gen(JetNamedFunction f) {
        final FunctionDescriptor functionDescriptor = state.getBindingContext().get(BindingContext.FUNCTION, f);
        assert functionDescriptor != null;
        Method method = state.getTypeMapper().mapToCallableMethod(functionDescriptor, false, owner.getContextKind()).getSignature();
        generateMethod(f, method, functionDescriptor);
    }

    public void generateMethod(JetDeclarationWithBody f, Method jvmMethod, FunctionDescriptor functionDescriptor) {
        CodegenContext.MethodContext funContext = owner.intoFunction(functionDescriptor);

        final JetExpression bodyExpression = f.getBodyExpression();
        generatedMethod(bodyExpression, jvmMethod, funContext, functionDescriptor, f);
    }

    private void generatedMethod(JetExpression bodyExpressions,
                                 Method jvmSignature,
                                 CodegenContext.MethodContext context,
                                 FunctionDescriptor functionDescriptor, JetDeclarationWithBody fun)
    {
        List<ValueParameterDescriptor> paramDescrs = functionDescriptor.getValueParameters();
        List<TypeParameterDescriptor> typeParameters = (functionDescriptor instanceof PropertyAccessorDescriptor ? ((PropertyAccessorDescriptor)functionDescriptor).getCorrespondingProperty(): functionDescriptor).getTypeParameters();

        int flags = ACC_PUBLIC; // TODO.

        OwnerKind kind = context.getContextKind();

        if (kind != OwnerKind.TRAIT_IMPL || bodyExpressions != null) {

            boolean isStatic = kind == OwnerKind.NAMESPACE || kind == OwnerKind.TRAIT_IMPL;
            if (isStatic) flags |= ACC_STATIC;

            boolean isAbstract = !isStatic && (bodyExpressions == null || CodegenUtil.isInterface(functionDescriptor.getContainingDeclaration()));
            if (isAbstract) flags |= ACC_ABSTRACT;

            final MethodVisitor mv = v.newMethod(fun, flags, jvmSignature.getName(), jvmSignature.getDescriptor(), null, null);
            if(kind != OwnerKind.TRAIT_IMPL && v.generateCode()) {
                int start = functionDescriptor.getReceiverParameter().exists() ? 1 : 0;
                for(int i = 0; i != paramDescrs.size(); ++i) {
                    AnnotationVisitor annotationVisitor = mv.visitParameterAnnotation(i + start, "Ljet/typeinfo/JetParameterName;", true);
                    annotationVisitor.visit("value", paramDescrs.get(i).getName());
                    annotationVisitor.visitEnd();
                }
            }
            if (!isAbstract && v.generateCode()) {
                mv.visitCode();
                
                Label methodBegin = new Label();
                mv.visitLabel(methodBegin);
                
                FrameMap frameMap = context.prepareFrame();

                ExpressionCodegen codegen = new ExpressionCodegen(mv, frameMap, jvmSignature.getReturnType(), context, state);

                Type[] argTypes = jvmSignature.getArgumentTypes();
                int add = functionDescriptor.getReceiverParameter().exists() ? state.getTypeMapper().mapType(functionDescriptor.getReceiverParameter().getType()).getSize() : 0;

                for (final TypeParameterDescriptor typeParameterDescriptor : typeParameters) {
                    int slot = frameMap.enterTemp();
                    add++;
                    codegen.addTypeParameter(typeParameterDescriptor, StackValue.local(slot, JetTypeMapper.TYPE_TYPEINFO));
                }

                for (int i = 0; i < paramDescrs.size(); i++) {
                    ValueParameterDescriptor parameter = paramDescrs.get(i);
                    frameMap.enter(parameter, argTypes[i+add].getSize());
                }

                if (kind instanceof OwnerKind.DelegateKind) {
                    OwnerKind.DelegateKind dk = (OwnerKind.DelegateKind) kind;
                    InstructionAdapter iv = new InstructionAdapter(mv);
                    iv.load(0, JetTypeMapper.TYPE_OBJECT);
                    dk.getDelegate().put(JetTypeMapper.TYPE_OBJECT, iv);
                    for (int i = 0; i < argTypes.length; i++) {
                        Type argType = argTypes[i];
                        iv.load(i + 1, argType);
                    }
                    iv.invokeinterface(dk.getOwnerClass(), jvmSignature.getName(), jvmSignature.getDescriptor());
                    iv.areturn(jvmSignature.getReturnType());
                }
                else {
                    for (ValueParameterDescriptor parameter : paramDescrs) {
                        Type sharedVarType = state.getTypeMapper().getSharedVarType(parameter);
                        Type localVarType = state.getTypeMapper().mapType(parameter.getOutType());
                        if (sharedVarType != null) {
                            int index = frameMap.getIndex(parameter);
                            mv.visitTypeInsn(NEW, sharedVarType.getInternalName());
                            mv.visitInsn(DUP);
                            mv.visitInsn(DUP);
                            mv.visitMethodInsn(INVOKESPECIAL, sharedVarType.getInternalName(), "<init>", "()V");
                            mv.visitVarInsn(localVarType.getOpcode(ILOAD), index);
                            mv.visitFieldInsn(PUTFIELD, sharedVarType.getInternalName(), "ref", StackValue.refType(localVarType).getDescriptor());
                            mv.visitVarInsn(sharedVarType.getOpcode(ISTORE), index);
                        }
                    }

                    codegen.returnExpression(bodyExpressions);
                }
                
                Label methodEnd = new Label();
                mv.visitLabel(methodEnd);

                // http://youtrack.jetbrains.net/issue/KT-746
                // Fill LocalVariableTable with method parameter names
                for (int i = 0; i < paramDescrs.size(); i++) {
                    ValueParameterDescriptor parameter = paramDescrs.get(i);
                    Type type = state.getTypeMapper().mapType(parameter.getOutType());
                    // TODO: specify signature
                    mv.visitLocalVariable(parameter.getName(), type.getDescriptor(), null, methodBegin, methodEnd, i + add);
                }

                mv.visitMaxs(0, 0);
                mv.visitEnd();

                generateBridgeIfNeeded(owner, state, v, jvmSignature, functionDescriptor, kind);
            }
        }

        generateDefaultIfNeeded(context, state, v, jvmSignature, functionDescriptor, kind);
    }

    static void generateBridgeIfNeeded(CodegenContext owner, GenerationState state, ClassBuilder v, Method jvmSignature, FunctionDescriptor functionDescriptor, OwnerKind kind) {
        Set<? extends FunctionDescriptor> overriddenFunctions = functionDescriptor.getOverriddenDescriptors();
        if(kind != OwnerKind.TRAIT_IMPL) {
            for (FunctionDescriptor overriddenFunction : overriddenFunctions) {
                // TODO should we check params here as well?
                checkOverride(owner, state, v, jvmSignature, functionDescriptor, overriddenFunction);
            }
            checkOverride(owner, state, v, jvmSignature, functionDescriptor, functionDescriptor.getOriginal());
        }
    }

    static void generateDefaultIfNeeded(CodegenContext.MethodContext owner, GenerationState state, ClassBuilder v, Method jvmSignature, FunctionDescriptor functionDescriptor, OwnerKind kind) {
        DeclarationDescriptor contextClass = owner.getContextDescriptor().getContainingDeclaration();

        if(kind != OwnerKind.TRAIT_IMPL) {
            // we don't generate defaults for traits but do for traitImpl
            if(contextClass instanceof ClassDescriptor) {
                PsiElement psiElement = state.getBindingContext().get(BindingContext.DESCRIPTOR_TO_DECLARATION, contextClass);
                if(psiElement instanceof JetClass) {
                    JetClass element = (JetClass) psiElement;
                    if(element.isTrait())
                        return;
                }
            }
        }

        boolean needed = false;
        for (ValueParameterDescriptor parameterDescriptor : functionDescriptor.getValueParameters()) {
            if(parameterDescriptor.hasDefaultValue()) {
                needed = true;
                break;
            }
        }

        if(needed) {
            boolean hasReceiver = functionDescriptor.getReceiverParameter().exists();
            boolean isStatic = kind == OwnerKind.NAMESPACE;

            if(kind == OwnerKind.TRAIT_IMPL) {
                String correctedDescr = "(" + jvmSignature.getDescriptor().substring(jvmSignature.getDescriptor().indexOf(";") + 1);
                jvmSignature = new Method(jvmSignature.getName(), correctedDescr);
            }

            int flags = ACC_PUBLIC; // TODO.

            String ownerInternalName = contextClass instanceof NamespaceDescriptor ?
                                       NamespaceCodegen.getJVMClassName(DescriptorUtils.getFQName(contextClass)) :
                                       state.getTypeMapper().mapType(((ClassDescriptor) contextClass).getDefaultType(), OwnerKind.IMPLEMENTATION).getInternalName();

            String descriptor = jvmSignature.getDescriptor().replace(")","I)");
            if(!isStatic)
                descriptor = descriptor.replace("(","(L" + ownerInternalName + ";");
            final MethodVisitor mv = v.newMethod(null, flags | ACC_STATIC, jvmSignature.getName() + "$default", descriptor, null, null);
            InstructionAdapter iv = new InstructionAdapter(mv);
            if (v.generateCode()) {
                mv.visitCode();

                FrameMap frameMap = owner.prepareFrame();

                ExpressionCodegen codegen = new ExpressionCodegen(mv, frameMap, jvmSignature.getReturnType(), owner, state);

                int var = 0;
                if(!isStatic) {
                    frameMap.enterTemp();
                    var++;
                }

                if(hasReceiver) {
                    frameMap.enterTemp();
                    var++;
                }

                List<TypeParameterDescriptor> typeParameters = functionDescriptor.getTypeParameters();
                for (final TypeParameterDescriptor typeParameterDescriptor : typeParameters) {
                    int slot = frameMap.enterTemp();
                    codegen.addTypeParameter(typeParameterDescriptor, StackValue.local(slot, JetTypeMapper.TYPE_TYPEINFO));
                    var++;
                }

                Type[] argTypes = jvmSignature.getArgumentTypes();
                List<ValueParameterDescriptor> paramDescrs = functionDescriptor.getValueParameters();
                for (int i = 0; i < paramDescrs.size(); i++) {
                    ValueParameterDescriptor parameter = paramDescrs.get(i);
                    int size = argTypes[i + (hasReceiver ? 1 : 0)].getSize();
                    var += size;
                    frameMap.enter(parameter, size);
                }

                frameMap.enterTemp();

                int maskIndex = var;

                var = 0;
                if(!isStatic) {
                    mv.visitVarInsn(ALOAD, var++);
                }

                if(hasReceiver) {
                    mv.visitVarInsn(ALOAD, var++); // todo - Long etc.
                }

                int extra = hasReceiver ? 1 : 0;
                for (final TypeParameterDescriptor typeParameterDescriptor : typeParameters) {
                    if(typeParameterDescriptor.isReified()) {
                        iv.load(var++, JetTypeMapper.TYPE_OBJECT);
                        extra++;
                    }
                }

                Type[] argumentTypes = jvmSignature.getArgumentTypes();
                for (int index = 0; index < paramDescrs.size(); index++) {
                    ValueParameterDescriptor parameterDescriptor = paramDescrs.get(index);

                    Type t = argumentTypes[extra + index];
                    Label endArg = null;
                    if (parameterDescriptor.hasDefaultValue()) {
                        iv.load(maskIndex, Type.INT_TYPE);
                        iv.iconst(1 << index);
                        iv.and(Type.INT_TYPE);
                        Label loadArg = new Label();
                        iv.ifeq(loadArg);

                        JetParameter jetParameter = (JetParameter) state.getBindingContext().get(BindingContext.DESCRIPTOR_TO_DECLARATION, parameterDescriptor);
                        assert jetParameter != null;
                        codegen.gen(jetParameter.getDefaultValue(), t);

                        endArg = new Label();
                        iv.goTo(endArg);

                        iv.mark(loadArg);
                    }

                    iv.load(var, t);
                    var += t.getSize();

                    if (parameterDescriptor.hasDefaultValue()) {
                        iv.mark(endArg);
                    }
                }

                if(!isStatic) {
                    if(kind == OwnerKind.TRAIT_IMPL) {
                        iv.invokeinterface(ownerInternalName, jvmSignature.getName(), jvmSignature.getDescriptor());
                    }
                    else
                        iv.invokevirtual(ownerInternalName, jvmSignature.getName(), jvmSignature.getDescriptor());
                }
                else {
                    iv.invokestatic(ownerInternalName, jvmSignature.getName(), jvmSignature.getDescriptor());
                }

                iv.areturn(jvmSignature.getReturnType());

                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }
    }

    private static void checkOverride(CodegenContext owner, GenerationState state, ClassBuilder v, Method jvmSignature, FunctionDescriptor functionDescriptor, FunctionDescriptor overriddenFunction) {
        Type type1 = state.getTypeMapper().mapType(overriddenFunction.getOriginal().getReturnType());
        Type type2 = state.getTypeMapper().mapType(functionDescriptor.getReturnType());
        if(!type1.equals(type2)) {
            Method overriden = state.getTypeMapper().mapSignature(overriddenFunction.getName(), overriddenFunction.getOriginal());
            int flags = ACC_PUBLIC; // TODO.

            final MethodVisitor mv = v.newMethod(null, flags, jvmSignature.getName(), overriden.getDescriptor(), null, null);
            if (v.generateCode()) {
                mv.visitCode();

                Type[] argTypes = jvmSignature.getArgumentTypes();
                InstructionAdapter iv = new InstructionAdapter(mv);
                iv.load(0, JetTypeMapper.TYPE_OBJECT);
                for (int i = 0, reg = 1; i < argTypes.length; i++) {
                    Type argType = argTypes[i];
                    iv.load(reg, argType);
                    if(argType.getSort() == Type.OBJECT) {
                        iv.checkcast(argType);
                    }
                    //noinspection AssignmentToForLoopParameter
                    reg += argType.getSize();
                }

                iv.invokevirtual(state.getTypeMapper().mapType(((ClassDescriptor) owner.getContextDescriptor()).getDefaultType(), OwnerKind.IMPLEMENTATION).getInternalName(), jvmSignature.getName(), jvmSignature.getDescriptor());
                if(JetTypeMapper.isPrimitive(jvmSignature.getReturnType()) && !JetTypeMapper.isPrimitive(overriden.getReturnType()))
                    StackValue.valueOf(iv, jvmSignature.getReturnType());
                if(jvmSignature.getReturnType() == Type.VOID_TYPE)
                    iv.aconst(null);
                iv.areturn(overriden.getReturnType());
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }
    }

}
