package com.tonic.ui.query.eval;

import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeDynamicInstruction;
import com.tonic.analysis.instruction.InvokeInsn;
import com.tonic.analysis.instruction.InvokeInterfaceInstruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.InvokeStaticInstruction;
import com.tonic.analysis.instruction.PutFieldInstruction;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.util.ArgumentTypeAnalyzer;
import com.tonic.ui.query.value.Value;
import com.tonic.utill.Opcode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.tonic.ui.query.eval.SubjectKind.ARG;
import static com.tonic.ui.query.eval.SubjectKind.CALL;
import static com.tonic.ui.query.eval.SubjectKind.CLASS;
import static com.tonic.ui.query.eval.SubjectKind.FIELD_ACCESS;
import static com.tonic.ui.query.eval.SubjectKind.INSTRUCTION;
import static com.tonic.ui.query.eval.SubjectKind.METHOD;

/**
 * Registers the static (bytecode-level) query vocabulary. Each line is one queryable fact; the same
 * keyword ({@code name}, {@code type}, {@code owner}, {@code value}, {@code arity}, …) is registered
 * per subject kind with kind-appropriate behavior. This is the only file that grows as the vocabulary
 * expands — no new AST nodes or visitor methods.
 */
public final class DefaultAttributes {

    private static final Map<Integer, String> MODIFIERS = new LinkedHashMap<>();
    static {
        MODIFIERS.put(0x0001, "public");
        MODIFIERS.put(0x0002, "private");
        MODIFIERS.put(0x0004, "protected");
        MODIFIERS.put(0x0008, "static");
        MODIFIERS.put(0x0010, "final");
        MODIFIERS.put(0x0020, "synchronized");
        MODIFIERS.put(0x0040, "volatile");
        MODIFIERS.put(0x0080, "transient");
        MODIFIERS.put(0x0100, "native");
        MODIFIERS.put(0x0200, "interface");
        MODIFIERS.put(0x0400, "abstract");
        MODIFIERS.put(0x0800, "strict");
        MODIFIERS.put(0x1000, "synthetic");
        MODIFIERS.put(0x2000, "annotation");
        MODIFIERS.put(0x4000, "enum");
    }

    private DefaultAttributes() {
    }

    /** Builds a registry populated with every static atom. */
    public static AttributeRegistry create() {
        AttributeRegistry r = new AttributeRegistry();
        registerClass(r);
        registerMethod(r);
        registerCall(r);
        registerArg(r);
        registerInstruction(r);
        registerFieldAccess(r);
        registerCfg(r);
        registerIdentities(r);
        return r;
    }

    /** Control-flow / SSA atoms. {@code recursive} is a static self-call scan; loop/block counts lift IR lazily. */
    private static void registerCfg(AttributeRegistry r) {
        r.registerScalar(METHOD, "recursive", s -> Value.of(isRecursive(methodOf(s), s.context())));
        r.registerScalar(METHOD, "blocks", s -> {
            IRMethod ir = s.context().ir();
            return ir == null ? Value.ABSENT : Value.of(ir.getBlockCount());
        });
        r.registerScalar(METHOD, "loops", s -> {
            IRMethod ir = s.context().ir();
            LoopAnalysis la = s.context().loopAnalysis();
            if (ir == null || la == null) {
                return Value.ABSENT;
            }
            long loops = ir.getBlocksInOrder().stream().filter(la::isLoopHeader).count();
            return Value.of(loops);
        });

        // Loop membership of a specific call/instruction, via offset -> IR block (no IR-offset
        // provenance needed; basic blocks already carry their start offset).
        r.registerScalar(CALL, "inLoop", s -> loopMembership(s.context(), call(s).invoke().getOffset(), false));
        r.registerScalar(CALL, "loopDepth", s -> loopMembership(s.context(), call(s).invoke().getOffset(), true));
        r.registerScalar(INSTRUCTION, "inLoop", s -> loopMembership(s.context(), insn(s).instruction().getOffset(), false));
        r.registerScalar(INSTRUCTION, "loopDepth", s -> loopMembership(s.context(), insn(s).instruction().getOffset(), true));
    }

    private static Value loopMembership(EvalContext ctx, int offset, boolean wantDepth) {
        LoopAnalysis la = ctx.loopAnalysis();
        IRBlock block = ctx.blockForOffset(offset);
        if (la == null || block == null) {
            return Value.ABSENT;
        }
        return wantDepth ? Value.of(la.getLoopDepth(block)) : Value.of(la.isInLoop(block));
    }

    private static boolean isRecursive(MethodEntry method, com.tonic.ui.query.eval.EvalContext ctx) {
        String owner = method.getOwnerName();
        String name = method.getName();
        String desc = method.getDesc();
        for (Instruction insn : ctx.instructions()) {
            if (insn instanceof InvokeInsn) {
                InvokeInsn call = (InvokeInsn) insn;
                if (owner.equals(call.getOwnerClass()) && name.equals(call.getMethodName())
                        && desc.equals(call.getMethodDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Self-references so a redundant subject prefix reads naturally inside a quantifier body
     * ({@code call.name}, {@code field.owner}, {@code method.descriptor}) — the keyword resolves to
     * the current subject of that kind.
     */
    private static void registerIdentities(AttributeRegistry r) {
        AttributeRegistry.Selector self = (s, step) -> Stream.of(s);
        registerStream(r, CLASS, self, "class");
        registerStream(r, METHOD, self, "method");
        registerStream(r, CALL, self, "call");
        registerStream(r, FIELD_ACCESS, self, "field");
        registerStream(r, INSTRUCTION, self, "insn", "instruction");
    }

    private static void registerClass(AttributeRegistry r) {
        r.registerScalar(CLASS, "name", s -> Value.of(classOf(s).getClassName()));
        r.registerScalar(CLASS, "modifiers", s -> modifiers(classOf(s).getAccess()));
    }

    private static void registerMethod(AttributeRegistry r) {
        r.registerScalar(METHOD, "name", s -> Value.of(methodOf(s).getName()));
        r.registerScalar(METHOD, "owner", s -> Value.of(methodOf(s).getOwnerName()));
        r.registerScalar(METHOD, "descriptor", s -> Value.of(methodOf(s).getDesc()));
        r.registerScalar(METHOD, "arity",
                s -> Value.of(ArgumentTypeAnalyzer.countDescriptorArguments(methodOf(s).getDesc())));
        r.registerScalar(METHOD, "modifiers", s -> modifiers(methodOf(s).getAccess()));
        r.registerScalar(METHOD, "line", s -> Value.of(s.context().lineForOffset(0)));
        r.registerScalar(METHOD, "opcodes", s -> Value.of(joinedMnemonics(s.context())));

        AttributeRegistry.Selector calls = (s, step) -> instructionStream(s, (insn, idx) ->
                insn instanceof InvokeInsn ? new Subject.CallSubject(insn, idx, s.context()) : null);
        registerStream(r, METHOD, calls, "call", "calls");

        AttributeRegistry.Selector instructions = (s, step) ->
                instructionStream(s, (insn, idx) -> new Subject.InstructionSubject(insn, idx, s.context()));
        registerStream(r, METHOD, instructions, "insn", "instruction", "instructions");

        AttributeRegistry.Selector fields = (s, step) -> instructionStream(s, (insn, idx) ->
                isFieldAccess(insn) ? new Subject.FieldAccessSubject(insn, s.context()) : null);
        registerStream(r, METHOD, fields, "field", "fields");
    }

    private static void registerCall(AttributeRegistry r) {
        r.registerScalar(CALL, "owner", s -> {
            InvokeInsn iv = invoke(s);
            return iv == null ? Value.ABSENT : Value.of(iv.getOwnerClass());
        });
        r.registerScalar(CALL, "name", s -> {
            InvokeInsn iv = invoke(s);
            return iv == null ? Value.ABSENT : Value.of(iv.getMethodName());
        });
        r.registerScalar(CALL, "descriptor", s -> {
            InvokeInsn iv = invoke(s);
            return iv == null ? Value.ABSENT : Value.of(iv.getMethodDescriptor());
        });
        r.registerScalar(CALL, "target", s -> {
            InvokeInsn iv = invoke(s);
            return iv == null ? Value.ABSENT
                    : Value.of(iv.getOwnerClass() + "." + iv.getMethodName() + iv.getMethodDescriptor());
        });
        r.registerScalar(CALL, "arity", s -> {
            InvokeInsn iv = invoke(s);
            return iv == null ? Value.ABSENT
                    : Value.of(ArgumentTypeAnalyzer.countDescriptorArguments(iv.getMethodDescriptor()));
        });
        r.registerScalar(CALL, "opcode", s -> Value.of(mnemonic(call(s).invoke())));
        r.registerScalar(CALL, "kind", s -> Value.of(invokeKind(call(s).invoke())));
        r.registerScalar(CALL, "line", s -> Value.of(s.context().lineForOffset(call(s).invoke().getOffset())));

        AttributeRegistry.Selector argSel = (s, step) -> args(call(s), step);
        registerStream(r, CALL, argSel, "arg", "args");
    }

    private static void registerStream(AttributeRegistry r, SubjectKind kind,
                                       AttributeRegistry.Selector selector, String... keywords) {
        for (String kw : keywords) {
            r.registerStream(kind, kw, selector);
        }
    }

    private static void registerArg(AttributeRegistry r) {
        r.registerScalar(ARG, "index", s -> Value.of(arg(s).argIndex()));
        r.registerScalar(ARG, "type", s -> ArgValueResolver.declaredType(arg(s).call(), arg(s).argIndex()));
        r.registerScalar(ARG, "value", s -> ArgValueResolver.value(arg(s).call(), arg(s).argIndex()));
        r.registerScalar(ARG, "kind", s -> ArgValueResolver.kind(arg(s).call(), arg(s).argIndex()));
    }

    private static void registerInstruction(AttributeRegistry r) {
        r.registerScalar(INSTRUCTION, "opcode", s -> Value.of(mnemonic(insn(s).instruction())));
        r.registerScalar(INSTRUCTION, "index", s -> Value.of(insn(s).index()));
        r.registerScalar(INSTRUCTION, "line", s -> Value.of(s.context().lineForOffset(insn(s).instruction().getOffset())));
    }

    private static void registerFieldAccess(AttributeRegistry r) {
        r.registerScalar(FIELD_ACCESS, "owner", s -> Value.of(fieldOwner(field(s).instruction())));
        r.registerScalar(FIELD_ACCESS, "name", s -> Value.of(fieldName(field(s).instruction())));
        r.registerScalar(FIELD_ACCESS, "descriptor", s -> Value.of(fieldDescriptor(field(s).instruction())));
        r.registerScalar(FIELD_ACCESS, "kind",
                s -> Value.of(field(s).instruction() instanceof PutFieldInstruction ? "write" : "read"));
    }

    // ---- selector helpers -------------------------------------------------

    private interface InstructionMapper {
        Subject map(Instruction insn, int index);
    }

    private static Stream<Subject> instructionStream(Subject s, InstructionMapper mapper) {
        List<Instruction> insns = s.context().instructions();
        List<Subject> out = new ArrayList<>();
        for (int i = 0; i < insns.size(); i++) {
            Subject sub = mapper.map(insns.get(i), i);
            if (sub != null) {
                out.add(sub);
            }
        }
        return out.stream();
    }

    private static Stream<Subject> args(Subject.CallSubject call, com.tonic.ui.query.ast.Step step) {
        int arity = ArgumentTypeAnalyzer.countDescriptorArguments(invokeDescriptor(call.invoke()));
        if (step.hasIndex()) {
            int idx = step.index();
            return idx >= 0 && idx < arity ? Stream.of(new Subject.ArgSubject(call, idx)) : Stream.empty();
        }
        List<Subject> out = new ArrayList<>();
        for (int i = 0; i < arity; i++) {
            out.add(new Subject.ArgSubject(call, i));
        }
        return out.stream();
    }

    // ---- subject casts ----------------------------------------------------

    private static ClassFile classOf(Subject s) { return ((Subject.ClassSubject) s).classFile(); }
    private static MethodEntry methodOf(Subject s) { return ((Subject.MethodSubject) s).method(); }
    private static Subject.CallSubject call(Subject s) { return (Subject.CallSubject) s; }
    private static Subject.ArgSubject arg(Subject s) { return (Subject.ArgSubject) s; }
    private static Subject.InstructionSubject insn(Subject s) { return (Subject.InstructionSubject) s; }
    private static Subject.FieldAccessSubject field(Subject s) { return (Subject.FieldAccessSubject) s; }

    private static InvokeInsn invoke(Subject s) {
        Instruction i = call(s).invoke();
        return i instanceof InvokeInsn ? (InvokeInsn) i : null;
    }

    // ---- value helpers ----------------------------------------------------

    private static Value modifiers(int access) {
        List<Value> names = new ArrayList<>();
        for (Map.Entry<Integer, String> e : MODIFIERS.entrySet()) {
            if ((access & e.getKey()) != 0) {
                names.add(Value.of(e.getValue()));
            }
        }
        return Value.ofSet(names);
    }

    private static String mnemonic(Instruction instr) {
        return Opcode.fromCode(instr.getOpcode()).getMnemonic();
    }

    /** Space-joined opcode mnemonics of the method body, for the {@code opcodes matches /.../} shorthand. */
    private static String joinedMnemonics(EvalContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (Instruction instr : ctx.instructions()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(mnemonic(instr));
        }
        return sb.toString();
    }

    private static String invokeKind(Instruction instr) {
        if (instr instanceof InvokeStaticInstruction) return "static";
        if (instr instanceof InvokeSpecialInstruction) return "special";
        if (instr instanceof InvokeInterfaceInstruction) return "interface";
        if (instr instanceof InvokeDynamicInstruction) return "dynamic";
        return "virtual";
    }

    private static String invokeDescriptor(Instruction invoke) {
        return invoke instanceof InvokeInsn ? ((InvokeInsn) invoke).getMethodDescriptor() : "()V";
    }

    private static boolean isFieldAccess(Instruction insn) {
        return insn instanceof GetFieldInstruction || insn instanceof PutFieldInstruction;
    }

    private static String fieldOwner(Instruction insn) {
        return insn instanceof GetFieldInstruction ? ((GetFieldInstruction) insn).getOwnerClass()
                : ((PutFieldInstruction) insn).getOwnerClass();
    }

    private static String fieldName(Instruction insn) {
        return insn instanceof GetFieldInstruction ? ((GetFieldInstruction) insn).getFieldName()
                : ((PutFieldInstruction) insn).getFieldName();
    }

    private static String fieldDescriptor(Instruction insn) {
        return insn instanceof GetFieldInstruction ? ((GetFieldInstruction) insn).getFieldDescriptor()
                : ((PutFieldInstruction) insn).getFieldDescriptor();
    }
}
