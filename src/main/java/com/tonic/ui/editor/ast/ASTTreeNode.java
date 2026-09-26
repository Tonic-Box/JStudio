package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.Statement;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

/** A tree node wrapping one AST node, optionally labelled with the property of its parent it fills. */
public abstract class ASTTreeNode extends DefaultMutableTreeNode
{

    protected final ASTNode astNode;
    protected final String propertyName;

    /**
     * Creates an unlabelled node.
     *
     * @param astNode the wrapped AST node
     */
    public ASTTreeNode(ASTNode astNode)
    {
        this(astNode, null);
    }

    /**
     * Creates a node labelled with the parent property it fills.
     *
     * @param astNode the wrapped AST node
     * @param propertyName the parent property name, or null for none
     */
    public ASTTreeNode(ASTNode astNode, String propertyName)
    {
        super(astNode);
        this.astNode = astNode;
        this.propertyName = propertyName;
    }

    protected void buildChildren()
    {
        List<ASTNode> children = astNode.getChildren();
        for (ASTNode child : children)
        {
            if (child != null)
            {
                add(createNodeFor(child, null));
            }
        }
    }

    /**
     * Wraps an AST node in the node class for its kind: statement, expression, or a generic node for anything else.
     *
     * @param node the AST node to wrap
     * @param propertyName the parent property name, or null for none
     * @return the new tree node, with its children built
     */
    public static ASTTreeNode createNodeFor(ASTNode node, String propertyName)
    {
        if (node instanceof Statement)
        {
            return new StatementTreeNode((Statement) node, propertyName);
        }
        else if (node instanceof Expression)
        {
            return new ExpressionTreeNode((Expression) node, propertyName);
        }
        return new GenericASTTreeNode(node, propertyName);
    }

    /** @return the wrapped AST node */
    public ASTNode getAstNode()
    {
        return astNode;
    }

    /** @return the parent property name, or null when unlabelled */
    public String getPropertyName()
    {
        return propertyName;
    }

    /**
     * Names the node's kind.
     *
     * @return the kind, shown first in the label
     */
    public abstract String getNodeTypeName();

    /**
     * Describes the node briefly.
     *
     * @return the description shown in parentheses, or null or empty for none
     */
    public abstract String getNodeDetails();

    /**
     * Names the node's type, if it has one.
     *
     * @return the type shown after a colon, or null or empty for none
     */
    public abstract String getTypeAnnotation();

    /**
     * Picks the node's icon.
     *
     * @return the icon, or null for none
     */
    public abstract Icon getIcon();

    /**
     * Builds the label: property name, kind, details in parentheses and type annotation, each only when present.
     *
     * @return the label text
     */
    public String getDisplayText()
    {
        StringBuilder sb = new StringBuilder();
        if (propertyName != null)
        {
            sb.append(propertyName).append(": ");
        }
        sb.append(getNodeTypeName());
        String details = getNodeDetails();
        if (details != null && !details.isEmpty())
        {
            sb.append("(").append(details).append(")");
        }
        String type = getTypeAnnotation();
        if (type != null && !type.isEmpty())
        {
            sb.append(" : ").append(type);
        }
        return sb.toString();
    }

    @Override
    public String toString()
    {
        return getDisplayText();
    }

    protected static String truncate(String s, int max)
    {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    protected static String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class GenericASTTreeNode extends ASTTreeNode
    {
        GenericASTTreeNode(ASTNode node, String propertyName)
        {
            super(node, propertyName);
            buildChildren();
        }

        @Override
        public String getNodeTypeName()
        {
            return astNode.getClass().getSimpleName();
        }

        @Override
        public String getNodeDetails()
        {
            return "";
        }

        @Override
        public String getTypeAnnotation()
        {
            return null;
        }

        @Override
        public Icon getIcon()
        {
            return null;
        }
    }
}
