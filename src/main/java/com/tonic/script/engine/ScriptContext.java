package com.tonic.script.engine;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Variable scope for script execution.
 */
public class ScriptContext {

    @Getter
    private final ScriptContext parent;
    private final Map<String, ScriptValue> variables = new HashMap<>();
    private final Map<String, Boolean> constants = new HashMap<>();

    public ScriptContext() {
        this.parent = null;
    }

    public ScriptContext(ScriptContext parent) {
        this.parent = parent;
    }

    /**
     * Defines a new variable in this scope.
     */
    public void define(String name, ScriptValue value) {
        variables.put(name, value);
        constants.put(name, false);
    }

    /**
     * Defines a new constant in this scope.
     */
    public void defineConstant(String name, ScriptValue value) {
        variables.put(name, value);
        constants.put(name, true);
    }

    /**
     * Gets a variable, searching up the scope chain.
     */
    public ScriptValue get(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        if (parent != null) {
            return parent.get(name);
        }
        return ScriptValue.NULL;
    }

    /**
     * Checks if a variable exists in the scope chain.
     */
    public boolean has(String name) {
        if (variables.containsKey(name)) {
            return true;
        }
        if (parent != null) {
            return parent.has(name);
        }
        return false;
    }

    /**
     * Sets a variable, searching up the scope chain.
     */
    public void set(String name, ScriptValue value) {
        // Find the scope where this variable is defined
        ScriptContext scope = findScope(name);

        if (scope != null) {
            if (scope.constants.getOrDefault(name, false)) {
                throw new RuntimeException("Cannot reassign constant: " + name);
            }
            scope.variables.put(name, value);
        } else {
            // Define in current scope if not found
            variables.put(name, value);
            constants.put(name, false);
        }
    }

    /**
     * Finds the scope where a variable is defined.
     */
    private ScriptContext findScope(String name) {
        if (variables.containsKey(name)) {
            return this;
        }
        if (parent != null) {
            return parent.findScope(name);
        }
        return null;
    }

    /**
     * Creates a child scope.
     */
    public ScriptContext child() {
        return new ScriptContext(this);
    }

    /**
     * Dumps all variables (for debugging).
     */
    public Map<String, ScriptValue> dump() {
        Map<String, ScriptValue> all = new HashMap<>();
        if (parent != null) {
            all.putAll(parent.dump());
        }
        all.putAll(variables);
        return all;
    }
}
