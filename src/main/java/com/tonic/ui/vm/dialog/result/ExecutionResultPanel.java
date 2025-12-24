package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.ExecutionResult;

import javax.swing.*;
import java.awt.*;

public class ExecutionResultPanel extends JPanel {

    private final SummaryBar summaryBar;
    private final JTabbedPane detailsTabs;
    private final CallTracePanel callTracePanel;
    private final ConsoleOutputPanel consolePanel;
    private final StatisticsPanel statsPanel;

    public ExecutionResultPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(JStudioTheme.getBgPrimary());

        summaryBar = new SummaryBar();
        add(summaryBar, BorderLayout.NORTH);

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
        summaryBar.update(result);
        callTracePanel.update(result.getMethodCalls());
        consolePanel.update(result.getConsoleOutput());
        statsPanel.update(result);

        if (!result.getMethodCalls().isEmpty()) {
            detailsTabs.setSelectedComponent(callTracePanel);
        } else if (!result.getConsoleOutput().isEmpty()) {
            detailsTabs.setSelectedComponent(consolePanel);
        } else {
            detailsTabs.setSelectedComponent(statsPanel);
        }
    }

    public void showExecuting() {
        summaryBar.showExecuting();
        callTracePanel.showEmpty();
        consolePanel.clear();
        statsPanel.showEmpty();
    }

    public void clear() {
        summaryBar.showEmpty();
        callTracePanel.showEmpty();
        consolePanel.clear();
        statsPanel.showEmpty();
    }
}
