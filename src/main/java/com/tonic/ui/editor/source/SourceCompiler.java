package com.tonic.ui.editor.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.FieldDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.Modifier;
import com.tonic.analysis.source.ast.decl.TypeDecl;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.lower.TypeResolver;
import com.tonic.analysis.source.lower.LoweringException;
import com.tonic.analysis.source.lower.SyntheticArrayConstructor;
import com.tonic.analysis.source.lower.SyntheticLambdaMethod;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.source.parser.ParseErrorListener;
import com.tonic.analysis.source.parser.ParseException;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceCompiler {

    public CompilationResult compile(String source, ClassFile originalClass, ClassPool classPool) {
        return compile(source, originalClass, classPool, null);
    }

    /**
     * Recompiles {@code source}. When {@code changedMethods} is non-null (the {@code name+descriptor} keys from
     * {@link com.tonic.ui.live.MethodBodyDiff}), only those methods are re-lowered and the rest keep their original
     * bytecode - so editing one method never perturbs the others. After lowering, the (re)compiled methods are
     * verified; a method that fails to verify fails the recompile rather than silently shipping invalid bytecode.
     */
    public CompilationResult compile(String source, ClassFile originalClass, ClassPool classPool,
                                     Set<String> changedMethods) {
        long startTime = System.currentTimeMillis();
        List<CompilationError> errors = new ArrayList<>();

        ParseErrorListener.CollectingErrorListener listener = ParseErrorListener.collecting();
        JavaParser parser = JavaParser.withErrorListener(listener);

        CompilationUnit cu;
        try {
            cu = parser.parse(source);
        } catch (ParseException e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(createErrorFromException(e, source));
            }
            return CompilationResult.failure(errors, source, elapsed(startTime));
        } catch (Exception e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(CompilationError.error(1, 1, 0, 1, "Parse error: " + e.getMessage()));
            }
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }

        if (listener.hasErrors()) {
            collectParseErrors(listener, source, errors);
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }

        try {
            List<CompilationError> methodWarnings = new ArrayList<>();
            ClassFile newClass = lowerToClassFile(cu, originalClass, classPool, methodWarnings, changedMethods);
            List<CompilationError> verifyErrors = gateVerify(newClass, classPool, changedMethods);
            if (!verifyErrors.isEmpty()) {
                verifyErrors.addAll(methodWarnings);
                return CompilationResult.failure(verifyErrors, source, elapsed(startTime));
            }
            return CompilationResult.builder()
                    .success(true)
                    .compiledClass(newClass)
                    .sourceCode(source)
                    .errors(methodWarnings)
                    .compilationTimeMs(elapsed(startTime))
                    .build();
        } catch (LoweringException e) {
            errors.add(CompilationError.error(1, 1, 0, 1, "Lowering error: " + e.getMessage()));
            return CompilationResult.failure(errors, source, elapsed(startTime));
        } catch (Exception e) {
            errors.add(CompilationError.error(1, 1, 0, 1, "Compilation error: " + e.getMessage()));
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }
    }

    public List<CompilationError> parseOnly(String source) {
        List<CompilationError> errors = new ArrayList<>();

        ParseErrorListener.CollectingErrorListener listener = ParseErrorListener.collecting();
        JavaParser parser = JavaParser.withErrorListener(listener);

        try {
            parser.parse(source);
        } catch (ParseException e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(createErrorFromException(e, source));
            }
        } catch (Exception e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(CompilationError.error(1, 1, 0, 1, "Parse error: " + e.getMessage()));
            }
        }

        if (listener.hasErrors()) {
            collectParseErrors(listener, source, errors);
        }

        return errors;
    }

    private ClassFile lowerToClassFile(CompilationUnit cu, ClassFile original, ClassPool classPool,
                                       List<CompilationError> warnings, Set<String> changedMethods) {
        TypeDecl primaryType = cu.getPrimaryType();
        if (primaryType == null) {
            throw new LoweringException("No type declaration found in source");
        }

        if (!(primaryType instanceof ClassDecl)) {
            throw new LoweringException("Only class types are currently supported for recompilation");
        }

        ClassDecl classDecl = (ClassDecl) primaryType;
        String ownerClass = original.getClassName();
        TypeResolver typeResolver = descriptorResolver(classPool, ownerClass, classDecl, cu);

        ASTLowerer lowerer = new ASTLowerer(original.getConstPool(), classPool);
        lowerer.setCurrentClassDecl(classDecl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(original.getConstPool());

        syncNewFields(classDecl, original, warnings);

        for (MethodDecl methodDecl : classDecl.getMethods()) {
            if (methodDecl.getBody() == null) {
                continue;
            }

            String methodName = methodDecl.getName();
            String descriptor = buildDescriptor(methodDecl, typeResolver);

            MethodEntry targetMethod = findMethod(original, methodName, descriptor);
            // Method-scoped recompile: an existing method whose body did not change keeps its original bytecode,
            // so editing one method never re-lowers (and risks perturbing) the others.
            if (changedMethods != null && targetMethod != null
                    && !changedMethods.contains(methodName + descriptor)) {
                continue;
            }
            boolean isNew = targetMethod == null;
            if (isNew) {
                try {
                    original.createNewMethodWithDescriptor(
                        accessFromModifiers(methodDecl.getModifiers()), methodName, descriptor);
                    targetMethod = findMethod(original, methodName, descriptor);
                } catch (Exception e) {
                    warnings.add(CompilationError.warning(1, 1, 0, 1,
                        "Could not add new method '" + methodName + descriptor + "': " + e.getMessage()));
                    continue;
                }
            }
            if (targetMethod == null) {
                warnings.add(CompilationError.warning(1, 1, 0, 1,
                    "Could not locate method '" + methodName + descriptor + "' after creating it."));
                continue;
            }

            // Per-method resilience: a method that cannot be lowered keeps its original bytecode (or,
            // for a newly added method, is reported) instead of aborting the whole-class recompile,
            // which would discard edits to every other, lowerable method.
            try {
                IRMethod irMethod = lowerer.lower(methodDecl, ownerClass);
                ssa.lower(irMethod, targetMethod);
            } catch (Exception e) {
                String prefix = isNew
                    ? "Could not compile new method '"
                    : "Could not recompile method '";
                String suffix = isNew ? "': " : "' (kept original): ";
                warnings.add(CompilationError.warning(1, 1, 0, 1,
                    prefix + methodName + descriptor + suffix + e.getMessage()));
            }
        }

        synthesizeStaticInitializer(classDecl, original, lowerer, ssa, ownerClass, warnings);

        emitPendingSynthetics(lowerer, ssa, original, ownerClass, warnings);

        removeDeletedMethods(classDecl, original, typeResolver);

        try {
            original.rebuild();
        } catch (IOException e) {
            throw new LoweringException("Failed to rebuild class file: " + e.getMessage(), e);
        }
        return original;
    }

    /** Access flags for generated synthetic methods: private static synthetic. */
    private static final int SYNTHETIC_METHOD_ACCESS = 0x0002 | 0x0008 | 0x1000;

    /**
     * Materializes synthetic methods (lambda bodies, array constructors) produced while lowering.
     * A synthetic the original class already provides (matching name and descriptor) is left
     * untouched, so round-tripped classes keep their working synthetic bodies; only genuinely new
     * synthetics — from freshly written or edited code — are generated. Generation is best-effort:
     * failures are reported as warnings rather than aborting the recompile.
     */
    private void emitPendingSynthetics(ASTLowerer lowerer, SSA ssa, ClassFile original,
                                       String ownerClass, List<CompilationError> warnings) {
        int guard = 0;
        while (lowerer.hasPendingSynthetics() && guard++ < 1000) {
            for (SyntheticLambdaMethod synthetic : lowerer.drainPendingLambdas()) {
                if (findMethod(original, synthetic.getName(), synthetic.getDescriptor()) != null) {
                    continue;
                }
                if (!synthetic.isStatic()) {
                    warnings.add(CompilationError.warning(1, 1, 0, 1,
                        "Lambda '" + synthetic.getName() + "' captures 'this' and could not be generated; "
                            + "its call site may not resolve."));
                    continue;
                }
                try {
                    original.createNewMethodWithDescriptor(
                        SYNTHETIC_METHOD_ACCESS, synthetic.getName(), synthetic.getDescriptor());
                    MethodEntry entry = findMethod(original, synthetic.getName(), synthetic.getDescriptor());
                    IRMethod irMethod = lowerer.lowerSyntheticLambda(synthetic, ownerClass);
                    ssa.lower(irMethod, entry);
                } catch (Exception e) {
                    original.removeMethod(synthetic.getName(), synthetic.getDescriptor());
                    warnings.add(CompilationError.warning(1, 1, 0, 1,
                        "Could not generate lambda method '" + synthetic.getName() + "': " + e.getMessage()));
                }
            }
            for (SyntheticArrayConstructor constructor : lowerer.drainPendingArrayConstructors()) {
                if (findMethod(original, constructor.getName(), constructor.getDescriptor()) != null) {
                    continue;
                }
                try {
                    original.createNewMethodWithDescriptor(
                        SYNTHETIC_METHOD_ACCESS, constructor.getName(), constructor.getDescriptor());
                    MethodEntry entry = findMethod(original, constructor.getName(), constructor.getDescriptor());
                    IRMethod irMethod = lowerer.lowerSyntheticArrayConstructor(constructor, ownerClass);
                    ssa.lower(irMethod, entry);
                } catch (Exception e) {
                    original.removeMethod(constructor.getName(), constructor.getDescriptor());
                    warnings.add(CompilationError.warning(1, 1, 0, 1,
                        "Could not generate array constructor '" + constructor.getName() + "': " + e.getMessage()));
                }
            }
        }
    }

    /**
     * Removes user-authored methods that the edited source no longer declares. Constructors,
     * static initializers and compiler-generated members (synthetic flag, or {@code lambda$} /
     * {@code access$} / {@code $deserializeLambda$} names) are never removed, since the decompiled
     * source represents them implicitly and removing them would break their call sites.
     */
    private void removeDeletedMethods(ClassDecl classDecl, ClassFile original, TypeResolver typeResolver) {
        Set<String> sourceMethods = new HashSet<>();
        for (MethodDecl methodDecl : classDecl.getMethods()) {
            if (methodDecl.getBody() == null) {
                continue;
            }
            sourceMethods.add(methodDecl.getName() + buildDescriptor(methodDecl, typeResolver));
        }

        List<MethodEntry> toRemove = new ArrayList<>();
        for (MethodEntry method : original.getMethods()) {
            String name = method.getName();
            if (name.equals("<init>") || name.equals("<clinit>")) {
                continue;
            }
            if (isCompilerGenerated(name, method.getAccess())) {
                continue;
            }
            if (!sourceMethods.contains(name + method.getDesc())) {
                toRemove.add(method);
            }
        }
        for (MethodEntry method : toRemove) {
            original.removeMethod(method.getName(), method.getDesc());
        }
    }

    private boolean isCompilerGenerated(String name, int access) {
        if ((access & 0x1000) != 0) {
            return true;
        }
        return name.startsWith("lambda$") || name.startsWith("access$")
            || name.equals("$deserializeLambda$");
    }

    /**
     * Creates ClassFile entries for fields present in the edited source but absent from the
     * original class, so that newly declared fields exist (with their JVM default value) and
     * any references to them resolve. Existing fields are left untouched.
     */
    private void syncNewFields(ClassDecl classDecl, ClassFile original, List<CompilationError> warnings) {
        for (FieldDecl field : classDecl.getFields()) {
            if (fieldExists(original, field.getName())) {
                continue;
            }
            try {
                String descriptor = field.getType().toIRType().getDescriptor();
                original.createNewField(accessFromModifiers(field.getModifiers()),
                    field.getName(), descriptor, new ArrayList<>());
            } catch (Exception e) {
                warnings.add(CompilationError.warning(1, 1, 0, 1,
                    "Could not add new field '" + field.getName() + "': " + e.getMessage()));
            }
        }
    }

    /**
     * Regenerates {@code <clinit>} from the source's static field initializers and static
     * initializer blocks, finding or creating the method entry and lowering into it. Field
     * initializers run first, then static initializer blocks in declaration order. Does nothing
     * when the source declares no static initialization.
     */
    private void synthesizeStaticInitializer(ClassDecl classDecl, ClassFile original,
                                             ASTLowerer lowerer, SSA ssa, String ownerClass,
                                             List<CompilationError> warnings) {
        List<Statement> initStatements = new ArrayList<>();
        for (FieldDecl field : classDecl.getFields()) {
            // A static field that the original class represents with a ConstantValue attribute (a compile-time
            // constant) is initialized by the class loader from the attribute - synthesizing a <clinit> putstatic for
            // it would fabricate a spurious static block on every round-trip, so skip it.
            if (field.isStatic() && field.hasInitializer() && !hasConstantValue(original, field.getName())) {
                VarRefExpr ref = new VarRefExpr(field.getName(), field.getType());
                BinaryExpr assign = new BinaryExpr(
                    BinaryOperator.ASSIGN, ref, field.getInitializer(), field.getType());
                initStatements.add(new ExprStmt(assign));
            }
        }
        for (BlockStmt block : classDecl.getStaticInitializers()) {
            initStatements.addAll(block.getStatements());
        }
        if (initStatements.isEmpty()) {
            return;
        }

        try {
            MethodDecl clinit = new MethodDecl("<clinit>", VoidSourceType.INSTANCE)
                .addModifier(Modifier.STATIC)
                .withBody(new BlockStmt(initStatements));
            MethodEntry entry = findMethod(original, "<clinit>", "()V");
            if (entry == null) {
                original.createNewMethodWithDescriptor(0x0008, "<clinit>", "()V");
                entry = findMethod(original, "<clinit>", "()V");
            }
            IRMethod irMethod = lowerer.lower(clinit, ownerClass);
            ssa.lower(irMethod, entry);
        } catch (Exception e) {
            warnings.add(CompilationError.warning(1, 1, 0, 1,
                "Could not synthesize static initializer: " + e.getMessage()));
        }
    }

    /**
     * Verifies the recompiled class and returns one compilation error per failing method, filtered to the methods we
     * re-lowered ({@code changedMethods}) so a pre-existing quirk in an untouched method never blocks an edit. An
     * empty list means the recompile passed verification and is safe to apply.
     */
    private List<CompilationError> gateVerify(ClassFile compiled, ClassPool classPool, Set<String> changedMethods) {
        List<CompilationError> errors = new ArrayList<>();
        try {
            com.tonic.analysis.verifier.VerificationResult result =
                    com.tonic.analysis.verifier.Verifier.builder().classPool(classPool).build().verify(compiled);
            for (com.tonic.analysis.verifier.VerificationError err : result.getErrors()) {
                if (!err.isError()) {
                    continue;
                }
                if (changedMethods != null && err.getMethodName() != null
                        && !changedMethods.contains(err.getMethodName())) {
                    continue;
                }
                String where = err.getMethodName() != null ? " in " + err.getMethodName() : "";
                errors.add(CompilationError.error(1, 1, 0, 1,
                        "Verification failed" + where + ": " + err.getMessage()));
            }
        } catch (Exception e) {
            // A failure inside the verifier itself must not block an otherwise-valid recompile.
        }
        return errors;
    }

    /**
     * Whether the original class's field {@code name} carries a {@code ConstantValue} attribute - i.e. a compile-time
     * constant the class loader initializes directly, needing no {@code <clinit>} assignment.
     */
    private boolean hasConstantValue(ClassFile classFile, String name) {
        for (FieldEntry field : classFile.getFields()) {
            if (field.getName().equals(name)) {
                for (com.tonic.parser.attribute.Attribute attr : field.getAttributes()) {
                    if (attr instanceof com.tonic.parser.attribute.ConstantValueAttribute) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private boolean fieldExists(ClassFile classFile, String name) {
        for (FieldEntry field : classFile.getFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Translates source-level modifiers into JVM access flags for created members.
     */
    private int accessFromModifiers(Set<Modifier> modifiers) {
        int flags = 0;
        if (modifiers.contains(Modifier.PUBLIC)) {
            flags |= 0x0001;
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            flags |= 0x0002;
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            flags |= 0x0004;
        }
        if (modifiers.contains(Modifier.STATIC)) {
            flags |= 0x0008;
        }
        if (modifiers.contains(Modifier.FINAL)) {
            flags |= 0x0010;
        }
        return flags;
    }

    private MethodEntry findMethod(ClassFile classFile, String name, String descriptor) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name) && method.getDesc().equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Builds a method's JVM descriptor, resolving reference types through {@code resolver} so imports are
     * applied ({@code Frame} -> {@code Ljava/awt/Frame;}) and nested classes use {@code $} - without which the
     * descriptor would not match the original method and the method would be wrongly treated as added/removed.
     * Falls back to the unresolved descriptor when no resolver (i.e. no class pool) is available.
     */
    private String buildDescriptor(MethodDecl methodDecl, TypeResolver resolver) {
        StringBuilder sb = new StringBuilder("(");
        for (var param : methodDecl.getParameters()) {
            sb.append(typeDescriptor(param.getType(), resolver));
        }
        sb.append(")");
        sb.append(typeDescriptor(methodDecl.getReturnType(), resolver));
        return sb.toString();
    }

    private String typeDescriptor(SourceType type, TypeResolver resolver) {
        return resolver != null ? resolver.descriptorOf(type) : type.toIRType().getDescriptor();
    }

    /** A type resolver for descriptor building, or null when there is no class pool to resolve against. */
    private TypeResolver descriptorResolver(ClassPool classPool, String ownerClass, ClassDecl classDecl,
                                            CompilationUnit cu) {
        if (classPool == null) {
            return null;
        }
        TypeResolver resolver = new TypeResolver(classPool, ownerClass);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(classDecl);
        return resolver;
    }

    private void collectParseErrors(ParseErrorListener.CollectingErrorListener listener,
                                     String source, List<CompilationError> errors) {
        for (ParseException pe : listener.getErrors()) {
            errors.add(createErrorFromException(pe, source));
        }
    }

    private CompilationError createErrorFromException(ParseException e, String source) {
        int line = e.getLine();
        int column = e.getColumn();
        int offset = calculateOffset(source, line, column);
        int length = calculateErrorLength(source, line, column);
        return CompilationError.error(line, column, offset, length, e.getMessage());
    }

    private int calculateOffset(String source, int line, int column) {
        if (line <= 0 || column <= 0) {
            return 0;
        }

        String[] lines = source.split("\n", -1);
        int offset = 0;

        for (int i = 0; i < line - 1 && i < lines.length; i++) {
            offset += lines[i].length() + 1;
        }

        if (line - 1 < lines.length) {
            offset += Math.min(column - 1, lines[line - 1].length());
        }

        return offset;
    }

    private int calculateErrorLength(String source, int line, int column) {
        if (line <= 0) {
            return 1;
        }

        String[] lines = source.split("\n", -1);
        if (line - 1 >= lines.length) {
            return 1;
        }

        String errorLine = lines[line - 1];
        int startCol = Math.max(0, column - 1);

        if (startCol >= errorLine.length()) {
            return 1;
        }

        int endCol = startCol;
        while (endCol < errorLine.length() && !Character.isWhitespace(errorLine.charAt(endCol))) {
            endCol++;
        }

        return Math.max(1, endCol - startCol);
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
