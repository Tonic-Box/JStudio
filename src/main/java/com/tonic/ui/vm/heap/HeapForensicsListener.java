package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.vm.heap.model.AllocationEvent;
import com.tonic.ui.vm.heap.model.MutationEvent;
import com.tonic.ui.vm.heap.model.ProvenanceInfo;

import java.util.ArrayList;
import java.util.List;

public class HeapForensicsListener implements BytecodeListener {

    private final HeapForensicsTracker tracker;
    private int provenanceDepth = 1;
    private boolean trackMutations = true;
    private boolean trackStaticFields = true;

    private StackFrame currentFrame;
    private long instructionCount = 0;

    public HeapForensicsListener(HeapForensicsTracker tracker) {
        this.tracker = tracker;
    }

    public void setProvenanceDepth(int depth) {
        this.provenanceDepth = Math.max(0, Math.min(depth, 10));
    }

    public int getProvenanceDepth() {
        return provenanceDepth;
    }

    public void setTrackMutations(boolean track) {
        this.trackMutations = track;
    }

    public boolean isTrackMutations() {
        return trackMutations;
    }

    public void setTrackStaticFields(boolean track) {
        this.trackStaticFields = track;
    }

    public boolean isTrackStaticFields() {
        return trackStaticFields;
    }

    @Override
    public void onExecutionStart(MethodEntry entryPoint) {
        System.out.println("[HeapForensics] === START: " +
            (entryPoint != null ? entryPoint.getOwnerName() + "." + entryPoint.getName() : "null") + " ===");
        instructionCount = 0;
        tracker.onExecutionStart();
    }

    @Override
    public void onExecutionEnd(BytecodeResult result) {
        System.out.println("[HeapForensics] === END: instructions=" + instructionCount +
            ", allocations=" + tracker.getTotalAllocationCount() +
            ", result=" + (result != null ? result : "null") + " ===");
        if (result != null && result.getException() != null) {
            System.out.println("[HeapForensics] EXCEPTION: " + result.getException());
            result.getException().printStackTrace();
        }
        tracker.onExecutionEnd(instructionCount);
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instruction) {
        currentFrame = frame;
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction) {
        instructionCount++;
        currentFrame = frame;
    }

    @Override
    public void onObjectAllocation(ObjectInstance instance) {
        System.out.println("[HeapForensics] OBJECT ALLOC: " + instance.getClassName() +
            " id=" + instance.getId() + " at instruction " + instructionCount);

        ProvenanceInfo provenance = captureProvenance();

        AllocationEvent event = AllocationEvent.builder()
            .objectId(instance.getId())
            .className(instance.getClassName())
            .opcode(0xBB)
            .instructionCount(instructionCount)
            .provenance(provenance)
            .build();

        tracker.recordAllocation(event, instance);
    }

    @Override
    public void onArrayAllocation(ArrayInstance array) {
        System.out.println("[HeapForensics] ARRAY ALLOC: " + array.getComponentType() + "[] " +
            "length=" + array.getLength() + " id=" + array.getId() + " at instruction " + instructionCount);

        ProvenanceInfo provenance = captureProvenance();

        AllocationEvent event = AllocationEvent.builder()
            .objectId(array.getId())
            .className(array.getComponentType() + "[]")
            .opcode(0xBD)
            .instructionCount(instructionCount)
            .provenance(provenance)
            .arrayLength(array.getLength())
            .build();

        tracker.recordAllocation(event, array);
    }

    @Override
    public void onFieldWrite(ObjectInstance instance, String fieldName,
                             ConcreteValue oldValue, ConcreteValue newValue) {
        if (!trackMutations) {
            return;
        }

        ProvenanceInfo provenance = provenanceDepth > 0 ? captureProvenance() : null;

        String fieldOwner = instance.getClassName();
        String fieldDescriptor = inferDescriptor(newValue);

        MutationEvent event = MutationEvent.builder()
            .objectId(instance.getId())
            .fieldOwner(fieldOwner)
            .fieldName(fieldName)
            .fieldDescriptor(fieldDescriptor)
            .oldValue(unwrapValue(oldValue))
            .newValue(unwrapValue(newValue))
            .instructionCount(instructionCount)
            .opcode(0xB5)
            .provenance(provenance)
            .build();

        tracker.recordMutation(event);
    }

    @Override
    public void onArrayWrite(ArrayInstance array, int index,
                             ConcreteValue oldValue, ConcreteValue newValue) {
        if (!trackMutations) {
            return;
        }

        ProvenanceInfo provenance = provenanceDepth > 0 ? captureProvenance() : null;

        MutationEvent event = MutationEvent.builder()
            .objectId(array.getId())
            .fieldOwner(array.getComponentType() + "[]")
            .fieldName("[" + index + "]")
            .fieldDescriptor(array.getComponentType())
            .oldValue(unwrapValue(oldValue))
            .newValue(unwrapValue(newValue))
            .instructionCount(instructionCount)
            .opcode(0x4F)
            .provenance(provenance)
            .build();

        tracker.recordMutation(event);
    }

    @Override
    public void onFramePush(StackFrame frame) {
        currentFrame = frame;
    }

    @Override
    public void onFramePop(StackFrame frame, ConcreteValue returnValue) {
    }

    private ProvenanceInfo captureProvenance() {
        if (provenanceDepth == 0 || currentFrame == null) {
            return null;
        }

        MethodEntry method = currentFrame.getMethod();
        int pc = currentFrame.getPC();
        int lineNumber = getLineNumber(method, pc);

        ProvenanceInfo.Builder builder = ProvenanceInfo.builder()
            .className(method.getOwnerName())
            .methodName(method.getName())
            .descriptor(method.getDesc())
            .methodSignature(method.getOwnerName() + "." + method.getName() + method.getDesc())
            .pc(pc)
            .lineNumber(lineNumber);

        if (provenanceDepth > 1) {
            List<ProvenanceInfo.StackFrameInfo> callStack = captureCallStack(provenanceDepth - 1);
            builder.callStack(callStack);
        }

        return builder.build();
    }

    private List<ProvenanceInfo.StackFrameInfo> captureCallStack(int maxFrames) {
        List<ProvenanceInfo.StackFrameInfo> frames = new ArrayList<>();

        return frames;
    }

    private int getLineNumber(MethodEntry method, int pc) {
        try {
            var codeAttr = method.getCodeAttribute();
            if (codeAttr != null) {
                for (var attr : codeAttr.getAttributes()) {
                    if (attr instanceof com.tonic.parser.attribute.LineNumberTableAttribute) {
                        var lnt = (com.tonic.parser.attribute.LineNumberTableAttribute) attr;
                        var entries = lnt.getLineNumberTable();
                        if (entries == null || entries.isEmpty()) return -1;

                        int lineNum = -1;
                        for (var entry : entries) {
                            if (entry.getStartPc() <= pc) {
                                lineNum = entry.getLineNumber();
                            } else {
                                break;
                            }
                        }
                        return lineNum;
                    }
                }
            }
        } catch (Exception e) {
        }
        return -1;
    }

    private String inferDescriptor(ConcreteValue value) {
        if (value == null || value.isNull()) {
            return "Ljava/lang/Object;";
        }
        switch (value.getTag()) {
            case INT: return "I";
            case LONG: return "J";
            case FLOAT: return "F";
            case DOUBLE: return "D";
            case REFERENCE:
                ObjectInstance ref = value.asReference();
                if (ref != null) {
                    return "L" + ref.getClassName() + ";";
                }
                return "Ljava/lang/Object;";
            default:
                return "Ljava/lang/Object;";
        }
    }

    private Object unwrapValue(ConcreteValue value) {
        if (value == null || value.isNull()) {
            return null;
        }
        switch (value.getTag()) {
            case INT: return value.asInt();
            case LONG: return value.asLong();
            case FLOAT: return value.asFloat();
            case DOUBLE: return value.asDouble();
            case REFERENCE: return value.asReference();
            default: return null;
        }
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public void reset() {
        instructionCount = 0;
        currentFrame = null;
    }
}
