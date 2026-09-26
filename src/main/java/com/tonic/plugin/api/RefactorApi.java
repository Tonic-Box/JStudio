package com.tonic.plugin.api;

import lombok.Getter;

/** Renames classes, methods and fields in the project currently open in the app, rewriting every reference in the bytecode; meant to be called off the EDT, and failures come back as results, not exceptions. */
public interface RefactorApi
{

    /**
     * Renames a class and every reference to it, then posts a ProjectRenamedEvent so the UI refreshes.
     *
     * @param oldName the class, internal or dotted
     * @param newName the full new name with package, internal or dotted; a bare simple name moves the class to the default package
     * @return success with what changed, or failure when no project is open, the new name is blank, equal to the old one or taken, the class is not found, or the rename fails
     */
    RenameResult renameClass(String oldName, String newName);

    /**
     * Renames a method and every call site, then posts a ProjectRenamedEvent so the UI refreshes.
     *
     * @param className the declaring class, internal or dotted
     * @param name the method's current name
     * @param descriptor the method's JVM descriptor, or null or blank when the name is not overloaded
     * @param newName the new name
     * @return success with what changed, or failure when no project is open, a name is blank, the class or method is not found, the name is overloaded and no descriptor was given, or the rename fails
     */
    RenameResult renameMethod(String className, String name, String descriptor, String newName);

    /**
     * Renames a field and every access, then posts a ProjectRenamedEvent so the UI refreshes.
     *
     * @param className the declaring class, internal or dotted
     * @param name the field's current name
     * @param descriptor the field's type descriptor, or null or blank to take the first field with the name
     * @param newName the new name
     * @return success with what changed, or failure when no project is open, a name is blank, the class or field is not found, or the rename fails
     */
    RenameResult renameField(String className, String name, String descriptor, String newName);

    /** The outcome of a rename: whether it applied, and a readable message saying what changed or why it failed. */
    @Getter
    final class RenameResult
    {
        private final boolean success;
        private final String message;

        /**
         * Creates a result.
         *
         * @param success whether the rename applied
         * @param message what changed, or why it failed
         */
        public RenameResult(boolean success, String message)
        {
            this.success = success;
            this.message = message;
        }
    }
}
