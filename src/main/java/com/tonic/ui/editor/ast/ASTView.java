package com.tonic.ui.editor.ast;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.source.ast.ASTPrinter;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.MethodEntry;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.ui.editor.view.AbstractTextView;
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

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ASTView extends AbstractTextView {

    private static final String SYNTAX_STYLE_AST = "text/ast";
    private static final String TEXT_VIEW = "TEXT";
    private static final String TREE_VIEW = "TREE";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_AST, "com.tonic.ui.editor.ast.ASTTokenMaker");
    }

    private final ClassEntryModel classEntry;

    private final JTree astTree;
    private final ASTTreeModel treeModel;
    private final JScrollPane treeScrollPane;

    private final JPanel contentPanel;
    private final CardLayout cardLayout;

    private final JToolBar toolbar;
    private final JToggleButton textViewBtn;
    private final JToggleButton treeViewBtn;
    private final JButton expandAllBtn;
    private final JButton collapseAllBtn;

    private static final String METHOD_DIVIDER = "=========================================================================";

    @Getter
    private boolean showingTreeView = false;

    public ASTView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SYNTAX_STYLE_AST);
        textArea.setEditable(false);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(JStudioTheme.getCodeFont(12));
        textArea.setCodeFoldingEnabled(false);
        textArea.setBracketMatchingEnabled(false);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(null);

        treeModel = new ASTTreeModel();
        astTree = new JTree(treeModel);
        astTree.setCellRenderer(new ASTTreeCellRenderer());
        astTree.setBackground(JStudioTheme.getBgTertiary());
        astTree.setRowHeight(20);
        astTree.setRootVisible(true);
        astTree.setShowsRootHandles(true);

        treeScrollPane = new JScrollPane(astTree);
        treeScrollPane.setBorder(null);
        treeScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.add(scrollPane, TEXT_VIEW);
        contentPanel.add(treeScrollPane, TREE_VIEW);

        add(overlayWrap(contentPanel), BorderLayout.CENTER);

        textViewBtn = (JToggleButton) toolbar.getComponentAtIndex(0);
        treeViewBtn = (JToggleButton) toolbar.getComponentAtIndex(1);
        expandAllBtn = (JButton) toolbar.getComponentAtIndex(3);
        collapseAllBtn = (JButton) toolbar.getComponentAtIndex(4);

        updateToolbarState();
    }

    private JToolBar createToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBackground(JStudioTheme.getBgSecondary());
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        ButtonGroup viewGroup = new ButtonGroup();

        JToggleButton textBtn = new JToggleButton("Text");
        styleToggleButton(textBtn);
        textBtn.setSelected(true);
        textBtn.addActionListener(e -> switchToView(TEXT_VIEW));
        viewGroup.add(textBtn);
        tb.add(textBtn);

        JToggleButton treeBtn = new JToggleButton("Tree");
        styleToggleButton(treeBtn);
        treeBtn.addActionListener(e -> switchToView(TREE_VIEW));
        viewGroup.add(treeBtn);
        tb.add(treeBtn);

        tb.addSeparator();

        JButton expandBtn = new JButton("Expand All");
        styleButton(expandBtn);
        expandBtn.addActionListener(e -> expandAll());
        expandBtn.setEnabled(false);
        tb.add(expandBtn);

        JButton collapseBtn = new JButton("Collapse All");
        styleButton(collapseBtn);
        collapseBtn.addActionListener(e -> collapseAll());
        collapseBtn.setEnabled(false);
        tb.add(collapseBtn);

        return tb;
    }

    private void styleToggleButton(JToggleButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(JStudioTheme.getBgSecondary());
        btn.setForeground(JStudioTheme.getTextPrimary());
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        btn.setFont(JStudioTheme.getCodeFont(11));
    }

    private void styleButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(JStudioTheme.getBgSecondary());
        btn.setForeground(JStudioTheme.getTextPrimary());
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        btn.setFont(JStudioTheme.getCodeFont(11));
    }

    private void switchToView(String viewName) {
        showingTreeView = TREE_VIEW.equals(viewName);
        cardLayout.show(contentPanel, viewName);
        updateToolbarState();
    }

    private void updateToolbarState() {
        expandAllBtn.setEnabled(showingTreeView);
        collapseAllBtn.setEnabled(showingTreeView);
    }

    private void expandAll() {
        for (int i = 0; i < astTree.getRowCount(); i++) {
            astTree.expandRow(i);
        }
    }

    private void collapseAll() {
        for (int i = astTree.getRowCount() - 1; i >= 1; i--) {
            astTree.collapseRow(i);
        }
    }

    @Override
    protected void applyChildThemes() {
        applyTextTheme();

        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        for (Component c : toolbar.getComponents()) {
            if (c instanceof JToggleButton) {
                styleToggleButton((JToggleButton) c);
            } else if (c instanceof JButton) {
                styleButton((JButton) c);
            }
        }

        SyntaxScheme scheme = textArea.getSyntaxScheme();

        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.WHITESPACE, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, Token.OPERATOR, JStudioTheme.getTextSecondary());

        setTokenStyle(scheme, ASTTokenMaker.TOKEN_COMMENT, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_DIVIDER, JStudioTheme.getAccentSecondary());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_HEADER, JStudioTheme.getAccent());

        setTokenStyle(scheme, ASTTokenMaker.TOKEN_NODE, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_EXPR_NODE, SyntaxColors.getJavaMethod());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_TYPE_NODE, SyntaxColors.getJavaType());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_LABEL, SyntaxColors.getIrValue());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_STRING, SyntaxColors.getJavaString());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_NUMBER, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, ASTTokenMaker.TOKEN_KEYWORD, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.LITERAL_CHAR, SyntaxColors.getJavaString());

        astTree.setBackground(JStudioTheme.getBgTertiary());
        treeScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

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
        loadingOverlay.showLoading("Decompiling to AST...");
        treeModel.clear();

        SwingWorker<ASTResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ASTResult doInBackground() {
                return generateASTBoth();
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) {
                    return;
                }
                try {
                    ASTResult result = get();
                    textArea.setText(result.textOutput);
                    textArea.setCaretPosition(0);
                    treeModel.loadClass(classEntry.getClassName(), result.methodEntries);
                    loaded = true;
                } catch (Exception e) {
                    textArea.setText("// Failed to generate AST: " + e.getMessage());
                }
            }
        };
        currentWorker = worker;
        worker.execute();
    }

    private static class ASTResult {
        String textOutput;
        List<ASTTreeModel.MethodASTEntry> methodEntries;
    }

    private ASTResult generateASTBoth() {
        ASTResult result = new ASTResult();
        result.methodEntries = new ArrayList<>();

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
            sb.append(" ").append(method.getName()).append(method.getDesc()).append("\n\n");

            BlockStmt body = null;
            if (method.getCodeAttribute() != null) {
                try {
                    IRMethod ir = ssa.lift(method);
                    body = MethodRecoverer.recoverMethod(ir, method);
                    if (body != null) {
                        String astOutput = ASTPrinter.format(body);
                        sb.append(astOutput);
                    } else {
                        sb.append("  // Could not decompile method\n");
                    }
                } catch (Exception e) {
                    sb.append("  // Error decompiling: ").append(e.getMessage()).append("\n");
                }
            } else {
                sb.append("  // No code (abstract or native)\n");
            }

            result.methodEntries.add(new ASTTreeModel.MethodASTEntry(method, body));
            sb.append("\n");
        }

        result.textOutput = sb.toString();
        return result;
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

    @Override
    public void setFontSize(int size) {
        textArea.setFont(JStudioTheme.getCodeFont(size));
        astTree.setRowHeight(size + 8);
    }

    public void setShowTreeView(boolean showTree) {
        if (showTree) {
            treeViewBtn.setSelected(true);
            switchToView(TREE_VIEW);
        } else {
            textViewBtn.setSelected(true);
            switchToView(TEXT_VIEW);
        }
    }
}
