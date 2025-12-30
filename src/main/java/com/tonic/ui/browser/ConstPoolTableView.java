package com.tonic.ui.browser;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.ColumnWidths;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ConstPoolTableView extends ThemedJPanel {

    private final JTable table;
    private final ConstPoolTableModel tableModel;
    private final TableRowSorter<ConstPoolTableModel> sorter;
    private final JTextField filterField;
    private final JPanel filterPanel;

    private BiConsumer<Item<?>, Integer> selectionListener;
    private ConstPool constPool;

    public ConstPoolTableView() {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, UIConstants.SPACING_TINY));
        filterPanel.setBackground(JStudioTheme.getBgSecondary());

        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setForeground(JStudioTheme.getTextSecondary());
        filterLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_SMALL));
        filterPanel.add(filterLabel);

        filterField = new JTextField(20);
        filterField.setBackground(JStudioTheme.getBgTertiary());
        filterField.setForeground(JStudioTheme.getTextPrimary());
        filterField.setCaretColor(JStudioTheme.getTextPrimary());
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        filterPanel.add(filterField);

        add(filterPanel, BorderLayout.NORTH);

        tableModel = new ConstPoolTableModel();
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        table.setBackground(JStudioTheme.getBgTertiary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        table.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(ColumnWidths.INDEX);
        table.getColumnModel().getColumn(0).setMaxWidth(ColumnWidths.INDEX + 20);
        table.getColumnModel().getColumn(1).setPreferredWidth(ColumnWidths.INDEX);
        table.getColumnModel().getColumn(1).setMaxWidth(ColumnWidths.INDEX + 20);
        table.getColumnModel().getColumn(2).setPreferredWidth(ColumnWidths.TYPE);
        table.getColumnModel().getColumn(2).setMaxWidth(ColumnWidths.TYPE + 20);
        table.getColumnModel().getColumn(3).setPreferredWidth(ColumnWidths.STRING_VALUE);

        table.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        table.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        table.getTableHeader().setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));

        table.setDefaultRenderer(Object.class, new ConstPoolCellRenderer());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && selectionListener != null) {
                int viewRow = table.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    ConstPoolEntry entry = tableModel.getEntryAt(modelRow);
                    if (entry != null) {
                        selectionListener.accept(entry.item, entry.index);
                    }
                }
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selectionListener != null) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        ConstPoolEntry entry = tableModel.getEntryAt(modelRow);
                        if (entry != null) {
                            selectionListener.accept(entry.item, entry.index);
                        }
                    }
                }
            }
        });

        add(table, BorderLayout.CENTER);
    }

    public void loadConstPool(ConstPool constPool) {
        this.constPool = constPool;
        tableModel.loadConstPool(constPool);
    }

    public void setSelectionListener(BiConsumer<Item<?>, Integer> listener) {
        this.selectionListener = listener;
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
    }

    static class ConstPoolEntry {
        final int index;
        final Item<?> item;
        final String typeName;
        final String displayValue;

        ConstPoolEntry(int index, Item<?> item, String typeName, String displayValue) {
            this.index = index;
            this.item = item;
            this.typeName = typeName;
            this.displayValue = displayValue;
        }
    }

    private static class ConstPoolTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Tag", "Type", "Value"};
        private final List<ConstPoolEntry> entries = new ArrayList<>();
        private ConstPool constPool;

        void loadConstPool(ConstPool constPool) {
            this.constPool = constPool;
            entries.clear();

            if (constPool == null) {
                fireTableDataChanged();
                return;
            }

            List<Item<?>> items = constPool.getItems();
            for (int i = 1; i < items.size(); i++) {
                Item<?> item = items.get(i);
                if (item != null) {
                    String typeName = getTypeName(item);
                    String displayValue = formatValue(item, constPool);
                    entries.add(new ConstPoolEntry(i, item, typeName, displayValue));
                }
            }

            fireTableDataChanged();
        }

        ConstPoolEntry getEntryAt(int row) {
            if (row >= 0 && row < entries.size()) {
                return entries.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ConstPoolEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.index;
                case 1: return entry.item.getType() & 0xFF;
                case 2: return entry.typeName;
                case 3: return entry.displayValue;
                default: return "";
            }
        }

        private String getTypeName(Item<?> item) {
            if (item instanceof Utf8Item) return "Utf8";
            if (item instanceof IntegerItem) return "Integer";
            if (item instanceof FloatItem) return "Float";
            if (item instanceof LongItem) return "Long";
            if (item instanceof DoubleItem) return "Double";
            if (item instanceof ClassRefItem) return "Class";
            if (item instanceof StringRefItem) return "String";
            if (item instanceof FieldRefItem) return "FieldRef";
            if (item instanceof MethodRefItem) return "MethodRef";
            if (item instanceof InterfaceRefItem) return "InterfaceRef";
            if (item instanceof NameAndTypeRefItem) return "NameAndType";
            if (item instanceof MethodHandleItem) return "MethodHandle";
            if (item instanceof MethodTypeItem) return "MethodType";
            if (item instanceof ConstantDynamicItem) return "Dynamic";
            if (item instanceof InvokeDynamicItem) return "InvokeDynamic";
            if (item instanceof PackageItem) return "Package";
            if (item instanceof ModuleItem) return "Module";
            return "Unknown";
        }

        private String formatValue(Item<?> item, ConstPool cp) {
            try {
                if (item instanceof Utf8Item) {
                    String val = ((Utf8Item) item).getValue();
                    if (val.length() > 80) {
                        val = val.substring(0, 77) + "...";
                    }
                    return escapeString(val);
                }
                if (item instanceof IntegerItem) {
                    return String.valueOf(((IntegerItem) item).getValue());
                }
                if (item instanceof FloatItem) {
                    return ((FloatItem) item).getValue() + "f";
                }
                if (item instanceof LongItem) {
                    return ((LongItem) item).getValue() + "L";
                }
                if (item instanceof DoubleItem) {
                    return ((DoubleItem) item).getValue() + "d";
                }
                if (item instanceof ClassRefItem) {
                    ClassRefItem classRef = (ClassRefItem) item;
                    int nameIdx = classRef.getNameIndex();
                    Item<?> nameItem = cp.getItem(nameIdx);
                    if (nameItem instanceof Utf8Item) {
                        return ((Utf8Item) nameItem).getValue();
                    }
                    return "→ #" + nameIdx;
                }
                if (item instanceof StringRefItem) {
                    StringRefItem stringRef = (StringRefItem) item;
                    int utf8Idx = stringRef.getValue();
                    Item<?> utf8Item = cp.getItem(utf8Idx);
                    if (utf8Item instanceof Utf8Item) {
                        String val = ((Utf8Item) utf8Item).getValue();
                        if (val.length() > 60) {
                            val = val.substring(0, 57) + "...";
                        }
                        return "\"" + escapeString(val) + "\"";
                    }
                    return "→ #" + utf8Idx;
                }
                if (item instanceof FieldRefItem) {
                    FieldRefItem fieldRef = (FieldRefItem) item;
                    return formatMemberRef(fieldRef.getValue().getClassIndex(),
                            fieldRef.getValue().getNameAndTypeIndex(), cp);
                }
                if (item instanceof MethodRefItem) {
                    MethodRefItem methodRef = (MethodRefItem) item;
                    return formatMemberRef(methodRef.getValue().getClassIndex(),
                            methodRef.getValue().getNameAndTypeIndex(), cp);
                }
                if (item instanceof InterfaceRefItem) {
                    InterfaceRefItem ifaceRef = (InterfaceRefItem) item;
                    return formatMemberRef(ifaceRef.getValue().getClassIndex(),
                            ifaceRef.getValue().getNameAndTypeIndex(), cp);
                }
                if (item instanceof NameAndTypeRefItem) {
                    NameAndTypeRefItem nat = (NameAndTypeRefItem) item;
                    int nameIdx = nat.getValue().getNameIndex();
                    int descIdx = nat.getValue().getDescriptorIndex();
                    String name = getUtf8(cp, nameIdx);
                    String desc = getUtf8(cp, descIdx);
                    return name + ":" + desc;
                }
                if (item instanceof MethodHandleItem) {
                    MethodHandleItem mh = (MethodHandleItem) item;
                    int kind = mh.getValue().getReferenceKind();
                    int refIdx = mh.getValue().getReferenceIndex();
                    return "kind=" + kind + " → #" + refIdx;
                }
                if (item instanceof MethodTypeItem) {
                    MethodTypeItem mt = (MethodTypeItem) item;
                    return getUtf8(cp, mt.getValue());
                }
                if (item instanceof InvokeDynamicItem) {
                    InvokeDynamicItem indy = (InvokeDynamicItem) item;
                    int bsm = indy.getValue().getBootstrapMethodAttrIndex();
                    int natIdx = indy.getValue().getNameAndTypeIndex();
                    return "bsm=" + bsm + ", nat=#" + natIdx;
                }
                if (item instanceof ConstantDynamicItem) {
                    ConstantDynamicItem cd = (ConstantDynamicItem) item;
                    int bsm = cd.getValue().getBootstrapMethodAttrIndex();
                    int natIdx = cd.getValue().getNameAndTypeIndex();
                    return "bsm=" + bsm + ", nat=#" + natIdx;
                }
                if (item instanceof PackageItem) {
                    PackageItem pkg = (PackageItem) item;
                    return getUtf8(cp, pkg.getValue());
                }
                if (item instanceof ModuleItem) {
                    ModuleItem mod = (ModuleItem) item;
                    return getUtf8(cp, mod.getValue());
                }
            } catch (Exception e) {
                return "<error>";
            }
            return item.getValue() != null ? item.getValue().toString() : "<null>";
        }

        private String formatMemberRef(int classIdx, int natIdx, ConstPool cp) {
            try {
                String className = "";
                Item<?> classItem = cp.getItem(classIdx);
                if (classItem instanceof ClassRefItem) {
                    int nameIdx = ((ClassRefItem) classItem).getNameIndex();
                    className = getUtf8(cp, nameIdx);
                    int lastSlash = className.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        className = className.substring(lastSlash + 1);
                    }
                }

                Item<?> natItem = cp.getItem(natIdx);
                if (natItem instanceof NameAndTypeRefItem) {
                    NameAndTypeRefItem nat = (NameAndTypeRefItem) natItem;
                    String name = getUtf8(cp, nat.getValue().getNameIndex());
                    String desc = getUtf8(cp, nat.getValue().getDescriptorIndex());
                    return className + "." + name + desc;
                }
                return className + ".#" + natIdx;
            } catch (Exception e) {
                return "#" + classIdx + ".#" + natIdx;
            }
        }

        private String getUtf8(ConstPool cp, int index) {
            try {
                Item<?> item = cp.getItem(index);
                if (item instanceof Utf8Item) {
                    return ((Utf8Item) item).getValue();
                }
            } catch (Exception e) {
                // ignore
            }
            return "#" + index;
        }

        private String escapeString(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    private static class ConstPoolCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                setBackground(row % 2 == 0 ? JStudioTheme.getBgTertiary() : JStudioTheme.getBgSecondary());
            }

            if (column == 2) {
                String type = value != null ? value.toString() : "";
                setForeground(getTypeColor(type));
            } else {
                setForeground(isSelected ? JStudioTheme.getTextPrimary() : JStudioTheme.getTextPrimary());
            }

            return this;
        }

        private Color getTypeColor(String type) {
            switch (type) {
                case "Utf8": return JStudioTheme.getAccent();
                case "Class": return JStudioTheme.getAccentSecondary();
                case "String": return JStudioTheme.getSuccess();
                case "MethodRef":
                case "InterfaceRef": return JStudioTheme.getWarning();
                case "FieldRef": return JStudioTheme.getInfo();
                case "Integer":
                case "Long":
                case "Float":
                case "Double": return new Color(206, 145, 120);
                case "NameAndType": return JStudioTheme.getTextSecondary();
                default: return JStudioTheme.getTextPrimary();
            }
        }
    }
}
