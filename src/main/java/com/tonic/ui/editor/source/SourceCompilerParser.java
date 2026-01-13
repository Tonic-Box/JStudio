package com.tonic.ui.editor.source;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;

import javax.swing.text.BadLocationException;
import java.util.Collections;
import java.util.List;

public class SourceCompilerParser extends AbstractParser {

    private final SourceCompiler compiler;
    private ClassFile originalClass;
    @Getter
    private List<CompilationError> lastErrors = Collections.emptyList();
    @Getter
    private boolean enabled;

    public SourceCompilerParser() {
        this.compiler = new SourceCompiler();
        this.enabled = false;
    }

    public void setOriginalClass(ClassFile originalClass) {
        this.originalClass = originalClass;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean hasErrors() {
        return lastErrors.stream().anyMatch(CompilationError::isError);
    }

    public int getErrorCount() {
        return (int) lastErrors.stream().filter(CompilationError::isError).count();
    }

    public int getWarningCount() {
        return (int) lastErrors.stream().filter(CompilationError::isWarning).count();
    }

    @Override
    public ParseResult parse(RSyntaxDocument doc, String style) {
        DefaultParseResult result = new DefaultParseResult(this);

        if (!enabled || originalClass == null) {
            lastErrors = Collections.emptyList();
            return result;
        }

        try {
            String source = doc.getText(0, doc.getLength());
            List<CompilationError> errors = compiler.parseOnly(source);
            lastErrors = errors;

            for (CompilationError error : errors) {
                int line = Math.max(0, error.getLine() - 1);
                DefaultParserNotice notice = new DefaultParserNotice(
                        this,
                        error.getMessage(),
                        line,
                        error.getOffset(),
                        error.getLength()
                );

                if (error.isError()) {
                    notice.setLevel(ParserNotice.Level.ERROR);
                } else {
                    notice.setLevel(ParserNotice.Level.WARNING);
                }

                result.addNotice(notice);
            }
        } catch (Exception e) {
            lastErrors = Collections.emptyList();
        }

        return result;
    }

    public CompilationResult compile(String source, ClassPool classPool) {
        if (originalClass == null) {
            return CompilationResult.failure(
                    Collections.singletonList(CompilationError.error(1, 1, 0, 1, "No class file to compile against")),
                    source,
                    0
            );
        }
        return compiler.compile(source, originalClass, classPool);
    }
}
