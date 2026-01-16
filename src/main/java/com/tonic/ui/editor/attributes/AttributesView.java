package com.tonic.ui.editor.attributes;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.*;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Getter;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Enumeration;
import java.util.List;

public class AttributesView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel treeModel;
    private final JScrollPane scrollPane;
    private final LoadingOverlay loadingOverlay;

    @Getter
    private boolean loaded = false;
    private SwingWorker<Void, Void> currentWorker;
    private String lastSearch;

    public AttributesView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        root = new DefaultMutableTreeNode("Attributes");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);

        tree.setBackground(JStudioTheme.getBgTertiary());
        tree.setForeground(JStudioTheme.getTextPrimary());
        tree.setFont(JStudioTheme.getCodeFont(12));
        tree.setRowHeight(22);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new AttributeTreeCellRenderer());

        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        loadingOverlay = new LoadingOverlay();

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new OverlayLayout(contentPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);
        contentPanel.add(loadingOverlay);
        contentPanel.add(scrollPane);

        add(contentPanel, BorderLayout.CENTER);

        applyTheme();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    public void refresh() {
        cancelCurrentWorker();
        loadingOverlay.showLoading("Loading attributes...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) return;
                try {
                    get();
                    loadAttributes();
                    loaded = true;
                } catch (Exception e) {
                    root.removeAllChildren();
                    root.add(new DefaultMutableTreeNode("Error loading attributes: " + e.getMessage()));
                    treeModel.reload();
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

    private void loadAttributes() {
        root.removeAllChildren();

        ClassFile cf = classEntry.getClassFile();
        if (cf == null) {
            treeModel.reload();
            return;
        }

        List<Attribute> classAttrs = cf.getClassAttributes();
        if (classAttrs != null && !classAttrs.isEmpty()) {
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                    new CategoryNode("Class Attributes", classAttrs.size(), CategoryType.CLASS));
            for (Attribute attr : classAttrs) {
                addAttributeNode(classNode, attr, cf);
            }
            root.add(classNode);
        }

        List<FieldEntry> fields = cf.getFields();
        if (fields != null && !fields.isEmpty()) {
            for (FieldEntry field : fields) {
                List<Attribute> fieldAttrs = field.getAttributes();
                if (fieldAttrs != null && !fieldAttrs.isEmpty()) {
                    String fieldName = resolveUtf8(cf, field.getNameIndex());
                    String fieldDesc = resolveUtf8(cf, field.getDescIndex());
                    DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(
                            new CategoryNode("Field: " + fieldName + " : " + shortenType(fieldDesc),
                                    fieldAttrs.size(), CategoryType.FIELD));
                    for (Attribute attr : fieldAttrs) {
                        addAttributeNode(fieldNode, attr, cf);
                    }
                    root.add(fieldNode);
                }
            }
        }

        List<MethodEntry> methods = cf.getMethods();
        if (methods != null && !methods.isEmpty()) {
            for (MethodEntry method : methods) {
                List<Attribute> methodAttrs = method.getAttributes();
                if (methodAttrs != null && !methodAttrs.isEmpty()) {
                    String methodName = resolveUtf8(cf, method.getNameIndex());
                    String methodDesc = resolveUtf8(cf, method.getDescIndex());
                    DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(
                            new CategoryNode("Method: " + methodName + shortenDescriptor(methodDesc),
                                    methodAttrs.size(), CategoryType.METHOD));
                    for (Attribute attr : methodAttrs) {
                        addAttributeNode(methodNode, attr, cf);
                    }
                    root.add(methodNode);
                }
            }
        }

        treeModel.reload();
    }

    private void addAttributeNode(DefaultMutableTreeNode parent, Attribute attr, ClassFile cf) {
        String displayName = formatAttributeName(attr, cf);
        AttributeNode attrNode = new AttributeNode(attr, displayName, getAttributeType(attr));
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(attrNode);

        if (attr instanceof CodeAttribute) {
            CodeAttribute code = (CodeAttribute) attr;
            List<Attribute> nested = code.getAttributes();
            if (nested != null) {
                for (Attribute nestedAttr : nested) {
                    addAttributeNode(node, nestedAttr, cf);
                }
            }
        }

        parent.add(node);
    }

    private String formatAttributeName(Attribute attr, ClassFile cf) {
        String name = getAttributeTypeName(attr);
        String extra = "";

        if (attr instanceof CodeAttribute) {
            CodeAttribute code = (CodeAttribute) attr;
            extra = String.format(" (stack=%d, locals=%d, code=%d bytes)",
                    code.getMaxStack(), code.getMaxLocals(), code.getCode().length);
        } else if (attr instanceof LineNumberTableAttribute) {
            LineNumberTableAttribute lnt = (LineNumberTableAttribute) attr;
            extra = " (" + lnt.getLineNumberTable().size() + " entries)";
        } else if (attr instanceof LocalVariableTableAttribute) {
            LocalVariableTableAttribute lvt = (LocalVariableTableAttribute) attr;
            extra = " (" + lvt.getLocalVariableTable().size() + " variables)";
        } else if (attr instanceof LocalVariableTypeTableAttribute) {
            LocalVariableTypeTableAttribute lvtt = (LocalVariableTypeTableAttribute) attr;
            extra = " (" + lvtt.getLocalVariableTypeTable().size() + " entries)";
        } else if (attr instanceof ExceptionsAttribute) {
            ExceptionsAttribute ex = (ExceptionsAttribute) attr;
            extra = " (" + ex.getExceptionIndexTable().size() + " throws)";
        } else if (attr instanceof InnerClassesAttribute) {
            InnerClassesAttribute ic = (InnerClassesAttribute) attr;
            extra = " (" + ic.getClasses().size() + " classes)";
        } else if (attr instanceof StackMapTableAttribute) {
            StackMapTableAttribute smt = (StackMapTableAttribute) attr;
            extra = " (" + smt.getFrames().size() + " frames)";
        } else if (attr instanceof BootstrapMethodsAttribute) {
            BootstrapMethodsAttribute bsm = (BootstrapMethodsAttribute) attr;
            extra = " (" + bsm.getBootstrapMethods().size() + " methods)";
        } else if (attr instanceof RuntimeInvisibleAnnotationsAttribute) {
            RuntimeInvisibleAnnotationsAttribute ria = (RuntimeInvisibleAnnotationsAttribute) attr;
            extra = " (" + ria.getAnnotations().size() + " annotations)";
        } else if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
            RuntimeVisibleAnnotationsAttribute rva = (RuntimeVisibleAnnotationsAttribute) attr;
            extra = " (" + rva.getAnnotations().size() + " annotations)";
        } else if (attr instanceof SourceFileAttribute) {
            SourceFileAttribute sf = (SourceFileAttribute) attr;
            extra = " -> " + resolveUtf8(cf, sf.getSourceFileIndex());
        } else if (attr instanceof SignatureAttribute) {
            SignatureAttribute sig = (SignatureAttribute) attr;
            String sigStr = resolveUtf8(cf, sig.getSignatureIndex());
            if (sigStr.length() > 40) {
                sigStr = sigStr.substring(0, 37) + "...";
            }
            extra = " -> " + sigStr;
        } else if (attr instanceof ConstantValueAttribute) {
            ConstantValueAttribute cv = (ConstantValueAttribute) attr;
            extra = " -> #" + cv.getConstantValueIndex();
        } else if (attr instanceof MethodParametersAttribute) {
            MethodParametersAttribute mp = (MethodParametersAttribute) attr;
            extra = " (" + mp.getParameters().size() + " parameters)";
        } else if (attr instanceof NestMembersAttribute) {
            NestMembersAttribute nm = (NestMembersAttribute) attr;
            extra = " (" + nm.getClasses().size() + " members)";
        } else if (attr instanceof NestHostAttribute) {
            NestHostAttribute nh = (NestHostAttribute) attr;
            extra = " -> #" + nh.getHostClassIndex();
        }

        return name + extra;
    }

    private String resolveUtf8(ClassFile cf, int index) {
        try {
            var item = cf.getConstPool().getItem(index);
            if (item instanceof Utf8Item) {
                return ((Utf8Item) item).getValue();
            }
        } catch (Exception e) {
            // ignore
        }
        return "#" + index;
    }

    private String shortenDescriptor(String desc) {
        if (desc == null) return "";
        int paren = desc.indexOf(')');
        if (paren > 0) {
            return desc.substring(0, paren + 1);
        }
        return desc;
    }

    private String shortenType(String desc) {
        if (desc == null) return "";
        switch (desc) {
            case "Z": return "boolean";
            case "B": return "byte";
            case "C": return "char";
            case "S": return "short";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            case "V": return "void";
            default:
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    String className = desc.substring(1, desc.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                } else if (desc.startsWith("[")) {
                    return shortenType(desc.substring(1)) + "[]";
                }
                return desc;
        }
    }

    private String getAttributeTypeName(Attribute attr) {
        if (attr instanceof CodeAttribute) return "Code";
        if (attr instanceof ConstantValueAttribute) return "ConstantValue";
        if (attr instanceof StackMapTableAttribute) return "StackMapTable";
        if (attr instanceof ExceptionsAttribute) return "Exceptions";
        if (attr instanceof InnerClassesAttribute) return "InnerClasses";
        if (attr instanceof EnclosingMethodAttribute) return "EnclosingMethod";
        if (attr instanceof SyntheticAttribute) return "Synthetic";
        if (attr instanceof SignatureAttribute) return "Signature";
        if (attr instanceof SourceFileAttribute) return "SourceFile";
        if (attr instanceof SourceDebugExtensionAttribute) return "SourceDebugExtension";
        if (attr instanceof LineNumberTableAttribute) return "LineNumberTable";
        if (attr instanceof LocalVariableTableAttribute) return "LocalVariableTable";
        if (attr instanceof LocalVariableTypeTableAttribute) return "LocalVariableTypeTable";
        if (attr instanceof DeprecatedAttribute) return "Deprecated";
        if (attr instanceof RuntimeInvisibleAnnotationsAttribute) return "RuntimeInvisibleAnnotations";
        if (attr instanceof RuntimeVisibleAnnotationsAttribute) return "RuntimeVisibleAnnotations";
        if (attr instanceof RuntimeVisibleParameterAnnotationsAttribute) return "RuntimeVisibleParameterAnnotations";
        if (attr instanceof AnnotationDefaultAttribute) return "AnnotationDefault";
        if (attr instanceof MethodParametersAttribute) return "MethodParameters";
        if (attr instanceof BootstrapMethodsAttribute) return "BootstrapMethods";
        if (attr instanceof ModuleAttribute) return "Module";
        if (attr instanceof NestHostAttribute) return "NestHost";
        if (attr instanceof NestMembersAttribute) return "NestMembers";
        return "Attribute";
    }

    private AttributeType getAttributeType(Attribute attr) {
        if (attr instanceof CodeAttribute) return AttributeType.CODE;
        if (attr instanceof LineNumberTableAttribute) return AttributeType.DEBUG;
        if (attr instanceof LocalVariableTableAttribute) return AttributeType.DEBUG;
        if (attr instanceof LocalVariableTypeTableAttribute) return AttributeType.DEBUG;
        if (attr instanceof StackMapTableAttribute) return AttributeType.DEBUG;
        if (attr instanceof SignatureAttribute) return AttributeType.SIGNATURE;
        if (attr instanceof RuntimeInvisibleAnnotationsAttribute) return AttributeType.ANNOTATION;
        if (attr instanceof RuntimeVisibleAnnotationsAttribute) return AttributeType.ANNOTATION;
        if (attr instanceof RuntimeVisibleParameterAnnotationsAttribute) return AttributeType.ANNOTATION;
        if (attr instanceof AnnotationDefaultAttribute) return AttributeType.ANNOTATION;
        if (attr instanceof ExceptionsAttribute) return AttributeType.EXCEPTION;
        if (attr instanceof DeprecatedAttribute) return AttributeType.DEPRECATED;
        if (attr instanceof SyntheticAttribute) return AttributeType.SYNTHETIC;
        if (attr instanceof SourceFileAttribute) return AttributeType.SOURCE;
        if (attr instanceof InnerClassesAttribute) return AttributeType.STRUCTURE;
        if (attr instanceof EnclosingMethodAttribute) return AttributeType.STRUCTURE;
        if (attr instanceof NestHostAttribute) return AttributeType.STRUCTURE;
        if (attr instanceof NestMembersAttribute) return AttributeType.STRUCTURE;
        if (attr instanceof BootstrapMethodsAttribute) return AttributeType.INVOKEDYNAMIC;
        return AttributeType.OTHER;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        tree.setBackground(JStudioTheme.getBgTertiary());
        tree.setForeground(JStudioTheme.getTextPrimary());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        repaint();
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Attributes for: ").append(classEntry.getClassName()).append("\n\n");
        appendNodeText(root, sb, 0);
        return sb.toString();
    }

    private void appendNodeText(DefaultMutableTreeNode node, StringBuilder sb, int indent) {
        String prefix = "  ".repeat(indent);
        Object userObj = node.getUserObject();

        if (userObj instanceof CategoryNode) {
            sb.append(prefix).append(((CategoryNode) userObj).name).append("\n");
        } else if (userObj instanceof AttributeNode) {
            sb.append(prefix).append("- ").append(((AttributeNode) userObj).displayName).append("\n");
        } else if (userObj instanceof String && !userObj.equals("Attributes")) {
            sb.append(prefix).append(userObj).append("\n");
        }

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            appendNodeText((DefaultMutableTreeNode) children.nextElement(), sb, indent + 1);
        }
    }

    public void copySelection() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) return;

        StringBuilder sb = new StringBuilder();
        for (TreePath path : paths) {
            Object node = path.getLastPathComponent();
            if (node instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) node).getUserObject();
                sb.append(userObj.toString()).append("\n");
            }
        }

        if (sb.length() > 0) {
            StringSelection selection = new StringSelection(sb.toString().trim());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public String getSelectedText() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
            return ((DefaultMutableTreeNode) node).getUserObject().toString();
        }
        return null;
    }

    public void goToLine(int line) {
        if (line >= 0 && line < tree.getRowCount()) {
            tree.setSelectionRow(line);
            tree.scrollRowToVisible(line);
        }
    }

    public void showFindDialog() {
        String input = (String) JOptionPane.showInputDialog(
                this, "Search:", "Find Attribute",
                JOptionPane.PLAIN_MESSAGE, null, null, lastSearch);
        lastSearch = input;
        if (input != null && !input.isEmpty()) {
            scrollToText(input);
        }
    }

    public void scrollToText(String text) {
        if (text == null || text.isEmpty()) return;

        String lowerText = text.toLowerCase();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath path = tree.getPathForRow(i);
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) node).getUserObject();
                    if (userObj.toString().toLowerCase().contains(lowerText)) {
                        tree.setSelectionPath(path);
                        tree.scrollPathToVisible(path);
                        return;
                    }
                }
            }
        }
    }

    public void setFontSize(int size) {
        tree.setFont(JStudioTheme.getCodeFont(size));
        tree.setRowHeight(size + 10);
    }

    public void setWordWrap(boolean enabled) {
        // Tree doesn't support word wrap
    }

    enum CategoryType {
        CLASS, FIELD, METHOD
    }

    enum AttributeType {
        CODE, DEBUG, SIGNATURE, ANNOTATION, EXCEPTION, DEPRECATED, SYNTHETIC, SOURCE, STRUCTURE, INVOKEDYNAMIC, OTHER
    }

    static class CategoryNode {
        final String name;
        final int count;
        final CategoryType type;

        CategoryNode(String name, int count, CategoryType type) {
            this.name = name;
            this.count = count;
            this.type = type;
        }

        @Override
        public String toString() {
            return name + " (" + count + ")";
        }
    }

    static class AttributeNode {
        final Attribute attribute;
        final String displayName;
        final AttributeType type;

        AttributeNode(Attribute attribute, String displayName, AttributeType type) {
            this.attribute = attribute;
            this.displayName = displayName;
            this.type = type;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static class AttributeTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            setBackgroundNonSelectionColor(JStudioTheme.getBgTertiary());
            setBackgroundSelectionColor(JStudioTheme.getSelection());
            setTextNonSelectionColor(JStudioTheme.getTextPrimary());
            setTextSelectionColor(JStudioTheme.getTextPrimary());
            setBorderSelectionColor(JStudioTheme.getAccent());

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();

                if (userObj instanceof CategoryNode) {
                    CategoryNode cat = (CategoryNode) userObj;
                    setFont(getFont().deriveFont(Font.BOLD));
                    if (!selected) {
                        setForeground(getCategoryColor(cat.type));
                    }
                    setIcon(null);
                } else if (userObj instanceof AttributeNode) {
                    AttributeNode attrNode = (AttributeNode) userObj;
                    setFont(getFont().deriveFont(Font.PLAIN));
                    if (!selected) {
                        setForeground(getAttributeColor(attrNode.type));
                    }
                    setIcon(null);
                }
            }

            return this;
        }

        private Color getCategoryColor(CategoryType type) {
            switch (type) {
                case CLASS: return JStudioTheme.getAccent();
                case FIELD: return JStudioTheme.getInfo();
                case METHOD: return JStudioTheme.getWarning();
                default: return JStudioTheme.getTextPrimary();
            }
        }

        private Color getAttributeColor(AttributeType type) {
            switch (type) {
                case CODE: return JStudioTheme.getAccentSecondary();
                case DEBUG: return JStudioTheme.getTextSecondary();
                case SIGNATURE: return JStudioTheme.getAccent();
                case ANNOTATION: return JStudioTheme.getSuccess();
                case EXCEPTION: return JStudioTheme.getError();
                case DEPRECATED:
                case SYNTHETIC: return JStudioTheme.getTextDisabled();
                case SOURCE: return JStudioTheme.getInfo();
                case STRUCTURE: return JStudioTheme.getWarning();
                case INVOKEDYNAMIC: return JStudioTheme.getAccentSecondary();
                case OTHER:
                default: return JStudioTheme.getTextPrimary();
            }
        }
    }
}
