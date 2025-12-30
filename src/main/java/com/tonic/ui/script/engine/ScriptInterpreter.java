package com.tonic.ui.script.engine;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interpreter for JStudio script language.
 */
public class ScriptInterpreter implements ScriptAST.Visitor<ScriptValue> {

    @Getter
    private final ScriptContext globalContext;

    @Getter
    private ScriptContext currentContext;

    private final List<String> logs = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private Consumer<String> logCallback;
    private Consumer<String> warnCallback;
    private Consumer<String> errorCallback;

    // Used for control flow
    private ScriptValue returnValue = null;
    private boolean returning = false;
    private boolean breaking = false;
    private boolean continuing = false;
    private int loopDepth = 0;

    public ScriptInterpreter() {
        this.globalContext = new ScriptContext();
        this.currentContext = globalContext;
        registerBuiltins();
    }

    private void registerBuiltins() {
        // Logging functions
        globalContext.defineConstant("log", ScriptValue.function(
            ScriptFunction.nativeN("log", args -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(args.get(i).asString());
                }
                log(sb.toString());
                return ScriptValue.NULL;
            })
        ));

        globalContext.defineConstant("warn", ScriptValue.function(
            ScriptFunction.native1("warn", arg -> {
                warn(arg.asString());
                return ScriptValue.NULL;
            })
        ));

        globalContext.defineConstant("error", ScriptValue.function(
            ScriptFunction.native1("error", arg -> {
                error(arg.asString());
                return ScriptValue.NULL;
            })
        ));

        // String functions
        globalContext.defineConstant("String", createStringObject());

        // Utility functions
        globalContext.defineConstant("typeof", ScriptValue.function(
            ScriptFunction.native1("typeof", arg -> {
                return ScriptValue.string(arg.getType().name().toLowerCase());
            })
        ));

        globalContext.defineConstant("parseInt", ScriptValue.function(
            ScriptFunction.native1("parseInt", arg -> {
                try {
                    return ScriptValue.number((int) Double.parseDouble(arg.asString()));
                } catch (NumberFormatException e) {
                    return ScriptValue.number(Double.NaN);
                }
            })
        ));

        globalContext.defineConstant("parseFloat", ScriptValue.function(
            ScriptFunction.native1("parseFloat", arg -> {
                try {
                    return ScriptValue.number(Double.parseDouble(arg.asString()));
                } catch (NumberFormatException e) {
                    return ScriptValue.number(Double.NaN);
                }
            })
        ));
    }

    private ScriptValue createStringObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("fromCharCode", ScriptValue.function(
            ScriptFunction.native1("fromCharCode", arg -> {
                int code = (int) arg.asNumber();
                return ScriptValue.string(String.valueOf((char) code));
            })
        ));

        return ScriptValue.object(props);
    }

    // ==================== Execution ====================

    public ScriptValue execute(List<ScriptAST> statements) {
        ScriptValue result = ScriptValue.NULL;

        for (ScriptAST stmt : statements) {
            result = evaluate(stmt);
            if (returning) {
                returning = false;
                return returnValue;
            }
        }

        return result;
    }

    public ScriptValue executeInContext(ScriptAST node, ScriptContext context) {
        ScriptContext previous = currentContext;
        currentContext = context;
        try {
            if (node instanceof ScriptAST.BlockStmt) {
                return execute(((ScriptAST.BlockStmt) node).getStatements());
            } else {
                // Expression body - return its value
                return evaluate(node);
            }
        } finally {
            currentContext = previous;
        }
    }

    private ScriptValue evaluate(ScriptAST node) {
        if (returning || breaking || continuing) return ScriptValue.NULL;
        return node.accept(this);
    }

    // ==================== Visitors ====================

    @Override
    public ScriptValue visitLiteral(ScriptAST.LiteralExpr expr) {
        return ScriptValue.of(expr.getValue());
    }

    @Override
    public ScriptValue visitIdentifier(ScriptAST.IdentifierExpr expr) {
        return currentContext.get(expr.getName());
    }

    @Override
    public ScriptValue visitBinary(ScriptAST.BinaryExpr expr) {
        String op = expr.getOperator();

        // Short-circuit evaluation for && and ||
        if ("&&".equals(op)) {
            ScriptValue left = evaluate(expr.getLeft());
            if (!left.asBoolean()) return ScriptValue.FALSE;
            return ScriptValue.bool(evaluate(expr.getRight()).asBoolean());
        }

        if ("||".equals(op)) {
            ScriptValue left = evaluate(expr.getLeft());
            if (left.asBoolean()) return left;
            return evaluate(expr.getRight());
        }

        // Assignment
        if ("=".equals(op)) {
            ScriptValue value = evaluate(expr.getRight());

            if (expr.getLeft() instanceof ScriptAST.IdentifierExpr) {
                String name = ((ScriptAST.IdentifierExpr) expr.getLeft()).getName();
                currentContext.set(name, value);
            } else if (expr.getLeft() instanceof ScriptAST.MemberAccessExpr) {
                ScriptAST.MemberAccessExpr member = (ScriptAST.MemberAccessExpr) expr.getLeft();
                ScriptValue obj = evaluate(member.getObject());
                obj.setProperty(member.getMember(), value);
            }

            return value;
        }

        ScriptValue left = evaluate(expr.getLeft());
        ScriptValue right = evaluate(expr.getRight());

        switch (op) {
            case "+": return ScriptValue.add(left, right);
            case "-": return ScriptValue.subtract(left, right);
            case "*": return ScriptValue.multiply(left, right);
            case "/": return ScriptValue.divide(left, right);
            case "%": return ScriptValue.modulo(left, right);
            case "==": return ScriptValue.bool(ScriptValue.equals(left, right));
            case "!=": return ScriptValue.bool(!ScriptValue.equals(left, right));
            case "<": return ScriptValue.bool(ScriptValue.compare(left, right) < 0);
            case "<=": return ScriptValue.bool(ScriptValue.compare(left, right) <= 0);
            case ">": return ScriptValue.bool(ScriptValue.compare(left, right) > 0);
            case ">=": return ScriptValue.bool(ScriptValue.compare(left, right) >= 0);
            default:
                throw new RuntimeException("Unknown binary operator: " + op);
        }
    }

    @Override
    public ScriptValue visitUnary(ScriptAST.UnaryExpr expr) {
        ScriptValue operand = evaluate(expr.getOperand());

        switch (expr.getOperator()) {
            case "-": return ScriptValue.negate(operand);
            case "!": return ScriptValue.not(operand);
            default:
                throw new RuntimeException("Unknown unary operator: " + expr.getOperator());
        }
    }

    @Override
    public ScriptValue visitCall(ScriptAST.CallExpr expr) {
        ScriptValue callee = evaluate(expr.getCallee());

        if (!callee.isFunction()) {
            throw new RuntimeException("Cannot call non-function: " + callee);
        }

        List<ScriptValue> args = new ArrayList<>();
        for (ScriptAST arg : expr.getArguments()) {
            args.add(evaluate(arg));
        }

        ScriptFunction func = callee.asFunction();
        return func.call(this, args);
    }

    @Override
    public ScriptValue visitMemberAccess(ScriptAST.MemberAccessExpr expr) {
        ScriptValue obj = evaluate(expr.getObject());

        // Optional chaining
        if (expr.isOptional() && obj.isNull()) {
            return ScriptValue.NULL;
        }

        // Check for method calls on native string
        if (obj.isString()) {
            return getStringMethod(obj, expr.getMember());
        }

        // Check for method calls on arrays
        if (obj.isArray()) {
            return getArrayMethod(obj, expr.getMember());
        }

        return obj.getProperty(expr.getMember());
    }

    private ScriptValue getStringMethod(ScriptValue strVal, String method) {
        String s = strVal.asString();

        switch (method) {
            case "length":
                return ScriptValue.number(s.length());
            case "toLowerCase":
                return ScriptValue.function(ScriptFunction.native0("toLowerCase",
                    () -> ScriptValue.string(s.toLowerCase())));
            case "toUpperCase":
                return ScriptValue.function(ScriptFunction.native0("toUpperCase",
                    () -> ScriptValue.string(s.toUpperCase())));
            case "trim":
                return ScriptValue.function(ScriptFunction.native0("trim",
                    () -> ScriptValue.string(s.trim())));
            case "startsWith":
                return ScriptValue.function(ScriptFunction.native1("startsWith",
                    arg -> ScriptValue.bool(s.startsWith(arg.asString()))));
            case "endsWith":
                return ScriptValue.function(ScriptFunction.native1("endsWith",
                    arg -> ScriptValue.bool(s.endsWith(arg.asString()))));
            case "includes":
            case "contains":
                return ScriptValue.function(ScriptFunction.native1("includes",
                    arg -> ScriptValue.bool(s.contains(arg.asString()))));
            case "indexOf":
                return ScriptValue.function(ScriptFunction.native1("indexOf",
                    arg -> ScriptValue.number(s.indexOf(arg.asString()))));
            case "substring":
                return ScriptValue.function(ScriptFunction.native2("substring",
                    (start, end) -> {
                        int startIdx = (int) start.asNumber();
                        if (end.isNull()) {
                            return ScriptValue.string(s.substring(startIdx));
                        }
                        int endIdx = (int) end.asNumber();
                        return ScriptValue.string(s.substring(startIdx, endIdx));
                    }));
            case "replace":
                return ScriptValue.function(ScriptFunction.native2("replace",
                    (search, replacement) ->
                        ScriptValue.string(s.replace(search.asString(), replacement.asString()))));
            case "split":
                return ScriptValue.function(ScriptFunction.native1("split",
                    arg -> {
                        String[] parts = s.split(java.util.regex.Pattern.quote(arg.asString()));
                        List<ScriptValue> list = new ArrayList<>();
                        for (String part : parts) {
                            list.add(ScriptValue.string(part));
                        }
                        return ScriptValue.array(list);
                    }));
            default:
                return ScriptValue.NULL;
        }
    }

    private ScriptValue getArrayMethod(ScriptValue arrVal, String method) {
        List<ScriptValue> arr = arrVal.asArray();
        ScriptInterpreter interp = this;

        switch (method) {
            case "length":
                return ScriptValue.number(arr.size());

            case "push":
                return ScriptValue.function(ScriptFunction.nativeN("push", args -> {
                    for (ScriptValue arg : args) {
                        arr.add(arg);
                    }
                    return ScriptValue.number(arr.size());
                }));

            case "pop":
                return ScriptValue.function(ScriptFunction.native0("pop", () -> {
                    if (arr.isEmpty()) return ScriptValue.NULL;
                    return arr.remove(arr.size() - 1);
                }));

            case "shift":
                return ScriptValue.function(ScriptFunction.native0("shift", () -> {
                    if (arr.isEmpty()) return ScriptValue.NULL;
                    return arr.remove(0);
                }));

            case "unshift":
                return ScriptValue.function(ScriptFunction.nativeN("unshift", args -> {
                    for (int i = args.size() - 1; i >= 0; i--) {
                        arr.add(0, args.get(i));
                    }
                    return ScriptValue.number(arr.size());
                }));

            case "indexOf":
                return ScriptValue.function(ScriptFunction.native1("indexOf", target -> {
                    for (int i = 0; i < arr.size(); i++) {
                        if (ScriptValue.equals(arr.get(i), target)) {
                            return ScriptValue.number(i);
                        }
                    }
                    return ScriptValue.number(-1);
                }));

            case "includes":
                return ScriptValue.function(ScriptFunction.native1("includes", target -> {
                    for (ScriptValue item : arr) {
                        if (ScriptValue.equals(item, target)) {
                            return ScriptValue.TRUE;
                        }
                    }
                    return ScriptValue.FALSE;
                }));

            case "join":
                return ScriptValue.function(ScriptFunction.native1("join", separator -> {
                    StringBuilder sb = new StringBuilder();
                    String sep = separator.isNull() ? "," : separator.asString();
                    for (int i = 0; i < arr.size(); i++) {
                        if (i > 0) sb.append(sep);
                        sb.append(arr.get(i).asString());
                    }
                    return ScriptValue.string(sb.toString());
                }));

            case "slice":
                return ScriptValue.function(ScriptFunction.native2("slice", (startVal, endVal) -> {
                    int start = (int) startVal.asNumber();
                    int end = endVal.isNull() ? arr.size() : (int) endVal.asNumber();
                    if (start < 0) start = Math.max(0, arr.size() + start);
                    if (end < 0) end = Math.max(0, arr.size() + end);
                    start = Math.min(start, arr.size());
                    end = Math.min(end, arr.size());
                    List<ScriptValue> result = new ArrayList<>(arr.subList(start, end));
                    return ScriptValue.array(result);
                }));

            case "concat":
                return ScriptValue.function(ScriptFunction.nativeN("concat", args -> {
                    List<ScriptValue> result = new ArrayList<>(arr);
                    for (ScriptValue arg : args) {
                        if (arg.isArray()) {
                            result.addAll(arg.asArray());
                        } else {
                            result.add(arg);
                        }
                    }
                    return ScriptValue.array(result);
                }));

            case "reverse":
                return ScriptValue.function(ScriptFunction.native0("reverse", () -> {
                    java.util.Collections.reverse(arr);
                    return arrVal;
                }));

            case "forEach":
                return ScriptValue.function(ScriptFunction.native1("forEach", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("forEach requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        fn.call(interp, args);
                    }
                    return ScriptValue.NULL;
                }));

            case "map":
                return ScriptValue.function(ScriptFunction.native1("map", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("map requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    List<ScriptValue> result = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        result.add(fn.call(interp, args));
                    }
                    return ScriptValue.array(result);
                }));

            case "filter":
                return ScriptValue.function(ScriptFunction.native1("filter", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("filter requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    List<ScriptValue> result = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        if (fn.call(interp, args).asBoolean()) {
                            result.add(arr.get(i));
                        }
                    }
                    return ScriptValue.array(result);
                }));

            case "find":
                return ScriptValue.function(ScriptFunction.native1("find", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("find requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        if (fn.call(interp, args).asBoolean()) {
                            return arr.get(i);
                        }
                    }
                    return ScriptValue.NULL;
                }));

            case "findIndex":
                return ScriptValue.function(ScriptFunction.native1("findIndex", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("findIndex requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        if (fn.call(interp, args).asBoolean()) {
                            return ScriptValue.number(i);
                        }
                    }
                    return ScriptValue.number(-1);
                }));

            case "some":
                return ScriptValue.function(ScriptFunction.native1("some", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("some requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        if (fn.call(interp, args).asBoolean()) {
                            return ScriptValue.TRUE;
                        }
                    }
                    return ScriptValue.FALSE;
                }));

            case "every":
                return ScriptValue.function(ScriptFunction.native1("every", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("every requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        if (!fn.call(interp, args).asBoolean()) {
                            return ScriptValue.FALSE;
                        }
                    }
                    return ScriptValue.TRUE;
                }));

            case "reduce":
                return ScriptValue.function(ScriptFunction.native2("reduce", (callback, initial) -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("reduce requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    ScriptValue accumulator;
                    int startIdx;
                    if (initial.isNull() && arr.isEmpty()) {
                        throw new RuntimeException("reduce of empty array with no initial value");
                    } else if (initial.isNull()) {
                        accumulator = arr.get(0);
                        startIdx = 1;
                    } else {
                        accumulator = initial;
                        startIdx = 0;
                    }
                    for (int i = startIdx; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(accumulator);
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        accumulator = fn.call(interp, args);
                    }
                    return accumulator;
                }));

            case "flat":
                return ScriptValue.function(ScriptFunction.native1("flat", depthVal -> {
                    int depth = depthVal.isNull() ? 1 : (int) depthVal.asNumber();
                    List<ScriptValue> result = flattenArray(arr, depth);
                    return ScriptValue.array(result);
                }));

            case "flatMap":
                return ScriptValue.function(ScriptFunction.native1("flatMap", callback -> {
                    if (!callback.isFunction()) {
                        throw new RuntimeException("flatMap requires a function argument");
                    }
                    ScriptFunction fn = callback.asFunction();
                    List<ScriptValue> result = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        List<ScriptValue> args = new ArrayList<>();
                        args.add(arr.get(i));
                        args.add(ScriptValue.number(i));
                        args.add(arrVal);
                        ScriptValue mapped = fn.call(interp, args);
                        if (mapped.isArray()) {
                            result.addAll(mapped.asArray());
                        } else {
                            result.add(mapped);
                        }
                    }
                    return ScriptValue.array(result);
                }));

            default:
                return ScriptValue.NULL;
        }
    }

    private List<ScriptValue> flattenArray(List<ScriptValue> arr, int depth) {
        List<ScriptValue> result = new ArrayList<>();
        for (ScriptValue item : arr) {
            if (depth > 0 && item.isArray()) {
                result.addAll(flattenArray(item.asArray(), depth - 1));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public ScriptValue visitArrowFunction(ScriptAST.ArrowFunctionExpr expr) {
        return ScriptValue.function(new ScriptFunction.UserFunction(
            expr.getParameters(),
            expr.getBody(),
            currentContext
        ));
    }

    @Override
    public ScriptValue visitArrayAccess(ScriptAST.ArrayAccessExpr expr) {
        ScriptValue arr = evaluate(expr.getArray());
        ScriptValue idx = evaluate(expr.getIndex());

        if (arr.isArray()) {
            int index = (int) idx.asNumber();
            List<ScriptValue> list = arr.asArray();
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            }
            return ScriptValue.NULL;
        }

        if (arr.isString()) {
            int index = (int) idx.asNumber();
            String s = arr.asString();
            if (index >= 0 && index < s.length()) {
                return ScriptValue.string(String.valueOf(s.charAt(index)));
            }
            return ScriptValue.NULL;
        }

        if (arr.isObject()) {
            return arr.getProperty(idx.asString());
        }

        return ScriptValue.NULL;
    }

    @Override
    public ScriptValue visitTernary(ScriptAST.TernaryExpr expr) {
        ScriptValue condition = evaluate(expr.getCondition());
        if (condition.asBoolean()) {
            return evaluate(expr.getThenBranch());
        } else {
            return evaluate(expr.getElseBranch());
        }
    }

    @Override
    public ScriptValue visitExpressionStmt(ScriptAST.ExpressionStmt stmt) {
        return evaluate(stmt.getExpression());
    }

    @Override
    public ScriptValue visitVarDecl(ScriptAST.VarDeclStmt stmt) {
        ScriptValue value = stmt.getInitializer() != null
            ? evaluate(stmt.getInitializer())
            : ScriptValue.NULL;

        if (stmt.isConstant()) {
            currentContext.defineConstant(stmt.getName(), value);
        } else {
            currentContext.define(stmt.getName(), value);
        }

        return value;
    }

    @Override
    public ScriptValue visitIf(ScriptAST.IfStmt stmt) {
        ScriptValue condition = evaluate(stmt.getCondition());

        if (condition.asBoolean()) {
            return evaluate(stmt.getThenBranch());
        } else if (stmt.getElseBranch() != null) {
            return evaluate(stmt.getElseBranch());
        }

        return ScriptValue.NULL;
    }

    @Override
    public ScriptValue visitReturn(ScriptAST.ReturnStmt stmt) {
        returnValue = stmt.getValue() != null
            ? evaluate(stmt.getValue())
            : ScriptValue.NULL;
        returning = true;
        return returnValue;
    }

    @Override
    public ScriptValue visitBlock(ScriptAST.BlockStmt stmt) {
        ScriptContext previous = currentContext;
        currentContext = currentContext.child();
        try {
            return execute(stmt.getStatements());
        } finally {
            currentContext = previous;
        }
    }

    // ==================== Loop Visitors ====================

    @Override
    public ScriptValue visitWhile(ScriptAST.WhileStmt stmt) {
        loopDepth++;
        try {
            while (evaluate(stmt.getCondition()).asBoolean()) {
                evaluate(stmt.getBody());
                if (returning || breaking) {
                    breaking = false;
                    break;
                }
                if (continuing) {
                    continuing = false;
                }
            }
        } finally {
            loopDepth--;
        }
        return ScriptValue.NULL;
    }

    @Override
    public ScriptValue visitFor(ScriptAST.ForStmt stmt) {
        ScriptContext previous = currentContext;
        currentContext = currentContext.child();
        loopDepth++;
        try {
            if (stmt.getInit() != null) {
                evaluate(stmt.getInit());
            }

            while (stmt.getCondition() == null || evaluate(stmt.getCondition()).asBoolean()) {
                evaluate(stmt.getBody());
                if (returning || breaking) {
                    breaking = false;
                    break;
                }
                if (continuing) {
                    continuing = false;
                }
                if (stmt.getUpdate() != null) {
                    evaluate(stmt.getUpdate());
                }
            }
        } finally {
            loopDepth--;
            currentContext = previous;
        }
        return ScriptValue.NULL;
    }

    @Override
    public ScriptValue visitForEach(ScriptAST.ForEachStmt stmt) {
        ScriptValue iterable = evaluate(stmt.getIterable());
        ScriptContext previous = currentContext;
        loopDepth++;

        try {
            if (iterable.isArray()) {
                List<ScriptValue> list = iterable.asArray();
                if (stmt.isForIn()) {
                    for (int i = 0; i < list.size(); i++) {
                        currentContext = previous.child();
                        if (stmt.isConstant()) {
                            currentContext.defineConstant(stmt.getVarName(), ScriptValue.number(i));
                        } else {
                            currentContext.define(stmt.getVarName(), ScriptValue.number(i));
                        }
                        evaluate(stmt.getBody());
                        if (returning || breaking) {
                            breaking = false;
                            break;
                        }
                        if (continuing) {
                            continuing = false;
                        }
                    }
                } else {
                    for (ScriptValue item : list) {
                        currentContext = previous.child();
                        if (stmt.isConstant()) {
                            currentContext.defineConstant(stmt.getVarName(), item);
                        } else {
                            currentContext.define(stmt.getVarName(), item);
                        }
                        evaluate(stmt.getBody());
                        if (returning || breaking) {
                            breaking = false;
                            break;
                        }
                        if (continuing) {
                            continuing = false;
                        }
                    }
                }
            } else if (iterable.isObject()) {
                Map<String, ScriptValue> obj = iterable.asObject();
                if (stmt.isForIn()) {
                    for (String key : obj.keySet()) {
                        currentContext = previous.child();
                        if (stmt.isConstant()) {
                            currentContext.defineConstant(stmt.getVarName(), ScriptValue.string(key));
                        } else {
                            currentContext.define(stmt.getVarName(), ScriptValue.string(key));
                        }
                        evaluate(stmt.getBody());
                        if (returning || breaking) {
                            breaking = false;
                            break;
                        }
                        if (continuing) {
                            continuing = false;
                        }
                    }
                } else {
                    for (ScriptValue value : obj.values()) {
                        currentContext = previous.child();
                        if (stmt.isConstant()) {
                            currentContext.defineConstant(stmt.getVarName(), value);
                        } else {
                            currentContext.define(stmt.getVarName(), value);
                        }
                        evaluate(stmt.getBody());
                        if (returning || breaking) {
                            breaking = false;
                            break;
                        }
                        if (continuing) {
                            continuing = false;
                        }
                    }
                }
            } else if (iterable.isString()) {
                String s = iterable.asString();
                for (int i = 0; i < s.length(); i++) {
                    currentContext = previous.child();
                    ScriptValue value = stmt.isForIn()
                        ? ScriptValue.number(i)
                        : ScriptValue.string(String.valueOf(s.charAt(i)));
                    if (stmt.isConstant()) {
                        currentContext.defineConstant(stmt.getVarName(), value);
                    } else {
                        currentContext.define(stmt.getVarName(), value);
                    }
                    evaluate(stmt.getBody());
                    if (returning || breaking) {
                        breaking = false;
                        break;
                    }
                    if (continuing) {
                        continuing = false;
                    }
                }
            }
        } finally {
            loopDepth--;
            currentContext = previous;
        }
        return ScriptValue.NULL;
    }

    @Override
    public ScriptValue visitBreak(ScriptAST.BreakStmt stmt) {
        if (loopDepth == 0) {
            throw new RuntimeException("'break' outside of loop");
        }
        breaking = true;
        return ScriptValue.NULL;
    }

    @Override
    public ScriptValue visitContinue(ScriptAST.ContinueStmt stmt) {
        if (loopDepth == 0) {
            throw new RuntimeException("'continue' outside of loop");
        }
        continuing = true;
        return ScriptValue.NULL;
    }

    // ==================== Exception Visitors ====================

    @Override
    public ScriptValue visitTry(ScriptAST.TryStmt stmt) {
        ScriptValue result = ScriptValue.NULL;
        Throwable caught = null;

        try {
            result = evaluate(stmt.getTryBlock());
        } catch (Throwable e) {
            caught = e;
        }

        if (caught != null && stmt.getCatchBlock() != null) {
            ScriptContext previous = currentContext;
            currentContext = currentContext.child();
            try {
                String message = caught.getMessage() != null ? caught.getMessage() : caught.toString();
                currentContext.define(stmt.getCatchParam(), ScriptValue.string(message));
                result = evaluate(stmt.getCatchBlock());
            } finally {
                currentContext = previous;
            }
        }

        if (stmt.getFinallyBlock() != null) {
            evaluate(stmt.getFinallyBlock());
        }

        return result;
    }

    @Override
    public ScriptValue visitThrow(ScriptAST.ThrowStmt stmt) {
        ScriptValue value = evaluate(stmt.getExpression());
        throw new ScriptException(value.asString());
    }

    // ==================== Update Expression Visitor ====================

    @Override
    public ScriptValue visitUpdate(ScriptAST.UpdateExpr expr) {
        ScriptAST operand = expr.getOperand();
        String op = expr.getOperator();
        boolean prefix = expr.isPrefix();

        if (operand instanceof ScriptAST.IdentifierExpr) {
            String name = ((ScriptAST.IdentifierExpr) operand).getName();
            ScriptValue current = currentContext.get(name);
            double value = current.asNumber();
            double newValue = "++".equals(op) ? value + 1 : value - 1;
            currentContext.set(name, ScriptValue.number(newValue));
            return ScriptValue.number(prefix ? newValue : value);
        } else if (operand instanceof ScriptAST.MemberAccessExpr) {
            ScriptAST.MemberAccessExpr member = (ScriptAST.MemberAccessExpr) operand;
            ScriptValue obj = evaluate(member.getObject());
            ScriptValue current = obj.getProperty(member.getMember());
            double value = current.asNumber();
            double newValue = "++".equals(op) ? value + 1 : value - 1;
            obj.setProperty(member.getMember(), ScriptValue.number(newValue));
            return ScriptValue.number(prefix ? newValue : value);
        } else if (operand instanceof ScriptAST.ArrayAccessExpr) {
            ScriptAST.ArrayAccessExpr arr = (ScriptAST.ArrayAccessExpr) operand;
            ScriptValue array = evaluate(arr.getArray());
            ScriptValue idx = evaluate(arr.getIndex());
            int index = (int) idx.asNumber();
            if (array.isArray()) {
                List<ScriptValue> list = array.asArray();
                double value = list.get(index).asNumber();
                double newValue = "++".equals(op) ? value + 1 : value - 1;
                list.set(index, ScriptValue.number(newValue));
                return ScriptValue.number(prefix ? newValue : value);
            }
        }

        throw new RuntimeException("Invalid operand for " + op);
    }

    // ==================== Compound Assignment Visitor ====================

    @Override
    public ScriptValue visitAssignment(ScriptAST.AssignmentExpr expr) {
        ScriptAST target = expr.getTarget();
        String op = expr.getOperator();
        ScriptValue right = evaluate(expr.getValue());

        ScriptValue current;
        if (target instanceof ScriptAST.IdentifierExpr) {
            String name = ((ScriptAST.IdentifierExpr) target).getName();
            current = currentContext.get(name);
            ScriptValue result = applyCompoundOp(current, op, right);
            currentContext.set(name, result);
            return result;
        } else if (target instanceof ScriptAST.MemberAccessExpr) {
            ScriptAST.MemberAccessExpr member = (ScriptAST.MemberAccessExpr) target;
            ScriptValue obj = evaluate(member.getObject());
            current = obj.getProperty(member.getMember());
            ScriptValue result = applyCompoundOp(current, op, right);
            obj.setProperty(member.getMember(), result);
            return result;
        } else if (target instanceof ScriptAST.ArrayAccessExpr) {
            ScriptAST.ArrayAccessExpr arr = (ScriptAST.ArrayAccessExpr) target;
            ScriptValue array = evaluate(arr.getArray());
            ScriptValue idx = evaluate(arr.getIndex());
            int index = (int) idx.asNumber();
            if (array.isArray()) {
                List<ScriptValue> list = array.asArray();
                current = list.get(index);
                ScriptValue result = applyCompoundOp(current, op, right);
                list.set(index, result);
                return result;
            }
        }

        throw new RuntimeException("Invalid compound assignment target");
    }

    private ScriptValue applyCompoundOp(ScriptValue left, String op, ScriptValue right) {
        switch (op) {
            case "+=": return ScriptValue.add(left, right);
            case "-=": return ScriptValue.subtract(left, right);
            case "*=": return ScriptValue.multiply(left, right);
            case "/=": return ScriptValue.divide(left, right);
            default:
                throw new RuntimeException("Unknown compound operator: " + op);
        }
    }

    // ==================== Script Exception ====================

    public static class ScriptException extends RuntimeException {
        public ScriptException(String message) {
            super(message);
        }
    }

    // ==================== Logging ====================

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    public void setWarnCallback(Consumer<String> callback) {
        this.warnCallback = callback;
    }

    public void setErrorCallback(Consumer<String> callback) {
        this.errorCallback = callback;
    }

    private void log(String message) {
        logs.add(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    private void warn(String message) {
        warnings.add(message);
        if (warnCallback != null) {
            warnCallback.accept("WARN: " + message);
        }
    }

    private void error(String message) {
        errors.add(message);
        if (errorCallback != null) {
            errorCallback.accept("ERROR: " + message);
        }
    }

    public List<String> getLogs() {
        return logs;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void clearLogs() {
        logs.clear();
        warnings.clear();
        errors.clear();
    }
}
