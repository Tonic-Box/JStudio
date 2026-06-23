package com.tonic.ui.editor.llvm;

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

/**
 * Editor view that shows the class lowered to textual LLVM IR. Lowering runs off the EDT; methods
 * outside the lowerer's computational subset are annotated rather than failing the view.
 */
public class LLVMView extends AbstractTextView {

    private static final String SYNTAX_STYLE_LLVM = "text/llvm";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_LLVM, "com.tonic.ui.editor.llvm.LlvmTokenMaker");
    }

    private final ClassEntryModel classEntry;

    private static final String METHOD_DIVIDER = "=========================================================================";

    public LLVMView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        initTextArea(SYNTAX_STYLE_LLVM);

        add(overlayWrap(scrollPane), BorderLayout.CENTER);
    }

    @Override
    protected void applyChildThemes() {
        applyTextTheme();

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

    @Override
    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Lowering to LLVM IR...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
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
        currentWorker = worker;
        worker.execute();
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
