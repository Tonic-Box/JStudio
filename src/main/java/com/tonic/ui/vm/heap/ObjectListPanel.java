package com.tonic.ui.vm.heap;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.heap.model.HeapObject;
import com.tonic.ui.vm.heap.model.ProvenanceInfo;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ObjectListPanel extends ThemedJPanel {

    private final JTable table;
    private final ObjectTableModel tableModel;
    private final JComboBox<String> sortCombo;

    private Consumer<HeapObject> onObjectSelected;
    private List<HeapObject> allObjects = new ArrayList<>();

    public ObjectListPanel() {
        super(BackgroundStyle.SECONDARY, new BorderLayout(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 2));
        topPanel.setBackground(JStudioTheme.getBgSecondary());
        topPanel.add(new JLabel("Sort:"));
        sortCombo = new JComboBox<>(new String[]{"ID", "Age (newest)", "Age (oldest)", "Class"});
        sortCombo.addActionListener(e -> sortAndRefresh());
        topPanel.add(sortCombo);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new ObjectTableModel();
        table = new JTable(tableModel);
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setSelectionBackground(JStudioTheme.getAccent());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        table.getColumnModel().getColumn(0).setPreferredWidth(25);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(30);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);

        table.setDefaultRenderer(Object.class, new ObjectCellRenderer());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onObjectSelected != null) {
                int row = table.getSelectedRow();
                if (row >= 0 && row < allObjects.size()) {
                    onObjectSelected.accept(allObjects.get(row));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        add(scroll, BorderLayout.CENTER);
    }

    public void setOnObjectSelected(Consumer<HeapObject> callback) {
        this.onObjectSelected = callback;
    }

    public void setObjects(List<HeapObject> objects) {
        this.allObjects = new ArrayList<>(objects);
        sortAndRefresh();
        if (!allObjects.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void sortAndRefresh() {
        int sortIndex = sortCombo.getSelectedIndex();
        switch (sortIndex) {
            case 0:
                allObjects.sort(Comparator.comparingInt(HeapObject::getId));
                break;
            case 1:
                allObjects.sort((a, b) -> Long.compare(b.getAllocationTime(), a.getAllocationTime()));
                break;
            case 2:
                allObjects.sort(Comparator.comparingLong(HeapObject::getAllocationTime));
                break;
            case 3:
                allObjects.sort(Comparator.comparing(HeapObject::getClassName));
                break;
        }
        tableModel.fireTableDataChanged();
    }

    private class ObjectTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Class", "Age", "Provenance"};

        @Override
        public int getRowCount() {
            return allObjects.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            HeapObject obj = allObjects.get(row);
            switch (column) {
                case 0: return obj.getId();
                case 1: return obj.getSimpleClassName();
                case 2: return obj.getAllocationTime();
                case 3:
                    ProvenanceInfo prov = obj.getProvenance();
                    if (prov == null) return "-";
                    String method = prov.getMethodName();
                    return method != null ? method : "-";
                default: return null;
            }
        }
    }

    private class ObjectCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setBackground(isSelected ? JStudioTheme.getAccent() : JStudioTheme.getBgSecondary());
            setForeground(JStudioTheme.getTextPrimary());

            if (column == 0 && value instanceof Integer) {
                setText("#" + value);
            } else if (column == 2 && value instanceof Long) {
                long age = (Long) value;
                if (age >= 1000) {
                    setText(String.format("%.1fk", age / 1000.0));
                } else {
                    setText(String.valueOf(age));
                }
            }

            if (row < allObjects.size()) {
                HeapObject obj = allObjects.get(row);
                if (obj.isLambda()) {
                    if (!isSelected) setForeground(JStudioTheme.getAccentSecondary());
                } else if (obj.isArray()) {
                    if (!isSelected) setForeground(JStudioTheme.getInfo());
                } else if (obj.isString()) {
                    if (!isSelected) setForeground(JStudioTheme.getWarning());
                }
            }

            return this;
        }
    }
}
