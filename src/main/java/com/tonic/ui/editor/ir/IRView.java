package com.tonic.ui.editor.ir;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.*;

import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class IRView extends JPanel implements ThemeChangeListener {

    private static final String SYNTAX_STYLE_IR = "text/ir";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_IR, "com.tonic.ui.editor.ir.IRTokenMaker");
    }

    private final ClassEntryModel classEntry;
    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final LoadingOverlay loadingOverlay;

    private static final String METHOD_DIVIDER = "=========================================================================";

    @Getter
    private boolean loaded = false;
    private SwingWorker<String, Void> currentWorker;

    public IRView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SYNTAX_STYLE_IR);
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

        applyTheme();
        ThemeManager.getInstance().addThemeChangeListener(this);
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
        setTokenStyle(scheme, Token.OPERATOR, SyntaxColors.getIrOperator());
        setTokenStyle(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_CHAR, SyntaxColors.getJavaString());

        setTokenStyle(scheme, IRTokenMaker.TOKEN_COMMENT, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_DIVIDER, JStudioTheme.getAccentSecondary());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_HEADER, JStudioTheme.getAccent());

        setTokenStyle(scheme, IRTokenMaker.TOKEN_BLOCK, SyntaxColors.getIrBlock());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_PHI, SyntaxColors.getIrPhi());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_VALUE, SyntaxColors.getIrValue());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_OPERATOR, SyntaxColors.getIrOperator());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_CONTROL, SyntaxColors.getIrControl());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_INVOKE, SyntaxColors.getIrInvoke());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_FIELD, SyntaxColors.getIrGetField());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_CONST, SyntaxColors.getIrConstant());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_NEW, SyntaxColors.getIrNew());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_CAST, SyntaxColors.getIrCast());

        repaint();
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }

    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Lifting to SSA IR...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return generateIR();
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) {
                    return;
                }
                try {
                    String irText = get();
                    textArea.setText(irText);
                    textArea.setCaretPosition(0);
                    loaded = true;
                } catch (Exception e) {
                    textArea.setText("// Failed to generate IR: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private String generateIR() {
        StringBuilder sb = new StringBuilder();

        sb.append("// Class: ").append(classEntry.getClassName()).append("\n");
        sb.append("// Super: ").append(classEntry.getSuperClassName()).append("\n");
        if (!classEntry.getInterfaceNames().isEmpty()) {
            sb.append("// Implements: ").append(String.join(", ", classEntry.getInterfaceNames())).append("\n");
        }
        sb.append("\n");

        SSA ssa = new SSA(classEntry.getClassFile().getConstPool());

        int methodIndex = 0;
        int totalMethods = classEntry.getMethods().size();
        for (MethodEntryModel methodModel : classEntry.getMethods()) {
            methodIndex++;
            MethodEntry method = methodModel.getMethodEntry();

            sb.append("\n// ").append(METHOD_DIVIDER).append("\n");
            sb.append("// Method ").append(methodIndex).append(" of ").append(totalMethods).append("\n");
            sb.append("// ").append(METHOD_DIVIDER).append("\n\n");

            sb.append("//").append(formatAccessFlags(method.getAccess()));
            sb.append(" ").append(method.getName()).append(method.getDesc()).append("\n");

            if (method.getCodeAttribute() != null) {
                String cachedIR = methodModel.getIrCache();
                if (cachedIR != null) {
                    sb.append(cachedIR);
                } else {
                    try {
                        IRFormatter formatter = new IRFormatter(method, ssa);
                        String ir = formatter.format();
                        methodModel.setIrCache(ir);
                        sb.append(ir);
                    } catch (Exception e) {
                        sb.append("  // Error lifting: ").append(e.getMessage()).append("\n");
                    }
                }
            } else {
                sb.append("  // No code (abstract or native)\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatAccessFlags(int flags) {
        StringBuilder sb = new StringBuilder();
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

    private String lastSearch;

    public void showFindDialog() {
        String input = (String) JOptionPane.showInputDialog(
            this,
            "Find:",
            "Find",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            lastSearch
        );
        lastSearch = input;
        if (input != null && !input.isEmpty()) {
            scrollToText(input);
        }
    }

    public String getSelectedText() {
        return textArea.getSelectedText();
    }

    public void scrollToText(String searchText) {
        if (searchText == null || searchText.isEmpty()) return;

        SearchContext context = new SearchContext(searchText);
        context.setMatchCase(false);
        context.setWholeWord(false);
        SearchEngine.find(textArea, context);
    }

    public void setFontSize(int size) {
        textArea.setFont(JStudioTheme.getCodeFont(size));
    }

    public void setWordWrap(boolean enabled) {
        textArea.setLineWrap(enabled);
        textArea.setWrapStyleWord(enabled);
    }
}
