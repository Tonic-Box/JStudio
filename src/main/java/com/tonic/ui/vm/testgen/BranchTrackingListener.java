package com.tonic.ui.vm.testgen;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.instruction.ConditionalBranchInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.LookupSwitchInstruction;
import com.tonic.analysis.instruction.TableSwitchInstruction;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A bytecode listener that records the branch decisions taken during one execution, capping each branch site at 100 recorded visits so loops do not flood the path. */
public class BranchTrackingListener implements BytecodeListener
{

    private static final int MAX_LOOP_ITERATIONS = 100;

    private final List<BranchDecision> branchPath = new ArrayList<>();
    private final Map<String, Integer> branchVisitCounts = new HashMap<>();

    private int pendingBranchPC = -1;
    private String pendingMethodKey = null;

    /** One branch decision: the method, the branch instruction's pc, and the pc execution actually continued at. */
    @Getter
    public static class BranchDecision
    {
        private final String methodKey;
        private final int branchPC;
        private final int targetPC;

        /**
         * Creates a branch decision.
         *
         * @param methodKey the method as owner.name plus descriptor
         * @param branchPC the pc of the branch instruction
         * @param targetPC the pc execution continued at
         */
        public BranchDecision(String methodKey, int branchPC, int targetPC)
        {
            this.methodKey = methodKey;
            this.branchPC = branchPC;
            this.targetPC = targetPC;
        }

        @Override
        public String toString()
        {
            return methodKey + "@" + branchPC + "->" + targetPC;
        }
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instr)
    {
        boolean isBranchInstruction = isBranchInstruction(instr);

        if (isBranchInstruction)
        {
            String methodKey = frame.getMethod().getOwnerName() + "." +
                    frame.getMethod().getName() +
                    frame.getMethod().getDesc();
            int pc = frame.getPC();

            String globalKey = methodKey + "@" + pc;
            int visitCount = branchVisitCounts.getOrDefault(globalKey, 0);

            if (visitCount < MAX_LOOP_ITERATIONS)
            {
                pendingBranchPC = pc;
                pendingMethodKey = methodKey;
                branchVisitCounts.put(globalKey, visitCount + 1);
            }
            else
            {
                pendingBranchPC = -1;
                pendingMethodKey = null;
            }
        }
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instr)
    {
        if (pendingBranchPC >= 0 && pendingMethodKey != null)
        {
            int actualTarget = frame.getPC();
            branchPath.add(new BranchDecision(pendingMethodKey, pendingBranchPC, actualTarget));
            pendingBranchPC = -1;
            pendingMethodKey = null;
        }
    }

    private boolean isBranchInstruction(Instruction instr)
    {
        return instr instanceof ConditionalBranchInstruction ||
                instr instanceof GotoInstruction ||
                instr instanceof TableSwitchInstruction ||
                instr instanceof LookupSwitchInstruction;
    }

    /**
     * Copies the recorded decisions.
     *
     * @return a copy of the recorded decisions, in execution order
     */
    public List<BranchDecision> getBranchPath()
    {
        return new ArrayList<>(branchPath);
    }

    /**
     * Counts the recorded decisions.
     *
     * @return the number of recorded decisions
     */
    public int getBranchCount()
    {
        return branchPath.size();
    }

    /**
     * Counts the distinct branch sites visited.
     *
     * @return the number of distinct branch sites among the recorded decisions
     */
    public int getUniqueBranchPoints()
    {
        return (int) branchPath.stream()
                .map(b -> b.methodKey + "@" + b.branchPC)
                .distinct()
                .count();
    }

    /**
     * Builds a signature identifying the path taken.
     *
     * @return the decisions joined with a bar, a 16-hex-digit hash when that exceeds 200 characters, or NO_BRANCHES when none were recorded
     */
    public String getPathSignature()
    {
        if (branchPath.isEmpty())
        {
            return "NO_BRANCHES";
        }

        StringBuilder sb = new StringBuilder();
        for (BranchDecision decision : branchPath)
        {
            if (sb.length() > 0) sb.append("|");
            sb.append(decision.toString());
        }

        if (sb.length() > 200)
        {
            return computeHash(sb.toString());
        }

        return sb.toString();
    }

    /**
     * Hashes the path signature.
     *
     * @return a 16-hex-digit hash of the path signature
     */
    public String getPathHash()
    {
        return computeHash(getPathSignature());
    }

    private String computeHash(String input)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest)
            {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        }
        catch (Exception e)
        {
            return String.valueOf(input.hashCode());
        }
    }

    /** Clears the recorded path and visit counts so the listener can track a new execution. */
    public void reset()
    {
        branchPath.clear();
        branchVisitCounts.clear();
        pendingBranchPC = -1;
        pendingMethodKey = null;
    }

    /**
     * Describes the recorded path for display.
     *
     * @return the branch point and decision counts, with per-method counts when three or fewer methods branched, or "No branches taken"
     */
    public String getSummary()
    {
        if (branchPath.isEmpty())
        {
            return "No branches taken";
        }

        int uniquePoints = getUniqueBranchPoints();
        int totalDecisions = branchPath.size();

        Map<String, Integer> methodBranches = new HashMap<>();
        for (BranchDecision decision : branchPath)
        {
            String method = decision.methodKey;
            int lastDot = method.lastIndexOf('.');
            int parenIdx = method.indexOf('(');
            if (lastDot >= 0 && parenIdx > lastDot)
            {
                method = method.substring(lastDot + 1, parenIdx);
            }
            methodBranches.merge(method, 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(uniquePoints).append(" branch points, ");
        sb.append(totalDecisions).append(" decisions");

        if (methodBranches.size() <= 3)
        {
            sb.append(" (");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : methodBranches.entrySet())
            {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                first = false;
            }
            sb.append(")");
        }

        return sb.toString();
    }
}
