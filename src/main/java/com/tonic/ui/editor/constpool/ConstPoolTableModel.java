package com.tonic.ui.editor.constpool;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.ui.model.ClassEntryModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConstPoolTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"#", "Type", "Value"};
    private static final Class<?>[] COLUMN_CLASSES = {Integer.class, String.class, String.class};

    private List<ConstPoolEntry> allEntries = new ArrayList<>();
    private List<ConstPoolEntry> filteredEntries = new ArrayList<>();
    private String typeFilter = "All";
    private String searchText = "";

    @Override
    public int getRowCount() {
        return filteredEntries.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= filteredEntries.size()) {
            return null;
        }
        ConstPoolEntry entry = filteredEntries.get(rowIndex);
        switch (columnIndex) {
            case 0: return entry.getIndex();
            case 1: return entry.getType();
            case 2: return entry.getValue();
            default: return null;
        }
    }

    public ConstPoolEntry getEntryAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= filteredEntries.size()) {
            return null;
        }
        return filteredEntries.get(rowIndex);
    }

    public void loadFromClassEntry(ClassEntryModel classEntry) {
        List<ConstPoolEntry> entries = buildEntries(classEntry);
        setEntries(entries);
    }

    public static List<ConstPoolEntry> buildEntries(ClassEntryModel classEntry) {
        List<ConstPoolEntry> entries = new ArrayList<>();

        if (classEntry == null || classEntry.getClassFile() == null) {
            return entries;
        }

        ConstPool cp = classEntry.getClassFile().getConstPool();
        if (cp == null) {
            return entries;
        }

        List<Item<?>> items = cp.getItems();
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item != null) {
                String typeName = getTypeName(item);
                String displayValue = formatValue(item, cp);
                String rawValue = getRawValue(item, cp);
                entries.add(new ConstPoolEntry(i, typeName, displayValue, rawValue));
            }
        }

        return entries;
    }

    public void setEntries(List<ConstPoolEntry> entries) {
        allEntries = new ArrayList<>(entries);
        applyFilters();
    }

    public void setTypeFilter(String type) {
        this.typeFilter = type;
        applyFilters();
    }

    public void setSearchText(String text) {
        this.searchText = text != null ? text.toLowerCase() : "";
        applyFilters();
    }

    public String getTypeFilter() {
        return typeFilter;
    }

    public int getTotalCount() {
        return allEntries.size();
    }

    public int getFilteredCount() {
        return filteredEntries.size();
    }

    private void applyFilters() {
        filteredEntries = allEntries.stream()
                .filter(e -> "All".equals(typeFilter) || e.getType().equals(typeFilter))
                .filter(e -> searchText.isEmpty() ||
                        e.getValue().toLowerCase().contains(searchText) ||
                        e.getRawValue().toLowerCase().contains(searchText) ||
                        String.valueOf(e.getIndex()).contains(searchText))
                .collect(Collectors.toList());
        fireTableDataChanged();
    }

    private static String getTypeName(Item<?> item) {
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

    private static String formatValue(Item<?> item, ConstPool cp) {
        try {
            if (item instanceof Utf8Item) {
                String val = ((Utf8Item) item).getValue();
                return "\"" + escapeString(truncate(val, 80)) + "\"";
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
                return getUtf8(cp, classRef.getNameIndex()).replace('/', '.');
            }
            if (item instanceof StringRefItem) {
                StringRefItem stringRef = (StringRefItem) item;
                String val = getUtf8(cp, stringRef.getValue());
                return "\"" + escapeString(truncate(val, 60)) + "\"";
            }
            if (item instanceof FieldRefItem) {
                FieldRefItem ref = (FieldRefItem) item;
                return formatMemberRef(ref.getValue().getClassIndex(),
                        ref.getValue().getNameAndTypeIndex(), cp, true);
            }
            if (item instanceof MethodRefItem) {
                MethodRefItem ref = (MethodRefItem) item;
                return formatMemberRef(ref.getValue().getClassIndex(),
                        ref.getValue().getNameAndTypeIndex(), cp, false);
            }
            if (item instanceof InterfaceRefItem) {
                InterfaceRefItem ref = (InterfaceRefItem) item;
                return formatMemberRef(ref.getValue().getClassIndex(),
                        ref.getValue().getNameAndTypeIndex(), cp, false);
            }
            if (item instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) item;
                String name = getUtf8(cp, nat.getValue().getNameIndex());
                String desc = getUtf8(cp, nat.getValue().getDescriptorIndex());
                return name + " : " + truncate(desc, 40);
            }
            if (item instanceof MethodHandleItem) {
                MethodHandleItem mh = (MethodHandleItem) item;
                int kind = mh.getValue().getReferenceKind();
                int refIdx = mh.getValue().getReferenceIndex();
                return getHandleKindName(kind) + " #" + refIdx;
            }
            if (item instanceof MethodTypeItem) {
                MethodTypeItem mt = (MethodTypeItem) item;
                return truncate(getUtf8(cp, mt.getValue()), 60);
            }
            if (item instanceof InvokeDynamicItem) {
                InvokeDynamicItem indy = (InvokeDynamicItem) item;
                int bsmIdx = indy.getValue().getBootstrapMethodAttrIndex();
                int natIdx = indy.getValue().getNameAndTypeIndex();
                String nat = formatNameAndType(natIdx, cp);
                return "bootstrap#" + bsmIdx + " " + nat;
            }
            if (item instanceof ConstantDynamicItem) {
                ConstantDynamicItem cd = (ConstantDynamicItem) item;
                int bsmIdx = cd.getValue().getBootstrapMethodAttrIndex();
                int natIdx = cd.getValue().getNameAndTypeIndex();
                String nat = formatNameAndType(natIdx, cp);
                return "bootstrap#" + bsmIdx + " " + nat;
            }
            if (item instanceof PackageItem) {
                return getUtf8(cp, ((PackageItem) item).getValue()).replace('/', '.');
            }
            if (item instanceof ModuleItem) {
                return getUtf8(cp, ((ModuleItem) item).getValue());
            }
        } catch (Exception e) {
            return "<error>";
        }
        return "";
    }

    private static String getRawValue(Item<?> item, ConstPool cp) {
        try {
            if (item instanceof Utf8Item) {
                return ((Utf8Item) item).getValue();
            }
            if (item instanceof StringRefItem) {
                return getUtf8(cp, ((StringRefItem) item).getValue());
            }
            if (item instanceof ClassRefItem) {
                return getUtf8(cp, ((ClassRefItem) item).getNameIndex());
            }
            if (item instanceof FieldRefItem || item instanceof MethodRefItem || item instanceof InterfaceRefItem) {
                return formatValue(item, cp);
            }
        } catch (Exception e) {
            // ignore
        }
        return formatValue(item, cp);
    }

    private static String formatMemberRef(int classIdx, int natIdx, ConstPool cp, boolean isField) {
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
                if (isField) {
                    return className + "." + name + " : " + truncate(desc, 20);
                } else {
                    return className + "." + name + truncate(desc, 30);
                }
            }
            return className;
        } catch (Exception e) {
            return "???";
        }
    }

    private static String formatNameAndType(int natIdx, ConstPool cp) {
        try {
            Item<?> natItem = cp.getItem(natIdx);
            if (natItem instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) natItem;
                String name = getUtf8(cp, nat.getValue().getNameIndex());
                return name;
            }
        } catch (Exception e) {
            // ignore
        }
        return "#" + natIdx;
    }

    private static String getHandleKindName(int kind) {
        switch (kind) {
            case 1: return "getField";
            case 2: return "getStatic";
            case 3: return "putField";
            case 4: return "putStatic";
            case 5: return "invokeVirtual";
            case 6: return "invokeStatic";
            case 7: return "invokeSpecial";
            case 8: return "newInvokeSpecial";
            case 9: return "invokeInterface";
            default: return "kind" + kind;
        }
    }

    private static String getUtf8(ConstPool cp, int index) {
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

    private static String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\0", "\\0");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
