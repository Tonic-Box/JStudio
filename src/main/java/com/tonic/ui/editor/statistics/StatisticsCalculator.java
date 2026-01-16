package com.tonic.ui.editor.statistics;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.simulation.metrics.ComplexityMetrics;

import java.util.*;

public class StatisticsCalculator {

    public ClassStatistics calculate(ClassEntryModel classEntry) {
        ClassFile cf = classEntry.getClassFile();
        if (cf == null) {
            return createEmptyStatistics();
        }

        List<MethodEntryModel> methods = classEntry.getMethods();
        int methodCount = methods != null ? methods.size() : 0;
        int fieldCount = classEntry.getFields() != null ? classEntry.getFields().size() : 0;
        int interfaceCount = classEntry.getInterfaceNames() != null ? classEntry.getInterfaceNames().size() : 0;

        int totalBytecodeSize = 0;
        int abstractMethodCount = 0;
        int staticMethodCount = 0;
        int nativeMethodCount = 0;
        double totalComplexity = 0;
        int complexityCount = 0;

        int lowComplexity = 0;
        int mediumComplexity = 0;
        int highComplexity = 0;

        List<ClassStatistics.MethodSizeInfo> methodSizes = new ArrayList<>();
        List<ClassStatistics.MethodDetailInfo> methodDetails = new ArrayList<>();
        Map<ClassStatistics.OpcodeCategory, Integer> opcodeDistribution = new EnumMap<>(ClassStatistics.OpcodeCategory.class);

        for (ClassStatistics.OpcodeCategory cat : ClassStatistics.OpcodeCategory.values()) {
            opcodeDistribution.put(cat, 0);
        }

        if (methods != null) {
            for (MethodEntryModel methodModel : methods) {
                MethodEntry method = methodModel.getMethodEntry();
                String methodName = methodModel.getName();
                String methodDesc = methodModel.getDescriptor();

                boolean isAbstract = methodModel.isAbstract();
                boolean isStatic = methodModel.isStatic();
                boolean isNative = methodModel.isNative();

                if (isAbstract) abstractMethodCount++;
                if (isStatic) staticMethodCount++;
                if (isNative) nativeMethodCount++;

                CodeAttribute codeAttr = method.getCodeAttribute();
                int bytecodeSize = 0;
                int maxStack = 0;
                int maxLocals = 0;
                int ccn = 1;
                int loops = 0;
                int branches = 0;

                if (codeAttr != null) {
                    bytecodeSize = codeAttr.getCode() != null ? codeAttr.getCode().length : 0;
                    maxStack = codeAttr.getMaxStack();
                    maxLocals = codeAttr.getMaxLocals();
                    totalBytecodeSize += bytecodeSize;

                    try {
                        SSA ssa = new SSA(cf.getConstPool());
                        IRMethod irMethod = ssa.lift(method);
                        ComplexityMetrics metrics = new ComplexityMetrics(irMethod);
                        ccn = metrics.getCyclomaticComplexity();
                        loops = metrics.getLoopCount();
                        branches = metrics.getBranchCount();
                        totalComplexity += ccn;
                        complexityCount++;
                    } catch (Exception e) {
                        // Fallback to basic complexity
                    }

                    countOpcodes(method, opcodeDistribution);
                }

                if (ccn <= 5) lowComplexity++;
                else if (ccn <= 10) mediumComplexity++;
                else highComplexity++;

                if (bytecodeSize > 0) {
                    methodSizes.add(ClassStatistics.MethodSizeInfo.builder()
                            .name(methodName)
                            .bytecodeSize(bytecodeSize)
                            .maxStack(maxStack)
                            .maxLocals(maxLocals)
                            .build());
                }

                methodDetails.add(ClassStatistics.MethodDetailInfo.builder()
                        .name(methodName)
                        .descriptor(methodDesc)
                        .bytecodeSize(bytecodeSize)
                        .maxStack(maxStack)
                        .maxLocals(maxLocals)
                        .cyclomaticComplexity(ccn)
                        .loopCount(loops)
                        .branchCount(branches)
                        .isStatic(isStatic)
                        .isAbstract(isAbstract)
                        .isNative(isNative)
                        .build());
            }
        }

        methodSizes.sort((a, b) -> Integer.compare(b.getBytecodeSize(), a.getBytecodeSize()));
        methodDetails.sort((a, b) -> Integer.compare(b.getBytecodeSize(), a.getBytecodeSize()));

        double avgComplexity = complexityCount > 0 ? totalComplexity / complexityCount : 0;

        return ClassStatistics.builder()
                .methodCount(methodCount)
                .fieldCount(fieldCount)
                .totalBytecodeSize(totalBytecodeSize)
                .averageComplexity(avgComplexity)
                .interfaceCount(interfaceCount)
                .abstractMethodCount(abstractMethodCount)
                .staticMethodCount(staticMethodCount)
                .nativeMethodCount(nativeMethodCount)
                .methodSizes(methodSizes)
                .methodDetails(methodDetails)
                .opcodeDistribution(opcodeDistribution)
                .lowComplexityCount(lowComplexity)
                .mediumComplexityCount(mediumComplexity)
                .highComplexityCount(highComplexity)
                .build();
    }

    private void countOpcodes(MethodEntry method, Map<ClassStatistics.OpcodeCategory, Integer> distribution) {
        try {
            CodeWriter codeWriter = new CodeWriter(method);
            for (Instruction instr : codeWriter.getInstructions()) {
                String instrStr = instr.toString().toLowerCase();
                String opName = instrStr.split("\\s+")[0];

                ClassStatistics.OpcodeCategory category = categorizeOpcode(opName);
                distribution.merge(category, 1, Integer::sum);
            }
        } catch (Exception e) {
            // Ignore errors in opcode counting
        }
    }

    private ClassStatistics.OpcodeCategory categorizeOpcode(String opcode) {
        if (opcode.startsWith("invoke")) {
            return ClassStatistics.OpcodeCategory.INVOKE;
        }
        if (opcode.endsWith("load") || opcode.startsWith("aload") || opcode.startsWith("iload") ||
                opcode.startsWith("lload") || opcode.startsWith("fload") || opcode.startsWith("dload")) {
            return ClassStatistics.OpcodeCategory.LOAD;
        }
        if (opcode.endsWith("store") || opcode.startsWith("astore") || opcode.startsWith("istore") ||
                opcode.startsWith("lstore") || opcode.startsWith("fstore") || opcode.startsWith("dstore")) {
            return ClassStatistics.OpcodeCategory.STORE;
        }
        if (opcode.startsWith("if") || opcode.equals("goto") || opcode.equals("goto_w") ||
                opcode.contains("switch") || opcode.equals("jsr") || opcode.equals("jsr_w")) {
            return ClassStatistics.OpcodeCategory.BRANCH;
        }
        if (opcode.startsWith("iconst") || opcode.startsWith("lconst") || opcode.startsWith("fconst") ||
                opcode.startsWith("dconst") || opcode.equals("aconst_null") ||
                opcode.equals("bipush") || opcode.equals("sipush") || opcode.startsWith("ldc")) {
            return ClassStatistics.OpcodeCategory.CONST;
        }
        if (opcode.matches("^[ilfd](add|sub|mul|div|rem|neg)$") || opcode.matches("^[il](and|or|xor|shl|shr|ushr)$") ||
                opcode.matches("^[ilfd]2[ilfd]$") || opcode.equals("iinc") ||
                opcode.matches("^[dfl]cmp[gl]?$") || opcode.equals("lcmp")) {
            return ClassStatistics.OpcodeCategory.ARITHMETIC;
        }
        if (opcode.contains("aload") && opcode.length() > 5 || opcode.contains("astore") && opcode.length() > 6 ||
                opcode.equals("arraylength") || opcode.contains("newarray") || opcode.equals("multianewarray") ||
                opcode.matches("^[bcsilfd]aload$") || opcode.matches("^[bcsilfd]astore$") ||
                opcode.equals("aaload") || opcode.equals("aastore")) {
            return ClassStatistics.OpcodeCategory.ARRAY;
        }
        if (opcode.startsWith("get") || opcode.startsWith("put")) {
            return ClassStatistics.OpcodeCategory.FIELD;
        }
        if (opcode.equals("pop") || opcode.equals("pop2") || opcode.startsWith("dup") ||
                opcode.equals("swap") || opcode.equals("nop")) {
            return ClassStatistics.OpcodeCategory.STACK;
        }
        if (opcode.contains("return") || opcode.equals("athrow")) {
            return ClassStatistics.OpcodeCategory.RETURN;
        }
        return ClassStatistics.OpcodeCategory.OTHER;
    }

    private ClassStatistics createEmptyStatistics() {
        Map<ClassStatistics.OpcodeCategory, Integer> emptyDistribution = new EnumMap<>(ClassStatistics.OpcodeCategory.class);
        for (ClassStatistics.OpcodeCategory cat : ClassStatistics.OpcodeCategory.values()) {
            emptyDistribution.put(cat, 0);
        }

        return ClassStatistics.builder()
                .methodCount(0)
                .fieldCount(0)
                .totalBytecodeSize(0)
                .averageComplexity(0)
                .interfaceCount(0)
                .abstractMethodCount(0)
                .staticMethodCount(0)
                .nativeMethodCount(0)
                .methodSizes(Collections.emptyList())
                .methodDetails(Collections.emptyList())
                .opcodeDistribution(emptyDistribution)
                .lowComplexityCount(0)
                .mediumComplexityCount(0)
                .highComplexityCount(0)
                .build();
    }
}
