package com.tonic.ui.live;

import com.tonic.analysis.MethodGrafter;
import com.tonic.live.LiveSession;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/** Builds the bytecode for a live patch, which HotSpot accepts only when the member set matches the running class. */
public final class LivePatch
{

    private LivePatch()
    {
    }

    /**
     * Fetches the running class and grafts the edited method bodies onto it; call off the EDT.
     *
     * @param session the attached session, used to fetch the running class bytes
     * @param internalName the class's internal name, with slashes
     * @param edited the recompiled class, source of the new method bodies
     * @param changedMethods name plus descriptor keys of the methods to graft
     * @return the running class bytes with the edited bodies spliced in
     * @throws IllegalStateException if a key is missing from the running or the edited class
     * @throws Exception if fetching, parsing, grafting or writing the class fails
     */
    public static byte[] buildGraftedRedefineBytes(LiveSession session, String internalName, ClassFile edited, Set<String> changedMethods) throws Exception
    {
        return graftOnto(session.fetchClassBytes(internalName), edited, changedMethods);
    }

    /**
     * Grafts the edited method bodies onto a running class's bytes.
     *
     * @param runningBytes the running class's bytes
     * @param edited the recompiled class, source of the new method bodies
     * @param changedMethods name plus descriptor keys of the methods to graft
     * @return the running class bytes with the edited bodies spliced in
     * @throws IllegalStateException if a key is missing from the running or the edited class, naming each such key
     * @throws Exception if parsing, grafting or writing the class fails
     */
    public static byte[] graftOnto(byte[] runningBytes, ClassFile edited, Set<String> changedMethods) throws Exception
    {
        ClassFile running = new ClassFile(new ByteArrayInputStream(runningBytes));

        Set<String> missing = new LinkedHashSet<>();
        for (String key : changedMethods)
        {
            if (findMethod(edited, key) == null || findMethod(running, key) == null)
            {
                missing.add(key);
            }
        }
        if (!missing.isEmpty())
        {
            throw new IllegalStateException("live redefine can only change the bodies of methods the running class already has; these changed methods are missing from the running or the recompiled class: " + missing);
        }
        for (String key : changedMethods)
        {
            MethodGrafter.replaceMethodBody(edited, findMethod(edited, key), running, findMethod(running, key));
        }
        running.rebuild();
        return running.write();
    }

    /**
     * Fetches the running class and returns the whole edited class's bytes if its member set matches; call off the EDT.
     *
     * @param session the attached session, used to fetch the running class bytes
     * @param internalName the class's internal name, with slashes
     * @param edited the edited class
     * @return the edited class's bytes
     * @throws IllegalStateException if the edit adds or removes a method or field, with a message listing them
     * @throws Exception if fetching, parsing or writing the class fails
     */
    public static byte[] buildRedefineBytes(LiveSession session, String internalName, ClassFile edited) throws Exception
    {
        return validateAgainst(session.fetchClassBytes(internalName), edited);
    }

    /**
     * Returns the edited class's bytes if its methods and fields match the running class's.
     *
     * @param runningBytes the running class's bytes
     * @param edited the edited class
     * @return the edited class's bytes
     * @throws IllegalStateException if the edit adds or removes a method or field, with a message listing them
     * @throws Exception if parsing or writing a class fails
     */
    public static byte[] validateAgainst(byte[] runningBytes, ClassFile edited) throws Exception
    {
        ClassFile running = new ClassFile(new ByteArrayInputStream(runningBytes));

        Set<String> runningMethods = methodKeys(running);
        Set<String> editedMethods = methodKeys(edited);
        Set<String> runningFields = fieldKeys(running);
        Set<String> editedFields = fieldKeys(edited);
        if (!runningMethods.equals(editedMethods) || !runningFields.equals(editedFields))
        {
            throw new IllegalStateException(describeMismatch(runningMethods, editedMethods, runningFields, editedFields));
        }
        return edited.write();
    }

    private static MethodEntry findMethod(ClassFile classFile, String key)
    {
        for (MethodEntry method : classFile.getMethods())
        {
            if ((method.getName() + method.getDesc()).equals(key))
            {
                return method;
            }
        }
        return null;
    }

    private static Set<String> methodKeys(ClassFile classFile)
    {
        Set<String> keys = new LinkedHashSet<>();
        for (MethodEntry method : classFile.getMethods())
        {
            keys.add(method.getName() + method.getDesc());
        }
        return keys;
    }

    private static Set<String> fieldKeys(ClassFile classFile)
    {
        Set<String> keys = new LinkedHashSet<>();
        for (FieldEntry field : classFile.getFields())
        {
            keys.add(field.getName() + " " + field.getDesc());
        }
        return keys;
    }

    private static String describeMismatch(Set<String> runningMethods, Set<String> editedMethods, Set<String> runningFields, Set<String> editedFields)
    {
        StringBuilder sb = new StringBuilder("the recompiled class has a different member set than the running class, which live redefine " + "cannot apply (it allows method-body changes only - not adding/removing members). This is " + "usually a decompile/recompile changing synthetic members; editing at the bytecode level " + "avoids it.");
        appendDiff(sb, "Added methods", minus(editedMethods, runningMethods));
        appendDiff(sb, "Removed methods", minus(runningMethods, editedMethods));
        appendDiff(sb, "Added fields", minus(editedFields, runningFields));
        appendDiff(sb, "Removed fields", minus(runningFields, editedFields));
        return sb.toString();
    }

    private static void appendDiff(StringBuilder sb, String label, Set<String> values)
    {
        if (!values.isEmpty())
        {
            sb.append(' ').append(label).append(": ").append(values).append('.');
        }
    }

    private static Set<String> minus(Set<String> a, Set<String> b)
    {
        Set<String> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }
}
