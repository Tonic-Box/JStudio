package com.tonic.ui.vm;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/** Method lookup and argument conversion shared by VMExecutionService and VmInstance. */
public final class VmSupport
{

    private VmSupport()
    {
    }

    /**
     * Finds a method by name and descriptor.
     *
     * @param classFile the class to search
     * @param methodName the method name
     * @param descriptor the method descriptor, or null or empty to take the first method with that name
     * @return the method, or null if none matches
     */
    public static MethodEntry findMethod(ClassFile classFile, String methodName, String descriptor)
    {
        for (MethodEntry method : classFile.getMethods())
        {
            if (method.getName().equals(methodName)
                    && (descriptor == null || descriptor.isEmpty() || method.getDesc().equals(descriptor)))
            {
                return method;
            }
        }
        return null;
    }

    /**
     * Converts host values to VM values.
     *
     * @param heapManager the heap strings are interned into
     * @param args the host values; may be null
     * @return the VM values, empty for null or empty input
     */
    public static ConcreteValue[] toConcreteValues(SimpleHeapManager heapManager, Object[] args)
    {
        if (args == null || args.length == 0)
        {
            return new ConcreteValue[0];
        }
        ConcreteValue[] result = new ConcreteValue[args.length];
        for (int i = 0; i < args.length; i++)
        {
            result[i] = toConcreteValue(heapManager, args[i]);
        }
        return result;
    }

    /**
     * Converts a host value to a VM value: boxed primitives become ints, longs, floats or doubles, strings are interned, VM values and objects pass through.
     *
     * @param heapManager the heap strings are interned into
     * @param value the host value
     * @return the VM value; a null reference for null or any unsupported type
     */
    public static ConcreteValue toConcreteValue(SimpleHeapManager heapManager, Object value)
    {
        if (value == null)
        {
            return ConcreteValue.nullRef();
        }
        if (value instanceof Integer)
        {
            return ConcreteValue.intValue((Integer) value);
        }
        if (value instanceof Long)
        {
            return ConcreteValue.longValue((Long) value);
        }
        if (value instanceof Float)
        {
            return ConcreteValue.floatValue((Float) value);
        }
        if (value instanceof Double)
        {
            return ConcreteValue.doubleValue((Double) value);
        }
        if (value instanceof Boolean)
        {
            return ConcreteValue.intValue((Boolean) value ? 1 : 0);
        }
        if (value instanceof Byte)
        {
            return ConcreteValue.intValue((Byte) value);
        }
        if (value instanceof Short)
        {
            return ConcreteValue.intValue((Short) value);
        }
        if (value instanceof Character)
        {
            return ConcreteValue.intValue((Character) value);
        }
        if (value instanceof String)
        {
            return ConcreteValue.reference(heapManager.internString((String) value));
        }
        if (value instanceof ConcreteValue)
        {
            return (ConcreteValue) value;
        }
        if (value instanceof ObjectInstance)
        {
            return ConcreteValue.reference((ObjectInstance) value);
        }
        return ConcreteValue.nullRef();
    }
}
