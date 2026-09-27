package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/** Runs and single-steps methods in JStudio's bytecode interpreter, each session isolated on its own heap over a snapshot of the project; handles belong to the instance that issued them, so keep one instance, and call it off the EDT because stepping can be slow. */
public interface VmDebugApi
{

    /**
     * Starts a new session paused at the method's first instruction; at most 16 sessions live per instance, and starting a 17th stops the oldest.
     *
     * @param className the declaring class's internal name, with slashes
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor, or null or empty to take the first method with the name
     * @param args the arguments in order, or null for none; a spec that cannot be built throws IllegalArgumentException
     * @param recursive true to interpret called methods so steps can go into them, false to delegate calls
     * @return the initial state, whose handle addresses the session from then on
     * @throws IllegalArgumentException if the method is not found
     */
    DebugState start(String className, String methodName, String descriptor, List<ArgSpec> args, boolean recursive);

    /**
     * Advances a session by one step.
     *
     * @param handle the session's handle
     * @param mode how far to step
     * @return the new state, terminated with a result when the method finished, or an inactive empty state when the handle is unknown or was passed to stop
     */
    DebugState step(String handle, StepMode mode);

    /**
     * Returns a session's state without stepping.
     *
     * @param handle the session's handle
     * @return the state, or an inactive empty state when the handle is unknown or was passed to stop
     */
    DebugState current(String handle);

    /**
     * Reports whether a session can still be stepped.
     *
     * @param handle the session's handle
     * @return true when the session is started and not finished
     */
    boolean isActive(String handle);

    /**
     * Ends a session and frees its VM; an unknown handle is ignored, so it is safe to call more than once.
     *
     * @param handle the session's handle
     */
    void stop(String handle);

    /** How far a step advances. */
    enum StepMode
    {
        /** Execute one instruction, descending into any call. */
        INTO,
        /** Execute one instruction, running any call to completion without pausing inside it. */
        OVER,
        /** Run until the current frame returns. */
        OUT
    }

    /** A snapshot of a session: its handle, whether it is active or terminated, the result once terminated, and the current method, pc, line, operand stack, locals and call stack; an inactive state has empty strings, -1 and empty lists. */
    @Getter
    final class DebugState
    {
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

        /**
         * Creates a state.
         *
         * @param handle the session's handle
         * @param active whether the session can still be stepped
         * @param terminated whether the method has finished
         * @param result the outcome once terminated, otherwise null
         * @param className the current method's class
         * @param methodName the current method's name
         * @param descriptor the current method's descriptor
         * @param pc the current instruction index, or -1
         * @param line the current source line, or -1
         * @param operandStack the operand stack, bottom first
         * @param locals the local variables
         * @param callStack the call frames
         */
        public DebugState(String handle, boolean active, boolean terminated, DebugResult result, String className, String methodName, String descriptor, int pc, int line, List<StackSlot> operandStack, List<Local> locals, List<Frame> callStack)
        {
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

    /** One operand-stack entry: its index, its value and type as text, and whether it takes two slots. */
    @Getter
    final class StackSlot
    {
        private final int index;
        private final String value;
        private final String type;
        private final boolean wide;

        /**
         * Creates an entry.
         *
         * @param index its position on the stack
         * @param value the value as text
         * @param type the type name
         * @param wide true for a long or double
         */
        public StackSlot(int index, String value, String type, boolean wide)
        {
            this.index = index;
            this.value = value;
            this.type = type;
            this.wide = wide;
        }
    }

    /** One local variable: its slot, name, type and value as text. */
    @Getter
    final class Local
    {
        private final int slot;
        private final String name;
        private final String type;
        private final String value;

        /**
         * Creates a local.
         *
         * @param slot its local-variable slot
         * @param name its name, from debug info when present
         * @param type the type name
         * @param value the value as text
         */
        public Local(int slot, String name, String type, String value)
        {
            this.slot = slot;
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    /** One call-stack frame: its method, pc, line, and whether it is the current frame. */
    @Getter
    final class Frame
    {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final int line;
        private final boolean current;

        /**
         * Creates a frame.
         *
         * @param className the method's class
         * @param methodName the method's name
         * @param descriptor the method's descriptor
         * @param pc the instruction index in the frame
         * @param line the source line, or -1
         * @param current true for the innermost frame
         */
        public Frame(String className, String methodName, String descriptor, int pc, int line, boolean current)
        {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
            this.line = line;
            this.current = current;
        }
    }

    /** How a finished session ended: success, the return value or thrown exception as text, and how many instructions ran. */
    @Getter
    final class DebugResult
    {
        private final boolean success;
        private final String returnValue;
        private final String exception;
        private final long instructionsExecuted;

        /**
         * Creates a result.
         *
         * @param success whether the method returned normally
         * @param returnValue the return value as text, or null for void or when it threw
         * @param exception the thrown exception as text, or null when it returned
         * @param instructionsExecuted how many instructions ran
         */
        public DebugResult(boolean success, String returnValue, String exception, long instructionsExecuted)
        {
            this.success = success;
            this.returnValue = returnValue;
            this.exception = exception;
            this.instructionsExecuted = instructionsExecuted;
        }
    }

    /** A method argument to build in the VM: a primitive, string or null directly, or an array or object built on the VM heap, with nested elements, constructor arguments and fields as ArgSpecs too. */
    @Getter
    final class ArgSpec
    {
        /** What kind of value an ArgSpec builds. */
        public enum Kind
        {INT, LONG, FLOAT, DOUBLE, BOOLEAN, BYTE, SHORT, CHAR, STRING, NULL, ARRAY, OBJECT}

        private final Kind kind;
        private final Object value;
        private final String componentType;
        private final List<ArgSpec> elements;
        private final String className;
        private final String constructorDescriptor;
        private final List<ArgSpec> constructorArgs;
        private final Map<String, ArgSpec> fields;

        private ArgSpec(Kind kind, Object value, String componentType, List<ArgSpec> elements, String className, String constructorDescriptor, List<ArgSpec> constructorArgs, Map<String, ArgSpec> fields)
        {
            this.kind = kind;
            this.value = value;
            this.componentType = componentType;
            this.elements = elements;
            this.className = className;
            this.constructorDescriptor = constructorDescriptor;
            this.constructorArgs = constructorArgs;
            this.fields = fields;
        }

        /**
         * Specifies a primitive value.
         *
         * @param kind one of the primitive kinds
         * @param value a Number for numeric kinds; for BOOLEAN a Boolean, a Number where non-zero is true, or text; for CHAR a Character, a Number or the first character of text
         * @return the spec
         */
        public static ArgSpec primitive(Kind kind, Object value)
        {
            return new ArgSpec(kind, value, null, null, null, null, null, null);
        }

        /**
         * Specifies a string, interned on the VM heap.
         *
         * @param value the text, or null for a null reference
         * @return the spec
         */
        public static ArgSpec string(String value)
        {
            return new ArgSpec(Kind.STRING, value, null, null, null, null, null, null);
        }

        /**
         * Specifies a null reference.
         *
         * @return the spec
         */
        public static ArgSpec nullRef()
        {
            return new ArgSpec(Kind.NULL, null, null, null, null, null, null, null);
        }

        /**
         * Specifies an array.
         *
         * @param componentType the element type's descriptor, such as I or Ljava/lang/String;
         * @param elements the elements in order, or null for an empty array
         * @return the spec
         */
        public static ArgSpec array(String componentType, List<ArgSpec> elements)
        {
            return new ArgSpec(Kind.ARRAY, null, componentType, elements, null, null, null, null);
        }

        /**
         * Specifies an object built by running one of its constructors in the VM; a constructor that throws fails start with IllegalArgumentException.
         *
         * @param className the class's internal name
         * @param constructorDescriptor the constructor's descriptor, or null to allocate without running one
         * @param constructorArgs the constructor's arguments, or null for none
         * @return the spec
         */
        public static ArgSpec object(String className, String constructorDescriptor, List<ArgSpec> constructorArgs)
        {
            return new ArgSpec(Kind.OBJECT, null, null, null, className, constructorDescriptor, constructorArgs, null);
        }

        /**
         * Specifies an object allocated without a constructor and filled by setting fields directly; a field declared on a superclass is found and stored under the class that declares it.
         *
         * @param className the class's internal name
         * @param fields the field values by field name
         * @return the spec
         */
        public static ArgSpec objectFields(String className, Map<String, ArgSpec> fields)
        {
            return new ArgSpec(Kind.OBJECT, null, null, null, className, null, null, fields);
        }
    }
}
