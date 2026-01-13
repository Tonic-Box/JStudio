package com.tonic.ui.editor.source;

import com.tonic.parser.ClassFile;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
@Builder
public class CompilationResult {

    boolean success;
    @Builder.Default
    List<CompilationError> errors = Collections.emptyList();
    ClassFile compiledClass;
    String sourceCode;
    long compilationTimeMs;

    public boolean hasErrors() {
        return errors.stream().anyMatch(CompilationError::isError);
    }

    public boolean hasWarnings() {
        return errors.stream().anyMatch(CompilationError::isWarning);
    }

    public int getErrorCount() {
        return (int) errors.stream().filter(CompilationError::isError).count();
    }

    public int getWarningCount() {
        return (int) errors.stream().filter(CompilationError::isWarning).count();
    }

    public static CompilationResult success(ClassFile compiledClass, String sourceCode, long timeMs) {
        return CompilationResult.builder()
                .success(true)
                .compiledClass(compiledClass)
                .sourceCode(sourceCode)
                .compilationTimeMs(timeMs)
                .build();
    }

    public static CompilationResult failure(List<CompilationError> errors, String sourceCode, long timeMs) {
        return CompilationResult.builder()
                .success(false)
                .errors(errors)
                .sourceCode(sourceCode)
                .compilationTimeMs(timeMs)
                .build();
    }
}
