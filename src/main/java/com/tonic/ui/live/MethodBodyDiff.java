package com.tonic.ui.live;

import com.tonic.analysis.source.ast.ASTPrinter;
import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.ConstructorDecl;
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
import java.util.TreeSet;

/** Finds the methods whose bodies changed between two revisions of a class's source, keyed by name plus descriptor; reformatting alone is not a change. */
public final class MethodBodyDiff
{

    private MethodBodyDiff()
    {
    }

    /**
     * Returns the name plus descriptor keys of the primary type's methods present in both revisions whose bodies differ.
     *
     * @param baseline the source before the edit, such as the original decompilation
     * @param edited the source the user just compiled
     * @param classPool the project's class pool, used to resolve reference types in signatures; may be null
     * @param ownerClass the internal name of the class being patched
     * @return the keys of changed methods; empty if either source is null, nothing comparable changed, or parsing fails
     */
    public static Set<String> changedMethods(String baseline, String edited, ClassPool classPool, String ownerClass)
    {
        if (baseline == null || edited == null)
        {
            return Collections.emptySet();
        }
        Map<String, String> baselineBodies = methodBodies(baseline, classPool, ownerClass);
        if (baselineBodies.isEmpty())
        {
            return Collections.emptySet();
        }
        Map<String, String> editedBodies = methodBodies(edited, classPool, ownerClass);

        Set<String> changed = new HashSet<>();
        for (Map.Entry<String, String> entry : editedBodies.entrySet())
        {
            String baselineBody = baselineBodies.get(entry.getKey());
            if (baselineBody != null && !baselineBody.equals(entry.getValue()))
            {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    /**
     * Returns the name plus descriptor keys of the primary type's methods and constructors present in only one of the two revisions, which a live redefine cannot apply.
     *
     * @param baseline the source before the edit, such as the original decompilation
     * @param edited the source the user just compiled
     * @param classPool the project's class pool, used to resolve reference types in signatures; may be null
     * @param ownerClass the internal name of the class being patched
     * @return the keys of added and removed methods; empty if either source is null or fails to parse
     */
    public static Set<String> addedOrRemovedMethods(String baseline, String edited, ClassPool classPool, String ownerClass)
    {
        if (baseline == null || edited == null)
        {
            return Collections.emptySet();
        }
        Map<String, String> baselineBodies = methodBodies(baseline, classPool, ownerClass);
        Map<String, String> editedBodies = methodBodies(edited, classPool, ownerClass);
        if (baselineBodies.isEmpty() || editedBodies.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<String> changed = new TreeSet<>(baselineBodies.keySet());
        changed.addAll(editedBodies.keySet());
        Set<String> common = new HashSet<>(baselineBodies.keySet());
        common.retainAll(editedBodies.keySet());
        changed.removeAll(common);
        return changed;
    }

    private static Map<String, String> methodBodies(String source, ClassPool classPool, String ownerClass)
    {
        Map<String, String> bodies = new HashMap<>();
        CompilationUnit unit;
        try
        {
            unit = JavaParser.create().parse(source);
        }
        catch (RuntimeException parseFailure)
        {
            return bodies;
        }
        TypeDecl primary = unit.getPrimaryType();
        if (!(primary instanceof ClassDecl))
        {
            return bodies;
        }
        ClassDecl classDecl = (ClassDecl) primary;
        TypeResolver resolver = null;
        if (classPool != null)
        {
            resolver = new TypeResolver(classPool, ownerClass);
            resolver.setImports(unit.getImports());
            resolver.setCurrentClassDecl(classDecl);
        }
        for (MethodDecl method : classDecl.getMethods())
        {
            if (method.getBody() != null)
            {
                bodies.put(signature(method, resolver), ASTPrinter.format(method.getBody()));
            }
        }
        for (ConstructorDecl constructor : classDecl.getConstructors())
        {
            if (constructor.getBody() != null)
            {
                bodies.put(constructorSignature(constructor, resolver), ASTPrinter.format(constructor.getBody()));
            }
        }
        return bodies;
    }

    private static String signature(MethodDecl method, TypeResolver resolver)
    {
        StringBuilder descriptor = new StringBuilder("(");
        for (ParameterDecl parameter : method.getParameters())
        {
            descriptor.append(resolver != null ? resolver.descriptorOf(parameter.getType()) : parameter.getType().toIRType().getDescriptor());
        }
        descriptor.append(")").append(resolver != null ? resolver.descriptorOf(method.getReturnType()) : method.getReturnType().toIRType().getDescriptor());
        return method.getName() + descriptor;
    }

    private static String constructorSignature(ConstructorDecl constructor, TypeResolver resolver)
    {
        StringBuilder descriptor = new StringBuilder("(");
        for (ParameterDecl parameter : constructor.getParameters())
        {
            descriptor.append(resolver != null ? resolver.descriptorOf(parameter.getType()) : parameter.getType().toIRType().getDescriptor());
        }
        descriptor.append(")V");
        return "<init>" + descriptor;
    }
}
