package com.tonic.ui.browser;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ConstPoolTreeView extends ThemedJPanel {

    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel treeModel;

    private BiConsumer<Item<?>, Integer> selectionListener;

    public ConstPoolTreeView() {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        root = new DefaultMutableTreeNode("Constant Pool");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);

        tree.setBackground(JStudioTheme.getBgTertiary());
        tree.setForeground(JStudioTheme.getTextPrimary());
        tree.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        tree.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ConstPoolTreeCellRenderer());

        tree.addTreeSelectionListener(e -> {
            if (selectionListener == null) return;
            TreePath path = e.getPath();
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) node).getUserObject();
                    if (userObj instanceof ItemNode) {
                        ItemNode itemNode = (ItemNode) userObj;
                        selectionListener.accept(itemNode.item, itemNode.index);
                    }
                }
            }
        });

        add(tree, BorderLayout.CENTER);
    }

    public void loadConstPool(ConstPool constPool) {
        root.removeAllChildren();

        if (constPool == null) {
            treeModel.reload();
            return;
        }

        Map<String, List<ItemNode>> grouped = new LinkedHashMap<>();
        String[] typeOrder = {"Utf8", "Integer", "Long", "Float", "Double", "Class", "String",
                "FieldRef", "MethodRef", "InterfaceRef", "NameAndType",
                "MethodHandle", "MethodType", "Dynamic", "InvokeDynamic", "Package", "Module"};

        for (String type : typeOrder) {
            grouped.put(type, new ArrayList<>());
        }

        List<Item<?>> items = constPool.getItems();
        for (int i = 1; i < items.size(); i++) {
            Item<?> item = items.get(i);
            if (item != null) {
                String typeName = getTypeName(item);
                String displayValue = formatValue(item, constPool);
                ItemNode node = new ItemNode(i, item, typeName, displayValue);
                List<ItemNode> list = grouped.computeIfAbsent(typeName, k -> new ArrayList<>());
                list.add(node);
            }
        }

        for (Map.Entry<String, List<ItemNode>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;

            String typeName = entry.getKey();
            int count = entry.getValue().size();
            TypeNode typeNode = new TypeNode(typeName, count);
            DefaultMutableTreeNode typeTreeNode = new DefaultMutableTreeNode(typeNode);

            for (ItemNode itemNode : entry.getValue()) {
                typeTreeNode.add(new DefaultMutableTreeNode(itemNode));
            }

            root.add(typeTreeNode);
        }

        treeModel.reload();

        for (int i = 0; i < tree.getRowCount() && i < 5; i++) {
            tree.expandRow(i);
        }
    }

    public void setSelectionListener(BiConsumer<Item<?>, Integer> listener) {
        this.selectionListener = listener;
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
                if (val.length() > 50) {
                    val = val.substring(0, 47) + "...";
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
                return getUtf8(cp, nameIdx);
            }
            if (item instanceof StringRefItem) {
                StringRefItem stringRef = (StringRefItem) item;
                int utf8Idx = stringRef.getValue();
                String val = getUtf8(cp, utf8Idx);
                if (val.length() > 40) {
                    val = val.substring(0, 37) + "...";
                }
                return "\"" + escapeString(val) + "\"";
            }
            if (item instanceof FieldRefItem) {
                FieldRefItem ref = (FieldRefItem) item;
                return formatMemberRef(ref.getValue().getClassIndex(),
                        ref.getValue().getNameAndTypeIndex(), cp);
            }
            if (item instanceof MethodRefItem) {
                MethodRefItem ref = (MethodRefItem) item;
                return formatMemberRef(ref.getValue().getClassIndex(),
                        ref.getValue().getNameAndTypeIndex(), cp);
            }
            if (item instanceof InterfaceRefItem) {
                InterfaceRefItem ref = (InterfaceRefItem) item;
                return formatMemberRef(ref.getValue().getClassIndex(),
                        ref.getValue().getNameAndTypeIndex(), cp);
            }
            if (item instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) item;
                String name = getUtf8(cp, nat.getValue().getNameIndex());
                String desc = getUtf8(cp, nat.getValue().getDescriptorIndex());
                return name + ":" + truncate(desc, 30);
            }
            if (item instanceof MethodHandleItem) {
                MethodHandleItem mh = (MethodHandleItem) item;
                return "kind=" + mh.getValue().getReferenceKind();
            }
            if (item instanceof MethodTypeItem) {
                MethodTypeItem mt = (MethodTypeItem) item;
                return truncate(getUtf8(cp, mt.getValue()), 40);
            }
            if (item instanceof InvokeDynamicItem) {
                InvokeDynamicItem indy = (InvokeDynamicItem) item;
                return "bsm=" + indy.getValue().getBootstrapMethodAttrIndex();
            }
            if (item instanceof ConstantDynamicItem) {
                ConstantDynamicItem cd = (ConstantDynamicItem) item;
                return "bsm=" + cd.getValue().getBootstrapMethodAttrIndex();
            }
            if (item instanceof PackageItem) {
                return getUtf8(cp, ((PackageItem) item).getValue());
            }
            if (item instanceof ModuleItem) {
                return getUtf8(cp, ((ModuleItem) item).getValue());
            }
        } catch (Exception e) {
            return "<error>";
        }
        return "";
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
                return className + "." + name;
            }
            return className;
        } catch (Exception e) {
            return "???";
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

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    static class TypeNode {
        final String typeName;
        final int count;

        TypeNode(String typeName, int count) {
            this.typeName = typeName;
            this.count = count;
        }

        @Override
        public String toString() {
            return typeName + " (" + count + ")";
        }
    }

    static class ItemNode {
        final int index;
        final Item<?> item;
        final String typeName;
        final String displayValue;

        ItemNode(int index, Item<?> item, String typeName, String displayValue) {
            this.index = index;
            this.item = item;
            this.typeName = typeName;
            this.displayValue = displayValue;
        }

        @Override
        public String toString() {
            return "#" + index + ": " + displayValue;
        }
    }

    private static class ConstPoolTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            setBackgroundNonSelectionColor(JStudioTheme.getBgTertiary());
            setBackgroundSelectionColor(JStudioTheme.getSelection());
            setTextNonSelectionColor(JStudioTheme.getTextPrimary());
            setTextSelectionColor(JStudioTheme.getTextPrimary());

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof TypeNode) {
                    TypeNode typeNode = (TypeNode) userObj;
                    setForeground(getTypeColor(typeNode.typeName));
                    setIcon(null);
                } else if (userObj instanceof ItemNode) {
                    if (!selected) {
                        setForeground(JStudioTheme.getTextPrimary());
                    }
                    setIcon(null);
                }
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
                case "Double": return JStudioTheme.getBcConst();
                case "NameAndType": return JStudioTheme.getTextSecondary();
                default: return JStudioTheme.getTextPrimary();
            }
        }
    }
}
