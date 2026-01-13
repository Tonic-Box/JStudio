package com.tonic.ui.editor.source;

import lombok.Value;

@Value
public class CompilationError {

    int line;
    int column;
    int offset;
    int length;
    String message;
    Severity severity;

    public enum Severity {
        ERROR,
        WARNING
    }

    public static CompilationError error(int line, int column, int offset, int length, String message) {
        return new CompilationError(line, column, offset, length, message, Severity.ERROR);
    }

    public static CompilationError warning(int line, int column, int offset, int length, String message) {
        return new CompilationError(line, column, offset, length, message, Severity.WARNING);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }
}
