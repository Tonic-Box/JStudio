package com.tonic.ui.editor.source;

import com.tonic.parser.ClassFile;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/** The outcome of compiling edited source: the compiled class on success, the diagnostics on failure, and the time taken. */
@Value
@Builder
public class CompilationResult
{

    boolean success;
    @Builder.Default
    List<CompilationError> errors = Collections.emptyList();
    ClassFile compiledClass;
    String sourceCode;
    long compilationTimeMs;

    /**
     * Tells whether any diagnostic is an error.
     *
     * @return true when at least one error was reported
     */
    public boolean hasErrors()
    {
        return errors.stream().anyMatch(CompilationError::isError);
    }

    /**
     * Tells whether any diagnostic is a warning.
     *
     * @return true when at least one warning was reported
     */
    public boolean hasWarnings()
    {
        return errors.stream().anyMatch(CompilationError::isWarning);
    }

    /**
     * Counts the error diagnostics.
     *
     * @return the number of errors
     */
    public int getErrorCount()
    {
        return (int) errors.stream().filter(CompilationError::isError).count();
    }

    /**
     * Counts the warning diagnostics.
     *
     * @return the number of warnings
     */
    public int getWarningCount()
    {
        return (int) errors.stream().filter(CompilationError::isWarning).count();
    }

    /**
     * Creates a successful result with no diagnostics.
     *
     * @param compiledClass the compiled class
     * @param sourceCode the source that was compiled
     * @param timeMs the compile time in milliseconds
     * @return the result
     */
    public static CompilationResult success(ClassFile compiledClass, String sourceCode, long timeMs)
    {
        return CompilationResult.builder()
                .success(true)
                .compiledClass(compiledClass)
                .sourceCode(sourceCode)
                .compilationTimeMs(timeMs)
                .build();
    }

    /**
     * Creates a failed result with no compiled class.
     *
     * @param errors the diagnostics
     * @param sourceCode the source that was compiled
     * @param timeMs the compile time in milliseconds
     * @return the result
     */
    public static CompilationResult failure(List<CompilationError> errors, String sourceCode, long timeMs)
    {
        return CompilationResult.builder()
                .success(false)
                .errors(errors)
                .sourceCode(sourceCode)
                .compilationTimeMs(timeMs)
                .build();
    }
}
