package com.tonic.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.simulation.model.TaintFlow;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A simulation listener that tracks tainted SSA values, seeded from method parameters and from calls to known input sources, and reports calls to known sinks (SQL, command, file, network, crypto) that receive one. */
public class TaintTrackingListener extends AbstractListener
{

    private final List<TaintFlowResult> taintFlows = new ArrayList<>();
    private final Map<Value, String> taintedValues = new HashMap<>();
    private final Map<Integer, String> taintedSlots = new HashMap<>();
    private final List<String> sourcesSeen = new ArrayList<>();
    private boolean parametersAreTainted = true;

    @Override
    public void onSimulationStart(IRMethod method)
    {
        super.onSimulationStart(method);
        taintFlows.clear();
        taintedValues.clear();
        taintedSlots.clear();
        sourcesSeen.clear();

        if (parametersAreTainted && method != null && method.getParameters() != null)
        {
            int slot = method.isStatic() ? 0 : 1;
            int index = 0;
            for (SSAValue param : method.getParameters())
            {
                String origin = "Method parameter " + index + " (user-controlled input)";
                taintedValues.put(param, origin);
                taintedSlots.put(slot, origin);
                slot += param.getType() != null && param.getType().isTwoSlot() ? 2 : 1;
                index++;
            }
        }
    }

    /**
     * Sets whether method parameters are tainted at the start of the next simulation; sources taint their results either way.
     *
     * @param tainted true to treat parameters as untrusted input
     */
    public void setParametersAreTainted(boolean tainted)
    {
        this.parametersAreTainted = tainted;
    }

    @Override
    public void onMethodCall(InvokeInstruction instr, SimulationState state)
    {
        String owner = instr.getOwner();
        String name = instr.getName();
        String desc = instr.getDescriptor();

        String origin = taintOf(instr.getOperands());
        TaintSinkInfo sink = detectSink(owner, name, desc);
        if (origin != null && sink != null)
        {
            String sinkDesc = formatMethodRef(owner, name, desc);
            List<String> path = new ArrayList<>(sourcesSeen);
            path.add("-> " + sinkDesc);
            taintFlows.add(new TaintFlowResult(instr, origin, sinkDesc, path, sink.category));
        }

        if (isTaintSource(owner, name))
        {
            String source = formatMethodRef(owner, name, desc) + " (source)";
            sourcesSeen.add(source);
            if (instr.getResult() != null)
            {
                taintedValues.put(instr.getResult(), source);
            }
        }
    }

    @Override
    public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after)
    {
        if (instr instanceof StoreLocalInstruction)
        {
            StoreLocalInstruction store = (StoreLocalInstruction) instr;
            String origin = taintedValues.get(store.getValue());
            if (origin != null)
            {
                taintedSlots.put(store.getLocalIndex(), origin);
            }
            else
            {
                taintedSlots.remove(store.getLocalIndex());
            }
            return;
        }
        String origin = instr instanceof LoadLocalInstruction ? taintedSlots.get(((LoadLocalInstruction) instr).getLocalIndex()) : taintOf(instr.getOperands());
        if (origin == null)
        {
            return;
        }
        if (instr.getResult() != null)
        {
            taintedValues.putIfAbsent(instr.getResult(), origin);
        }
        if (instr instanceof InvokeInstruction)
        {
            Value receiver = ((InvokeInstruction) instr).getReceiver();
            if (receiver != null)
            {
                taintedValues.putIfAbsent(receiver, origin);
            }
        }
    }

    private String taintOf(List<Value> operands)
    {
        for (Value operand : operands)
        {
            String origin = operand != null ? taintedValues.get(operand) : null;
            if (origin != null)
            {
                return origin;
            }
        }
        return null;
    }

    private boolean isTaintSource(String owner, String name)
    {
        if ("java/lang/System".equals(owner))
        {
            return "getenv".equals(name) || "getProperty".equals(name);
        }
        if (owner != null && owner.contains("Scanner"))
        {
            return name != null && name.startsWith("next");
        }
        if (owner != null && owner.contains("Reader"))
        {
            return "readLine".equals(name) || "read".equals(name);
        }
        if (owner != null && owner.contains("InputStream"))
        {
            return "read".equals(name);
        }
        return false;
    }

    private TaintSinkInfo detectSink(String owner, String name, String desc)
    {
        if (owner == null) return null;

        if (owner.contains("Statement") || owner.contains("Connection"))
        {
            if ("executeQuery".equals(name) || "executeUpdate".equals(name) ||
                    "execute".equals(name) || "prepareStatement".equals(name))
            {
                return new TaintSinkInfo(TaintFlow.TaintCategory.SQL_INJECTION, "SQL query execution");
            }
        }

        if ("java/lang/Runtime".equals(owner) || "java/lang/ProcessBuilder".equals(owner))
        {
            if ("exec".equals(name) || "command".equals(name) || "start".equals(name))
            {
                return new TaintSinkInfo(TaintFlow.TaintCategory.COMMAND_INJECTION, "Command execution");
            }
        }

        if (owner.contains("File") || owner.contains("Path"))
        {
            if ("new".equals(name) || "<init>".equals(name) ||
                    "get".equals(name) || "resolve".equals(name))
            {
                return new TaintSinkInfo(TaintFlow.TaintCategory.PATH_TRAVERSAL, "File path construction");
            }
        }

        if (owner.contains("OutputStream") || owner.contains("Writer"))
        {
            if ("write".equals(name) || "print".equals(name) || "println".equals(name))
            {
                return new TaintSinkInfo(TaintFlow.TaintCategory.FILE_WRITE, "File/output write");
            }
        }

        if (owner.contains("Socket") || owner.contains("URL") || owner.contains("Http"))
        {
            if ("write".equals(name) || "getOutputStream".equals(name) ||
                    "openConnection".equals(name) || "send".equals(name))
            {
                return new TaintSinkInfo(TaintFlow.TaintCategory.NETWORK_OUTPUT, "Network output");
            }
        }

        if (owner.contains("Cipher") || owner.contains("SecretKey") ||
                owner.contains("Mac") || owner.contains("Signature"))
        {
            if ("init".equals(name) || "doFinal".equals(name) ||
                    "update".equals(name) || "generateSecret".equals(name))
            {
                return new TaintSinkInfo(TaintFlow.TaintCategory.CRYPTO_LEAK, "Cryptographic operation");
            }
        }

        return null;
    }

    private String formatMethodRef(String owner, String name, String desc)
    {
        int lastSlash = owner != null ? owner.lastIndexOf('/') : -1;
        String simpleName = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
        return simpleName + "." + name + "()";
    }

    /**
     * Lists the taint flows recorded in the last simulation.
     *
     * @return the taint flows, unmodifiable
     */
    public List<TaintFlowResult> getTaintFlows()
    {
        return Collections.unmodifiableList(taintFlows);
    }

    /**
     * Counts the taint flows recorded in the last simulation.
     *
     * @return the number of taint flows
     */
    public int getTaintFlowCount()
    {
        return taintFlows.size();
    }

    private static class TaintSinkInfo
    {
        final TaintFlow.TaintCategory category;
        final String description;

        TaintSinkInfo(TaintFlow.TaintCategory category, String description)
        {
            this.category = category;
            this.description = description;
        }
    }

    /** One tainted value reaching a sink: the sink call, where the value came from, its category, and the source calls seen before it. */
    @Getter
    public static class TaintFlowResult
    {
        private final InvokeInstruction sinkInstruction;
        private final String sourceDescription;
        private final String sinkDescription;
        private final List<String> flowPath;
        private final TaintFlow.TaintCategory category;

        /**
         * Creates a taint flow result.
         *
         * @param sinkInstruction the sink call
         * @param sourceDescription where the tainted value reaching the sink came from
         * @param sinkDescription the sink as SimpleOwner.name()
         * @param flowPath the source calls seen before the sink, ending with the sink
         * @param category the kind of vulnerability the sink represents
         */
        public TaintFlowResult(InvokeInstruction sinkInstruction, String sourceDescription, String sinkDescription, List<String> flowPath, TaintFlow.TaintCategory category)
        {
            this.sinkInstruction = sinkInstruction;
            this.sourceDescription = sourceDescription;
            this.sinkDescription = sinkDescription;
            this.flowPath = flowPath;
            this.category = category;
        }

        /**
         * Returns the id of the IR block holding the sink call.
         *
         * @return the block id, or -1 if the instruction or its block is missing
         */
        public int getBlockId()
        {
            if (sinkInstruction != null && sinkInstruction.getBlock() != null)
            {
                return sinkInstruction.getBlock().getId();
            }
            return -1;
        }
    }
}
