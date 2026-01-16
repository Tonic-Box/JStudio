package com.tonic.ui.editor.bytecode;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.*;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

public class BytecodeView extends JPanel implements ThemeChangeListener {

    private static final String SYNTAX_STYLE_BYTECODE = "text/bytecode";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_BYTECODE, "com.tonic.ui.editor.bytecode.BytecodeTokenMaker");
    }

    private final ClassEntryModel classEntry;
    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final BytecodeSearchPanel searchPanel;
    private final LoadingOverlay loadingOverlay;

    private static final String METHOD_DIVIDER = "=========================================================================";

    private boolean loaded = false;
    private SwingWorker<String, Void> currentWorker;

    private final Set<Integer> highlightedLines = new HashSet<>();
    private int lastClickedLine = -1;

    public BytecodeView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SYNTAX_STYLE_BYTECODE);
        textArea.setEditable(false);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(JStudioTheme.getCodeFont(12));
        textArea.setCodeFoldingEnabled(false);
        textArea.setBracketMatchingEnabled(false);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(null);

        loadingOverlay = new LoadingOverlay();

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new OverlayLayout(contentPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);
        contentPanel.add(loadingOverlay);
        contentPanel.add(scrollPane);

        add(contentPanel, BorderLayout.CENTER);

        searchPanel = new BytecodeSearchPanel(textArea);
        add(searchPanel, BorderLayout.SOUTH);

        applyTheme();
        setupMouseListener();

        ThemeManager.getInstance().addThemeChangeListener(this);
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

                    if (e.isControlDown()) {
                        toggleHighlight(lineNum);
                    } else if (e.isShiftDown() && lastClickedLine >= 0) {
                        highlightRange(lastClickedLine, lineNum);
                    } else {
                        clearHighlights();
                        addHighlight(lineNum);
                    }
                    lastClickedLine = lineNum;
                } catch (Exception ex) {
                    // Ignore
                }
            }
        });
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());

        textArea.setBackground(JStudioTheme.getBgTertiary());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setSelectionColor(JStudioTheme.getSelection());
        textArea.setCurrentLineHighlightColor(JStudioTheme.getLineHighlight());
        textArea.setFadeCurrentLineHighlight(true);

        scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());

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

    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Loading bytecode...");

        currentWorker = new SwingWorker<>() {
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
                    loaded = true;
                } catch (Exception e) {
                    textArea.setText("// Error displaying bytecode: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
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

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
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

    public String getText() {
        return textArea.getText();
    }

    public void copySelection() {
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public void goToLine(int line) {
        try {
            int offset = textArea.getLineStartOffset(line - 1);
            textArea.setCaretPosition(offset);
            textArea.requestFocus();
        } catch (Exception e) {
            // Line out of range
        }
    }

    public void highlightLine(int line) {
        clearHighlights();
        addHighlight(line - 1);
        try {
            int caretLine = Math.max(line - 2, 0);
            int caretOffset = textArea.getLineStartOffset(caretLine);
            textArea.setCaretPosition(caretOffset);

            int highlightOffset = textArea.getLineStartOffset(line - 1);
            java.awt.Rectangle rect = textArea.modelToView2D(highlightOffset).getBounds();
            if (rect != null) {
                rect.height = textArea.getHeight() / 3;
                textArea.scrollRectToVisible(rect);
            }
            textArea.requestFocus();
        } catch (Exception e) {
            // Line out of range
        }
    }

    public void showFindDialog() {
        searchPanel.showPanel();
    }

    public String getSelectedText() {
        return textArea.getSelectedText();
    }

    public void scrollToText(String searchText) {
        if (searchText == null || searchText.isEmpty()) return;

        String text = textArea.getText();
        int index = text.toLowerCase().indexOf(searchText.toLowerCase());
        if (index >= 0) {
            textArea.setCaretPosition(index);
            textArea.select(index, index + searchText.length());
            textArea.requestFocus();
        }
    }

    public void setFontSize(int size) {
        textArea.setFont(JStudioTheme.getCodeFont(size));
    }

    public void setWordWrap(boolean enabled) {
        textArea.setLineWrap(enabled);
        textArea.setWrapStyleWord(enabled);
    }

    public boolean highlightPC(String methodName, String methodDesc, int pc) {
        if (!loaded) {
            refresh();
        }

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

    public boolean highlightPCAdditive(String methodName, String methodDesc, int pc) {
        if (!loaded) {
            refresh();
        }

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
                addHighlight(lineNum);
                lastClickedLine = lineNum;
            } catch (Exception e) {
                // Ignore
            }
            return true;
        }

        return false;
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
        for (int lineNum : highlightedLines) {
            try {
                textArea.removeLineHighlight(lineNum);
            } catch (Exception e) {
                // Ignore
            }
        }
        highlightedLines.clear();
    }

    public void addHighlight(int lineNumber) {
        if (highlightedLines.add(lineNumber)) {
            try {
                Color highlightColor = JStudioTheme.getAccentSecondary();
                Color bgColor = new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue(), 100);
                textArea.addLineHighlight(lineNumber, bgColor);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void removeHighlight(int lineNumber) {
        if (highlightedLines.remove(lineNumber)) {
            try {
                textArea.removeLineHighlight(lineNumber);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void toggleHighlight(int lineNumber) {
        if (highlightedLines.contains(lineNumber)) {
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

    public Set<Integer> getHighlightedLines() {
        return new HashSet<>(highlightedLines);
    }
}
