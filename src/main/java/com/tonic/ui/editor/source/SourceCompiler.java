package com.tonic.ui.editor.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.ConstructorDecl;
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

import com.tonic.analysis.source.ast.SourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses edited Java source and lowers it into a class file based on the original class, verifying the recompiled methods. */
public class SourceCompiler
{

    /**
     * Recompiles every method of the source.
     *
     * @param source the edited source
     * @param originalClass the class the source was decompiled from
     * @param classPool the pool used to resolve types
     * @return the result, carrying parse, lowering or verify errors on failure
     */
    public CompilationResult compile(String source, ClassFile originalClass, ClassPool classPool)
    {
        return compile(source, originalClass, classPool, null);
    }

    /**
     * Recompiles the source, re-lowering only the changed methods; a recompiled method that fails verification fails the whole compile.
     *
     * @param source the edited source
     * @param originalClass the class the source was decompiled from
     * @param classPool the pool used to resolve types; the working copy replaces the original in it only on success
     * @param changedMethods the name plus descriptor keys of the methods to re-lower, or null for all
     * @return the result, carrying parse, lowering or verify errors on failure and any method warnings on success
     */
    public CompilationResult compile(String source, ClassFile originalClass, ClassPool classPool, Set<String> changedMethods)
    {
        long startTime = System.currentTimeMillis();
        List<CompilationError> errors = new ArrayList<>();

        ParseErrorListener.CollectingErrorListener listener = ParseErrorListener.collecting();
        JavaParser parser = JavaParser.withErrorListener(listener);

        CompilationUnit cu;
        try
        {
            cu = parser.parse(source);
        }
        catch (ParseException e)
        {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty())
            {
                errors.add(createErrorFromException(e, source));
            }
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }
        catch (Exception e)
        {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty())
            {
                errors.add(CompilationError.error(1, 1, 0, 1, "Parse error: " + e.getMessage()));
            }
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }

        if (listener.hasErrors())
        {
            collectParseErrors(listener, source, errors);
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }

        Map<String, Integer> memberLines = buildMemberLineMap(cu, originalClass, classPool);
        ClassFile working = workingCopy(originalClass);
        boolean swappedIn = swapInPool(classPool, originalClass, working);
        boolean committed = false;
        try
        {
            List<CompilationError> methodWarnings = new ArrayList<>();
            ClassFile newClass = lowerToClassFile(cu, working, classPool, methodWarnings, changedMethods);
            List<CompilationError> verifyErrors = gateVerify(newClass, classPool, changedMethods, memberLines);
            if (!verifyErrors.isEmpty())
            {
                verifyErrors.addAll(methodWarnings);
                return CompilationResult.failure(verifyErrors, source, elapsed(startTime));
            }
            committed = true;
            return CompilationResult.builder()
                    .success(true)
                    .compiledClass(newClass)
                    .sourceCode(source)
                    .errors(methodWarnings)
                    .compilationTimeMs(elapsed(startTime))
                    .build();
        }
        catch (LoweringException e)
        {
            errors.add(CompilationError.error(1, 1, 0, 1, "Lowering error: " + e.getMessage()));
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }
        catch (Exception e)
        {
            errors.add(CompilationError.error(1, 1, 0, 1, "Compilation error: " + e.getMessage()));
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }
        finally
        {
            if (swappedIn && !committed)
            {
                restoreInPool(classPool, working, originalClass);
            }
        }
    }

    private ClassFile workingCopy(ClassFile original)
    {
        try
        {
            return new ClassFile(new java.io.ByteArrayInputStream(original.write()));
        }
        catch (Exception e)
        {
            return original;
        }
    }

    private boolean swapInPool(ClassPool pool, ClassFile original, ClassFile working)
    {
        if (pool == null || working == original)
        {
            return false;
        }
        pool.remove(original.getClassName());
        pool.put(working);
        return true;
    }

    private void restoreInPool(ClassPool pool, ClassFile working, ClassFile original)
    {
        pool.remove(working.getClassName());
        pool.put(original);
    }

    /**
     * Parses the source without compiling it.
     *
     * @param source the edited source
     * @return the parse errors, empty when it parses cleanly
     */
    public List<CompilationError> parseOnly(String source)
    {
        List<CompilationError> errors = new ArrayList<>();

        ParseErrorListener.CollectingErrorListener listener = ParseErrorListener.collecting();
        JavaParser parser = JavaParser.withErrorListener(listener);

        Exception failure = null;
        try
        {
            parser.parse(source);
        }
        catch (Exception e)
        {
            failure = e;
        }

        collectParseErrors(listener, source, errors);
        if (errors.isEmpty() && failure instanceof ParseException)
        {
            errors.add(createErrorFromException((ParseException) failure, source));
        }
        else if (errors.isEmpty() && failure != null)
        {
            errors.add(CompilationError.error(1, 1, 0, 1, "Parse error: " + failure.getMessage()));
        }
        return errors;
    }

    private ClassFile lowerToClassFile(CompilationUnit cu, ClassFile original, ClassPool classPool, List<CompilationError> warnings, Set<String> changedMethods)
    {
        TypeDecl primaryType = cu.getPrimaryType();
        if (primaryType == null)
        {
            throw new LoweringException("No type declaration found in source");
        }

        if (!(primaryType instanceof ClassDecl))
        {
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

        for (MethodDecl methodDecl : classDecl.getMethods())
        {
            if (methodDecl.getBody() == null)
            {
                continue;
            }

            String methodName = methodDecl.getName();
            String descriptor = buildDescriptor(methodDecl, typeResolver);

            MethodEntry targetMethod = findMethod(original, methodName, descriptor);
            if (changedMethods != null && targetMethod != null
                    && !changedMethods.contains(methodName + descriptor))
            {
                continue;
            }
            boolean isNew = targetMethod == null;
            if (isNew)
            {
                try
                {
                    original.createNewMethodWithDescriptor(accessFromModifiers(methodDecl.getModifiers()), methodName, descriptor);
                    targetMethod = findMethod(original, methodName, descriptor);
                }
                catch (Exception e)
                {
                    warnings.add(CompilationError.warning(1, 1, 0, 1, "Could not add new method '" + methodName + descriptor + "': " + e.getMessage()));
                    continue;
                }
            }
            if (targetMethod == null)
            {
                warnings.add(CompilationError.warning(1, 1, 0, 1, "Could not locate method '" + methodName + descriptor + "' after creating it."));
                continue;
            }

            try
            {
                IRMethod irMethod = lowerer.lower(methodDecl, ownerClass);
                ssa.lower(irMethod, targetMethod);
            }
            catch (Exception e)
            {
                String prefix = isNew
                        ? "Could not compile new method '"
                        : "Could not recompile method '";
                String suffix = isNew ? "': " : "' (kept original): ";
                warnings.add(CompilationError.warning(lineOf(methodDecl.getLocation()), 1, 0, 1, prefix + methodName + descriptor + suffix + e.getMessage()));
            }
        }

        lowerConstructors(classDecl, original, lowerer, ssa, typeResolver, ownerClass, changedMethods, warnings);

        synthesizeStaticInitializer(classDecl, original, lowerer, ssa, ownerClass, warnings);

        emitPendingSynthetics(lowerer, ssa, original, ownerClass, warnings);

        removeDeletedMethods(classDecl, original, typeResolver);

        try
        {
            original.rebuild();
        }
        catch (IOException e)
        {
            throw new LoweringException("Failed to rebuild class file: " + e.getMessage(), e);
        }
        return original;
    }

    private void lowerConstructors(ClassDecl classDecl, ClassFile original, ASTLowerer lowerer, SSA ssa, TypeResolver typeResolver, String ownerClass, Set<String> changedMethods, List<CompilationError> warnings)
    {
        for (ConstructorDecl ctorDecl : classDecl.getConstructors())
        {
            if (ctorDecl.getBody() == null)
            {
                continue;
            }
            String descriptor = ctorDescriptor(ctorDecl, typeResolver);
            String key = "<init>" + descriptor;
            if (changedMethods != null && !changedMethods.contains(key))
            {
                continue;
            }
            MethodEntry targetMethod = findMethod(original, "<init>", descriptor);
            if (targetMethod == null)
            {
                continue;
            }
            try
            {
                IRMethod irMethod = lowerer.lower(toInitMethodDecl(ctorDecl), ownerClass);
                ssa.lower(irMethod, targetMethod);
            }
            catch (Exception e)
            {
                String detail = e.getMessage() != null ? e.getMessage()
                        : e.getClass().getSimpleName()
                        + (e.getStackTrace().length > 0 ? " at " + e.getStackTrace()[0] : "");
                warnings.add(CompilationError.warning(lineOf(ctorDecl.getLocation()), 1, 0, 1, "Could not recompile constructor '" + key + "' (kept original): " + detail));
            }
        }
    }

    private String ctorDescriptor(ConstructorDecl ctorDecl, TypeResolver resolver)
    {
        StringBuilder sb = new StringBuilder("(");
        for (var param : ctorDecl.getParameters())
        {
            sb.append(typeDescriptor(param.getType(), resolver));
        }
        return sb.append(")V").toString();
    }

    private MethodDecl toInitMethodDecl(ConstructorDecl ctorDecl)
    {
        MethodDecl methodDecl = new MethodDecl("<init>", VoidSourceType.INSTANCE).withModifiers(ctorDecl.getModifiers());
        for (var param : ctorDecl.getParameters())
        {
            methodDecl.addParameter(param);
        }
        return methodDecl.withBody(ctorDecl.getBody());
    }

    private static final int SYNTHETIC_METHOD_ACCESS = 0x0002 | 0x0008 | 0x1000;

    private void emitPendingSynthetics(ASTLowerer lowerer, SSA ssa, ClassFile original, String ownerClass, List<CompilationError> warnings)
    {
        int guard = 0;
        while (lowerer.hasPendingSynthetics() && guard++ < 1000)
        {
            for (SyntheticLambdaMethod synthetic : lowerer.drainPendingLambdas())
            {
                if (findMethod(original, synthetic.getName(), synthetic.getDescriptor()) != null)
                {
                    continue;
                }
                if (!synthetic.isStatic())
                {
                    warnings.add(CompilationError.warning(1, 1, 0, 1, "Lambda '" + synthetic.getName() + "' captures 'this' and could not be generated; " + "its call site may not resolve."));
                    continue;
                }
                try
                {
                    original.createNewMethodWithDescriptor(SYNTHETIC_METHOD_ACCESS, synthetic.getName(), synthetic.getDescriptor());
                    MethodEntry entry = findMethod(original, synthetic.getName(), synthetic.getDescriptor());
                    IRMethod irMethod = lowerer.lowerSyntheticLambda(synthetic, ownerClass);
                    ssa.lower(irMethod, entry);
                }
                catch (Exception e)
                {
                    original.removeMethod(synthetic.getName(), synthetic.getDescriptor());
                    warnings.add(CompilationError.warning(1, 1, 0, 1, "Could not generate lambda method '" + synthetic.getName() + "': " + e.getMessage()));
                }
            }
            for (SyntheticArrayConstructor constructor : lowerer.drainPendingArrayConstructors())
            {
                if (findMethod(original, constructor.getName(), constructor.getDescriptor()) != null)
                {
                    continue;
                }
                try
                {
                    original.createNewMethodWithDescriptor(SYNTHETIC_METHOD_ACCESS, constructor.getName(), constructor.getDescriptor());
                    MethodEntry entry = findMethod(original, constructor.getName(), constructor.getDescriptor());
                    IRMethod irMethod = lowerer.lowerSyntheticArrayConstructor(constructor, ownerClass);
                    ssa.lower(irMethod, entry);
                }
                catch (Exception e)
                {
                    original.removeMethod(constructor.getName(), constructor.getDescriptor());
                    warnings.add(CompilationError.warning(1, 1, 0, 1, "Could not generate array constructor '" + constructor.getName() + "': " + e.getMessage()));
                }
            }
        }
    }

    private void removeDeletedMethods(ClassDecl classDecl, ClassFile original, TypeResolver typeResolver)
    {
        Set<String> sourceMethods = new HashSet<>();
        for (MethodDecl methodDecl : classDecl.getMethods())
        {
            if (methodDecl.getBody() == null)
            {
                continue;
            }
            sourceMethods.add(methodDecl.getName() + buildDescriptor(methodDecl, typeResolver));
        }

        List<MethodEntry> toRemove = new ArrayList<>();
        for (MethodEntry method : original.getMethods())
        {
            String name = method.getName();
            if (name.equals("<init>") || name.equals("<clinit>"))
            {
                continue;
            }
            if (isCompilerGenerated(name, method.getAccess()))
            {
                continue;
            }
            if (!sourceMethods.contains(name + method.getDesc()))
            {
                toRemove.add(method);
            }
        }
        for (MethodEntry method : toRemove)
        {
            original.removeMethod(method.getName(), method.getDesc());
        }
    }

    private boolean isCompilerGenerated(String name, int access)
    {
        if ((access & 0x1000) != 0)
        {
            return true;
        }
        return name.startsWith("lambda$") || name.startsWith("access$")
                || name.equals("$deserializeLambda$");
    }

    private void syncNewFields(ClassDecl classDecl, ClassFile original, List<CompilationError> warnings)
    {
        for (FieldDecl field : classDecl.getFields())
        {
            if (fieldExists(original, field.getName()))
            {
                continue;
            }
            try
            {
                String descriptor = field.getType().toIRType().getDescriptor();
                original.createNewField(accessFromModifiers(field.getModifiers()), field.getName(), descriptor, new ArrayList<>());
            }
            catch (Exception e)
            {
                warnings.add(CompilationError.warning(1, 1, 0, 1, "Could not add new field '" + field.getName() + "': " + e.getMessage()));
            }
        }
    }

    private void synthesizeStaticInitializer(ClassDecl classDecl, ClassFile original, ASTLowerer lowerer, SSA ssa, String ownerClass, List<CompilationError> warnings)
    {
        List<Statement> initStatements = new ArrayList<>();
        for (FieldDecl field : classDecl.getFields())
        {
            if (field.isStatic() && field.hasInitializer() && !hasConstantValue(original, field.getName()))
            {
                VarRefExpr ref = new VarRefExpr(field.getName(), field.getType());
                BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, ref, field.getInitializer(), field.getType());
                initStatements.add(new ExprStmt(assign));
            }
        }
        for (BlockStmt block : classDecl.getStaticInitializers())
        {
            initStatements.addAll(block.getStatements());
        }
        if (initStatements.isEmpty())
        {
            return;
        }

        try
        {
            MethodDecl clinit = new MethodDecl("<clinit>", VoidSourceType.INSTANCE)
                    .addModifier(Modifier.STATIC)
                    .withBody(new BlockStmt(initStatements));
            MethodEntry entry = findMethod(original, "<clinit>", "()V");
            if (entry == null)
            {
                original.createNewMethodWithDescriptor(0x0008, "<clinit>", "()V");
                entry = findMethod(original, "<clinit>", "()V");
            }
            IRMethod irMethod = lowerer.lower(clinit, ownerClass);
            ssa.lower(irMethod, entry);
        }
        catch (Exception e)
        {
            warnings.add(CompilationError.warning(1, 1, 0, 1, "Could not synthesize static initializer: " + e.getMessage()));
        }
    }

    private List<CompilationError> gateVerify(ClassFile compiled, ClassPool classPool, Set<String> changedMethods, Map<String, Integer> memberLines)
    {
        List<CompilationError> errors = new ArrayList<>();
        try
        {
            com.tonic.analysis.verifier.VerificationResult result =
                    com.tonic.analysis.verifier.Verifier.builder().classPool(classPool).build().verify(compiled);
            for (com.tonic.analysis.verifier.VerificationError err : result.getErrors())
            {
                if (!err.isError())
                {
                    continue;
                }
                if (changedMethods != null && err.getMethodName() != null
                        && !changedMethods.contains(err.getMethodName()))
                {
                    continue;
                }
                String where = err.getMethodName() != null ? " in " + err.getMethodName() : "";
                int line = err.getMethodName() != null ? memberLines.getOrDefault(err.getMethodName(), 1) : 1;
                errors.add(CompilationError.error(line, 1, 0, 1, "Verification failed" + where + ": " + err.getMessage()));
            }
        }
        catch (Exception e)
        {
        }
        return errors;
    }

    private Map<String, Integer> buildMemberLineMap(CompilationUnit cu, ClassFile original, ClassPool classPool)
    {
        Map<String, Integer> map = new HashMap<>();
        TypeDecl primary = cu.getPrimaryType();
        if (!(primary instanceof ClassDecl))
        {
            return map;
        }
        ClassDecl classDecl = (ClassDecl) primary;
        TypeResolver resolver = descriptorResolver(classPool, original.getClassName(), classDecl, cu);
        for (MethodDecl m : classDecl.getMethods())
        {
            if (m.getBody() != null)
            {
                map.put(m.getName() + buildDescriptor(m, resolver), lineOf(m.getLocation()));
            }
        }
        for (ConstructorDecl c : classDecl.getConstructors())
        {
            if (c.getBody() != null)
            {
                map.put("<init>" + ctorDescriptor(c, resolver), lineOf(c.getLocation()));
            }
        }
        return map;
    }

    private int lineOf(SourceLocation location)
    {
        return location != null && location.hasLineNumber() ? location.lineNumber() : 1;
    }

    private boolean hasConstantValue(ClassFile classFile, String name)
    {
        for (FieldEntry field : classFile.getFields())
        {
            if (field.getName().equals(name))
            {
                for (com.tonic.parser.attribute.Attribute attr : field.getAttributes())
                {
                    if (attr instanceof com.tonic.parser.attribute.ConstantValueAttribute)
                    {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private boolean fieldExists(ClassFile classFile, String name)
    {
        for (FieldEntry field : classFile.getFields())
        {
            if (field.getName().equals(name))
            {
                return true;
            }
        }
        return false;
    }

    private int accessFromModifiers(Set<Modifier> modifiers)
    {
        int flags = 0;
        if (modifiers.contains(Modifier.PUBLIC))
        {
            flags |= 0x0001;
        }
        if (modifiers.contains(Modifier.PRIVATE))
        {
            flags |= 0x0002;
        }
        if (modifiers.contains(Modifier.PROTECTED))
        {
            flags |= 0x0004;
        }
        if (modifiers.contains(Modifier.STATIC))
        {
            flags |= 0x0008;
        }
        if (modifiers.contains(Modifier.FINAL))
        {
            flags |= 0x0010;
        }
        return flags;
    }

    private MethodEntry findMethod(ClassFile classFile, String name, String descriptor)
    {
        for (MethodEntry method : classFile.getMethods())
        {
            if (method.getName().equals(name) && method.getDesc().equals(descriptor))
            {
                return method;
            }
        }
        return null;
    }

    private String buildDescriptor(MethodDecl methodDecl, TypeResolver resolver)
    {
        StringBuilder sb = new StringBuilder("(");
        for (var param : methodDecl.getParameters())
        {
            sb.append(typeDescriptor(param.getType(), resolver));
        }
        sb.append(")");
        sb.append(typeDescriptor(methodDecl.getReturnType(), resolver));
        return sb.toString();
    }

    private String typeDescriptor(SourceType type, TypeResolver resolver)
    {
        return resolver != null ? resolver.descriptorOf(type) : type.toIRType().getDescriptor();
    }

    private TypeResolver descriptorResolver(ClassPool classPool, String ownerClass, ClassDecl classDecl, CompilationUnit cu)
    {
        if (classPool == null)
        {
            return null;
        }
        TypeResolver resolver = new TypeResolver(classPool, ownerClass);
        resolver.setImports(cu.getImports());
        resolver.setCurrentClassDecl(classDecl);
        return resolver;
    }

    private void collectParseErrors(ParseErrorListener.CollectingErrorListener listener, String source, List<CompilationError> errors)
    {
        for (ParseException pe : listener.getErrors())
        {
            errors.add(createErrorFromException(pe, source));
        }
    }

    private CompilationError createErrorFromException(ParseException e, String source)
    {
        int line = e.getLine();
        int column = e.getColumn();
        int offset = calculateOffset(source, line, column);
        int length = calculateErrorLength(source, line, column);
        return CompilationError.error(line, column, offset, length, e.getMessage());
    }

    private int calculateOffset(String source, int line, int column)
    {
        if (line <= 0 || column <= 0)
        {
            return 0;
        }

        String[] lines = source.split("\n", -1);
        int offset = 0;

        for (int i = 0; i < line - 1 && i < lines.length; i++)
        {
            offset += lines[i].length() + 1;
        }

        if (line - 1 < lines.length)
        {
            offset += Math.min(column - 1, lines[line - 1].length());
        }

        return offset;
    }

    private int calculateErrorLength(String source, int line, int column)
    {
        if (line <= 0)
        {
            return 1;
        }

        String[] lines = source.split("\n", -1);
        if (line - 1 >= lines.length)
        {
            return 1;
        }

        String errorLine = lines[line - 1];
        int startCol = Math.max(0, column - 1);

        if (startCol >= errorLine.length())
        {
            return 1;
        }

        int endCol = startCol;
        while (endCol < errorLine.length() && !Character.isWhitespace(errorLine.charAt(endCol)))
        {
            endCol++;
        }

        return Math.max(1, endCol - startCol);
    }

    private long elapsed(long startTime)
    {
        return System.currentTimeMillis() - startTime;
    }
}
