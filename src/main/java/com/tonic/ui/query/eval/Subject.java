package com.tonic.ui.query.eval;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/**
 * The polymorphic evaluation cursor. An accessor step rebinds the subject (method -> call -> arg);
 * attribute resolvers read from the concrete subject. Thin immutable wrappers over YABR/parser
 * objects plus the shared {@link EvalContext}.
 */
public interface Subject {

    SubjectKind kind();

    EvalContext context();

    final class MethodSubject implements Subject {
        private final MethodEntry method;
        private final EvalContext context;
        public MethodSubject(MethodEntry method, EvalContext context) { this.method = method; this.context = context; }
        public MethodEntry method() { return method; }
        @Override public SubjectKind kind() { return SubjectKind.METHOD; }
        @Override public EvalContext context() { return context; }
    }

    final class ClassSubject implements Subject {
        private final ClassFile classFile;
        private final EvalContext context;
        public ClassSubject(ClassFile classFile, EvalContext context) { this.classFile = classFile; this.context = context; }
        public ClassFile classFile() { return classFile; }
        @Override public SubjectKind kind() { return SubjectKind.CLASS; }
        @Override public EvalContext context() { return context; }
    }

    final class InstructionSubject implements Subject {
        private final Instruction instruction;
        private final int index;
        private final EvalContext context;
        public InstructionSubject(Instruction instruction, int index, EvalContext context) {
            this.instruction = instruction; this.index = index; this.context = context;
        }
        public Instruction instruction() { return instruction; }
        public int index() { return index; }
        @Override public SubjectKind kind() { return SubjectKind.INSTRUCTION; }
        @Override public EvalContext context() { return context; }
    }

    /** An invocation site; {@code invoke} implements {@link com.tonic.analysis.instruction.InvokeInsn}. */
    final class CallSubject implements Subject {
        private final Instruction invoke;
        private final int index;
        private final EvalContext context;
        public CallSubject(Instruction invoke, int index, EvalContext context) {
            this.invoke = invoke; this.index = index; this.context = context;
        }
        public Instruction invoke() { return invoke; }
        public int index() { return index; }
        @Override public SubjectKind kind() { return SubjectKind.CALL; }
        @Override public EvalContext context() { return context; }
    }

    final class ArgSubject implements Subject {
        private final CallSubject call;
        private final int argIndex;
        public ArgSubject(CallSubject call, int argIndex) { this.call = call; this.argIndex = argIndex; }
        public CallSubject call() { return call; }
        public int argIndex() { return argIndex; }
        @Override public SubjectKind kind() { return SubjectKind.ARG; }
        @Override public EvalContext context() { return call.context(); }
    }

    /** A field read/write site; {@code instruction} is a GetField/PutField instruction. */
    final class FieldAccessSubject implements Subject {
        private final Instruction instruction;
        private final EvalContext context;
        public FieldAccessSubject(Instruction instruction, EvalContext context) {
            this.instruction = instruction; this.context = context;
        }
        public Instruction instruction() { return instruction; }
        @Override public SubjectKind kind() { return SubjectKind.FIELD_ACCESS; }
        @Override public EvalContext context() { return context; }
    }
}
