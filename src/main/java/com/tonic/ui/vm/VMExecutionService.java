package com.tonic.ui.vm;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.debug.DebugSession;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.StatusMessageEvent;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.MethodCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VMExecutionService {

    private static final VMExecutionService INSTANCE = new VMExecutionService();

    private ClassPool classPool;
    private ClassResolver classResolver;
    private SimpleHeapManager heapManager;
    private BytecodeContext context;
    private BytecodeEngine currentEngine;
    private DebugSession currentDebugSession;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean executing = new AtomicBoolean(false);

    private int maxCallDepth = 1000;
    private int maxInstructions = 10_000_000;

    private VMExecutionService() {
    }

    public static VMExecutionService getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized.get()) {
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
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

    public synchronized void shutdown() {
        if (!initialized.get()) {
            return;
        }

        if (currentEngine != null) {
            currentEngine.interrupt();
            currentEngine = null;
        }

        if (currentDebugSession != null) {
            if (!currentDebugSession.isStopped()) {
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

    public synchronized void reset() {
        shutdown();
        initialize();
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isExecuting() {
        return executing.get();
    }

    public ExecutionResult executeStaticMethod(String className, String methodName, String descriptor, Object... args) {
        ensureInitialized();

        if (executing.get()) {
            return ExecutionResult.builder()
                .success(false)
                .exception(new IllegalStateException("Another execution is in progress"))
                .build();
        }

        executing.set(true);
        long startTime = System.currentTimeMillis();

        try {
            ClassFile classFile = classPool.get(className);
            if (classFile == null) {
                throw new IllegalArgumentException("Class not found: " + className);
            }

            MethodEntry method = findMethod(classFile, methodName, descriptor);
            if (method == null) {
                throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
            }

            int access = method.getAccess();
            if ((access & 0x0008) == 0) {
                throw new IllegalArgumentException("Method is not static: " + methodName);
            }

            ConcreteValue[] vmArgs = convertToConcreteValues(args);
            BytecodeEngine engine = new BytecodeEngine(context);
            currentEngine = engine;

            BytecodeResult result = engine.execute(method, vmArgs);

            long endTime = System.currentTimeMillis();

            return buildExecutionResult(result, endTime - startTime);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            return ExecutionResult.builder()
                .success(false)
                .exception(e)
                .executionTimeMs(endTime - startTime)
                .build();
        } finally {
            currentEngine = null;
            executing.set(false);
        }
    }

    public ExecutionResult executeMethod(String className, String methodName, String descriptor,
                                         Object receiver, Object... args) {
        ensureInitialized();

        if (executing.get()) {
            return ExecutionResult.builder()
                .success(false)
                .exception(new IllegalStateException("Another execution is in progress"))
                .build();
        }

        executing.set(true);
        long startTime = System.currentTimeMillis();

        try {
            ClassFile classFile = classPool.get(className);
            if (classFile == null) {
                throw new IllegalArgumentException("Class not found: " + className);
            }

            MethodEntry method = findMethod(classFile, methodName, descriptor);
            if (method == null) {
                throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
            }

            int access = method.getAccess();
            if ((access & 0x0008) != 0) {
                throw new IllegalArgumentException("Method is static, use executeStaticMethod instead");
            }

            ConcreteValue[] allArgs = new ConcreteValue[args.length + 1];
            allArgs[0] = convertToConcreteValue(receiver);
            for (int i = 0; i < args.length; i++) {
                allArgs[i + 1] = convertToConcreteValue(args[i]);
            }

            BytecodeEngine engine = new BytecodeEngine(context);
            currentEngine = engine;

            BytecodeResult result = engine.execute(method, allArgs);

            long endTime = System.currentTimeMillis();

            return buildExecutionResult(result, endTime - startTime);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            return ExecutionResult.builder()
                .success(false)
                .exception(e)
                .executionTimeMs(endTime - startTime)
                .build();
        } finally {
            currentEngine = null;
            executing.set(false);
        }
    }

    public ExecutionResult traceStaticMethod(String className, String methodName, String descriptor, Object... args) {
        ensureInitialized();

        if (executing.get()) {
            return ExecutionResult.builder()
                .success(false)
                .exception(new IllegalStateException("Another execution is in progress"))
                .build();
        }

        executing.set(true);
        long startTime = System.currentTimeMillis();
        List<MethodCall> methodCalls = new ArrayList<>();

        try {
            ClassFile classFile = classPool.get(className);
            if (classFile == null) {
                throw new IllegalArgumentException("Class not found: " + className);
            }

            MethodEntry method = findMethod(classFile, methodName, descriptor);
            if (method == null) {
                throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
            }

            ConcreteValue[] vmArgs = convertToConcreteValues(args);

            BytecodeEngine engine = new BytecodeEngine(context);
            currentEngine = engine;

            BytecodeResult result = engine.execute(method, vmArgs);

            long endTime = System.currentTimeMillis();

            return buildExecutionResult(result, endTime - startTime, methodCalls);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            return ExecutionResult.builder()
                .success(false)
                .exception(e)
                .executionTimeMs(endTime - startTime)
                .methodCalls(methodCalls)
                .build();
        } finally {
            currentEngine = null;
            executing.set(false);
        }
    }

    public void interrupt() {
        if (currentEngine != null) {
            currentEngine.interrupt();
        }
        if (currentDebugSession != null && !currentDebugSession.isStopped()) {
            currentDebugSession.stop();
        }
    }

    public DebugSession createDebugSession(String className, String methodName, String descriptor,
                                            boolean recursive, Object... args) {
        ensureInitialized();

        ClassFile classFile = classPool.get(className);
        if (classFile == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }

        MethodEntry method = findMethod(classFile, methodName, descriptor);
        if (method == null) {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }

        if (currentDebugSession != null && !currentDebugSession.isStopped()) {
            currentDebugSession.stop();
        }

        BytecodeContext sessionContext = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .mode(recursive ?
                com.tonic.analysis.execution.core.ExecutionMode.RECURSIVE :
                com.tonic.analysis.execution.core.ExecutionMode.DELEGATED)
            .maxCallDepth(maxCallDepth)
            .maxInstructions(maxInstructions)
            .build();

        currentDebugSession = new DebugSession(sessionContext);
        ConcreteValue[] vmArgs = convertToConcreteValues(args);
        currentDebugSession.start(method, vmArgs);

        return currentDebugSession;
    }

    public DebugSession getCurrentDebugSession() {
        return currentDebugSession;
    }

    public MethodEntry findMethod(String className, String methodName, String descriptor) {
        ensureInitialized();

        ClassFile classFile = classPool.get(className);
        if (classFile == null) {
            return null;
        }
        return findMethod(classFile, methodName, descriptor);
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    public ClassResolver getClassResolver() {
        return classResolver;
    }

    public void setMaxCallDepth(int maxCallDepth) {
        this.maxCallDepth = maxCallDepth;
        if (initialized.get()) {
            rebuildContext();
        }
    }

    public void setMaxInstructions(int maxInstructions) {
        this.maxInstructions = maxInstructions;
        if (initialized.get()) {
            rebuildContext();
        }
    }

    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    public int getMaxInstructions() {
        return maxInstructions;
    }

    public String getVMStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("VM Status:\n");
        sb.append("  Initialized: ").append(initialized.get()).append("\n");
        sb.append("  Executing: ").append(executing.get()).append("\n");

        if (classPool != null) {
            sb.append("  Classes: ").append(classPool.getClasses().size()).append("\n");
        }

        if (heapManager != null) {
            sb.append("  Heap Objects: ").append(heapManager.objectCount()).append("\n");
        }

        sb.append("  Max Call Depth: ").append(maxCallDepth).append("\n");
        sb.append("  Max Instructions: ").append(maxInstructions).append("\n");

        if (currentDebugSession != null) {
            sb.append("  Debug Session: ").append(currentDebugSession.getState()).append("\n");
        }

        return sb.toString();
    }

    private void ensureInitialized() {
        if (!initialized.get()) {
            initialize();
        }
    }

    private void rebuildContext() {
        this.context = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .maxCallDepth(maxCallDepth)
            .maxInstructions(maxInstructions)
            .build();
    }

    private MethodEntry findMethod(ClassFile classFile, String methodName, String descriptor) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(methodName)) {
                if (descriptor == null || descriptor.isEmpty() || method.getDesc().equals(descriptor)) {
                    return method;
                }
            }
        }
        return null;
    }

    private ConcreteValue[] convertToConcreteValues(Object[] args) {
        if (args == null || args.length == 0) {
            return new ConcreteValue[0];
        }

        ConcreteValue[] result = new ConcreteValue[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = convertToConcreteValue(args[i]);
        }
        return result;
    }

    private ConcreteValue convertToConcreteValue(Object value) {
        if (value == null) {
            return ConcreteValue.nullRef();
        }

        if (value instanceof Integer) {
            return ConcreteValue.intValue((Integer) value);
        } else if (value instanceof Long) {
            return ConcreteValue.longValue((Long) value);
        } else if (value instanceof Float) {
            return ConcreteValue.floatValue((Float) value);
        } else if (value instanceof Double) {
            return ConcreteValue.doubleValue((Double) value);
        } else if (value instanceof Boolean) {
            return ConcreteValue.intValue((Boolean) value ? 1 : 0);
        } else if (value instanceof Byte) {
            return ConcreteValue.intValue((Byte) value);
        } else if (value instanceof Short) {
            return ConcreteValue.intValue((Short) value);
        } else if (value instanceof Character) {
            return ConcreteValue.intValue((Character) value);
        } else if (value instanceof String) {
            return ConcreteValue.reference(heapManager.internString((String) value));
        } else {
            return ConcreteValue.nullRef();
        }
    }

    private Object convertFromConcreteValue(ConcreteValue value) {
        if (value == null || value.isNull()) {
            return null;
        }

        switch (value.getTag()) {
            case INT:
                return value.asInt();
            case LONG:
                return value.asLong();
            case FLOAT:
                return value.asFloat();
            case DOUBLE:
                return value.asDouble();
            case REFERENCE:
                return value.asReference().toString();
            default:
                return value.toString();
        }
    }

    private ExecutionResult buildExecutionResult(BytecodeResult result, long executionTimeMs) {
        return buildExecutionResult(result, executionTimeMs, null);
    }

    private ExecutionResult buildExecutionResult(BytecodeResult result, long executionTimeMs, List<MethodCall> methodCalls) {
        boolean success = result.isSuccess();
        Object returnValue = null;
        String returnType = "V";
        Throwable exception = null;

        if (result.isSuccess() && result.getReturnValue() != null) {
            returnValue = convertFromConcreteValue(result.getReturnValue());
            returnType = result.getReturnValue().getTag().name();
        }

        if (result.hasException()) {
            exception = new RuntimeException("VM Exception: " + result.getException().toString());
        }

        ExecutionResult.Builder builder = ExecutionResult.builder()
            .success(success)
            .returnValue(returnValue)
            .returnType(returnType)
            .executionTimeMs(executionTimeMs)
            .instructionsExecuted(result.getInstructionsExecuted());

        if (exception != null) {
            builder.exception(exception);
        }

        if (methodCalls != null) {
            builder.methodCalls(methodCalls);
        }

        return builder.build();
    }
}
