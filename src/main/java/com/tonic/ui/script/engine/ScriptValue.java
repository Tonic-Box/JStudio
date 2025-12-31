package com.tonic.ui.script.engine;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Runtime values for the script interpreter.
 */
@Getter
public class ScriptValue {

    public enum Type {
        NULL,
        BOOLEAN,
        NUMBER,
        STRING,
        FUNCTION,
        OBJECT,
        ARRAY,
        NATIVE   // Java object wrapper
    }

    private final Type type;
    private final Object value;

    private ScriptValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    // ==================== Factory Methods ====================

    public static final ScriptValue NULL = new ScriptValue(Type.NULL, null);
    public static final ScriptValue TRUE = new ScriptValue(Type.BOOLEAN, true);
    public static final ScriptValue FALSE = new ScriptValue(Type.BOOLEAN, false);

    @SuppressWarnings("unchecked")
    public static ScriptValue of(Object value) {
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

    public static ScriptValue bool(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static ScriptValue number(double value) {
        return new ScriptValue(Type.NUMBER, value);
    }

    public static ScriptValue string(String value) {
        return new ScriptValue(Type.STRING, value);
    }

    public static ScriptValue function(ScriptFunction func) {
        return new ScriptValue(Type.FUNCTION, func);
    }

    public static ScriptValue object(Map<String, ScriptValue> props) {
        return new ScriptValue(Type.OBJECT, props);
    }

    public static ScriptValue array(List<?> items) {
        return new ScriptValue(Type.ARRAY, items);
    }

    public static ScriptValue native_(Object obj) {
        return new ScriptValue(Type.NATIVE, obj);
    }

    // ==================== Type Checks ====================

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isBoolean() {
        return type == Type.BOOLEAN;
    }

    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isFunction() {
        return type == Type.FUNCTION;
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isNative() {
        return type == Type.NATIVE;
    }

    // ==================== Conversions ====================

    public boolean asBoolean() {
        if (type == Type.BOOLEAN) return (Boolean) value;
        if (type == Type.NULL) return false;
        if (type == Type.NUMBER) return ((Double) value) != 0;
        if (type == Type.STRING) return !((String) value).isEmpty();
        return true; // Objects, functions, arrays are truthy
    }

    public double asNumber() {
        if (type == Type.NUMBER) return (Double) value;
        if (type == Type.BOOLEAN) return (Boolean) value ? 1 : 0;
        if (type == Type.STRING) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    public String asString() {
        if (type == Type.STRING) return (String) value;
        if (type == Type.NULL) return "null";
        if (type == Type.BOOLEAN) return value.toString();
        if (type == Type.NUMBER) {
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

    public ScriptFunction asFunction() {
        if (type == Type.FUNCTION) return (ScriptFunction) value;
        throw new RuntimeException("Value is not a function: " + this);
    }

    @SuppressWarnings("unchecked")
    public Map<String, ScriptValue> asObject() {
        if (type == Type.OBJECT) return (Map<String, ScriptValue>) value;
        throw new RuntimeException("Value is not an object: " + this);
    }

    @SuppressWarnings("unchecked")
    public List<ScriptValue> asArray() {
        if (type == Type.ARRAY) return (List<ScriptValue>) value;
        throw new RuntimeException("Value is not an array: " + this);
    }

    @SuppressWarnings("unchecked")
    public <T> T asNative(Class<T> clazz) {
        if (type == Type.NATIVE && clazz.isInstance(value)) {
            return (T) value;
        }
        throw new RuntimeException("Value is not a " + clazz.getSimpleName() + ": " + this);
    }

    public Object unwrap() {
        return value;
    }

    // ==================== Property Access ====================

    public ScriptValue getProperty(String name) {
        if (type == Type.OBJECT) {
            Map<String, ScriptValue> props = asObject();
            return props.getOrDefault(name, NULL);
        }
        if (type == Type.STRING) {
            String s = (String) value;
            if ("length".equals(name)) return number(s.length());
        }
        if (type == Type.ARRAY) {
            List<?> arr = (List<?>) value;
            if ("length".equals(name)) return number(arr.size());
        }
        // Could use reflection here for native object property access
        return NULL;
    }

    public void setProperty(String name, ScriptValue val) {
        if (type == Type.OBJECT) {
            asObject().put(name, val);
        }
    }

    // ==================== Operators ====================

    public static ScriptValue add(ScriptValue left, ScriptValue right) {
        // String concatenation
        if (left.isString() || right.isString()) {
            return string(left.asString() + right.asString());
        }
        // Numeric addition
        return number(left.asNumber() + right.asNumber());
    }

    public static ScriptValue subtract(ScriptValue left, ScriptValue right) {
        return number(left.asNumber() - right.asNumber());
    }

    public static ScriptValue multiply(ScriptValue left, ScriptValue right) {
        return number(left.asNumber() * right.asNumber());
    }

    public static ScriptValue divide(ScriptValue left, ScriptValue right) {
        return number(left.asNumber() / right.asNumber());
    }

    public static ScriptValue modulo(ScriptValue left, ScriptValue right) {
        return number(left.asNumber() % right.asNumber());
    }

    public static ScriptValue negate(ScriptValue val) {
        return number(-val.asNumber());
    }

    public static ScriptValue not(ScriptValue val) {
        return bool(!val.asBoolean());
    }

    public static boolean equals(ScriptValue left, ScriptValue right) {
        if (left.type != right.type) return false;
        if (left.isNull()) return right.isNull();
        return java.util.Objects.equals(left.value, right.value);
    }

    public static int compare(ScriptValue left, ScriptValue right) {
        return Double.compare(left.asNumber(), right.asNumber());
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ScriptValue) {
            return equals(this, (ScriptValue) obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, value);
    }
}
