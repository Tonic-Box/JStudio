package com.tonic.ui.editor.bytecode;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.SyntaxColors;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.SwingUtilities;

/**
 * Bytecode view showing disassembled JVM instructions.
 */
public class BytecodeView extends JPanel implements ThemeManager.ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final JTextPane textPane;
    private final StyledDocument doc;

    // Styles for different instruction types
    private final SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet loadStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet storeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet invokeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet fieldStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet branchStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet stackStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet constStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet returnStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet newStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet offsetStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet commentStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet headerStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet dividerStyle = new SimpleAttributeSet();

    private final JScrollPane scrollPane;

    private static final String METHOD_DIVIDER = "=========================================================================";

    private boolean loaded = false;

    public BytecodeView(ClassEntryModel classEntry) {
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

        // Initialize styles
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
        updateStyle(loadStyle, SyntaxColors.getBcLoad());
        updateStyle(storeStyle, SyntaxColors.getBcStore());
        updateStyle(invokeStyle, SyntaxColors.getBcInvoke());
        updateStyle(fieldStyle, SyntaxColors.getBcField());
        updateStyle(branchStyle, SyntaxColors.getBcBranch());
        updateStyle(stackStyle, SyntaxColors.getBcStack());
        updateStyle(constStyle, SyntaxColors.getBcConst());
        updateStyle(returnStyle, SyntaxColors.getBcReturn());
        updateStyle(newStyle, SyntaxColors.getBcNew());
        updateStyle(offsetStyle, SyntaxColors.getBcOffset());
        updateStyle(commentStyle, JStudioTheme.getTextSecondary());
        updateStyle(headerStyle, JStudioTheme.getAccent());
        StyleConstants.setBold(headerStyle, true);
        updateStyle(dividerStyle, new Color(0x3D, 0x59, 0xA1));
    }

    private void updateStyle(SimpleAttributeSet style, Color color) {
        if (style == null) return;
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(12).getFamily());
        StyleConstants.setFontSize(style, 12);
    }

    private SimpleAttributeSet createStyle(Color color) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(12).getFamily());
        StyleConstants.setFontSize(style, 12);
        return style;
    }

    /**
     * Refresh/reload the bytecode view.
     */
    public void refresh() {
        try {
            doc.remove(0, doc.getLength());

            // Class header
            appendText("// Class: " + classEntry.getClassName() + "\n", commentStyle);
            appendText("// Super: " + classEntry.getSuperClassName() + "\n", commentStyle);
            if (!classEntry.getInterfaceNames().isEmpty()) {
                appendText("// Implements: " + String.join(", ", classEntry.getInterfaceNames()) + "\n", commentStyle);
            }
            appendText("\n", defaultStyle);

            // Methods
            int methodIndex = 0;
            int totalMethods = classEntry.getMethods().size();
            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                methodIndex++;
                MethodEntry method = methodModel.getMethodEntry();

                // Method divider
                appendText("\n// " + METHOD_DIVIDER + "\n", dividerStyle);
                appendText("// Method " + methodIndex + " of " + totalMethods + "\n", dividerStyle);
                appendText("// " + METHOD_DIVIDER + "\n\n", dividerStyle);

                // Method header
                appendText(formatAccessFlags(method.getAccess()), commentStyle);
                appendText(" " + method.getName() + method.getDesc() + "\n", headerStyle);

                // Method code
                if (method.getCodeAttribute() != null) {
                    BytecodeFormatter formatter = new BytecodeFormatter(method);
                    String bytecode = formatter.format();
                    formatBytecode(bytecode);
                } else {
                    appendText("  // No code (abstract or native)\n", commentStyle);
                }

                appendText("\n", defaultStyle);
            }

            textPane.setCaretPosition(0);
            loaded = true;

        } catch (Exception e) {
            try {
                doc.insertString(0, "// Error displaying bytecode: " + e.getMessage(), commentStyle);
            } catch (BadLocationException ex) {
                // Ignore
            }
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

    private void formatBytecode(String bytecode) {
        String[] lines = bytecode.split("\n");
        for (String line : lines) {
            formatBytcodeLine(line);
        }
    }

    private void formatBytcodeLine(String line) {
        if (line.trim().isEmpty()) {
            appendText("\n", defaultStyle);
            return;
        }

        // Check for comment lines
        if (line.trim().startsWith("//")) {
            appendText(line + "\n", commentStyle);
            return;
        }

        // Try to parse instruction line: "  offset: opcode operands"
        String trimmed = line.trim();
        int colonIndex = trimmed.indexOf(':');

        if (colonIndex > 0) {
            // Offset
            String offset = trimmed.substring(0, colonIndex);
            appendText("  " + offset, offsetStyle);
            appendText(": ", defaultStyle);

            // Rest of line (opcode + operands)
            String rest = trimmed.substring(colonIndex + 1).trim();
            String[] parts = rest.split("\\s+", 2);
            String opcode = parts[0];
            String operands = parts.length > 1 ? parts[1] : "";

            // Color based on opcode category
            SimpleAttributeSet opcodeStyle = getOpcodeStyle(opcode);
            appendText(opcode, opcodeStyle);

            if (!operands.isEmpty()) {
                appendText(" " + operands, defaultStyle);
            }
            appendText("\n", defaultStyle);
        } else {
            // Non-instruction line
            appendText(line + "\n", defaultStyle);
        }
    }

    private SimpleAttributeSet getOpcodeStyle(String opcode) {
        opcode = opcode.toLowerCase();

        // Load instructions
        if (opcode.contains("load") || opcode.equals("ldc") || opcode.equals("ldc_w") ||
                opcode.equals("ldc2_w") || opcode.startsWith("aload") || opcode.startsWith("iload") ||
                opcode.startsWith("lload") || opcode.startsWith("fload") || opcode.startsWith("dload")) {
            return loadStyle;
        }

        // Store instructions
        if (opcode.contains("store") || opcode.startsWith("astore") || opcode.startsWith("istore") ||
                opcode.startsWith("lstore") || opcode.startsWith("fstore") || opcode.startsWith("dstore")) {
            return storeStyle;
        }

        // Invoke instructions
        if (opcode.startsWith("invoke")) {
            return invokeStyle;
        }

        // Field access
        if (opcode.startsWith("get") || opcode.startsWith("put")) {
            return fieldStyle;
        }

        // Branch instructions
        if (opcode.startsWith("if") || opcode.equals("goto") || opcode.equals("goto_w") ||
                opcode.equals("jsr") || opcode.equals("jsr_w") || opcode.equals("ret") ||
                opcode.equals("tableswitch") || opcode.equals("lookupswitch")) {
            return branchStyle;
        }

        // Stack operations
        if (opcode.equals("pop") || opcode.equals("pop2") || opcode.equals("dup") ||
                opcode.equals("dup_x1") || opcode.equals("dup_x2") || opcode.equals("dup2") ||
                opcode.equals("dup2_x1") || opcode.equals("dup2_x2") || opcode.equals("swap")) {
            return stackStyle;
        }

        // Constants
        if (opcode.startsWith("iconst") || opcode.startsWith("lconst") || opcode.startsWith("fconst") ||
                opcode.startsWith("dconst") || opcode.equals("aconst_null") || opcode.startsWith("bipush") ||
                opcode.startsWith("sipush")) {
            return constStyle;
        }

        // Return
        if (opcode.contains("return") || opcode.equals("athrow")) {
            return returnStyle;
        }

        // New/allocation
        if (opcode.equals("new") || opcode.equals("newarray") || opcode.equals("anewarray") ||
                opcode.equals("multianewarray")) {
            return newStyle;
        }

        return defaultStyle;
    }

    private void appendText(String text, SimpleAttributeSet style) {
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    /**
     * Get the current text.
     */
    public String getText() {
        return textPane.getText();
    }

    /**
     * Copy current selection to clipboard.
     */
    public void copySelection() {
        String selected = textPane.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    /**
     * Go to a specific line.
     */
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

    /**
     * Show find dialog.
     */
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

    /**
     * Get the selected text.
     */
    public String getSelectedText() {
        return textPane.getSelectedText();
    }

    /**
     * Scroll to and highlight text.
     */
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

    /**
     * Set the font size.
     */
    public void setFontSize(int size) {
        textPane.setFont(JStudioTheme.getCodeFont(size));
        // Update styles to match
        updateStyleFontSize(size);
    }

    private void updateStyleFontSize(int size) {
        StyleConstants.setFontSize(defaultStyle, size);
        StyleConstants.setFontSize(loadStyle, size);
        StyleConstants.setFontSize(storeStyle, size);
        StyleConstants.setFontSize(invokeStyle, size);
        StyleConstants.setFontSize(fieldStyle, size);
        StyleConstants.setFontSize(branchStyle, size);
        StyleConstants.setFontSize(stackStyle, size);
        StyleConstants.setFontSize(constStyle, size);
        StyleConstants.setFontSize(returnStyle, size);
        StyleConstants.setFontSize(newStyle, size);
        StyleConstants.setFontSize(offsetStyle, size);
        StyleConstants.setFontSize(commentStyle, size);
        StyleConstants.setFontSize(headerStyle, size);
        StyleConstants.setFontSize(dividerStyle, size);
    }

    /**
     * Set word wrap enabled/disabled.
     */
    public void setWordWrap(boolean enabled) {
        // JTextPane doesn't have built-in word wrap toggle like JTextArea
        // We can achieve this by modifying the viewport behavior
        // For now, this is a no-op for JTextPane-based views
    }
}
