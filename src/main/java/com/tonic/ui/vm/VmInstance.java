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

/** One isolated bytecode VM with its own heap, resolver and class pool, driving at most one debug session at a time. */
public final class VmInstance
{

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

    /**
     * Creates a VM with a fresh heap over the given class pool.
     *
     * @param classPool the classes the VM resolves against
     * @param maxCallDepth the call depth limit for every run
     * @param maxInstructions the instruction limit for every run
     */
    public VmInstance(ClassPool classPool, int maxCallDepth, int maxInstructions)
    {
        this.classPool = classPool;
        this.heapManager = new SimpleHeapManager();
        this.classResolver = new ClassResolver(classPool);
        this.heapManager.setClassResolver(classResolver);
        this.maxCallDepth = maxCallDepth;
        this.maxInstructions = maxInstructions;
    }

    /**
     * Finds a method in this VM's class pool.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @return the method, or null if the class or method is not found
     */
    public MethodEntry findMethod(String className, String methodName, String descriptor)
    {
        ClassFile classFile = classPool.get(className);
        return classFile == null ? null : VmSupport.findMethod(classFile, methodName, descriptor);
    }

    /**
     * Starts a debug session on a method, stopping any session still running.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param recursive true to step into callees, false to delegate calls
     * @param args the arguments, converted to VM values
     * @return the started session, also kept as the current session
     * @throws IllegalArgumentException if the class or method is not found
     */
    public DebugSession createDebugSession(String className, String methodName, String descriptor, boolean recursive, Object... args)
    {
        ClassFile classFile = classPool.get(className);
        if (classFile == null)
        {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        MethodEntry method = VmSupport.findMethod(classFile, methodName, descriptor);
        if (method == null)
        {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        if (currentDebugSession != null && !currentDebugSession.isStopped())
        {
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
        currentDebugSession.start(method, new VmValueConverter(heapManager).toConcreteAll(args, null));
        return currentDebugSession;
    }

    /**
     * Runs a method to completion on this VM's heap, in recursive mode; used to construct object arguments.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param receiver the instance to call on, or null for a static method
     * @param args the arguments, converted to VM values
     * @return the engine's result
     * @throws IllegalArgumentException if the class or method is not found
     */
    public BytecodeResult executeMethod(String className, String methodName, String descriptor, Object receiver, Object... args)
    {
        ClassFile classFile = classPool.get(className);
        if (classFile == null)
        {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        MethodEntry method = VmSupport.findMethod(classFile, methodName, descriptor);
        if (method == null)
        {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        Object[] all;
        if (receiver != null)
        {
            all = new Object[args.length + 1];
            all[0] = receiver;
            System.arraycopy(args, 0, all, 1, args.length);
        }
        else
        {
            all = args;
        }
        ConcreteValue[] vmArgs = new VmValueConverter(heapManager).toConcreteAll(all, null);
        BytecodeContext context = new BytecodeContext.Builder()
                .heapManager(heapManager)
                .classResolver(classResolver)
                .mode(ExecutionMode.RECURSIVE)
                .maxCallDepth(maxCallDepth)
                .maxInstructions(maxInstructions)
                .build();
        return new BytecodeEngine(context).execute(method, vmArgs);
    }

    /** Stops the current debug session if it is running and forgets it. */
    public void dispose()
    {
        if (currentDebugSession != null && !currentDebugSession.isStopped())
        {
            currentDebugSession.stop();
        }
        currentDebugSession = null;
    }
}
