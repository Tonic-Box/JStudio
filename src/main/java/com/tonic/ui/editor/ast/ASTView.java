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

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.SwingUtilities;

public class ASTView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final JTextPane textPane;
    private final StyledDocument doc;

    private final SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet nodeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet typeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet labelStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet valueStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet bracketStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet commentStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet headerStyle = new SimpleAttributeSet();

    private final JScrollPane scrollPane;

    private static final String METHOD_DIVIDER = "=========================================================================";

    @Getter
    private boolean loaded = false;

    public ASTView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setCaretColor(JStudioTheme.getTextPrimary());
        textPane.setFont(JStudioTheme.getCodeFont(12));

        doc = textPane.getStyledDocument();

        initStyles();

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        add(scrollPane, BorderLayout.CENTER);

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());

        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setCaretColor(JStudioTheme.getTextPrimary());

        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

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
            // Ignore
        }

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return generateAST();
            }

            @Override
            protected void done() {
                try {
                    String ast = get();
                    doc.remove(0, doc.getLength());
                    formatAST(ast);
                    textPane.setCaretPosition(0);
                    loaded = true;
                } catch (Exception e) {
                    try {
                        doc.remove(0, doc.getLength());
                        doc.insertString(0, "// Failed to generate AST: " + e.getMessage(), commentStyle);
                    } catch (BadLocationException ex) {
                        // Ignore
                    }
                }
            }
        };

        worker.execute();
    }

    private String generateAST() {
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

            if (method.getCodeAttribute() != null) {
                try {
                    IRMethod ir = ssa.lift(method);
                    BlockStmt body = MethodRecoverer.recoverMethod(ir, method);
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
            // Ignore
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
        // JTextPane doesn't have built-in word wrap toggle
    }
}
