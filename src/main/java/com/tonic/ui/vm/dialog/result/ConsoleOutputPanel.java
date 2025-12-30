package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class ConsoleOutputPanel extends ThemedJPanel {

    private final JTextPane outputPane;
    private final StyledDocument doc;
    private final JLabel lineCountLabel;
    private final JButton clearBtn;
    private final JButton exportBtn;

    private static final Color STDOUT_COLOR = JStudioTheme.getTextPrimary();
    private static final Color STDERR_COLOR = new Color(244, 135, 113);

    public ConsoleOutputPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL + 1, 3));
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        clearBtn = new JButton("Clear");
        clearBtn.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        clearBtn.addActionListener(e -> clear());

        exportBtn = new JButton("Export");
        exportBtn.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        exportBtn.addActionListener(e -> exportToFile());

        lineCountLabel = new JLabel("Lines: 0");
        lineCountLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        lineCountLabel.setForeground(JStudioTheme.getTextSecondary());

        toolbar.add(clearBtn);
        toolbar.add(exportBtn);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(lineCountLabel);

        add(toolbar, BorderLayout.NORTH);

        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setBackground(JStudioTheme.getBgPrimary());
        outputPane.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        doc = outputPane.getStyledDocument();

        initStyles();

        JScrollPane scrollPane = new JScrollPane(outputPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgPrimary());

        add(scrollPane, BorderLayout.CENTER);

        showEmpty();
    }

    private void initStyles() {
        Style defaultStyle = outputPane.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, STDOUT_COLOR);

        Style stdoutStyle = outputPane.addStyle("stdout", defaultStyle);
        StyleConstants.setForeground(stdoutStyle, STDOUT_COLOR);

        Style stderrStyle = outputPane.addStyle("stderr", defaultStyle);
        StyleConstants.setForeground(stderrStyle, STDERR_COLOR);

        Style prefixStyle = outputPane.addStyle("prefix", defaultStyle);
        StyleConstants.setForeground(prefixStyle, JStudioTheme.getTextSecondary());
        StyleConstants.setFontSize(prefixStyle, 10);
    }

    public void showEmpty() {
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, "(No console output)", outputPane.getStyle("default"));
        } catch (BadLocationException ignored) {}
        lineCountLabel.setText("Lines: 0");
    }

    public void clear() {
        showEmpty();
    }

    public void update(List<String> output) {
        try {
            doc.remove(0, doc.getLength());

            if (output == null || output.isEmpty()) {
                doc.insertString(0, "(No console output)", outputPane.getStyle("default"));
                lineCountLabel.setText("Lines: 0");
                return;
            }

            int lineCount = 0;
            for (String line : output) {
                boolean isStderr = line.startsWith("[stderr]") || line.startsWith("ERROR:") ||
                                   line.startsWith("Exception") || line.contains("Exception:");

                String prefix;
                Style lineStyle;
                String content;

                if (line.startsWith("[stdout]")) {
                    prefix = "[stdout] ";
                    content = line.substring(8).trim();
                    lineStyle = outputPane.getStyle("stdout");
                } else if (line.startsWith("[stderr]")) {
                    prefix = "[stderr] ";
                    content = line.substring(8).trim();
                    lineStyle = outputPane.getStyle("stderr");
                } else if (isStderr) {
                    prefix = "[stderr] ";
                    content = line;
                    lineStyle = outputPane.getStyle("stderr");
                } else {
                    prefix = "[stdout] ";
                    content = line;
                    lineStyle = outputPane.getStyle("stdout");
                }

                doc.insertString(doc.getLength(), prefix, outputPane.getStyle("prefix"));
                doc.insertString(doc.getLength(), content + "\n", lineStyle);
                lineCount++;
            }

            lineCountLabel.setText("Lines: " + lineCount);
            outputPane.setCaretPosition(0);

        } catch (BadLocationException ignored) {}
    }

    private void exportToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("console_output.txt"));
        int result = chooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                FileWriter writer = new FileWriter(file);
                writer.write(outputPane.getText());
                writer.close();
                JOptionPane.showMessageDialog(this, "Exported to " + file.getName(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
