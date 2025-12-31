package com.tonic.ui.editor.ir;

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

/**
 * IR view showing SSA-form intermediate representation.
 */
public class IRView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final JTextPane textPane;
    private final StyledDocument doc;

    // Styles for different IR elements
    private final SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet blockStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet phiStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet valueStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet operatorStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet controlStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet invokeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet fieldStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet constStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet commentStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet headerStyle = new SimpleAttributeSet();

    private final JScrollPane scrollPane;

    private static final String METHOD_DIVIDER = "=========================================================================";

    @Getter
    private boolean loaded = false;

    public IRView(ClassEntryModel classEntry) {
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
        updateStyle(blockStyle, SyntaxColors.getIrBlock());
        StyleConstants.setBold(blockStyle, true);
        updateStyle(phiStyle, SyntaxColors.getIrPhi());
        updateStyle(valueStyle, SyntaxColors.getIrValue());
        updateStyle(operatorStyle, SyntaxColors.getIrOperator());
        updateStyle(controlStyle, SyntaxColors.getIrControl());
        updateStyle(invokeStyle, SyntaxColors.getBcInvoke());
        updateStyle(fieldStyle, SyntaxColors.getBcField());
        updateStyle(constStyle, SyntaxColors.getBcConst());
        updateStyle(commentStyle, JStudioTheme.getTextSecondary());
        updateStyle(headerStyle, JStudioTheme.getAccent());
        StyleConstants.setBold(headerStyle, true);
    }

    private void updateStyle(SimpleAttributeSet style, Color color) {
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(12).getFamily());
        StyleConstants.setFontSize(style, 12);
    }

    /**
     * Refresh/reload the IR view.
     */
    public void refresh() {
        // Show loading message
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, "// Lifting to SSA IR...\n", commentStyle);
        } catch (BadLocationException e) {
            // Ignore
        }

        // Lift IR in background
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return generateIR();
            }

            @Override
            protected void done() {
                try {
                    String ir = get();
                    doc.remove(0, doc.getLength());
                    formatIR(ir);
                    textPane.setCaretPosition(0);
                    loaded = true;
                } catch (Exception e) {
                    try {
                        doc.remove(0, doc.getLength());
                        doc.insertString(0, "// Failed to generate IR: " + e.getMessage(), commentStyle);
                    } catch (BadLocationException ex) {
                        // Ignore
                    }
                }
            }
        };

        worker.execute();
    }

    private String generateIR() {
        StringBuilder sb = new StringBuilder();

        // Class header
        sb.append("// Class: ").append(classEntry.getClassName()).append("\n");
        sb.append("// Super: ").append(classEntry.getSuperClassName()).append("\n");
        if (!classEntry.getInterfaceNames().isEmpty()) {
            sb.append("// Implements: ").append(String.join(", ", classEntry.getInterfaceNames())).append("\n");
        }
        sb.append("\n");

        // Create SSA processor
        SSA ssa = new SSA(classEntry.getClassFile().getConstPool());

        // Methods
        int methodIndex = 0;
        int totalMethods = classEntry.getMethods().size();
        for (MethodEntryModel methodModel : classEntry.getMethods()) {
            methodIndex++;
            MethodEntry method = methodModel.getMethodEntry();

            // Method divider
            sb.append("\n// ").append(METHOD_DIVIDER).append("\n");
            sb.append("// Method ").append(methodIndex).append(" of ").append(totalMethods).append("\n");
            sb.append("// ").append(METHOD_DIVIDER).append("\n\n");

            // Method header
            sb.append("//").append(formatAccessFlags(method.getAccess()));
            sb.append(" ").append(method.getName()).append(method.getDesc()).append("\n");

            // Method IR
            if (method.getCodeAttribute() != null) {
                // Check cache first
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

    private void formatIR(String ir) {
        String[] lines = ir.split("\n");
        for (String line : lines) {
            formatIRLine(line);
        }
    }

    private void formatIRLine(String line) {
        if (line.trim().isEmpty()) {
            appendText("\n", defaultStyle);
            return;
        }

        String trimmed = line.trim();

        // Comment lines
        if (trimmed.startsWith("//")) {
            appendText(line + "\n", commentStyle);
            return;
        }

        // Block headers (BLOCK B0:)
        if (trimmed.startsWith("BLOCK ")) {
            appendText(line + "\n", blockStyle);
            return;
        }

        // PHI instructions
        if (trimmed.startsWith("PHI:")) {
            formatPhiLine(line);
            return;
        }

        // Regular IR instructions
        formatInstructionLine(line);
    }

    private void formatPhiLine(String line) {
        int phiIdx = line.indexOf("PHI:");
        if (phiIdx >= 0) {
            appendText(line.substring(0, phiIdx), defaultStyle);
            appendText("PHI", phiStyle);
            appendText(": ", defaultStyle);
            String rest = line.substring(phiIdx + 4);
            formatPhiBody(rest);
        } else {
            appendText(line + "\n", phiStyle);
        }
    }

    private void formatPhiBody(String body) {
        // phi(B0:v1, B1:v2)
        appendText(body + "\n", valueStyle);
    }

    private void formatInstructionLine(String line) {
        String trimmed = line.trim();

        // Find the leading whitespace
        int leadingSpaces = line.length() - trimmed.length();
        if (leadingSpaces > 0) {
            appendText(line.substring(0, leadingSpaces), defaultStyle);
        }

        // Check for result assignment (v1 = ...)
        int eqIdx = trimmed.indexOf(" = ");
        if (eqIdx > 0) {
            String result = trimmed.substring(0, eqIdx);
            appendText(result, valueStyle);
            appendText(" = ", operatorStyle);
            String rest = trimmed.substring(eqIdx + 3);
            formatInstructionBody(rest);
        } else {
            formatInstructionBody(trimmed);
        }
    }

    private void formatInstructionBody(String body) {
        // Determine instruction type and style
        SimpleAttributeSet style = getInstructionStyle(body);

        // Parse keyword and operands
        int spaceIdx = body.indexOf(' ');
        if (spaceIdx > 0) {
            String keyword = body.substring(0, spaceIdx);
            String operands = body.substring(spaceIdx);
            appendText(keyword, style);
            appendText(operands + "\n", defaultStyle);
        } else {
            appendText(body + "\n", style);
        }
    }

    private SimpleAttributeSet getInstructionStyle(String instruction) {
        String lower = instruction.toLowerCase();

        // Control flow
        if (lower.startsWith("if ") || lower.startsWith("goto ") ||
                lower.startsWith("return") || lower.startsWith("switch ") ||
                lower.startsWith("throw")) {
            return controlStyle;
        }

        // Invocations
        if (lower.startsWith("invoke")) {
            return invokeStyle;
        }

        // Field access
        if (lower.startsWith("getfield") || lower.startsWith("putfield") ||
                lower.startsWith("getstatic") || lower.startsWith("putstatic")) {
            return fieldStyle;
        }

        // Constants
        if (lower.startsWith("const ")) {
            return constStyle;
        }

        // PHI
        if (lower.startsWith("phi")) {
            return phiStyle;
        }

        // Operators (binary, unary)
        if (lower.startsWith("add") || lower.startsWith("sub") ||
                lower.startsWith("mul") || lower.startsWith("div") ||
                lower.startsWith("rem") || lower.startsWith("neg") ||
                lower.startsWith("and") || lower.startsWith("or") ||
                lower.startsWith("xor") || lower.startsWith("shl") ||
                lower.startsWith("shr") || lower.startsWith("ushr")) {
            return operatorStyle;
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
        StyleConstants.setFontSize(blockStyle, size);
        StyleConstants.setFontSize(phiStyle, size);
        StyleConstants.setFontSize(valueStyle, size);
        StyleConstants.setFontSize(operatorStyle, size);
        StyleConstants.setFontSize(controlStyle, size);
        StyleConstants.setFontSize(invokeStyle, size);
        StyleConstants.setFontSize(fieldStyle, size);
        StyleConstants.setFontSize(constStyle, size);
        StyleConstants.setFontSize(commentStyle, size);
        StyleConstants.setFontSize(headerStyle, size);
    }

    /**
     * Set word wrap enabled/disabled.
     */
    public void setWordWrap(boolean enabled) {
        // JTextPane doesn't have built-in word wrap toggle like JTextArea
        // For now, this is a no-op for JTextPane-based views
    }
}
