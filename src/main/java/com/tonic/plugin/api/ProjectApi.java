package com.tonic.plugin.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Read access to the project's classes, methods and fields through wrappers over the live project model, built afresh on every call. */
public interface ProjectApi
{

    /**
     * Returns the project's name.
     *
     * @return the name, "Untitled" when none was set
     */
    String getName();

    /**
     * Returns the file the project was loaded from.
     *
     * @return its absolute path, or an empty string when the project has no source file
     */
    String getPath();

    /**
     * Returns every class in the project.
     *
     * @return a new list in no fixed order, empty when no project is open
     */
    List<ClassInfo> getClasses();

    /**
     * Returns the classes that pass a filter.
     *
     * @param filter which classes to keep
     * @return a new list in no fixed order
     */
    List<ClassInfo> getClasses(Predicate<ClassInfo> filter);

    /**
     * Finds a class by internal name, dotted name, or a name suffix such as the simple name; for a suffix the first match wins.
     *
     * @param name the class name, with slashes or dots, or its end
     * @return the class, or empty when none matches
     */
    Optional<ClassInfo> getClass(String name);

    /**
     * Runs an action on every class, on the calling thread.
     *
     * @param action called once per class
     */
    void forEachClass(Consumer<ClassInfo> action);

    /**
     * Runs an action on every method of every class, constructors and static initializers included, on the calling thread.
     *
     * @param action called once per method
     */
    void forEachMethod(Consumer<MethodInfo> action);

    /**
     * Returns a class's declared methods, constructors and static initializer included.
     *
     * @param className the class, looked up as getClass does
     * @return a new list in no fixed order, or an empty list when the class is not found
     */
    List<MethodInfo> getMethods(String className);

    /**
     * Finds a declared method by exact name and descriptor.
     *
     * @param className the class, looked up as getClass does
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor, such as (I)V
     * @return the method, or empty when the class or method is not found
     */
    Optional<MethodInfo> getMethod(String className, String methodName, String descriptor);

    /**
     * Returns the distinct packages that contain classes.
     *
     * @return sorted dotted package names, with an empty string for the default package
     */
    List<String> getPackages();

    /**
     * Returns the classes in exactly one package, not its subpackages.
     *
     * @param packageName the package name, dotted or with slashes, empty for the default package
     * @return a new list, empty when nothing matches
     */
    List<ClassInfo> getClassesInPackage(String packageName);

    /**
     * Returns how many classes the project has.
     *
     * @return the class count
     */
    int getClassCount();

    /**
     * Counts the methods across all classes, constructors and static initializers included.
     *
     * @return the method count
     */
    int getMethodCount();

    /** A view of one class, backed by the live project model. */
    interface ClassInfo
    {
        /**
         * Returns the class's name.
         *
         * @return the internal name, with slashes
         */
        String getName();

        /**
         * Returns the class's name without its package.
         *
         * @return the simple name
         */
        String getSimpleName();

        /**
         * Returns the class's package.
         *
         * @return the dotted package name, or an empty string for the default package
         */
        String getPackageName();

        /**
         * Returns the superclass.
         *
         * @return its internal name, as the class file records it
         */
        String getSuperclass();

        /**
         * Returns the directly implemented interfaces.
         *
         * @return a new list of internal names
         */
        List<String> getInterfaces();

        /**
         * Returns the class's declared methods, constructors and static initializer included.
         *
         * @return a new list in no fixed order
         */
        List<MethodInfo> getMethods();

        /**
         * Returns the class's declared fields.
         *
         * @return a new list
         */
        List<FieldInfo> getFields();

        /**
         * Returns the class's access flags.
         *
         * @return the JVM access flags
         */
        int getAccessFlags();

        /**
         * Reports whether the class is an interface.
         *
         * @return true for an interface
         */
        boolean isInterface();

        /**
         * Reports whether the class is abstract.
         *
         * @return true for an abstract class or interface
         */
        boolean isAbstract();

        /**
         * Reports whether the class is an enum.
         *
         * @return true for an enum
         */
        boolean isEnum();

        /**
         * Reports whether the class is an annotation type.
         *
         * @return true for an annotation type
         */
        boolean isAnnotation();

        /**
         * Serializes the class as it stands now, edits included.
         *
         * @return the class file bytes, or an empty array when writing fails
         */
        byte[] getBytecode();
    }

    /** A view of one method, backed by the live project model. */
    interface MethodInfo
    {
        /**
         * Returns the method's name.
         *
         * @return the name; constructors and static initializers carry their JVM names
         */
        String getName();

        /**
         * Returns the method's descriptor.
         *
         * @return the JVM descriptor, such as (I)V
         */
        String getDescriptor();

        /**
         * Returns the declaring class.
         *
         * @return its internal name, with slashes
         */
        String getClassName();

        /**
         * Returns a readable signature for display, not the generic signature attribute.
         *
         * @return the name followed by readable parameter types in parentheses
         */
        String getSignature();

        /**
         * Returns the method's access flags.
         *
         * @return the JVM access flags
         */
        int getAccessFlags();

        /**
         * Reports whether the method is static.
         *
         * @return true for a static method
         */
        boolean isStatic();

        /**
         * Reports whether the method is abstract.
         *
         * @return true for an abstract method
         */
        boolean isAbstract();

        /**
         * Reports whether the method is native.
         *
         * @return true for a native method
         */
        boolean isNative();

        /**
         * Reports whether the method has the synthetic flag.
         *
         * @return true when the compiler generated it
         */
        boolean isSynthetic();

        /**
         * Returns the parameter types.
         *
         * @return a new list of field descriptors, such as I or Ljava/lang/String;, in declaration order
         */
        List<String> getParameterTypes();

        /**
         * Returns the return type.
         *
         * @return its field descriptor, V for void
         */
        String getReturnType();

        /**
         * Counts the method's bytecode instructions.
         *
         * @return the number of instructions, 0 for an abstract or native method
         */
        int getInstructionCount();

        /**
         * Returns the method's raw code bytes.
         *
         * @return a copy of the code array, or an empty array for an abstract or native method
         */
        byte[] getBytecode();
    }

    /** A view of one field, backed by the live project model. */
    interface FieldInfo
    {
        /**
         * Returns the field's name.
         *
         * @return the name
         */
        String getName();

        /**
         * Returns the field's type.
         *
         * @return its field descriptor
         */
        String getDescriptor();

        /**
         * Returns the declaring class.
         *
         * @return its internal name, with slashes
         */
        String getClassName();

        /**
         * Returns the field's access flags.
         *
         * @return the JVM access flags
         */
        int getAccessFlags();

        /**
         * Reports whether the field is static.
         *
         * @return true for a static field
         */
        boolean isStatic();

        /**
         * Reports whether the field is final.
         *
         * @return true for a final field
         */
        boolean isFinal();

        /**
         * Returns the field's constant initializer from its ConstantValue attribute.
         *
         * @return the Integer, Long, Float, Double or String constant, or null when the field has none
         */
        Object getConstantValue();
    }
}
