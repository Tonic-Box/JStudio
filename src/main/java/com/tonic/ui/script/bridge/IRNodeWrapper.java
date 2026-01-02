package com.tonic.ui.script.bridge;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.ui.script.engine.ScriptValue;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps IR nodes (instructions, blocks, values) for script access.
 * Provides a uniform interface to access node properties from scripts.
 */
@Getter
public class IRNodeWrapper {

    private final Object node;

    public IRNodeWrapper(Object node) {
        this.node = node;
    }

    public Object unwrap() {
        return node;
    }

    /**
     * Creates a ScriptValue representing this node.
     */
    public ScriptValue toScriptValue() {
        Map<String, ScriptValue> props = new HashMap<>();

        // Common properties
        props.put("type", ScriptValue.string(node.getClass().getSimpleName()));
        props.put("_native", ScriptValue.native_(this));

        // Type-specific properties
        if (node instanceof BinaryOpInstruction) {
            BinaryOpInstruction binary = (BinaryOpInstruction) node;
            props.put("op", ScriptValue.string(binary.getOp().name()));
            props.put("left", wrapValue(binary.getLeft()));
            props.put("right", wrapValue(binary.getRight()));
            props.put("result", wrapValue(binary.getResult()));
        }
        else if (node instanceof UnaryOpInstruction) {
            UnaryOpInstruction unary = (UnaryOpInstruction) node;
            props.put("op", ScriptValue.string(unary.getOp().name()));
            props.put("operand", wrapValue(unary.getOperand()));
            props.put("result", wrapValue(unary.getResult()));
        }
        else if (node instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) node;
            props.put("methodName", ScriptValue.string(invoke.getName()));
            props.put("name", ScriptValue.string(invoke.getName()));
            props.put("owner", ScriptValue.string(invoke.getOwner()));
            props.put("descriptor", ScriptValue.string(invoke.getDescriptor()));
            props.put("invokeType", ScriptValue.string(invoke.getInvokeType().name()));
            props.put("args", wrapValueList(invoke.getArguments()));
            props.put("result", wrapValue(invoke.getResult()));
        }
        else if (node instanceof FieldAccessInstruction) {
            FieldAccessInstruction field = (FieldAccessInstruction) node;
            props.put("fieldName", ScriptValue.string(field.getName()));
            props.put("name", ScriptValue.string(field.getName()));
            props.put("owner", ScriptValue.string(field.getOwner()));
            props.put("descriptor", ScriptValue.string(field.getDescriptor()));
            props.put("isStatic", ScriptValue.bool(field.isStatic()));
            props.put("isLoad", ScriptValue.bool(field.isLoad()));
            props.put("isStore", ScriptValue.bool(field.isStore()));
            props.put("objectRef", field.getObjectRef() != null ? wrapValue(field.getObjectRef()) : ScriptValue.NULL);
            if (field.isLoad()) {
                props.put("result", wrapValue(field.getResult()));
            } else {
                props.put("value", wrapValue(field.getValue()));
            }
        }
        else if (node instanceof ConstantInstruction) {
            ConstantInstruction constInstr = (ConstantInstruction) node;
            props.put("value", wrapConstant(constInstr.getConstant()));
            props.put("result", wrapValue(constInstr.getResult()));
        }
        else if (node instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) node;
            props.put("condition", ScriptValue.string(branch.getCondition() != null ? branch.getCondition().name() : ""));
            props.put("left", branch.getLeft() != null ? wrapValue(branch.getLeft()) : ScriptValue.NULL);
            props.put("right", branch.getRight() != null ? wrapValue(branch.getRight()) : ScriptValue.NULL);
            props.put("trueTarget", branch.getTrueTarget() != null ?
                ScriptValue.string("block_" + branch.getTrueTarget().getId()) : ScriptValue.NULL);
            props.put("falseTarget", branch.getFalseTarget() != null ?
                ScriptValue.string("block_" + branch.getFalseTarget().getId()) : ScriptValue.NULL);
        }
        else if (node instanceof ReturnInstruction) {
            ReturnInstruction ret = (ReturnInstruction) node;
            props.put("value", ret.getReturnValue() != null ? wrapValue(ret.getReturnValue()) : ScriptValue.NULL);
            props.put("isVoidReturn", ScriptValue.bool(ret.isVoidReturn()));
        }
        else if (node instanceof NewInstruction) {
            NewInstruction newInstr = (NewInstruction) node;
            props.put("className", ScriptValue.string(newInstr.getClassName()));
            props.put("result", wrapValue(newInstr.getResult()));
        }
        else if (node instanceof TypeCheckInstruction) {
            TypeCheckInstruction typeCheck = (TypeCheckInstruction) node;
            props.put("operand", wrapValue(typeCheck.getOperand()));
            props.put("targetType", ScriptValue.string(typeCheck.getTargetType() != null ? typeCheck.getTargetType().toString() : ""));
            props.put("result", wrapValue(typeCheck.getResult()));
            props.put("isCast", ScriptValue.bool(typeCheck.isCast()));
            props.put("isInstanceOf", ScriptValue.bool(typeCheck.isInstanceOf()));
        }
        else if (node instanceof IRBlock) {
            IRBlock block = (IRBlock) node;
            props.put("id", ScriptValue.number(block.getId()));
            props.put("instructionCount", ScriptValue.number(block.getInstructions().size()));

            List<ScriptValue> instrs = new ArrayList<>();
            for (IRInstruction instr : block.getInstructions()) {
                instrs.add(new IRNodeWrapper(instr).toScriptValue());
            }
            props.put("instructions", ScriptValue.array(instrs));
        }

        return ScriptValue.object(props);
    }

    private ScriptValue wrapValue(Value value) {
        if (value == null) return ScriptValue.NULL;

        Map<String, ScriptValue> props = new HashMap<>();
        props.put("type", ScriptValue.string(value.getClass().getSimpleName()));
        props.put("_native", ScriptValue.native_(value));

        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            props.put("id", ScriptValue.number(ssa.getId()));
            props.put("name", ScriptValue.string(ssa.getName()));
            props.put("irType", ssa.getType() != null ?
                ScriptValue.string(ssa.getType().toString()) : ScriptValue.NULL);
        }
        else if (value instanceof Constant) {
            props.put("isConstant", ScriptValue.TRUE);
            props.putAll(getConstantProps((Constant) value));
        }

        return ScriptValue.object(props);
    }

    private ScriptValue wrapConstant(Constant constant) {
        if (constant == null) return ScriptValue.NULL;

        Map<String, ScriptValue> props = new HashMap<>();
        props.put("type", ScriptValue.string(constant.getClass().getSimpleName()));
        props.put("isConstant", ScriptValue.TRUE);
        props.put("_native", ScriptValue.native_(constant));
        props.putAll(getConstantProps(constant));

        return ScriptValue.object(props);
    }

    private Map<String, ScriptValue> getConstantProps(Constant constant) {
        Map<String, ScriptValue> props = new HashMap<>();

        if (constant instanceof IntConstant) {
            props.put("value", ScriptValue.number(((IntConstant) constant).getValue()));
            props.put("constantType", ScriptValue.string("int"));
        }
        else if (constant instanceof LongConstant) {
            props.put("value", ScriptValue.number(((LongConstant) constant).getValue()));
            props.put("constantType", ScriptValue.string("long"));
        }
        else if (constant instanceof FloatConstant) {
            props.put("value", ScriptValue.number(((FloatConstant) constant).getValue()));
            props.put("constantType", ScriptValue.string("float"));
        }
        else if (constant instanceof DoubleConstant) {
            props.put("value", ScriptValue.number(((DoubleConstant) constant).getValue()));
            props.put("constantType", ScriptValue.string("double"));
        }
        else if (constant instanceof StringConstant) {
            props.put("value", ScriptValue.string(((StringConstant) constant).getValue()));
            props.put("constantType", ScriptValue.string("string"));
        }
        else if (constant instanceof NullConstant) {
            props.put("value", ScriptValue.NULL);
            props.put("constantType", ScriptValue.string("null"));
        }

        return props;
    }

    private ScriptValue wrapValueList(List<? extends Value> values) {
        List<ScriptValue> wrapped = new ArrayList<>();
        for (Value value : values) {
            wrapped.add(wrapValue(value));
        }
        return ScriptValue.array(wrapped);
    }
}
