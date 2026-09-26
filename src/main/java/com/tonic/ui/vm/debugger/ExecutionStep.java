package com.tonic.ui.vm.debugger;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** One executed instruction in an execution trace, with the operand stack before and after and the locals. */
@Getter
public class ExecutionStep
{

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int pc;
    private final int lineNumber;
    private final String instruction;
    private final List<String> stackBefore;
    private final List<String> stackAfter;
    private final List<String> locals;
    private final int callDepth;
    private String note;

    /**
     * Creates a step with empty stack and locals lists.
     *
     * @param className the internal name of the executing class
     * @param methodName the executing method's name
     * @param descriptor the executing method's descriptor
     * @param pc the instruction's bytecode offset
     * @param lineNumber the source line, or zero or less if unknown
     * @param instruction the instruction's text
     * @param callDepth how deep the executing frame is in the call stack
     */
    public ExecutionStep(String className, String methodName, String descriptor, int pc, int lineNumber, String instruction, int callDepth)
    {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.pc = pc;
        this.lineNumber = lineNumber;
        this.instruction = instruction;
        this.callDepth = callDepth;
        this.stackBefore = new ArrayList<>();
        this.stackAfter = new ArrayList<>();
        this.locals = new ArrayList<>();
    }

    /**
     * Replaces the recorded operand stack before the instruction with a copy of the given entries.
     *
     * @param stack the stack entries as text
     */
    public void setStackBefore(List<String> stack)
    {
        stackBefore.clear();
        stackBefore.addAll(stack);
    }

    /**
     * Replaces the recorded operand stack after the instruction with a copy of the given entries.
     *
     * @param stack the stack entries as text
     */
    public void setStackAfter(List<String> stack)
    {
        stackAfter.clear();
        stackAfter.addAll(stack);
    }

    /**
     * Replaces the recorded locals with a copy of the given entries.
     *
     * @param localVars the local variables as text
     */
    public void setLocals(List<String> localVars)
    {
        locals.clear();
        locals.addAll(localVars);
    }

    /**
     * Sets a free-text note shown with the step.
     *
     * @param note the note, or null for none
     */
    public void setNote(String note)
    {
        this.note = note;
    }

}
