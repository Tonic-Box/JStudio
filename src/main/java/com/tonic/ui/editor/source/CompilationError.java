package com.tonic.ui.editor.source;

import lombok.Value;

/** A compiler diagnostic: its position in the source, message, and severity. */
@Value
public class CompilationError
{

    int line;
    int column;
    int offset;
    int length;
    String message;
    Severity severity;

    /** How serious a diagnostic is. */
    public enum Severity
    {
        ERROR,
        WARNING
    }

    /**
     * Creates an error diagnostic.
     *
     * @param line the source line
     * @param column the source column
     * @param offset the character offset into the source
     * @param length the length of the flagged span
     * @param message the diagnostic text
     * @return the error
     */
    public static CompilationError error(int line, int column, int offset, int length, String message)
    {
        return new CompilationError(line, column, offset, length, message, Severity.ERROR);
    }

    /**
     * Creates a warning diagnostic.
     *
     * @param line the source line
     * @param column the source column
     * @param offset the character offset into the source
     * @param length the length of the flagged span
     * @param message the diagnostic text
     * @return the warning
     */
    public static CompilationError warning(int line, int column, int offset, int length, String message)
    {
        return new CompilationError(line, column, offset, length, message, Severity.WARNING);
    }

    /**
     * Tells whether this diagnostic is an error.
     *
     * @return true for an error
     */
    public boolean isError()
    {
        return severity == Severity.ERROR;
    }

    /**
     * Tells whether this diagnostic is a warning.
     *
     * @return true for a warning
     */
    public boolean isWarning()
    {
        return severity == Severity.WARNING;
    }
}
