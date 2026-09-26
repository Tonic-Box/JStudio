package com.tonic.script.engine;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/** A variable scope of the script interpreter, chained to its parent; names can be marked constant. */
public class ScriptContext
{

    @Getter
    private final ScriptContext parent;
    private final Map<String, ScriptValue> variables = new HashMap<>();
    private final Map<String, Boolean> constants = new HashMap<>();

    /** Creates a root scope. */
    public ScriptContext()
    {
        this.parent = null;
    }

    /**
     * Creates a scope nested in another.
     *
     * @param parent the enclosing scope, or null for a root
     */
    public ScriptContext(ScriptContext parent)
    {
        this.parent = parent;
    }

    /**
     * Defines or redefines a variable in this scope, shadowing any outer one.
     *
     * @param name the variable name
     * @param value its value
     */
    public void define(String name, ScriptValue value)
    {
        variables.put(name, value);
        constants.put(name, false);
    }

    /**
     * Defines or redefines a constant in this scope; later assignment through set fails.
     *
     * @param name the constant name
     * @param value its value
     */
    public void defineConstant(String name, ScriptValue value)
    {
        variables.put(name, value);
        constants.put(name, true);
    }

    /**
     * Looks a name up in this scope, then outward.
     *
     * @param name the variable name
     * @return its value, or the script null value when no scope defines it
     */
    public ScriptValue get(String name)
    {
        if (variables.containsKey(name))
        {
            return variables.get(name);
        }
        if (parent != null)
        {
            return parent.get(name);
        }
        return ScriptValue.NULL;
    }

    /**
     * Assigns to the nearest scope defining the name, or defines it here when none does.
     *
     * @param name the variable name
     * @param value the new value
     * @throws RuntimeException if the nearest definition is a constant
     */
    public void set(String name, ScriptValue value)
    {
        ScriptContext scope = findScope(name);

        if (scope != null)
        {
            if (scope.constants.getOrDefault(name, false))
            {
                throw new RuntimeException("Cannot reassign constant: " + name);
            }
            scope.variables.put(name, value);
        }
        else
        {
            variables.put(name, value);
            constants.put(name, false);
        }
    }

    private ScriptContext findScope(String name)
    {
        if (variables.containsKey(name))
        {
            return this;
        }
        if (parent != null)
        {
            return parent.findScope(name);
        }
        return null;
    }

    /**
     * Creates a scope nested in this one.
     *
     * @return the new scope
     */
    public ScriptContext child()
    {
        return new ScriptContext(this);
    }

}
