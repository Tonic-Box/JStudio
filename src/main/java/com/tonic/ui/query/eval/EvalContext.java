package com.tonic.ui.query.eval;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.table.LineNumberTableEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-subject evaluation scope with lazy, cached views of a method so repeated accessors (every
 * {@code arg(n)}, {@code instructions}, {@code line}) share one decode/analysis. Method-scoped when a
 * {@link MethodEntry} is present, class-scoped otherwise. Carries the {@link EvidenceCollector}.
 */
public final class EvalContext {

    private final ClassFile classFile;
    private final MethodEntry method;
    private final EvidenceCollector evidence;
    private final Map<Object, Object> cache = new HashMap<>();

    private CodeWriter codeWriter;
    private List<Instruction> instructions;
    private Map<Integer, Integer> lineByOffset;
    private boolean codeAttempted;
    private IRMethod ir;
    private boolean irAttempted;
    private LoopAnalysis loopAnalysis;
    private boolean loopAttempted;
    private List<IRBlock> blocksByOffset;
    private DefUseChains defUse;
    private boolean defUseAttempted;

    public EvalContext(ClassFile classFile, MethodEntry method, EvidenceCollector evidence) {
        this.classFile = classFile;
        this.method = method;
        this.evidence = evidence;
    }

    public MethodEntry method() {
        return method;
    }

    public EvidenceCollector evidence() {
        return evidence;
    }

    /** Lazily-built CodeWriter, or {@code null} for abstract/native methods or class-scoped contexts. */
    public CodeWriter codeWriter() {
        if (!codeAttempted) {
            codeAttempted = true;
            if (method != null && method.getCodeAttribute() != null) {
                try {
                    codeWriter = new CodeWriter(method);
                } catch (Exception e) {
                    codeWriter = null;
                }
            }
        }
        return codeWriter;
    }

    public List<Instruction> instructions() {
        if (instructions == null) {
            CodeWriter cw = codeWriter();
            instructions = cw != null ? cw.getInstructionList() : Collections.emptyList();
        }
        return instructions;
    }

    /** Lazily lifts the method to SSA IR (cached), or {@code null} if it cannot be lifted. */
    public IRMethod ir() {
        if (!irAttempted) {
            irAttempted = true;
            if (method != null && method.getCodeAttribute() != null) {
                try {
                    ir = new SSA(classFile.getConstPool()).lift(method);
                } catch (Exception e) {
                    ir = null;
                }
            }
        }
        return ir;
    }

    /** Lazily builds loop analysis over the SSA IR (cached), or {@code null} if unavailable. */
    public LoopAnalysis loopAnalysis() {
        if (!loopAttempted) {
            loopAttempted = true;
            IRMethod m = ir();
            if (m != null) {
                try {
                    DominatorTree dom = new DominatorTree(m);
                    dom.compute();
                    LoopAnalysis la = new LoopAnalysis(m, dom);
                    la.compute();
                    loopAnalysis = la;
                } catch (Exception e) {
                    loopAnalysis = null;
                }
            }
        }
        return loopAnalysis;
    }

    /** Lazily builds def-use chains over the SSA IR (cached), or {@code null} if unavailable. */
    public DefUseChains defUse() {
        if (!defUseAttempted) {
            defUseAttempted = true;
            IRMethod m = ir();
            if (m != null) {
                try {
                    DefUseChains chains = new DefUseChains(m);
                    chains.compute();
                    defUse = chains;
                } catch (Exception e) {
                    defUse = null;
                }
            }
        }
        return defUse;
    }

    /**
     * Maps a bytecode offset to its containing IR basic block (the block with the greatest start
     * offset {@code <=} the given offset). Returns {@code null} when the method cannot be lifted.
     */
    public IRBlock blockForOffset(int offset) {
        IRMethod m = ir();
        if (m == null) {
            return null;
        }
        if (blocksByOffset == null) {
            blocksByOffset = new ArrayList<>(m.getBlocksInOrder());
            blocksByOffset.sort(Comparator.comparingInt(IRBlock::getBytecodeOffset));
        }
        IRBlock best = null;
        for (IRBlock b : blocksByOffset) {
            int start = b.getBytecodeOffset();
            if (start >= 0 && start <= offset) {
                best = b;
            } else if (start > offset) {
                break;
            }
        }
        return best;
    }

    /** Best-effort source line for a bytecode offset, or -1 when no LineNumberTable is present. */
    public int lineForOffset(int offset) {
        if (lineByOffset == null) {
            lineByOffset = buildLineTable();
        }
        int best = -1;
        int bestPc = -1;
        for (Map.Entry<Integer, Integer> e : lineByOffset.entrySet()) {
            int pc = e.getKey();
            if (pc <= offset && pc > bestPc) {
                bestPc = pc;
                best = e.getValue();
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    public <T> T memo(Object key, java.util.function.Supplier<T> supplier) {
        return (T) cache.computeIfAbsent(key, k -> supplier.get());
    }

    private Map<Integer, Integer> buildLineTable() {
        Map<Integer, Integer> map = new HashMap<>();
        if (method == null || method.getCodeAttribute() == null) {
            return map;
        }
        for (Attribute attr : method.getCodeAttribute().getAttributes()) {
            if (attr instanceof LineNumberTableAttribute) {
                List<LineNumberTableEntry> table = ((LineNumberTableAttribute) attr).getLineNumberTable();
                if (table != null) {
                    for (LineNumberTableEntry e : table) {
                        map.put(e.getStartPc(), e.getLineNumber());
                    }
                }
            }
        }
        return map;
    }
}
