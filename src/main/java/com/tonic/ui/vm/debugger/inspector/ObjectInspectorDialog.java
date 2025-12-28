package com.tonic.ui.vm.debugger.inspector;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.debugger.edit.ValueEditDialog;
import com.tonic.ui.vm.debugger.edit.ValueParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ObjectInspectorDialog extends JDialog {

    private static final int VALUE_COLUMN = 2;

    private final ObjectInstance object;
    private final ClassResolver classResolver;
    private final ObjectFieldEnumerator enumerator;
    private final FieldsTableModel tableModel;
    private final JTable fieldsTable;
    private final Set<Integer> visitedObjectIds;

    private FieldEditCallback onFieldEdit;

    @FunctionalInterface
    public interface FieldEditCallback {
        void onFieldEdit(ObjectInstance object, String owner, String name, String desc, Object newValue);
    }

    public ObjectInspectorDialog(Window owner, ObjectInstance object, ClassResolver classResolver,
                                 Set<Integer> visitedObjectIds, FieldEditCallback onFieldEdit) {
        super(owner, buildTitle(object), ModalityType.APPLICATION_MODAL);
        this.object = object;
        this.classResolver = classResolver;
        this.visitedObjectIds = visitedObjectIds != null ? visitedObjectIds : new HashSet<>();
        this.visitedObjectIds.add(object.getId());
        this.onFieldEdit = onFieldEdit;
        this.enumerator = new ObjectFieldEnumerator(classResolver);

        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        tableModel = new FieldsTableModel();
        fieldsTable = createFieldsTable();

        JScrollPane scrollPane = new JScrollPane(fieldsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        loadFields();

        setPreferredSize(new Dimension(500, 400));
        pack();
        setLocationRelativeTo(owner);
    }

    private static String buildTitle(ObjectInstance object) {
        if (object == null) {
            return "Object Inspector - null";
        }

        String className = object.getClassName();
        int lastSlash = className.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;

        return "Object Inspector - " + simpleName + " @" + Integer.toHexString(object.getId());
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.setOpaque(false);

        JLabel classLabel = new JLabel("Class: " + object.getClassName().replace('/', '.'));
        classLabel.setForeground(JStudioTheme.getTextPrimary());
        classLabel.setFont(classLabel.getFont().deriveFont(Font.BOLD));
        panel.add(classLabel);

        JLabel idLabel = new JLabel("Object ID: " + object.getId() + " (0x" + Integer.toHexString(object.getId()) + ")");
        idLabel.setForeground(JStudioTheme.getTextSecondary());
        panel.add(idLabel);

        if (object instanceof ArrayInstance) {
            ArrayInstance array = (ArrayInstance) object;
            JLabel arrayLabel = new JLabel("Array Length: " + array.getLength());
            arrayLabel.setForeground(JStudioTheme.getTextSecondary());
            panel.add(arrayLabel);
        }

        return panel;
    }

    private JTable createFieldsTable() {
        JTable table = new JTable(tableModel);
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setSelectionBackground(JStudioTheme.getAccent());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getTableHeader().setBackground(JStudioTheme.getBgPrimary());
        table.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        table.setRowHeight(22);

        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);

        table.setDefaultRenderer(Object.class, new FieldCellRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick(e.getPoint());
                }
            }
        });

        return table;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setOpaque(false);

        JButton closeButton = new JButton("Close");
        closeButton.setBackground(JStudioTheme.getBgSecondary());
        closeButton.setForeground(JStudioTheme.getTextPrimary());
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());

        panel.add(closeButton);
        return panel;
    }

    private void loadFields() {
        List<FieldInfo> fields = enumerator.enumerate(object);
        tableModel.setFields(fields);
    }

    private void handleDoubleClick(Point point) {
        int row = fieldsTable.rowAtPoint(point);
        int column = fieldsTable.columnAtPoint(point);

        if (row < 0) {
            return;
        }

        FieldInfo field = tableModel.getFieldAt(row);
        if (field == null) {
            return;
        }

        ValueTag tag = field.getValueTag();

        if (tag == ValueTag.REFERENCE || tag == ValueTag.NULL) {
            Object rawValue = field.getValue();
            if (rawValue instanceof ObjectInstance) {
                ObjectInstance nestedObj = (ObjectInstance) rawValue;
                if (visitedObjectIds.contains(nestedObj.getId())) {
                    JOptionPane.showMessageDialog(this,
                        "Circular reference detected.\nThis object is already being inspected.",
                        "Circular Reference",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }

                Set<Integer> newVisited = new HashSet<>(visitedObjectIds);
                showDialog(this, nestedObj, classResolver, newVisited, onFieldEdit);
            } else if (rawValue == null) {
                JOptionPane.showMessageDialog(this,
                    "Field value is null. Cannot inspect null references.",
                    "Null Reference",
                    JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        if (column != VALUE_COLUMN || field.isFinal()) {
            return;
        }

        if (!field.isEditable()) {
            return;
        }

        ConcreteValue newValue = ValueEditDialog.showDialog(
            this,
            "Edit Field: " + field.getName(),
            field.getValueString(),
            tag
        );

        if (newValue != null && onFieldEdit != null) {
            Object converted = convertConcreteValue(newValue, field.getDescriptor());
            onFieldEdit.onFieldEdit(
                object,
                field.getOwnerClass(),
                field.getName(),
                field.getDescriptor(),
                converted
            );

            loadFields();
        }
    }

    private Object convertConcreteValue(ConcreteValue value, String descriptor) {
        if (value == null || value.isNull()) {
            return null;
        }

        switch (value.getTag()) {
            case INT:
                return value.asInt();
            case LONG:
                return value.asLong();
            case FLOAT:
                return value.asFloat();
            case DOUBLE:
                return value.asDouble();
            case REFERENCE:
                return value.asReference();
            default:
                return null;
        }
    }

    private class FieldCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            FieldInfo field = tableModel.getFieldAt(row);

            if (isSelected) {
                setBackground(JStudioTheme.getAccent());
            } else if (field != null && field.isFinal()) {
                setBackground(JStudioTheme.getBgPrimary());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }
            setForeground(JStudioTheme.getTextPrimary());

            if (field != null && column == VALUE_COLUMN) {
                ValueTag tag = field.getValueTag();
                if ((tag == ValueTag.REFERENCE || tag == ValueTag.NULL) &&
                    field.getValue() instanceof ObjectInstance) {
                    setForeground(JStudioTheme.getAccent());
                    setText(value + " [click to inspect]");
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (field.isEditable() && !field.isFinal()) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }

            return this;
        }
    }

    public static void showDialog(Component parent, ObjectInstance object,
                                  ClassResolver classResolver, FieldEditCallback onFieldEdit) {
        showDialog(parent, object, classResolver, null, onFieldEdit);
    }

    public static void showDialog(Component parent, ObjectInstance object,
                                  ClassResolver classResolver, Set<Integer> visitedObjectIds,
                                  FieldEditCallback onFieldEdit) {
        Window window = SwingUtilities.getWindowAncestor(parent);
        ObjectInspectorDialog dialog = new ObjectInspectorDialog(
            window, object, classResolver, visitedObjectIds, onFieldEdit
        );
        dialog.setVisible(true);
    }
}
