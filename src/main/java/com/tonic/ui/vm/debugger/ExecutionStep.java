package com.tonic.ui.vm.debugger;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ExecutionStep {

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

    public ExecutionStep(String className, String methodName, String descriptor,
                         int pc, int lineNumber, String instruction, int callDepth) {
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

    public void setStackBefore(List<String> stack) {
        stackBefore.clear();
        stackBefore.addAll(stack);
    }

    public void setStackAfter(List<String> stack) {
        stackAfter.clear();
        stackAfter.addAll(stack);
    }

    public void setLocals(List<String> localVars) {
        locals.clear();
        locals.addAll(localVars);
    }

    public void setNote(String note) {
        this.note = note;
    }

}
