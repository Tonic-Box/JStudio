package com.tonic.ui.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.ui.simulation.model.TaintFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaintTrackingListener extends AbstractListener {

    private final List<TaintFlowResult> taintFlows = new ArrayList<>();
    private final Set<Integer> taintedLocals = new HashSet<>();
    private final List<String> currentFlowPath = new ArrayList<>();
    private boolean parametersAreTainted = true;
    private int parameterCount = 0;

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        taintFlows.clear();
        taintedLocals.clear();
        currentFlowPath.clear();

        if (parametersAreTainted && method != null) {
            parameterCount = countParameters(method);
            for (int i = 0; i < parameterCount; i++) {
                taintedLocals.add(i);
            }
        }
    }

    private int countParameters(IRMethod method) {
        if (method.getParameters() == null) return 0;
        return method.getParameters().size();
    }

    public void setParametersAreTainted(boolean tainted) {
        this.parametersAreTainted = tainted;
    }

    @Override
    public void onMethodCall(InvokeInstruction instr, SimulationState state) {
        String owner = instr.getOwner();
        String name = instr.getName();
        String desc = instr.getDescriptor();

        boolean hasTaintedArgs = checkTaintedArgs(instr, state);
        if (!hasTaintedArgs) {
            return;
        }

        TaintSinkInfo sink = detectSink(owner, name, desc);
        if (sink != null) {
            String sourceDesc = "Method parameter (user-controlled input)";
            String sinkDesc = formatMethodRef(owner, name, desc);
            List<String> path = new ArrayList<>(currentFlowPath);
            path.add("→ " + sinkDesc);

            taintFlows.add(new TaintFlowResult(
                    instr,
                    sourceDesc,
                    sinkDesc,
                    path,
                    sink.category
            ));
        }

        if (isTaintSource(owner, name)) {
            currentFlowPath.add(formatMethodRef(owner, name, desc) + " (source)");
            if (instr.getResult() != null) {
                markTainted(instr.getResult());
            }
        }
    }

    private boolean checkTaintedArgs(InvokeInstruction instr, SimulationState state) {
        if (taintedLocals.isEmpty()) {
            return false;
        }

        var args = instr.getMethodArguments();
        for (var arg : args) {
            if (arg instanceof SSAValue) {
                SSAValue ssa = (SSAValue) arg;
                if (taintedLocals.contains(ssa.getId())) {
                    return true;
                }
            }
        }

        return !taintedLocals.isEmpty() && parameterCount > 0;
    }

    private void markTainted(SSAValue value) {
        if (value != null) {
            taintedLocals.add(value.getId());
        }
    }

    private boolean isTaintSource(String owner, String name) {
        if ("java/lang/System".equals(owner)) {
            return "getenv".equals(name) || "getProperty".equals(name);
        }
        if (owner != null && owner.contains("Scanner")) {
            return name != null && name.startsWith("next");
        }
        if (owner != null && owner.contains("Reader")) {
            return "readLine".equals(name) || "read".equals(name);
        }
        if (owner != null && owner.contains("InputStream")) {
            return "read".equals(name);
        }
        return false;
    }

    private TaintSinkInfo detectSink(String owner, String name, String desc) {
        if (owner == null) return null;

        if (owner.contains("Statement") || owner.contains("Connection")) {
            if ("executeQuery".equals(name) || "executeUpdate".equals(name) ||
                    "execute".equals(name) || "prepareStatement".equals(name)) {
                return new TaintSinkInfo(TaintFlow.TaintCategory.SQL_INJECTION,
                        "SQL query execution");
            }
        }

        if ("java/lang/Runtime".equals(owner) || "java/lang/ProcessBuilder".equals(owner)) {
            if ("exec".equals(name) || "command".equals(name) || "start".equals(name)) {
                return new TaintSinkInfo(TaintFlow.TaintCategory.COMMAND_INJECTION,
                        "Command execution");
            }
        }

        if (owner.contains("File") || owner.contains("Path")) {
            if ("new".equals(name) || "<init>".equals(name) ||
                    "get".equals(name) || "resolve".equals(name)) {
                return new TaintSinkInfo(TaintFlow.TaintCategory.PATH_TRAVERSAL,
                        "File path construction");
            }
        }

        if (owner.contains("OutputStream") || owner.contains("Writer")) {
            if ("write".equals(name) || "print".equals(name) || "println".equals(name)) {
                return new TaintSinkInfo(TaintFlow.TaintCategory.FILE_WRITE,
                        "File/output write");
            }
        }

        if (owner.contains("Socket") || owner.contains("URL") || owner.contains("Http")) {
            if ("write".equals(name) || "getOutputStream".equals(name) ||
                    "openConnection".equals(name) || "send".equals(name)) {
                return new TaintSinkInfo(TaintFlow.TaintCategory.NETWORK_OUTPUT,
                        "Network output");
            }
        }

        if (owner.contains("Cipher") || owner.contains("SecretKey") ||
                owner.contains("Mac") || owner.contains("Signature")) {
            if ("init".equals(name) || "doFinal".equals(name) ||
                    "update".equals(name) || "generateSecret".equals(name)) {
                return new TaintSinkInfo(TaintFlow.TaintCategory.CRYPTO_LEAK,
                        "Cryptographic operation");
            }
        }

        return null;
    }

    private String formatMethodRef(String owner, String name, String desc) {
        int lastSlash = owner != null ? owner.lastIndexOf('/') : -1;
        String simpleName = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
        return simpleName + "." + name + "()";
    }

    public List<TaintFlowResult> getTaintFlows() {
        return Collections.unmodifiableList(taintFlows);
    }

    public int getTaintFlowCount() {
        return taintFlows.size();
    }

    private static class TaintSinkInfo {
        final TaintFlow.TaintCategory category;
        final String description;

        TaintSinkInfo(TaintFlow.TaintCategory category, String description) {
            this.category = category;
            this.description = description;
        }
    }

    public static class TaintFlowResult {
        private final InvokeInstruction sinkInstruction;
        private final String sourceDescription;
        private final String sinkDescription;
        private final List<String> flowPath;
        private final TaintFlow.TaintCategory category;

        public TaintFlowResult(InvokeInstruction sinkInstruction, String sourceDescription,
                               String sinkDescription, List<String> flowPath,
                               TaintFlow.TaintCategory category) {
            this.sinkInstruction = sinkInstruction;
            this.sourceDescription = sourceDescription;
            this.sinkDescription = sinkDescription;
            this.flowPath = flowPath;
            this.category = category;
        }

        public InvokeInstruction getSinkInstruction() {
            return sinkInstruction;
        }

        public String getSourceDescription() {
            return sourceDescription;
        }

        public String getSinkDescription() {
            return sinkDescription;
        }

        public List<String> getFlowPath() {
            return flowPath;
        }

        public TaintFlow.TaintCategory getCategory() {
            return category;
        }

        public int getBlockId() {
            if (sinkInstruction != null && sinkInstruction.getBlock() != null) {
                return sinkInstruction.getBlock().getId();
            }
            return -1;
        }
    }
}
