package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.testgen.FuzzTestGeneratorDialog;
import com.tonic.ui.vm.testgen.TestGeneratorDialog;

import javax.swing.*;
import java.awt.*;

public class ExecutionResultPanel extends ThemedJPanel {

    private final SummaryBar summaryBar;
    private final JTabbedPane detailsTabs;
    private final CallTracePanel callTracePanel;
    private final ConsoleOutputPanel consolePanel;
    private final StatisticsPanel statsPanel;
    private final JButton saveAsTestButton;
    private final JButton fuzzTestButton;

    private ExecutionResult currentResult;
    private String executionClassName;
    private String executionMethodName;
    private String executionDescriptor;
    private Object[] executionArgs;

    public ExecutionResultPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout(0, UIConstants.SPACING_MEDIUM));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(JStudioTheme.getBgPrimary());

        summaryBar = new SummaryBar();
        summaryBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(summaryBar);

        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        toolbarPanel.setBackground(JStudioTheme.getBgPrimary());
        toolbarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbarPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        fuzzTestButton = new JButton("Fuzz & Generate Tests...");
        fuzzTestButton.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        fuzzTestButton.setEnabled(false);
        fuzzTestButton.setToolTipText("Run method with varied inputs and generate comprehensive tests");
        fuzzTestButton.addActionListener(e -> openFuzzTestDialog());
        toolbarPanel.add(fuzzTestButton);

        saveAsTestButton = new JButton("Save Execution as Test...");
        saveAsTestButton.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        saveAsTestButton.setEnabled(false);
        saveAsTestButton.setToolTipText("Generate a test from the current execution result");
        saveAsTestButton.addActionListener(e -> openTestGeneratorDialog());
        toolbarPanel.add(saveAsTestButton);

        topPanel.add(toolbarPanel);

        add(topPanel, BorderLayout.NORTH);

        callTracePanel = new CallTracePanel();
        consolePanel = new ConsoleOutputPanel();
        statsPanel = new StatisticsPanel();

        detailsTabs = new JTabbedPane(JTabbedPane.TOP);
        detailsTabs.setBackground(JStudioTheme.getBgPrimary());
        detailsTabs.setForeground(JStudioTheme.getTextPrimary());
        detailsTabs.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        detailsTabs.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Details",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE).deriveFont(Font.BOLD),
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

        saveAsTestButton.setEnabled(executionClassName != null && currentResult != null);

        if (!result.getMethodCalls().isEmpty()) {
            detailsTabs.setSelectedComponent(callTracePanel);
        } else if (!result.getConsoleOutput().isEmpty()) {
            detailsTabs.setSelectedComponent(consolePanel);
        } else {
            detailsTabs.setSelectedComponent(statsPanel);
        }
    }

    public void setMethodContext(String className, String methodName, String descriptor) {
        this.executionClassName = className;
        this.executionMethodName = methodName;
        this.executionDescriptor = descriptor;
        fuzzTestButton.setEnabled(className != null);
    }

    public void setExecutionContext(String className, String methodName,
                                     String descriptor, Object[] args) {
        this.executionClassName = className;
        this.executionMethodName = methodName;
        this.executionDescriptor = descriptor;
        this.executionArgs = args != null ? args.clone() : new Object[0];
        fuzzTestButton.setEnabled(className != null);
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

    private void openFuzzTestDialog() {
        if (executionClassName == null) {
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        FuzzTestGeneratorDialog dialog = new FuzzTestGeneratorDialog(owner);
        dialog.setMethod(executionClassName, executionMethodName, executionDescriptor);
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

    public void clearAll() {
        clear();
        executionClassName = null;
        executionMethodName = null;
        executionDescriptor = null;
        executionArgs = null;
        fuzzTestButton.setEnabled(false);
    }
}
