package com.tonic.ui.vm.debugger;

import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import com.tonic.parser.constpool.*;
import com.tonic.ui.core.util.JvmDescriptorFormatter;
import com.tonic.utill.Opcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, stateless disassembler: turns a {@link MethodEntry}'s {@link CodeAttribute} into a list of
 * {@link InstructionEntry} rows plus a PC-to-row index map. Holds no UI state so it can be unit-tested in isolation.
 */
final class BytecodeDisassembler {

    /** Disassembly output: the ordered instruction rows and the PC -> row-index lookup map. */
    static final class Result {
        final List<InstructionEntry> instructions;
        final Map<Integer, Integer> pcToRow;

        Result(List<InstructionEntry> instructions, Map<Integer, Integer> pcToRow) {
            this.instructions = instructions;
            this.pcToRow = pcToRow;
        }
    }

    /**
     * Disassembles a method's code into rows and a PC index map. Returns empty collections when the method has no
     * code or empty bytecode.
     */
    Result disassemble(MethodEntry method) {
        List<InstructionEntry> instructions = new ArrayList<>();
        Map<Integer, Integer> pcToRow = new HashMap<>();

        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return new Result(instructions, pcToRow);
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            return new Result(instructions, pcToRow);
        }

        Map<Integer, Integer> lineNumberTable = buildLineNumberTable(code);
        ConstPool constPool = method.getClassFile().getConstPool();

        int pc = 0;
        int index = 0;
        int currentLine = -1;

        while (pc < bytecode.length) {
            int opcodeValue = Byte.toUnsignedInt(bytecode[pc]);
            Opcode opcode = Opcode.fromCode(opcodeValue);
            String mnemonic = opcode.getMnemonic();

            if (lineNumberTable.containsKey(pc)) {
                currentLine = lineNumberTable.get(pc);
            }

            int instrLength = calculateInstructionLength(bytecode, pc, opcode);
            String operands = formatOperandsEnhanced(bytecode, pc, opcode, constPool);
            InstructionCategory category = categorizeOpcode(opcode);

            pcToRow.put(pc, index);
            instructions.add(new InstructionEntry(
                index,
                pc,
                mnemonic,
                operands,
                currentLine,
                category,
                false
            ));

            pc += instrLength;
            index++;
        }

        return new Result(instructions, pcToRow);
    }

    private InstructionCategory categorizeOpcode(Opcode opcode) {
        if (opcode == null) return InstructionCategory.OTHER;

        String name = opcode.getMnemonic();

        if (name.endsWith("load") || name.endsWith("store") || name.startsWith("iload") ||
            name.startsWith("lload") || name.startsWith("fload") || name.startsWith("dload") ||
            name.startsWith("aload") || name.startsWith("istore") || name.startsWith("lstore") ||
            name.startsWith("fstore") || name.startsWith("dstore") || name.startsWith("astore")) {
            return InstructionCategory.LOAD_STORE;
        }

        if (name.startsWith("invoke") || name.equals("invokedynamic")) {
            return InstructionCategory.INVOKE;
        }

        if (name.startsWith("get") || name.startsWith("put")) {
            return InstructionCategory.FIELD_ACCESS;
        }

        if (name.startsWith("if") || name.equals("goto") || name.equals("goto_w") ||
            name.equals("jsr") || name.equals("jsr_w") || name.equals("ret") ||
            name.startsWith("return") || name.equals("ireturn") || name.equals("lreturn") ||
            name.equals("freturn") || name.equals("dreturn") || name.equals("areturn") ||
            name.equals("tableswitch") || name.equals("lookupswitch") || name.equals("athrow")) {
            return InstructionCategory.CONTROL_FLOW;
        }

        if (name.equals("new") || name.equals("newarray") || name.equals("anewarray") ||
            name.equals("multianewarray") || name.equals("arraylength") ||
            name.equals("checkcast") || name.equals("instanceof") || name.equals("monitorenter") ||
            name.equals("monitorexit")) {
            return InstructionCategory.OBJECT;
        }

        if (name.equals("dup") || name.equals("dup_x1") || name.equals("dup_x2") ||
            name.equals("dup2") || name.equals("dup2_x1") || name.equals("dup2_x2") ||
            name.equals("pop") || name.equals("pop2") || name.equals("swap") || name.equals("nop")) {
            return InstructionCategory.STACK;
        }

        if (name.startsWith("iconst") || name.startsWith("lconst") || name.startsWith("fconst") ||
            name.startsWith("dconst") || name.equals("aconst_null") || name.equals("bipush") ||
            name.equals("sipush") || name.startsWith("ldc")) {
            return InstructionCategory.CONSTANT;
        }

        if (name.startsWith("i") || name.startsWith("l") || name.startsWith("f") || name.startsWith("d") ||
            name.contains("add") || name.contains("sub") || name.contains("mul") || name.contains("div") ||
            name.contains("rem") || name.contains("neg") || name.contains("shl") || name.contains("shr") ||
            name.contains("and") || name.contains("or") || name.contains("xor") || name.contains("2")) {
            return InstructionCategory.ARITHMETIC;
        }

        return InstructionCategory.OTHER;
    }

    private String formatOperandsEnhanced(byte[] bytecode, int pc, Opcode opcode, ConstPool constPool) {
        if (opcode == null) return "";
        int operandSize = opcode.getOperandCount();
        if (operandSize == 0) return "";

        StringBuilder sb = new StringBuilder();
        int idx = pc + 1;

        try {
            switch (opcode) {
                case BIPUSH:
                    if (idx < bytecode.length) {
                        sb.append(bytecode[idx]);
                    }
                    break;
                case SIPUSH:
                    if (idx + 1 < bytecode.length) {
                        short value = (short)(((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF));
                        sb.append(value);
                    }
                    break;
                case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE:
                case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE:
                case IF_ICMPGT: case IF_ICMPLE: case IF_ACMPEQ: case IF_ACMPNE:
                case GOTO: case JSR: case IFNULL: case IFNONNULL:
                    if (idx + 1 < bytecode.length) {
                        short offset = (short)(((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF));
                        int target = pc + offset;
                        sb.append("-> ").append(target);
                    }
                    break;
                case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
                case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
                case RET:
                    if (idx < bytecode.length) {
                        sb.append("local[").append(Byte.toUnsignedInt(bytecode[idx])).append("]");
                    }
                    break;
                case LDC:
                    if (idx < bytecode.length) {
                        int cpIdx = Byte.toUnsignedInt(bytecode[idx]);
                        sb.append(resolveConstant(constPool, cpIdx));
                    }
                    break;
                case LDC_W: case LDC2_W:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveConstant(constPool, cpIdx));
                    }
                    break;
                case GETSTATIC: case PUTSTATIC: case GETFIELD: case PUTFIELD:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveFieldRef(constPool, cpIdx));
                    }
                    break;
                case INVOKEVIRTUAL: case INVOKESPECIAL: case INVOKESTATIC:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveMethodRef(constPool, cpIdx));
                    }
                    break;
                case INVOKEINTERFACE:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveInterfaceMethodRef(constPool, cpIdx));
                    }
                    break;
                case INVOKEDYNAMIC:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveInvokeDynamic(constPool, cpIdx));
                    }
                    break;
                case NEW: case ANEWARRAY: case CHECKCAST: case INSTANCEOF:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveClassRef(constPool, cpIdx));
                    }
                    break;
                case IINC:
                    if (idx + 1 < bytecode.length) {
                        int varIdx = Byte.toUnsignedInt(bytecode[idx]);
                        int constVal = bytecode[idx + 1];
                        sb.append("local[").append(varIdx).append("] += ").append(constVal);
                    }
                    break;
                case NEWARRAY:
                    if (idx < bytecode.length) {
                        int atype = Byte.toUnsignedInt(bytecode[idx]);
                        sb.append(getArrayTypeName(atype)).append("[]");
                    }
                    break;
                case MULTIANEWARRAY:
                    if (idx + 2 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        int dims = Byte.toUnsignedInt(bytecode[idx + 2]);
                        sb.append(resolveClassRef(constPool, cpIdx)).append(" dim=").append(dims);
                    }
                    break;
                default:
                    for (int i = 0; i < operandSize && idx + i < bytecode.length; i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(String.format("%02X", bytecode[idx + i]));
                    }
            }
        } catch (Exception e) {
            sb.append("(error)");
        }

        return sb.toString();
    }

    private String resolveConstant(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof StringRefItem) {
                StringRefItem strRef = (StringRefItem) item;
                int utf8Index = strRef.getValue();
                Item<?> utf8Item = constPool.getItem(utf8Index);
                if (utf8Item instanceof Utf8Item) {
                    String value = ((Utf8Item) utf8Item).getValue();
                    if (value.length() > 30) {
                        value = value.substring(0, 27) + "...";
                    }
                    return "\"" + escapeString(value) + "\"";
                }
            } else if (item instanceof IntegerItem) {
                return String.valueOf(((IntegerItem) item).getValue());
            } else if (item instanceof LongItem) {
                return ((LongItem) item).getValue() + "L";
            } else if (item instanceof FloatItem) {
                return ((FloatItem) item).getValue() + "f";
            } else if (item instanceof DoubleItem) {
                return ((DoubleItem) item).getValue() + "d";
            } else if (item instanceof ClassRefItem) {
                return resolveClassRef(constPool, index);
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveClassRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                String name = classRef.getClassName();
                if (name != null) {
                    return JvmDescriptorFormatter.getSimpleClassName(name);
                }
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveFieldRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) item;
                String owner = fieldRef.getOwner();
                String name = fieldRef.getName();
                return JvmDescriptorFormatter.getSimpleClassName(owner) + "." + name;
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveMethodRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) item;
                String owner = methodRef.getOwner();
                String name = methodRef.getName();
                return JvmDescriptorFormatter.getSimpleClassName(owner) + "." + name + "()";
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveInterfaceMethodRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) item;
                String owner = ifaceRef.getOwner();
                String name = ifaceRef.getName();
                return JvmDescriptorFormatter.getSimpleClassName(owner) + "." + name + "()";
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveInvokeDynamic(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof InvokeDynamicItem) {
                return "invokedynamic#" + index;
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    private Map<Integer, Integer> buildLineNumberTable(CodeAttribute code) {
        Map<Integer, Integer> table = new HashMap<>();
        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof LineNumberTableAttribute) {
                LineNumberTableAttribute lnt = (LineNumberTableAttribute) attr;
                for (LineNumberTableEntry entry : lnt.getLineNumberTable()) {
                    table.put(entry.getStartPc(), entry.getLineNumber());
                }
            }
        }
        return table;
    }

    private int calculateInstructionLength(byte[] code, int pc, Opcode opcode) {
        int baseLength = 1 + opcode.getOperandCount();

        switch (opcode) {
            case TABLESWITCH: {
                int padding = (4 - ((pc + 1) % 4)) % 4;
                int idx = pc + 1 + padding;
                if (idx + 12 > code.length) return baseLength;
                int low = readInt(code, idx + 4);
                int high = readInt(code, idx + 8);
                int count = high - low + 1;
                return 1 + padding + 12 + (count * 4);
            }
            case LOOKUPSWITCH: {
                int padding = (4 - ((pc + 1) % 4)) % 4;
                int idx = pc + 1 + padding;
                if (idx + 8 > code.length) return baseLength;
                int npairs = readInt(code, idx + 4);
                return 1 + padding + 8 + (npairs * 8);
            }
            case WIDE: {
                if (pc + 1 >= code.length) return 1;
                int subOpcode = Byte.toUnsignedInt(code[pc + 1]);
                if (subOpcode == 0x84) {
                    return 6;
                } else {
                    return 4;
                }
            }
            default:
                return baseLength;
        }
    }

    private int readInt(byte[] code, int pos) {
        if (pos + 3 >= code.length) return 0;
        return ((code[pos] & 0xFF) << 24) |
               ((code[pos + 1] & 0xFF) << 16) |
               ((code[pos + 2] & 0xFF) << 8) |
               (code[pos + 3] & 0xFF);
    }

    private String getArrayTypeName(int atype) {
        switch (atype) {
            case 4: return "boolean";
            case 5: return "char";
            case 6: return "float";
            case 7: return "double";
            case 8: return "byte";
            case 9: return "short";
            case 10: return "int";
            case 11: return "long";
            default: return "type" + atype;
        }
    }
}
