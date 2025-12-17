package com.tonic.ui.navigator;

import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.Icons;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Base class for navigator tree nodes.
 */
public abstract class NavigatorNode extends DefaultMutableTreeNode {

    public NavigatorNode(Object userObject) {
        super(userObject);
    }

    public abstract String getDisplayText();

    public abstract Icon getIcon();

    public abstract String getTooltip();

    // === Concrete Node Types ===

    /**
     * Root node representing the project.
     */
    public static class ProjectNode extends NavigatorNode {
        private final String name;
        private final int classCount;

        public ProjectNode(String name, int classCount) {
            super(name);
            this.name = name;
            this.classCount = classCount;
        }

        @Override
        public String getDisplayText() {
            return name + " (" + classCount + " classes)";
        }

        @Override
        public Icon getIcon() {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip() {
            return name + " - " + classCount + " classes";
        }
    }

    /**
     * Node representing a package.
     */
    public static class PackageNode extends NavigatorNode {
        private final String packageName;
        private String displayName;

        public PackageNode(String packageName) {
            super(packageName);
            this.packageName = packageName;
            // Show last segment of package name
            int lastDot = packageName.lastIndexOf('.');
            this.displayName = lastDot >= 0 ? packageName.substring(lastDot + 1) : packageName;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayText() {
            return escapeHtml(displayName);
        }

        @Override
        public Icon getIcon() {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip() {
            return packageName;
        }

        private String escapeHtml(String text) {
            if (text == null) return "";
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '<': sb.append("&lt;"); break;
                    case '>': sb.append("&gt;"); break;
                    case '&': sb.append("&amp;"); break;
                    case '"': sb.append("&quot;"); break;
                    default: sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    /**
     * Node representing a class.
     */
    public static class ClassNode extends NavigatorNode {
        private final ClassEntryModel classEntry;

        public ClassNode(ClassEntryModel classEntry) {
            super(classEntry);
            this.classEntry = classEntry;
        }

        public ClassEntryModel getClassEntry() {
            return classEntry;
        }

        @Override
        public String getDisplayText() {
            return escapeHtml(classEntry.getSimpleName());
        }

        @Override
        public Icon getIcon() {
            return classEntry.getIcon();
        }

        @Override
        public String getTooltip() {
            return classEntry.getClassName();
        }

        private String escapeHtml(String text) {
            if (text == null) return "";
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '<': sb.append("&lt;"); break;
                    case '>': sb.append("&gt;"); break;
                    case '&': sb.append("&amp;"); break;
                    case '"': sb.append("&quot;"); break;
                    default: sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    /**
     * Node representing a method.
     */
    public static class MethodNode extends NavigatorNode {
        private final MethodEntryModel methodEntry;

        public MethodNode(MethodEntryModel methodEntry) {
            super(methodEntry);
            this.methodEntry = methodEntry;
        }

        public MethodEntryModel getMethodEntry() {
            return methodEntry;
        }

        @Override
        public String getDisplayText() {
            return methodEntry.getDisplaySignature();
        }

        @Override
        public Icon getIcon() {
            return methodEntry.getIcon();
        }

        @Override
        public String getTooltip() {
            return methodEntry.getName() + methodEntry.getDescriptor();
        }
    }

    /**
     * Node representing a field.
     */
    public static class FieldNode extends NavigatorNode {
        private final FieldEntryModel fieldEntry;

        public FieldNode(FieldEntryModel fieldEntry) {
            super(fieldEntry);
            this.fieldEntry = fieldEntry;
        }

        public FieldEntryModel getFieldEntry() {
            return fieldEntry;
        }

        @Override
        public String getDisplayText() {
            return fieldEntry.getName() + ": " + fieldEntry.getDisplayType();
        }

        @Override
        public Icon getIcon() {
            return fieldEntry.getIcon();
        }

        @Override
        public String getTooltip() {
            return fieldEntry.getName() + " : " + fieldEntry.getDescriptor();
        }
    }

    /**
     * Category node (e.g., "Fields", "Methods").
     */
    public static class CategoryNode extends NavigatorNode {
        private final String name;
        private final Icon icon;

        public CategoryNode(String name, Icon icon) {
            super(name);
            this.name = name;
            this.icon = icon;
        }

        @Override
        public String getDisplayText() {
            return name;
        }

        @Override
        public Icon getIcon() {
            return icon;
        }

        @Override
        public String getTooltip() {
            return name;
        }
    }
}
