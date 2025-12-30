package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.ExecutionResult;

import javax.swing.*;
import java.awt.*;

public class SummaryBar extends JPanel {

    private final JLabel statusIcon;
    private final JLabel statusLabel;
    private final JLabel returnLabel;
    private final JLabel timeLabel;
    private final JLabel instructionsLabel;
    private final JLabel callsLabel;
    private final JPanel stackTracePanel;
    private final JTextArea stackTraceArea;
    private final JButton expandButton;

    private boolean expanded = false;

    private static Color successBg() {
        Color success = JStudioTheme.getSuccess();
        return new Color(success.getRed() / 8, success.getGreen() / 4, success.getBlue() / 8);
    }

    private static Color failureBg() {
        Color error = JStudioTheme.getError();
        return new Color(error.getRed() / 4, error.getGreen() / 8, error.getBlue() / 8);
    }

    public SummaryBar() {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        mainRow.setOpaque(false);

        statusIcon = new JLabel();
        statusIcon.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        statusLabel = new JLabel();
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        returnLabel = createInfoLabel();
        timeLabel = createInfoLabel();
        instructionsLabel = createInfoLabel();
        callsLabel = createInfoLabel();

        expandButton = new JButton("Show stack trace");
        expandButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        expandButton.setVisible(false);
        expandButton.addActionListener(e -> toggleStackTrace());

        mainRow.add(statusIcon);
        mainRow.add(statusLabel);
        mainRow.add(createSeparator());
        mainRow.add(returnLabel);
        mainRow.add(createSeparator());
        mainRow.add(timeLabel);
        mainRow.add(createSeparator());
        mainRow.add(instructionsLabel);
        mainRow.add(createSeparator());
        mainRow.add(callsLabel);
        mainRow.add(Box.createHorizontalStrut(10));
        mainRow.add(expandButton);

        add(mainRow, BorderLayout.NORTH);

        stackTracePanel = new JPanel(new BorderLayout());
        stackTracePanel.setOpaque(false);
        stackTracePanel.setVisible(false);
        stackTracePanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        stackTraceArea = new JTextArea(5, 60);
        stackTraceArea.setEditable(false);
        stackTraceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        stackTraceArea.setBackground(JStudioTheme.getBgPrimary());
        stackTraceArea.setForeground(JStudioTheme.getError());

        JScrollPane scrollPane = new JScrollPane(stackTraceArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        stackTracePanel.add(scrollPane, BorderLayout.CENTER);

        add(stackTracePanel, BorderLayout.CENTER);

        showEmpty();
    }

    private JLabel createInfoLabel() {
        JLabel label = new JLabel();
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        label.setForeground(JStudioTheme.getTextPrimary());
        return label;
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        sep.setForeground(JStudioTheme.getBorder());
        return sep;
    }

    public void showEmpty() {
        statusIcon.setText("");
        statusLabel.setText("No result yet");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        returnLabel.setText("");
        timeLabel.setText("");
        instructionsLabel.setText("");
        callsLabel.setText("");
        expandButton.setVisible(false);
        stackTracePanel.setVisible(false);
        expanded = false;
        setBackground(JStudioTheme.getBgSecondary());
    }

    public void showExecuting() {
        statusIcon.setText("...");
        statusIcon.setForeground(JStudioTheme.getTextPrimary());
        statusLabel.setText("Executing");
        statusLabel.setForeground(JStudioTheme.getTextPrimary());
        returnLabel.setText("");
        timeLabel.setText("");
        instructionsLabel.setText("");
        callsLabel.setText("");
        expandButton.setVisible(false);
        stackTracePanel.setVisible(false);
        expanded = false;
        setBackground(JStudioTheme.getBgSecondary());
    }

    public void update(ExecutionResult result) {
        if (result.isSuccess()) {
            statusIcon.setText("\u2713");
            statusIcon.setForeground(JStudioTheme.getSuccess());
            statusLabel.setText("SUCCESS");
            statusLabel.setForeground(JStudioTheme.getSuccess());
            setBackground(successBg());

            String returnValue = result.getFormattedReturnValue();
            String returnType = formatReturnType(result.getReturnType());
            returnLabel.setText("Return: " + returnValue + " (" + returnType + ")");

            expandButton.setVisible(false);
            stackTracePanel.setVisible(false);
        } else {
            statusIcon.setText("\u2717");
            statusIcon.setForeground(JStudioTheme.getError());
            statusLabel.setText("FAILED");
            statusLabel.setForeground(JStudioTheme.getError());
            setBackground(failureBg());

            Throwable ex = result.getException();
            if (ex != null) {
                String exName = ex.getClass().getSimpleName();
                String exMsg = ex.getMessage();
                if (exMsg != null && exMsg.length() > 40) {
                    exMsg = exMsg.substring(0, 37) + "...";
                }
                returnLabel.setText(exName + (exMsg != null ? ": " + exMsg : ""));

                StringBuilder sb = new StringBuilder();
                for (StackTraceElement ste : ex.getStackTrace()) {
                    sb.append("  at ").append(ste.toString()).append("\n");
                }
                stackTraceArea.setText(sb.toString());
                expandButton.setVisible(true);
            } else {
                returnLabel.setText("Execution failed");
                expandButton.setVisible(false);
            }
        }

        timeLabel.setText(result.getExecutionTimeMs() + "ms");

        long instructions = result.getInstructionsExecuted();
        if (instructions > 0) {
            instructionsLabel.setText(instructions + " instrs");
        } else {
            instructionsLabel.setText("");
        }

        int callCount = result.getMethodCalls().size();
        if (callCount > 0) {
            callsLabel.setText(callCount + " calls");
        } else {
            callsLabel.setText("");
        }

        expanded = false;
        stackTracePanel.setVisible(false);
        expandButton.setText("Show stack trace");

        revalidate();
        repaint();
    }

    private void toggleStackTrace() {
        expanded = !expanded;
        stackTracePanel.setVisible(expanded);
        expandButton.setText(expanded ? "Hide stack trace" : "Show stack trace");
        revalidate();
        repaint();
    }

    private String formatReturnType(String type) {
        if (type == null) return "?";
        switch (type) {
            case "V": return "void";
            case "Z": return "boolean";
            case "B": return "byte";
            case "C": return "char";
            case "S": return "short";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            default:
                if (type.startsWith("L") && type.endsWith(";")) {
                    String className = type.substring(1, type.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                if (type.startsWith("[")) {
                    return formatReturnType(type.substring(1)) + "[]";
                }
                return type;
        }
    }
}
