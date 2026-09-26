package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.parser.MethodEntry;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

/** The tree model behind the AST view: one child per method under a root named for the class. */
public class ASTTreeModel extends DefaultTreeModel
{

    private final DefaultMutableTreeNode rootNode;

    /** Creates an empty model with a root labelled AST. */
    public ASTTreeModel()
    {
        super(new DefaultMutableTreeNode("AST"));
        this.rootNode = (DefaultMutableTreeNode) getRoot();
    }

    /**
     * Replaces the tree with one node per method and reloads it.
     *
     * @param className the label for the root
     * @param methods the methods to show, in order
     */
    public void loadClass(String className, List<MethodASTEntry> methods)
    {
        rootNode.removeAllChildren();
        rootNode.setUserObject(className);

        for (MethodASTEntry entry : methods)
        {
            MethodRootNode methodNode = new MethodRootNode(entry.method(), entry.body());
            rootNode.add(methodNode);
        }

        reload();
    }

    /** Removes every method and resets the root label to AST. */
    public void clear()
    {
        rootNode.removeAllChildren();
        rootNode.setUserObject("AST");
        reload();
    }

    /**
     * Counts the method nodes under the root.
     *
     * @return the number of methods loaded
     */
    public int getMethodCount()
    {
        return rootNode.getChildCount();
    }

    /** A method paired with its recovered body, the input to loadClass. */
    public static class MethodASTEntry
    {
        private final MethodEntry method;
        private final BlockStmt body;

        /**
         * Creates an entry.
         *
         * @param method the method
         * @param body the recovered body, or null when it could not be recovered
         */
        public MethodASTEntry(MethodEntry method, BlockStmt body)
        {
            this.method = method;
            this.body = body;
        }

        /** @return the method */
        public MethodEntry method()
        {
            return method;
        }

        /** @return the recovered body, or null when it could not be recovered */
        public BlockStmt body()
        {
            return body;
        }
    }
}
