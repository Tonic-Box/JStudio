package com.tonic.ui.vm.testgen;

import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.instruction.ConditionalBranchInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.LookupSwitchInstruction;
import com.tonic.analysis.instruction.TableSwitchInstruction;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BranchTrackingListener implements BytecodeEngine.BytecodeListener {

    private static final int MAX_LOOP_ITERATIONS = 100;

    private final List<BranchDecision> branchPath = new ArrayList<>();
    private final Map<Integer, Integer> branchVisitCounts = new HashMap<>();

    private int pendingBranchPC = -1;
    private String pendingMethodKey = null;
    private boolean isBranchInstruction = false;

    public static class BranchDecision {
        private final String methodKey;
        private final int branchPC;
        private final int targetPC;

        public BranchDecision(String methodKey, int branchPC, int targetPC) {
            this.methodKey = methodKey;
            this.branchPC = branchPC;
            this.targetPC = targetPC;
        }

        public String getMethodKey() {
            return methodKey;
        }

        public int getBranchPC() {
            return branchPC;
        }

        public int getTargetPC() {
            return targetPC;
        }

        @Override
        public String toString() {
            return methodKey + "@" + branchPC + "->" + targetPC;
        }
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instr) {
        isBranchInstruction = isBranchInstruction(instr);

        if (isBranchInstruction) {
            String methodKey = frame.getMethod().getOwnerName() + "." +
                               frame.getMethod().getName() +
                               frame.getMethod().getDesc();
            int pc = frame.getPC();

            int globalKey = (methodKey.hashCode() * 31) + pc;
            int visitCount = branchVisitCounts.getOrDefault(globalKey, 0);

            if (visitCount < MAX_LOOP_ITERATIONS) {
                pendingBranchPC = pc;
                pendingMethodKey = methodKey;
                branchVisitCounts.put(globalKey, visitCount + 1);
            } else {
                pendingBranchPC = -1;
                pendingMethodKey = null;
            }
        }
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instr) {
        if (pendingBranchPC >= 0 && pendingMethodKey != null) {
            int actualTarget = frame.getPC();
            branchPath.add(new BranchDecision(pendingMethodKey, pendingBranchPC, actualTarget));
            pendingBranchPC = -1;
            pendingMethodKey = null;
        }
    }

    private boolean isBranchInstruction(Instruction instr) {
        return instr instanceof ConditionalBranchInstruction ||
               instr instanceof GotoInstruction ||
               instr instanceof TableSwitchInstruction ||
               instr instanceof LookupSwitchInstruction;
    }

    public List<BranchDecision> getBranchPath() {
        return new ArrayList<>(branchPath);
    }

    public int getBranchCount() {
        return branchPath.size();
    }

    public int getUniqueBranchPoints() {
        return (int) branchPath.stream()
            .map(b -> b.methodKey + "@" + b.branchPC)
            .distinct()
            .count();
    }

    public String getPathSignature() {
        if (branchPath.isEmpty()) {
            return "NO_BRANCHES";
        }

        StringBuilder sb = new StringBuilder();
        for (BranchDecision decision : branchPath) {
            if (sb.length() > 0) sb.append("|");
            sb.append(decision.toString());
        }

        if (sb.length() > 200) {
            return computeHash(sb.toString());
        }

        return sb.toString();
    }

    public String getPathHash() {
        return computeHash(getPathSignature());
    }

    private String computeHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public void reset() {
        branchPath.clear();
        branchVisitCounts.clear();
        pendingBranchPC = -1;
        pendingMethodKey = null;
    }

    public String getSummary() {
        if (branchPath.isEmpty()) {
            return "No branches taken";
        }

        int uniquePoints = getUniqueBranchPoints();
        int totalDecisions = branchPath.size();

        Map<String, Integer> methodBranches = new HashMap<>();
        for (BranchDecision decision : branchPath) {
            String method = decision.methodKey;
            int lastDot = method.lastIndexOf('.');
            int parenIdx = method.indexOf('(');
            if (lastDot >= 0 && parenIdx > lastDot) {
                method = method.substring(lastDot + 1, parenIdx);
            }
            methodBranches.merge(method, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(uniquePoints).append(" branch points, ");
        sb.append(totalDecisions).append(" decisions");

        if (methodBranches.size() <= 3) {
            sb.append(" (");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : methodBranches.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                first = false;
            }
            sb.append(")");
        }

        return sb.toString();
    }
}
