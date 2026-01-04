package com.tonic.ui.script.bridge;

import com.tonic.analysis.source.ast.ASTFactory;
import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.TypeUtils;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.ast.validation.ASTValidator;
import com.tonic.analysis.source.editor.*;
import com.tonic.ui.script.engine.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Bridge between JStudio script and the decompiled AST system.
 * Exposes AST manipulation functionality to scripts via the 'ast' global object.
 */
public class ASTBridge {

    private final ScriptInterpreter interpreter;
    private final List<HandlerRegistration> handlers = new ArrayList<>();
    private Consumer<String> logCallback;

    public ASTBridge(ScriptInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Creates the 'ast' object to be registered in the script context.
     */
    public ScriptValue createAstObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        // Handler registration methods
        props.put("onMethodCall", ScriptValue.function(
            ScriptFunction.native1("onMethodCall", this::registerMethodCallHandler)
        ));

        props.put("onFieldAccess", ScriptValue.function(
            ScriptFunction.native1("onFieldAccess", this::registerFieldAccessHandler)
        ));

        props.put("onBinaryExpr", ScriptValue.function(
            ScriptFunction.native1("onBinaryExpr", this::registerBinaryExprHandler)
        ));

        props.put("onUnaryExpr", ScriptValue.function(
            ScriptFunction.native1("onUnaryExpr", this::registerUnaryExprHandler)
        ));

        props.put("onIf", ScriptValue.function(
            ScriptFunction.native1("onIf", this::registerIfHandler)
        ));

        props.put("onReturn", ScriptValue.function(
            ScriptFunction.native1("onReturn", this::registerReturnHandler)
        ));

        // Factory for creating new nodes
        props.put("factory", createFactoryObject());

        // Validation framework
        props.put("validate", ScriptValue.function(
            ScriptFunction.native1("validate", this::validateNode)
        ));

        // Type utilities
        props.put("isNumeric", ScriptValue.function(
            ScriptFunction.native1("isNumeric", this::isNumeric)
        ));
        props.put("isIntegral", ScriptValue.function(
            ScriptFunction.native1("isIntegral", this::isIntegral)
        ));
        props.put("isPrimitive", ScriptValue.function(
            ScriptFunction.native1("isPrimitive", this::isPrimitive)
        ));
        props.put("isReference", ScriptValue.function(
            ScriptFunction.native1("isReference", this::isReference)
        ));
        props.put("isVoid", ScriptValue.function(
            ScriptFunction.native1("isVoid", this::isVoid)
        ));
        props.put("isBoxedType", ScriptValue.function(
            ScriptFunction.native1("isBoxedType", this::isBoxedType)
        ));
        props.put("box", ScriptValue.function(
            ScriptFunction.native1("box", this::boxType)
        ));
        props.put("unbox", ScriptValue.function(
            ScriptFunction.native1("unbox", this::unboxType)
        ));
        props.put("getSimpleName", ScriptValue.function(
            ScriptFunction.native1("getSimpleName", this::getSimpleName)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue createFactoryObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        // Literals using ASTFactory
        props.put("intLit", ScriptValue.function(
            ScriptFunction.native1("intLit", arg -> wrap(ASTFactory.intLit((int) arg.asNumber())))
        ));
        props.put("longLit", ScriptValue.function(
            ScriptFunction.native1("longLit", arg -> wrap(ASTFactory.longLit((long) arg.asNumber())))
        ));
        props.put("floatLit", ScriptValue.function(
            ScriptFunction.native1("floatLit", arg -> wrap(ASTFactory.floatLit((float) arg.asNumber())))
        ));
        props.put("doubleLit", ScriptValue.function(
            ScriptFunction.native1("doubleLit", arg -> wrap(ASTFactory.doubleLit(arg.asNumber())))
        ));
        props.put("boolLit", ScriptValue.function(
            ScriptFunction.native1("boolLit", arg -> wrap(ASTFactory.boolLit(arg.asBoolean())))
        ));
        props.put("stringLit", ScriptValue.function(
            ScriptFunction.native1("stringLit", arg -> wrap(ASTFactory.stringLit(arg.asString())))
        ));
        props.put("nullLit", ScriptValue.function(
            ScriptFunction.native0("nullLit", () -> wrap(ASTFactory.nullLit()))
        ));

        // Variable references
        props.put("varRef", ScriptValue.function(
            ScriptFunction.native2("varRef", (name, type) -> {
                SourceType srcType = unwrapType(type);
                return wrap(ASTFactory.varRef(name.asString(), srcType));
            })
        ));
        props.put("intVar", ScriptValue.function(
            ScriptFunction.native1("intVar", name -> wrap(ASTFactory.intVar(name.asString())))
        ));
        props.put("boolVar", ScriptValue.function(
            ScriptFunction.native1("boolVar", name -> wrap(ASTFactory.boolVar(name.asString())))
        ));

        // Binary operations using ASTFactory
        props.put("add", ScriptValue.function(
            ScriptFunction.native2("add", (l, r) -> wrap(ASTFactory.add(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("sub", ScriptValue.function(
            ScriptFunction.native2("sub", (l, r) -> wrap(ASTFactory.sub(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("mul", ScriptValue.function(
            ScriptFunction.native2("mul", (l, r) -> wrap(ASTFactory.mul(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("div", ScriptValue.function(
            ScriptFunction.native2("div", (l, r) -> wrap(ASTFactory.div(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("mod", ScriptValue.function(
            ScriptFunction.native2("mod", (l, r) -> wrap(ASTFactory.mod(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("eq", ScriptValue.function(
            ScriptFunction.native2("eq", (l, r) -> wrap(ASTFactory.eq(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("ne", ScriptValue.function(
            ScriptFunction.native2("ne", (l, r) -> wrap(ASTFactory.ne(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("lt", ScriptValue.function(
            ScriptFunction.native2("lt", (l, r) -> wrap(ASTFactory.lt(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("le", ScriptValue.function(
            ScriptFunction.native2("le", (l, r) -> wrap(ASTFactory.le(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("gt", ScriptValue.function(
            ScriptFunction.native2("gt", (l, r) -> wrap(ASTFactory.gt(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("ge", ScriptValue.function(
            ScriptFunction.native2("ge", (l, r) -> wrap(ASTFactory.ge(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("and", ScriptValue.function(
            ScriptFunction.native2("and", (l, r) -> wrap(ASTFactory.and(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("or", ScriptValue.function(
            ScriptFunction.native2("or", (l, r) -> wrap(ASTFactory.or(unwrapExpr(l), unwrapExpr(r))))
        ));
        props.put("assign", ScriptValue.function(
            ScriptFunction.native2("assign", (l, r) -> wrap(ASTFactory.assign(unwrapExpr(l), unwrapExpr(r))))
        ));

        // Unary operations
        props.put("not", ScriptValue.function(
            ScriptFunction.native1("not", arg -> wrap(ASTFactory.not(unwrapExpr(arg))))
        ));
        props.put("neg", ScriptValue.function(
            ScriptFunction.native1("neg", arg -> wrap(ASTFactory.neg(unwrapExpr(arg))))
        ));
        props.put("preIncr", ScriptValue.function(
            ScriptFunction.native1("preIncr", arg -> wrap(ASTFactory.preIncr(unwrapExpr(arg))))
        ));
        props.put("postIncr", ScriptValue.function(
            ScriptFunction.native1("postIncr", arg -> wrap(ASTFactory.postIncr(unwrapExpr(arg))))
        ));
        props.put("preDecr", ScriptValue.function(
            ScriptFunction.native1("preDecr", arg -> wrap(ASTFactory.preDecr(unwrapExpr(arg))))
        ));
        props.put("postDecr", ScriptValue.function(
            ScriptFunction.native1("postDecr", arg -> wrap(ASTFactory.postDecr(unwrapExpr(arg))))
        ));

        // Other expressions
        props.put("ternary", ScriptValue.function(
            ScriptFunction.nativeN("ternary", args -> {
                if (args.size() < 3) return ScriptValue.NULL;
                return wrap(ASTFactory.ternary(unwrapExpr(args.get(0)), unwrapExpr(args.get(1)), unwrapExpr(args.get(2))));
            })
        ));

        // Method calls
        props.put("methodCall", ScriptValue.function(
            ScriptFunction.nativeN("methodCall", args -> {
                if (args.size() < 2) return ScriptValue.NULL;
                Expression recv = unwrapExpr(args.get(0));
                String name = args.get(1).asString();
                List<Expression> callArgs = new ArrayList<>();
                for (int i = 2; i < args.size(); i++) {
                    Expression arg = unwrapExpr(args.get(i));
                    if (arg != null) callArgs.add(arg);
                }
                Expression[] argsArray = callArgs.toArray(new Expression[0]);
                return wrap(ASTFactory.methodCall(recv, name, "unknown", ReferenceSourceType.OBJECT, argsArray));
            })
        ));

        props.put("staticCall", ScriptValue.function(
            ScriptFunction.nativeN("staticCall", args -> {
                if (args.size() < 2) return ScriptValue.NULL;
                String owner = args.get(0).asString();
                String name = args.get(1).asString();
                List<Expression> callArgs = new ArrayList<>();
                for (int i = 2; i < args.size(); i++) {
                    Expression arg = unwrapExpr(args.get(i));
                    if (arg != null) callArgs.add(arg);
                }
                Expression[] argsArray = callArgs.toArray(new Expression[0]);
                return wrap(ASTFactory.staticCall(owner, name, ReferenceSourceType.OBJECT, argsArray));
            })
        ));

        // Field access
        props.put("fieldAccess", ScriptValue.function(
            ScriptFunction.native2("fieldAccess", (receiver, fieldName) -> {
                Expression recv = unwrapExpr(receiver);
                return wrap(ASTFactory.fieldAccess(recv, fieldName.asString(), "unknown", ReferenceSourceType.OBJECT));
            })
        ));
        props.put("staticField", ScriptValue.function(
            ScriptFunction.native2("staticField", (owner, name) ->
                wrap(ASTFactory.staticField(owner.asString(), name.asString(), ReferenceSourceType.OBJECT)))
        ));

        // Statements
        props.put("block", ScriptValue.function(
            ScriptFunction.nativeN("block", args -> {
                List<Statement> stmts = new ArrayList<>();
                for (ScriptValue arg : args) {
                    Statement stmt = unwrapStmt(arg);
                    if (stmt != null) stmts.add(stmt);
                }
                return wrap(ASTFactory.block(stmts));
            })
        ));
        props.put("ifStmt", ScriptValue.function(
            ScriptFunction.native2("ifStmt", (cond, then) ->
                wrap(ASTFactory.ifStmt(unwrapExpr(cond), unwrapStmt(then))))
        ));
        props.put("ifElse", ScriptValue.function(
            ScriptFunction.nativeN("ifElse", args -> {
                if (args.size() < 3) return ScriptValue.NULL;
                return wrap(ASTFactory.ifElse(unwrapExpr(args.get(0)), unwrapStmt(args.get(1)), unwrapStmt(args.get(2))));
            })
        ));
        props.put("whileLoop", ScriptValue.function(
            ScriptFunction.native2("whileLoop", (cond, body) ->
                wrap(ASTFactory.whileLoop(unwrapExpr(cond), unwrapStmt(body))))
        ));
        props.put("returnStmt", ScriptValue.function(
            ScriptFunction.native1("returnStmt", val -> wrap(ASTFactory.returnStmt(unwrapExpr(val))))
        ));
        props.put("returnVoid", ScriptValue.function(
            ScriptFunction.native0("returnVoid", () -> wrap(ASTFactory.returnVoid()))
        ));
        props.put("throwStmt", ScriptValue.function(
            ScriptFunction.native1("throwStmt", ex -> wrap(ASTFactory.throwStmt(unwrapExpr(ex))))
        ));
        props.put("breakStmt", ScriptValue.function(
            ScriptFunction.native0("breakStmt", () -> wrap(ASTFactory.breakStmt()))
        ));
        props.put("continueStmt", ScriptValue.function(
            ScriptFunction.native0("continueStmt", () -> wrap(ASTFactory.continueStmt()))
        ));
        props.put("exprStmt", ScriptValue.function(
            ScriptFunction.native1("exprStmt", expr -> wrap(ASTFactory.exprStmt(unwrapExpr(expr))))
        ));

        // Legacy aliases for backward compatibility
        props.put("intLiteral", props.get("intLit"));
        props.put("stringLiteral", props.get("stringLit"));
        props.put("boolLiteral", props.get("boolLit"));
        props.put("nullLiteral", props.get("nullLit"));
        props.put("binary", ScriptValue.function(
            ScriptFunction.nativeN("binary", args -> {
                if (args.size() < 3) return ScriptValue.NULL;
                Expression left = unwrapExpr(args.get(0));
                String op = args.get(1).asString();
                Expression right = unwrapExpr(args.get(2));
                BinaryOperator binOp = symbolToBinaryOp(op);
                if (binOp == null) return ScriptValue.NULL;
                return wrap(ASTFactory.binary(binOp, left, right, PrimitiveSourceType.INT));
            })
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue wrap(ASTNode node) {
        return new ASTNodeWrapper(interpreter, node).toScriptValue();
    }

    private SourceType unwrapType(ScriptValue val) {
        if (val.isNull()) return ReferenceSourceType.OBJECT;
        if (val.isString()) {
            String s = val.asString();
            switch (s) {
                case "int": return PrimitiveSourceType.INT;
                case "long": return PrimitiveSourceType.LONG;
                case "float": return PrimitiveSourceType.FLOAT;
                case "double": return PrimitiveSourceType.DOUBLE;
                case "boolean": return PrimitiveSourceType.BOOLEAN;
                case "char": return PrimitiveSourceType.CHAR;
                case "byte": return PrimitiveSourceType.BYTE;
                case "short": return PrimitiveSourceType.SHORT;
                case "void": return VoidSourceType.INSTANCE;
                default: return new ReferenceSourceType(s);
            }
        }
        return ReferenceSourceType.OBJECT;
    }

    private Statement unwrapStmt(ScriptValue val) {
        if (val.isNull()) return null;
        if (val.isNative()) {
            Object obj = val.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object node = ((ASTNodeWrapper) obj).unwrap();
                if (node instanceof Statement) return (Statement) node;
            }
            if (obj instanceof Statement) return (Statement) obj;
        }
        return null;
    }

    private Expression createLiteral(ScriptValue arg) {
        if (arg.isNull()) {
            return LiteralExpr.ofNull();
        }
        if (arg.isBoolean()) {
            return LiteralExpr.ofBoolean(arg.asBoolean());
        }
        if (arg.isNumber()) {
            double d = arg.asNumber();
            if (d == (int) d) {
                return LiteralExpr.ofInt((int) d);
            }
            return LiteralExpr.ofDouble(d);
        }
        return LiteralExpr.ofString(arg.asString());
    }

    private BinaryOperator symbolToBinaryOp(String symbol) {
        for (BinaryOperator op : BinaryOperator.values()) {
            if (op.getSymbol().equals(symbol)) {
                return op;
            }
        }
        return null;
    }

    private Expression unwrapExpr(ScriptValue val) {
        if (val.isNull()) return null;
        if (val.isNative()) {
            Object obj = val.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object node = ((ASTNodeWrapper) obj).unwrap();
                if (node instanceof Expression) {
                    return (Expression) node;
                }
            }
            if (obj instanceof Expression) {
                return (Expression) obj;
            }
        }
        return createLiteral(val);
    }

    // ==================== Handler Registration ====================

    private ScriptValue registerMethodCallHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onMethodCall requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.METHOD_CALL, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerFieldAccessHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onFieldAccess requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.FIELD_ACCESS, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerBinaryExprHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onBinaryExpr requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.BINARY_EXPR, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerUnaryExprHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onUnaryExpr requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.UNARY_EXPR, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerIfHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onIf requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.IF_STMT, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerReturnHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onReturn requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.RETURN_STMT, callback.asFunction()));
        return ScriptValue.NULL;
    }

    // ==================== Apply to AST Editor ====================

    /**
     * Applies all registered handlers to the AST via the given editor.
     * Returns the number of modifications made.
     */
    public int applyTo(ASTEditor editor) {
        int[] modCount = new int[]{0};

        for (HandlerRegistration reg : handlers) {
            switch (reg.type) {
                case METHOD_CALL:
                    editor.onMethodCall((ctx, call) -> {
                        ScriptValue wrapped = new ASTNodeWrapper(call).toScriptValue();
                        ScriptValue result = callHandler(reg.function, wrapped);
                        return processResult(result, call, modCount);
                    });
                    break;

                case FIELD_ACCESS:
                    editor.onFieldAccess((ctx, field) -> {
                        ScriptValue wrapped = new ASTNodeWrapper(field).toScriptValue();
                        ScriptValue result = callHandler(reg.function, wrapped);
                        return processResult(result, field, modCount);
                    });
                    break;

                case BINARY_EXPR:
                    editor.onBinaryExpr((ctx, expr) -> {
                        ScriptValue wrapped = new ASTNodeWrapper(expr).toScriptValue();
                        ScriptValue result = callHandler(reg.function, wrapped);
                        return processResult(result, expr, modCount);
                    });
                    break;

                case UNARY_EXPR:
                    editor.onUnaryExpr((ctx, expr) -> {
                        ScriptValue wrapped = new ASTNodeWrapper(expr).toScriptValue();
                        ScriptValue result = callHandler(reg.function, wrapped);
                        return processResult(result, expr, modCount);
                    });
                    break;

                case IF_STMT:
                    editor.onIf((ctx, stmt) -> {
                        ScriptValue wrapped = new ASTNodeWrapper(stmt).toScriptValue();
                        ScriptValue result = callHandler(reg.function, wrapped);
                        return processStmtResult(result, stmt, modCount);
                    });
                    break;

                case RETURN_STMT:
                    editor.onReturn((ctx, stmt) -> {
                        ScriptValue wrapped = new ASTNodeWrapper(stmt).toScriptValue();
                        ScriptValue result = callHandler(reg.function, wrapped);
                        return processStmtResult(result, stmt, modCount);
                    });
                    break;
            }
        }

        editor.apply();
        return modCount[0];
    }

    private ScriptValue callHandler(ScriptFunction function, ScriptValue arg) {
        List<ScriptValue> args = new ArrayList<>();
        args.add(arg);
        try {
            return function.call(interpreter, args);
        } catch (Exception e) {
            if (logCallback != null) {
                logCallback.accept("Handler error: " + e.getMessage());
            }
            return ScriptValue.NULL;
        }
    }

    private Replacement processResult(ScriptValue result, Expression original, int[] modCount) {
        if (result == null || result.isNull()) {
            // null means remove
            modCount[0]++;
            return Replacement.remove();
        }
        if (result.isNative()) {
            Object obj = result.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object node = ((ASTNodeWrapper) obj).unwrap();
                if (node instanceof Expression && node != original) {
                    modCount[0]++;
                    return Replacement.with((Expression) node);
                }
            }
        }
        if (result.isObject()) {
            // Could reconstruct expression from object properties
        }
        return Replacement.keep();
    }

    private Replacement processStmtResult(ScriptValue result, Statement original, int[] modCount) {
        if (result == null || result.isNull()) {
            modCount[0]++;
            return Replacement.remove();
        }
        if (result.isNative()) {
            Object obj = result.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object node = ((ASTNodeWrapper) obj).unwrap();
                if (node instanceof Statement && node != original) {
                    modCount[0]++;
                    return Replacement.with((Statement) node);
                }
            }
        }
        return Replacement.keep();
    }

    /**
     * Clears all registered handlers.
     */
    public void clearHandlers() {
        handlers.clear();
    }

    // ==================== Validation Framework ====================

    private ScriptValue validateNode(ScriptValue nodeVal) {
        ASTNode astNode = unwrapASTNode(nodeVal);
        if (astNode == null) {
            Map<String, ScriptValue> result = new HashMap<>();
            result.put("valid", ScriptValue.bool(false));
            result.put("errors", ScriptValue.array(List.of(
                wrapValidationError("NULL_NODE", "ERROR", "Node is null or invalid")
            )));
            return ScriptValue.object(result);
        }

        ASTValidator validator = new ASTValidator();
        ASTValidator.ValidationResult validationResult = validator.validate(astNode);

        Map<String, ScriptValue> result = new HashMap<>();
        result.put("valid", ScriptValue.bool(validationResult.isValid()));

        List<ScriptValue> errors = new ArrayList<>();
        for (var error : validationResult.getErrors()) {
            errors.add(wrapValidationError(
                error.getCategory().name(),
                error.getSeverity().name(),
                error.getMessage()
            ));
        }
        result.put("errors", ScriptValue.array(errors));

        return ScriptValue.object(result);
    }

    private ScriptValue wrapValidationError(String category, String severity, String message) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("category", ScriptValue.string(category));
        props.put("severity", ScriptValue.string(severity));
        props.put("message", ScriptValue.string(message));
        return ScriptValue.object(props);
    }

    private ASTNode unwrapASTNode(ScriptValue val) {
        if (val.isNull()) return null;
        if (val.isNative()) {
            Object obj = val.unwrap();
            if (obj instanceof ASTNodeWrapper) {
                Object node = ((ASTNodeWrapper) obj).unwrap();
                if (node instanceof ASTNode) return (ASTNode) node;
            }
            if (obj instanceof ASTNode) return (ASTNode) obj;
        }
        return null;
    }

    // ==================== Type Utilities ====================

    private ScriptValue isNumeric(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.bool(TypeUtils.isNumeric(type));
    }

    private ScriptValue isIntegral(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.bool(TypeUtils.isIntegral(type));
    }

    private ScriptValue isPrimitive(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.bool(TypeUtils.isPrimitive(type));
    }

    private ScriptValue isReference(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.bool(TypeUtils.isReference(type));
    }

    private ScriptValue isVoid(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.bool(TypeUtils.isVoid(type));
    }

    private ScriptValue isBoxedType(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.bool(TypeUtils.isBoxedType(type));
    }

    private ScriptValue boxType(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        SourceType boxed = TypeUtils.box(type);
        if (boxed == null) return ScriptValue.NULL;
        return ScriptValue.string(boxed.toJavaSource());
    }

    private ScriptValue unboxType(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        SourceType unboxed = TypeUtils.unbox(type);
        if (unboxed == null) return ScriptValue.NULL;
        return ScriptValue.string(unboxed.toJavaSource());
    }

    private ScriptValue getSimpleName(ScriptValue typeVal) {
        SourceType type = unwrapType(typeVal);
        return ScriptValue.string(TypeUtils.getSimpleName(type));
    }

    // ==================== Internal Classes ====================

    private enum HandlerType {
        METHOD_CALL,
        FIELD_ACCESS,
        BINARY_EXPR,
        UNARY_EXPR,
        IF_STMT,
        RETURN_STMT
    }

    private static class HandlerRegistration {
        final HandlerType type;
        final ScriptFunction function;

        HandlerRegistration(HandlerType type, ScriptFunction function) {
            this.type = type;
            this.function = function;
        }
    }
}
