package com.tonic.ui.vm;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.resolve.ResolutionException;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.vm.model.HeapReference;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.ConstructorCall;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.FactoryCall;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.FieldInjection;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.ExpressionObject;
import com.tonic.util.DescriptorParser;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/** Converts host values to VM values of a declared type and back, building arrays and, when it has a resolver, objects described by object specs. */
public final class VmValueConverter
{

    private static final String PRIMITIVES = "ZBCSIJFD";

    private final SimpleHeapManager heap;
    private final ClassResolver resolver;
    private final int maxCallDepth;
    private final int maxInstructions;

    /**
     * Creates a converter that can also build objects by running their constructors or factory methods.
     *
     * @param heap the heap values are allocated in
     * @param resolver the resolver used to find and run constructors, factory methods and fields
     * @param maxCallDepth the call depth limit for constructor and factory runs
     * @param maxInstructions the instruction limit for constructor and factory runs
     */
    public VmValueConverter(SimpleHeapManager heap, ClassResolver resolver, int maxCallDepth, int maxInstructions)
    {
        this.heap = heap;
        this.resolver = resolver;
        this.maxCallDepth = maxCallDepth;
        this.maxInstructions = maxInstructions;
    }

    /**
     * Creates a converter for plain values only; object specs are rejected.
     *
     * @param heap the heap strings and arrays are allocated in
     */
    public VmValueConverter(SimpleHeapManager heap)
    {
        this(heap, null, 0, 0);
    }

    /**
     * Converts host values to VM values, each as its declared type.
     *
     * @param values the host values; may be null
     * @param types the declared type descriptors in the same order, or null to infer every type from its value
     * @return the VM values, empty for null or empty input
     * @throws IllegalArgumentException if types are given and their count differs from the values', or a value cannot be passed as its type
     * @throws IllegalStateException if building an object fails in the VM
     */
    public ConcreteValue[] toConcreteAll(Object[] values, List<String> types)
    {
        int count = values == null ? 0 : values.length;
        if (types != null && types.size() != count)
        {
            throw new IllegalArgumentException("Expected " + types.size() + " arguments but got " + count);
        }
        if (count == 0)
        {
            return new ConcreteValue[0];
        }
        ConcreteValue[] out = new ConcreteValue[values.length];
        for (int i = 0; i < values.length; i++)
        {
            out[i] = toConcrete(values[i], types != null ? types.get(i) : null);
        }
        return out;
    }

    /**
     * Converts a host value to a VM value: primitives are narrowed to the declared type, strings interned, host arrays copied into VM arrays, and constructor, factory and field-injection specs built in the VM.
     *
     * @param value the host value
     * @param type the declared type descriptor, or null to infer it from the value
     * @return the VM value
     * @throws IllegalArgumentException if the value cannot be passed as the type, such as null for a primitive, a boxed number for a reference, or an expression object
     * @throws IllegalStateException if building an object fails in the VM
     */
    public ConcreteValue toConcrete(Object value, String type)
    {
        if (value instanceof ConcreteValue)
        {
            return (ConcreteValue) value;
        }
        if (type != null && isPrimitive(type))
        {
            return primitive(value, type.charAt(0));
        }
        if (value == null)
        {
            return ConcreteValue.nullRef();
        }
        if (value instanceof ObjectInstance)
        {
            return ConcreteValue.reference((ObjectInstance) value);
        }
        if (value instanceof String)
        {
            return ConcreteValue.reference(heap.internString((String) value));
        }
        if (value.getClass().isArray())
        {
            return ConcreteValue.reference(array(value, type));
        }
        if (value instanceof ConstructorCall)
        {
            ConstructorCall call = (ConstructorCall) value;
            return ConcreteValue.reference(construct(call.getTypeName(), call.getDescriptor(), call.getArgs()));
        }
        if (value instanceof FactoryCall)
        {
            FactoryCall call = (FactoryCall) value;
            return ConcreteValue.reference(callFactory(call.getTypeName(), call.getMethodName(), call.getDescriptor(), call.getArgs()));
        }
        if (value instanceof FieldInjection)
        {
            FieldInjection injection = (FieldInjection) value;
            return ConcreteValue.reference(inject(injection.getTypeName(), injection.getFieldValues()));
        }
        if (value instanceof ExpressionObject)
        {
            throw new IllegalArgumentException("Cannot build " + value + " in the VM");
        }
        if (type == null)
        {
            return inferredPrimitive(value);
        }
        throw new IllegalArgumentException("Cannot pass " + value.getClass().getSimpleName() + " " + value + " as " + type);
    }

    /**
     * Converts a VM value to a host value of its declared type: primitives box to their Java type, strings decode, primitive and string arrays copy to host arrays, and other objects become heap references.
     *
     * @param value the VM value, or null
     * @param type the declared type descriptor, or null to infer it from the value's tag
     * @return the host value, null for a null reference or a missing value
     */
    public Object toHost(ConcreteValue value, String type)
    {
        if (value == null)
        {
            return null;
        }
        if (type != null && isPrimitive(type))
        {
            switch (type.charAt(0))
            {
                case 'Z':
                    return value.asInt() != 0;
                case 'B':
                    return (byte) value.asInt();
                case 'C':
                    return (char) value.asInt();
                case 'S':
                    return (short) value.asInt();
                case 'J':
                    return value.asLong();
                case 'F':
                    return value.asFloat();
                case 'D':
                    return value.asDouble();
                default:
                    return value.asInt();
            }
        }
        switch (value.getTag())
        {
            case INT:
                return value.asInt();
            case LONG:
                return value.asLong();
            case FLOAT:
                return value.asFloat();
            case DOUBLE:
                return value.asDouble();
            case REFERENCE:
                return hostReference(value.asReference());
            default:
                return null;
        }
    }

    /**
     * Allocates an object and runs one of its constructors.
     *
     * @param className the class's internal name, with slashes
     * @param descriptor the constructor's descriptor
     * @param args the constructor arguments as host values
     * @return the constructed object
     * @throws IllegalArgumentException if the class or constructor is not found or an argument cannot be passed
     * @throws IllegalStateException if the converter has no resolver or the constructor throws
     */
    public ObjectInstance construct(String className, String descriptor, Object[] args)
    {
        MethodEntry constructor = method(className, "<init>", descriptor);
        ConcreteValue[] params = toConcreteAll(args, DescriptorParser.parameterDescriptors(descriptor));
        ObjectInstance object = heap.newObject(className);
        ConcreteValue[] frame = new ConcreteValue[params.length + 1];
        frame[0] = ConcreteValue.reference(object);
        System.arraycopy(params, 0, frame, 1, params.length);
        run(className, constructor, frame);
        return object;
    }

    private ObjectInstance callFactory(String className, String methodName, String descriptor, Object[] args)
    {
        MethodEntry factory = method(className, methodName, descriptor);
        if ((factory.getAccess() & 0x0008) == 0)
        {
            throw new IllegalArgumentException("Factory method is not static: " + className + "." + methodName + descriptor);
        }
        BytecodeResult result = run(className, factory, toConcreteAll(args, DescriptorParser.parameterDescriptors(descriptor)));
        ConcreteValue returned = result.getReturnValue();
        if (returned == null || !returned.isReference())
        {
            throw new IllegalStateException("Factory method " + className + "." + methodName + descriptor + " did not return an object");
        }
        return returned.isNull() ? null : returned.asReference();
    }

    private ObjectInstance inject(String className, Map<String, Object> fieldValues)
    {
        requireResolver(className);
        ObjectInstance object = heap.newObject(className);
        for (Map.Entry<String, Object> entry : fieldValues.entrySet())
        {
            FieldEntry field = findField(className, entry.getKey());
            String descriptor = field.getDesc();
            object.setField(field.getOwnerName(), field.getName(), descriptor, storable(toConcrete(entry.getValue(), descriptor), descriptor));
        }
        return object;
    }

    private FieldEntry findField(String className, String fieldName)
    {
        String current = className;
        while (current != null)
        {
            ClassFile classFile = resolveClass(current);
            for (FieldEntry field : classFile.getFields())
            {
                if (field.getName().equals(fieldName) && (field.getAccess() & 0x0008) == 0)
                {
                    return field;
                }
            }
            current = classFile.getSuperClassName();
        }
        throw new IllegalArgumentException("Instance field not found: " + className + "." + fieldName);
    }

    private MethodEntry method(String className, String methodName, String descriptor)
    {
        requireResolver(className);
        MethodEntry method = VmSupport.findMethod(resolveClass(className), methodName, descriptor);
        if (method == null)
        {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        return method;
    }

    private ClassFile resolveClass(String className)
    {
        try
        {
            return resolver.resolveClass(className);
        }
        catch (ResolutionException e)
        {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }

    private void requireResolver(String className)
    {
        if (resolver == null)
        {
            throw new IllegalStateException("Cannot build " + className + " without a class resolver");
        }
    }

    private BytecodeResult run(String className, MethodEntry method, ConcreteValue[] args)
    {
        BytecodeContext context = new BytecodeContext.Builder().heapManager(heap).classResolver(resolver).mode(ExecutionMode.RECURSIVE).maxCallDepth(maxCallDepth).maxInstructions(maxInstructions).build();
        BytecodeEngine engine = new BytecodeEngine(context);
        engine.ensureClassInitialized(className);
        BytecodeResult result = engine.execute(method, args);
        if (!result.isSuccess())
        {
            String reason = result.hasException() ? String.valueOf(result.getException()) : String.valueOf(result.getStatus());
            throw new IllegalStateException(className + "." + method.getName() + method.getDesc() + " failed: " + reason);
        }
        return result;
    }

    private ConcreteValue primitive(Object value, char tag)
    {
        long bits;
        if (value instanceof Boolean)
        {
            bits = (Boolean) value ? 1 : 0;
        }
        else if (value instanceof Character)
        {
            bits = (Character) value;
        }
        else if (value instanceof Number)
        {
            Number number = (Number) value;
            if (tag == 'F')
            {
                return ConcreteValue.floatValue(number.floatValue());
            }
            if (tag == 'D')
            {
                return ConcreteValue.doubleValue(number.doubleValue());
            }
            bits = number.longValue();
        }
        else
        {
            throw new IllegalArgumentException("Cannot pass " + (value == null ? "null" : value.getClass().getSimpleName() + " " + value) + " as primitive " + tag);
        }
        switch (tag)
        {
            case 'Z':
                return ConcreteValue.intValue(bits != 0 ? 1 : 0);
            case 'B':
                return ConcreteValue.intValue((byte) bits);
            case 'C':
                return ConcreteValue.intValue((char) bits);
            case 'S':
                return ConcreteValue.intValue((short) bits);
            case 'J':
                return ConcreteValue.longValue(bits);
            case 'F':
                return ConcreteValue.floatValue(bits);
            case 'D':
                return ConcreteValue.doubleValue(bits);
            default:
                return ConcreteValue.intValue((int) bits);
        }
    }

    private static ConcreteValue inferredPrimitive(Object value)
    {
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
        if (value instanceof Number)
        {
            return ConcreteValue.intValue(((Number) value).intValue());
        }
        if (value instanceof Boolean)
        {
            return ConcreteValue.intValue((Boolean) value ? 1 : 0);
        }
        if (value instanceof Character)
        {
            return ConcreteValue.intValue((Character) value);
        }
        throw new IllegalArgumentException("Cannot pass " + value.getClass().getSimpleName() + " " + value + " to the VM");
    }

    private ArrayInstance array(Object hostArray, String type)
    {
        String component = type != null && type.startsWith("[") ? type.substring(1) : descriptorOf(hostArray.getClass().getComponentType());
        int length = Array.getLength(hostArray);
        ArrayInstance array = heap.newArray(component, length);
        for (int i = 0; i < length; i++)
        {
            ConcreteValue element = toConcrete(Array.get(hostArray, i), component);
            switch (component)
            {
                case "Z":
                    array.setBoolean(i, element.asInt() != 0);
                    break;
                case "B":
                    array.setByte(i, (byte) element.asInt());
                    break;
                case "C":
                    array.setChar(i, (char) element.asInt());
                    break;
                case "S":
                    array.setShort(i, (short) element.asInt());
                    break;
                case "I":
                    array.setInt(i, element.asInt());
                    break;
                case "J":
                    array.setLong(i, element.asLong());
                    break;
                case "F":
                    array.setFloat(i, element.asFloat());
                    break;
                case "D":
                    array.setDouble(i, element.asDouble());
                    break;
                default:
                    array.set(i, element.isNull() ? null : element.asReference());
                    break;
            }
        }
        return array;
    }

    private Object hostReference(ObjectInstance object)
    {
        if (object == null)
        {
            return null;
        }
        if (object instanceof ArrayInstance)
        {
            return hostArray((ArrayInstance) object);
        }
        if ("java/lang/String".equals(object.getClassName()))
        {
            return heap.extractString(object);
        }
        return new HeapReference(object.getClassName(), object.getId());
    }

    private Object hostArray(ArrayInstance array)
    {
        String component = array.getComponentType();
        Class<?> hostComponent = hostClassOf(component);
        Object host = Array.newInstance(hostComponent, array.getLength());
        for (int i = 0; i < array.getLength(); i++)
        {
            Object element = array.get(i);
            if (element == null || hostComponent.isPrimitive())
            {
                Array.set(host, i, element);
            }
            else
            {
                Array.set(host, i, hostReference((ObjectInstance) element));
            }
        }
        return host;
    }

    private static Class<?> hostClassOf(String descriptor)
    {
        switch (descriptor)
        {
            case "Z":
                return boolean.class;
            case "B":
                return byte.class;
            case "C":
                return char.class;
            case "S":
                return short.class;
            case "I":
                return int.class;
            case "J":
                return long.class;
            case "F":
                return float.class;
            case "D":
                return double.class;
            case "Ljava/lang/String;":
                return String.class;
            default:
                return Object.class;
        }
    }

    private static String descriptorOf(Class<?> type)
    {
        if (type.isPrimitive())
        {
            return String.valueOf(PRIMITIVES.charAt(List.of(boolean.class, byte.class, char.class, short.class, int.class, long.class, float.class, double.class).indexOf(type)));
        }
        if (type.isArray())
        {
            return type.getName().replace('.', '/');
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static Object storable(ConcreteValue value, String descriptor)
    {
        if (!isPrimitive(descriptor))
        {
            return value.isNull() ? null : value.asReference();
        }
        switch (descriptor.charAt(0))
        {
            case 'Z':
                return value.asInt() != 0;
            case 'B':
                return (byte) value.asInt();
            case 'C':
                return (char) value.asInt();
            case 'S':
                return (short) value.asInt();
            case 'J':
                return value.asLong();
            case 'F':
                return value.asFloat();
            case 'D':
                return value.asDouble();
            default:
                return value.asInt();
        }
    }

    private static boolean isPrimitive(String type)
    {
        return type.length() == 1 && PRIMITIVES.indexOf(type.charAt(0)) >= 0;
    }
}
