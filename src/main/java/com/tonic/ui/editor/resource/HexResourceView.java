package com.tonic.ui.editor.resource;

import com.tonic.ui.model.ResourceEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.JTextPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class HexResourceView extends JPanel implements ThemeChangeListener {

    private final ResourceEntryModel resource;
    private final JTextPane textPane;
    private final JScrollPane scrollPane;
    private final JPanel headerPanel;
    private final JLabel headerLabel;

    private static final int BYTES_PER_LINE = 16;

    private static final String STYLE_OFFSET = "offset";
    private static final String STYLE_HEX = "hex";
    private static final String STYLE_ASCII = "ascii";
    private static final String STYLE_SEPARATOR = "separator";

    public HexResourceView(ResourceEntryModel resource) {
        this.resource = resource;
        setLayout(new BorderLayout());

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(JStudioTheme.getCodeFont(12));

        setupStyles();

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerLabel = new JLabel("  Offset    00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   ASCII");
        headerLabel.setFont(JStudioTheme.getCodeFont(12));
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);

        displayHexDump();
        applyTheme();

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void setupStyles() {
        StyledDocument doc = textPane.getStyledDocument();

        Style offsetStyle = doc.addStyle(STYLE_OFFSET, null);
        StyleConstants.setForeground(offsetStyle, JStudioTheme.getSuccess());

        Style hexStyle = doc.addStyle(STYLE_HEX, null);
        StyleConstants.setForeground(hexStyle, JStudioTheme.getTextPrimary());

        Style asciiStyle = doc.addStyle(STYLE_ASCII, null);
        StyleConstants.setForeground(asciiStyle, JStudioTheme.getAccent());

        Style separatorStyle = doc.addStyle(STYLE_SEPARATOR, null);
        StyleConstants.setForeground(separatorStyle, JStudioTheme.getTextSecondary());
    }

    private void displayHexDump() {
        byte[] bytes = resource.getData();
        StyledDocument doc = textPane.getStyledDocument();

        try {
            for (int offset = 0; offset < bytes.length; offset += BYTES_PER_LINE) {
                String offsetStr = String.format("%08X  ", offset);
                doc.insertString(doc.getLength(), offsetStr, doc.getStyle(STYLE_OFFSET));

                StringBuilder hexPart = new StringBuilder();
                StringBuilder asciiPart = new StringBuilder();

                for (int i = 0; i < BYTES_PER_LINE; i++) {
                    int byteOffset = offset + i;
                    if (byteOffset < bytes.length) {
                        int b = bytes[byteOffset] & 0xFF;
                        hexPart.append(String.format("%02X ", b));

                        if (b >= 32 && b < 127) {
                            asciiPart.append((char) b);
                        } else {
                            asciiPart.append('.');
                        }
                    } else {
                        hexPart.append("   ");
                        asciiPart.append(' ');
                    }

                    if (i == 7) {
                        hexPart.append(' ');
                    }
                }

                doc.insertString(doc.getLength(), hexPart.toString(), doc.getStyle(STYLE_HEX));
                doc.insertString(doc.getLength(), " ", doc.getStyle(STYLE_SEPARATOR));
                doc.insertString(doc.getLength(), asciiPart.toString(), doc.getStyle(STYLE_ASCII));
                doc.insertString(doc.getLength(), "\n", null);
            }

            textPane.setCaretPosition(0);
        } catch (BadLocationException e) {
            // Ignore
        }
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
    }

    public String getText() {
        return textPane.getText();
    }

    public void setFontSize(int size) {
        textPane.setFont(JStudioTheme.getCodeFont(size));
        headerLabel.setFont(JStudioTheme.getCodeFont(size));
    }
}
