package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.ui.theme.Icons;

import javax.swing.Icon;
import java.util.List;

public class StatementTreeNode extends ASTTreeNode {

    public StatementTreeNode(Statement stmt, String propertyName) {
        super(stmt, propertyName);
        buildChildrenWithLabels();
    }

    private Statement getStatement() {
        return (Statement) astNode;
    }

    private void buildChildrenWithLabels() {
        Statement stmt = getStatement();

        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            List<Statement> stmts = block.getStatements();
            for (int i = 0; i < stmts.size(); i++) {
                add(createNodeFor(stmts.get(i), "[" + i + "]"));
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            add(createNodeFor(ifStmt.getCondition(), "condition"));
            add(createNodeFor(ifStmt.getThenBranch(), "then"));
            if (ifStmt.hasElse()) {
                add(createNodeFor(ifStmt.getElseBranch(), "else"));
            }
        } else if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            add(createNodeFor(whileStmt.getCondition(), "condition"));
            add(createNodeFor(whileStmt.getBody(), "body"));
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            add(createNodeFor(doWhile.getBody(), "body"));
            add(createNodeFor(doWhile.getCondition(), "condition"));
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            List<Statement> init = forStmt.getInit();
            if (init != null && !init.isEmpty()) {
                for (int i = 0; i < init.size(); i++) {
                    add(createNodeFor(init.get(i), "init[" + i + "]"));
                }
            }
            if (forStmt.getCondition() != null) {
                add(createNodeFor(forStmt.getCondition(), "condition"));
            }
            List<Expression> update = forStmt.getUpdate();
            if (update != null && !update.isEmpty()) {
                for (int i = 0; i < update.size(); i++) {
                    add(createNodeFor(update.get(i), "update[" + i + "]"));
                }
            }
            add(createNodeFor(forStmt.getBody(), "body"));
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            add(createNodeFor(forEach.getVariable(), "variable"));
            add(createNodeFor(forEach.getIterable(), "iterable"));
            add(createNodeFor(forEach.getBody(), "body"));
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            add(createNodeFor(switchStmt.getSelector(), "selector"));
            List<SwitchCase> cases = switchStmt.getCases();
            for (int i = 0; i < cases.size(); i++) {
                SwitchCase sc = cases.get(i);
                String label = sc.isDefault() ? "default" : "case[" + i + "]";
                for (int j = 0; j < sc.statements().size(); j++) {
                    add(createNodeFor(sc.statements().get(j), label + "[" + j + "]"));
                }
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            if (tryCatch.hasResources()) {
                List<Expression> resources = tryCatch.getResources();
                for (int i = 0; i < resources.size(); i++) {
                    add(createNodeFor(resources.get(i), "resource[" + i + "]"));
                }
            }
            add(createNodeFor(tryCatch.getTryBlock(), "try"));
            List<CatchClause> catches = tryCatch.getCatches();
            for (int i = 0; i < catches.size(); i++) {
                add(createNodeFor(catches.get(i).body(), "catch[" + i + "]"));
            }
            if (tryCatch.getFinallyBlock() != null) {
                add(createNodeFor(tryCatch.getFinallyBlock(), "finally"));
            }
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                add(createNodeFor(ret.getValue(), "value"));
            }
        } else if (stmt instanceof ThrowStmt) {
            ThrowStmt throwStmt = (ThrowStmt) stmt;
            add(createNodeFor(throwStmt.getException(), "exception"));
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt varDecl = (VarDeclStmt) stmt;
            if (varDecl.getInitializer() != null) {
                add(createNodeFor(varDecl.getInitializer(), "initializer"));
            }
        } else if (stmt instanceof ExprStmt) {
            ExprStmt exprStmt = (ExprStmt) stmt;
            add(createNodeFor(exprStmt.getExpression(), "expression"));
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt sync = (SynchronizedStmt) stmt;
            add(createNodeFor(sync.getLock(), "lock"));
            add(createNodeFor(sync.getBody(), "body"));
        } else if (stmt instanceof LabeledStmt) {
            LabeledStmt labeled = (LabeledStmt) stmt;
            add(createNodeFor(labeled.getStatement(), "statement"));
        } else {
            for (ASTNode child : astNode.getChildren()) {
                if (child != null) {
                    add(createNodeFor(child, null));
                }
            }
        }
    }

    @Override
    public String getNodeTypeName() {
        return astNode.getClass().getSimpleName();
    }

    @Override
    public String getNodeDetails() {
        Statement stmt = getStatement();

        if (stmt instanceof BlockStmt) {
            int size = ((BlockStmt) stmt).size();
            return size + (size == 1 ? " stmt" : " stmts");
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            if (ifStmt.isElseIf()) return "else-if chain";
            if (ifStmt.hasElse()) return "with else";
            return "";
        } else if (stmt instanceof WhileStmt) {
            String label = stmt.getLabel();
            return label != null ? "label=" + label : "";
        } else if (stmt instanceof DoWhileStmt) {
            String label = stmt.getLabel();
            return label != null ? "label=" + label : "";
        } else if (stmt instanceof ForStmt) {
            String label = stmt.getLabel();
            return label != null ? "label=" + label : "";
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            String label = forEach.getLabel();
            return label != null ? "label=" + label : "";
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt varDecl = (VarDeclStmt) stmt;
            StringBuilder sb = new StringBuilder(varDecl.getName());
            if (varDecl.isFinal()) sb.insert(0, "final ");
            return sb.toString();
        } else if (stmt instanceof BreakStmt) {
            String target = ((BreakStmt) stmt).getTargetLabel();
            return target != null ? target : "";
        } else if (stmt instanceof ContinueStmt) {
            String target = ((ContinueStmt) stmt).getTargetLabel();
            return target != null ? target : "";
        } else if (stmt instanceof LabeledStmt) {
            return stmt.getLabel();
        } else if (stmt instanceof SwitchStmt) {
            int cases = ((SwitchStmt) stmt).getCases().size();
            return cases + (cases == 1 ? " case" : " cases");
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tc = (TryCatchStmt) stmt;
            StringBuilder sb = new StringBuilder();
            if (tc.hasResources()) sb.append("try-with-resources ");
            int catches = tc.getCatches().size();
            sb.append(catches).append(catches == 1 ? " catch" : " catches");
            if (tc.getFinallyBlock() != null) sb.append(" + finally");
            return sb.toString();
        }
        return "";
    }

    @Override
    public String getTypeAnnotation() {
        Statement stmt = getStatement();
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt varDecl = (VarDeclStmt) stmt;
            if (varDecl.isUseVarKeyword()) {
                return "var -> " + varDecl.getType().toJavaSource();
            }
            return varDecl.getType().toJavaSource();
        }
        return null;
    }

    @Override
    public Icon getIcon() {
        Statement stmt = getStatement();
        if (stmt instanceof BlockStmt) return Icons.getIcon("folder", 14);
        if (stmt instanceof IfStmt) return Icons.getIcon("callgraph", 14);
        if (stmt instanceof WhileStmt || stmt instanceof DoWhileStmt ||
            stmt instanceof ForStmt || stmt instanceof ForEachStmt) {
            return Icons.getIcon("refresh", 14);
        }
        if (stmt instanceof ReturnStmt) return Icons.getIcon("back", 14);
        if (stmt instanceof ThrowStmt) return Icons.getIcon("warning", 14);
        if (stmt instanceof VarDeclStmt) return Icons.getIcon("field", 14);
        if (stmt instanceof SwitchStmt) return Icons.getIcon("ir", 14);
        if (stmt instanceof TryCatchStmt) return Icons.getIcon("debug", 14);
        return Icons.getIcon("ast", 14);
    }
}
