package com.tonic.ui.service;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import lombok.Getter;

import java.util.Iterator;
import java.util.List;

public class ClassCreationService implements AccessFlags {

    private static final ClassCreationService INSTANCE = new ClassCreationService();

    private ClassCreationService() {
    }

    public static ClassCreationService getInstance() {
        return INSTANCE;
    }

    public ClassFile createClass(ClassCreationParams params) {
        switch (params.getClassType()) {
            case INTERFACE:
                return createInterface(params);
            case ENUM:
                return createEnum(params);
            case ANNOTATION:
                return createAnnotation(params);
            case CLASS:
            default:
                return createRegularClass(params);
        }
    }

    private ClassFile createRegularClass(ClassCreationParams params) {
        int accessFlags = computeClassAccessFlags(params);

        ClassBuilder builder = ClassBuilder.create(params.getFullClassName())
                .version(V1_8, 0)
                .access(accessFlags)
                .superClass(params.getSuperClass());

        addInterfaces(builder, params.getInterfaces());

        ClassFile cf = builder.build();
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private ClassFile createInterface(ClassCreationParams params) {
        ClassBuilder builder = ClassBuilder.create(params.getFullClassName())
                .version(V1_8, 0)
                .access(ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT)
                .superClass("java/lang/Object");

        addInterfaces(builder, params.getInterfaces());

        ClassFile cf = builder.build();
        removeMethod(cf, "<init>", "()V");
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private ClassFile createEnum(ClassCreationParams params) {
        String className = params.getFullClassName();

        ClassBuilder builder = ClassBuilder.create(className)
                .version(V1_8, 0)
                .access(ACC_PUBLIC, ACC_FINAL, ACC_ENUM)
                .superClass("java/lang/Enum");

        addInterfaces(builder, params.getInterfaces());

        String arrayDesc = "[L" + className + ";";

        builder.addField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "$VALUES", arrayDesc)
                .end();

        builder.addMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V")
                .code()
                .aload(0)
                .aload(1)
                .iload(2)
                .invokespecial("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V")
                .vreturn()
                .end()
                .end();

        builder.addMethod(ACC_PUBLIC | ACC_STATIC, "values", "()" + arrayDesc)
                .code()
                .getstatic(className, "$VALUES", arrayDesc)
                .invokevirtual(arrayDesc.substring(1, arrayDesc.length() - 1), "clone", "()Ljava/lang/Object;")
                .checkcast(arrayDesc.substring(1, arrayDesc.length() - 1))
                .areturn()
                .end()
                .end();

        builder.addMethod(ACC_PUBLIC | ACC_STATIC, "valueOf", "(Ljava/lang/String;)L" + className + ";")
                .code()
                .ldc(className.replace('/', '.'))
                .aload(0)
                .invokestatic("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;")
                .checkcast(className)
                .areturn()
                .end()
                .end();

        builder.addMethod(ACC_STATIC, "<clinit>", "()V")
                .code()
                .iconst(0)
                .anewarray(className)
                .putstatic(className, "$VALUES", arrayDesc)
                .vreturn()
                .end()
                .end();

        ClassFile cf = builder.build();
        removeMethod(cf, "<init>", "()V");
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private ClassFile createAnnotation(ClassCreationParams params) {
        ClassBuilder builder = ClassBuilder.create(params.getFullClassName())
                .version(V1_8, 0)
                .access(ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT, ACC_ANNOTATION)
                .superClass("java/lang/Object")
                .interfaces("java/lang/annotation/Annotation");

        ClassFile cf = builder.build();
        removeMethod(cf, "<init>", "()V");
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private int computeClassAccessFlags(ClassCreationParams params) {
        int flags = 0;

        if (params.isPublicAccess()) {
            flags |= ACC_PUBLIC;
        }

        if (params.isAbstract()) {
            flags |= ACC_ABSTRACT;
        }

        if (params.isFinal()) {
            flags |= ACC_FINAL;
        }

        return flags;
    }

    private void addInterfaces(ClassBuilder builder, List<String> interfaces) {
        if (interfaces != null && !interfaces.isEmpty()) {
            builder.interfaces(interfaces.toArray(new String[0]));
        }
    }

    private void removeMethod(ClassFile cf, String name, String desc) {
        Iterator<MethodEntry> it = cf.getMethods().iterator();
        while (it.hasNext()) {
            MethodEntry method = it.next();
            if (name.equals(method.getName()) && desc.equals(method.getDesc())) {
                it.remove();
                break;
            }
        }
    }


    @Getter
    public enum ClassType {
        CLASS("Class"),
        INTERFACE("Interface"),
        ENUM("Enum"),
        ANNOTATION("Annotation");

        private final String displayName;

        ClassType(String displayName) {
            this.displayName = displayName;
        }

    }

    public static class ClassCreationParams {
        @Getter
        private final String fullClassName;
        @Getter
        private final ClassType classType;
        @Getter
        private final boolean publicAccess;
        private final boolean isAbstract;
        private final boolean isFinal;
        @Getter
        private final String superClass;
        @Getter
        private final List<String> interfaces;

        private ClassCreationParams(Builder builder) {
            this.fullClassName = builder.fullClassName;
            this.classType = builder.classType;
            this.publicAccess = builder.publicAccess;
            this.isAbstract = builder.isAbstract;
            this.isFinal = builder.isFinal;
            this.superClass = builder.superClass != null ? builder.superClass : "java/lang/Object";
            this.interfaces = builder.interfaces;
        }

        public boolean isAbstract() {
            return isAbstract;
        }

        public boolean isFinal() {
            return isFinal;
        }

        public static Builder builder(String fullClassName) {
            return new Builder(fullClassName);
        }

        public static class Builder {
            private final String fullClassName;
            private ClassType classType = ClassType.CLASS;
            private boolean publicAccess = true;
            private boolean isAbstract = false;
            private boolean isFinal = false;
            private String superClass;
            private List<String> interfaces;

            private Builder(String fullClassName) {
                this.fullClassName = fullClassName;
            }

            public Builder classType(ClassType classType) {
                this.classType = classType;
                return this;
            }

            public Builder publicAccess(boolean publicAccess) {
                this.publicAccess = publicAccess;
                return this;
            }

            public Builder isAbstract(boolean isAbstract) {
                this.isAbstract = isAbstract;
                return this;
            }

            public Builder isFinal(boolean isFinal) {
                this.isFinal = isFinal;
                return this;
            }

            public Builder superClass(String superClass) {
                this.superClass = superClass;
                return this;
            }

            public Builder interfaces(List<String> interfaces) {
                this.interfaces = interfaces;
                return this;
            }

            public ClassCreationParams build() {
                return new ClassCreationParams(this);
            }
        }
    }
}
