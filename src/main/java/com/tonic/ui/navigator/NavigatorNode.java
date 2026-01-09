package com.tonic.ui.navigator;

import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ResourceEntryModel;
import com.tonic.ui.simulation.metrics.ComplexityMetrics;
import com.tonic.ui.theme.Icons;
import lombok.Getter;

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

    @Override
    public String toString() {
        return getDisplayText();
    }

    protected static String sanitizeDisplayText(String text) {
        if (text == null) return "";
        String sanitized = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        sanitized = sanitized
                .replace("https://", "https\u200B://")
                .replace("http://", "http\u200B://")
                .replace("ftp://", "ftp\u200B://")
                .replace("file://", "file\u200B://");
        return sanitized;
    }

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
        @Getter
        private final String packageName;
        private String displayName;

        public PackageNode(String packageName) {
            super(packageName);
            this.packageName = packageName;
            // Show last segment of package name
            int lastDot = packageName.lastIndexOf('.');
            this.displayName = lastDot >= 0 ? packageName.substring(lastDot + 1) : packageName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayText() {
            return sanitizeDisplayText(displayName);
        }

        @Override
        public Icon getIcon() {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip() {
            return sanitizeDisplayText(packageName);
        }
    }

    /**
     * Node representing a class.
     */
    @Getter
    public static class ClassNode extends NavigatorNode {
        private final ClassEntryModel classEntry;

        public ClassNode(ClassEntryModel classEntry) {
            super(classEntry);
            this.classEntry = classEntry;
        }

        @Override
        public String getDisplayText() {
            return sanitizeDisplayText(classEntry.getSimpleName());
        }

        @Override
        public Icon getIcon() {
            return classEntry.getIcon();
        }

        @Override
        public String getTooltip() {
            return sanitizeDisplayText(classEntry.getClassName());
        }
    }

    /**
     * Node representing a method.
     */
    @Getter
    public static class MethodNode extends NavigatorNode {
        private final MethodEntryModel methodEntry;

        public MethodNode(MethodEntryModel methodEntry) {
            super(methodEntry);
            this.methodEntry = methodEntry;
        }

        @Override
        public String getDisplayText() {
            return sanitizeDisplayText(methodEntry.getDisplaySignature());
        }

        @Override
        public Icon getIcon() {
            return methodEntry.getIcon();
        }

        @Override
        public String getTooltip() {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append(sanitizeDisplayText(methodEntry.getName() + methodEntry.getDescriptor()));
            ComplexityMetrics metrics = methodEntry.getComplexityMetrics();
            if (metrics != null) {
                tooltip.append("<br><i>").append(metrics.getSummary()).append("</i>");
                return "<html>" + tooltip + "</html>";
            }
            return tooltip.toString();
        }
    }

    /**
     * Node representing a field.
     */
    @Getter
    public static class FieldNode extends NavigatorNode {
        private final FieldEntryModel fieldEntry;

        public FieldNode(FieldEntryModel fieldEntry) {
            super(fieldEntry);
            this.fieldEntry = fieldEntry;
        }

        @Override
        public String getDisplayText() {
            return sanitizeDisplayText(fieldEntry.getName() + ": " + fieldEntry.getDisplayType());
        }

        @Override
        public Icon getIcon() {
            return fieldEntry.getIcon();
        }

        @Override
        public String getTooltip() {
            return sanitizeDisplayText(fieldEntry.getName() + " : " + fieldEntry.getDescriptor());
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

    @Getter
    public static class ResourcesRootNode extends NavigatorNode {
        private final int resourceCount;

        public ResourcesRootNode(int resourceCount) {
            super("Resources");
            this.resourceCount = resourceCount;
        }

        @Override
        public String getDisplayText() {
            return "Resources (" + resourceCount + ")";
        }

        @Override
        public Icon getIcon() {
            return Icons.getIcon("resource");
        }

        @Override
        public String getTooltip() {
            return resourceCount + " resource files";
        }
    }

    @Getter
    public static class ResourceFolderNode extends NavigatorNode {
        private final String folderPath;
        private final String folderName;

        public ResourceFolderNode(String folderPath) {
            super(folderPath);
            this.folderPath = folderPath;
            int lastSlash = folderPath.lastIndexOf('/');
            this.folderName = lastSlash >= 0 ? folderPath.substring(lastSlash + 1) : folderPath;
        }

        @Override
        public String getDisplayText() {
            return sanitizeDisplayText(folderName);
        }

        @Override
        public Icon getIcon() {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip() {
            return sanitizeDisplayText(folderPath);
        }
    }

    @Getter
    public static class ResourceNode extends NavigatorNode {
        private final ResourceEntryModel resource;

        public ResourceNode(ResourceEntryModel resource) {
            super(resource);
            this.resource = resource;
        }

        @Override
        public String getDisplayText() {
            return sanitizeDisplayText(resource.getName());
        }

        @Override
        public Icon getIcon() {
            return resource.getIcon();
        }

        @Override
        public String getTooltip() {
            return sanitizeDisplayText(resource.getPath() + " (" + resource.getFormattedSize() + ")");
        }
    }
}
