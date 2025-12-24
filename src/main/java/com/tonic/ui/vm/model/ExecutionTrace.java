package com.tonic.ui.vm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExecutionTrace {

    private final String entryClass;
    private final String entryMethod;
    private final String entryDescriptor;
    private final long startTime;
    private long endTime;
    private final List<MethodCall> allCalls;

    public ExecutionTrace(String entryClass, String entryMethod, String entryDescriptor) {
        this.entryClass = entryClass;
        this.entryMethod = entryMethod;
        this.entryDescriptor = entryDescriptor;
        this.startTime = System.nanoTime();
        this.allCalls = new ArrayList<>();
    }

    public String getEntryClass() {
        return entryClass;
    }

    public String getEntryMethod() {
        return entryMethod;
    }

    public String getEntryDescriptor() {
        return entryDescriptor;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDurationNanos() {
        return endTime - startTime;
    }

    public long getDurationMs() {
        return getDurationNanos() / 1_000_000;
    }

    public void addCall(MethodCall call) {
        allCalls.add(call);
    }

    public List<MethodCall> getAllCalls() {
        return Collections.unmodifiableList(allCalls);
    }

    public List<MethodCall> getRootCalls() {
        return allCalls.stream()
            .filter(call -> call.getDepth() == 0)
            .collect(Collectors.toList());
    }

    public int getTotalCallCount() {
        return allCalls.size();
    }

    public int getMaxDepth() {
        return allCalls.stream()
            .mapToInt(MethodCall::getDepth)
            .max()
            .orElse(0);
    }

    public String getEntrySignature() {
        return entryClass.replace('/', '.') + "." + entryMethod + entryDescriptor;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trace: ").append(getEntrySignature()).append("\n");
        sb.append("Duration: ").append(getDurationMs()).append("ms\n");
        sb.append("Total calls: ").append(getTotalCallCount()).append("\n");
        sb.append("Max depth: ").append(getMaxDepth());
        return sb.toString();
    }

    public String toTreeString() {
        StringBuilder sb = new StringBuilder();
        for (MethodCall call : allCalls) {
            sb.append(call.getIndentedString()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ExecutionTrace{" +
            "entry=" + getEntrySignature() +
            ", calls=" + allCalls.size() +
            ", duration=" + getDurationMs() + "ms" +
            '}';
    }
}
