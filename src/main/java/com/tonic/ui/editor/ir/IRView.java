package com.tonic.ui.editor.ir;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.MethodEntry;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.ui.editor.view.AbstractTextView;
import com.tonic.ui.theme.*;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;

public class IRView extends AbstractTextView {

    private static final String SYNTAX_STYLE_IR = "text/ir";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_IR, "com.tonic.ui.editor.ir.IRTokenMaker");
    }

    private final ClassEntryModel classEntry;

    private static final String METHOD_DIVIDER = "=========================================================================";

    public IRView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        initTextArea(SYNTAX_STYLE_IR);

        add(overlayWrap(scrollPane), BorderLayout.CENTER);
    }

    @Override
    protected void applyChildThemes() {
        applyTextTheme();

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

    @Override
    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Lifting to SSA IR...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
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
        currentWorker = worker;
        worker.execute();
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

    private String lastSearch;

    @Override
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

    @Override
    public void scrollToText(String searchText) {
        if (searchText == null || searchText.isEmpty()) return;

        SearchContext context = new SearchContext(searchText);
        context.setMatchCase(false);
        context.setWholeWord(false);
        SearchEngine.find(textArea, context);
    }
}
