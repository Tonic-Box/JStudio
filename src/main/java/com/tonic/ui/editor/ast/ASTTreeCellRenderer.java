package com.tonic.ui.editor.ast;

import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.SyntaxColors;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;

public class ASTTreeCellRenderer extends DefaultTreeCellRenderer {

    public ASTTreeCellRenderer() {
        setOpaque(false);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        setBackgroundNonSelectionColor(JStudioTheme.getBgTertiary());
        setBackgroundSelectionColor(JStudioTheme.getSelection());
        setTextNonSelectionColor(JStudioTheme.getTextPrimary());
        setTextSelectionColor(JStudioTheme.getTextPrimary());
        setBorderSelectionColor(null);

        if (value instanceof ASTTreeNode) {
            ASTTreeNode node = (ASTTreeNode) value;
            setText(formatASTNode(node));
            Icon icon = node.getIcon();
            if (icon != null) {
                setIcon(icon);
            }
        } else if (value instanceof MethodRootNode) {
            MethodRootNode method = (MethodRootNode) value;
            setText(formatMethodNode(method));
            setIcon(method.getIcon());
        } else if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();
            if (userObject instanceof String) {
                setText(formatClassRoot((String) userObject));
                setIcon(Icons.getIcon("class", 14));
            }
        }

        return this;
    }

    private String formatASTNode(ASTTreeNode node) {
        StringBuilder html = new StringBuilder("<html>");

        String propName = node.getPropertyName();
        if (propName != null && !propName.isEmpty()) {
            html.append("<font color='").append(colorToHex(SyntaxColors.getIrValue())).append("'>");
            html.append(escapeHtml(propName));
            html.append("</font>: ");
        }

        html.append("<b><font color='").append(colorToHex(SyntaxColors.getJavaKeyword())).append("'>");
        html.append(escapeHtml(node.getNodeTypeName()));
        html.append("</font></b>");

        String details = node.getNodeDetails();
        if (details != null && !details.isEmpty()) {
            html.append("<font color='").append(colorToHex(SyntaxColors.getJavaString())).append("'>");
            html.append("(").append(escapeHtml(details)).append(")");
            html.append("</font>");
        }

        String type = node.getTypeAnnotation();
        if (type != null && !type.isEmpty()) {
            html.append(" <font color='").append(colorToHex(SyntaxColors.getJavaType())).append("'>");
            html.append(": ").append(escapeHtml(type));
            html.append("</font>");
        }

        html.append("</html>");
        return html.toString();
    }

    private String formatMethodNode(MethodRootNode method) {
        StringBuilder html = new StringBuilder("<html>");
        html.append("<b><font color='").append(colorToHex(JStudioTheme.getAccent())).append("'>");
        html.append(escapeHtml(method.getDisplayText()));
        html.append("</font></b>");

        if (!method.hasBody()) {
            html.append(" <font color='").append(colorToHex(JStudioTheme.getTextSecondary())).append("'>");
            html.append("(no body)");
            html.append("</font>");
        }

        html.append("</html>");
        return html.toString();
    }

    private String formatClassRoot(String className) {
        StringBuilder html = new StringBuilder("<html>");
        html.append("<b><font color='").append(colorToHex(SyntaxColors.getJavaType())).append("'>");
        html.append(escapeHtml(className));
        html.append("</font></b>");
        html.append("</html>");
        return html.toString();
    }

    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
