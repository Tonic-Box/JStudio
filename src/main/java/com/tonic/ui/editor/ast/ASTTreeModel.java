package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.parser.MethodEntry;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public class ASTTreeModel extends DefaultTreeModel {

    private final DefaultMutableTreeNode rootNode;

    public ASTTreeModel() {
        super(new DefaultMutableTreeNode("AST"));
        this.rootNode = (DefaultMutableTreeNode) getRoot();
    }

    public void loadClass(String className, List<MethodASTEntry> methods) {
        rootNode.removeAllChildren();
        rootNode.setUserObject(className);

        for (MethodASTEntry entry : methods) {
            MethodRootNode methodNode = new MethodRootNode(entry.method(), entry.body());
            rootNode.add(methodNode);
        }

        reload();
    }

    public void clear() {
        rootNode.removeAllChildren();
        rootNode.setUserObject("AST");
        reload();
    }

    public int getMethodCount() {
        return rootNode.getChildCount();
    }

    public static class MethodASTEntry {
        private final MethodEntry method;
        private final BlockStmt body;

        public MethodASTEntry(MethodEntry method, BlockStmt body) {
            this.method = method;
            this.body = body;
        }

        public MethodEntry method() {
            return method;
        }

        public BlockStmt body() {
            return body;
        }
    }
}
