package com.tonic.ui.editor.cfg;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;

import java.util.*;

public class CFGBuilder {

    public List<CFGBlock> buildCFG(MethodEntry method) {
        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null) {
            return Collections.emptyList();
        }

        CodeWriter codeWriter = new CodeWriter(method);
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction instr : codeWriter.getInstructions()) {
            instructions.add(instr);
        }

        if (instructions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> boundaries = findBoundaries(instructions, codeAttr);
        Map<Integer, CFGBlock> blockMap = createBlocks(instructions, boundaries);
        connectBlocks(blockMap, instructions, codeAttr);
        markExceptionHandlers(blockMap, codeAttr, method);

        List<CFGBlock> result = new ArrayList<>(blockMap.values());
        result.sort(Comparator.comparingInt(CFGBlock::getStartOffset));
        return result;
    }

    private Set<Integer> findBoundaries(List<Instruction> instructions, CodeAttribute codeAttr) {
        Set<Integer> boundaries = new TreeSet<>();
        boundaries.add(0);

        for (Instruction instr : instructions) {
            int offset = instr.getOffset();

            if (instr instanceof ConditionalBranchInstruction) {
                ConditionalBranchInstruction branch = (ConditionalBranchInstruction) instr;
                int target = offset + branch.getBranchOffset();
                boundaries.add(target);
                boundaries.add(offset + instr.getLength());
            } else if (instr instanceof GotoInstruction) {
                GotoInstruction gotoInstr = (GotoInstruction) instr;
                int target;
                if (gotoInstr.getType() == GotoInstruction.GotoType.GOTO_WIDE) {
                    target = offset + gotoInstr.getBranchOffsetWide();
                } else {
                    target = offset + gotoInstr.getBranchOffset();
                }
                boundaries.add(target);
                boundaries.add(offset + instr.getLength());
            } else if (instr instanceof TableSwitchInstruction) {
                TableSwitchInstruction tableSwitch = (TableSwitchInstruction) instr;
                boundaries.add(offset + tableSwitch.getDefaultOffset());
                for (int jumpOffset : tableSwitch.getJumpOffsets().values()) {
                    boundaries.add(offset + jumpOffset);
                }
                boundaries.add(offset + instr.getLength());
            } else if (instr instanceof LookupSwitchInstruction) {
                LookupSwitchInstruction lookupSwitch = (LookupSwitchInstruction) instr;
                boundaries.add(offset + lookupSwitch.getDefaultOffset());
                for (int jumpOffset : lookupSwitch.getMatchOffsets().values()) {
                    boundaries.add(offset + jumpOffset);
                }
                boundaries.add(offset + instr.getLength());
            } else if (instr instanceof ReturnInstruction || instr instanceof ATHROWInstruction) {
                boundaries.add(offset + instr.getLength());
            }
        }

        if (codeAttr.getExceptionTable() != null) {
            for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
                boundaries.add(entry.getStartPc());
                boundaries.add(entry.getEndPc());
                boundaries.add(entry.getHandlerPc());
            }
        }

        return boundaries;
    }

    private Map<Integer, CFGBlock> createBlocks(List<Instruction> instructions, Set<Integer> boundaries) {
        Map<Integer, CFGBlock> blockMap = new TreeMap<>();
        int blockId = 0;
        CFGBlock currentBlock = null;

        for (Instruction instr : instructions) {
            int offset = instr.getOffset();

            if (boundaries.contains(offset)) {
                currentBlock = new CFGBlock(blockId++, offset);
                blockMap.put(offset, currentBlock);
            }

            if (currentBlock != null) {
                currentBlock.addInstruction(instr);
            }
        }

        return blockMap;
    }

    private void connectBlocks(Map<Integer, CFGBlock> blockMap, List<Instruction> instructions, CodeAttribute codeAttr) {
        for (CFGBlock block : blockMap.values()) {
            Instruction lastInstr = block.getLastInstruction();
            if (lastInstr == null) continue;

            int offset = lastInstr.getOffset();

            if (lastInstr instanceof ConditionalBranchInstruction) {
                ConditionalBranchInstruction branch = (ConditionalBranchInstruction) lastInstr;
                int trueTarget = offset + branch.getBranchOffset();
                int falseTarget = offset + lastInstr.getLength();

                CFGBlock trueBlock = blockMap.get(trueTarget);
                CFGBlock falseBlock = blockMap.get(falseTarget);

                if (trueBlock != null) {
                    block.addEdge(trueBlock, CFGEdgeType.CONDITIONAL_TRUE);
                }
                if (falseBlock != null) {
                    block.addEdge(falseBlock, CFGEdgeType.CONDITIONAL_FALSE);
                }
            } else if (lastInstr instanceof GotoInstruction) {
                GotoInstruction gotoInstr = (GotoInstruction) lastInstr;
                int target;
                if (gotoInstr.getType() == GotoInstruction.GotoType.GOTO_WIDE) {
                    target = offset + gotoInstr.getBranchOffsetWide();
                } else {
                    target = offset + gotoInstr.getBranchOffset();
                }

                CFGBlock targetBlock = blockMap.get(target);
                if (targetBlock != null) {
                    block.addEdge(targetBlock, CFGEdgeType.UNCONDITIONAL);
                }
            } else if (lastInstr instanceof TableSwitchInstruction) {
                TableSwitchInstruction tableSwitch = (TableSwitchInstruction) lastInstr;
                int defaultTarget = offset + tableSwitch.getDefaultOffset();
                CFGBlock defaultBlock = blockMap.get(defaultTarget);
                if (defaultBlock != null) {
                    block.addEdge(defaultBlock, CFGEdgeType.SWITCH_DEFAULT);
                }

                for (int jumpOffset : tableSwitch.getJumpOffsets().values()) {
                    int target = offset + jumpOffset;
                    CFGBlock caseBlock = blockMap.get(target);
                    if (caseBlock != null && caseBlock != defaultBlock) {
                        block.addEdge(caseBlock, CFGEdgeType.SWITCH_CASE);
                    }
                }
            } else if (lastInstr instanceof LookupSwitchInstruction) {
                LookupSwitchInstruction lookupSwitch = (LookupSwitchInstruction) lastInstr;
                int defaultTarget = offset + lookupSwitch.getDefaultOffset();
                CFGBlock defaultBlock = blockMap.get(defaultTarget);
                if (defaultBlock != null) {
                    block.addEdge(defaultBlock, CFGEdgeType.SWITCH_DEFAULT);
                }

                for (int jumpOffset : lookupSwitch.getMatchOffsets().values()) {
                    int target = offset + jumpOffset;
                    CFGBlock caseBlock = blockMap.get(target);
                    if (caseBlock != null && caseBlock != defaultBlock) {
                        block.addEdge(caseBlock, CFGEdgeType.SWITCH_CASE);
                    }
                }
            } else if (!(lastInstr instanceof ReturnInstruction) && !(lastInstr instanceof ATHROWInstruction)) {
                int fallthrough = offset + lastInstr.getLength();
                CFGBlock nextBlock = blockMap.get(fallthrough);
                if (nextBlock != null) {
                    block.addEdge(nextBlock, CFGEdgeType.NORMAL);
                }
            }
        }

        if (codeAttr.getExceptionTable() != null) {
            for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
                CFGBlock handlerBlock = blockMap.get(entry.getHandlerPc());
                if (handlerBlock == null) continue;

                for (CFGBlock block : blockMap.values()) {
                    if (block.getStartOffset() >= entry.getStartPc() && block.getStartOffset() < entry.getEndPc()) {
                        block.addEdge(handlerBlock, CFGEdgeType.EXCEPTION);
                    }
                }
            }
        }
    }

    private void markExceptionHandlers(Map<Integer, CFGBlock> blockMap, CodeAttribute codeAttr, MethodEntry method) {
        if (codeAttr.getExceptionTable() == null) return;

        for (ExceptionTableEntry entry : codeAttr.getExceptionTable()) {
            CFGBlock handlerBlock = blockMap.get(entry.getHandlerPc());
            if (handlerBlock != null) {
                handlerBlock.setExceptionHandler(true);
                String type = entry.getCatchType() == 0 ? "finally" :
                    method.getClassFile().getConstPool().getClassName(entry.getCatchType());
                handlerBlock.setHandlerType(type);
            }
        }
    }
}
