package com.tonic.ui.script.bridge;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge for bytecode instrumentation.
 * Exposes an 'instrument' global object for modifying IR.
 */
public class InstrumentationBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private final IRBridge irBridge;
    private Consumer<String> logCallback;

    private final List<InstrumentationRule> rules = new ArrayList<>();

    public InstrumentationBridge(ScriptInterpreter interpreter, ProjectModel projectModel, IRBridge irBridge) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
        this.irBridge = irBridge;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public ScriptValue createInstrumentObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("beforeMethod", ScriptValue.function(
            ScriptFunction.native1("beforeMethod", this::beforeMethod)
        ));

        props.put("afterMethod", ScriptValue.function(
            ScriptFunction.native1("afterMethod", this::afterMethod)
        ));

        props.put("beforeCall", ScriptValue.function(
            ScriptFunction.native1("beforeCall", this::beforeCall)
        ));

        props.put("afterCall", ScriptValue.function(
            ScriptFunction.native1("afterCall", this::afterCall)
        ));

        props.put("replaceCall", ScriptValue.function(
            ScriptFunction.native1("replaceCall", this::replaceCall)
        ));

        props.put("beforeFieldRead", ScriptValue.function(
            ScriptFunction.native1("beforeFieldRead", this::beforeFieldRead)
        ));

        props.put("afterFieldWrite", ScriptValue.function(
            ScriptFunction.native1("afterFieldWrite", this::afterFieldWrite)
        ));

        props.put("removeInstruction", ScriptValue.function(
            ScriptFunction.native1("removeInstruction", this::removeInstruction)
        ));

        props.put("replaceConstant", ScriptValue.function(
            ScriptFunction.native1("replaceConstant", this::replaceConstant)
        ));

        props.put("apply", ScriptValue.function(
            ScriptFunction.native1("apply", this::applyRules)
        ));

        props.put("clearRules", ScriptValue.function(
            ScriptFunction.native0("clearRules", this::clearRules)
        ));

        props.put("getRuleCount", ScriptValue.function(
            ScriptFunction.native0("getRuleCount", () -> ScriptValue.number(rules.size()))
        ));

        props.put("modifyInstruction", ScriptValue.function(
            ScriptFunction.native1("modifyInstruction", this::modifyInstruction)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue beforeMethod(ScriptValue config) {
        if (!config.isObject()) {
            log("beforeMethod requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        ScriptFunction filter = cfg.containsKey("filter") && cfg.get("filter").isFunction() ?
            cfg.get("filter").asFunction() : null;
        ScriptFunction inject = cfg.containsKey("inject") && cfg.get("inject").isFunction() ?
            cfg.get("inject").asFunction() : null;

        if (inject == null) {
            log("beforeMethod requires 'inject' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.BEFORE_METHOD, filter, inject, null));
        log("Added beforeMethod rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue afterMethod(ScriptValue config) {
        if (!config.isObject()) {
            log("afterMethod requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        ScriptFunction filter = cfg.containsKey("filter") && cfg.get("filter").isFunction() ?
            cfg.get("filter").asFunction() : null;
        ScriptFunction inject = cfg.containsKey("inject") && cfg.get("inject").isFunction() ?
            cfg.get("inject").asFunction() : null;

        if (inject == null) {
            log("afterMethod requires 'inject' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.AFTER_METHOD, filter, inject, null));
        log("Added afterMethod rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue beforeCall(ScriptValue config) {
        if (!config.isObject()) {
            log("beforeCall requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        String target = cfg.containsKey("target") ? cfg.get("target").asString() : null;
        ScriptFunction inject = cfg.containsKey("inject") && cfg.get("inject").isFunction() ?
            cfg.get("inject").asFunction() : null;

        if (inject == null) {
            log("beforeCall requires 'inject' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.BEFORE_CALL, null, inject, target));
        log("Added beforeCall rule" + (target != null ? " for " + target : ""));
        return ScriptValue.bool(true);
    }

    private ScriptValue afterCall(ScriptValue config) {
        if (!config.isObject()) {
            log("afterCall requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        String target = cfg.containsKey("target") ? cfg.get("target").asString() : null;
        ScriptFunction inject = cfg.containsKey("inject") && cfg.get("inject").isFunction() ?
            cfg.get("inject").asFunction() : null;

        if (inject == null) {
            log("afterCall requires 'inject' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.AFTER_CALL, null, inject, target));
        log("Added afterCall rule" + (target != null ? " for " + target : ""));
        return ScriptValue.bool(true);
    }

    private ScriptValue replaceCall(ScriptValue config) {
        if (!config.isObject()) {
            log("replaceCall requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        String target = cfg.containsKey("target") ? cfg.get("target").asString() : null;
        ScriptFunction replacement = cfg.containsKey("with") && cfg.get("with").isFunction() ?
            cfg.get("with").asFunction() : null;

        if (replacement == null) {
            log("replaceCall requires 'with' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.REPLACE_CALL, null, replacement, target));
        log("Added replaceCall rule" + (target != null ? " for " + target : ""));
        return ScriptValue.bool(true);
    }

    private ScriptValue beforeFieldRead(ScriptValue config) {
        if (!config.isObject()) {
            log("beforeFieldRead requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        String target = cfg.containsKey("target") ? cfg.get("target").asString() : null;
        ScriptFunction inject = cfg.containsKey("inject") && cfg.get("inject").isFunction() ?
            cfg.get("inject").asFunction() : null;

        if (inject == null) {
            log("beforeFieldRead requires 'inject' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.BEFORE_FIELD_READ, null, inject, target));
        log("Added beforeFieldRead rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue afterFieldWrite(ScriptValue config) {
        if (!config.isObject()) {
            log("afterFieldWrite requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        String target = cfg.containsKey("target") ? cfg.get("target").asString() : null;
        ScriptFunction inject = cfg.containsKey("inject") && cfg.get("inject").isFunction() ?
            cfg.get("inject").asFunction() : null;

        if (inject == null) {
            log("afterFieldWrite requires 'inject' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.AFTER_FIELD_WRITE, null, inject, target));
        log("Added afterFieldWrite rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue removeInstruction(ScriptValue config) {
        if (!config.isObject()) {
            log("removeInstruction requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        ScriptFunction filter = cfg.containsKey("filter") && cfg.get("filter").isFunction() ?
            cfg.get("filter").asFunction() : null;

        if (filter == null) {
            log("removeInstruction requires 'filter' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.REMOVE, filter, null, null));
        log("Added removeInstruction rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue replaceConstant(ScriptValue config) {
        if (!config.isObject()) {
            log("replaceConstant requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        ScriptFunction filter = cfg.containsKey("filter") && cfg.get("filter").isFunction() ?
            cfg.get("filter").asFunction() : null;
        ScriptFunction replacement = cfg.containsKey("with") && cfg.get("with").isFunction() ?
            cfg.get("with").asFunction() : null;

        if (replacement == null) {
            log("replaceConstant requires 'with' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.REPLACE_CONSTANT, filter, replacement, null));
        log("Added replaceConstant rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue modifyInstruction(ScriptValue config) {
        if (!config.isObject()) {
            log("modifyInstruction requires a config object");
            return ScriptValue.bool(false);
        }

        Map<String, ScriptValue> cfg = config.asObject();
        ScriptFunction filter = cfg.containsKey("filter") && cfg.get("filter").isFunction() ?
            cfg.get("filter").asFunction() : null;
        ScriptFunction modifier = cfg.containsKey("modify") && cfg.get("modify").isFunction() ?
            cfg.get("modify").asFunction() : null;

        if (modifier == null) {
            log("modifyInstruction requires 'modify' function");
            return ScriptValue.bool(false);
        }

        rules.add(new InstrumentationRule(RuleType.MODIFY, filter, modifier, null));
        log("Added modifyInstruction rule");
        return ScriptValue.bool(true);
    }

    private ScriptValue applyRules(ScriptValue methodRef) {
        String className;
        String methodName;
        String methodDesc = null;

        if (methodRef.isString()) {
            String s = methodRef.asString();
            int dotIdx = s.lastIndexOf('.');
            if (dotIdx > 0) {
                className = s.substring(0, dotIdx).replace('.', '/');
                String rest = s.substring(dotIdx + 1);
                int parenIdx = rest.indexOf('(');
                if (parenIdx > 0) {
                    methodName = rest.substring(0, parenIdx);
                    methodDesc = rest.substring(parenIdx);
                } else {
                    methodName = rest;
                }
            } else {
                log("Invalid method reference: " + s);
                return ScriptValue.number(0);
            }
        } else if (methodRef.isObject()) {
            Map<String, ScriptValue> obj = methodRef.asObject();
            className = obj.containsKey("className") ? obj.get("className").asString() : null;
            methodName = obj.containsKey("name") ? obj.get("name").asString() : null;
            methodDesc = obj.containsKey("desc") ? obj.get("desc").asString() : null;
        } else {
            log("Invalid method reference type");
            return ScriptValue.number(0);
        }

        if (className == null || methodName == null) {
            log("Missing class or method name");
            return ScriptValue.number(0);
        }

        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) {
            log("Class not found: " + className);
            return ScriptValue.number(0);
        }

        MethodEntry targetMethod = null;
        for (MethodEntryModel mm : classEntry.getMethods()) {
            MethodEntry m = mm.getMethodEntry();
            if (m.getName().equals(methodName)) {
                if (methodDesc == null || m.getDesc().equals(methodDesc)) {
                    targetMethod = m;
                    break;
                }
            }
        }

        if (targetMethod == null || targetMethod.getCodeAttribute() == null) {
            log("Method not found or has no code: " + methodName);
            return ScriptValue.number(0);
        }

        try {
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(targetMethod);

            if (irMethod == null || irMethod.getEntryBlock() == null) {
                log("Failed to lift method to IR");
                return ScriptValue.number(0);
            }

            int modifications = applyRulesToMethod(irMethod);
            log("Applied " + modifications + " modifications to " + methodName);
            return ScriptValue.number(modifications);
        } catch (Exception e) {
            log("Error applying rules: " + e.getMessage());
            return ScriptValue.number(0);
        }
    }

    private int applyRulesToMethod(IRMethod method) {
        int modCount = 0;

        for (InstrumentationRule rule : rules) {
            switch (rule.type) {
                case BEFORE_CALL:
                case AFTER_CALL:
                case REPLACE_CALL:
                    modCount += applyCallRule(method, rule);
                    break;
                case BEFORE_FIELD_READ:
                case AFTER_FIELD_WRITE:
                    modCount += applyFieldRule(method, rule);
                    break;
                case REMOVE:
                    modCount += applyRemoveRule(method, rule);
                    break;
                case REPLACE_CONSTANT:
                    modCount += applyConstantRule(method, rule);
                    break;
                case MODIFY:
                    modCount += applyModifyRule(method, rule);
                    break;
            }
        }

        return modCount;
    }

    private int applyCallRule(IRMethod method, InstrumentationRule rule) {
        int count = 0;
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : new ArrayList<>(block.getInstructions())) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    if (matchesTarget(invoke, rule.targetPattern)) {
                        ScriptValue instrWrapper = wrapInvoke(invoke);
                        callCallback(rule.action, instrWrapper);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int applyFieldRule(IRMethod method, InstrumentationRule rule) {
        int count = 0;
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : new ArrayList<>(block.getInstructions())) {
                if (rule.type == RuleType.BEFORE_FIELD_READ && instr instanceof GetFieldInstruction) {
                    GetFieldInstruction field = (GetFieldInstruction) instr;
                    if (matchesFieldTarget(field.getOwner(), field.getName(), rule.targetPattern)) {
                        ScriptValue instrWrapper = wrapField(field);
                        callCallback(rule.action, instrWrapper);
                        count++;
                    }
                } else if (rule.type == RuleType.AFTER_FIELD_WRITE && instr instanceof PutFieldInstruction) {
                    PutFieldInstruction field = (PutFieldInstruction) instr;
                    if (matchesFieldTarget(field.getOwner(), field.getName(), rule.targetPattern)) {
                        ScriptValue instrWrapper = wrapField(field);
                        callCallback(rule.action, instrWrapper);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int applyRemoveRule(IRMethod method, InstrumentationRule rule) {
        int count = 0;
        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> toRemove = new ArrayList<>();
            for (IRInstruction instr : block.getInstructions()) {
                ScriptValue wrapped = wrapInstruction(instr);
                if (matchesFilter(rule.filter, wrapped)) {
                    toRemove.add(instr);
                }
            }
            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
                count++;
            }
        }
        return count;
    }

    private int applyConstantRule(IRMethod method, InstrumentationRule rule) {
        int count = 0;
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof ConstantInstruction) {
                    ScriptValue wrapped = wrapConstant((ConstantInstruction) instr);
                    if (matchesFilter(rule.filter, wrapped)) {
                        callCallback(rule.action, wrapped);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int applyModifyRule(IRMethod method, InstrumentationRule rule) {
        int count = 0;
        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                ScriptValue wrapped = wrapInstruction(instr);
                if (matchesFilter(rule.filter, wrapped)) {
                    callCallback(rule.action, wrapped);
                    count++;
                }
            }
        }
        return count;
    }

    private boolean matchesTarget(InvokeInstruction invoke, String pattern) {
        if (pattern == null) return true;
        String fullName = invoke.getOwner() + "." + invoke.getName();
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return fullName.matches(regex);
        }
        return fullName.contains(pattern);
    }

    private boolean matchesFieldTarget(String owner, String name, String pattern) {
        if (pattern == null) return true;
        String fullName = owner + "." + name;
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return fullName.matches(regex);
        }
        return fullName.contains(pattern);
    }

    private boolean matchesFilter(ScriptFunction filter, ScriptValue wrapped) {
        if (filter == null) return true;
        try {
            List<ScriptValue> args = new ArrayList<>();
            args.add(wrapped);
            return filter.call(interpreter, args).asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private void callCallback(ScriptFunction fn, ScriptValue arg) {
        if (fn == null) return;
        try {
            List<ScriptValue> args = new ArrayList<>();
            args.add(arg);
            fn.call(interpreter, args);
        } catch (Exception e) {
            log("Callback error: " + e.getMessage());
        }
    }

    private ScriptValue clearRules() {
        rules.clear();
        log("Cleared all instrumentation rules");
        return ScriptValue.NULL;
    }

    private ScriptValue wrapInvoke(InvokeInstruction invoke) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("type", ScriptValue.string("InvokeInstruction"));
        props.put("owner", ScriptValue.string(invoke.getOwner()));
        props.put("name", ScriptValue.string(invoke.getName()));
        props.put("descriptor", ScriptValue.string(invoke.getDescriptor()));
        props.put("invokeType", ScriptValue.string(invoke.getInvokeType().name()));
        return ScriptValue.object(props);
    }

    private ScriptValue wrapField(Object field) {
        Map<String, ScriptValue> props = new HashMap<>();
        if (field instanceof GetFieldInstruction) {
            GetFieldInstruction gf = (GetFieldInstruction) field;
            props.put("type", ScriptValue.string("GetFieldInstruction"));
            props.put("owner", ScriptValue.string(gf.getOwner()));
            props.put("name", ScriptValue.string(gf.getName()));
            props.put("isStatic", ScriptValue.bool(gf.isStatic()));
        } else if (field instanceof PutFieldInstruction) {
            PutFieldInstruction pf = (PutFieldInstruction) field;
            props.put("type", ScriptValue.string("PutFieldInstruction"));
            props.put("owner", ScriptValue.string(pf.getOwner()));
            props.put("name", ScriptValue.string(pf.getName()));
            props.put("isStatic", ScriptValue.bool(pf.isStatic()));
        }
        return ScriptValue.object(props);
    }

    private ScriptValue wrapInstruction(IRInstruction instr) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("type", ScriptValue.string(instr.getClass().getSimpleName()));
        return ScriptValue.object(props);
    }

    private ScriptValue wrapConstant(ConstantInstruction instr) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("type", ScriptValue.string("ConstantInstruction"));
        Constant c = instr.getConstant();
        if (c instanceof IntConstant) {
            props.put("value", ScriptValue.number(((IntConstant) c).getValue()));
            props.put("constantType", ScriptValue.string("int"));
        } else if (c instanceof StringConstant) {
            props.put("value", ScriptValue.string(((StringConstant) c).getValue()));
            props.put("constantType", ScriptValue.string("string"));
        }
        return ScriptValue.object(props);
    }

    private enum RuleType {
        BEFORE_METHOD, AFTER_METHOD,
        BEFORE_CALL, AFTER_CALL, REPLACE_CALL,
        BEFORE_FIELD_READ, AFTER_FIELD_WRITE,
        REMOVE, REPLACE_CONSTANT, MODIFY
    }

    private static class InstrumentationRule {
        final RuleType type;
        final ScriptFunction filter;
        final ScriptFunction action;
        final String targetPattern;

        InstrumentationRule(RuleType type, ScriptFunction filter, ScriptFunction action, String targetPattern) {
            this.type = type;
            this.filter = filter;
            this.action = action;
            this.targetPattern = targetPattern;
        }
    }
}
