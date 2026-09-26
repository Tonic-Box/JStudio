package com.tonic.script.engine;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An immutable runtime value of the script interpreter: a type tag and the Java value behind it; objects and arrays wrap mutable Java collections. */
@Getter
public class ScriptValue
{

    /** The script value types. */
    public enum Type
    {
        NULL,
        BOOLEAN,
        NUMBER,
        STRING,
        FUNCTION,
        OBJECT,
        ARRAY,
        NATIVE
    }

    private final Type type;
    private final Object value;

    private ScriptValue(Type type, Object value)
    {
        this.type = type;
        this.value = value;
    }

    public static final ScriptValue NULL = new ScriptValue(Type.NULL, null);
    public static final ScriptValue TRUE = new ScriptValue(Type.BOOLEAN, true);
    public static final ScriptValue FALSE = new ScriptValue(Type.BOOLEAN, false);

    /**
     * Converts a Java value to the matching script value: null, booleans, numbers, strings, functions, lists and maps, with anything else wrapped as native.
     *
     * @param value the Java value, or null
     * @return the script value; a script value is returned as is
     */
    @SuppressWarnings("unchecked")
    public static ScriptValue of(Object value)
    {
        if (value == null) return NULL;
        if (value instanceof ScriptValue) return (ScriptValue) value;
        if (value instanceof Boolean) return (Boolean) value ? TRUE : FALSE;
        if (value instanceof Number) return number(((Number) value).doubleValue());
        if (value instanceof String) return string((String) value);
        if (value instanceof ScriptFunction) return function((ScriptFunction) value);
        if (value instanceof List) return array((List<?>) value);
        if (value instanceof Map) return object((Map<String, ScriptValue>) value);
        return native_(value);
    }

    /**
     * Gets the shared boolean value.
     *
     * @param value the boolean
     * @return TRUE or FALSE
     */
    public static ScriptValue bool(boolean value)
    {
        return value ? TRUE : FALSE;
    }

    /**
     * Creates a number value.
     *
     * @param value the number
     * @return the value
     */
    public static ScriptValue number(double value)
    {
        return new ScriptValue(Type.NUMBER, value);
    }

    /**
     * Creates a string value.
     *
     * @param value the text
     * @return the value
     */
    public static ScriptValue string(String value)
    {
        return new ScriptValue(Type.STRING, value);
    }

    /**
     * Creates a function value.
     *
     * @param func the function
     * @return the value
     */
    public static ScriptValue function(ScriptFunction func)
    {
        return new ScriptValue(Type.FUNCTION, func);
    }

    /**
     * Creates an object value backed by the given map, not a copy.
     *
     * @param props the properties by name
     * @return the value
     */
    public static ScriptValue object(Map<String, ScriptValue> props)
    {
        return new ScriptValue(Type.OBJECT, props);
    }

    /**
     * Creates an array value backed by the given list, not a copy.
     *
     * @param items the elements, expected to be script values
     * @return the value
     */
    public static ScriptValue array(List<?> items)
    {
        return new ScriptValue(Type.ARRAY, items);
    }

    /**
     * Wraps a Java object as a native value.
     *
     * @param obj the object
     * @return the value
     */
    public static ScriptValue native_(Object obj)
    {
        return new ScriptValue(Type.NATIVE, obj);
    }

    /**
     * Tells whether the value is null.
     *
     * @return true for that type
     */
    public boolean isNull()
    {
        return type == Type.NULL;
    }

    /**
     * Tells whether the value is a boolean.
     *
     * @return true for that type
     */
    public boolean isBoolean()
    {
        return type == Type.BOOLEAN;
    }

    /**
     * Tells whether the value is a number.
     *
     * @return true for that type
     */
    public boolean isNumber()
    {
        return type == Type.NUMBER;
    }

    /**
     * Tells whether the value is a string.
     *
     * @return true for that type
     */
    public boolean isString()
    {
        return type == Type.STRING;
    }

    /**
     * Tells whether the value is a function.
     *
     * @return true for that type
     */
    public boolean isFunction()
    {
        return type == Type.FUNCTION;
    }

    /**
     * Tells whether the value is an object.
     *
     * @return true for that type
     */
    public boolean isObject()
    {
        return type == Type.OBJECT;
    }

    /**
     * Tells whether the value is an array.
     *
     * @return true for that type
     */
    public boolean isArray()
    {
        return type == Type.ARRAY;
    }

    /**
     * Tells whether the value is a wrapped Java object.
     *
     * @return true for that type
     */
    public boolean isNative()
    {
        return type == Type.NATIVE;
    }

    /**
     * Converts the value to a boolean the way JavaScript does.
     *
     * @return false for null, false, zero and the empty string; true otherwise
     */
    public boolean asBoolean()
    {
        if (type == Type.BOOLEAN) return (Boolean) value;
        if (type == Type.NULL) return false;
        if (type == Type.NUMBER) return ((Double) value) != 0;
        if (type == Type.STRING) return !((String) value).isEmpty();
        return true;
    }

    /**
     * Converts the value to a number.
     *
     * @return the number, 1 or 0 for booleans, the parsed value of a string, or NaN when it cannot be converted
     */
    public double asNumber()
    {
        if (type == Type.NUMBER) return (Double) value;
        if (type == Type.BOOLEAN) return (Boolean) value ? 1 : 0;
        if (type == Type.STRING)
        {
            try
            {
                return Double.parseDouble((String) value);
            }
            catch (NumberFormatException e)
            {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * Converts the value to display text; whole numbers print without a decimal point.
     *
     * @return the text, with placeholders such as [Object] for functions, objects and arrays
     */
    public String asString()
    {
        if (type == Type.STRING) return (String) value;
        if (type == Type.NULL) return "null";
        if (type == Type.BOOLEAN) return value.toString();
        if (type == Type.NUMBER)
        {
            double d = (Double) value;
            if (d == (long) d) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        if (type == Type.FUNCTION) return "[Function]";
        if (type == Type.OBJECT) return "[Object]";
        if (type == Type.ARRAY) return "[Array]";
        if (type == Type.NATIVE) return value.toString();
        return "undefined";
    }

    /**
     * Gets the function this value holds.
     *
     * @return the function
     * @throws RuntimeException if the value is not a function
     */
    public ScriptFunction asFunction()
    {
        if (type == Type.FUNCTION) return (ScriptFunction) value;
        throw new RuntimeException("Value is not a function: " + this);
    }

    /**
     * Gets the property map this value holds.
     *
     * @return the live property map
     * @throws RuntimeException if the value is not an object
     */
    @SuppressWarnings("unchecked")
    public Map<String, ScriptValue> asObject()
    {
        if (type == Type.OBJECT) return (Map<String, ScriptValue>) value;
        throw new RuntimeException("Value is not an object: " + this);
    }

    /**
     * Gets the element list this value holds.
     *
     * @return the live element list
     * @throws RuntimeException if the value is not an array
     */
    @SuppressWarnings("unchecked")
    public List<ScriptValue> asArray()
    {
        if (type == Type.ARRAY) return (List<ScriptValue>) value;
        throw new RuntimeException("Value is not an array: " + this);
    }

    /**
     * Gets the wrapped Java object as a given type.
     *
     * @param <T> the expected type
     * @param clazz the expected type
     * @return the object
     * @throws RuntimeException if the value is not native or not of that type
     */
    @SuppressWarnings("unchecked")
    public <T> T asNative(Class<T> clazz)
    {
        if (type == Type.NATIVE && clazz.isInstance(value))
        {
            return (T) value;
        }
        throw new RuntimeException("Value is not a " + clazz.getSimpleName() + ": " + this);
    }

    /** @return the Java value behind this script value */
    public Object unwrap()
    {
        return value;
    }

    /**
     * Reads a property: an object entry, or length on a string or array.
     *
     * @param name the property name
     * @return the property value, or null when there is none
     */
    public ScriptValue getProperty(String name)
    {
        if (type == Type.OBJECT)
        {
            Map<String, ScriptValue> props = asObject();
            return props.getOrDefault(name, NULL);
        }
        if (type == Type.STRING)
        {
            String s = (String) value;
            if ("length".equals(name)) return number(s.length());
        }
        if (type == Type.ARRAY)
        {
            List<?> arr = (List<?>) value;
            if ("length".equals(name)) return number(arr.size());
        }
        return NULL;
    }

    /**
     * Sets an object property; does nothing on other types.
     *
     * @param name the property name
     * @param val the new value
     */
    public void setProperty(String name, ScriptValue val)
    {
        if (type == Type.OBJECT)
        {
            asObject().put(name, val);
        }
    }

    /**
     * Adds two values: concatenates as strings if either is a string, otherwise adds as numbers.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the sum or concatenation
     */
    public static ScriptValue add(ScriptValue left, ScriptValue right)
    {
        if (left.isString() || right.isString())
        {
            return string(left.asString() + right.asString());
        }
        return number(left.asNumber() + right.asNumber());
    }

    /**
     * Subtracts two values as numbers.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the numeric result, NaN when an operand is not numeric
     */
    public static ScriptValue subtract(ScriptValue left, ScriptValue right)
    {
        return number(left.asNumber() - right.asNumber());
    }

    /**
     * Multiplies two values as numbers.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the numeric result, NaN when an operand is not numeric
     */
    public static ScriptValue multiply(ScriptValue left, ScriptValue right)
    {
        return number(left.asNumber() * right.asNumber());
    }

    /**
     * Divides two values as numbers.
     *
     * @param left the left operand
     * @param right the right operand
     * @return the numeric result, NaN when an operand is not numeric
     */
    public static ScriptValue divide(ScriptValue left, ScriptValue right)
    {
        return number(left.asNumber() / right.asNumber());
    }

    /**
     * Takes the floating-point remainder of two values as numbers.
     *
     * @param left the dividend
     * @param right the divisor
     * @return the remainder, NaN when an operand is not numeric
     */
    public static ScriptValue modulo(ScriptValue left, ScriptValue right)
    {
        return number(left.asNumber() % right.asNumber());
    }

    /**
     * Negates a value as a number.
     *
     * @param val the operand
     * @return the negated number
     */
    public static ScriptValue negate(ScriptValue val)
    {
        return number(-val.asNumber());
    }

    /**
     * Negates a value as a boolean.
     *
     * @param val the operand
     * @return the logical complement
     */
    public static ScriptValue not(ScriptValue val)
    {
        return bool(!val.asBoolean());
    }

    /**
     * Compares two values strictly: same type and equal Java values.
     *
     * @param left the first value
     * @param right the second value
     * @return true when they are equal
     */
    public static boolean equals(ScriptValue left, ScriptValue right)
    {
        if (left.type != right.type) return false;
        if (left.isNull()) return right.isNull();
        return Objects.equals(left.value, right.value);
    }

    /**
     * Orders two values as numbers.
     *
     * @param left the first value
     * @param right the second value
     * @return negative, zero or positive as left is less than, equal to or greater than right
     */
    public static int compare(ScriptValue left, ScriptValue right)
    {
        return Double.compare(left.asNumber(), right.asNumber());
    }

    @Override
    public String toString()
    {
        return asString();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ScriptValue)
        {
            return equals(this, (ScriptValue) obj);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, value);
    }
}
