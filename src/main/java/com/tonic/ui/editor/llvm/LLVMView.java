package com.tonic.ui.editor.llvm;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
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

/**
 * Editor view that shows the class lowered to textual LLVM IR. Lowering runs off the EDT; methods
 * outside the lowerer's computational subset are annotated rather than failing the view.
 */
public class LLVMView extends JPanel implements ThemeChangeListener {

    private static final String SYNTAX_STYLE_LLVM = "text/llvm";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_LLVM, "com.tonic.ui.editor.llvm.LlvmTokenMaker");
    }

    private final ClassEntryModel classEntry;
    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final LoadingOverlay loadingOverlay;

    private static final String METHOD_DIVIDER = "=========================================================================";

    @Getter
    private boolean loaded = false;
    private SwingWorker<String, Void> currentWorker;

    public LLVMView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SYNTAX_STYLE_LLVM);
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

        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_COMMENT, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_STRING, SyntaxColors.getJavaString());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_NUMBER, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_LOCAL, SyntaxColors.getIrSsaValue());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_GLOBAL, SyntaxColors.getIrInvoke());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_CONTROL, SyntaxColors.getIrControl());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_OPCODE, SyntaxColors.getIrBinaryOp());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_TYPE, SyntaxColors.getIrType());
        setTokenStyle(scheme, LlvmTokenMaker.TOKEN_PREDICATE, SyntaxColors.getIrOperator());

        repaint();
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }

    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Lowering to LLVM IR...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return generateLLVM();
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) {
                    return;
                }
                try {
                    String text = get();
                    textArea.setText(text);
                    textArea.setCaretPosition(0);
                    loaded = true;
                } catch (Exception e) {
                    textArea.setText("; Failed to generate LLVM IR: " + e.getMessage());
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

    private String generateLLVM() {
        StringBuilder sb = new StringBuilder();

        sb.append("; Class: ").append(classEntry.getClassName()).append("\n");
        sb.append("; Super: ").append(classEntry.getSuperClassName()).append("\n");
        if (!classEntry.getInterfaceNames().isEmpty()) {
            sb.append("; Implements: ").append(String.join(", ", classEntry.getInterfaceNames())).append("\n");
        }
        sb.append("\n");

        SSA ssa = new SSA(classEntry.getClassFile().getConstPool());

        int methodIndex = 0;
        int totalMethods = classEntry.getMethods().size();
        for (MethodEntryModel methodModel : classEntry.getMethods()) {
            methodIndex++;
            MethodEntry method = methodModel.getMethodEntry();

            sb.append("\n; ").append(METHOD_DIVIDER).append("\n");
            sb.append("; Method ").append(methodIndex).append(" of ").append(totalMethods).append("\n");
            sb.append("; ").append(METHOD_DIVIDER).append("\n\n");

            sb.append(";").append(formatAccessFlags(method.getAccess()));
            sb.append(" ").append(method.getName()).append(method.getDesc()).append("\n");

            String cached = methodModel.getLlvmCache();
            if (cached != null) {
                sb.append(cached);
            } else {
                LLVMFormatter formatter = new LLVMFormatter(method, ssa);
                String llvm = formatter.format();
                methodModel.setLlvmCache(llvm);
                sb.append(llvm);
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
