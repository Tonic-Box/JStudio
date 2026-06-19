package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;

/**
 * Author, manage, and (when explicitly enabled) run JStudio Script-Editor scripts - the JS-like transform DSL.
 * {@link #write}/{@link #list}/{@link #read} operate on the user scripts directory and are always safe; {@link #run}
 * APPLIES the script's transforms to the loaded project (it mutates bytecode) and streams its console output to the
 * bottom Script Console tab. Returns plain DTOs so callers need no engine dependency.
 */
public interface ScriptApi {

    /**
     * Saves (or overwrites, when the name already exists) a user script and opens the Script Editor to it. {@code
     * mode} is {@code ast|ir|both} (falls back to the script's {@code // @mode:} header). Returns the saved name.
     */
    String write(String name, String mode, String content);

    /** All saved user scripts (name, mode, description). */
    List<ScriptInfo> list();

    /** A saved script's source by name, or {@code null} if there is none. */
    String read(String name);

    /**
     * Runs a script - a saved one by {@code name}, otherwise inline {@code content}+{@code mode} - over the scope
     * ({@code all}|{@code class}|{@code method}; {@code className}/{@code methodName}+{@code methodDescriptor} for
     * the narrower scopes), APPLYING its transforms. Streams output to the Script Console tab; returns the captured
     * output + modification count.
     */
    RunResult run(String name, String content, String mode, String scope,
                  String className, String methodName, String methodDescriptor);

    /** Metadata for a saved script. */
    @Getter
    final class ScriptInfo {
        private final String name;
        private final String mode;
        private final String description;

        public ScriptInfo(String name, String mode, String description) {
            this.name = name;
            this.mode = mode;
            this.description = description;
        }
    }

    /** The outcome of a run: total modifications applied, the captured console output, and whether it errored. */
    @Getter
    final class RunResult {
        private final int modifications;
        private final String output;
        private final boolean error;

        public RunResult(int modifications, String output, boolean error) {
            this.modifications = modifications;
            this.output = output;
            this.error = error;
        }
    }
}
