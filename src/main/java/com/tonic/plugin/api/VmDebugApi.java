package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Drives JStudio's bytecode interpreter / debugger from a plugin: execute a method with caller-defined arguments
 * and single-step through it, observing the operand stack, locals, and call stack at each step. The VM is an
 * isolated interpreter over the loaded project's classes (its own heap; no real JVM/IO side effects).
 * <p>
 * Each {@link #start} creates a NEW isolated session - its own heap over a defensive snapshot of the project's
 * bytecode - and returns its {@code handle}; pass that handle to {@link #step}/{@link #current}/{@link #stop}.
 * Independent handles run independently, so concurrent callers (e.g. subagents) don't interfere with each other or
 * with the Bytecode Debugger UI. Call off the EDT (stepping can be slow).
 */
public interface VmDebugApi {

    /**
     * Starts a NEW isolated debug session at {@code className.methodName descriptor} with the given arguments and
     * pauses at the first instruction. {@code recursive} lets the interpreter execute called methods (needed to step
     * into calls and for recursion). Returns the initial {@link DebugState}, whose {@code handle} addresses this
     * session in subsequent calls.
     */
    DebugState start(String className, String methodName, String descriptor, List<ArgSpec> args, boolean recursive);

    /** Advances the session for {@code handle} one step and returns the new state (terminal state if it finished). */
    DebugState step(String handle, StepMode mode);

    /** The current state of {@code handle} without stepping. */
    DebugState current(String handle);

    /** True if the session for {@code handle} is running and paused (steppable). */
    boolean isActive(String handle);

    /** Ends and disposes the session for {@code handle}. */
    void stop(String handle);

    enum StepMode {
        /** Execute one instruction, descending into any call. */
        INTO,
        /** Execute one instruction, running any call to completion without pausing inside it. */
        OVER,
        /** Run until the current frame returns. */
        OUT
    }

    @Getter
    final class DebugState {
        private final String handle;
        private final boolean active;
        private final boolean terminated;
        private final DebugResult result;
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final int line;
        private final List<StackSlot> operandStack;
        private final List<Local> locals;
        private final List<Frame> callStack;

        public DebugState(String handle, boolean active, boolean terminated, DebugResult result, String className,
                          String methodName, String descriptor, int pc, int line,
                          List<StackSlot> operandStack, List<Local> locals, List<Frame> callStack) {
            this.handle = handle;
            this.active = active;
            this.terminated = terminated;
            this.result = result;
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
            this.line = line;
            this.operandStack = operandStack;
            this.locals = locals;
            this.callStack = callStack;
        }
    }

    @Getter
    final class StackSlot {
        private final int index;
        private final String value;
        private final String type;
        private final boolean wide;

        public StackSlot(int index, String value, String type, boolean wide) {
            this.index = index;
            this.value = value;
            this.type = type;
            this.wide = wide;
        }
    }

    @Getter
    final class Local {
        private final int slot;
        private final String name;
        private final String type;
        private final String value;

        public Local(int slot, String name, String type, String value) {
            this.slot = slot;
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    @Getter
    final class Frame {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final int line;
        private final boolean current;

        public Frame(String className, String methodName, String descriptor, int pc, int line, boolean current) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
            this.line = line;
            this.current = current;
        }
    }

    @Getter
    final class DebugResult {
        private final boolean success;
        private final String returnValue;
        private final String exception;
        private final long instructionsExecuted;

        public DebugResult(boolean success, String returnValue, String exception, long instructionsExecuted) {
            this.success = success;
            this.returnValue = returnValue;
            this.exception = exception;
            this.instructionsExecuted = instructionsExecuted;
        }
    }

    /**
     * A method-argument specification, built by the caller. Primitives/strings/null are direct; arrays and objects
     * are constructed on the VM heap (objects via a constructor, or by setting fields directly). Recursive: array
     * elements, constructor args, and field values are themselves {@code ArgSpec}s.
     */
    @Getter
    final class ArgSpec {
        public enum Kind { INT, LONG, FLOAT, DOUBLE, BOOLEAN, BYTE, SHORT, CHAR, STRING, NULL, ARRAY, OBJECT }

        private final Kind kind;
        private final Object value;
        private final String componentType;
        private final List<ArgSpec> elements;
        private final String className;
        private final String constructorDescriptor;
        private final List<ArgSpec> constructorArgs;
        private final Map<String, ArgSpec> fields;

        private ArgSpec(Kind kind, Object value, String componentType, List<ArgSpec> elements, String className,
                        String constructorDescriptor, List<ArgSpec> constructorArgs, Map<String, ArgSpec> fields) {
            this.kind = kind;
            this.value = value;
            this.componentType = componentType;
            this.elements = elements;
            this.className = className;
            this.constructorDescriptor = constructorDescriptor;
            this.constructorArgs = constructorArgs;
            this.fields = fields;
        }

        /** A primitive value (boxed in {@code value}) of the given primitive {@link Kind}. */
        public static ArgSpec primitive(Kind kind, Object value) {
            return new ArgSpec(kind, value, null, null, null, null, null, null);
        }

        public static ArgSpec string(String value) {
            return new ArgSpec(Kind.STRING, value, null, null, null, null, null, null);
        }

        public static ArgSpec nullRef() {
            return new ArgSpec(Kind.NULL, null, null, null, null, null, null, null);
        }

        /** An array of {@code componentType} (JVM descriptor, e.g. "I" or "Ljava/lang/String;") with {@code elements}. */
        public static ArgSpec array(String componentType, List<ArgSpec> elements) {
            return new ArgSpec(Kind.ARRAY, null, componentType, elements, null, null, null, null);
        }

        /** An object built by running {@code className}'s constructor {@code constructorDescriptor} with {@code args}. */
        public static ArgSpec object(String className, String constructorDescriptor, List<ArgSpec> constructorArgs) {
            return new ArgSpec(Kind.OBJECT, null, null, null, className, constructorDescriptor, constructorArgs, null);
        }

        /** An object allocated and populated by setting {@code fields} directly (no constructor run). */
        public static ArgSpec objectFields(String className, Map<String, ArgSpec> fields) {
            return new ArgSpec(Kind.OBJECT, null, null, null, className, null, null, fields);
        }
    }
}
