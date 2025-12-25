package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.testgen.TestGeneratorDialog;

import javax.swing.*;
import java.awt.*;

public class ExecutionResultPanel extends JPanel {

    private final SummaryBar summaryBar;
    private final JTabbedPane detailsTabs;
    private final CallTracePanel callTracePanel;
    private final ConsoleOutputPanel consolePanel;
    private final StatisticsPanel statsPanel;
    private final JButton saveAsTestButton;

    private ExecutionResult currentResult;
    private String executionClassName;
    private String executionMethodName;
    private String executionDescriptor;
    private Object[] executionArgs;

    public ExecutionResultPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(JStudioTheme.getBgPrimary());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(JStudioTheme.getBgPrimary());

        summaryBar = new SummaryBar();
        topPanel.add(summaryBar, BorderLayout.CENTER);

        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        toolbarPanel.setBackground(JStudioTheme.getBgPrimary());
        saveAsTestButton = new JButton("Save as Test...");
        saveAsTestButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        saveAsTestButton.setEnabled(false);
        saveAsTestButton.addActionListener(e -> openTestGeneratorDialog());
        toolbarPanel.add(saveAsTestButton);
        topPanel.add(toolbarPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        callTracePanel = new CallTracePanel();
        consolePanel = new ConsoleOutputPanel();
        statsPanel = new StatisticsPanel();

        detailsTabs = new JTabbedPane(JTabbedPane.TOP);
        detailsTabs.setBackground(JStudioTheme.getBgPrimary());
        detailsTabs.setForeground(JStudioTheme.getTextPrimary());
        detailsTabs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        detailsTabs.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Details",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 11),
            JStudioTheme.getTextPrimary()
        ));

        detailsTabs.addTab("Call Trace", callTracePanel);
        detailsTabs.addTab("Console", consolePanel);
        detailsTabs.addTab("Statistics", statsPanel);

        add(detailsTabs, BorderLayout.CENTER);
    }

    public void displayResult(ExecutionResult result) {
        this.currentResult = result;
        summaryBar.update(result);
        callTracePanel.update(result.getMethodCalls());
        consolePanel.update(result.getConsoleOutput());
        statsPanel.update(result);

        saveAsTestButton.setEnabled(executionClassName != null);

        if (!result.getMethodCalls().isEmpty()) {
            detailsTabs.setSelectedComponent(callTracePanel);
        } else if (!result.getConsoleOutput().isEmpty()) {
            detailsTabs.setSelectedComponent(consolePanel);
        } else {
            detailsTabs.setSelectedComponent(statsPanel);
        }
    }

    public void setExecutionContext(String className, String methodName,
                                     String descriptor, Object[] args) {
        this.executionClassName = className;
        this.executionMethodName = methodName;
        this.executionDescriptor = descriptor;
        this.executionArgs = args != null ? args.clone() : new Object[0];
    }

    private void openTestGeneratorDialog() {
        if (currentResult == null || executionClassName == null) {
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        TestGeneratorDialog dialog = new TestGeneratorDialog(owner);
        dialog.setExecutionResult(currentResult, executionClassName,
                                   executionMethodName, executionDescriptor, executionArgs);
        dialog.setVisible(true);
    }

    public void showExecuting() {
        summaryBar.showExecuting();
        callTracePanel.showEmpty();
        consolePanel.clear();
        statsPanel.showEmpty();
    }

    public void clear() {
        currentResult = null;
        summaryBar.showEmpty();
        callTracePanel.showEmpty();
        consolePanel.clear();
        statsPanel.showEmpty();
        saveAsTestButton.setEnabled(false);
    }
}
