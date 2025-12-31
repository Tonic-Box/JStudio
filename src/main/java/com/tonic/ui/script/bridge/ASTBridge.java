package com.tonic.ui.script.bridge;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.editor.*;
import com.tonic.ui.script.engine.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

        return ScriptValue.object(props);
    }

    private ScriptValue createFactoryObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("methodCall", ScriptValue.function(
            ScriptFunction.nativeN("methodCall", args -> {
                if (args.size() < 3) return ScriptValue.NULL;
                Expression recv = unwrapExpr(args.get(0));
                String name = args.get(1).asString();
                List<Expression> callArgs = new ArrayList<>();
                for (int i = 2; i < args.size(); i++) {
                    Expression arg = unwrapExpr(args.get(i));
                    if (arg != null) callArgs.add(arg);
                }
                // Use unknown owner for factory-created calls
                MethodCallExpr expr = new MethodCallExpr(recv, name, "unknown", callArgs, false,
                    ReferenceSourceType.OBJECT);
                return new ASTNodeWrapper(expr).toScriptValue();
            })
        ));

        props.put("intLiteral", ScriptValue.function(
            ScriptFunction.native1("intLiteral", arg -> {
                int val = (int) arg.asNumber();
                return new ASTNodeWrapper(LiteralExpr.ofInt(val)).toScriptValue();
            })
        ));

        props.put("stringLiteral", ScriptValue.function(
            ScriptFunction.native1("stringLiteral", arg -> new ASTNodeWrapper(LiteralExpr.ofString(arg.asString())).toScriptValue())
        ));

        props.put("boolLiteral", ScriptValue.function(
            ScriptFunction.native1("boolLiteral", arg -> new ASTNodeWrapper(LiteralExpr.ofBoolean(arg.asBoolean())).toScriptValue())
        ));

        props.put("nullLiteral", ScriptValue.function(
            ScriptFunction.native0("nullLiteral", () -> new ASTNodeWrapper(LiteralExpr.ofNull()).toScriptValue())
        ));

        props.put("fieldAccess", ScriptValue.function(
            ScriptFunction.native2("fieldAccess", (receiver, fieldName) -> {
                Expression recv = unwrapExpr(receiver);
                return new ASTNodeWrapper(
                    FieldAccessExpr.instanceField(recv, fieldName.asString(), "unknown", ReferenceSourceType.OBJECT)
                ).toScriptValue();
            })
        ));

        props.put("binary", ScriptValue.function(
            ScriptFunction.nativeN("binary", args -> {
                if (args.size() < 3) return ScriptValue.NULL;
                Expression left = unwrapExpr(args.get(0));
                String op = args.get(1).asString();
                Expression right = unwrapExpr(args.get(2));
                BinaryOperator binOp = symbolToBinaryOp(op);
                if (binOp == null) return ScriptValue.NULL;
                return new ASTNodeWrapper(new BinaryExpr(binOp, left, right, PrimitiveSourceType.INT)).toScriptValue();
            })
        ));

        return ScriptValue.object(props);
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
