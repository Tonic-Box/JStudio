package com.tonic.ui.live;

import com.tonic.analysis.source.ast.ASTPrinter;
import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.decl.TypeDecl;
import com.tonic.analysis.source.lower.TypeResolver;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.parser.ClassPool;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Determines which methods of a class actually changed between two revisions of its source, keyed by JVM
 * signature ({@code name + descriptor}). A live patch grafts only these bodies onto the running class, leaving
 * every untouched method - and the class's synthetic members - byte-identical, which is what lets a JVMTI
 * redefine accept the result (it can change method bodies but never the member set).
 *
 * <p>Bodies are compared by their canonical {@link ASTPrinter} rendering, so reformatting alone is not a
 * change. The descriptor is derived with the same front end {@code SourceCompiler} uses, so the keys line up
 * with the recompiled {@code ClassFile}'s methods exactly (including overloads).
 */
public final class MethodBodyDiff {

    private MethodBodyDiff() {
    }

    /**
     * Returns the {@code name + descriptor} keys of the primary type's methods whose body differs between
     * {@code baseline} and {@code edited}. Methods present in only one revision are added/removed members,
     * not body changes, and are handled at the bytecode level by {@link LivePatch}.
     *
     * @param baseline   the source as it was before the edit (e.g. the original decompilation)
     * @param edited     the source the user just compiled
     * @param classPool  the project's class pool, used to resolve reference types in signatures (may be null)
     * @param ownerClass the internal name of the class being patched
     * @return the signatures of changed methods (empty if nothing comparable changed or parsing fails)
     */
    public static Set<String> changedMethods(String baseline, String edited, ClassPool classPool, String ownerClass) {
        if (baseline == null || edited == null) {
            return Collections.emptySet();
        }
        Map<String, String> baselineBodies = methodBodies(baseline, classPool, ownerClass);
        if (baselineBodies.isEmpty()) {
            return Collections.emptySet();
        }
        Map<String, String> editedBodies = methodBodies(edited, classPool, ownerClass);

        Set<String> changed = new HashSet<>();
        for (Map.Entry<String, String> entry : editedBodies.entrySet()) {
            String baselineBody = baselineBodies.get(entry.getKey());
            if (baselineBody != null && !baselineBody.equals(entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    /** Maps each concrete method of the source's primary type to a canonical rendering of its body. */
    private static Map<String, String> methodBodies(String source, ClassPool classPool, String ownerClass) {
        Map<String, String> bodies = new HashMap<>();
        CompilationUnit unit;
        try {
            unit = JavaParser.create().parse(source);
        } catch (RuntimeException parseFailure) {
            return bodies;
        }
        TypeDecl primary = unit.getPrimaryType();
        if (!(primary instanceof ClassDecl)) {
            return bodies;
        }
        ClassDecl classDecl = (ClassDecl) primary;
        TypeResolver resolver = null;
        if (classPool != null) {
            resolver = new TypeResolver(classPool, ownerClass);
            resolver.setImports(unit.getImports());
            resolver.setCurrentClassDecl(classDecl);
        }
        for (MethodDecl method : classDecl.getMethods()) {
            if (method.getBody() != null) {
                bodies.put(signature(method, resolver), ASTPrinter.format(method.getBody()));
            }
        }
        return bodies;
    }

    /**
     * The method's {@code name + descriptor} key, with reference types resolved through {@code resolver} (so it
     * matches the compiled class's signature). Falls back to the unresolved descriptor when no pool is given.
     */
    private static String signature(MethodDecl method, TypeResolver resolver) {
        StringBuilder descriptor = new StringBuilder("(");
        for (ParameterDecl parameter : method.getParameters()) {
            descriptor.append(resolver != null
                    ? resolver.descriptorOf(parameter.getType())
                    : parameter.getType().toIRType().getDescriptor());
        }
        descriptor.append(")").append(resolver != null
                ? resolver.descriptorOf(method.getReturnType())
                : method.getReturnType().toIRType().getDescriptor());
        return method.getName() + descriptor;
    }
}
