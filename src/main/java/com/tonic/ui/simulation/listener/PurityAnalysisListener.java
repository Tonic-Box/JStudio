package com.tonic.ui.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ArrayStoreInstruction;
import com.tonic.analysis.ssa.ir.GetFieldInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.PutFieldInstruction;
import com.tonic.ui.simulation.model.MethodPurity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PurityAnalysisListener extends AbstractListener {

    @Getter
    private int fieldReadCount;
    @Getter
    private int fieldWriteCount;
    private int staticFieldWriteCount;
    @Getter
    private int arrayWriteCount;
    @Getter
    private int methodCallCount;
    private int nativeCallCount;
    private final List<String> impureReasons = new ArrayList<>();
    private final Set<String> calledMethods = new HashSet<>();

    private static final Set<String> KNOWN_PURE_METHODS = new HashSet<>();
    private static final Set<String> KNOWN_IMPURE_PREFIXES = new HashSet<>();

    static {
        KNOWN_PURE_METHODS.add("java/lang/String.length()I");
        KNOWN_PURE_METHODS.add("java/lang/String.charAt(I)C");
        KNOWN_PURE_METHODS.add("java/lang/String.substring(II)Ljava/lang/String;");
        KNOWN_PURE_METHODS.add("java/lang/String.equals(Ljava/lang/Object;)Z");
        KNOWN_PURE_METHODS.add("java/lang/String.hashCode()I");
        KNOWN_PURE_METHODS.add("java/lang/Math.abs(I)I");
        KNOWN_PURE_METHODS.add("java/lang/Math.max(II)I");
        KNOWN_PURE_METHODS.add("java/lang/Math.min(II)I");
        KNOWN_PURE_METHODS.add("java/lang/Integer.valueOf(I)Ljava/lang/Integer;");
        KNOWN_PURE_METHODS.add("java/lang/Long.valueOf(J)Ljava/lang/Long;");

        KNOWN_IMPURE_PREFIXES.add("java/io/");
        KNOWN_IMPURE_PREFIXES.add("java/net/");
        KNOWN_IMPURE_PREFIXES.add("java/nio/");
        KNOWN_IMPURE_PREFIXES.add("java/sql/");
        KNOWN_IMPURE_PREFIXES.add("java/lang/Runtime");
        KNOWN_IMPURE_PREFIXES.add("java/lang/ProcessBuilder");
        KNOWN_IMPURE_PREFIXES.add("java/lang/Thread");
    }

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        fieldReadCount = 0;
        fieldWriteCount = 0;
        staticFieldWriteCount = 0;
        arrayWriteCount = 0;
        methodCallCount = 0;
        nativeCallCount = 0;
        impureReasons.clear();
        calledMethods.clear();
    }

    @Override
    public void onFieldRead(GetFieldInstruction instr, SimulationState state) {
        fieldReadCount++;
    }

    @Override
    public void onFieldWrite(PutFieldInstruction instr, SimulationState state) {
        fieldWriteCount++;
        if (instr.isStatic()) {
            staticFieldWriteCount++;
            impureReasons.add("Writes static field: " + instr.getOwner() + "." + instr.getName());
        } else {
            impureReasons.add("Writes instance field: " + instr.getName());
        }
    }

    @Override
    public void onArrayWrite(ArrayStoreInstruction instr, SimulationState state) {
        arrayWriteCount++;
        impureReasons.add("Writes to array");
    }

    @Override
    public void onMethodCall(InvokeInstruction instr, SimulationState state) {
        methodCallCount++;
        String methodRef = instr.getOwner() + "." + instr.getName() + instr.getDescriptor();
        calledMethods.add(methodRef);

        if (isKnownImpure(instr.getOwner(), instr.getName())) {
            impureReasons.add("Calls impure method: " + formatMethodRef(instr));
        }
    }

    private boolean isKnownPure(String owner, String name, String desc) {
        String methodRef = owner + "." + name + desc;
        return KNOWN_PURE_METHODS.contains(methodRef);
    }

    private boolean isKnownImpure(String owner, String name) {
        if (owner == null) return false;

        for (String prefix : KNOWN_IMPURE_PREFIXES) {
            if (owner.startsWith(prefix)) {
                return true;
            }
        }

        return "println".equals(name) || "print".equals(name) || "write".equals(name);
    }

    private String formatMethodRef(InvokeInstruction instr) {
        String owner = instr.getOwner();
        int lastSlash = owner != null ? owner.lastIndexOf('/') : -1;
        String simpleName = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
        return simpleName + "." + instr.getName() + "()";
    }

    public MethodPurity.PurityLevel computePurityLevel() {
        if (fieldWriteCount > 0 || arrayWriteCount > 0 || staticFieldWriteCount > 0) {
            return MethodPurity.PurityLevel.WRITES_FIELDS;
        }

        boolean hasImpureCalls = false;
        for (String method : calledMethods) {
            for (String prefix : KNOWN_IMPURE_PREFIXES) {
                if (method.startsWith(prefix)) {
                    hasImpureCalls = true;
                    break;
                }
            }
        }

        if (hasImpureCalls || nativeCallCount > 0) {
            return MethodPurity.PurityLevel.HAS_SIDE_EFFECTS;
        }

        if (fieldReadCount > 0) {
            return MethodPurity.PurityLevel.READS_FIELDS;
        }

        return MethodPurity.PurityLevel.PURE;
    }

    public List<String> getImpureReasons() {
        return new ArrayList<>(impureReasons);
    }

    public Set<String> getCalledMethods() {
        return new HashSet<>(calledMethods);
    }

    public boolean isPure() {
        return computePurityLevel() == MethodPurity.PurityLevel.PURE;
    }
}
