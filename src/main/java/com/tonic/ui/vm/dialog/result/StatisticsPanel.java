package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.MethodCall;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class StatisticsPanel extends ThemedJPanel {

    private final JLabel totalTimeLabel;
    private final JLabel instructionsLabel;
    private final JLabel totalCallsLabel;
    private final JLabel uniqueMethodsLabel;
    private final JLabel maxDepthLabel;
    private final JLabel deepestPathLabel;

    private final DefaultTableModel hotMethodsModel;

    public StatisticsPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout(UIConstants.SPACING_MEDIUM + 2, UIConstants.SPACING_MEDIUM + 2));
        setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_MEDIUM + 2, UIConstants.SPACING_MEDIUM + 2, UIConstants.SPACING_MEDIUM + 2, UIConstants.SPACING_MEDIUM + 2));

        JPanel metricsPanel = new JPanel();
        metricsPanel.setLayout(new BoxLayout(metricsPanel, BoxLayout.Y_AXIS));
        metricsPanel.setBackground(JStudioTheme.getBgPrimary());

        metricsPanel.add(createSection("Timing"));
        totalTimeLabel = createMetricLabel("Total time:", "0ms");
        metricsPanel.add(totalTimeLabel);

        metricsPanel.add(Box.createVerticalStrut(15));
        metricsPanel.add(createSection("Instructions"));
        instructionsLabel = createMetricLabel("Total executed:", "0");
        metricsPanel.add(instructionsLabel);

        metricsPanel.add(Box.createVerticalStrut(15));
        metricsPanel.add(createSection("Call Analysis"));
        totalCallsLabel = createMetricLabel("Total calls:", "0");
        metricsPanel.add(totalCallsLabel);
        uniqueMethodsLabel = createMetricLabel("Unique methods:", "0");
        metricsPanel.add(uniqueMethodsLabel);
        maxDepthLabel = createMetricLabel("Max depth:", "0");
        metricsPanel.add(maxDepthLabel);
        deepestPathLabel = createMetricLabel("Deepest path:", "-");
        metricsPanel.add(deepestPathLabel);

        metricsPanel.add(Box.createVerticalStrut(15));
        metricsPanel.add(createSection("Hot Methods (by call count)"));

        add(metricsPanel, BorderLayout.NORTH);

        String[] columns = {"Method", "Calls", "Total Time"};
        hotMethodsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable hotMethodsTable = new JTable(hotMethodsModel);
        hotMethodsTable.setBackground(JStudioTheme.getBgPrimary());
        hotMethodsTable.setForeground(JStudioTheme.getTextPrimary());
        hotMethodsTable.setSelectionBackground(JStudioTheme.getSelection());
        hotMethodsTable.setGridColor(JStudioTheme.getBorder());
        hotMethodsTable.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        hotMethodsTable.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        hotMethodsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        hotMethodsTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);

        hotMethodsTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        hotMethodsTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        hotMethodsTable.getColumnModel().getColumn(2).setPreferredWidth(80);

        JScrollPane tableScroll = new JScrollPane(hotMethodsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        tableScroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        tableScroll.setPreferredSize(new Dimension(0, 150));

        add(tableScroll, BorderLayout.CENTER);

        showEmpty();
    }

    private JLabel createSection(String title) {
        JLabel label = new JLabel(title);
        label.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL).deriveFont(Font.BOLD));
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, UIConstants.SPACING_SMALL + 1, 0));
        return label;
    }

    private JLabel createMetricLabel(String name, String value) {
        JLabel label = new JLabel("  " + name + "  " + value);
        label.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private void updateMetricLabel(JLabel label, String name, String value) {
        label.setText("  " + name + "  " + value);
    }

    public void showEmpty() {
        updateMetricLabel(totalTimeLabel, "Total time:", "0ms");
        updateMetricLabel(instructionsLabel, "Total executed:", "0");
        updateMetricLabel(totalCallsLabel, "Total calls:", "0");
        updateMetricLabel(uniqueMethodsLabel, "Unique methods:", "0");
        updateMetricLabel(maxDepthLabel, "Max depth:", "0");
        updateMetricLabel(deepestPathLabel, "Deepest path:", "-");
        hotMethodsModel.setRowCount(0);
    }

    public void update(ExecutionResult result) {
        updateMetricLabel(totalTimeLabel, "Total time:", result.getExecutionTimeMs() + "ms");
        updateMetricLabel(instructionsLabel, "Total executed:",
            String.valueOf(result.getInstructionsExecuted()));

        List<MethodCall> calls = result.getMethodCalls();
        int totalCalls = calls.size();
        updateMetricLabel(totalCallsLabel, "Total calls:", String.valueOf(totalCalls));

        Set<String> uniqueMethods = new HashSet<>();
        int maxDepth = 0;
        Map<String, Integer> callCounts = new HashMap<>();
        Map<String, Long> totalTimes = new HashMap<>();

        for (MethodCall call : calls) {
            String sig = call.getShortSignature();
            uniqueMethods.add(sig);
            maxDepth = Math.max(maxDepth, call.getDepth());

            callCounts.merge(sig, 1, Integer::sum);
            totalTimes.merge(sig, call.getDurationNanos(), Long::sum);
        }

        updateMetricLabel(uniqueMethodsLabel, "Unique methods:", String.valueOf(uniqueMethods.size()));
        updateMetricLabel(maxDepthLabel, "Max depth:", String.valueOf(maxDepth));

        String deepestPath = buildDeepestPath(calls);
        updateMetricLabel(deepestPathLabel, "Deepest path:", deepestPath);

        hotMethodsModel.setRowCount(0);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(callCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            String method = entry.getKey();
            int count = entry.getValue();
            long nanos = totalTimes.getOrDefault(method, 0L);
            String timeStr = String.format("%.2fms", nanos / 1_000_000.0);

            hotMethodsModel.addRow(new Object[]{method, count, timeStr});
        }
    }

    private String buildDeepestPath(List<MethodCall> calls) {
        if (calls.isEmpty()) return "-";

        int maxDepth = 0;
        List<String> deepestPath = new ArrayList<>();

        Deque<String> currentPath = new ArrayDeque<>();

        for (MethodCall call : calls) {
            int depth = call.getDepth();

            while (currentPath.size() > depth) {
                currentPath.pollLast();
            }

            currentPath.addLast(call.getSimpleOwnerName() + "." + call.getMethodName());

            if (depth > maxDepth) {
                maxDepth = depth;
                deepestPath = new ArrayList<>(currentPath);
            }
        }

        if (deepestPath.isEmpty()) return "-";

        if (deepestPath.size() > 4) {
            return deepestPath.get(0) + " \u2192 ... \u2192 " +
                   deepestPath.get(deepestPath.size() - 2) + " \u2192 " +
                   deepestPath.get(deepestPath.size() - 1);
        }

        return String.join(" \u2192 ", deepestPath);
    }
}
