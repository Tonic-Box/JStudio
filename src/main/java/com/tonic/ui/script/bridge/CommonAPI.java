package com.tonic.ui.script.bridge;

import com.tonic.ui.script.engine.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Common API utilities shared between AST and IR modes.
 * Provides logging, context access, and shared utilities.
 */
public class CommonAPI {

    private String className;
    private String methodName;
    private String methodDescriptor;
    private Consumer<String> logCallback;
    private Consumer<String> warnCallback;
    private Consumer<String> errorCallback;

    public CommonAPI() {
    }

    /**
     * Sets the current context information.
     */
    public void setContext(String className, String methodName, String methodDescriptor) {
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
    }

    /**
     * Sets the log callbacks.
     */
    public void setCallbacks(Consumer<String> log, Consumer<String> warn, Consumer<String> error) {
        this.logCallback = log;
        this.warnCallback = warn;
        this.errorCallback = error;
    }

    /**
     * Creates the 'context' object to be registered in the script context.
     */
    public ScriptValue createContextObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("className", ScriptValue.string(className != null ? className : ""));
        props.put("methodName", ScriptValue.string(methodName != null ? methodName : ""));
        props.put("methodDescriptor", ScriptValue.string(methodDescriptor != null ? methodDescriptor : ""));

        // Simple name without package
        String simpleName = className != null ? className : "";
        int lastSlash = simpleName.lastIndexOf('/');
        if (lastSlash >= 0) {
            simpleName = simpleName.substring(lastSlash + 1);
        }
        int lastDot = simpleName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleName = simpleName.substring(lastDot + 1);
        }
        props.put("simpleClassName", ScriptValue.string(simpleName));

        // Package name
        String packageName = "";
        if (className != null) {
            int sep = className.lastIndexOf('/');
            if (sep < 0) sep = className.lastIndexOf('.');
            if (sep >= 0) {
                packageName = className.substring(0, sep).replace('/', '.');
            }
        }
        props.put("packageName", ScriptValue.string(packageName));

        return ScriptValue.object(props);
    }

    /**
     * Registers common utility functions in the interpreter's global context.
     */
    public void registerIn(ScriptInterpreter interpreter) {
        ScriptContext global = interpreter.getGlobalContext();

        // Context object
        global.defineConstant("context", createContextObject());

        // Enhanced logging with callbacks
        if (logCallback != null) {
            interpreter.setLogCallback(logCallback);
        }
        if (warnCallback != null) {
            interpreter.setWarnCallback(warnCallback);
        }
        if (errorCallback != null) {
            interpreter.setErrorCallback(errorCallback);
        }

        // Additional utility functions
        global.defineConstant("print", ScriptValue.function(
            ScriptFunction.nativeN("print", args -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(args.get(i).asString());
                }
                if (logCallback != null) {
                    logCallback.accept(sb.toString());
                } else {
                    System.out.println(sb);
                }
                return ScriptValue.NULL;
            })
        ));

        // Type checking utilities
        global.defineConstant("isString", ScriptValue.function(
            ScriptFunction.native1("isString", arg -> ScriptValue.bool(arg.isString()))
        ));

        global.defineConstant("isNumber", ScriptValue.function(
            ScriptFunction.native1("isNumber", arg -> ScriptValue.bool(arg.isNumber()))
        ));

        global.defineConstant("isBoolean", ScriptValue.function(
            ScriptFunction.native1("isBoolean", arg -> ScriptValue.bool(arg.isBoolean()))
        ));

        global.defineConstant("isNull", ScriptValue.function(
            ScriptFunction.native1("isNull", arg -> ScriptValue.bool(arg.isNull()))
        ));

        global.defineConstant("isFunction", ScriptValue.function(
            ScriptFunction.native1("isFunction", arg -> ScriptValue.bool(arg.isFunction()))
        ));

        global.defineConstant("isObject", ScriptValue.function(
            ScriptFunction.native1("isObject", arg -> ScriptValue.bool(arg.isObject()))
        ));

        global.defineConstant("isArray", ScriptValue.function(
            ScriptFunction.native1("isArray", arg -> ScriptValue.bool(arg.isArray()))
        ));

        // Array utilities
        global.defineConstant("Array", createArrayObject());

        // Object utilities
        global.defineConstant("Object", createObjectUtilities());

        // Math utilities
        global.defineConstant("Math", createMathObject());
    }

    private ScriptValue createArrayObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("isArray", ScriptValue.function(
            ScriptFunction.native1("isArray", arg -> ScriptValue.bool(arg.isArray()))
        ));

        props.put("of", ScriptValue.function(
            ScriptFunction.nativeN("of", ScriptValue::array)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue createObjectUtilities() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("keys", ScriptValue.function(
            ScriptFunction.native1("keys", arg -> {
                if (!arg.isObject()) return ScriptValue.array(new ArrayList<>());
                List<ScriptValue> keys = new ArrayList<>();
                for (String key : arg.asObject().keySet()) {
                    keys.add(ScriptValue.string(key));
                }
                return ScriptValue.array(keys);
            })
        ));

        props.put("values", ScriptValue.function(
            ScriptFunction.native1("values", arg -> {
                if (!arg.isObject()) return ScriptValue.array(new ArrayList<>());
                return ScriptValue.array(new ArrayList<>(arg.asObject().values()));
            })
        ));

        props.put("entries", ScriptValue.function(
            ScriptFunction.native1("entries", arg -> {
                if (!arg.isObject()) return ScriptValue.array(new ArrayList<>());
                List<ScriptValue> entries = new ArrayList<>();
                for (Map.Entry<String, ScriptValue> entry : arg.asObject().entrySet()) {
                    List<ScriptValue> pair = new ArrayList<>();
                    pair.add(ScriptValue.string(entry.getKey()));
                    pair.add(entry.getValue());
                    entries.add(ScriptValue.array(pair));
                }
                return ScriptValue.array(entries);
            })
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue createMathObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("PI", ScriptValue.number(Math.PI));
        props.put("E", ScriptValue.number(Math.E));

        props.put("abs", ScriptValue.function(
            ScriptFunction.native1("abs", arg -> ScriptValue.number(Math.abs(arg.asNumber())))
        ));

        props.put("floor", ScriptValue.function(
            ScriptFunction.native1("floor", arg -> ScriptValue.number(Math.floor(arg.asNumber())))
        ));

        props.put("ceil", ScriptValue.function(
            ScriptFunction.native1("ceil", arg -> ScriptValue.number(Math.ceil(arg.asNumber())))
        ));

        props.put("round", ScriptValue.function(
            ScriptFunction.native1("round", arg -> ScriptValue.number(Math.round(arg.asNumber())))
        ));

        props.put("min", ScriptValue.function(
            ScriptFunction.native2("min", (a, b) -> ScriptValue.number(Math.min(a.asNumber(), b.asNumber())))
        ));

        props.put("max", ScriptValue.function(
            ScriptFunction.native2("max", (a, b) -> ScriptValue.number(Math.max(a.asNumber(), b.asNumber())))
        ));

        props.put("pow", ScriptValue.function(
            ScriptFunction.native2("pow", (base, exp) -> ScriptValue.number(Math.pow(base.asNumber(), exp.asNumber())))
        ));

        props.put("sqrt", ScriptValue.function(
            ScriptFunction.native1("sqrt", arg -> ScriptValue.number(Math.sqrt(arg.asNumber())))
        ));

        props.put("random", ScriptValue.function(
            ScriptFunction.native0("random", () -> ScriptValue.number(Math.random()))
        ));

        return ScriptValue.object(props);
    }
}
