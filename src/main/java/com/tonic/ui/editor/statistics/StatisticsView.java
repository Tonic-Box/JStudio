package com.tonic.ui.editor.statistics;

import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.editor.statistics.charts.BarChart;
import com.tonic.ui.editor.statistics.charts.PieChart;
import com.tonic.ui.editor.statistics.charts.StatCard;
import com.tonic.ui.editor.statistics.charts.StatTable;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatisticsView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final LoadingOverlay loadingOverlay;
    private final JPanel contentPanel;
    private final JScrollPane scrollPane;

    private StatCard methodCountCard;
    private StatCard fieldCountCard;
    private StatCard bytecodeCard;
    private StatCard complexityCard;

    private BarChart methodSizeChart;
    private PieChart complexityPieChart;
    private BarChart opcodeChart;
    private StatTable methodTable;

    @Getter
    private boolean loaded = false;
    private SwingWorker<ClassStatistics, Void> currentWorker;

    public StatisticsView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(JStudioTheme.getBgTertiary());
        contentPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        buildUI();

        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        loadingOverlay = new LoadingOverlay();

        JPanel wrapperPanel = new JPanel();
        wrapperPanel.setLayout(new OverlayLayout(wrapperPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);
        wrapperPanel.add(loadingOverlay);
        wrapperPanel.add(scrollPane);

        add(wrapperPanel, BorderLayout.CENTER);

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void buildUI() {
        JPanel overviewPanel = createOverviewPanel();
        overviewPanel.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.add(overviewPanel);
        contentPanel.add(Box.createVerticalStrut(16));

        JPanel chartsRow = new JPanel(new GridLayout(1, 2, 16, 0));
        chartsRow.setOpaque(false);
        chartsRow.setAlignmentX(LEFT_ALIGNMENT);
        chartsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        methodSizeChart = new BarChart("Method Sizes (bytes)");
        methodSizeChart.setShowAllEntries(true);
        JScrollPane methodSizeScrollPane = createChartScrollPane(methodSizeChart);
        chartsRow.add(methodSizeScrollPane);

        complexityPieChart = new PieChart("Complexity Distribution");
        chartsRow.add(complexityPieChart);

        contentPanel.add(chartsRow);
        contentPanel.add(Box.createVerticalStrut(16));

        opcodeChart = new BarChart("Opcode Distribution");
        opcodeChart.setPercentageMode(true);
        opcodeChart.setShowAllEntries(true);
        JScrollPane opcodeScrollPane = createChartScrollPane(opcodeChart);
        opcodeScrollPane.setAlignmentX(LEFT_ALIGNMENT);
        opcodeScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        opcodeScrollPane.setPreferredSize(new Dimension(500, 280));
        contentPanel.add(opcodeScrollPane);
        contentPanel.add(Box.createVerticalStrut(16));

        methodTable = new StatTable("Method Details");
        methodTable.setAlignmentX(LEFT_ALIGNMENT);
        methodTable.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        contentPanel.add(methodTable);

        contentPanel.add(Box.createVerticalGlue());
    }

    private JScrollPane createChartScrollPane(JPanel chart) {
        JScrollPane sp = new JScrollPane(chart);
        sp.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        sp.getViewport().setBackground(JStudioTheme.getBgSecondary());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    private JPanel createOverviewPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        methodCountCard = new StatCard("0", "Methods", JStudioTheme.getAccent());
        fieldCountCard = new StatCard("0", "Fields", JStudioTheme.getInfo());
        bytecodeCard = new StatCard("0", "Bytes", JStudioTheme.getWarning());
        complexityCard = new StatCard("0.0", "Avg CCN", JStudioTheme.getSuccess());

        panel.add(methodCountCard);
        panel.add(fieldCountCard);
        panel.add(bytecodeCard);
        panel.add(complexityCard);

        return panel;
    }

    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Calculating statistics...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected ClassStatistics doInBackground() {
                StatisticsCalculator calculator = new StatisticsCalculator();
                return calculator.calculate(classEntry);
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) return;

                try {
                    ClassStatistics stats = get();
                    updateUI(stats);
                    loaded = true;
                } catch (Exception e) {
                    showError("Error calculating statistics: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private void updateUI(ClassStatistics stats) {
        methodCountCard.setValue(String.valueOf(stats.getMethodCount()));
        fieldCountCard.setValue(String.valueOf(stats.getFieldCount()));
        bytecodeCard.setValue(formatBytes(stats.getTotalBytecodeSize()));
        complexityCard.setValue(String.format("%.1f", stats.getAverageComplexity()));

        List<BarChart.BarEntry> sizeEntries = new ArrayList<>();
        for (ClassStatistics.MethodSizeInfo info : stats.getMethodSizes()) {
            sizeEntries.add(new BarChart.BarEntry(info.getName(), info.getBytecodeSize()));
        }
        methodSizeChart.setData(sizeEntries);

        List<PieChart.PieSlice> complexitySlices = new ArrayList<>();
        if (stats.getLowComplexityCount() > 0) {
            complexitySlices.add(new PieChart.PieSlice("Low (1-5)", stats.getLowComplexityCount(), JStudioTheme.getSuccess()));
        }
        if (stats.getMediumComplexityCount() > 0) {
            complexitySlices.add(new PieChart.PieSlice("Medium (6-10)", stats.getMediumComplexityCount(), JStudioTheme.getWarning()));
        }
        if (stats.getHighComplexityCount() > 0) {
            complexitySlices.add(new PieChart.PieSlice("High (11+)", stats.getHighComplexityCount(), JStudioTheme.getError()));
        }
        int totalMethods = stats.getLowComplexityCount() + stats.getMediumComplexityCount() + stats.getHighComplexityCount();
        complexityPieChart.setCenterLabel(totalMethods + " methods");
        complexityPieChart.setData(complexitySlices);

        List<BarChart.BarEntry> opcodeEntries = new ArrayList<>();
        Color[] opcodeColors = {
                JStudioTheme.getAccent(),
                JStudioTheme.getInfo(),
                JStudioTheme.getSuccess(),
                JStudioTheme.getWarning(),
                JStudioTheme.getError(),
                JStudioTheme.getAccentSecondary(),
                new Color(156, 136, 255),
                new Color(255, 136, 136),
                new Color(136, 255, 200),
                new Color(200, 200, 200),
                JStudioTheme.getTextSecondary()
        };

        Map<ClassStatistics.OpcodeCategory, Integer> distribution = stats.getOpcodeDistribution();
        int colorIndex = 0;
        for (ClassStatistics.OpcodeCategory category : ClassStatistics.OpcodeCategory.values()) {
            int count = distribution.getOrDefault(category, 0);
            if (count > 0) {
                opcodeEntries.add(new BarChart.BarEntry(
                        category.getDisplayName(),
                        count,
                        opcodeColors[colorIndex % opcodeColors.length]
                ));
            }
            colorIndex++;
        }
        opcodeEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        opcodeChart.setData(opcodeEntries);

        methodTable.setData(stats.getMethodDetails());

        revalidate();
        repaint();
    }

    private String formatBytes(int bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        return String.valueOf(bytes);
    }

    private void showError(String message) {
        System.err.println(message);
        methodCountCard.setValue("?");
        fieldCountCard.setValue("?");
        bytecodeCard.setValue("?");
        complexityCard.setValue("?");
    }

    public String getText() {
        return "// Statistics for: " + classEntry.getClassName() + "\n\n" +
                "Methods: " + methodCountCard + "\n" +
                "Fields: " + fieldCountCard + "\n";
    }

    public void copySelection() {
        // No text selection in this view
    }

    public String getSelectedText() {
        return null;
    }

    public void goToLine(int line) {
        // Not applicable for statistics view
    }

    public void showFindDialog() {
        // Not applicable for statistics view
    }

    public void scrollToText(String text) {
        // Not applicable for statistics view
    }

    public void setFontSize(int size) {
        // Could adjust card font sizes if needed
    }

    public void setWordWrap(boolean enabled) {
        // Not applicable for statistics view
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        setBackground(JStudioTheme.getBgTertiary());
        contentPanel.setBackground(JStudioTheme.getBgTertiary());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        methodCountCard.setAccentColor(JStudioTheme.getAccent());
        fieldCountCard.setAccentColor(JStudioTheme.getInfo());
        bytecodeCard.setAccentColor(JStudioTheme.getWarning());
        complexityCard.setAccentColor(JStudioTheme.getSuccess());

        repaint();
    }
}
