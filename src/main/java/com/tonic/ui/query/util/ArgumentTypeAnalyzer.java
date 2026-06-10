package com.tonic.ui.query.util;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.ui.query.ast.ArgumentType;

/**
 * Static helpers for classifying invoke-instruction arguments by how they are produced; backs the
 * {@code arg(n).type}/{@code arg(n).kind} attributes via {@link com.tonic.ui.query.eval.ArgValueResolver}.
 */
public class ArgumentTypeAnalyzer {

    public static boolean isInvokeInstruction(Instruction instr) {
        return instr instanceof InvokeVirtualInstruction
            || instr instanceof InvokeStaticInstruction
            || instr instanceof InvokeSpecialInstruction
            || instr instanceof InvokeInterfaceInstruction
            || instr instanceof InvokeDynamicInstruction;
    }

    public static int getArgumentCount(Instruction invokeInstr) {
        String desc = getDescriptor(invokeInstr);
        if (desc == null) {
            return 0;
        }
        return countDescriptorArguments(desc);
    }

    public static int countDescriptorArguments(String desc) {
        if (desc == null || !desc.startsWith("(")) {
            return 0;
        }

        int count = 0;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end < 0) break;
                i = end + 1;
                count++;
            } else if (c == '[') {
                i++;
            } else if (c == 'J' || c == 'D') {
                i++;
                count++;
            } else {
                i++;
                count++;
            }
        }

        return count;
    }

    public static Instruction findArgumentProducer(CodeWriter codeWriter, int invokeIndex, int argIndex, int totalArgs, Instruction invokeInstr) {
        int slotsToSkip = 0;
        String desc = getDescriptor(invokeInstr);
        if (desc != null) {
            for (int i = argIndex + 1; i < totalArgs; i++) {
                slotsToSkip += getArgumentSlotSize(desc, i);
            }
        } else {
            slotsToSkip = totalArgs - argIndex - 1;
        }

        int stackDepth = slotsToSkip;

        for (int idx = invokeIndex - 1; idx >= 0; idx--) {
            Instruction instr = codeWriter.getInstructionAt(idx);
            if (instr == null) break;

            int change = instr.getStackChange();

            if (change > 0 && stackDepth == 0) {
                return instr;
            }

            stackDepth -= change;

            if (isControlFlow(instr)) {
                break;
            }
        }

        return null;
    }

    public static String getDescriptor(Instruction invokeInstr) {
        if (invokeInstr instanceof InvokeVirtualInstruction) {
            return ((InvokeVirtualInstruction) invokeInstr).getMethodDescriptor();
        } else if (invokeInstr instanceof InvokeStaticInstruction) {
            return ((InvokeStaticInstruction) invokeInstr).getMethodDescriptor();
        } else if (invokeInstr instanceof InvokeSpecialInstruction) {
            return ((InvokeSpecialInstruction) invokeInstr).getMethodDescriptor();
        } else if (invokeInstr instanceof InvokeInterfaceInstruction) {
            return ((InvokeInterfaceInstruction) invokeInstr).getMethodDescriptor();
        } else if (invokeInstr instanceof InvokeDynamicInstruction) {
            String resolved = ((InvokeDynamicInstruction) invokeInstr).resolveMethod();
            int parenIdx = resolved.indexOf('(');
            if (parenIdx >= 0) {
                return resolved.substring(parenIdx);
            }
            return null;
        }
        return null;
    }

    private static int getArgumentSlotSize(String desc, int argIndex) {
        if (desc == null || !desc.startsWith("(")) {
            return 1;
        }

        int count = 0;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (count == argIndex) {
                if (c == 'J' || c == 'D') {
                    return 2;
                }
                return 1;
            }

            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end < 0) break;
                i = end + 1;
                count++;
            } else if (c == '[') {
                i++;
            } else {
                i++;
                count++;
            }
        }

        return 1;
    }

    private static boolean isControlFlow(Instruction instr) {
        return instr instanceof GotoInstruction
            || instr instanceof ConditionalBranchInstruction
            || instr instanceof ReturnInstruction
            || instr instanceof TableSwitchInstruction
            || instr instanceof LookupSwitchInstruction
            || instr instanceof ATHROWInstruction;
    }

    public static ArgumentType classifyInstruction(Instruction instr) {
        if (instr instanceof LdcInstruction
            || instr instanceof LdcWInstruction
            || instr instanceof Ldc2WInstruction
            || instr instanceof IConstInstruction
            || instr instanceof LConstInstruction
            || instr instanceof FConstInstruction
            || instr instanceof DConstInstruction
            || instr instanceof AConstNullInstruction
            || instr instanceof BipushInstruction
            || instr instanceof SipushInstruction) {
            return ArgumentType.LITERAL;
        }

        if (instr instanceof GetFieldInstruction) {
            return ArgumentType.FIELD;
        }

        if (instr instanceof ILoadInstruction
            || instr instanceof LLoadInstruction
            || instr instanceof FLoadInstruction
            || instr instanceof DLoadInstruction
            || instr instanceof ALoadInstruction) {
            return ArgumentType.LOCAL;
        }

        if (isInvokeInstruction(instr)) {
            return ArgumentType.CALL;
        }

        if (instr instanceof ArithmeticInstruction
            || instr instanceof ArithmeticShiftInstruction
            || instr instanceof ConversionInstruction
            || instr instanceof ArrayLengthInstruction
            || instr instanceof AALoadInstruction
            || instr instanceof IALoadInstruction
            || instr instanceof LALoadInstruction
            || instr instanceof FALoadInstruction
            || instr instanceof DALoadInstruction
            || instr instanceof BALOADInstruction
            || instr instanceof CALoadInstruction
            || instr instanceof SALoadInstruction
            || instr instanceof CheckCastInstruction
            || instr instanceof NewInstruction
            || instr instanceof NewArrayInstruction
            || instr instanceof ANewArrayInstruction) {
            return ArgumentType.DYNAMIC;
        }

        return ArgumentType.ANY;
    }
}
