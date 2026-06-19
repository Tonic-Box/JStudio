package com.tonic.plugin.api;

import lombok.Getter;

/**
 * Rename refactorings on the loaded project - classes, methods, and fields - with every reference updated
 * project-wide (via YABR's {@code Renamer}). These MUTATE the loaded project's bytecode. Names may be given in
 * internal ({@code com/foo/Bar}) or dotted ({@code com.foo.Bar}) form; the implementation normalizes them. Returns a
 * plain DTO so callers need no host/YABR dependency.
 */
public interface RefactorApi {

    /**
     * Renames a class and every reference to it. {@code newName} is the full target class name (with package); a bare
     * simple name moves the class to the default package.
     */
    RenameResult renameClass(String oldName, String newName);

    /**
     * Renames a method on {@code className} and every call site. {@code descriptor} disambiguates overloads; when
     * omitted and the name is unique on the class it is resolved automatically.
     */
    RenameResult renameMethod(String className, String name, String descriptor, String newName);

    /** Renames a field on {@code className} and every access. {@code descriptor} is optional (fields are unique by name). */
    RenameResult renameField(String className, String name, String descriptor, String newName);

    /** The outcome: whether the rename applied, and a human-readable message (what changed, or why it failed). */
    @Getter
    final class RenameResult {
        private final boolean success;
        private final String message;

        public RenameResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
