package com.tonic.ui.browser;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.*;
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
import java.util.List;
import java.util.function.BiConsumer;

public class AttributesBrowserPanel extends ThemedJPanel {

    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel treeModel;

    private BiConsumer<Attribute, String> selectionListener;
    private ClassFile classFile;

    public AttributesBrowserPanel() {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        root = new DefaultMutableTreeNode("Attributes");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);

        tree.setBackground(JStudioTheme.getBgTertiary());
        tree.setForeground(JStudioTheme.getTextPrimary());
        tree.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        tree.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new AttributeTreeCellRenderer());

        tree.addTreeSelectionListener(e -> {
            if (selectionListener == null) return;
            TreePath path = e.getPath();
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) node).getUserObject();
                    if (userObj instanceof AttributeNode) {
                        AttributeNode attrNode = (AttributeNode) userObj;
                        selectionListener.accept(attrNode.attribute, attrNode.context);
                    }
                }
            }
        });

        add(tree, BorderLayout.CENTER);
    }

    public void loadClass(ClassFile classFile) {
        this.classFile = classFile;
        root.removeAllChildren();

        if (classFile == null) {
            treeModel.reload();
            return;
        }

        List<Attribute> classAttrs = classFile.getClassAttributes();
        if (classAttrs != null && !classAttrs.isEmpty()) {
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                    new CategoryNode("Class Attributes", classAttrs.size()));
            for (Attribute attr : classAttrs) {
                addAttributeNode(classNode, attr, "class");
            }
            root.add(classNode);
        }

        List<FieldEntry> fields = classFile.getFields();
        if (fields != null && !fields.isEmpty()) {
            for (FieldEntry field : fields) {
                List<Attribute> fieldAttrs = field.getAttributes();
                if (fieldAttrs != null && !fieldAttrs.isEmpty()) {
                    String fieldName = resolveUtf8(field.getNameIndex());
                    DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(
                            new CategoryNode("Field: " + fieldName, fieldAttrs.size()));
                    for (Attribute attr : fieldAttrs) {
                        addAttributeNode(fieldNode, attr, "field:" + fieldName);
                    }
                    root.add(fieldNode);
                }
            }
        }

        List<MethodEntry> methods = classFile.getMethods();
        if (methods != null && !methods.isEmpty()) {
            for (MethodEntry method : methods) {
                List<Attribute> methodAttrs = method.getAttributes();
                if (methodAttrs != null && !methodAttrs.isEmpty()) {
                    String methodName = resolveUtf8(method.getNameIndex());
                    String methodDesc = resolveUtf8(method.getDescIndex());
                    String shortDesc = shortenDescriptor(methodDesc);
                    DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(
                            new CategoryNode("Method: " + methodName + shortDesc, methodAttrs.size()));
                    for (Attribute attr : methodAttrs) {
                        addAttributeNode(methodNode, attr, "method:" + methodName + methodDesc);
                    }
                    root.add(methodNode);
                }
            }
        }

        treeModel.reload();

        for (int i = 0; i < Math.min(tree.getRowCount(), 10); i++) {
            tree.expandRow(i);
        }
    }

    private void addAttributeNode(DefaultMutableTreeNode parent, Attribute attr, String context) {
        String displayName = formatAttributeName(attr);
        AttributeNode attrNode = new AttributeNode(attr, displayName, context);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(attrNode);

        if (attr instanceof CodeAttribute) {
            CodeAttribute code = (CodeAttribute) attr;
            List<Attribute> nested = code.getAttributes();
            if (nested != null) {
                for (Attribute nestedAttr : nested) {
                    addAttributeNode(node, nestedAttr, context + " > Code");
                }
            }
        }

        parent.add(node);
    }

    private String formatAttributeName(Attribute attr) {
        String name = getAttributeTypeName(attr);
        String extra = "";

        if (attr instanceof CodeAttribute) {
            CodeAttribute code = (CodeAttribute) attr;
            extra = " (stack=" + code.getMaxStack() + ", locals=" + code.getMaxLocals() + ")";
        } else if (attr instanceof LineNumberTableAttribute) {
            LineNumberTableAttribute lnt = (LineNumberTableAttribute) attr;
            extra = " (" + lnt.getLineNumberTable().size() + " entries)";
        } else if (attr instanceof LocalVariableTableAttribute) {
            LocalVariableTableAttribute lvt = (LocalVariableTableAttribute) attr;
            extra = " (" + lvt.getLocalVariableTable().size() + " vars)";
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
        } else if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
            RuntimeVisibleAnnotationsAttribute rva = (RuntimeVisibleAnnotationsAttribute) attr;
            extra = " (" + rva.getAnnotations().size() + " annotations)";
        } else if (attr instanceof RuntimeInvisibleAnnotationsAttribute) {
            RuntimeInvisibleAnnotationsAttribute ria = (RuntimeInvisibleAnnotationsAttribute) attr;
            extra = " (" + ria.getAnnotations().size() + " annotations)";
        } else if (attr instanceof SourceFileAttribute) {
            SourceFileAttribute sf = (SourceFileAttribute) attr;
            extra = " → " + resolveUtf8(sf.getSourceFileIndex());
        } else if (attr instanceof SignatureAttribute) {
            SignatureAttribute sig = (SignatureAttribute) attr;
            String sigStr = resolveUtf8(sig.getSignatureIndex());
            if (sigStr.length() > 30) {
                sigStr = sigStr.substring(0, 27) + "...";
            }
            extra = " → " + sigStr;
        } else if (attr instanceof ConstantValueAttribute) {
            ConstantValueAttribute cv = (ConstantValueAttribute) attr;
            extra = " → #" + cv.getConstantValueIndex();
        } else if (attr instanceof MethodParametersAttribute) {
            MethodParametersAttribute mp = (MethodParametersAttribute) attr;
            extra = " (" + mp.getParameters().size() + " params)";
        } else if (attr instanceof NestMembersAttribute) {
            NestMembersAttribute nm = (NestMembersAttribute) attr;
            extra = " (" + nm.getClasses().size() + " members)";
        } else if (attr instanceof NestHostAttribute) {
            NestHostAttribute nh = (NestHostAttribute) attr;
            extra = " → #" + nh.getHostClassIndex();
        }

        return name + extra;
    }

    private String resolveUtf8(int index) {
        try {
            var item = classFile.getConstPool().getItem(index);
            if (item instanceof com.tonic.parser.constpool.Utf8Item) {
                return ((com.tonic.parser.constpool.Utf8Item) item).getValue();
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
        if (attr instanceof RuntimeVisibleAnnotationsAttribute) return "RuntimeVisibleAnnotations";
        if (attr instanceof RuntimeInvisibleAnnotationsAttribute) return "RuntimeInvisibleAnnotations";
        if (attr instanceof RuntimeVisibleParameterAnnotationsAttribute) return "RuntimeVisibleParameterAnnotations";
        if (attr instanceof AnnotationDefaultAttribute) return "AnnotationDefault";
        if (attr instanceof MethodParametersAttribute) return "MethodParameters";
        if (attr instanceof BootstrapMethodsAttribute) return "BootstrapMethods";
        if (attr instanceof ModuleAttribute) return "Module";
        if (attr instanceof NestHostAttribute) return "NestHost";
        if (attr instanceof NestMembersAttribute) return "NestMembers";
        return "Attribute";
    }

    public void setSelectionListener(BiConsumer<Attribute, String> listener) {
        this.selectionListener = listener;
    }

    static class CategoryNode {
        final String name;
        final int count;

        CategoryNode(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return name + " (" + count + ")";
        }
    }

    static class AttributeNode {
        final Attribute attribute;
        final String displayName;
        final String context;

        AttributeNode(Attribute attribute, String displayName, String context) {
            this.attribute = attribute;
            this.displayName = displayName;
            this.context = context;
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

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof CategoryNode) {
                    CategoryNode cat = (CategoryNode) userObj;
                    setForeground(getCategoryColor(cat.name));
                    setIcon(null);
                } else if (userObj instanceof AttributeNode) {
                    AttributeNode attrNode = (AttributeNode) userObj;
                    if (!selected) {
                        setForeground(getAttributeColor(attrNode.attribute));
                    }
                    setIcon(null);
                }
            }

            return this;
        }

        private Color getCategoryColor(String name) {
            if (name.startsWith("Class")) return JStudioTheme.getAccent();
            if (name.startsWith("Field")) return JStudioTheme.getInfo();
            if (name.startsWith("Method")) return JStudioTheme.getWarning();
            return JStudioTheme.getTextPrimary();
        }

        private Color getAttributeColor(Attribute attr) {
            if (attr instanceof CodeAttribute) return JStudioTheme.getAccentSecondary();
            if (attr instanceof LineNumberTableAttribute) return JStudioTheme.getTextSecondary();
            if (attr instanceof LocalVariableTableAttribute) return JStudioTheme.getTextSecondary();
            if (attr instanceof StackMapTableAttribute) return JStudioTheme.getTextSecondary();
            if (attr instanceof SignatureAttribute) return JStudioTheme.getAccent();
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) return JStudioTheme.getSuccess();
            if (attr instanceof RuntimeInvisibleAnnotationsAttribute) return JStudioTheme.getSuccess();
            if (attr instanceof ExceptionsAttribute) return JStudioTheme.getError();
            if (attr instanceof DeprecatedAttribute) return JStudioTheme.getTextDisabled();
            if (attr instanceof SyntheticAttribute) return JStudioTheme.getTextDisabled();
            return JStudioTheme.getTextPrimary();
        }
    }
}
