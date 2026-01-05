package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.Statement;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

public abstract class ASTTreeNode extends DefaultMutableTreeNode {

    protected final ASTNode astNode;
    protected final String propertyName;

    public ASTTreeNode(ASTNode astNode) {
        this(astNode, null);
    }

    public ASTTreeNode(ASTNode astNode, String propertyName) {
        super(astNode);
        this.astNode = astNode;
        this.propertyName = propertyName;
    }

    protected void buildChildren() {
        List<ASTNode> children = astNode.getChildren();
        for (ASTNode child : children) {
            if (child != null) {
                add(createNodeFor(child, null));
            }
        }
    }

    public static ASTTreeNode createNodeFor(ASTNode node, String propertyName) {
        if (node instanceof Statement) {
            return new StatementTreeNode((Statement) node, propertyName);
        } else if (node instanceof Expression) {
            return new ExpressionTreeNode((Expression) node, propertyName);
        }
        return new GenericASTTreeNode(node, propertyName);
    }

    public ASTNode getAstNode() {
        return astNode;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public abstract String getNodeTypeName();

    public abstract String getNodeDetails();

    public abstract String getTypeAnnotation();

    public abstract Icon getIcon();

    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (propertyName != null) {
            sb.append(propertyName).append(": ");
        }
        sb.append(getNodeTypeName());
        String details = getNodeDetails();
        if (details != null && !details.isEmpty()) {
            sb.append("(").append(details).append(")");
        }
        String type = getTypeAnnotation();
        if (type != null && !type.isEmpty()) {
            sb.append(" : ").append(type);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getDisplayText();
    }

    protected static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    protected static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class GenericASTTreeNode extends ASTTreeNode {
        GenericASTTreeNode(ASTNode node, String propertyName) {
            super(node, propertyName);
            buildChildren();
        }

        @Override
        public String getNodeTypeName() {
            return astNode.getClass().getSimpleName();
        }

        @Override
        public String getNodeDetails() {
            return "";
        }

        @Override
        public String getTypeAnnotation() {
            return null;
        }

        @Override
        public Icon getIcon() {
            return null;
        }
    }
}
