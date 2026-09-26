package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;

/** Writes, lists, reads and runs JStudio Script Editor scripts from the user scripts directory; only run changes the project, and failures to run come back as results. */
public interface ScriptApi
{

    /**
     * Saves a user script, overwriting one with the same name, and posts a ScriptWrittenEvent so the Script Editor opens it.
     *
     * @param name the script name, or null or blank for "AI Script"; characters outside letters, digits, underscore and dash become underscores in the file name
     * @param mode ast, ir or both, or null or blank to read the script's // @mode: header, which defaults to ast
     * @param content the script source, or null for an empty script
     * @return the name saved under, trimmed
     * @throws IllegalStateException if the script file cannot be written
     */
    String write(String name, String mode, String content);

    /**
     * Lists the saved user scripts.
     *
     * @return a new list, empty when there are none
     */
    List<ScriptInfo> list();

    /**
     * Returns a saved script's source.
     *
     * @param name the script name, matched exactly
     * @return the source, or null when no script has the name
     */
    String read(String name);

    /**
     * Runs a saved script, or else inline content, over part of the current project, applying its transforms; runs on the calling thread and streams each output line to the Script Console as a ScriptConsoleEvent.
     *
     * @param name a saved script's name, or null or blank to run content instead
     * @param content inline source, used only when name is null or blank
     * @param mode ast, ir or both for inline content, or null to read its header; ignored for a saved script
     * @param scope all, class or method, ignoring case; null, blank or anything else means all
     * @param className the target class for class and method scope, internal or dotted
     * @param methodName the target method's name for method scope
     * @param methodDescriptor the target method's descriptor, or null or blank to take the first method with the name
     * @return the modification count, the captured output and whether an error was reported; an error result with no changes when there is no script, no project, or the target is not found
     */
    RunResult run(String name, String content, String mode, String scope, String className, String methodName, String methodDescriptor);

    /** A saved script's name, mode and description. */
    @Getter
    final class ScriptInfo
    {
        private final String name;
        private final String mode;
        private final String description;

        /**
         * Creates the metadata.
         *
         * @param name the script name
         * @param mode ast, ir or both, lowercase
         * @param description the script's description
         */
        public ScriptInfo(String name, String mode, String description)
        {
            this.name = name;
            this.mode = mode;
            this.description = description;
        }
    }

    /** The outcome of a script run: how many modifications it applied, its captured console output, and whether it reported an error. */
    @Getter
    final class RunResult
    {
        private final int modifications;
        private final String output;
        private final boolean error;

        /**
         * Creates a result.
         *
         * @param modifications how many changes the run applied
         * @param output the captured console output, or the failure message
         * @param error whether the run failed or printed an error line
         */
        public RunResult(int modifications, String output, boolean error)
        {
            this.modifications = modifications;
            this.output = output;
            this.error = error;
        }
    }
}
