package com.tonic.ui.query.exec;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeVirtualInstruction;
import com.tonic.analysis.instruction.InvokeStaticInstruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.InvokeInterfaceInstruction;
import com.tonic.ui.query.planner.probe.ProbeResult;
import com.tonic.ui.query.planner.probe.ProbeSet;
import com.tonic.ui.query.planner.probe.ProbeSpec;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

public class QueryProbeListener implements BytecodeListener {

    private final ProbeSet probes;
    private final ProbeResult.Builder resultBuilder;
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final long startTimeNs;
    @Getter
    private String currentMethod;
    private int currentPc;
    private long instructionCount;

    public QueryProbeListener(ProbeSet probes, String methodSignature) {
        this.probes = probes;
        this.resultBuilder = ProbeResult.builder(methodSignature);
        this.startTimeNs = System.nanoTime();
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instruction) {
        currentMethod = frame.getMethod().getOwnerName() + "." +
                        frame.getMethod().getName() + frame.getMethod().getDesc();
        currentPc = instruction.getOffset();
        instructionCount++;
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction) {
        int opcode = instruction.getOpcode();

        if (opcode >= 0xB6 && opcode <= 0xBA) {
            handleMethodCall(instruction);
        }

        if (opcode == 0xBB) {
            handleAllocation(instruction);
        }

        if (opcode >= 0xB2 && opcode <= 0xB5) {
            handleFieldAccess(instruction, opcode >= 0xB4);
        }

        if (opcode == 0xBF) {
            handleException(frame);
        }

    }

    private void handleMethodCall(Instruction instruction) {
        if (!probes.hasProbeType(ProbeSpec.ProbeType.CALL)) {
            return;
        }

        String owner = null;
        String name = null;
        String desc = null;

        if (instruction instanceof InvokeVirtualInstruction) {
            InvokeVirtualInstruction inv = (InvokeVirtualInstruction) instruction;
            owner = inv.getOwnerClass();
            name = inv.getMethodName();
            desc = inv.getMethodDescriptor();
        } else if (instruction instanceof InvokeStaticInstruction) {
            InvokeStaticInstruction inv = (InvokeStaticInstruction) instruction;
            owner = inv.getOwnerClass();
            name = inv.getMethodName();
            desc = inv.getMethodDescriptor();
        } else if (instruction instanceof InvokeSpecialInstruction) {
            InvokeSpecialInstruction inv = (InvokeSpecialInstruction) instruction;
            owner = inv.getOwnerClass();
            name = inv.getMethodName();
            desc = inv.getMethodDescriptor();
        } else if (instruction instanceof InvokeInterfaceInstruction) {
            InvokeInterfaceInstruction inv = (InvokeInterfaceInstruction) instruction;
            owner = inv.getOwnerClass();
            name = inv.getMethodName();
            desc = inv.getMethodDescriptor();
        }

        if (owner == null || name == null) {
            return;
        }

        for (ProbeSpec.CallProbe spec : probes.getProbesOfType(ProbeSpec.CallProbe.class)) {
            if (spec.matches(owner, name, desc)) {
                long seq = sequenceCounter.incrementAndGet();
                resultBuilder.recordCall(seq, currentPc, owner, name, desc);
                break;
            }
        }
    }

    private void handleAllocation(Instruction instruction) {
        if (!probes.hasProbeType(ProbeSpec.ProbeType.ALLOCATION)) {
            return;
        }

        String typeName = extractAllocationType(instruction);
        if (typeName == null) return;

        for (ProbeSpec.AllocProbe spec : probes.getProbesOfType(ProbeSpec.AllocProbe.class)) {
            if (spec.matches(typeName)) {
                resultBuilder.recordAllocation(typeName);
                break;
            }
        }
    }

    private void handleFieldAccess(Instruction instruction, boolean isWrite) {
        if (!probes.hasProbeType(ProbeSpec.ProbeType.FIELD)) {
            return;
        }

        String fieldRef = extractFieldTarget(instruction);
        if (fieldRef == null) return;

        String[] parts = parseFieldRef(fieldRef);
        if (parts == null) return;

        String owner = parts[0];
        String name = parts[1];
        String desc = parts[2];

        for (ProbeSpec.FieldProbe spec : probes.getProbesOfType(ProbeSpec.FieldProbe.class)) {
            if (spec.matches(owner, name)) {
                if ((isWrite && spec.trackWrites()) || (!isWrite && spec.trackReads())) {
                    long seq = sequenceCounter.incrementAndGet();
                    resultBuilder.recordFieldAccess(seq, currentPc, owner, name, desc, isWrite);
                    break;
                }
            }
        }
    }

    private void handleException(StackFrame frame) {
        if (!probes.hasProbeType(ProbeSpec.ProbeType.EXCEPTION)) {
            return;
        }

        var exception = frame.getException();
        if (exception == null) return;

        String exType = exception.getClassName();

        for (ProbeSpec.ExceptionProbe spec : probes.getProbesOfType(ProbeSpec.ExceptionProbe.class)) {
            if (spec.exceptionType() == null || exType.contains(spec.exceptionType())) {
                long seq = sequenceCounter.incrementAndGet();
                resultBuilder.recordException(seq, currentPc, exType, false);
                break;
            }
        }
    }

    public ProbeResult buildResult() {
        resultBuilder.instructionCount(instructionCount);
        resultBuilder.executionTime(System.nanoTime() - startTimeNs);
        return resultBuilder.build();
    }

    private String extractAllocationType(Instruction instruction) {
        String text = instruction.toString();
        if (text != null && text.contains(" ")) {
            return text.substring(text.lastIndexOf(' ') + 1);
        }
        return null;
    }

    private String extractFieldTarget(Instruction instruction) {
        return instruction.toString();
    }

    private String[] parseFieldRef(String fieldRef) {
        if (fieldRef == null) return null;

        int dot = fieldRef.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }

        String owner = fieldRef.substring(0, dot);
        String nameAndDesc = fieldRef.substring(dot + 1);
        int colon = nameAndDesc.indexOf(':');

        if (colon < 0) {
            return new String[] { owner, nameAndDesc, "" };
        }

        String name = nameAndDesc.substring(0, colon);
        String desc = nameAndDesc.substring(colon + 1);

        return new String[] { owner, name, desc };
    }
}
