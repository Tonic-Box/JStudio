package com.tonic.ui.editor.bytecode;

import com.tonic.parser.MethodEntry;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.ui.editor.dual.BcLocation;
import com.tonic.ui.editor.dual.BytecodeLineIndex;
import com.tonic.ui.editor.view.AbstractTextView;
import com.tonic.ui.theme.*;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;

import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

public class BytecodeView extends AbstractTextView {

    private static final String SYNTAX_STYLE_BYTECODE = "text/bytecode";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_BYTECODE, "com.tonic.ui.editor.bytecode.BytecodeTokenMaker");
    }

    private final ClassEntryModel classEntry;

    private static final String METHOD_DIVIDER = "=========================================================================";

    private Runnable pendingHighlight;

    private final Map<Integer, Object> highlightedLines = new HashMap<>();
    private int lastClickedLine = -1;

    private IntConsumer onLineActivated;
    private BytecodeLineIndex lineIndex;

    public BytecodeView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        initTextArea(SYNTAX_STYLE_BYTECODE);

        add(overlayWrap(scrollPane), BorderLayout.CENTER);
        add(searchPanel, BorderLayout.SOUTH);

        setupMouseListener();
    }

    private void setupMouseListener() {
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;

                try {
                    int offset = textArea.viewToModel2D(e.getPoint());
                    int lineNum = textArea.getLineOfOffset(offset);
                    if (lineNum < 0) return;

                    if (e.getClickCount() == 2 && onLineActivated != null) {
                        lastClickedLine = lineNum;
                        onLineActivated.accept(lineNum);
                        return;
                    }

                    if (e.isControlDown()) {
                        toggleHighlight(lineNum);
                    } else if (e.isShiftDown() && lastClickedLine >= 0) {
                        highlightRange(lastClickedLine, lineNum);
                    } else {
                        clearHighlights();
                    }
                    lastClickedLine = lineNum;
                } catch (Exception ex) {
                    // Ignore
                }
            }
        });
    }

    @Override
    protected void applyChildThemes() {
        applyTextTheme();

        SyntaxScheme scheme = textArea.getSyntaxScheme();

        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.WHITESPACE, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextSecondary());

        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_COMMENT, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_DIVIDER, JStudioTheme.getAccentSecondary());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_HEADER, JStudioTheme.getAccent());

        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OFFSET, SyntaxColors.getBcOffset());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_LOAD, SyntaxColors.getBcLoad());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_STORE, SyntaxColors.getBcStore());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_INVOKE, SyntaxColors.getBcInvoke());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_FIELD, SyntaxColors.getBcField());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_BRANCH, SyntaxColors.getBcBranch());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_STACK, SyntaxColors.getBcStack());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_CONST, SyntaxColors.getBcConst());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_RETURN, SyntaxColors.getBcReturn());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_NEW, SyntaxColors.getBcNew());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_ARITHMETIC, SyntaxColors.getBcArithmetic());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_TYPE, SyntaxColors.getBcType());

        repaint();
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }

    @Override
    public void refresh() {
        cancelCurrentWorker();
        lineIndex = null;
        loadingOverlay.showLoading("Loading bytecode...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return generateBytecodeText();
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) {
                    return;
                }
                try {
                    String bytecodeText = get();
                    textArea.setText(bytecodeText);
                    textArea.setCaretPosition(0);
                    lineIndex = null;
                    loaded = true;
                    Runnable highlight = pendingHighlight;
                    pendingHighlight = null;
                    if (highlight != null) {
                        highlight.run();
                    }
                } catch (Exception e) {
                    pendingHighlight = null;
                    textArea.setText("// Error displaying bytecode: " + e.getMessage());
                }
            }
        };
        currentWorker = worker;
        worker.execute();
    }

    private String generateBytecodeText() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Class: ").append(classEntry.getClassName()).append("\n");
        sb.append("// Super: ").append(classEntry.getSuperClassName()).append("\n");
        if (!classEntry.getInterfaceNames().isEmpty()) {
            sb.append("// Implements: ").append(String.join(", ", classEntry.getInterfaceNames())).append("\n");
        }
        sb.append("\n");

        int methodIndex = 0;
        int totalMethods = classEntry.getMethods().size();
        for (MethodEntryModel methodModel : classEntry.getMethods()) {
            methodIndex++;
            MethodEntry method = methodModel.getMethodEntry();

            sb.append("\n// ").append(METHOD_DIVIDER).append("\n");
            sb.append("// Method ").append(methodIndex).append(" of ").append(totalMethods).append("\n");
            sb.append("// ").append(METHOD_DIVIDER).append("\n\n");

            sb.append(formatAccessFlags(method.getAccess()));
            sb.append(" ").append(method.getName()).append(method.getDesc()).append("\n");

            if (method.getCodeAttribute() != null) {
                BytecodeFormatter formatter = new BytecodeFormatter(method);
                sb.append(formatter.format());
            } else {
                sb.append("  // No code (abstract or native)\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatAccessFlags(int flags) {
        StringBuilder sb = new StringBuilder();
        sb.append("//");
        if ((flags & 0x0001) != 0) sb.append(" public");
        if ((flags & 0x0002) != 0) sb.append(" private");
        if ((flags & 0x0004) != 0) sb.append(" protected");
        if ((flags & 0x0008) != 0) sb.append(" static");
        if ((flags & 0x0010) != 0) sb.append(" final");
        if ((flags & 0x0020) != 0) sb.append(" synchronized");
        if ((flags & 0x0100) != 0) sb.append(" native");
        if ((flags & 0x0400) != 0) sb.append(" abstract");
        return sb.toString();
    }

    @Override
    public void highlightLine(int line) {
        clearHighlights();
        addHighlight(line - 1);
        try {
            int caretLine = Math.max(line - 2, 0);
            int caretOffset = textArea.getLineStartOffset(caretLine);
            textArea.setCaretPosition(caretOffset);

            int highlightOffset = textArea.getLineStartOffset(line - 1);
            Rectangle rect = textArea.modelToView2D(highlightOffset).getBounds();
            if (rect != null) {
                rect.height = textArea.getHeight() / 3;
                textArea.scrollRectToVisible(rect);
            }
            textArea.requestFocus();
        } catch (Exception e) {
            // Line out of range
        }
    }

    /**
     * Highlights the instruction at the given PC. Loading is asynchronous: if a refresh worker is
     * in flight, its completion replaces the document and resets the caret, which would wipe a
     * highlight applied now — so the highlight is deferred and applied when that load finishes.
     */
    public boolean highlightPC(String methodName, String methodDesc, int pc) {
        if (!loaded) {
            refresh();
        }
        if (currentWorker != null && !currentWorker.isDone()) {
            pendingHighlight = () -> applyHighlightPC(methodName, methodDesc, pc);
            return true;
        }
        return applyHighlightPC(methodName, methodDesc, pc);
    }

    private boolean applyHighlightPC(String methodName, String methodDesc, int pc) {
        String text = textArea.getText();
        String methodSignature = methodName + methodDesc;
        int methodStart = text.indexOf(methodSignature);
        if (methodStart < 0) {
            methodStart = text.indexOf(methodName);
        }
        if (methodStart < 0) {
            return false;
        }

        String pcPattern = String.format("%d:", pc);
        int pcIndex = text.indexOf(pcPattern, methodStart);

        if (pcIndex < 0) {
            pcPattern = String.format(" %d:", pc);
            pcIndex = text.indexOf(pcPattern, methodStart);
        }

        if (pcIndex >= 0) {
            textArea.setCaretPosition(pcIndex);
            textArea.requestFocus();

            try {
                int lineNum = textArea.getLineOfOffset(pcIndex);
                clearHighlights();
                addHighlight(lineNum);
                lastClickedLine = lineNum;

                int lineStart = textArea.getLineStartOffset(lineNum);
                int lineEnd = textArea.getLineEndOffset(lineNum);
                if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\n') {
                    lineEnd--;
                }
                textArea.select(lineStart, lineEnd);
            } catch (Exception e) {
                // Ignore
            }
            return true;
        }

        textArea.setCaretPosition(methodStart);
        textArea.select(methodStart, methodStart + methodSignature.length());
        textArea.requestFocus();
        return true;
    }

    public boolean scrollToMethod(String methodName, String methodDesc) {
        if (!loaded) {
            refresh();
        }

        String text = textArea.getText();
        String searchPattern = methodDesc != null ? methodName + methodDesc : methodName;
        int index = text.indexOf(searchPattern);

        if (index >= 0) {
            clearHighlights();
            try {
                int lineNumber = textArea.getLineOfOffset(index);
                addHighlight(lineNumber);
            } catch (Exception e) {
                // Ignore
            }
            textArea.setCaretPosition(index);
            textArea.requestFocus();
            return true;
        }

        return false;
    }

    public boolean scrollToField(String fieldName) {
        if (!loaded) {
            refresh();
        }

        String text = textArea.getText();
        int index = text.indexOf(fieldName);

        if (index >= 0) {
            clearHighlights();
            try {
                int lineNumber = textArea.getLineOfOffset(index);
                addHighlight(lineNumber);
            } catch (Exception e) {
                // Ignore
            }
            textArea.setCaretPosition(index);
            textArea.requestFocus();
            return true;
        }

        return false;
    }

    public void clearHighlights() {
        for (Object tag : highlightedLines.values()) {
            try {
                textArea.removeLineHighlight(tag);
            } catch (Exception e) {
                // Ignore
            }
        }
        highlightedLines.clear();
    }

    public void addHighlight(int lineNumber) {
        if (!highlightedLines.containsKey(lineNumber)) {
            try {
                Color highlightColor = JStudioTheme.getAccentSecondary();
                Color bgColor = new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue(), 100);
                Object tag = textArea.addLineHighlight(lineNumber, bgColor);
                highlightedLines.put(lineNumber, tag);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void removeHighlight(int lineNumber) {
        Object tag = highlightedLines.remove(lineNumber);
        if (tag != null) {
            try {
                textArea.removeLineHighlight(tag);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void toggleHighlight(int lineNumber) {
        if (highlightedLines.containsKey(lineNumber)) {
            removeHighlight(lineNumber);
        } else {
            addHighlight(lineNumber);
        }
    }

    public void highlightRange(int fromLine, int toLine) {
        int start = Math.min(fromLine, toLine);
        int end = Math.max(fromLine, toLine);
        for (int i = start; i <= end; i++) {
            addHighlight(i);
        }
    }

    /**
     * Registers a listener fired with the 0-based display line on a double-click, used by the dual
     * view to drive cross-pane highlighting. Single/ctrl/shift-click behavior is unchanged.
     */
    public void setOnLineActivated(IntConsumer onLineActivated) {
        this.onLineActivated = onLineActivated;
    }

    /**
     * The instruction location at a 0-based display line, or null when the line is not an instruction.
     */
    public BcLocation locationAtLine(int displayLine) {
        return ensureLineIndex().locationAtLine(displayLine);
    }

    /**
     * Clears existing highlights, highlights every instruction line whose offset is in {@code [pcLo, pcHi]}
     * for the given {@code name+desc} method, scrolls the first into view, and returns whether any matched.
     */
    public boolean highlightPcSpan(String methodKey, int pcLo, int pcHi) {
        List<Integer> displayLines = ensureLineIndex().displayLinesForPcRange(methodKey, pcLo, pcHi);
        if (displayLines.isEmpty()) {
            return false;
        }
        clearHighlights();
        for (int line : displayLines) {
            addHighlight(line);
        }
        scrollToDisplayLine(displayLines.get(0));
        return true;
    }

    private BytecodeLineIndex ensureLineIndex() {
        if (lineIndex == null) {
            lineIndex = BytecodeLineIndex.parse(textArea.getText());
        }
        return lineIndex;
    }

    private void scrollToDisplayLine(int zeroBasedLine) {
        try {
            int offset = textArea.getLineStartOffset(zeroBasedLine);
            Rectangle2D view = textArea.modelToView2D(offset);
            if (view != null) {
                Rectangle rect = view.getBounds();
                rect.height = textArea.getHeight() / 3;
                textArea.scrollRectToVisible(rect);
            }
        } catch (Exception e) {
            // Line out of range
        }
    }

}
