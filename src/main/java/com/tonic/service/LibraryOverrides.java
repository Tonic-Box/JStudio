package com.tonic.service;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Answers whether a project method overrides or implements a method declared by a library or JDK type, resolving library types by reflection. */
public final class LibraryOverrides
{

    private final ProjectModel project;
    private final Set<String> userClasses;
    private final Map<String, Signatures> cache = new HashMap<>();

    /**
     * Creates the lookup over a project's user classes.
     *
     * @param project the project whose user classes are walked through their bytecode
     */
    public LibraryOverrides(ProjectModel project)
    {
        this.project = project;
        this.userClasses = project.getUserClassNames();
    }

    /**
     * Tells whether a method overrides or implements one declared outside the project; constructors and static initializers never do.
     *
     * @param owner the declaring class's internal name, with slashes
     * @param name the method's name
     * @param descriptor the method's descriptor
     * @return true where a library supertype declares the method, or where a supertype cannot be resolved
     */
    public boolean overridesLibrary(String owner, String name, String descriptor)
    {
        if (name.equals("<init>") || name.equals("<clinit>"))
        {
            return false;
        }
        Signatures signatures = cache.computeIfAbsent(owner, this::collect);
        return signatures.unresolved || signatures.names.contains(name + ' ' + descriptor);
    }

    private Signatures collect(String owner)
    {
        Signatures signatures = new Signatures();
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        pushSupertypes(project.getClass(owner), stack);
        while (!stack.isEmpty())
        {
            String type = stack.pop();
            if (!visited.add(type))
            {
                continue;
            }
            if (userClasses.contains(type))
            {
                pushSupertypes(project.getClass(type), stack);
            }
            else if (!collectReflective(type, signatures.names))
            {
                signatures.unresolved = true;
            }
        }
        return signatures;
    }

    private static void pushSupertypes(ClassEntryModel entry, Deque<String> stack)
    {
        if (entry == null || entry.getClassFile() == null)
        {
            return;
        }
        ClassFile cf = entry.getClassFile();
        String superName = cf.getSuperClassName();
        if (superName != null && !superName.isEmpty())
        {
            stack.push(superName);
        }
        List<String> interfaces = cf.getInterfaceNames();
        if (interfaces != null)
        {
            for (String iface : interfaces)
            {
                if (iface != null && !iface.isEmpty())
                {
                    stack.push(iface);
                }
            }
        }
    }

    private boolean collectReflective(String internalName, Set<String> names)
    {
        try
        {
            Class<?> root = Class.forName(internalName.replace('/', '.'), false, getClass().getClassLoader());
            Set<Class<?>> visited = new HashSet<>();
            Deque<Class<?>> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty())
            {
                Class<?> type = queue.poll();
                if (!visited.add(type))
                {
                    continue;
                }
                for (Method method : type.getDeclaredMethods())
                {
                    names.add(method.getName() + ' ' + methodDescriptor(method));
                }
                if (type.getSuperclass() != null)
                {
                    queue.add(type.getSuperclass());
                }
                Collections.addAll(queue, type.getInterfaces());
            }
            return true;
        }
        catch (Throwable unresolvable)
        {
            return false;
        }
    }

    private static String methodDescriptor(Method method)
    {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes())
        {
            sb.append(typeDescriptor(parameter));
        }
        return sb.append(')').append(typeDescriptor(method.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> type)
    {
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type.isArray()) return "[" + typeDescriptor(type.getComponentType());
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static final class Signatures
    {
        private final Set<String> names = new HashSet<>();
        private boolean unresolved;
    }
}
