package com.tonic.ui.script.bridge;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;
import lombok.Getter;

import java.util.*;

@Getter
public class ASTNodeWrapper {

    private final ScriptInterpreter interpreter;
    private final Object node;

    private static final Map<String, Class<? extends ASTNode>> AST_CLASSES = new HashMap<>();
    static {
        AST_CLASSES.put("MethodCallExpr", MethodCallExpr.class);
        AST_CLASSES.put("BinaryExpr", BinaryExpr.class);
        AST_CLASSES.put("UnaryExpr", UnaryExpr.class);
        AST_CLASSES.put("FieldAccessExpr", FieldAccessExpr.class);
        AST_CLASSES.put("VarRefExpr", VarRefExpr.class);
        AST_CLASSES.put("LiteralExpr", LiteralExpr.class);
        AST_CLASSES.put("TernaryExpr", TernaryExpr.class);
        AST_CLASSES.put("CastExpr", CastExpr.class);
        AST_CLASSES.put("InstanceOfExpr", InstanceOfExpr.class);
        AST_CLASSES.put("ArrayAccessExpr", ArrayAccessExpr.class);
        AST_CLASSES.put("NewExpr", NewExpr.class);
        AST_CLASSES.put("ThisExpr", ThisExpr.class);
        AST_CLASSES.put("IfStmt", IfStmt.class);
        AST_CLASSES.put("WhileStmt", WhileStmt.class);
        AST_CLASSES.put("DoWhileStmt", DoWhileStmt.class);
        AST_CLASSES.put("ForStmt", ForStmt.class);
        AST_CLASSES.put("ForEachStmt", ForEachStmt.class);
        AST_CLASSES.put("ReturnStmt", ReturnStmt.class);
        AST_CLASSES.put("BlockStmt", BlockStmt.class);
        AST_CLASSES.put("ExprStmt", ExprStmt.class);
        AST_CLASSES.put("VarDeclStmt", VarDeclStmt.class);
        AST_CLASSES.put("ThrowStmt", ThrowStmt.class);
        AST_CLASSES.put("TryCatchStmt", TryCatchStmt.class);
        AST_CLASSES.put("SwitchStmt", SwitchStmt.class);
        AST_CLASSES.put("BreakStmt", BreakStmt.class);
        AST_CLASSES.put("ContinueStmt", ContinueStmt.class);
    }

    public ASTNodeWrapper(Object node) {
        this(null, node);
    }

    public ASTNodeWrapper(ScriptInterpreter interpreter, Object node) {
        this.interpreter = interpreter;
        this.node = node;
    }

    public Object unwrap() {
        return node;
    }

    public ScriptValue toScriptValue() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("type", ScriptValue.string(node.getClass().getSimpleName()));
        props.put("_native", ScriptValue.native_(this));

        addTraversalMethods(props);
        addMutationMethods(props);
        addTypeSpecificProperties(props);
        addFluentSetters(props);

        return ScriptValue.object(props);
    }

    private void addTraversalMethods(Map<String, ScriptValue> props) {
        props.put("walk", ScriptValue.function(
            ScriptFunction.native1("walk", this::walk)
        ));
        props.put("findFirst", ScriptValue.function(
            ScriptFunction.nativeN("findFirst", this::findFirst)
        ));
        props.put("findAll", ScriptValue.function(
            ScriptFunction.nativeN("findAll", this::findAll)
        ));
        props.put("findAncestor", ScriptValue.function(
            ScriptFunction.native1("findAncestor", this::findAncestor)
        ));
        props.put("getChildren", ScriptValue.function(
            ScriptFunction.native0("getChildren", this::getChildren)
        ));
    }

    private void addMutationMethods(Map<String, ScriptValue> props) {
        props.put("clone", ScriptValue.function(
            ScriptFunction.native0("clone", this::deepClone)
        ));
        props.put("replaceWith", ScriptValue.function(
            ScriptFunction.native1("replaceWith", this::replaceWith)
        ));
        props.put("remove", ScriptValue.function(
            ScriptFunction.native0("remove", this::remove)
        ));
    }

    private ScriptValue walk(ScriptValue callback) {
        if (!(node instanceof ASTNode) || interpreter == null || !callback.isFunction()) {
            return ScriptValue.NULL;
        }
        ASTNode astNode = (ASTNode) node;
        astNode.walk(child -> {
            try {
                callback.asFunction().call(interpreter,
                    List.of(new ASTNodeWrapper(interpreter, child).toScriptValue()));
            } catch (Exception ignored) {}
        });
        return ScriptValue.NULL;
    }

    private ScriptValue findFirst(List<ScriptValue> args) {
        if (!(node instanceof ASTNode) || args.isEmpty()) {
            return ScriptValue.NULL;
        }
        String typeName = args.get(0).asString();
        Class<? extends ASTNode> targetClass = AST_CLASSES.get(typeName);
        if (targetClass == null) return ScriptValue.NULL;

        ASTNode astNode = (ASTNode) node;
        ScriptFunction predicate = args.size() > 1 && args.get(1).isFunction() ?
            args.get(1).asFunction() : null;

        Optional<? extends ASTNode> found = astNode.findFirst(targetClass, n -> {
            if (predicate == null || interpreter == null) return true;
            try {
                ScriptValue result = predicate.call(interpreter,
                    List.of(new ASTNodeWrapper(interpreter, n).toScriptValue()));
                return result.isBoolean() && result.asBoolean();
            } catch (Exception e) {
                return false;
            }
        });

        return found.map(n -> new ASTNodeWrapper(interpreter, n).toScriptValue())
                   .orElse(ScriptValue.NULL);
    }

    private ScriptValue findAll(List<ScriptValue> args) {
        if (!(node instanceof ASTNode) || args.isEmpty()) {
            return ScriptValue.array(new ArrayList<>());
        }
        String typeName = args.get(0).asString();
        Class<? extends ASTNode> targetClass = AST_CLASSES.get(typeName);
        if (targetClass == null) return ScriptValue.array(new ArrayList<>());

        ASTNode astNode = (ASTNode) node;
        ScriptFunction predicate = args.size() > 1 && args.get(1).isFunction() ?
            args.get(1).asFunction() : null;

        List<? extends ASTNode> found = astNode.findAll(targetClass, n -> {
            if (predicate == null || interpreter == null) return true;
            try {
                ScriptValue result = predicate.call(interpreter,
                    List.of(new ASTNodeWrapper(interpreter, n).toScriptValue()));
                return result.isBoolean() && result.asBoolean();
            } catch (Exception e) {
                return false;
            }
        });

        List<ScriptValue> wrapped = new ArrayList<>();
        for (ASTNode n : found) {
            wrapped.add(new ASTNodeWrapper(interpreter, n).toScriptValue());
        }
        return ScriptValue.array(wrapped);
    }

    private ScriptValue findAncestor(ScriptValue typeName) {
        if (!(node instanceof ASTNode)) return ScriptValue.NULL;
        Class<? extends ASTNode> targetClass = AST_CLASSES.get(typeName.asString());
        if (targetClass == null) return ScriptValue.NULL;

        ASTNode astNode = (ASTNode) node;
        Optional<? extends ASTNode> found = astNode.findAncestor(targetClass);
        return found.map(n -> new ASTNodeWrapper(interpreter, n).toScriptValue())
                   .orElse(ScriptValue.NULL);
    }

    private ScriptValue getChildren() {
        if (!(node instanceof ASTNode)) return ScriptValue.array(new ArrayList<>());
        ASTNode astNode = (ASTNode) node;
        List<ASTNode> children = astNode.getChildren();
        List<ScriptValue> wrapped = new ArrayList<>();
        for (ASTNode child : children) {
            wrapped.add(new ASTNodeWrapper(interpreter, child).toScriptValue());
        }
        return ScriptValue.array(wrapped);
    }

    private ScriptValue deepClone() {
        if (!(node instanceof ASTNode)) return ScriptValue.NULL;
        ASTNode astNode = (ASTNode) node;
        ASTNode cloned = astNode.deepClone();
        return new ASTNodeWrapper(interpreter, cloned).toScriptValue();
    }

    private ScriptValue replaceWith(ScriptValue replacement) {
        if (!(node instanceof ASTNode)) return ScriptValue.bool(false);
        ASTNode astNode = (ASTNode) node;
        ASTNode newNode = unwrapASTNode(replacement);
        if (newNode == null) return ScriptValue.bool(false);
        boolean success = astNode.replaceWith(newNode);
        return ScriptValue.bool(success);
    }

    private ScriptValue remove() {
        if (!(node instanceof ASTNode)) return ScriptValue.bool(false);
        ASTNode astNode = (ASTNode) node;
        boolean success = astNode.remove();
        return ScriptValue.bool(success);
    }

    private ASTNode unwrapASTNode(ScriptValue val) {
        if (val.isNull()) return null;
        if (val.isNative()) {
            Object obj = val.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object n = ((ASTNodeWrapper) obj).unwrap();
                if (n instanceof ASTNode) return (ASTNode) n;
            }
            if (obj instanceof ASTNode) return (ASTNode) obj;
        }
        return null;
    }

    private void addFluentSetters(Map<String, ScriptValue> props) {
        if (node instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) node;
            props.put("withLeft", ScriptValue.function(
                ScriptFunction.native1("withLeft", val -> {
                    Expression expr = unwrapExpr(val);
                    return new ASTNodeWrapper(interpreter, binary.withLeft(expr)).toScriptValue();
                })
            ));
            props.put("withRight", ScriptValue.function(
                ScriptFunction.native1("withRight", val -> {
                    Expression expr = unwrapExpr(val);
                    return new ASTNodeWrapper(interpreter, binary.withRight(expr)).toScriptValue();
                })
            ));
        }
        else if (node instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) node;
            props.put("withOperand", ScriptValue.function(
                ScriptFunction.native1("withOperand", val -> {
                    Expression expr = unwrapExpr(val);
                    return new ASTNodeWrapper(interpreter, unary.withOperand(expr)).toScriptValue();
                })
            ));
        }
        else if (node instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) node;
            props.put("withCondition", ScriptValue.function(
                ScriptFunction.native1("withCondition", val ->
                    new ASTNodeWrapper(interpreter, ternary.withCondition(unwrapExpr(val))).toScriptValue())
            ));
            props.put("withThenExpr", ScriptValue.function(
                ScriptFunction.native1("withThenExpr", val ->
                    new ASTNodeWrapper(interpreter, ternary.withThenExpr(unwrapExpr(val))).toScriptValue())
            ));
            props.put("withElseExpr", ScriptValue.function(
                ScriptFunction.native1("withElseExpr", val ->
                    new ASTNodeWrapper(interpreter, ternary.withElseExpr(unwrapExpr(val))).toScriptValue())
            ));
        }
        else if (node instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) node;
            props.put("withCondition", ScriptValue.function(
                ScriptFunction.native1("withCondition", val ->
                    new ASTNodeWrapper(interpreter, ifStmt.withCondition(unwrapExpr(val))).toScriptValue())
            ));
            props.put("withThenBranch", ScriptValue.function(
                ScriptFunction.native1("withThenBranch", val ->
                    new ASTNodeWrapper(interpreter, ifStmt.withThenBranch(unwrapStmt(val))).toScriptValue())
            ));
            props.put("withElseBranch", ScriptValue.function(
                ScriptFunction.native1("withElseBranch", val ->
                    new ASTNodeWrapper(interpreter, ifStmt.withElseBranch(unwrapStmt(val))).toScriptValue())
            ));
        }
        else if (node instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) node;
            props.put("withCondition", ScriptValue.function(
                ScriptFunction.native1("withCondition", val ->
                    new ASTNodeWrapper(interpreter, whileStmt.withCondition(unwrapExpr(val))).toScriptValue())
            ));
            props.put("withBody", ScriptValue.function(
                ScriptFunction.native1("withBody", val ->
                    new ASTNodeWrapper(interpreter, whileStmt.withBody(unwrapStmt(val))).toScriptValue())
            ));
        }
        else if (node instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) node;
            props.put("withValue", ScriptValue.function(
                ScriptFunction.native1("withValue", val ->
                    new ASTNodeWrapper(interpreter, ret.withValue(unwrapExpr(val))).toScriptValue())
            ));
        }
    }

    private Expression unwrapExpr(ScriptValue val) {
        if (val.isNull()) return null;
        if (val.isNative()) {
            Object obj = val.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object n = ((ASTNodeWrapper) obj).unwrap();
                if (n instanceof Expression) return (Expression) n;
            }
            if (obj instanceof Expression) return (Expression) obj;
        }
        return null;
    }

    private Statement unwrapStmt(ScriptValue val) {
        if (val.isNull()) return null;
        if (val.isNative()) {
            Object obj = val.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object n = ((ASTNodeWrapper) obj).unwrap();
                if (n instanceof Statement) return (Statement) n;
            }
            if (obj instanceof Statement) return (Statement) obj;
        }
        return null;
    }

    private void addTypeSpecificProperties(Map<String, ScriptValue> props) {
        if (node instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) node;
            props.put("name", ScriptValue.string(call.getMethodName()));
            props.put("owner", ScriptValue.string(call.getOwnerClass() != null ? call.getOwnerClass() : ""));
            props.put("receiver", call.getReceiver() != null ?
                new ASTNodeWrapper(interpreter, call.getReceiver()).toScriptValue() : ScriptValue.NULL);
            props.put("args", wrapList(call.getArguments()));
            props.put("isStatic", ScriptValue.bool(call.isStatic()));
        }
        else if (node instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) node;
            props.put("name", ScriptValue.string(field.getFieldName()));
            props.put("owner", ScriptValue.string(field.getOwnerClass() != null ? field.getOwnerClass() : ""));
            props.put("receiver", field.getReceiver() != null ?
                new ASTNodeWrapper(interpreter, field.getReceiver()).toScriptValue() : ScriptValue.NULL);
            props.put("isStatic", ScriptValue.bool(field.isStatic()));
        }
        else if (node instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) node;
            props.put("op", ScriptValue.string(binary.getOperator().getSymbol()));
            props.put("left", new ASTNodeWrapper(interpreter, binary.getLeft()).toScriptValue());
            props.put("right", new ASTNodeWrapper(interpreter, binary.getRight()).toScriptValue());
            props.put("isComparison", ScriptValue.bool(binary.isComparison()));
            props.put("isLogical", ScriptValue.bool(binary.isLogical()));
            props.put("isAssignment", ScriptValue.bool(binary.isAssignment()));
        }
        else if (node instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) node;
            props.put("op", ScriptValue.string(unary.getOperator().getSymbol()));
            props.put("operand", new ASTNodeWrapper(interpreter, unary.getOperand()).toScriptValue());
            props.put("isPrefix", ScriptValue.bool(unary.isPrefix()));
        }
        else if (node instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) node;
            props.put("value", wrapLiteralValue(lit.getValue()));
            props.put("isNull", ScriptValue.bool(lit.isNull()));
            props.put("isString", ScriptValue.bool(lit.isString()));
            props.put("isNumeric", ScriptValue.bool(lit.isNumeric()));
            props.put("isConstant", ScriptValue.TRUE);
        }
        else if (node instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) node;
            props.put("name", ScriptValue.string(varRef.getName()));
        }
        else if (node instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) node;
            props.put("condition", new ASTNodeWrapper(interpreter, ifStmt.getCondition()).toScriptValue());
            props.put("thenBranch", new ASTNodeWrapper(interpreter, ifStmt.getThenBranch()).toScriptValue());
            props.put("elseBranch", ifStmt.getElseBranch() != null ?
                new ASTNodeWrapper(interpreter, ifStmt.getElseBranch()).toScriptValue() : ScriptValue.NULL);
        }
        else if (node instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) node;
            props.put("condition", new ASTNodeWrapper(interpreter, whileStmt.getCondition()).toScriptValue());
            props.put("body", new ASTNodeWrapper(interpreter, whileStmt.getBody()).toScriptValue());
        }
        else if (node instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) node;
            props.put("value", ret.getValue() != null ?
                new ASTNodeWrapper(interpreter, ret.getValue()).toScriptValue() : ScriptValue.NULL);
        }
        else if (node instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) node;
            props.put("statements", wrapList(block.getStatements()));
        }
        else if (node instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) node;
            props.put("condition", new ASTNodeWrapper(interpreter, ternary.getCondition()).toScriptValue());
            props.put("thenExpr", new ASTNodeWrapper(interpreter, ternary.getThenExpr()).toScriptValue());
            props.put("elseExpr", new ASTNodeWrapper(interpreter, ternary.getElseExpr()).toScriptValue());
        }
    }

    private ScriptValue wrapLiteralValue(Object value) {
        if (value == null) return ScriptValue.NULL;
        if (value instanceof String) return ScriptValue.string((String) value);
        if (value instanceof Number) return ScriptValue.number(((Number) value).doubleValue());
        if (value instanceof Boolean) return ScriptValue.bool((Boolean) value);
        if (value instanceof Character) return ScriptValue.string(String.valueOf(value));
        return ScriptValue.string(value.toString());
    }

    private ScriptValue wrapList(List<?> items) {
        List<ScriptValue> wrapped = new ArrayList<>();
        for (Object item : items) {
            wrapped.add(new ASTNodeWrapper(interpreter, item).toScriptValue());
        }
        return ScriptValue.array(wrapped);
    }
}
