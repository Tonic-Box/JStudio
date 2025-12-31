package com.tonic.ui.deobfuscation.detection;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.ui.deobfuscation.model.DecryptorCandidate;
import com.tonic.ui.deobfuscation.model.DecryptorCandidate.DecryptorType;

import java.util.ArrayList;
import java.util.List;

public class DecryptorDetector {

    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_PRIVATE = 0x0002;

    public List<DecryptorCandidate> scan(ClassFile classFile) {
        List<DecryptorCandidate> candidates = new ArrayList<>();

        for (MethodEntry method : classFile.getMethods()) {
            DecryptorCandidate candidate = analyzeMethod(classFile, method);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return candidates;
    }

    private DecryptorCandidate analyzeMethod(ClassFile classFile, MethodEntry method) {
        String name = method.getName();
        String desc = method.getDesc();
        int access = method.getAccess();

        if (name.equals("<init>") || name.equals("<clinit>")) {
            return null;
        }

        if ((access & ACC_STATIC) == 0) {
            return null;
        }

        if (!desc.endsWith(")Ljava/lang/String;") && !desc.endsWith(")[B")) {
            return null;
        }

        DecryptorType type = DecryptorType.fromDescriptor(desc);
        double confidence = 0.0;
        List<String> indicators = new ArrayList<>();

        if (type == DecryptorType.STRING_TO_STRING) {
            confidence = 0.6;
            indicators.add("String→String signature");
        } else if (type == DecryptorType.INT_TO_STRING) {
            confidence = 0.5;
            indicators.add("int→String signature (index-based)");
        } else if (type == DecryptorType.BYTES_TO_STRING) {
            confidence = 0.4;
            indicators.add("byte[]→String signature");
        } else if (type == DecryptorType.STRING_TO_BYTES) {
            confidence = 0.4;
            indicators.add("String→byte[] signature");
        } else if (desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) {
            type = DecryptorType.STRING_INT_TO_STRING;
            confidence = 0.55;
            indicators.add("String,int→String signature");
        } else {
            return null;
        }

        if ((access & ACC_PRIVATE) != 0) {
            confidence += 0.1;
            indicators.add("Private method");
        }

        if (isSuspiciousName(name)) {
            confidence += 0.15;
            indicators.add("Suspicious method name");
        }

        CodeAttribute code = method.getCodeAttribute();
        if (code != null) {
            BytecodeAnalysis analysis = analyzeBytecode(code);

            if (analysis.hasXorOperations) {
                confidence += 0.2;
                indicators.add("Contains XOR operations");
            }
            if (analysis.hasArrayOperations) {
                confidence += 0.1;
                indicators.add("Contains array operations");
            }
            if (analysis.hasLoops) {
                confidence += 0.05;
                indicators.add("Contains loops");
            }
            if (analysis.createsString) {
                confidence += 0.1;
                indicators.add("Creates String object");
            }
        }

        confidence = Math.min(1.0, confidence);

        if (confidence < 0.3) {
            return null;
        }

        DecryptorCandidate candidate = new DecryptorCandidate(classFile, method, type, confidence);
        for (String indicator : indicators) {
            candidate.addIndicator(indicator);
        }

        return candidate;
    }

    private boolean isSuspiciousName(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("decrypt") || lower.contains("decode") ||
            lower.contains("deobfusc") || lower.contains("unscramble") ||
            lower.contains("unhide") || lower.contains("reveal")) {
            return true;
        }
        if (name.length() <= 2) {
            return true;
        }
        return name.matches("^[a-z]$") || name.matches("^[a-z]{2}$");
    }

    private BytecodeAnalysis analyzeBytecode(CodeAttribute code) {
        BytecodeAnalysis analysis = new BytecodeAnalysis();

        try {
            byte[] bytecode = code.getCode();
            if (bytecode == null || bytecode.length == 0) {
                return analysis;
            }

            int pc = 0;
            int prevPc = -1;
            while (pc < bytecode.length) {
                int opcode = Byte.toUnsignedInt(bytecode[pc]);
                int instrLen = getInstructionLength(opcode, bytecode, pc);

                if (opcode == 0x82) {
                    analysis.hasXorOperations = true;
                }

                if ((opcode >= 0x2E && opcode <= 0x35) || (opcode >= 0x4F && opcode <= 0x56)) {
                    analysis.hasArrayOperations = true;
                }

                if (opcode >= 0x99 && opcode <= 0xA7 && instrLen >= 3) {
                    int branchOffset = ((bytecode[pc + 1] & 0xFF) << 8) | (bytecode[pc + 2] & 0xFF);
                    if (branchOffset < 0 || (pc + branchOffset) < pc) {
                        analysis.hasLoops = true;
                    }
                }

                if (opcode == 0xB7 || opcode == 0xB8) {
                    analysis.createsString = true;
                }

                prevPc = pc;
                pc += instrLen;

                if (pc <= prevPc) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        return analysis;
    }

    private int getInstructionLength(int opcode, byte[] bytecode, int pc) {
        switch (opcode) {
            case 0xAA: return getTableSwitchLength(bytecode, pc);
            case 0xAB: return getLookupSwitchLength(bytecode, pc);
            case 0xC4: return getWideLength(bytecode, pc);
            default:
                int baseLen = OPCODE_LENGTHS[opcode];
                return baseLen > 0 ? baseLen : 1;
        }
    }

    private int getTableSwitchLength(byte[] bytecode, int pc) {
        int padding = (4 - ((pc + 1) % 4)) % 4;
        int base = pc + 1 + padding;
        if (base + 12 > bytecode.length) return 1;
        int low = readInt(bytecode, base + 4);
        int high = readInt(bytecode, base + 8);
        return 1 + padding + 12 + (high - low + 1) * 4;
    }

    private int getLookupSwitchLength(byte[] bytecode, int pc) {
        int padding = (4 - ((pc + 1) % 4)) % 4;
        int base = pc + 1 + padding;
        if (base + 8 > bytecode.length) return 1;
        int npairs = readInt(bytecode, base + 4);
        return 1 + padding + 8 + npairs * 8;
    }

    private int getWideLength(byte[] bytecode, int pc) {
        if (pc + 1 >= bytecode.length) return 1;
        int op = Byte.toUnsignedInt(bytecode[pc + 1]);
        return op == 0x84 ? 6 : 4;
    }

    private int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) |
               ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static final int[] OPCODE_LENGTHS = {
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        2, 3, 2, 3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3,
        3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 0, 0, 1, 1, 1, 1,
        1, 1, 3, 3, 3, 3, 3, 3, 3, 5, 5, 3, 2, 3, 1, 1,
        3, 3, 1, 1, 0, 4, 3, 3, 5, 5, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
    };

    private static class BytecodeAnalysis {
        boolean hasXorOperations;
        boolean hasArrayOperations;
        boolean hasLoops;
        boolean createsString;
    }
}
