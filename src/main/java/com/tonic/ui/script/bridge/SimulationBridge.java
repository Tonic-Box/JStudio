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
 * Bridge for abstract interpretation and simulation.
 * Exposes a 'simulation' global object for stepping through IR execution.
 */
public class SimulationBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private Consumer<String> logCallback;

    private IRMethod currentMethod;
    private IRBlock currentBlock;
    private int currentInstrIndex;
    private final Map<Integer, ScriptValue> abstractState = new HashMap<>();
    private final List<ScriptFunction> onInstructionCallbacks = new ArrayList<>();
    private final List<ScriptFunction> onInvokeCallbacks = new ArrayList<>();
    private final List<ScriptFunction> onFieldReadCallbacks = new ArrayList<>();
    private final List<ScriptFunction> onFieldWriteCallbacks = new ArrayList<>();
    private final List<ScriptFunction> onBranchCallbacks = new ArrayList<>();
    private boolean stopped = false;

    public SimulationBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public ScriptValue createSimulationObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("load", ScriptValue.function(
            ScriptFunction.native1("load", this::loadMethod)
        ));

        props.put("run", ScriptValue.function(
            ScriptFunction.native0("run", this::runSimulation)
        ));

        props.put("step", ScriptValue.function(
            ScriptFunction.native0("step", this::stepInstruction)
        ));

        props.put("stop", ScriptValue.function(
            ScriptFunction.native0("stop", this::stopSimulation)
        ));

        props.put("reset", ScriptValue.function(
            ScriptFunction.native0("reset", this::resetSimulation)
        ));

        props.put("onInstruction", ScriptValue.function(
            ScriptFunction.native1("onInstruction", this::registerInstructionCallback)
        ));

        props.put("onInvoke", ScriptValue.function(
            ScriptFunction.native1("onInvoke", this::registerInvokeCallback)
        ));

        props.put("onFieldRead", ScriptValue.function(
            ScriptFunction.native1("onFieldRead", this::registerFieldReadCallback)
        ));

        props.put("onFieldWrite", ScriptValue.function(
            ScriptFunction.native1("onFieldWrite", this::registerFieldWriteCallback)
        ));

        props.put("onBranch", ScriptValue.function(
            ScriptFunction.native1("onBranch", this::registerBranchCallback)
        ));

        props.put("getState", ScriptValue.function(
            ScriptFunction.native0("getState", this::getState)
        ));

        props.put("setState", ScriptValue.function(
            ScriptFunction.native2("setState", this::setState)
        ));

        props.put("getCurrentBlock", ScriptValue.function(
            ScriptFunction.native0("getCurrentBlock", this::getCurrentBlock)
        ));

        props.put("getCurrentInstruction", ScriptValue.function(
            ScriptFunction.native0("getCurrentInstruction", this::getCurrentInstruction)
        ));

        props.put("getBlocks", ScriptValue.function(
            ScriptFunction.native0("getBlocks", this::getBlocks)
        ));

        props.put("clearCallbacks", ScriptValue.function(
            ScriptFunction.native0("clearCallbacks", this::clearCallbacks)
        ));

        props.put("trace", ScriptValue.function(
            ScriptFunction.native0("trace", this::collectTrace)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue loadMethod(ScriptValue methodRef) {
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

            resetSimulation();
            log("Loaded method for simulation: " + methodName);
            return ScriptValue.bool(true);
        } catch (Exception e) {
            log("Error loading method: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }

    private ScriptValue runSimulation() {
        if (currentMethod == null) {
            log("No method loaded");
            return ScriptValue.bool(false);
        }

        stopped = false;
        int stepCount = 0;
        int maxSteps = 10000;

        while (!stopped && currentBlock != null && stepCount < maxSteps) {
            stepInstruction();
            stepCount++;
        }

        log("Simulation completed: " + stepCount + " steps");
        return ScriptValue.number(stepCount);
    }

    private ScriptValue stepInstruction() {
        if (currentMethod == null || currentBlock == null) {
            return ScriptValue.bool(false);
        }

        List<IRInstruction> instructions = currentBlock.getInstructions();
        if (currentInstrIndex >= instructions.size()) {
            advanceToNextBlock();
            return ScriptValue.bool(currentBlock != null);
        }

        IRInstruction instr = instructions.get(currentInstrIndex);
        processInstruction(instr);
        currentInstrIndex++;

        if (currentInstrIndex >= instructions.size()) {
            advanceToNextBlock();
        }

        return ScriptValue.bool(true);
    }

    private void processInstruction(IRInstruction instr) {
        ScriptValue instrWrapper = wrapInstruction(instr);

        for (ScriptFunction callback : onInstructionCallbacks) {
            callCallback(callback, instrWrapper);
        }

        if (instr instanceof InvokeInstruction) {
            for (ScriptFunction callback : onInvokeCallbacks) {
                callCallback(callback, instrWrapper);
            }
        } else if (instr instanceof GetFieldInstruction) {
            for (ScriptFunction callback : onFieldReadCallbacks) {
                callCallback(callback, instrWrapper);
            }
        } else if (instr instanceof PutFieldInstruction) {
            for (ScriptFunction callback : onFieldWriteCallbacks) {
                callCallback(callback, instrWrapper);
            }
        } else if (instr instanceof BranchInstruction) {
            for (ScriptFunction callback : onBranchCallbacks) {
                callCallback(callback, instrWrapper);
            }
        }

        updateAbstractState(instr);
    }

    private void updateAbstractState(IRInstruction instr) {
        Value result = instr.getResult();
        if (result instanceof SSAValue) {
            SSAValue ssa = (SSAValue) result;
            ScriptValue abstractValue = computeAbstractValue(instr);
            abstractState.put(ssa.getId(), abstractValue);
        }
    }

    private ScriptValue computeAbstractValue(IRInstruction instr) {
        if (instr instanceof ConstantInstruction) {
            ConstantInstruction constInstr = (ConstantInstruction) instr;
            Constant c = constInstr.getConstant();
            if (c instanceof IntConstant) {
                return ScriptValue.number(((IntConstant) c).getValue());
            } else if (c instanceof LongConstant) {
                return ScriptValue.number(((LongConstant) c).getValue());
            } else if (c instanceof StringConstant) {
                return ScriptValue.string(((StringConstant) c).getValue());
            } else if (c instanceof NullConstant) {
                return ScriptValue.NULL;
            }
        }
        return ScriptValue.string("unknown");
    }

    private void advanceToNextBlock() {
        if (currentBlock == null) return;

        List<IRInstruction> instrs = currentBlock.getInstructions();
        if (!instrs.isEmpty()) {
            IRInstruction lastInstr = instrs.get(instrs.size() - 1);
            if (lastInstr instanceof BranchInstruction) {
                BranchInstruction branch = (BranchInstruction) lastInstr;
                currentBlock = branch.getTrueTarget();
            } else if (lastInstr instanceof ReturnInstruction) {
                currentBlock = null;
            } else {
                List<IRBlock> blocks = currentMethod.getBlocks();
                int idx = blocks.indexOf(currentBlock);
                if (idx >= 0 && idx + 1 < blocks.size()) {
                    currentBlock = blocks.get(idx + 1);
                } else {
                    currentBlock = null;
                }
            }
        } else {
            currentBlock = null;
        }

        currentInstrIndex = 0;
    }

    private ScriptValue stopSimulation() {
        stopped = true;
        return ScriptValue.NULL;
    }

    private ScriptValue resetSimulation() {
        if (currentMethod != null) {
            currentBlock = currentMethod.getEntryBlock();
        }
        currentInstrIndex = 0;
        abstractState.clear();
        stopped = false;
        return ScriptValue.NULL;
    }

    private ScriptValue registerInstructionCallback(ScriptValue callback) {
        if (callback.isFunction()) {
            onInstructionCallbacks.add(callback.asFunction());
        }
        return ScriptValue.NULL;
    }

    private ScriptValue registerInvokeCallback(ScriptValue callback) {
        if (callback.isFunction()) {
            onInvokeCallbacks.add(callback.asFunction());
        }
        return ScriptValue.NULL;
    }

    private ScriptValue registerFieldReadCallback(ScriptValue callback) {
        if (callback.isFunction()) {
            onFieldReadCallbacks.add(callback.asFunction());
        }
        return ScriptValue.NULL;
    }

    private ScriptValue registerFieldWriteCallback(ScriptValue callback) {
        if (callback.isFunction()) {
            onFieldWriteCallbacks.add(callback.asFunction());
        }
        return ScriptValue.NULL;
    }

    private ScriptValue registerBranchCallback(ScriptValue callback) {
        if (callback.isFunction()) {
            onBranchCallbacks.add(callback.asFunction());
        }
        return ScriptValue.NULL;
    }

    private ScriptValue getState() {
        Map<String, ScriptValue> state = new HashMap<>();
        for (Map.Entry<Integer, ScriptValue> entry : abstractState.entrySet()) {
            state.put("v" + entry.getKey(), entry.getValue());
        }
        return ScriptValue.object(state);
    }

    private ScriptValue setState(ScriptValue varId, ScriptValue value) {
        int id = (int) varId.asNumber();
        abstractState.put(id, value);
        return ScriptValue.NULL;
    }

    private ScriptValue getCurrentBlock() {
        if (currentBlock == null) return ScriptValue.NULL;

        Map<String, ScriptValue> props = new HashMap<>();
        props.put("id", ScriptValue.number(currentBlock.getId()));
        props.put("instructionCount", ScriptValue.number(currentBlock.getInstructions().size()));
        props.put("currentIndex", ScriptValue.number(currentInstrIndex));
        return ScriptValue.object(props);
    }

    private ScriptValue getCurrentInstruction() {
        if (currentBlock == null) return ScriptValue.NULL;

        List<IRInstruction> instrs = currentBlock.getInstructions();
        if (currentInstrIndex >= instrs.size()) return ScriptValue.NULL;

        return wrapInstruction(instrs.get(currentInstrIndex));
    }

    private ScriptValue getBlocks() {
        if (currentMethod == null) return ScriptValue.array(new ArrayList<>());

        List<ScriptValue> blocks = new ArrayList<>();
        for (IRBlock block : currentMethod.getBlocks()) {
            Map<String, ScriptValue> props = new HashMap<>();
            props.put("id", ScriptValue.number(block.getId()));
            props.put("instructionCount", ScriptValue.number(block.getInstructions().size()));
            blocks.add(ScriptValue.object(props));
        }
        return ScriptValue.array(blocks);
    }

    private ScriptValue clearCallbacks() {
        onInstructionCallbacks.clear();
        onInvokeCallbacks.clear();
        onFieldReadCallbacks.clear();
        onFieldWriteCallbacks.clear();
        onBranchCallbacks.clear();
        return ScriptValue.NULL;
    }

    private ScriptValue collectTrace() {
        if (currentMethod == null) {
            log("No method loaded");
            return ScriptValue.array(new ArrayList<>());
        }

        List<ScriptValue> trace = new ArrayList<>();
        resetSimulation();
        stopped = false;
        int maxSteps = 10000;
        int stepCount = 0;

        while (!stopped && currentBlock != null && stepCount < maxSteps) {
            List<IRInstruction> instrs = currentBlock.getInstructions();
            if (currentInstrIndex < instrs.size()) {
                IRInstruction instr = instrs.get(currentInstrIndex);
                Map<String, ScriptValue> step = new HashMap<>();
                step.put("blockId", ScriptValue.number(currentBlock.getId()));
                step.put("instrIndex", ScriptValue.number(currentInstrIndex));
                step.put("instruction", wrapInstruction(instr));
                trace.add(ScriptValue.object(step));
            }
            stepInstruction();
            stepCount++;
        }

        log("Collected trace: " + trace.size() + " instructions");
        return ScriptValue.array(trace);
    }

    private ScriptValue wrapInstruction(IRInstruction instr) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("type", ScriptValue.string(instr.getClass().getSimpleName()));

        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            props.put("owner", ScriptValue.string(invoke.getOwner()));
            props.put("name", ScriptValue.string(invoke.getName()));
            props.put("descriptor", ScriptValue.string(invoke.getDescriptor()));
            props.put("invokeType", ScriptValue.string(invoke.getInvokeType().name()));
        } else if (instr instanceof GetFieldInstruction) {
            GetFieldInstruction field = (GetFieldInstruction) instr;
            props.put("owner", ScriptValue.string(field.getOwner()));
            props.put("name", ScriptValue.string(field.getName()));
            props.put("isStatic", ScriptValue.bool(field.isStatic()));
        } else if (instr instanceof PutFieldInstruction) {
            PutFieldInstruction field = (PutFieldInstruction) instr;
            props.put("owner", ScriptValue.string(field.getOwner()));
            props.put("name", ScriptValue.string(field.getName()));
            props.put("isStatic", ScriptValue.bool(field.isStatic()));
        } else if (instr instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) instr;
            props.put("condition", ScriptValue.string(
                branch.getCondition() != null ? branch.getCondition().name() : "GOTO"));
        } else if (instr instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) instr;
            props.put("isVoid", ScriptValue.bool(ret.isVoidReturn()));
        }

        return ScriptValue.object(props);
    }

    private void callCallback(ScriptFunction fn, ScriptValue arg) {
        try {
            List<ScriptValue> args = new ArrayList<>();
            args.add(arg);
            ScriptValue result = fn.call(interpreter, args);
            if (result.isBoolean() && !result.asBoolean()) {
                stopped = true;
            }
        } catch (Exception e) {
            log("Callback error: " + e.getMessage());
        }
    }
}
