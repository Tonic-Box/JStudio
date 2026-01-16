package com.tonic.ui.vm.debugger;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ExecutionTrace {

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final LocalDateTime startTime;
    private final List<ExecutionStep> steps;
    private LocalDateTime endTime;
    private String finalResult;
    private boolean completedNormally;

    public ExecutionTrace(String className, String methodName, String descriptor) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.startTime = LocalDateTime.now();
        this.steps = new ArrayList<>();
        this.completedNormally = false;
    }

    public void addStep(ExecutionStep step) {
        steps.add(step);
    }

    public void complete(String result, boolean normal) {
        this.endTime = LocalDateTime.now();
        this.finalResult = result;
        this.completedNormally = normal;
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        sb.append("# Execution Trace\n\n");
        sb.append("## Method Information\n");
        sb.append("- **Class:** `").append(className.replace('/', '.')).append("`\n");
        sb.append("- **Method:** `").append(methodName).append(descriptor).append("`\n");
        sb.append("- **Started:** ").append(startTime.format(dtf)).append("\n");
        if (endTime != null) {
            sb.append("- **Ended:** ").append(endTime.format(dtf)).append("\n");
        }
        sb.append("- **Total Steps:** ").append(steps.size()).append("\n");
        if (finalResult != null) {
            sb.append("- **Result:** ").append(completedNormally ? "Completed" : "Exception").append(" - ").append(finalResult).append("\n");
        }
        sb.append("\n---\n\n");

        sb.append("## Execution Steps\n\n");

        String currentMethod = className + "." + methodName;
        int stepNum = 1;

        for (ExecutionStep step : steps) {
            String stepMethod = step.getClassName() + "." + step.getMethodName();
            if (!stepMethod.equals(currentMethod)) {
                sb.append("\n### -> Entered: `").append(step.getClassName().replace('/', '.')).append(".").append(step.getMethodName()).append("`\n\n");
                currentMethod = stepMethod;
            }

            sb.append("#### Step ").append(stepNum++).append(": PC=").append(step.getPc());
            if (step.getLineNumber() > 0) {
                sb.append(" (Line ").append(step.getLineNumber()).append(")");
            }
            sb.append("\n\n");

            sb.append("**Instruction:** `").append(step.getInstruction()).append("`\n\n");

            if (!step.getStackBefore().isEmpty()) {
                sb.append("**Stack (before):**\n```\n");
                for (String entry : step.getStackBefore()) {
                    sb.append("  ").append(entry).append("\n");
                }
                sb.append("```\n\n");
            }

            if (!step.getStackAfter().isEmpty()) {
                sb.append("**Stack (after):**\n```\n");
                for (String entry : step.getStackAfter()) {
                    sb.append("  ").append(entry).append("\n");
                }
                sb.append("```\n\n");
            }

            if (!step.getLocals().isEmpty()) {
                sb.append("**Locals:**\n```\n");
                for (String entry : step.getLocals()) {
                    sb.append("  ").append(entry).append("\n");
                }
                sb.append("```\n\n");
            }

            if (step.getNote() != null && !step.getNote().isEmpty()) {
                sb.append("**Note:** ").append(step.getNote()).append("\n\n");
            }

            sb.append("---\n\n");
        }

        return sb.toString();
    }

    public String toCompactText() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        sb.append("=== EXECUTION TRACE ===\n");
        sb.append("Method: ").append(className.replace('/', '.')).append(".").append(methodName).append(descriptor).append("\n");
        sb.append("Started: ").append(startTime.format(dtf)).append("\n");
        sb.append("Steps: ").append(steps.size()).append("\n");
        if (finalResult != null) {
            sb.append("Result: ").append(finalResult).append("\n");
        }
        sb.append("\n");

        String currentMethod = "";
        for (int i = 0; i < steps.size(); i++) {
            ExecutionStep step = steps.get(i);
            String stepMethod = step.getClassName() + "." + step.getMethodName();

            if (!stepMethod.equals(currentMethod)) {
                sb.append("\n>> ").append(step.getClassName().replace('/', '.')).append(".").append(step.getMethodName()).append("\n");
                currentMethod = stepMethod;
            }

            sb.append(String.format("[%3d] PC=%3d ", i + 1, step.getPc()));
            if (step.getLineNumber() > 0) {
                sb.append(String.format("L%d ", step.getLineNumber()));
            }
            sb.append(step.getInstruction());

            if (!step.getStackAfter().isEmpty()) {
                sb.append("  -> Stack: [");
                sb.append(String.join(", ", step.getStackAfter()));
                sb.append("]");
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
