package com.tonic.ui.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StringDecryptionListener extends AbstractListener {

    private final List<DecryptionResult> decryptionResults = new ArrayList<>();
    private InvokeInstruction pendingInvoke;
    private String pendingArgsString;

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        decryptionResults.clear();
        pendingInvoke = null;
        pendingArgsString = null;
    }

    @Override
    public void onBeforeInstruction(IRInstruction instr, SimulationState state) {
        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            if (returnsString(invoke)) {
                pendingInvoke = invoke;
                pendingArgsString = captureArguments(invoke, state);
            }
        }
    }

    @Override
    public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after) {
        if (pendingInvoke == null || after == null || instr != pendingInvoke) {
            pendingInvoke = null;
            pendingArgsString = null;
            return;
        }

        if (after.stackDepth() > 0) {
            SimValue topValue = after.peek(0);
            if (topValue != null && topValue.isConstant()) {
                Object constantValue = topValue.getConstantValue();
                if (constantValue instanceof String) {
                    String decrypted = (String) constantValue;
                    if (!decrypted.isEmpty()) {
                        DecryptionResult result = new DecryptionResult(
                                pendingInvoke,
                                decrypted,
                                pendingArgsString,
                                formatMethodRef(pendingInvoke)
                        );
                        decryptionResults.add(result);
                    }
                }
            }
        }

        pendingInvoke = null;
        pendingArgsString = null;
    }

    private boolean returnsString(InvokeInstruction invoke) {
        String desc = invoke.getDescriptor();
        return desc != null && desc.endsWith(")Ljava/lang/String;");
    }

    private String captureArguments(InvokeInstruction invoke, SimulationState state) {
        List<Value> args = invoke.getMethodArguments();
        if (args.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int stackBase = state.stackDepth() - args.size();

        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");

            int stackIndex = args.size() - 1 - i;
            if (stackBase + stackIndex >= 0 && stackBase + stackIndex < state.stackDepth()) {
                SimValue val = state.peek(stackIndex);
                if (val != null && val.isConstant()) {
                    Object constVal = val.getConstantValue();
                    if (constVal instanceof byte[]) {
                        sb.append(formatByteArray((byte[]) constVal));
                    } else if (constVal instanceof char[]) {
                        sb.append(formatCharArray((char[]) constVal));
                    } else if (constVal instanceof String) {
                        sb.append("\"").append(constVal).append("\"");
                    } else if (constVal != null) {
                        sb.append(constVal);
                    } else {
                        sb.append(args.get(i));
                    }
                } else {
                    sb.append(args.get(i));
                }
            } else {
                sb.append(args.get(i));
            }
        }

        return sb.toString();
    }

    private String formatByteArray(byte[] bytes) {
        if (bytes.length <= 20) {
            StringBuilder sb = new StringBuilder("byte[]{");
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(bytes[i] & 0xFF);
            }
            sb.append("}");
            return sb.toString();
        }
        return "byte[" + bytes.length + "]";
    }

    private String formatCharArray(char[] chars) {
        if (chars.length <= 20) {
            return "char[]{\"" + new String(chars) + "\"}";
        }
        return "char[" + chars.length + "]";
    }

    private String formatMethodRef(InvokeInstruction invoke) {
        String owner = invoke.getOwner();
        int lastSlash = owner.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
        return simpleName + "." + invoke.getName();
    }

    public List<DecryptionResult> getDecryptionResults() {
        return Collections.unmodifiableList(decryptionResults);
    }

    public int getDecryptedCount() {
        return decryptionResults.size();
    }

    @Getter
    public static class DecryptionResult {
        private final InvokeInstruction instruction;
        private final String decryptedValue;
        private final String encryptedInput;
        private final String methodUsed;

        public DecryptionResult(InvokeInstruction instruction, String decryptedValue,
                                String encryptedInput, String methodUsed) {
            this.instruction = instruction;
            this.decryptedValue = decryptedValue;
            this.encryptedInput = encryptedInput;
            this.methodUsed = methodUsed;
        }

        public int getBlockId() {
            if (instruction != null && instruction.getBlock() != null) {
                return instruction.getBlock().getId();
            }
            return -1;
        }

        @Override
        public String toString() {
            return "DecryptionResult[value=\"" + decryptedValue +
                    "\", input=" + encryptedInput +
                    ", method=" + methodUsed + "]";
        }
    }
}
