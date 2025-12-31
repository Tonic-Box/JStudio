package com.tonic.ui.script.engine;

import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * Represents a callable function in the script runtime.
 */
public abstract class ScriptFunction {

    /**
     * Calls this function with the given arguments.
     */
    public abstract ScriptValue call(ScriptInterpreter interpreter, List<ScriptValue> args);

    /**
     * User-defined arrow function from script.
     */
    @Getter
    public static class UserFunction extends ScriptFunction {
        private final List<String> parameters;
        private final ScriptAST body;
        private final ScriptContext closure;

        public UserFunction(List<String> parameters, ScriptAST body, ScriptContext closure) {
            this.parameters = parameters;
            this.body = body;
            this.closure = closure;
        }

        @Override
        public ScriptValue call(ScriptInterpreter interpreter, List<ScriptValue> args) {
            // Create new scope with closure as parent
            ScriptContext scope = new ScriptContext(closure);

            // Bind arguments to parameters
            for (int i = 0; i < parameters.size(); i++) {
                ScriptValue arg = i < args.size() ? args.get(i) : ScriptValue.NULL;
                scope.define(parameters.get(i), arg);
            }

            // Execute body
            return interpreter.executeInContext(body, scope);
        }
    }

    /**
     * Native Java function exposed to script.
     */
    public static class NativeFunction extends ScriptFunction {
        private final String name;
        private final Function<List<ScriptValue>, ScriptValue> impl;

        public NativeFunction(String name, Function<List<ScriptValue>, ScriptValue> impl) {
            this.name = name;
            this.impl = impl;
        }

        @Override
        public ScriptValue call(ScriptInterpreter interpreter, List<ScriptValue> args) {
            return impl.apply(args);
        }

        @Override
        public String toString() {
            return "[NativeFunction: " + name + "]";
        }
    }

    /**
     * Creates a native function with 0 arguments.
     */
    public static ScriptFunction native0(String name, java.util.function.Supplier<ScriptValue> fn) {
        return new NativeFunction(name, args -> fn.get());
    }

    /**
     * Creates a native function with 1 argument.
     */
    public static ScriptFunction native1(String name, Function<ScriptValue, ScriptValue> fn) {
        return new NativeFunction(name, args -> {
            ScriptValue arg = args.isEmpty() ? ScriptValue.NULL : args.get(0);
            return fn.apply(arg);
        });
    }

    /**
     * Creates a native function with 2 arguments.
     */
    public static ScriptFunction native2(String name, java.util.function.BiFunction<ScriptValue, ScriptValue, ScriptValue> fn) {
        return new NativeFunction(name, args -> {
            ScriptValue arg1 = !args.isEmpty() ? args.get(0) : ScriptValue.NULL;
            ScriptValue arg2 = args.size() > 1 ? args.get(1) : ScriptValue.NULL;
            return fn.apply(arg1, arg2);
        });
    }

    /**
     * Creates a native function with variable arguments.
     */
    public static ScriptFunction nativeN(String name, Function<List<ScriptValue>, ScriptValue> fn) {
        return new NativeFunction(name, fn);
    }
}
