package com.tonic.ui.vm;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.debug.DebugSession;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.event.EventBus;
import com.tonic.event.events.ProjectLoadedEvent;
import com.tonic.event.events.StatusMessageEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ConsoleLogService;
import com.tonic.service.ProjectService;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.MethodCall;
import com.tonic.util.DescriptorParser;

import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** The shared bytecode VM over the current project's class pool, initialized on first use and shut down when another project loads; runs one execution at a time. */
public class VMExecutionService
{

    private static final VMExecutionService INSTANCE = new VMExecutionService();

    @Getter
    private ClassPool classPool;
    @Getter
    private ClassResolver classResolver;
    @Getter
    private SimpleHeapManager heapManager;
    private BytecodeContext context;
    private BytecodeEngine currentEngine;
    @Getter
    private DebugSession currentDebugSession;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean executing = new AtomicBoolean(false);

    @Getter
    private int maxCallDepth = 1000;
    @Getter
    private int maxInstructions = 10_000_000;

    private long cachedSnapshotVersion = -1;
    private Map<String, byte[]> cachedFrozenClasses;

    private VMExecutionService()
    {
        EventBus.getInstance().register(ProjectLoadedEvent.class, this::onProjectLoaded);
    }

    private void onProjectLoaded(ProjectLoadedEvent event)
    {
        if (initialized.get())
        {
            shutdown();
        }
    }

    /** @return the single service instance */
    public static VMExecutionService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Creates the heap, resolver and context over the current project's class pool; does nothing if already initialized.
     *
     * @throws IllegalStateException if no project is loaded
     */
    public synchronized void initialize()
    {
        if (initialized.get())
        {
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            throw new IllegalStateException("No project loaded. Load a project before initializing the VM.");
        }

        this.classPool = project.getClassPool();
        this.heapManager = new SimpleHeapManager();
        this.classResolver = new ClassResolver(classPool);
        this.heapManager.setClassResolver(classResolver);

        rebuildContext();
        initialized.set(true);

        EventBus.getInstance().post(new StatusMessageEvent(this, "VM initialized with " + classPool.getClasses().size() + " classes"));
    }

    /** Interrupts any running execution, stops the debug session and drops the VM state; does nothing if not initialized. */
    public synchronized void shutdown()
    {
        if (!initialized.get())
        {
            return;
        }

        if (currentEngine != null)
        {
            currentEngine.interrupt();
            currentEngine = null;
        }

        if (currentDebugSession != null)
        {
            if (!currentDebugSession.isStopped())
            {
                currentDebugSession.stop();
            }
            currentDebugSession = null;
        }

        classPool = null;
        classResolver = null;
        heapManager = null;
        context = null;
        initialized.set(false);

        EventBus.getInstance().post(new StatusMessageEvent(this, "VM shutdown"));
    }

    /**
     * Shuts the VM down and initializes it again with a fresh heap.
     *
     * @throws IllegalStateException if no project is loaded
     */
    public synchronized void reset()
    {
        shutdown();
        initialize();
    }

    /**
     * Tells whether the VM is initialized.
     *
     * @return true once initialized and until shut down
     */
    public boolean isInitialized()
    {
        return initialized.get();
    }

    /**
     * Tells whether an execution is running.
     *
     * @return true while a run is in progress
     */
    public boolean isExecuting()
    {
        return executing.get();
    }

    /**
     * Runs a static method to completion, initializing the VM first if needed.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param args the arguments, converted to VM values of the declared parameter types
     * @return the result; a failed result carrying the exception if another execution is running, the class or method is missing, the method is not static, an argument cannot be converted, or the run throws
     * @throws IllegalStateException if the VM is not initialized and no project is loaded
     */
    public ExecutionResult executeStaticMethod(String className, String methodName, String descriptor, Object... args)
    {
        return run(className, methodName, descriptor, Boolean.TRUE, null, args, false, null, null);
    }

    /**
     * Runs an instance method to completion on a receiver, initializing the VM first if needed.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param receiver the instance to call on: a VM object, or a constructor, factory or field-injection spec built in the VM first
     * @param args the arguments, converted to VM values of the declared parameter types
     * @return the result; a failed result carrying the exception if another execution is running, the class or method is missing, the method is static, the receiver or an argument cannot be built, or the run throws
     * @throws IllegalStateException if the VM is not initialized and no project is loaded
     */
    public ExecutionResult executeMethod(String className, String methodName, String descriptor, Object receiver, Object... args)
    {
        return run(className, methodName, descriptor, Boolean.FALSE, receiver, args, false, null, null);
    }

    /**
     * Runs a static method to completion in recursive mode with a listener attached, initializing the VM first if needed.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param args the arguments, converted to VM values of the declared parameter types
     * @param listener told about each instruction, or null for none
     * @return the result; a failed result carrying the exception if another execution is running, the class or method is missing, the method is not static, an argument cannot be converted, or the run throws
     * @throws IllegalStateException if the VM is not initialized and no project is loaded
     */
    public ExecutionResult executeStaticMethodWithListener(String className, String methodName, String descriptor, Object[] args, BytecodeListener listener)
    {
        return run(className, methodName, descriptor, Boolean.TRUE, null, args, true, engine -> listener, null);
    }

    /**
     * Runs a static or instance method to completion in recursive mode with a listener attached, initializing the VM first if needed.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param receiver null for a static method; for an instance method a VM object, or a constructor, factory or field-injection spec built in the VM first
     * @param args the arguments, converted to VM values of the declared parameter types
     * @param listener told about each instruction, or null for none
     * @return the result; a failed result carrying the exception if another execution is running, the class or method is missing, the receiver does not match the method, the receiver or an argument cannot be built, or the run throws
     * @throws IllegalStateException if the VM is not initialized and no project is loaded
     */
    public ExecutionResult executeWithListener(String className, String methodName, String descriptor, Object receiver, Object[] args, BytecodeListener listener)
    {
        return run(className, methodName, descriptor, null, receiver, args, true, engine -> listener, null);
    }

    /**
     * Runs a method to completion in recursive mode with a listener attached, initializing the VM first if needed.
     *
     * @param method the method to run
     * @param args the arguments, the receiver first for an instance method, converted to VM values of the declared types
     * @param listener told about each instruction, or null for none
     * @return the engine's result
     * @throws IllegalStateException if another execution is running, or if the VM is not initialized and no project is loaded
     * @throws IllegalArgumentException if the argument count does not match the method or an argument cannot be converted
     */
    public BytecodeResult executeMethodWithListener(MethodEntry method, Object[] args, BytecodeListener listener)
    {
        ensureInitialized();

        if (!executing.compareAndSet(false, true))
        {
            throw new IllegalStateException("Another execution is in progress");
        }

        try
        {
            ConcreteValue[] vmArgs = converter().toConcreteAll(args, frameTypes(method));
            BytecodeEngine engine = new BytecodeEngine(recursiveContext());
            currentEngine = engine;
            if (listener != null)
            {
                engine.addListener(listener);
            }
            return engine.execute(method, vmArgs);
        }
        finally
        {
            currentEngine = null;
            executing.set(false);
        }
    }

    /**
     * Runs a static method to completion in recursive mode, recording every call it makes with its arguments, return value and timing.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param args the arguments, converted to VM values of the declared parameter types
     * @return the result with the recorded calls; a failed result carrying the exception and the calls so far if another execution is running, the class or method is missing, the method is not static, an argument cannot be converted, or the run throws
     * @throws IllegalStateException if the VM is not initialized and no project is loaded
     */
    public ExecutionResult traceStaticMethod(String className, String methodName, String descriptor, Object... args)
    {
        List<MethodCall> methodCalls = new ArrayList<>();
        return run(className, methodName, descriptor, Boolean.TRUE, null, args, true, engine -> new CallTraceListener(engine, methodCalls), methodCalls);
    }

    private ExecutionResult run(String className, String methodName, String descriptor, Boolean wantStatic, Object receiver, Object[] args, boolean recursive, Function<BytecodeEngine, BytecodeListener> listenerFactory, List<MethodCall> methodCalls)
    {
        ensureInitialized();

        if (!executing.compareAndSet(false, true))
        {
            return failure(new IllegalStateException("Another execution is in progress"), 0, methodCalls);
        }

        long startTime = System.currentTimeMillis();
        try
        {
            MethodEntry method = requireMethod(className, methodName, descriptor);
            boolean isStatic = (method.getAccess() & 0x0008) != 0;
            if (wantStatic != null && wantStatic != isStatic)
            {
                throw new IllegalArgumentException(isStatic ? "Method is static, use executeStaticMethod instead" : "Method is not static: " + methodName);
            }

            ConcreteValue[] vmArgs = arguments(method, receiver, args);
            BytecodeEngine engine = new BytecodeEngine(recursive ? recursiveContext() : context);
            currentEngine = engine;
            BytecodeListener listener = listenerFactory != null ? listenerFactory.apply(engine) : null;
            if (listener != null)
            {
                engine.addListener(listener);
            }

            BytecodeResult result = engine.execute(method, vmArgs);
            if (listener instanceof CallTraceListener)
            {
                ((CallTraceListener) listener).finish(result);
            }
            return buildExecutionResult(result, method, System.currentTimeMillis() - startTime, methodCalls);
        }
        catch (Exception e)
        {
            return failure(e, System.currentTimeMillis() - startTime, methodCalls);
        }
        finally
        {
            currentEngine = null;
            executing.set(false);
        }
    }

    private MethodEntry requireMethod(String className, String methodName, String descriptor)
    {
        ClassFile classFile = classPool.get(className);
        if (classFile == null)
        {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        MethodEntry method = findMethod(classFile, methodName, descriptor);
        if (method == null)
        {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + (descriptor != null ? descriptor : ""));
        }
        return method;
    }

    private ConcreteValue[] arguments(MethodEntry method, Object receiver, Object[] args)
    {
        VmValueConverter converter = converter();
        boolean isStatic = (method.getAccess() & 0x0008) != 0;
        if (isStatic && receiver != null)
        {
            throw new IllegalArgumentException("A static method takes no receiver: " + method.getName());
        }
        if (!isStatic && receiver == null)
        {
            throw new IllegalArgumentException("An instance method needs a receiver: " + method.getName());
        }
        ConcreteValue self = isStatic ? null : converter.toConcrete(receiver, "L" + method.getOwnerName() + ";");
        ConcreteValue[] params = converter.toConcreteAll(args, DescriptorParser.parameterDescriptors(method.getDesc()));
        if (isStatic)
        {
            return params;
        }
        ConcreteValue[] all = new ConcreteValue[params.length + 1];
        all[0] = self;
        System.arraycopy(params, 0, all, 1, params.length);
        return all;
    }

    private static List<String> frameTypes(MethodEntry method)
    {
        List<String> types = new ArrayList<>(DescriptorParser.parameterDescriptors(method.getDesc()));
        if ((method.getAccess() & 0x0008) == 0)
        {
            types.add(0, "L" + method.getOwnerName() + ";");
        }
        return types;
    }

    private ExecutionResult failure(Exception exception, long executionTimeMs, List<MethodCall> methodCalls)
    {
        return ExecutionResult.builder()
                .success(false)
                .exception(exception)
                .executionTimeMs(executionTimeMs)
                .methodCalls(methodCalls)
                .build();
    }

    private BytecodeContext recursiveContext()
    {
        return new BytecodeContext.Builder()
                .heapManager(heapManager)
                .classResolver(classResolver)
                .mode(ExecutionMode.RECURSIVE)
                .maxCallDepth(maxCallDepth)
                .maxInstructions(maxInstructions)
                .build();
    }

    private VmValueConverter converter()
    {
        return new VmValueConverter(heapManager, classResolver, maxCallDepth, maxInstructions);
    }

    private final class CallTraceListener implements BytecodeListener
    {
        private final BytecodeEngine engine;
        private final List<MethodCall> methodCalls;
        private final Deque<MethodCall> open = new ArrayDeque<>();

        CallTraceListener(BytecodeEngine engine, List<MethodCall> methodCalls)
        {
            this.engine = engine;
            this.methodCalls = methodCalls;
        }

        @Override
        public void beforeInstruction(StackFrame frame, Instruction instruction)
        {
            int depth = engine.getCallStack().depth();
            if (depth <= open.size())
            {
                return;
            }
            StackFrame top = engine.getCallStack().peek();
            if (top == null)
            {
                return;
            }
            MethodEntry method = top.getMethod();
            boolean isStatic = (method.getAccess() & 0x0008) != 0;
            MethodCall call = new MethodCall(method.getOwnerName(), method.getName(), method.getDesc(), argumentsOf(top), isStatic, depth - 1);
            open.push(call);
            methodCalls.add(call);
        }

        @Override
        public void afterInstruction(StackFrame frame, Instruction instruction)
        {
            int depth = engine.getCallStack().depth();
            boolean innermost = true;
            while (open.size() > depth)
            {
                MethodCall call = open.pop();
                call.setEndTimeNanos(System.nanoTime());
                if (innermost && frame.getException() == null && frame.isCompleted())
                {
                    call.setReturnValue(returnValueOf(call.getDescriptor(), frame.getReturnValue()));
                }
                else
                {
                    call.setExceptional(true);
                }
                innermost = false;
            }
        }

        void finish(BytecodeResult result)
        {
            while (!open.isEmpty())
            {
                MethodCall call = open.pop();
                call.setEndTimeNanos(System.nanoTime());
                if (open.isEmpty() && result.isSuccess())
                {
                    call.setReturnValue(returnValueOf(call.getDescriptor(), result.getReturnValue()));
                }
                else
                {
                    call.setExceptional(true);
                }
            }
        }
    }

    /** Interrupts the running execution and stops the debug session if either is active. */
    public void interrupt()
    {
        if (currentEngine != null)
        {
            currentEngine.interrupt();
        }
        if (currentDebugSession != null && !currentDebugSession.isStopped())
        {
            currentDebugSession.stop();
        }
    }

    /**
     * Starts a debug session on a method, stopping any session still running, initializing the VM first if needed.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @param recursive true to step into callees, false to delegate calls
     * @param args the arguments, the receiver first for an instance method, converted to VM values of the declared types
     * @return the started session, also kept as the current session
     * @throws IllegalArgumentException if the class or method is not found or an argument cannot be converted
     * @throws IllegalStateException if another execution is running, or if the VM is not initialized and no project is loaded
     */
    public DebugSession createDebugSession(String className, String methodName, String descriptor, boolean recursive, Object... args)
    {
        ensureInitialized();

        if (executing.get())
        {
            throw new IllegalStateException("Another execution is in progress");
        }

        MethodEntry method = requireMethod(className, methodName, descriptor);

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

        ConcreteValue[] vmArgs = converter().toConcreteAll(args, frameTypes(method));
        currentDebugSession = new DebugSession(sessionContext);
        currentDebugSession.start(method, vmArgs);

        return currentDebugSession;
    }

    /**
     * Creates an isolated VM over a snapshot of the project's user classes, reusing the cached snapshot until the project's bytecode changes.
     *
     * @return the new VM
     * @throws IllegalStateException if no project is loaded
     */
    public synchronized VmInstance createSnapshotInstance()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            throw new IllegalStateException("No project loaded. Load a project before starting a VM session.");
        }
        long version = project.getBytecodeVersion();
        if (cachedFrozenClasses == null || cachedSnapshotVersion != version)
        {
            Map<String, byte[]> frozen = new HashMap<>();
            for (ClassEntryModel entry : project.getUserClasses())
            {
                try
                {
                    frozen.put(entry.getClassName(), entry.getClassFile().write());
                }
                catch (Exception e)
                {
                    ConsoleLogService.getInstance().warn("VM snapshot: " + entry.getClassName() + " could not be serialized and is read from the live project: " + e.getMessage());
                }
            }
            cachedFrozenClasses = frozen;
            cachedSnapshotVersion = version;
        }
        SnapshotClassPool pool = new SnapshotClassPool(cachedFrozenClasses, project.getClassPool());
        return new VmInstance(pool, maxCallDepth, maxInstructions);
    }

    /**
     * Finds a method in the VM's class pool, initializing the VM first if needed.
     *
     * @param className the class's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @return the method, or null if the class or method is not found
     * @throws IllegalStateException if the VM is not initialized and no project is loaded
     */
    public MethodEntry findMethod(String className, String methodName, String descriptor)
    {
        ensureInitialized();

        ClassFile classFile = classPool.get(className);
        if (classFile == null)
        {
            return null;
        }
        return findMethod(classFile, methodName, descriptor);
    }

    /**
     * Sets the call depth limit, applying it to the shared context if the VM is initialized.
     *
     * @param maxCallDepth the new limit
     */
    public void setMaxCallDepth(int maxCallDepth)
    {
        this.maxCallDepth = maxCallDepth;
        if (initialized.get())
        {
            rebuildContext();
        }
    }

    /**
     * Sets the instruction limit, applying it to the shared context if the VM is initialized.
     *
     * @param maxInstructions the new limit
     */
    public void setMaxInstructions(int maxInstructions)
    {
        this.maxInstructions = maxInstructions;
        if (initialized.get())
        {
            rebuildContext();
        }
    }

    /**
     * Describes the VM's state: initialization, execution, class and heap counts, limits and debug session state.
     *
     * @return a multi-line status report
     */
    public String getVMStatus()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("VM Status:\n");
        sb.append("  Initialized: ").append(initialized.get()).append("\n");
        sb.append("  Executing: ").append(executing.get()).append("\n");

        if (classPool != null)
        {
            sb.append("  Classes: ").append(classPool.getClasses().size()).append("\n");
        }

        if (heapManager != null)
        {
            sb.append("  Heap Objects: ").append(heapManager.objectCount()).append("\n");
        }

        sb.append("  Max Call Depth: ").append(maxCallDepth).append("\n");
        sb.append("  Max Instructions: ").append(maxInstructions).append("\n");

        if (currentDebugSession != null)
        {
            sb.append("  Debug Session: ").append(currentDebugSession.getState()).append("\n");
        }

        return sb.toString();
    }

    private void ensureInitialized()
    {
        if (!initialized.get())
        {
            initialize();
        }
    }

    private void rebuildContext()
    {
        this.context = new BytecodeContext.Builder()
                .heapManager(heapManager)
                .classResolver(classResolver)
                .maxCallDepth(maxCallDepth)
                .maxInstructions(maxInstructions)
                .build();
    }

    private MethodEntry findMethod(ClassFile classFile, String methodName, String descriptor)
    {
        return VmSupport.findMethod(classFile, methodName, descriptor);
    }

    private Object returnValueOf(String methodDescriptor, ConcreteValue value)
    {
        String type = DescriptorParser.returnDescriptor(methodDescriptor);
        return "V".equals(type) ? null : converter().toHost(value, type);
    }

    private ExecutionResult buildExecutionResult(BytecodeResult result, MethodEntry method, long executionTimeMs, List<MethodCall> methodCalls)
    {
        String returnType = DescriptorParser.returnDescriptor(method.getDesc());
        ExecutionResult.Builder builder = ExecutionResult.builder()
                .success(result.isSuccess())
                .returnType(returnType)
                .executionTimeMs(executionTimeMs)
                .instructionsExecuted(result.getInstructionsExecuted())
                .methodCalls(methodCalls);

        if (result.isSuccess() && !"V".equals(returnType))
        {
            builder.returnValue(converter().toHost(result.getReturnValue(), returnType));
        }
        if (result.hasException())
        {
            builder.exception(new RuntimeException("VM Exception: " + result.getException().toString()));
        }
        else if (!result.isSuccess())
        {
            builder.exception(new IllegalStateException("Execution stopped: " + result.getStatus()));
        }
        return builder.build();
    }

    private Object[] argumentsOf(StackFrame frame)
    {
        MethodEntry method = frame.getMethod();
        List<String> types = DescriptorParser.parameterDescriptors(method.getDesc());
        ConcreteLocals locals = frame.getLocals();
        Object[] args = new Object[types.size()];
        int slot = (method.getAccess() & 0x0008) != 0 ? 0 : 1;
        for (int i = 0; i < types.size(); i++)
        {
            String type = types.get(i);
            args[i] = converter().toHost(locals.get(slot), type);
            slot += type.equals("J") || type.equals("D") ? 2 : 1;
        }
        return args;
    }
}
