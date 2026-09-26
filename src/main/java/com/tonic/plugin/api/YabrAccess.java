package com.tonic.plugin.api;

import java.util.List;
import java.util.Optional;

/** Direct access to the project's YABR bytecode model for plugins that need more than ProjectApi; YABR types come back as Object, and class lookups accept internal, dotted or suffix names as ProjectApi.getClass does. */
public interface YabrAccess
{

    /**
     * Returns a class-pool view over the project.
     *
     * @return the view, the same one for this instance
     */
    ClassPool getClassPool();

    /**
     * Finds a class's YABR ClassFile.
     *
     * @param name the class name, looked up as ProjectApi.getClass does
     * @return the ClassFile, or empty when the class is not found
     */
    Optional<Object> getClassFile(String name);

    /**
     * Returns a method's SSA IR if the app has already lifted and cached it; this never lifts on its own.
     *
     * @param className the declaring class
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor
     * @return the cached IRMethod, or empty when the class or method is not found or nothing is cached
     */
    Optional<Object> liftToIR(String className, String methodName, String descriptor);

    /**
     * Builds the call graph of the whole project; can be slow on a large project.
     *
     * @return a new YABR CallGraph, or null when the project has no class pool
     */
    Object buildCallGraph();

    /**
     * Lifts a method to SSA and builds its data-flow graph.
     *
     * @param className the declaring class
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor
     * @return a new YABR DataFlowGraph, or null when the method is not found, has no code, or lifting fails
     */
    Object buildDataFlowGraph(String className, String methodName, String descriptor);

    /**
     * Finds a method's YABR MethodEntry.
     *
     * @param className the declaring class
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor
     * @return the MethodEntry, or null when the class or method is not found
     */
    Object getMethodEntry(String className, String methodName, String descriptor);

    /**
     * Disassembles one method's bytecode.
     *
     * @param className the declaring class
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor
     * @param verbose true to add line numbers, locals, frames and the exception table
     * @return the listing, or empty when the method is not found or has no code
     */
    Optional<String> disassembleMethod(String className, String methodName, String descriptor, boolean verbose);

    /**
     * Lifts one method to SSA and renders it as text, with blocks, phi nodes and instructions.
     *
     * @param className the declaring class
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor
     * @return the IR text, or empty when the class or method is not found
     */
    Optional<String> methodSsaIr(String className, String methodName, String descriptor);

    /**
     * Decompiles one method's body to source.
     *
     * @param className the declaring class
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor
     * @return the source, or empty when the method is not found, has no code, or recovery fails
     */
    Optional<String> methodAst(String className, String methodName, String descriptor);

    /**
     * Lists every class in the project.
     *
     * @return a new list of internal names, in no fixed order
     */
    List<String> getLoadedClassNames();

    /**
     * Serializes a class as it stands now, edits included.
     *
     * @param className the class name, looked up as ProjectApi.getClass does
     * @return the class file bytes, or an empty array when the class is not found or writing fails
     */
    byte[] getClassBytes(String className);

    /**
     * Parses class file bytes and adds the class to the project.
     *
     * @param name the class name, used only in the error message
     * @param bytecode the class file bytes
     * @throws RuntimeException if the bytes do not parse or the class cannot be added
     */
    void addClass(String name, byte[] bytecode);

    /**
     * Removes a class from the YABR class pool; the project model's class list is not updated.
     *
     * @param name the class's exact internal name
     */
    void removeClass(String name);

    /** A class-pool view over the project, keyed by class name; its writes change the project itself. */
    interface ClassPool
    {

        /**
         * Lists every class in the project.
         *
         * @return a new list of internal names, in no fixed order
         */
        List<String> getClassNames();

        /**
         * Reports whether a class exists.
         *
         * @param name the class name, looked up as ProjectApi.getClass does, suffixes included
         * @return true when a class matches
         */
        boolean hasClass(String name);

        /**
         * Serializes a class as it stands now.
         *
         * @param name the class name, looked up as ProjectApi.getClass does
         * @return the class file bytes, or an empty array when the class is not found or writing fails
         */
        byte[] getClassBytes(String name);

        /**
         * Finds a class's YABR ClassFile.
         *
         * @param name the class name, looked up as ProjectApi.getClass does
         * @return the ClassFile, or null when the class is not found
         */
        Object getClassNode(String name);

        /**
         * Parses class file bytes and adds the class to the project.
         *
         * @param name the class name, used only in the error message
         * @param bytecode the class file bytes
         * @throws RuntimeException if the bytes do not parse or the class cannot be added
         */
        void putClass(String name, byte[] bytecode);

        /**
         * Adds a YABR ClassFile to the project; anything else is ignored.
         *
         * @param name unused
         * @param classNode the ClassFile to add
         */
        void putClassNode(String name, Object classNode);

        /**
         * Refuses to clear the project; plugins cannot remove the open project's classes wholesale.
         *
         * @throws UnsupportedOperationException always
         */
        void clear();

        /**
         * Counts the project's classes.
         *
         * @return the class count
         */
        int size();
    }
}
