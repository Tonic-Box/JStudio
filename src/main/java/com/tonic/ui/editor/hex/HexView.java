package com.tonic.ui.editor.hex;

import com.tonic.parser.ClassFile;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Hex view showing raw class file bytes in traditional hex dump format.
 */
public class HexView extends JPanel implements ThemeManager.ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final JTextPane textPane;
    private final JScrollPane scrollPane;
    private final JPanel headerPanel;
    private final JLabel headerLabel;

    private static final int BYTES_PER_LINE = 16;
    private boolean loaded = false;

    // Style names
    private static final String STYLE_OFFSET = "offset";
    private static final String STYLE_HEX = "hex";
    private static final String STYLE_ASCII = "ascii";
    private static final String STYLE_SEPARATOR = "separator";
    private static final String STYLE_HIGHLIGHT = "highlight";

    public HexView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        // Create styled text pane
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(JStudioTheme.getCodeFont(12));
        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setCaretColor(JStudioTheme.getTextPrimary());

        // Setup styles
        setupStyles();

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        add(scrollPane, BorderLayout.CENTER);

        // Header showing what the columns mean
        headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(JStudioTheme.getBgSecondary());
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        headerLabel = new JLabel("  Offset    00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   ASCII");
        headerLabel.setFont(JStudioTheme.getCodeFont(12));
        headerLabel.setForeground(JStudioTheme.getTextSecondary());
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);

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

        headerPanel.setBackground(JStudioTheme.getBgSecondary());
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        headerLabel.setForeground(JStudioTheme.getTextSecondary());

        setupStyles();
        repaint();
    }

    private void setupStyles() {
        StyledDocument doc = textPane.getStyledDocument();

        // Offset style (address column)
        Style offsetStyle = doc.addStyle(STYLE_OFFSET, null);
        StyleConstants.setForeground(offsetStyle, JStudioTheme.getSuccess());

        // Hex bytes style
        Style hexStyle = doc.addStyle(STYLE_HEX, null);
        StyleConstants.setForeground(hexStyle, JStudioTheme.getTextPrimary());

        // ASCII style
        Style asciiStyle = doc.addStyle(STYLE_ASCII, null);
        StyleConstants.setForeground(asciiStyle, JStudioTheme.getAccent());

        // Separator style
        Style separatorStyle = doc.addStyle(STYLE_SEPARATOR, null);
        StyleConstants.setForeground(separatorStyle, JStudioTheme.getTextSecondary());

        // Highlight style for special bytes (magic number, etc.)
        Style highlightStyle = doc.addStyle(STYLE_HIGHLIGHT, null);
        StyleConstants.setForeground(highlightStyle, JStudioTheme.getWarning());
    }

    /**
     * Refresh/reload the hex view.
     */
    public void refresh() {
        if (loaded) {
            return; // Already loaded
        }

        textPane.setText("");

        try {
            // Serialize the class file to bytes
            ClassFile cf = classEntry.getClassFile();
            byte[] bytes = cf.write();
            displayHexDump(bytes);
            loaded = true;
        } catch (Exception e) {
            try {
                StyledDocument doc = textPane.getStyledDocument();
                doc.insertString(0, "Failed to read class file bytes: " + e.getMessage(), null);
            } catch (BadLocationException ex) {
                // Ignore
            }
        }
    }

    private void displayHexDump(byte[] bytes) {
        StyledDocument doc = textPane.getStyledDocument();

        try {
            StringBuilder sb = new StringBuilder();

            for (int offset = 0; offset < bytes.length; offset += BYTES_PER_LINE) {
                // Offset column
                String offsetStr = String.format("%08X  ", offset);
                doc.insertString(doc.getLength(), offsetStr, doc.getStyle(STYLE_OFFSET));

                // Hex bytes
                StringBuilder hexPart = new StringBuilder();
                StringBuilder asciiPart = new StringBuilder();

                for (int i = 0; i < BYTES_PER_LINE; i++) {
                    int byteOffset = offset + i;
                    if (byteOffset < bytes.length) {
                        int b = bytes[byteOffset] & 0xFF;
                        hexPart.append(String.format("%02X ", b));

                        // ASCII representation
                        if (b >= 32 && b < 127) {
                            asciiPart.append((char) b);
                        } else {
                            asciiPart.append('.');
                        }
                    } else {
                        hexPart.append("   ");
                        asciiPart.append(' ');
                    }

                    // Add extra space after 8 bytes for readability
                    if (i == 7) {
                        hexPart.append(' ');
                    }
                }

                // Choose style based on position (highlight magic number at start)
                String style = (offset < 4) ? STYLE_HIGHLIGHT : STYLE_HEX;
                doc.insertString(doc.getLength(), hexPart.toString(), doc.getStyle(style));

                // Separator
                doc.insertString(doc.getLength(), " ", doc.getStyle(STYLE_SEPARATOR));

                // ASCII column
                doc.insertString(doc.getLength(), asciiPart.toString(), doc.getStyle(STYLE_ASCII));

                // Newline
                doc.insertString(doc.getLength(), "\n", null);
            }

            // Scroll to top
            textPane.setCaretPosition(0);

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

    /**
     * Show find dialog.
     */
    public void showFindDialog() {
        String input = JOptionPane.showInputDialog(this, "Find (hex bytes like 'CA FE'):", "Find",
                JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.isEmpty()) {
            String text = textPane.getText().toUpperCase();
            String searchUpper = input.toUpperCase();
            int pos = text.indexOf(searchUpper);
            if (pos >= 0) {
                textPane.setCaretPosition(pos);
                textPane.select(pos, pos + input.length());
            }
        }
    }

    /**
     * Get the selected text.
     */
    public String getSelectedText() {
        return textPane.getSelectedText();
    }

    /**
     * Scroll to text.
     */
    public void scrollToText(String text) {
        if (text == null || text.isEmpty()) return;

        String content = textPane.getText().toUpperCase();
        String searchUpper = text.toUpperCase();
        int pos = content.indexOf(searchUpper);
        if (pos >= 0) {
            textPane.setCaretPosition(pos);
            textPane.select(pos, pos + text.length());
        }
    }

    /**
     * Set the font size.
     */
    public void setFontSize(int size) {
        textPane.setFont(JStudioTheme.getCodeFont(size));
    }

    /**
     * Set word wrap (not really applicable for hex view).
     */
    public void setWordWrap(boolean enabled) {
        // Hex view doesn't use word wrap
    }
}
