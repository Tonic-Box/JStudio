package com.tonic.service;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import lombok.Getter;

import java.util.Iterator;
import java.util.List;

/** Builds new empty class files (class, interface, enum or annotation) for adding to a project. */
public class ClassCreationService implements AccessFlags
{

    private static final ClassCreationService INSTANCE = new ClassCreationService();

    private ClassCreationService()
    {
    }

    /** @return the shared instance */
    public static ClassCreationService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Builds a class file of the requested kind, with no default constructor for interfaces, enums and annotations and no static initializer except an enum's.
     *
     * @param params what to create
     * @return the new class file
     */
    public ClassFile createClass(ClassCreationParams params)
    {
        switch (params.getClassType())
        {
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

    private ClassFile createRegularClass(ClassCreationParams params)
    {
        int accessFlags = computeClassAccessFlags(params);

        ClassBuilder builder = ClassBuilder.create(params.getFullClassName())
                .version(params.getMajorVersion(), 0)
                .access(accessFlags)
                .superClass(params.getSuperClass());

        addInterfaces(builder, params.getInterfaces());

        ClassFile cf = builder.build();
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private ClassFile createInterface(ClassCreationParams params)
    {
        ClassBuilder builder = ClassBuilder.create(params.getFullClassName())
                .version(params.getMajorVersion(), 0)
                .access(ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT)
                .superClass("java/lang/Object");

        addInterfaces(builder, params.getInterfaces());

        ClassFile cf = builder.build();
        removeMethod(cf, "<init>", "()V");
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private ClassFile createEnum(ClassCreationParams params)
    {
        String className = params.getFullClassName();

        ClassBuilder builder = ClassBuilder.create(className)
                .version(params.getMajorVersion(), 0)
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
                .invokevirtual(arrayDesc, "clone", "()Ljava/lang/Object;")
                .checkcast(arrayDesc)
                .areturn()
                .end()
                .end();

        builder.addMethod(ACC_PUBLIC | ACC_STATIC, "valueOf", "(Ljava/lang/String;)L" + className + ";")
                .code()
                .ldcClass(className)
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

    private ClassFile createAnnotation(ClassCreationParams params)
    {
        ClassBuilder builder = ClassBuilder.create(params.getFullClassName())
                .version(params.getMajorVersion(), 0)
                .access(ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT, ACC_ANNOTATION)
                .superClass("java/lang/Object")
                .interfaces("java/lang/annotation/Annotation");

        ClassFile cf = builder.build();
        removeMethod(cf, "<init>", "()V");
        removeMethod(cf, "<clinit>", "()V");
        return cf;
    }

    private int computeClassAccessFlags(ClassCreationParams params)
    {
        int flags = 0;

        if (params.isPublicAccess())
        {
            flags |= ACC_PUBLIC;
        }

        if (params.isAbstract())
        {
            flags |= ACC_ABSTRACT;
        }

        if (params.isFinal())
        {
            flags |= ACC_FINAL;
        }

        return flags;
    }

    private void addInterfaces(ClassBuilder builder, List<String> interfaces)
    {
        if (interfaces != null && !interfaces.isEmpty())
        {
            builder.interfaces(interfaces.toArray(new String[0]));
        }
    }

    private void removeMethod(ClassFile cf, String name, String desc)
    {
        Iterator<MethodEntry> it = cf.getMethods().iterator();
        while (it.hasNext())
        {
            MethodEntry method = it.next();
            if (name.equals(method.getName()) && desc.equals(method.getDesc()))
            {
                it.remove();
                break;
            }
        }
    }


    /** The kinds of class the service can create, each with its display name. */
    @Getter
    public enum ClassType
    {
        CLASS("Class"),
        INTERFACE("Interface"),
        ENUM("Enum"),
        ANNOTATION("Annotation");

        private final String displayName;

        ClassType(String displayName)
        {
            this.displayName = displayName;
        }

    }

    /** The settings for a new class; the superclass defaults to java/lang/Object. */
    public static class ClassCreationParams
    {
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
        @Getter
        private final int majorVersion;

        private ClassCreationParams(Builder builder)
        {
            this.fullClassName = builder.fullClassName;
            this.classType = builder.classType;
            this.publicAccess = builder.publicAccess;
            this.isAbstract = builder.isAbstract;
            this.isFinal = builder.isFinal;
            this.superClass = builder.superClass != null ? builder.superClass : "java/lang/Object";
            this.interfaces = builder.interfaces;
            this.majorVersion = builder.majorVersion;
        }

        /** @return whether the class is abstract */
        public boolean isAbstract()
        {
            return isAbstract;
        }

        /** @return whether the class is final */
        public boolean isFinal()
        {
            return isFinal;
        }

        /**
         * Starts a builder for a public class of Java 8 version extending java/lang/Object.
         *
         * @param fullClassName the internal name of the new class, with slashes
         * @return the builder
         */
        public static Builder builder(String fullClassName)
        {
            return new Builder(fullClassName);
        }

        /** The builder for class creation settings. */
        public static class Builder
        {
            private final String fullClassName;
            private ClassType classType = ClassType.CLASS;
            private boolean publicAccess = true;
            private boolean isAbstract = false;
            private boolean isFinal = false;
            private String superClass;
            private List<String> interfaces;
            private int majorVersion = V1_8;

            private Builder(String fullClassName)
            {
                this.fullClassName = fullClassName;
            }

            /**
             * Sets the class file major version.
             *
             * @param majorVersion the major version, such as 52 for Java 8
             * @return this builder
             */
            public Builder majorVersion(int majorVersion)
            {
                this.majorVersion = majorVersion;
                return this;
            }

            /**
             * Sets the kind of class.
             *
             * @param classType the kind
             * @return this builder
             */
            public Builder classType(ClassType classType)
            {
                this.classType = classType;
                return this;
            }

            /**
             * Sets whether the class is public.
             *
             * @param publicAccess true for public, false for package-private
             * @return this builder
             */
            public Builder publicAccess(boolean publicAccess)
            {
                this.publicAccess = publicAccess;
                return this;
            }

            /**
             * Sets whether a regular class is abstract.
             *
             * @param isAbstract true to mark it abstract
             * @return this builder
             */
            public Builder isAbstract(boolean isAbstract)
            {
                this.isAbstract = isAbstract;
                return this;
            }

            /**
             * Sets whether a regular class is final.
             *
             * @param isFinal true to mark it final
             * @return this builder
             */
            public Builder isFinal(boolean isFinal)
            {
                this.isFinal = isFinal;
                return this;
            }

            /**
             * Sets the superclass of a regular class.
             *
             * @param superClass the superclass internal name, or null for java/lang/Object
             * @return this builder
             */
            public Builder superClass(String superClass)
            {
                this.superClass = superClass;
                return this;
            }

            /**
             * Sets the interfaces the class implements.
             *
             * @param interfaces the interface internal names, or null for none
             * @return this builder
             */
            public Builder interfaces(List<String> interfaces)
            {
                this.interfaces = interfaces;
                return this;
            }

            /**
             * Creates the settings from this builder.
             *
             * @return the settings
             */
            public ClassCreationParams build()
            {
                return new ClassCreationParams(this);
            }
        }
    }
}
