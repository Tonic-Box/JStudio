package com.tonic.ui.editor.hex;

import com.tonic.parser.ClassFile;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.editor.view.AbstractEditorView;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/** The hex view: the class file's written bytes as a sixteen-bytes-per-line dump with offset and ASCII columns. */
public class HexView extends AbstractEditorView
{

    private final ClassEntryModel classEntry;
    private final JTextPane textPane;
    private final JScrollPane scrollPane;
    private final JPanel headerPanel;
    private final JLabel headerLabel;

    private static final int BYTES_PER_LINE = 16;

    private static final String STYLE_OFFSET = "offset";
    private static final String STYLE_HEX = "hex";
    private static final String STYLE_ASCII = "ascii";
    private static final String STYLE_SEPARATOR = "separator";
    private static final String STYLE_HIGHLIGHT = "highlight";

    /**
     * Creates the view; the dump loads on the first refresh.
     *
     * @param classEntry the class whose bytes to show
     */
    public HexView(ClassEntryModel classEntry)
    {
        this.classEntry = classEntry;

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(JStudioTheme.getCodeFont(12));
        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setCaretColor(JStudioTheme.getTextPrimary());

        setupStyles();

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        add(overlayWrap(scrollPane), BorderLayout.CENTER);

        headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(JStudioTheme.getBgSecondary());
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        headerLabel = new JLabel("  Offset    00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   ASCII");
        headerLabel.setFont(JStudioTheme.getCodeFont(12));
        headerLabel.setForeground(JStudioTheme.getTextSecondary());
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);
    }

    @Override
    protected void applyChildThemes()
    {
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

    private void setupStyles()
    {
        StyledDocument doc = textPane.getStyledDocument();

        Style offsetStyle = doc.addStyle(STYLE_OFFSET, null);
        StyleConstants.setForeground(offsetStyle, JStudioTheme.getSuccess());

        Style hexStyle = doc.addStyle(STYLE_HEX, null);
        StyleConstants.setForeground(hexStyle, JStudioTheme.getTextPrimary());

        Style asciiStyle = doc.addStyle(STYLE_ASCII, null);
        StyleConstants.setForeground(asciiStyle, JStudioTheme.getAccent());

        Style separatorStyle = doc.addStyle(STYLE_SEPARATOR, null);
        StyleConstants.setForeground(separatorStyle, JStudioTheme.getTextSecondary());

        Style highlightStyle = doc.addStyle(STYLE_HIGHLIGHT, null);
        StyleConstants.setForeground(highlightStyle, JStudioTheme.getWarning());
    }

    @Override
    public void refresh()
    {
        if (loaded)
        {
            return;
        }

        cancelCurrentWorker();

        textPane.setText("");
        loadingOverlay.showLoading("Loading hex dump...");

        SwingWorker<byte[], Void> worker = new SwingWorker<>()
        {
            @Override
            protected byte[] doInBackground()
            {
                try
                {
                    ClassFile cf = classEntry.getClassFile();
                    return cf.write();
                }
                catch (Exception e)
                {
                    return null;
                }
            }

            @Override
            protected void done()
            {
                loadingOverlay.hideLoading();
                if (isCancelled())
                {
                    return;
                }
                try
                {
                    byte[] bytes = get();
                    if (bytes != null)
                    {
                        displayHexDump(bytes);
                        loaded = true;
                    }
                    else
                    {
                        StyledDocument doc = textPane.getStyledDocument();
                        doc.insertString(0, "Failed to read class file bytes", null);
                    }
                }
                catch (Exception e)
                {
                    try
                    {
                        StyledDocument doc = textPane.getStyledDocument();
                        doc.insertString(0, "Failed to read class file bytes: " + e.getMessage(), null);
                    }
                    catch (BadLocationException ex)
                    {
                    }
                }
            }
        };
        currentWorker = worker;
        worker.execute();
    }

    private void displayHexDump(byte[] bytes)
    {
        StyledDocument doc = textPane.getStyledDocument();

        try
        {
            for (int offset = 0; offset < bytes.length; offset += BYTES_PER_LINE)
            {
                String offsetStr = String.format("%08X  ", offset);
                doc.insertString(doc.getLength(), offsetStr, doc.getStyle(STYLE_OFFSET));

                StringBuilder hexPart = new StringBuilder();
                StringBuilder asciiPart = new StringBuilder();

                for (int i = 0; i < BYTES_PER_LINE; i++)
                {
                    int byteOffset = offset + i;
                    if (byteOffset < bytes.length)
                    {
                        int b = bytes[byteOffset] & 0xFF;
                        hexPart.append(String.format("%02X ", b));

                        if (b >= 32 && b < 127)
                        {
                            asciiPart.append((char) b);
                        }
                        else
                        {
                            asciiPart.append('.');
                        }
                    }
                    else
                    {
                        hexPart.append("   ");
                        asciiPart.append(' ');
                    }

                    if (i == 7)
                    {
                        hexPart.append(' ');
                    }
                }

                String hex = hexPart.toString();
                if (offset == 0)
                {
                    int magicEnd = Math.min(bytes.length, 4) * 3;
                    doc.insertString(doc.getLength(), hex.substring(0, magicEnd), doc.getStyle(STYLE_HIGHLIGHT));
                    doc.insertString(doc.getLength(), hex.substring(magicEnd), doc.getStyle(STYLE_HEX));
                }
                else
                {
                    doc.insertString(doc.getLength(), hex, doc.getStyle(STYLE_HEX));
                }

                doc.insertString(doc.getLength(), " ", doc.getStyle(STYLE_SEPARATOR));

                doc.insertString(doc.getLength(), asciiPart.toString(), doc.getStyle(STYLE_ASCII));

                doc.insertString(doc.getLength(), "\n", null);
            }

            textPane.setCaretPosition(0);

        }
        catch (BadLocationException e)
        {
        }
    }

    /**
     * Gets the dump text.
     *
     * @return the whole dump as text
     */
    @Override
    public String getText()
    {
        return textPane.getText();
    }

    /** Copies the selected dump text to the system clipboard. */
    @Override
    public void copySelection()
    {
        String selected = textPane.getSelectedText();
        if (selected != null && !selected.isEmpty())
        {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    /**
     * Moves the caret to a dump line; out-of-range lines are ignored.
     *
     * @param line the 1-based line number
     */
    @Override
    public void goToLine(int line)
    {
        try
        {
            int offset = textPane.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
            textPane.setCaretPosition(offset);
            textPane.requestFocus();
        }
        catch (Exception e)
        {
        }
    }

    /** Prompts for text such as hex bytes and selects its first case-insensitive match in the dump. */
    @Override
    public void showFindDialog()
    {
        String input = JOptionPane.showInputDialog(this, "Find (hex bytes like 'CA FE'):", "Find", JOptionPane.PLAIN_MESSAGE);
        scrollToText(input);
    }

    /**
     * Gets the selected dump text.
     *
     * @return the selection, or null when nothing is selected
     */
    @Override
    public String getSelectedText()
    {
        return textPane.getSelectedText();
    }

    /**
     * Selects the first case-insensitive match of a text in the dump.
     *
     * @param text the text to find; null or empty does nothing
     */
    @Override
    public void scrollToText(String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        try
        {
            Document doc = textPane.getDocument();
            int pos = doc.getText(0, doc.getLength()).toUpperCase().indexOf(text.toUpperCase());
            if (pos >= 0)
            {
                textPane.setCaretPosition(pos);
                textPane.select(pos, pos + text.length());
            }
        }
        catch (BadLocationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets the dump's font size.
     *
     * @param size the font size in points
     */
    @Override
    public void setFontSize(int size)
    {
        textPane.setFont(JStudioTheme.getCodeFont(size));
    }
}
