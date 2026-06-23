package com.tonic.ui.vm;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.debug.DebugSession;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

/**
 * One isolated bytecode-VM instance: its own heap, resolver, and class pool, driving at most one debug session at a
 * time. The YABR engine holds no global mutable state, so independent instances run without interfering - each AI
 * subagent can own one, backed by a {@link SnapshotClassPool} that is immune to concurrent project edits.
 */
public final class VmInstance {

    @Getter
    private final ClassPool classPool;
    @Getter
    private final SimpleHeapManager heapManager;
    @Getter
    private final ClassResolver classResolver;
    private final int maxCallDepth;
    private final int maxInstructions;
    @Getter
    private DebugSession currentDebugSession;

    public VmInstance(ClassPool classPool, int maxCallDepth, int maxInstructions) {
        this.classPool = classPool;
        this.heapManager = new SimpleHeapManager();
        this.classResolver = new ClassResolver(classPool);
        this.heapManager.setClassResolver(classResolver);
        this.maxCallDepth = maxCallDepth;
        this.maxInstructions = maxInstructions;
    }

    public MethodEntry findMethod(String className, String methodName, String descriptor) {
        ClassFile classFile = classPool.get(className);
        return classFile == null ? null : VmSupport.findMethod(classFile, methodName, descriptor);
    }

    public DebugSession createDebugSession(String className, String methodName, String descriptor,
                                           boolean recursive, Object... args) {
        ClassFile classFile = classPool.get(className);
        if (classFile == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        MethodEntry method = VmSupport.findMethod(classFile, methodName, descriptor);
        if (method == null) {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        if (currentDebugSession != null && !currentDebugSession.isStopped()) {
            currentDebugSession.stop();
        }
        BytecodeContext sessionContext = new BytecodeContext.Builder()
                .heapManager(heapManager)
                .classResolver(classResolver)
                .mode(recursive ? ExecutionMode.RECURSIVE : ExecutionMode.DELEGATED)
                .maxCallDepth(maxCallDepth)
                .maxInstructions(maxInstructions)
                .build();
        currentDebugSession = new DebugSession(sessionContext);
        currentDebugSession.start(method, VmSupport.toConcreteValues(heapManager, args));
        return currentDebugSession;
    }

    /** Runs a method to completion on this instance's heap (used to construct object arguments via a constructor). */
    public BytecodeResult executeMethod(String className, String methodName, String descriptor,
                                        Object receiver, Object... args) {
        ClassFile classFile = classPool.get(className);
        if (classFile == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        MethodEntry method = VmSupport.findMethod(classFile, methodName, descriptor);
        if (method == null) {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        Object[] all;
        if (receiver != null) {
            all = new Object[args.length + 1];
            all[0] = receiver;
            System.arraycopy(args, 0, all, 1, args.length);
        } else {
            all = args;
        }
        ConcreteValue[] vmArgs = VmSupport.toConcreteValues(heapManager, all);
        BytecodeContext context = new BytecodeContext.Builder()
                .heapManager(heapManager)
                .classResolver(classResolver)
                .mode(ExecutionMode.RECURSIVE)
                .maxCallDepth(maxCallDepth)
                .maxInstructions(maxInstructions)
                .build();
        return new BytecodeEngine(context).execute(method, vmArgs);
    }

    public void dispose() {
        if (currentDebugSession != null && !currentDebugSession.isStopped()) {
            currentDebugSession.stop();
        }
        currentDebugSession = null;
    }
}
