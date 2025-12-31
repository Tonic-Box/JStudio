package com.tonic.ui.script.bridge;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;

public class TypeBridge extends AbstractBridge {

    private IRMethod currentMethod;
    private final Map<String, IRMethod> methodCache = new HashMap<>();

    public TypeBridge(ProjectModel projectModel) {
        super(projectModel);
    }

    @Override
    public ScriptValue createBridgeObject() {
        return createTypesObject();
    }

    public ScriptValue createTypesObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("analyze", ScriptValue.function(
            ScriptFunction.native1("analyze", this::analyzeMethod)
        ));

        props.put("getVariables", ScriptValue.function(
            ScriptFunction.native0("getVariables", this::getVariables)
        ));

        props.put("getType", ScriptValue.function(
            ScriptFunction.native1("getType", this::getType)
        ));

        props.put("findCasts", ScriptValue.function(
            ScriptFunction.native0("findCasts", this::findCasts)
        ));

        props.put("findInstanceOf", ScriptValue.function(
            ScriptFunction.native0("findInstanceOf", this::findInstanceOf)
        ));

        props.put("findAllocations", ScriptValue.function(
            ScriptFunction.native0("findAllocations", this::findAllocations)
        ));

        props.put("getReturnType", ScriptValue.function(
            ScriptFunction.native0("getReturnType", this::getReturnType)
        ));

        props.put("getParameterTypes", ScriptValue.function(
            ScriptFunction.native0("getParameterTypes", this::getParameterTypes)
        ));

        props.put("findByType", ScriptValue.function(
            ScriptFunction.native1("findByType", this::findByType)
        ));

        props.put("getInvocationReturnTypes", ScriptValue.function(
            ScriptFunction.native0("getInvocationReturnTypes", this::getInvocationReturnTypes)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue analyzeMethod(ScriptValue methodRef) {
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
                return ScriptValue.bool(false);
            }
        } else if (methodRef.isObject()) {
            Map<String, ScriptValue> obj = methodRef.asObject();
            className = obj.containsKey("className") ? obj.get("className").asString() : null;
            methodName = obj.containsKey("name") ? obj.get("name").asString() : null;
            methodDesc = obj.containsKey("desc") ? obj.get("desc").asString() : null;
        } else {
            log("Invalid method reference type");
            return ScriptValue.bool(false);
        }

        if (className == null || methodName == null) {
            log("Missing class or method name");
            return ScriptValue.bool(false);
        }

        String cacheKey = className + "." + methodName + (methodDesc != null ? methodDesc : "");
        if (methodCache.containsKey(cacheKey)) {
            currentMethod = methodCache.get(cacheKey);
            log("Using cached type analysis for " + cacheKey);
            return ScriptValue.bool(true);
        }

        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) {
            log("Class not found: " + className);
            return ScriptValue.bool(false);
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
            return ScriptValue.bool(false);
        }

        try {
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            currentMethod = ssa.lift(targetMethod);

            if (currentMethod == null || currentMethod.getEntryBlock() == null) {
                log("Failed to lift method to IR");
                return ScriptValue.bool(false);
            }

            methodCache.put(cacheKey, currentMethod);
            log("Type analysis ready for " + methodName);
            return ScriptValue.bool(true);
        } catch (Exception e) {
            log("Error analyzing method: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }

    private ScriptValue getVariables() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        Set<SSAValue> variables = new LinkedHashSet<>();
        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() instanceof SSAValue) {
                    variables.add(instr.getResult());
                }
            }
        }

        List<ScriptValue> result = new ArrayList<>();
        for (SSAValue var : variables) {
            result.add(wrapVariable(var));
        }
        return ScriptValue.array(result);
    }

    private ScriptValue getType(ScriptValue varRef) {
        if (currentMethod == null) return ScriptValue.NULL;

        if (varRef.isNumber()) {
            int id = (int) varRef.asNumber();
            for (IRBlock block : currentMethod.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr.getResult() instanceof SSAValue) {
                        SSAValue ssa = instr.getResult();
                        if (ssa.getId() == id) {
                            return ssa.getType() != null ?
                                ScriptValue.string(ssa.getType().toString()) : ScriptValue.NULL;
                        }
                    }
                }
            }
        } else if (varRef.isString()) {
            String name = varRef.asString();
            for (IRBlock block : currentMethod.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr.getResult() instanceof SSAValue) {
                        SSAValue ssa = instr.getResult();
                        if (ssa.getName().equals(name)) {
                            return ssa.getType() != null ?
                                ScriptValue.string(ssa.getType().toString()) : ScriptValue.NULL;
                        }
                    }
                }
            }
        }

        return ScriptValue.NULL;
    }

    private ScriptValue findCasts() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        List<ScriptValue> casts = new ArrayList<>();
        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CastInstruction) {
                    CastInstruction cast = (CastInstruction) instr;
                    Map<String, ScriptValue> props = new HashMap<>();
                    props.put("targetType", ScriptValue.string(
                        cast.getTargetType() != null ? cast.getTargetType().toString() : ""));
                    props.put("operand", wrapValue(cast.getObjectRef()));
                    props.put("result", wrapValue(cast.getResult()));
                    props.put("blockId", ScriptValue.number(block.getId()));
                    casts.add(ScriptValue.object(props));
                }
            }
        }
        return ScriptValue.array(casts);
    }

    private ScriptValue findInstanceOf() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        List<ScriptValue> checks = new ArrayList<>();
        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InstanceOfInstruction) {
                    InstanceOfInstruction iof = (InstanceOfInstruction) instr;
                    Map<String, ScriptValue> props = new HashMap<>();
                    props.put("checkType", ScriptValue.string(
                        iof.getCheckType() != null ? iof.getCheckType().toString() : ""));
                    props.put("operand", wrapValue(iof.getObjectRef()));
                    props.put("result", wrapValue(iof.getResult()));
                    props.put("blockId", ScriptValue.number(block.getId()));
                    checks.add(ScriptValue.object(props));
                }
            }
        }
        return ScriptValue.array(checks);
    }

    private ScriptValue findAllocations() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        List<ScriptValue> allocs = new ArrayList<>();
        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof NewInstruction) {
                    NewInstruction newInstr = (NewInstruction) instr;
                    Map<String, ScriptValue> props = new HashMap<>();
                    props.put("className", ScriptValue.string(newInstr.getClassName()));
                    props.put("result", wrapValue(newInstr.getResult()));
                    props.put("blockId", ScriptValue.number(block.getId()));
                    allocs.add(ScriptValue.object(props));
                } else if (instr instanceof NewArrayInstruction) {
                    NewArrayInstruction newArr = (NewArrayInstruction) instr;
                    Map<String, ScriptValue> props = new HashMap<>();
                    props.put("elementType", ScriptValue.string(
                        newArr.getElementType() != null ? newArr.getElementType().toString() : ""));
                    props.put("isArray", ScriptValue.TRUE);
                    props.put("result", wrapValue(newArr.getResult()));
                    props.put("blockId", ScriptValue.number(block.getId()));
                    allocs.add(ScriptValue.object(props));
                }
            }
        }
        return ScriptValue.array(allocs);
    }

    private ScriptValue getReturnType() {
        if (currentMethod == null) return ScriptValue.NULL;

        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof ReturnInstruction) {
                    ReturnInstruction ret = (ReturnInstruction) instr;
                    if (ret.getReturnValue() instanceof SSAValue) {
                        SSAValue ssa = (SSAValue) ret.getReturnValue();
                        if (ssa.getType() != null) {
                            return ScriptValue.string(ssa.getType().toString());
                        }
                    }
                }
            }
        }
        return ScriptValue.NULL;
    }

    private ScriptValue getParameterTypes() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        List<ScriptValue> params = new ArrayList<>();
        IRBlock entryBlock = currentMethod.getEntryBlock();
        if (entryBlock != null) {
            int paramIndex = 0;
            for (IRInstruction instr : entryBlock.getInstructions()) {
                if (instr.getResult() instanceof SSAValue) {
                    SSAValue ssa = instr.getResult();
                    String name = ssa.getName();
                    if (name != null && (name.startsWith("p") || name.startsWith("arg"))) {
                        Map<String, ScriptValue> props = new HashMap<>();
                        props.put("index", ScriptValue.number(paramIndex++));
                        props.put("name", ScriptValue.string(name));
                        props.put("type", ssa.getType() != null ?
                            ScriptValue.string(ssa.getType().toString()) : ScriptValue.NULL);
                        params.add(ScriptValue.object(props));
                    }
                }
            }
        }
        return ScriptValue.array(params);
    }

    private ScriptValue findByType(ScriptValue typePattern) {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        String pattern = typePattern.asString().toLowerCase();
        List<ScriptValue> matches = new ArrayList<>();

        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() instanceof SSAValue) {
                    SSAValue ssa = instr.getResult();
                    if (ssa.getType() != null) {
                        String typeStr = ssa.getType().toString().toLowerCase();
                        if (typeStr.contains(pattern)) {
                            Map<String, ScriptValue> props = new HashMap<>();
                            props.put("variable", wrapVariable(ssa));
                            props.put("instruction", ScriptValue.string(instr.getClass().getSimpleName()));
                            props.put("blockId", ScriptValue.number(block.getId()));
                            matches.add(ScriptValue.object(props));
                        }
                    }
                }
            }
        }
        return ScriptValue.array(matches);
    }

    private ScriptValue getInvocationReturnTypes() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        List<ScriptValue> invokes = new ArrayList<>();
        for (IRBlock block : currentMethod.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    Map<String, ScriptValue> props = new HashMap<>();
                    props.put("owner", ScriptValue.string(invoke.getOwner()));
                    props.put("name", ScriptValue.string(invoke.getName()));
                    props.put("descriptor", ScriptValue.string(invoke.getDescriptor()));
                    props.put("invokeType", ScriptValue.string(invoke.getInvokeType().name()));

                    String returnType = parseReturnTypeFromDescriptor(invoke.getDescriptor());
                    props.put("returnType", ScriptValue.string(returnType));

                    if (invoke.getResult() instanceof SSAValue) {
                        SSAValue ssa = invoke.getResult();
                        props.put("inferredType", ssa.getType() != null ?
                            ScriptValue.string(ssa.getType().toString()) : ScriptValue.NULL);
                    }

                    props.put("blockId", ScriptValue.number(block.getId()));
                    invokes.add(ScriptValue.object(props));
                }
            }
        }
        return ScriptValue.array(invokes);
    }

    private String parseReturnTypeFromDescriptor(String desc) {
        int idx = desc.lastIndexOf(')');
        if (idx < 0 || idx + 1 >= desc.length()) return "V";
        return desc.substring(idx + 1);
    }

    private ScriptValue wrapVariable(SSAValue var) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("id", ScriptValue.number(var.getId()));
        props.put("name", ScriptValue.string(var.getName()));
        props.put("type", var.getType() != null ?
            ScriptValue.string(var.getType().toString()) : ScriptValue.NULL);
        return ScriptValue.object(props);
    }

    private ScriptValue wrapValue(Value value) {
        if (value == null) return ScriptValue.NULL;

        Map<String, ScriptValue> props = new HashMap<>();
        props.put("valueType", ScriptValue.string(value.getClass().getSimpleName()));

        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            props.put("id", ScriptValue.number(ssa.getId()));
            props.put("name", ScriptValue.string(ssa.getName()));
            props.put("type", ssa.getType() != null ?
                ScriptValue.string(ssa.getType().toString()) : ScriptValue.NULL);
        }

        return ScriptValue.object(props);
    }

    public IRMethod getCurrentMethod() {
        return currentMethod;
    }
}
