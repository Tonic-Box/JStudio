package com.tonic.script.engine;

import lombok.Getter;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** A callable value in the script runtime: a user-written function or a native Java one. */
public abstract class ScriptFunction
{

    /**
     * Calls the function.
     *
     * @param interpreter the interpreter to evaluate a user function body in
     * @param args the arguments, in order
     * @return the function result
     */
    public abstract ScriptValue call(ScriptInterpreter interpreter, List<ScriptValue> args);

    /** A function written in script, closing over the scope it was defined in; missing arguments are null. */
    @Getter
    public static class UserFunction extends ScriptFunction
    {
        private final List<String> parameters;
        private final ScriptAST body;
        private final ScriptContext closure;

        /**
         * Creates a user function.
         *
         * @param parameters the parameter names, in order
         * @param body the function body, a block or a single expression
         * @param closure the scope it was defined in
         */
        public UserFunction(List<String> parameters, ScriptAST body, ScriptContext closure)
        {
            this.parameters = parameters;
            this.body = body;
            this.closure = closure;
        }

        @Override
        public ScriptValue call(ScriptInterpreter interpreter, List<ScriptValue> args)
        {
            ScriptContext scope = new ScriptContext(closure);

            for (int i = 0; i < parameters.size(); i++)
            {
                ScriptValue arg = i < args.size() ? args.get(i) : ScriptValue.NULL;
                scope.define(parameters.get(i), arg);
            }

            return interpreter.executeInContext(body, scope);
        }
    }

    /** A Java function exposed to scripts under a name. */
    public static class NativeFunction extends ScriptFunction
    {
        private final String name;
        private final Function<List<ScriptValue>, ScriptValue> impl;

        /**
         * Creates a native function.
         *
         * @param name the name shown when the function is printed
         * @param impl the Java implementation, given the argument list
         */
        public NativeFunction(String name, Function<List<ScriptValue>, ScriptValue> impl)
        {
            this.name = name;
            this.impl = impl;
        }

        @Override
        public ScriptValue call(ScriptInterpreter interpreter, List<ScriptValue> args)
        {
            return impl.apply(args);
        }

        @Override
        public String toString()
        {
            return "[NativeFunction: " + name + "]";
        }
    }

    /**
     * Wraps a Java supplier as a function that ignores its arguments.
     *
     * @param name the function name
     * @param fn the implementation
     * @return the function
     */
    public static ScriptFunction native0(String name, Supplier<ScriptValue> fn)
    {
        return new NativeFunction(name, args -> fn.get());
    }

    /**
     * Wraps a one-argument Java function; a missing argument is null and extra ones are ignored.
     *
     * @param name the function name
     * @param fn the implementation
     * @return the function
     */
    public static ScriptFunction native1(String name, Function<ScriptValue, ScriptValue> fn)
    {
        return new NativeFunction(name, args ->
        {
            ScriptValue arg = args.isEmpty() ? ScriptValue.NULL : args.get(0);
            return fn.apply(arg);
        });
    }

    /**
     * Wraps a two-argument Java function; missing arguments are null and extra ones are ignored.
     *
     * @param name the function name
     * @param fn the implementation
     * @return the function
     */
    public static ScriptFunction native2(String name, BiFunction<ScriptValue, ScriptValue, ScriptValue> fn)
    {
        return new NativeFunction(name, args ->
        {
            ScriptValue arg1 = !args.isEmpty() ? args.get(0) : ScriptValue.NULL;
            ScriptValue arg2 = args.size() > 1 ? args.get(1) : ScriptValue.NULL;
            return fn.apply(arg1, arg2);
        });
    }

    /**
     * Wraps a Java function that takes the whole argument list.
     *
     * @param name the function name
     * @param fn the implementation
     * @return the function
     */
    public static ScriptFunction nativeN(String name, Function<List<ScriptValue>, ScriptValue> fn)
    {
        return new NativeFunction(name, fn);
    }
}
