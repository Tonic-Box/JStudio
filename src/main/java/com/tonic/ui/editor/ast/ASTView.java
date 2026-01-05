package com.tonic.ui.editor.ast;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.source.ast.ASTPrinter;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.*;
import lombok.Getter;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

public class ASTView extends JPanel implements ThemeChangeListener {

    private static final String TEXT_VIEW = "TEXT";
    private static final String TREE_VIEW = "TREE";

    private final ClassEntryModel classEntry;

    private final JTextPane textPane;
    private final StyledDocument doc;
    private final JScrollPane textScrollPane;

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

    private final SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet nodeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet typeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet labelStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet valueStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet bracketStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet commentStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet headerStyle = new SimpleAttributeSet();

    private static final String METHOD_DIVIDER = "=========================================================================";

    @Getter
    private boolean loaded = false;
    private boolean showingTreeView = false;

    public ASTView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setCaretColor(JStudioTheme.getTextPrimary());
        textPane.setFont(JStudioTheme.getCodeFont(12));

        doc = textPane.getStyledDocument();
        initStyles();

        textScrollPane = new JScrollPane(textPane);
        textScrollPane.setBorder(null);
        textScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

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
        contentPanel.add(textScrollPane, TEXT_VIEW);
        contentPanel.add(treeScrollPane, TREE_VIEW);

        add(contentPanel, BorderLayout.CENTER);

        textViewBtn = (JToggleButton) toolbar.getComponentAtIndex(0);
        treeViewBtn = (JToggleButton) toolbar.getComponentAtIndex(1);
        expandAllBtn = (JButton) toolbar.getComponentAtIndex(3);
        collapseAllBtn = (JButton) toolbar.getComponentAtIndex(4);

        updateToolbarState();

        ThemeManager.getInstance().addThemeChangeListener(this);
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
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());

        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        for (Component c : toolbar.getComponents()) {
            if (c instanceof JToggleButton) {
                styleToggleButton((JToggleButton) c);
            } else if (c instanceof JButton) {
                styleButton((JButton) c);
            }
        }

        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setCaretColor(JStudioTheme.getTextPrimary());

        textScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        astTree.setBackground(JStudioTheme.getBgTertiary());
        treeScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        initStyles();
        repaint();
    }

    private void initStyles() {
        updateStyle(defaultStyle, JStudioTheme.getTextPrimary());
        updateStyle(nodeStyle, SyntaxColors.getJavaKeyword());
        StyleConstants.setBold(nodeStyle, true);
        updateStyle(typeStyle, SyntaxColors.getJavaType());
        updateStyle(labelStyle, SyntaxColors.getIrValue());
        updateStyle(valueStyle, SyntaxColors.getJavaString());
        updateStyle(bracketStyle, JStudioTheme.getTextSecondary());
        updateStyle(commentStyle, JStudioTheme.getTextSecondary());
        updateStyle(headerStyle, JStudioTheme.getAccent());
        StyleConstants.setBold(headerStyle, true);
    }

    private void updateStyle(SimpleAttributeSet style, Color color) {
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(12).getFamily());
        StyleConstants.setFontSize(style, 12);
    }

    public void refresh() {
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, "// Decompiling to AST...\n", commentStyle);
        } catch (BadLocationException e) {
        }

        treeModel.clear();

        SwingWorker<ASTResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ASTResult doInBackground() {
                return generateASTBoth();
            }

            @Override
            protected void done() {
                try {
                    ASTResult result = get();
                    doc.remove(0, doc.getLength());
                    formatAST(result.textOutput);
                    textPane.setCaretPosition(0);

                    treeModel.loadClass(classEntry.getClassName(), result.methodEntries);

                    loaded = true;
                } catch (Exception e) {
                    try {
                        doc.remove(0, doc.getLength());
                        doc.insertString(0, "// Failed to generate AST: " + e.getMessage(), commentStyle);
                    } catch (BadLocationException ex) {
                    }
                }
            }
        };

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

    private void formatAST(String ast) {
        String[] lines = ast.split("\n");
        for (String line : lines) {
            formatASTLine(line);
        }
    }

    private void formatASTLine(String line) {
        if (line.trim().isEmpty()) {
            appendText("\n", defaultStyle);
            return;
        }

        String trimmed = line.trim();

        if (trimmed.startsWith("//")) {
            appendText(line + "\n", commentStyle);
            return;
        }

        int leadingSpaces = line.length() - trimmed.length();
        if (leadingSpaces > 0) {
            appendText(line.substring(0, leadingSpaces), defaultStyle);
        }

        formatASTContent(trimmed);
    }

    private void formatASTContent(String content) {
        int parenIdx = content.indexOf('(');
        int colonIdx = content.indexOf(':');

        if (parenIdx > 0 && (colonIdx < 0 || parenIdx < colonIdx)) {
            String nodeName = content.substring(0, parenIdx);
            appendText(nodeName, nodeStyle);

            String rest = content.substring(parenIdx);
            formatParenContent(rest);
        } else if (colonIdx > 0) {
            String label = content.substring(0, colonIdx);
            appendText(label, labelStyle);
            appendText(":", defaultStyle);
            String rest = content.substring(colonIdx + 1);
            if (!rest.isEmpty()) {
                appendText(rest + "\n", defaultStyle);
            } else {
                appendText("\n", defaultStyle);
            }
        } else {
            appendText(content + "\n", defaultStyle);
        }
    }

    private void formatParenContent(String content) {
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (!inString) {
                if (c == '(' || c == '[') {
                    if (current.length() > 0) {
                        appendText(current.toString(), defaultStyle);
                        current = new StringBuilder();
                    }
                    appendText(String.valueOf(c), bracketStyle);
                    depth++;
                    continue;
                } else if (c == ')' || c == ']') {
                    if (current.length() > 0) {
                        formatValue(current.toString());
                        current = new StringBuilder();
                    }
                    appendText(String.valueOf(c), bracketStyle);
                    depth--;
                    continue;
                } else if (c == ':' && depth == 0) {
                    if (current.length() > 0) {
                        formatValue(current.toString());
                        current = new StringBuilder();
                    }
                    appendText(" : ", defaultStyle);

                    String rest = content.substring(i + 1).trim();
                    if (!rest.isEmpty()) {
                        appendText(rest, typeStyle);
                    }
                    appendText("\n", defaultStyle);
                    return;
                }
            }

            current.append(c);
        }

        if (current.length() > 0) {
            formatValue(current.toString());
        }
        appendText("\n", defaultStyle);
    }

    private void formatValue(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;

        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            appendText(trimmed, valueStyle);
        } else if (trimmed.matches("-?\\d+(\\.\\d+)?[fFdDlL]?")) {
            appendText(trimmed, valueStyle);
        } else if (trimmed.equals("null") || trimmed.equals("true") || trimmed.equals("false")) {
            appendText(trimmed, valueStyle);
        } else {
            appendText(trimmed, defaultStyle);
        }
    }

    private void appendText(String text, SimpleAttributeSet style) {
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
        }
    }

    public String getText() {
        return textPane.getText();
    }

    public void copySelection() {
        String selected = textPane.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public void goToLine(int line) {
        try {
            int offset = textPane.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
            textPane.setCaretPosition(offset);
            textPane.requestFocus();
        } catch (Exception e) {
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
        return textPane.getSelectedText();
    }

    public void scrollToText(String searchText) {
        if (searchText == null || searchText.isEmpty()) return;

        String text = textPane.getText();
        int index = text.toLowerCase().indexOf(searchText.toLowerCase());
        if (index >= 0) {
            textPane.setCaretPosition(index);
            textPane.select(index, index + searchText.length());
            textPane.requestFocus();
        }
    }

    public void setFontSize(int size) {
        textPane.setFont(JStudioTheme.getCodeFont(size));
        updateStyleFontSize(size);
        astTree.setRowHeight(size + 8);
    }

    private void updateStyleFontSize(int size) {
        StyleConstants.setFontSize(defaultStyle, size);
        StyleConstants.setFontSize(nodeStyle, size);
        StyleConstants.setFontSize(typeStyle, size);
        StyleConstants.setFontSize(labelStyle, size);
        StyleConstants.setFontSize(valueStyle, size);
        StyleConstants.setFontSize(bracketStyle, size);
        StyleConstants.setFontSize(commentStyle, size);
        StyleConstants.setFontSize(headerStyle, size);
    }

    public void setWordWrap(boolean enabled) {
    }

    public boolean isShowingTreeView() {
        return showingTreeView;
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
