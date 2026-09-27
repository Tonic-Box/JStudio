package com.tonic.deobfuscation;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ArrayAccessInstruction;
import com.tonic.analysis.ssa.ir.BranchConditions;
import com.tonic.analysis.ssa.ir.CopyInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.DoubleConstant;
import com.tonic.analysis.ssa.value.FloatConstant;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.LongConstant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.StringConstant;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.service.ConsoleLogService;
import com.tonic.ui.vm.model.MethodCall;
import com.tonic.util.DescriptorParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds the calls a class makes to a method and recovers each call's arguments when they are constants or array literals built right before the call. */
public final class CallSites
{

    private static final Object UNRESOLVED = new Object();

    private CallSites()
    {
    }

    /** One call to the target method: the calling method, the call's bytecode offset, and its arguments when every one is a known constant. */
    public static final class CallSite
    {
        private final MethodEntry caller;
        private final int offset;
        private final Object[] arguments;
        private final String unresolvedReason;

        private CallSite(MethodEntry caller, int offset, Object[] arguments, String unresolvedReason)
        {
            this.caller = caller;
            this.offset = offset;
            this.arguments = arguments;
            this.unresolvedReason = unresolvedReason;
        }

        /** @return the method containing the call */
        public MethodEntry getCaller()
        {
            return caller;
        }

        /** @return the call's bytecode offset, or -1 when unknown */
        public int getOffset()
        {
            return offset;
        }

        /**
         * Tells whether every argument was recovered.
         *
         * @return true when the arguments are known constants
         */
        public boolean isResolved()
        {
            return arguments != null;
        }

        /**
         * Copies the recovered arguments.
         *
         * @return the arguments as host values, arrays as Object arrays of their elements; null when not every argument is a constant
         */
        public Object[] getArguments()
        {
            return arguments != null ? arguments.clone() : null;
        }

        /** @return why the arguments could not be recovered, or null when they were */
        public String getUnresolvedReason()
        {
            return unresolvedReason;
        }

        /**
         * Formats the arguments for display.
         *
         * @return the arguments separated by commas, strings quoted, or the reason they are unknown
         */
        public String describeArguments()
        {
            if (arguments == null)
            {
                return "(" + unresolvedReason + ")";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arguments.length; i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                sb.append(MethodCall.formatValue(arguments[i]));
            }
            return sb.toString();
        }
    }

    /**
     * Finds every call in a class to one method, lifting each method that has code; a method that cannot be lifted is logged and skipped.
     *
     * @param classFile the class to search
     * @param owner the called method's owner, internal name
     * @param name the called method's name
     * @param descriptor the called method's descriptor
     * @return the call sites, in method order and by offset within a method
     */
    public static List<CallSite> find(ClassFile classFile, String owner, String name, String descriptor)
    {
        List<CallSite> sites = new ArrayList<>();
        List<String> types = DescriptorParser.parameterDescriptors(descriptor);
        for (MethodEntry method : classFile.getMethods())
        {
            if (method.getCodeAttribute() == null)
            {
                continue;
            }
            IRMethod ir;
            try
            {
                ir = new SSA(classFile.getConstPool()).lift(method);
            }
            catch (RuntimeException e)
            {
                ConsoleLogService.getInstance().warn("Could not lift " + classFile.getClassName() + "." + method.getName() + method.getDesc() + " to look for decryptor calls: " + e.getMessage());
                continue;
            }
            List<CallSite> inMethod = new ArrayList<>();
            for (IRBlock block : ir.getBlocks())
            {
                for (IRInstruction instr : block.getInstructions())
                {
                    if (instr instanceof InvokeInstruction && isCallTo((InvokeInstruction) instr, owner, name, descriptor))
                    {
                        inMethod.add(resolve(method, (InvokeInstruction) instr, types));
                    }
                }
            }
            inMethod.sort(Comparator.comparingInt(CallSite::getOffset));
            sites.addAll(inMethod);
        }
        return sites;
    }

    private static boolean isCallTo(InvokeInstruction invoke, String owner, String name, String descriptor)
    {
        return owner.equals(invoke.getOwner()) && name.equals(invoke.getName()) && descriptor.equals(invoke.getDescriptor());
    }

    private static CallSite resolve(MethodEntry caller, InvokeInstruction invoke, List<String> types)
    {
        List<Value> args = invoke.getMethodArguments();
        Object[] values = new Object[args.size()];
        for (int i = 0; i < args.size(); i++)
        {
            Object value = constantOf(args.get(i), invoke);
            if (value == UNRESOLVED)
            {
                return new CallSite(caller, invoke.getBytecodeOffset(), null, "argument " + (i + 1) + " (" + DescriptorParser.formatFieldDescriptor(types.get(i)) + ") is not a constant");
            }
            values[i] = value;
        }
        return new CallSite(caller, invoke.getBytecodeOffset(), values, null);
    }

    private static Object constantOf(Value value, IRInstruction call)
    {
        while (value instanceof SSAValue && ((SSAValue) value).getDefinition() instanceof CopyInstruction)
        {
            value = ((CopyInstruction) ((SSAValue) value).getDefinition()).getSource();
        }
        Constant constant = BranchConditions.resolveConstant(value);
        if (constant instanceof NullConstant)
        {
            return null;
        }
        if (constant instanceof IntConstant || constant instanceof LongConstant || constant instanceof FloatConstant || constant instanceof DoubleConstant || constant instanceof StringConstant)
        {
            return constant.getValue();
        }
        if (constant == null && value instanceof SSAValue && ((SSAValue) value).getDefinition() instanceof NewArrayInstruction)
        {
            return arrayLiteral((SSAValue) value, (NewArrayInstruction) ((SSAValue) value).getDefinition(), call);
        }
        return UNRESOLVED;
    }

    private static Object arrayLiteral(SSAValue array, NewArrayInstruction allocation, IRInstruction call)
    {
        IRBlock block = call.getBlock();
        if (allocation.isMultiDimensional() || allocation.getBlock() != block)
        {
            return UNRESOLVED;
        }
        Constant length = BranchConditions.resolveConstant(allocation.getDimensions().get(0));
        if (!(length instanceof IntConstant) || ((IntConstant) length).getValue() < 0)
        {
            return UNRESOLVED;
        }
        String component = allocation.getElementType().getDescriptor();
        Object[] elements = new Object[((IntConstant) length).getValue()];
        for (int i = 0; i < elements.length; i++)
        {
            elements[i] = defaultValue(component);
        }
        List<IRInstruction> instructions = block.getInstructions();
        int callIndex = instructions.indexOf(call);
        List<Value> aliases = new ArrayList<>();
        aliases.add(array);
        for (int a = 0; a < aliases.size(); a++)
        {
            Value alias = aliases.get(a);
            if (!(alias instanceof SSAValue))
            {
                continue;
            }
            for (IRInstruction use : ((SSAValue) alias).getUses())
            {
                int useIndex = instructions.indexOf(use);
                if (use == call || useIndex < 0 || useIndex > callIndex)
                {
                    continue;
                }
                if (use instanceof CopyInstruction)
                {
                    aliases.add(use.getResult());
                }
                else if (use instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) use).isStore() && aliases.contains(((ArrayAccessInstruction) use).getArray()))
                {
                    ArrayAccessInstruction store = (ArrayAccessInstruction) use;
                    Constant index = BranchConditions.resolveConstant(store.getIndex());
                    Object element = constantOf(store.getValue(), call);
                    if (!(index instanceof IntConstant) || element == UNRESOLVED)
                    {
                        return UNRESOLVED;
                    }
                    int at = ((IntConstant) index).getValue();
                    if (at < 0 || at >= elements.length)
                    {
                        return UNRESOLVED;
                    }
                    elements[at] = element;
                }
                else if (!(use instanceof ArrayAccessInstruction) || !((ArrayAccessInstruction) use).isLoad())
                {
                    return UNRESOLVED;
                }
            }
        }
        return elements;
    }

    private static Object defaultValue(String component)
    {
        switch (component)
        {
            case "J":
                return 0L;
            case "F":
                return 0f;
            case "D":
                return 0.0;
            case "Z":
            case "B":
            case "C":
            case "S":
            case "I":
                return 0;
            default:
                return null;
        }
    }
}
