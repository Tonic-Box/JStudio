package com.tonic.ui.editor.cfg;

import com.tonic.analysis.instruction.Instruction;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** A basic block of a method's control flow graph: its instructions, bytecode range, outgoing edges and predecessors. */
@Getter
public class CFGBlock
{
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

    /**
     * Creates an empty block.
     *
     * @param id the block number
     * @param startOffset the bytecode offset of its first instruction
     */
    public CFGBlock(int id, int startOffset)
    {
        this.id = id;
        this.startOffset = startOffset;
        this.endOffset = startOffset;
    }

    /**
     * Appends an instruction and moves the block's end offset past it.
     *
     * @param instruction the instruction
     */
    public void addInstruction(Instruction instruction)
    {
        instructions.add(instruction);
        endOffset = instruction.getOffset() + instruction.getLength();
    }

    /**
     * Adds an outgoing edge and records this block as a predecessor of the target.
     *
     * @param target the block control flows to
     * @param type the kind of edge
     */
    public void addEdge(CFGBlock target, CFGEdgeType type)
    {
        outEdges.add(new CFGEdge(target, type));
        target.predecessors.add(this);
    }

    /**
     * Gets the block's final instruction.
     *
     * @return the last instruction, or null when the block is empty
     */
    public Instruction getLastInstruction()
    {
        return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
    }

    /**
     * Tells whether the block has no instructions.
     *
     * @return true when empty
     */
    public boolean isEmpty()
    {
        return instructions.isEmpty();
    }
}
