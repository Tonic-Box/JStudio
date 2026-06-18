package com.tonic.plugin.api;

import java.util.List;
import java.util.Optional;

public interface YabrAccess {

    ClassPool getClassPool();

    Optional<Object> getClassFile(String name);

    Optional<Object> liftToIR(String className, String methodName, String descriptor);

    Object buildCallGraph();

    Object buildDataFlowGraph(String className, String methodName, String descriptor);

    Object getMethodEntry(String className, String methodName, String descriptor);

    /** Raw bytecode disassembly of one method ({@code verbose} adds line numbers, locals, frames, exception table). */
    Optional<String> disassembleMethod(String className, String methodName, String descriptor, boolean verbose);

    /** SSA IR text (blocks, phi nodes, instructions) for one method. */
    Optional<String> methodSsaIr(String className, String methodName, String descriptor);

    /** Decompiled AST source for one method, or empty if recovery fails (obfuscated/abstract/native). */
    Optional<String> methodAst(String className, String methodName, String descriptor);

    List<String> getLoadedClassNames();

    byte[] getClassBytes(String className);

    void addClass(String name, byte[] bytecode);

    void removeClass(String name);

    interface ClassPool {

        List<String> getClassNames();

        boolean hasClass(String name);

        byte[] getClassBytes(String name);

        Object getClassNode(String name);

        void putClass(String name, byte[] bytecode);

        void putClassNode(String name, Object classNode);

        void clear();

        int size();
    }
}
