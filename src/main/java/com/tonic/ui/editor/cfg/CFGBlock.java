package com.tonic.ui.editor.cfg;

import com.tonic.analysis.instruction.Instruction;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class CFGBlock {
    private final int id;
    private final int startOffset;
    @Setter
    private int endOffset;
    private final List<Instruction> instructions = new ArrayList<>();
    private final List<CFGEdge> outEdges = new ArrayList<>();
    private final List<CFGBlock> predecessors = new ArrayList<>();
    @Setter
    private boolean exceptionHandler;
    @Setter
    private String handlerType;

    public CFGBlock(int id, int startOffset) {
        this.id = id;
        this.startOffset = startOffset;
        this.endOffset = startOffset;
    }

    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
        endOffset = instruction.getOffset() + instruction.getLength();
    }

    public void addEdge(CFGBlock target, CFGEdgeType type) {
        outEdges.add(new CFGEdge(target, type));
        target.predecessors.add(this);
    }

    public Instruction getLastInstruction() {
        return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
    }

    public boolean isEmpty() {
        return instructions.isEmpty();
    }
}
