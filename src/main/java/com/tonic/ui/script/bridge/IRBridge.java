package com.tonic.ui.script.bridge;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.transform.IRTransform;
import com.tonic.analysis.ssa.value.*;
import com.tonic.ui.script.engine.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge between JStudio script and the SSA IR system.
 * Exposes IR manipulation functionality to scripts via the 'ir' global object.
 */
public class IRBridge {

    private final ScriptInterpreter interpreter;
    private final List<HandlerRegistration> handlers = new ArrayList<>();
    private Consumer<String> logCallback;

    public IRBridge(ScriptInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Creates the 'ir' object to be registered in the script context.
     */
    public ScriptValue createIRObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        // Handler registration methods
        props.put("onBinaryOp", ScriptValue.function(
            ScriptFunction.native1("onBinaryOp", this::registerBinaryOpHandler)
        ));

        props.put("onUnaryOp", ScriptValue.function(
            ScriptFunction.native1("onUnaryOp", this::registerUnaryOpHandler)
        ));

        props.put("onInvoke", ScriptValue.function(
            ScriptFunction.native1("onInvoke", this::registerInvokeHandler)
        ));

        props.put("onGetField", ScriptValue.function(
            ScriptFunction.native1("onGetField", this::registerGetFieldHandler)
        ));

        props.put("onPutField", ScriptValue.function(
            ScriptFunction.native1("onPutField", this::registerPutFieldHandler)
        ));

        props.put("onConstant", ScriptValue.function(
            ScriptFunction.native1("onConstant", this::registerConstantHandler)
        ));

        props.put("onBranch", ScriptValue.function(
            ScriptFunction.native1("onBranch", this::registerBranchHandler)
        ));

        props.put("onReturn", ScriptValue.function(
            ScriptFunction.native1("onReturn", this::registerReturnHandler)
        ));

        props.put("forEachBlock", ScriptValue.function(
            ScriptFunction.native1("forEachBlock", this::registerBlockHandler)
        ));

        props.put("forEachInstruction", ScriptValue.function(
            ScriptFunction.native1("forEachInstruction", this::registerInstructionHandler)
        ));

        // Factory methods
        props.put("constant", ScriptValue.function(
            ScriptFunction.native2("constant", this::createConstant)
        ));

        props.put("intConstant", ScriptValue.function(
            ScriptFunction.native1("intConstant", arg -> {
                int val = (int) arg.asNumber();
                return ScriptValue.native_(new IntConstant(val));
            })
        ));

        props.put("longConstant", ScriptValue.function(
            ScriptFunction.native1("longConstant", arg -> {
                long val = (long) arg.asNumber();
                return ScriptValue.native_(new LongConstant(val));
            })
        ));

        props.put("stringConstant", ScriptValue.function(
            ScriptFunction.native1("stringConstant", arg -> ScriptValue.native_(new StringConstant(arg.asString())))
        ));

        props.put("nullConstant", ScriptValue.function(
            ScriptFunction.native0("nullConstant", () -> ScriptValue.native_(NullConstant.INSTANCE))
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue createConstant(ScriptValue value, ScriptValue type) {
        String typeStr = type.isNull() ? "int" : type.asString().toLowerCase();

        if (value.isNull()) {
            return ScriptValue.native_(NullConstant.INSTANCE);
        }

        switch (typeStr) {
            case "long":
                return ScriptValue.native_(new LongConstant((long) value.asNumber()));
            case "float":
                return ScriptValue.native_(new FloatConstant((float) value.asNumber()));
            case "double":
                return ScriptValue.native_(new DoubleConstant(value.asNumber()));
            case "string":
                return ScriptValue.native_(new StringConstant(value.asString()));
            default:
                return ScriptValue.native_(new IntConstant((int) value.asNumber()));
        }
    }

    // ==================== Handler Registration ====================

    private ScriptValue registerBinaryOpHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onBinaryOp requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.BINARY_OP, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerUnaryOpHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onUnaryOp requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.UNARY_OP, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerInvokeHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onInvoke requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.INVOKE, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerGetFieldHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onGetField requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.GET_FIELD, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerPutFieldHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onPutField requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.PUT_FIELD, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerConstantHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onConstant requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.CONSTANT, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerBranchHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onBranch requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.BRANCH, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerReturnHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onReturn requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.RETURN, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerBlockHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("forEachBlock requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.BLOCK, callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerInstructionHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("forEachInstruction requires a function argument");
        }
        handlers.add(new HandlerRegistration(HandlerType.INSTRUCTION, callback.asFunction()));
        return ScriptValue.NULL;
    }

    // ==================== Apply to IRMethod ====================

    /**
     * Creates an IRTransform that applies all registered handlers.
     */
    public IRTransform createTransform(String name) {
        return new ScriptedIRTransform(name, this);
    }

    /**
     * Runs all registered handlers on the given method.
     * Returns the number of modifications made.
     */
    public int applyTo(IRMethod method) {
        int modCount = 0;

        // Process each handler
        for (HandlerRegistration reg : handlers) {
            switch (reg.type) {
                case BLOCK:
                    for (IRBlock block : method.getBlocks()) {
                        ScriptValue wrapped = new IRNodeWrapper(block).toScriptValue();
                        callHandler(reg.function, wrapped);
                    }
                    break;

                case INSTRUCTION:
                    for (IRBlock block : method.getBlocks()) {
                        List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
                        for (IRInstruction instr : instructions) {
                            ScriptValue wrapped = new IRNodeWrapper(instr).toScriptValue();
                            ScriptValue result = callHandler(reg.function, wrapped);
                            modCount += processResult(result, instr, block);
                        }
                    }
                    break;

                default:
                    modCount += processTypedHandler(reg, method);
                    break;
            }
        }

        return modCount;
    }

    private int processTypedHandler(HandlerRegistration reg, IRMethod method) {
        int modCount = 0;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());

            for (IRInstruction instr : instructions) {
                boolean matches = false;

                switch (reg.type) {
                    case BINARY_OP:
                        matches = instr instanceof BinaryOpInstruction;
                        break;
                    case UNARY_OP:
                        matches = instr instanceof UnaryOpInstruction;
                        break;
                    case INVOKE:
                        matches = instr instanceof InvokeInstruction;
                        break;
                    case GET_FIELD:
                        matches = instr instanceof FieldAccessInstruction && ((FieldAccessInstruction) instr).isLoad();
                        break;
                    case PUT_FIELD:
                        matches = instr instanceof FieldAccessInstruction && ((FieldAccessInstruction) instr).isStore();
                        break;
                    case CONSTANT:
                        matches = instr instanceof ConstantInstruction;
                        break;
                    case BRANCH:
                        matches = instr instanceof BranchInstruction;
                        break;
                    case RETURN:
                        matches = instr instanceof ReturnInstruction;
                        break;
                }

                if (matches) {
                    ScriptValue wrapped = new IRNodeWrapper(instr).toScriptValue();
                    ScriptValue result = callHandler(reg.function, wrapped);
                    modCount += processResult(result, instr, block);
                }
            }
        }

        return modCount;
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

    private int processResult(ScriptValue result, IRInstruction original, IRBlock block) {
        if (result == null) {
            return 0; // No change (undefined return)
        }

        if (result.isNull()) {
            // null explicitly returned means remove
            block.removeInstruction(original);
            return 1;
        }

        if (result.isNative()) {
            Object obj = result.unwrap();

            // If it's a Constant, create a ConstantInstruction
            if (obj instanceof Constant && original.getResult() != null) {
                ConstantInstruction newInstr = new ConstantInstruction(original.getResult(), (Constant) obj);
                newInstr.setBlock(block);
                int idx = block.getInstructions().indexOf(original);
                if (idx >= 0) {
                    block.removeInstruction(original);
                    block.insertInstruction(idx, newInstr);
                    return 1;
                }
            }

            // If it's an IRInstruction, replace
            if (obj instanceof IRInstruction && obj != original) {
                IRInstruction replacement = (IRInstruction) obj;
                replacement.setBlock(block);
                int idx = block.getInstructions().indexOf(original);
                if (idx >= 0) {
                    block.removeInstruction(original);
                    block.insertInstruction(idx, replacement);
                    return 1;
                }
            }
        }

        return 0;
    }

    /**
     * Clears all registered handlers.
     */
    public void clearHandlers() {
        handlers.clear();
    }

    // ==================== Internal Classes ====================

    private enum HandlerType {
        BINARY_OP,
        UNARY_OP,
        INVOKE,
        GET_FIELD,
        PUT_FIELD,
        CONSTANT,
        BRANCH,
        RETURN,
        BLOCK,
        INSTRUCTION
    }

    private static class HandlerRegistration {
        final HandlerType type;
        final ScriptFunction function;

        HandlerRegistration(HandlerType type, ScriptFunction function) {
            this.type = type;
            this.function = function;
        }
    }

    /**
     * IRTransform wrapper that runs the script handlers.
     */
    private static class ScriptedIRTransform implements IRTransform {
        private final String name;
        private final IRBridge bridge;

        ScriptedIRTransform(String name, IRBridge bridge) {
            this.name = name;
            this.bridge = bridge;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean run(IRMethod method) {
            int mods = bridge.applyTo(method);
            return mods > 0;
        }
    }
}
