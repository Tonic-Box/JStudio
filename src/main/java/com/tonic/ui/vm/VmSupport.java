package com.tonic.ui.vm;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/**
 * Shared method-lookup and argument-conversion helpers for the bytecode VM, used by both the default
 * {@link VMExecutionService} path and the isolated per-AI {@link VmInstance} path (one definition, no duplication).
 */
public final class VmSupport {

    private VmSupport() {
    }

    public static MethodEntry findMethod(ClassFile classFile, String methodName, String descriptor) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(methodName)
                    && (descriptor == null || descriptor.isEmpty() || method.getDesc().equals(descriptor))) {
                return method;
            }
        }
        return null;
    }

    public static ConcreteValue[] toConcreteValues(SimpleHeapManager heapManager, Object[] args) {
        if (args == null || args.length == 0) {
            return new ConcreteValue[0];
        }
        ConcreteValue[] result = new ConcreteValue[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = toConcreteValue(heapManager, args[i]);
        }
        return result;
    }

    public static ConcreteValue toConcreteValue(SimpleHeapManager heapManager, Object value) {
        if (value == null) {
            return ConcreteValue.nullRef();
        }
        if (value instanceof Integer) {
            return ConcreteValue.intValue((Integer) value);
        }
        if (value instanceof Long) {
            return ConcreteValue.longValue((Long) value);
        }
        if (value instanceof Float) {
            return ConcreteValue.floatValue((Float) value);
        }
        if (value instanceof Double) {
            return ConcreteValue.doubleValue((Double) value);
        }
        if (value instanceof Boolean) {
            return ConcreteValue.intValue((Boolean) value ? 1 : 0);
        }
        if (value instanceof Byte) {
            return ConcreteValue.intValue((Byte) value);
        }
        if (value instanceof Short) {
            return ConcreteValue.intValue((Short) value);
        }
        if (value instanceof Character) {
            return ConcreteValue.intValue((Character) value);
        }
        if (value instanceof String) {
            return ConcreteValue.reference(heapManager.internString((String) value));
        }
        if (value instanceof ConcreteValue) {
            return (ConcreteValue) value;
        }
        if (value instanceof ObjectInstance) {
            return ConcreteValue.reference((ObjectInstance) value);
        }
        return ConcreteValue.nullRef();
    }
}
