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

import java.util.Collections;
import java.util.List;

/** An editor parser that flags syntax errors in the edited source as the user types, and compiles it against the original class on request. */
public class SourceCompilerParser extends AbstractParser
{

    private final SourceCompiler compiler;
    private ClassFile originalClass;
    @Getter
    private List<CompilationError> lastErrors = Collections.emptyList();
    @Getter
    private boolean enabled;

    /** Creates the parser, disabled until enabled. */
    public SourceCompilerParser()
    {
        this.compiler = new SourceCompiler();
        this.enabled = false;
    }

    /**
     * Sets the class that edits compile against; parsing is skipped while none is set.
     *
     * @param originalClass the class being edited
     */
    public void setOriginalClass(ClassFile originalClass)
    {
        this.originalClass = originalClass;
    }

    /**
     * Turns as-you-type error checking on or off.
     *
     * @param enabled whether to check
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * Tells whether the last parse found an error.
     *
     * @return true when the last parse reported at least one error
     */
    public boolean hasErrors()
    {
        return lastErrors.stream().anyMatch(CompilationError::isError);
    }

    /**
     * Counts the errors from the last parse.
     *
     * @return the number of errors
     */
    public int getErrorCount()
    {
        return (int) lastErrors.stream().filter(CompilationError::isError).count();
    }

    /**
     * Counts the warnings from the last parse.
     *
     * @return the number of warnings
     */
    public int getWarningCount()
    {
        return (int) lastErrors.stream().filter(CompilationError::isWarning).count();
    }

    @Override
    public ParseResult parse(RSyntaxDocument doc, String style)
    {
        DefaultParseResult result = new DefaultParseResult(this);

        if (!enabled || originalClass == null)
        {
            lastErrors = Collections.emptyList();
            return result;
        }

        try
        {
            String source = doc.getText(0, doc.getLength());
            List<CompilationError> errors = compiler.parseOnly(source);
            lastErrors = errors;

            for (CompilationError error : errors)
            {
                int line = Math.max(0, error.getLine() - 1);
                DefaultParserNotice notice = new DefaultParserNotice(this, error.getMessage(), line, error.getOffset(), error.getLength());

                if (error.isError())
                {
                    notice.setLevel(ParserNotice.Level.ERROR);
                }
                else
                {
                    notice.setLevel(ParserNotice.Level.WARNING);
                }

                result.addNotice(notice);
            }
        }
        catch (Exception e)
        {
            lastErrors = Collections.emptyList();
        }

        return result;
    }

    /**
     * Compiles the whole source against the original class.
     *
     * @param source the edited source
     * @param classPool the pool used to resolve types
     * @return the result; a failure when no original class is set
     */
    public CompilationResult compile(String source, ClassPool classPool)
    {
        return compile(source, classPool, null);
    }

    /**
     * Compiles the source against the original class, re-lowering only the changed methods.
     *
     * @param source the edited source
     * @param classPool the pool used to resolve types
     * @param changedMethods the name plus descriptor keys of the methods to re-lower, or null for all
     * @return the result; a failure when no original class is set
     */
    public CompilationResult compile(String source, ClassPool classPool, java.util.Set<String> changedMethods)
    {
        if (originalClass == null)
        {
            return CompilationResult.failure(Collections.singletonList(CompilationError.error(1, 1, 0, 1, "No class file to compile against")), source, 0);
        }
        return compiler.compile(source, originalClass, classPool, changedMethods);
    }
}
