package com.tonic.ui.editor.statistics.charts;

import com.tonic.ui.editor.statistics.ClassStatistics;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

public class StatTable extends JPanel implements ThemeChangeListener {

    private final JTable table;
    private final MethodTableModel tableModel;
    private final String title;

    private static final String[] COLUMN_NAMES = {"Method", "Size", "Stack", "Locals", "CCN", "Loops", "Branches"};
    private static final int[] COLUMN_WIDTHS = {180, 60, 50, 50, 45, 50, 60};

    public StatTable(String title) {
        this.title = title;
        setLayout(new BorderLayout());
        setOpaque(false);

        tableModel = new MethodTableModel();
        table = new JTable(tableModel);
        setupTable();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        JPanel titlePanel = createTitlePanel();
        add(titlePanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(500, 200));
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private JPanel createTitlePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(JStudioTheme.getCodeFont(12).deriveFont(Font.BOLD));
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(titleLabel, BorderLayout.WEST);

        return panel;
    }

    private void setupTable() {
        table.setFont(JStudioTheme.getCodeFont(11));
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);

        JTableHeader header = table.getTableHeader();
        header.setFont(JStudioTheme.getCodeFont(11).deriveFont(Font.BOLD));
        header.setBackground(JStudioTheme.getBgTertiary());
        header.setForeground(JStudioTheme.getTextPrimary());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        TableColumnModel columnModel = table.getColumnModel();
        for (int i = 0; i < COLUMN_WIDTHS.length && i < columnModel.getColumnCount(); i++) {
            TableColumn column = columnModel.getColumn(i);
            column.setPreferredWidth(COLUMN_WIDTHS[i]);
            if (i == 0) {
                column.setCellRenderer(new MethodNameRenderer());
            } else if (i == 4) {
                column.setCellRenderer(new ComplexityRenderer());
            } else {
                column.setCellRenderer(new NumberRenderer());
            }
        }
    }

    public void setData(List<ClassStatistics.MethodDetailInfo> methods) {
        tableModel.setData(methods);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.getTableHeader().setBackground(JStudioTheme.getBgTertiary());
        table.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        repaint();
    }

    private static class MethodTableModel extends AbstractTableModel {
        private List<ClassStatistics.MethodDetailInfo> methods = List.of();

        public void setData(List<ClassStatistics.MethodDetailInfo> methods) {
            this.methods = methods != null ? methods : List.of();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return methods.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 0) return String.class;
            return Integer.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ClassStatistics.MethodDetailInfo method = methods.get(row);
            switch (column) {
                case 0: return method.getName();
                case 1: return method.getBytecodeSize();
                case 2: return method.getMaxStack();
                case 3: return method.getMaxLocals();
                case 4: return method.getCyclomaticComplexity();
                case 5: return method.getLoopCount();
                case 6: return method.getBranchCount();
                default: return null;
            }
        }
    }

    private static class MethodNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setFont(JStudioTheme.getCodeFont(11));
            setBorder(new EmptyBorder(0, 8, 0, 4));

            if (!isSelected) {
                setBackground(row % 2 == 0 ? JStudioTheme.getBgSecondary() : darken(JStudioTheme.getBgSecondary(), 0.95f));
                setForeground(JStudioTheme.getTextPrimary());
            }

            return this;
        }

        private Color darken(Color color, float factor) {
            return new Color(
                    (int) (color.getRed() * factor),
                    (int) (color.getGreen() * factor),
                    (int) (color.getBlue() * factor)
            );
        }
    }

    private static class NumberRenderer extends DefaultTableCellRenderer {
        public NumberRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setFont(JStudioTheme.getCodeFont(11));
            setBorder(new EmptyBorder(0, 4, 0, 8));

            if (!isSelected) {
                setBackground(row % 2 == 0 ? JStudioTheme.getBgSecondary() : darken(JStudioTheme.getBgSecondary(), 0.95f));
                setForeground(JStudioTheme.getTextSecondary());
            }

            return this;
        }

        private Color darken(Color color, float factor) {
            return new Color(
                    (int) (color.getRed() * factor),
                    (int) (color.getGreen() * factor),
                    (int) (color.getBlue() * factor)
            );
        }
    }

    private static class ComplexityRenderer extends DefaultTableCellRenderer {
        public ComplexityRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setFont(JStudioTheme.getCodeFont(11).deriveFont(Font.BOLD));
            setBorder(new EmptyBorder(0, 4, 0, 4));

            int ccn = value != null ? (Integer) value : 0;

            if (!isSelected) {
                setBackground(row % 2 == 0 ? JStudioTheme.getBgSecondary() : darken(JStudioTheme.getBgSecondary(), 0.95f));

                if (ccn <= 5) {
                    setForeground(JStudioTheme.getSuccess());
                } else if (ccn <= 10) {
                    setForeground(JStudioTheme.getWarning());
                } else {
                    setForeground(JStudioTheme.getError());
                }
            }

            return this;
        }

        private Color darken(Color color, float factor) {
            return new Color(
                    (int) (color.getRed() * factor),
                    (int) (color.getGreen() * factor),
                    (int) (color.getBlue() * factor)
            );
        }
    }
}
