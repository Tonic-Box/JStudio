package com.tonic.ui.navigator;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.simulation.metrics.ComplexityMetrics;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.RunnableOverlayIcon;
import lombok.Getter;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

/** A node of the class navigator tree, which supplies its own label, icon and tooltip. */
public abstract class NavigatorNode extends DefaultMutableTreeNode
{

    /**
     * Creates a node.
     *
     * @param userObject the model object the node stands for
     */
    public NavigatorNode(Object userObject)
    {
        super(userObject);
    }

    /**
     * Gives the tree label.
     *
     * @return the label text
     */
    public abstract String getDisplayText();

    /**
     * Gives the tree icon.
     *
     * @return the icon
     */
    public abstract Icon getIcon();

    /**
     * Gives the hover text.
     *
     * @return the tooltip text
     */
    public abstract String getTooltip();

    @Override
    public String toString()
    {
        return getDisplayText();
    }

    protected static String sanitizeDisplayText(String text)
    {
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

    /** The root node: the project name and its class count. */
    public static class ProjectNode extends NavigatorNode
    {
        private final String name;
        private final int classCount;

        /**
         * Creates the root.
         *
         * @param name the project name
         * @param classCount the number of classes, shown in the label
         */
        public ProjectNode(String name, int classCount)
        {
            super(name);
            this.name = name;
            this.classCount = classCount;
        }

        @Override
        public String getDisplayText()
        {
            return name + " (" + classCount + " classes)";
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip()
        {
            return name + " - " + classCount + " classes";
        }
    }

    /** A package node, labelled by its last segment unless given another display name. */
    public static class PackageNode extends NavigatorNode
    {
        @Getter
        private final String packageName;
        private String displayName;

        /**
         * Creates a package node.
         *
         * @param packageName the dotted package name
         */
        public PackageNode(String packageName)
        {
            super(packageName);
            this.packageName = packageName;
            int lastDot = packageName.lastIndexOf('.');
            this.displayName = lastDot >= 0 ? packageName.substring(lastDot + 1) : packageName;
        }

        /**
         * Replaces the label, for example when single-child packages are compacted into one node.
         *
         * @param displayName the label text
         */
        public void setDisplayName(String displayName)
        {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayText()
        {
            return sanitizeDisplayText(displayName);
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip()
        {
            return sanitizeDisplayText(packageName);
        }
    }

    /** A class node; its icon carries a run overlay when the class has a main method. */
    @Getter
    public static class ClassNode extends NavigatorNode
    {
        private final ClassEntryModel classEntry;

        /**
         * Creates a class node.
         *
         * @param classEntry the class
         */
        public ClassNode(ClassEntryModel classEntry)
        {
            super(classEntry);
            this.classEntry = classEntry;
        }

        @Override
        public String getDisplayText()
        {
            return sanitizeDisplayText(classEntry.getSimpleName());
        }

        @Override
        public Icon getIcon()
        {
            Icon icon = Icons.getIcon(classEntry.getIconKey());
            return classEntry.hasMainMethod()
                    ? new RunnableOverlayIcon(icon) : icon;
        }

        @Override
        public String getTooltip()
        {
            return sanitizeDisplayText(classEntry.getClassName());
        }
    }

    /** A method node; its tooltip adds the complexity summary when metrics exist. */
    @Getter
    public static class MethodNode extends NavigatorNode
    {
        private final MethodEntryModel methodEntry;

        /**
         * Creates a method node.
         *
         * @param methodEntry the method
         */
        public MethodNode(MethodEntryModel methodEntry)
        {
            super(methodEntry);
            this.methodEntry = methodEntry;
        }

        @Override
        public String getDisplayText()
        {
            return sanitizeDisplayText(methodEntry.getDisplaySignature());
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon(methodEntry.getIconKey());
        }

        @Override
        public String getTooltip()
        {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append(sanitizeDisplayText(methodEntry.getName() + methodEntry.getDescriptor()));
            ComplexityMetrics metrics = methodEntry.getComplexityMetrics();
            if (metrics != null)
            {
                tooltip.append("<br><i>").append(metrics.getSummary()).append("</i>");
                return "<html>" + tooltip + "</html>";
            }
            return tooltip.toString();
        }
    }

    /** A field node, labelled name: type. */
    @Getter
    public static class FieldNode extends NavigatorNode
    {
        private final FieldEntryModel fieldEntry;

        /**
         * Creates a field node.
         *
         * @param fieldEntry the field
         */
        public FieldNode(FieldEntryModel fieldEntry)
        {
            super(fieldEntry);
            this.fieldEntry = fieldEntry;
        }

        @Override
        public String getDisplayText()
        {
            return sanitizeDisplayText(fieldEntry.getName() + ": " + fieldEntry.getDisplayType());
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon(fieldEntry.getIconKey());
        }

        @Override
        public String getTooltip()
        {
            return sanitizeDisplayText(fieldEntry.getName() + " : " + fieldEntry.getDescriptor());
        }
    }

    /** A grouping node such as Fields or Methods. */
    public static class CategoryNode extends NavigatorNode
    {
        private final String name;
        private final Icon icon;

        /**
         * Creates a grouping node.
         *
         * @param name the label
         * @param icon the icon
         */
        public CategoryNode(String name, Icon icon)
        {
            super(name);
            this.name = name;
            this.icon = icon;
        }

        @Override
        public String getDisplayText()
        {
            return name;
        }

        @Override
        public Icon getIcon()
        {
            return icon;
        }

        @Override
        public String getTooltip()
        {
            return name;
        }
    }

    /** The root of the non-class resources, labelled with their count. */
    @Getter
    public static class ResourcesRootNode extends NavigatorNode
    {
        private final int resourceCount;

        /**
         * Creates the resources root.
         *
         * @param resourceCount the number of resource files
         */
        public ResourcesRootNode(int resourceCount)
        {
            super("Resources");
            this.resourceCount = resourceCount;
        }

        @Override
        public String getDisplayText()
        {
            return "Resources (" + resourceCount + ")";
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon("resource");
        }

        @Override
        public String getTooltip()
        {
            return resourceCount + " resource files";
        }
    }

    /** A resource folder node, labelled by its last path segment. */
    @Getter
    public static class ResourceFolderNode extends NavigatorNode
    {
        private final String folderPath;
        private final String folderName;

        /**
         * Creates a folder node.
         *
         * @param folderPath the folder's path in the archive, with slashes
         */
        public ResourceFolderNode(String folderPath)
        {
            super(folderPath);
            this.folderPath = folderPath;
            int lastSlash = folderPath.lastIndexOf('/');
            this.folderName = lastSlash >= 0 ? folderPath.substring(lastSlash + 1) : folderPath;
        }

        @Override
        public String getDisplayText()
        {
            return sanitizeDisplayText(folderName);
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon("package");
        }

        @Override
        public String getTooltip()
        {
            return sanitizeDisplayText(folderPath);
        }
    }

    /** A resource file node; its tooltip shows the path and size. */
    @Getter
    public static class ResourceNode extends NavigatorNode
    {
        private final ResourceEntryModel resource;

        /**
         * Creates a resource node.
         *
         * @param resource the resource file
         */
        public ResourceNode(ResourceEntryModel resource)
        {
            super(resource);
            this.resource = resource;
        }

        @Override
        public String getDisplayText()
        {
            return sanitizeDisplayText(resource.getName());
        }

        @Override
        public Icon getIcon()
        {
            return Icons.getIcon(resource.getIconKey());
        }

        @Override
        public String getTooltip()
        {
            return sanitizeDisplayText(resource.getPath() + " (" + resource.getFormattedSize() + ")");
        }
    }
}
