package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.ui.theme.Icons;

import javax.swing.Icon;
import java.util.List;

public class ExpressionTreeNode extends ASTTreeNode {

    public ExpressionTreeNode(Expression expr, String propertyName) {
        super(expr, propertyName);
        buildChildrenWithLabels();
    }

    private Expression getExpression() {
        return (Expression) astNode;
    }

    private void buildChildrenWithLabels() {
        Expression expr = getExpression();

        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            add(createNodeFor(binary.getLeft(), "left"));
            add(createNodeFor(binary.getRight(), "right"));
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            add(createNodeFor(unary.getOperand(), "operand"));
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            add(createNodeFor(ternary.getCondition(), "condition"));
            add(createNodeFor(ternary.getThenExpr(), "then"));
            add(createNodeFor(ternary.getElseExpr(), "else"));
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            if (call.getReceiver() != null) {
                add(createNodeFor(call.getReceiver(), "receiver"));
            }
            List<? extends ASTNode> args = call.getArguments();
            for (int i = 0; i < args.size(); i++) {
                add(createNodeFor(args.get(i), "arg[" + i + "]"));
            }
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                add(createNodeFor(field.getReceiver(), "receiver"));
            }
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            List<? extends ASTNode> args = newExpr.getArguments();
            for (int i = 0; i < args.size(); i++) {
                add(createNodeFor(args.get(i), "arg[" + i + "]"));
            }
        } else if (expr instanceof CastExpr) {
            CastExpr cast = (CastExpr) expr;
            add(createNodeFor(cast.getExpression(), "expression"));
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr access = (ArrayAccessExpr) expr;
            add(createNodeFor(access.getArray(), "array"));
            add(createNodeFor(access.getIndex(), "index"));
        } else if (expr instanceof InstanceOfExpr) {
            InstanceOfExpr instanceOf = (InstanceOfExpr) expr;
            add(createNodeFor(instanceOf.getExpression(), "expression"));
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            List<Expression> dims = newArray.getDimensions();
            for (int i = 0; i < dims.size(); i++) {
                add(createNodeFor(dims.get(i), "dim[" + i + "]"));
            }
            if (newArray.getInitializer() != null) {
                add(createNodeFor(newArray.getInitializer(), "initializer"));
            }
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            add(createNodeFor(lambda.getBody(), "body"));
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr init = (ArrayInitExpr) expr;
            List<Expression> elements = init.getElements();
            for (int i = 0; i < elements.size(); i++) {
                add(createNodeFor(elements.get(i), "[" + i + "]"));
            }
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
        Expression expr = getExpression();

        if (expr instanceof BinaryExpr) {
            return ((BinaryExpr) expr).getOperator().getSymbol();
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            return unary.getOperator().getSymbol() + (unary.isPostfix() ? " postfix" : "");
        } else if (expr instanceof VarRefExpr) {
            return ((VarRefExpr) expr).getName();
        } else if (expr instanceof LiteralExpr) {
            return formatLiteralValue((LiteralExpr) expr);
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            String name = call.getMethodName();
            if (call.isStatic()) {
                return call.getOwnerSimpleName() + "." + name;
            }
            return name;
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.isStatic()) {
                return field.getOwnerSimpleName() + "." + field.getFieldName();
            }
            return field.getFieldName();
        } else if (expr instanceof NewExpr) {
            return ((NewExpr) expr).getSimpleName();
        } else if (expr instanceof CastExpr) {
            return "→ " + ((CastExpr) expr).getTargetType().toJavaSource();
        } else if (expr instanceof InstanceOfExpr) {
            InstanceOfExpr instanceOf = (InstanceOfExpr) expr;
            String result = instanceOf.getCheckType().toJavaSource();
            if (instanceOf.hasPatternVariable()) {
                result += " " + instanceOf.getPatternVariable();
            }
            return result;
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            return newArray.getElementType().toJavaSource() +
                   "[" + newArray.getDimensionCount() + "]";
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            int params = lambda.getParameterCount();
            String body = lambda.isExpressionBody() ? "expr" : "block";
            return params + " params, " + body;
        } else if (expr instanceof ArrayInitExpr) {
            int size = ((ArrayInitExpr) expr).getElements().size();
            return size + (size == 1 ? " element" : " elements");
        } else if (expr instanceof ClassExpr) {
            return ((ClassExpr) expr).getClassType().toJavaSource() + ".class";
        }
        return "";
    }

    private String formatLiteralValue(LiteralExpr literal) {
        Object value = literal.getValue();
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            String s = (String) value;
            if (s.length() > 20) {
                return "\"" + truncate(s, 17) + "\"";
            }
            return "\"" + s + "\"";
        }
        if (value instanceof Character) {
            return "'" + value + "'";
        }
        return value.toString();
    }

    @Override
    public String getTypeAnnotation() {
        SourceType type = getExpression().getType();
        if (type != null) {
            return type.toJavaSource();
        }
        return null;
    }

    @Override
    public Icon getIcon() {
        Expression expr = getExpression();
        if (expr instanceof BinaryExpr || expr instanceof UnaryExpr) {
            return Icons.getIcon("source", 14);
        }
        if (expr instanceof VarRefExpr) {
            return Icons.getIcon("field", 14);
        }
        if (expr instanceof LiteralExpr) {
            return Icons.getIcon("bytecode", 14);
        }
        if (expr instanceof MethodCallExpr) {
            return Icons.getIcon("method_public", 14);
        }
        if (expr instanceof FieldAccessExpr) {
            return Icons.getIcon("field", 14);
        }
        if (expr instanceof NewExpr || expr instanceof NewArrayExpr) {
            return Icons.getIcon("class", 14);
        }
        if (expr instanceof TernaryExpr) {
            return Icons.getIcon("callgraph", 14);
        }
        if (expr instanceof CastExpr || expr instanceof InstanceOfExpr) {
            return Icons.getIcon("interface", 14);
        }
        if (expr instanceof LambdaExpr) {
            return Icons.getIcon("transform", 14);
        }
        if (expr instanceof ThisExpr || expr instanceof SuperExpr) {
            return Icons.getIcon("class", 14);
        }
        return Icons.getIcon("ast", 14);
    }
}
