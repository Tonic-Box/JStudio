package com.tonic.ui.vm.heap;

import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArrayEditorDialog extends JDialog {

    @Getter
    private final String componentType;
    private final List<Object> elements = new ArrayList<>();
    @Getter
    private boolean confirmed = false;

    private JTable elementsTable;
    private DefaultTableModel tableModel;
    private JButton addBtn;
    private JButton removeBtn;
    private JButton moveUpBtn;
    private JButton moveDownBtn;
    private JButton okBtn;
    private JButton cancelBtn;
    private JLabel infoLabel;

    public ArrayEditorDialog(Window owner, String componentType, Object[] initialValues) {
        super(owner, "Edit Array: " + formatComponentType(componentType) + "[]", ModalityType.APPLICATION_MODAL);
        this.componentType = componentType;

        if (initialValues != null) {
            Collections.addAll(elements, initialValues);
        }

        initComponents();
        layoutComponents();
        updateButtonStates();

        setSize(400, 350);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        tableModel = new DefaultTableModel(new String[]{"Index", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Integer.class : Object.class;
            }
        };

        elementsTable = new JTable(tableModel);
        elementsTable.setBackground(JStudioTheme.getBgSecondary());
        elementsTable.setForeground(JStudioTheme.getTextPrimary());
        elementsTable.setSelectionBackground(JStudioTheme.getAccent());
        elementsTable.setRowHeight(24);
        elementsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        elementsTable.getColumnModel().getColumn(0).setMaxWidth(60);
        elementsTable.getColumnModel().getColumn(1).setPreferredWidth(300);

        elementsTable.getColumnModel().getColumn(1).setCellEditor(createCellEditor());
        elementsTable.getColumnModel().getColumn(1).setCellRenderer(createCellRenderer());

        refreshTable();

        addBtn = new JButton("Add");
        addBtn.addActionListener(e -> addElement());

        removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> removeSelected());

        moveUpBtn = new JButton("▲");
        moveUpBtn.setToolTipText("Move Up");
        moveUpBtn.addActionListener(e -> moveUp());

        moveDownBtn = new JButton("▼");
        moveDownBtn.setToolTipText("Move Down");
        moveDownBtn.addActionListener(e -> moveDown());

        okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            commitEditing();
            confirmed = true;
            dispose();
        });

        cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        infoLabel = new JLabel(getTypeHint());
        infoLabel.setForeground(JStudioTheme.getTextSecondary());
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));

        elementsTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(JStudioTheme.getBgPrimary());
        topPanel.add(infoLabel);
        add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(elementsTable);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        add(scrollPane, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(JStudioTheme.getBgPrimary());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        Dimension btnSize = new Dimension(80, 28);
        for (JButton btn : new JButton[]{addBtn, removeBtn, moveUpBtn, moveDownBtn}) {
            btn.setMaximumSize(btnSize);
            btn.setPreferredSize(btnSize);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            rightPanel.add(btn);
            rightPanel.add(Box.createVerticalStrut(5));
        }
        rightPanel.add(Box.createVerticalGlue());

        add(rightPanel, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(JStudioTheme.getBgPrimary());
        bottomPanel.add(okBtn);
        bottomPanel.add(cancelBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private TableCellEditor createCellEditor() {
        switch (componentType) {
            case "Z":
                JComboBox<Boolean> boolCombo = new JComboBox<>(new Boolean[]{true, false});
                return new DefaultCellEditor(boolCombo);
            case "C":
                JTextField charField = new JTextField();
                return new DefaultCellEditor(charField) {
                    @Override
                    public Object getCellEditorValue() {
                        String text = charField.getText();
                        if (text.isEmpty()) return '\0';
                        if (text.length() == 1) return text.charAt(0);
                        if (text.startsWith("'") && text.endsWith("'") && text.length() == 3) {
                            return text.charAt(1);
                        }
                        try {
                            return (char) Integer.parseInt(text);
                        } catch (NumberFormatException e) {
                            return text.charAt(0);
                        }
                    }
                };
            default:
                return new DefaultCellEditor(new JTextField());
        }
    }

    private TableCellRenderer createCellRenderer() {
        return new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBackground(isSelected ? JStudioTheme.getAccent() : JStudioTheme.getBgSecondary());
                setForeground(JStudioTheme.getTextPrimary());

                if (value == null) {
                    setText("null");
                    setForeground(JStudioTheme.getTextSecondary());
                } else if (value instanceof String) {
                    setText("\"" + value + "\"");
                } else if (value instanceof Character) {
                    setText("'" + value + "'");
                } else {
                    setText(String.valueOf(value));
                }
                return this;
            }
        };
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (int i = 0; i < elements.size(); i++) {
            tableModel.addRow(new Object[]{i, elements.get(i)});
        }
    }

    private void addElement() {
        Object defaultValue = getDefaultValue();
        elements.add(defaultValue);
        refreshTable();
        int lastRow = elements.size() - 1;
        elementsTable.setRowSelectionInterval(lastRow, lastRow);
        elementsTable.editCellAt(lastRow, 1);
        updateButtonStates();
    }

    private void removeSelected() {
        int[] selected = elementsTable.getSelectedRows();
        if (selected.length == 0) return;

        commitEditing();

        for (int i = selected.length - 1; i >= 0; i--) {
            if (selected[i] < elements.size()) {
                elements.remove(selected[i]);
            }
        }
        refreshTable();
        updateButtonStates();
    }

    private void moveUp() {
        int row = elementsTable.getSelectedRow();
        if (row <= 0) return;
        commitEditing();
        Object temp = elements.get(row);
        elements.set(row, elements.get(row - 1));
        elements.set(row - 1, temp);
        refreshTable();
        elementsTable.setRowSelectionInterval(row - 1, row - 1);
    }

    private void moveDown() {
        int row = elementsTable.getSelectedRow();
        if (row < 0 || row >= elements.size() - 1) return;
        commitEditing();
        Object temp = elements.get(row);
        elements.set(row, elements.get(row + 1));
        elements.set(row + 1, temp);
        refreshTable();
        elementsTable.setRowSelectionInterval(row + 1, row + 1);
    }

    private void commitEditing() {
        if (elementsTable.isEditing()) {
            elementsTable.getCellEditor().stopCellEditing();
        }

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object value = tableModel.getValueAt(i, 1);
            elements.set(i, parseValue(value));
        }
    }

    private Object parseValue(Object rawValue) {
        if (rawValue == null) return null;
        String str = rawValue.toString().trim();

        if (str.equalsIgnoreCase("null")) return null;

        try {
            switch (componentType) {
                case "I":
                    return Integer.parseInt(str);
                case "J":
                    return Long.parseLong(str.replace("L", "").replace("l", ""));
                case "F":
                    return Float.parseFloat(str.replace("f", "").replace("F", ""));
                case "D":
                    return Double.parseDouble(str.replace("d", "").replace("D", ""));
                case "B":
                    return (byte) Integer.parseInt(str);
                case "S":
                    return (short) Integer.parseInt(str);
                case "Z":
                    if (rawValue instanceof Boolean) return rawValue;
                    return Boolean.parseBoolean(str);
                case "C":
                    if (rawValue instanceof Character) return rawValue;
                    if (str.length() == 1) return str.charAt(0);
                    if (str.startsWith("'") && str.endsWith("'") && str.length() == 3) {
                        return str.charAt(1);
                    }
                    return (char) Integer.parseInt(str);
                case "Ljava/lang/String;":
                    if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
                        return str.substring(1, str.length() - 1);
                    }
                    return str;
                default:
                    if (componentType.startsWith("Ljava/lang/Integer")) return Integer.parseInt(str);
                    if (componentType.startsWith("Ljava/lang/Long")) return Long.parseLong(str);
                    if (componentType.startsWith("Ljava/lang/Double")) return Double.parseDouble(str);
                    if (componentType.startsWith("Ljava/lang/Float")) return Float.parseFloat(str);
                    if (componentType.startsWith("Ljava/lang/Boolean")) return Boolean.parseBoolean(str);
                    return str;
            }
        } catch (NumberFormatException e) {
            return getDefaultValue();
        }
    }

    private Object getDefaultValue() {
        switch (componentType) {
            case "I": return 0;
            case "J": return 0L;
            case "F": return 0.0f;
            case "D": return 0.0;
            case "B": return (byte) 0;
            case "S": return (short) 0;
            case "Z": return false;
            case "C": return 'a';
            case "Ljava/lang/String;": return "";
            default: return null;
        }
    }

    private String getTypeHint() {
        switch (componentType) {
            case "I": return "Integer array - enter whole numbers";
            case "J": return "Long array - enter whole numbers (suffix L optional)";
            case "F": return "Float array - enter decimal numbers";
            case "D": return "Double array - enter decimal numbers";
            case "B": return "Byte array - enter values -128 to 127";
            case "S": return "Short array - enter values -32768 to 32767";
            case "Z": return "Boolean array - select true/false";
            case "C": return "Char array - enter single characters or ASCII codes";
            case "Ljava/lang/String;": return "String array - enter text values";
            default: return "Array of " + formatComponentType(componentType);
        }
    }

    private void updateButtonStates() {
        int selected = elementsTable.getSelectedRow();
        removeBtn.setEnabled(selected >= 0);
        moveUpBtn.setEnabled(selected > 0);
        moveDownBtn.setEnabled(selected >= 0 && selected < elements.size() - 1);
    }

    public Object[] getElements() {
        return elements.toArray();
    }

    private static String formatComponentType(String type) {
        switch (type) {
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            case "B": return "byte";
            case "S": return "short";
            case "Z": return "boolean";
            case "C": return "char";
            case "Ljava/lang/String;": return "String";
            default:
                if (type.startsWith("L") && type.endsWith(";")) {
                    String className = type.substring(1, type.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return type;
        }
    }

    public static String formatArrayDisplay(Object[] elements, String componentType) {
        if (elements == null || elements.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) sb.append(", ");
            Object val = elements[i];
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else if (val instanceof Character) {
                sb.append("'").append(val).append("'");
            } else {
                sb.append(val);
            }
            if (sb.length() > 50) {
                sb.append(", ...");
                break;
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
