package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.service.ConsoleLogService;
import com.tonic.ui.vm.heap.model.AllocationEvent;
import com.tonic.ui.vm.heap.model.MutationEvent;
import com.tonic.ui.vm.heap.model.ProvenanceInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The bytecode listener that feeds a heap forensics tracker: it records allocations and field and array writes with their provenance. */
public class HeapForensicsListener implements BytecodeListener
{

    private final HeapForensicsTracker tracker;
    private final Deque<StackFrame> callStackFrames = new ArrayDeque<>();

    @Getter
    private int provenanceDepth = 1;
    @Getter
    @Setter
    private boolean trackMutations = true;

    private StackFrame currentFrame;
    private int currentOpcode = -1;
    @Getter
    private volatile long instructionCount = 0;

    /**
     * Creates a listener that records into a tracker.
     *
     * @param tracker the tracker to record into
     */
    public HeapForensicsListener(HeapForensicsTracker tracker)
    {
        this.tracker = tracker;
    }

    /**
     * Sets how many frames of provenance to capture: 0 for none, 1 for the current frame only, more to add callers.
     *
     * @param depth the frame count, clamped to 0 through 10
     */
    public void setProvenanceDepth(int depth)
    {
        this.provenanceDepth = Math.max(0, Math.min(depth, 10));
    }

    @Override
    public void onExecutionStart(MethodEntry entryPoint)
    {
        callStackFrames.clear();
        ConsoleLogService.getInstance().debug("[HeapForensics] === START: " + (entryPoint != null ? entryPoint.getOwnerName() + "." + entryPoint.getName() : "null") + " ===");
        instructionCount = 0;
        tracker.onExecutionStart();
    }

    @Override
    public void onExecutionEnd(BytecodeResult result)
    {
        ConsoleLogService.getInstance().debug("[HeapForensics] === END: instructions=" + instructionCount + ", allocations=" + tracker.getTotalAllocationCount() + ", result=" + (result != null ? result : "null") + " ===");
        if (result != null && result.getException() != null)
        {
            ConsoleLogService.getInstance().error("[HeapForensics] EXCEPTION", result.getException());
        }
        tracker.onExecutionEnd(instructionCount);
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instruction)
    {
        currentFrame = frame;
        currentOpcode = instruction != null ? instruction.getOpcode() : -1;
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction)
    {
        instructionCount++;
        currentFrame = frame;
    }

    @Override
    public void onObjectAllocation(ObjectInstance instance)
    {
        ConsoleLogService.getInstance().debug("[HeapForensics] OBJECT ALLOC: " + instance.getClassName() + " id=" + instance.getId() + " at instruction " + instructionCount);

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
    public void onArrayAllocation(ArrayInstance array)
    {
        ConsoleLogService.getInstance().debug("[HeapForensics] ARRAY ALLOC: " + array.getComponentType() + "[] " + "length=" + array.getLength() + " id=" + array.getId() + " at instruction " + instructionCount);

        ProvenanceInfo provenance = captureProvenance();

        AllocationEvent event = AllocationEvent.builder()
                .objectId(array.getId())
                .className(array.getComponentType() + "[]")
                .opcode(arrayAllocationOpcode(array))
                .instructionCount(instructionCount)
                .provenance(provenance)
                .arrayLength(array.getLength())
                .build();

        tracker.recordAllocation(event, array);
    }

    @Override
    public void onFieldWrite(ObjectInstance instance, String owner, String fieldName, String descriptor, ConcreteValue oldValue, ConcreteValue newValue)
    {
        if (!trackMutations)
        {
            return;
        }

        ProvenanceInfo provenance = provenanceDepth > 0 ? captureProvenance() : null;

        MutationEvent event = MutationEvent.builder()
                .objectId(instance.getId())
                .fieldOwner(owner)
                .fieldName(fieldName)
                .fieldDescriptor(descriptor)
                .oldValue(unwrapValue(oldValue))
                .newValue(unwrapValue(newValue))
                .instructionCount(instructionCount)
                .opcode(0xB5)
                .provenance(provenance)
                .build();

        tracker.recordMutation(event);
    }

    @Override
    public void onArrayWrite(ArrayInstance array, int index, ConcreteValue oldValue, ConcreteValue newValue)
    {
        if (!trackMutations)
        {
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
                .mutationType(MutationEvent.MutationType.forArrayStore(array.getComponentType()))
                .provenance(provenance)
                .build();

        tracker.recordMutation(event);
    }

    @Override
    public void onFramePush(StackFrame frame)
    {
        callStackFrames.push(frame);
        currentFrame = frame;
    }

    @Override
    public void onFramePop(StackFrame frame, ConcreteValue returnValue)
    {
        if (!callStackFrames.isEmpty())
        {
            callStackFrames.pop();
        }
        currentFrame = callStackFrames.peek();
    }

    private ProvenanceInfo captureProvenance()
    {
        if (provenanceDepth == 0 || currentFrame == null)
        {
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

        if (provenanceDepth > 1)
        {
            List<ProvenanceInfo.StackFrameInfo> callStack = captureCallStack(provenanceDepth - 1);
            builder.callStack(callStack);
        }

        return builder.build();
    }

    private List<ProvenanceInfo.StackFrameInfo> captureCallStack(int maxFrames)
    {
        List<ProvenanceInfo.StackFrameInfo> frames = new ArrayList<>();
        int count = 0;
        for (StackFrame frame : callStackFrames)
        {
            if (frame == currentFrame)
            {
                continue;
            }
            if (count >= maxFrames)
            {
                break;
            }
            MethodEntry method = frame.getMethod();
            int pc = frame.getPC();
            int lineNumber = getLineNumber(method, pc);
            frames.add(new ProvenanceInfo.StackFrameInfo(method.getOwnerName(), method.getName(), method.getDesc(), pc, lineNumber));
            count++;
        }
        return frames;
    }

    private int getLineNumber(MethodEntry method, int pc)
    {
        try
        {
            var codeAttr = method.getCodeAttribute();
            if (codeAttr != null)
            {
                for (var attr : codeAttr.getAttributes())
                {
                    if (attr instanceof LineNumberTableAttribute)
                    {
                        var lnt = (LineNumberTableAttribute) attr;
                        var entries = lnt.getLineNumberTable();
                        if (entries == null || entries.isEmpty()) return -1;

                        int lineNum = -1;
                        for (var entry : entries)
                        {
                            if (entry.getStartPc() <= pc)
                            {
                                lineNum = entry.getLineNumber();
                            }
                            else
                            {
                                break;
                            }
                        }
                        return lineNum;
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return -1;
    }

    private int arrayAllocationOpcode(ArrayInstance array)
    {
        if (currentOpcode == 0xBC || currentOpcode == 0xBD || currentOpcode == 0xC5)
        {
            return currentOpcode;
        }
        return array.isPrimitiveArray() ? 0xBC : 0xBD;
    }

    private Object unwrapValue(ConcreteValue value)
    {
        if (value == null || value.isNull())
        {
            return null;
        }
        switch (value.getTag())
        {
            case INT:
                return value.asInt();
            case LONG:
                return value.asLong();
            case FLOAT:
                return value.asFloat();
            case DOUBLE:
                return value.asDouble();
            case REFERENCE:
                return value.asReference();
            default:
                return null;
        }
    }

    /** Clears the instruction count and the tracked call stack. */
    public void reset()
    {
        instructionCount = 0;
        currentFrame = null;
        currentOpcode = -1;
        callStackFrames.clear();
    }
}
