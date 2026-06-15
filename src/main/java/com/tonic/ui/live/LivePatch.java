package com.tonic.ui.live;

import com.tonic.analysis.MethodGrafter;
import com.tonic.live.LiveSession;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Prepares (and pre-validates) the bytecode for a live patch. HotSpot's {@code redefineClasses} accepts
 * method-<i>body</i> changes only: as long as the class handed to it has the same methods and fields as the
 * running class, redefine succeeds - even for classes using lambdas/invokedynamic. What it cannot do is
 * <b>add or remove</b> methods or fields.
 *
 * <p>Two strategies are offered:
 * <ul>
 *   <li>{@link #buildGraftedRedefineBytes} - for the source-recompile path. Only the methods the user actually
 *       edited are grafted onto the <i>running</i> class, so every untouched method and synthetic member keeps
 *       its exact running bytes. This is robust against a decompile/recompile round-trip perturbing synthetic
 *       members, which would otherwise drift the member set and get the whole class rejected.</li>
 *   <li>{@link #buildRedefineBytes} - for the bytecode-editor path, where the edited class has no decompile
 *       round-trip and so already shares the running member set; it is sent whole after a member-set check.</li>
 * </ul>
 */
public final class LivePatch {

    private LivePatch() {
    }

    /**
     * Builds redefine bytes by grafting only the edited method bodies onto the running class. Performs network
     * I/O (fetches the running class) - call off the EDT. Throws with an actionable message if the edit adds a
     * member (which live redefine cannot apply).
     *
     * @param session        the attached session, used to fetch the running class bytes
     * @param internalName   the class's internal name
     * @param edited         the recompiled class, source of the new method bodies
     * @param changedMethods {@code name + descriptor} keys of the methods to graft (see {@link MethodBodyDiff})
     * @return the running class bytes with the edited bodies spliced in
     */
    public static byte[] buildGraftedRedefineBytes(LiveSession session, String internalName, ClassFile edited,
                                                   Set<String> changedMethods) throws Exception {
        ClassFile running = new ClassFile(new ByteArrayInputStream(session.fetchClassBytes(internalName)));

        // Only the changed methods are spliced in, and only when they resolve in BOTH classes by exact
        // signature. The result therefore always has the running class's member set, so no member-set check is
        // needed - and crucially must not be done against the recompiled class, whose member set can carry
        // spurious "new" methods when the decompiler/recompiler mis-resolves a method's descriptor.
        for (String key : changedMethods) {
            MethodEntry source = findMethod(edited, key);
            MethodEntry target = findMethod(running, key);
            if (source != null && target != null) {
                MethodGrafter.replaceMethodBody(edited, source, running, target);
            }
        }
        running.rebuild();
        return running.write();
    }

    /**
     * Validates the edit against the running class and returns the whole edited class's bytes. Performs network
     * I/O (fetches the running class) - call off the EDT. Throws with an actionable message if the edit changed
     * the class's member set (which live redefine cannot apply).
     */
    public static byte[] buildRedefineBytes(LiveSession session, String internalName, ClassFile edited) throws Exception {
        return validateAgainst(session.fetchClassBytes(internalName), edited);
    }

    /**
     * Checks {@code edited}'s member set against the running class bytes and returns {@code edited}'s bytes if
     * they match. Separated from the network fetch so it can be exercised directly.
     */
    public static byte[] validateAgainst(byte[] runningBytes, ClassFile edited) throws Exception {
        ClassFile running = new ClassFile(new ByteArrayInputStream(runningBytes));

        Set<String> runningMethods = methodKeys(running);
        Set<String> editedMethods = methodKeys(edited);
        Set<String> runningFields = fieldKeys(running);
        Set<String> editedFields = fieldKeys(edited);
        if (!runningMethods.equals(editedMethods) || !runningFields.equals(editedFields)) {
            throw new IllegalStateException(describeMismatch(runningMethods, editedMethods, runningFields, editedFields));
        }
        return edited.write();
    }

    private static MethodEntry findMethod(ClassFile classFile, String key) {
        for (MethodEntry method : classFile.getMethods()) {
            if ((method.getName() + method.getDesc()).equals(key)) {
                return method;
            }
        }
        return null;
    }

    private static Set<String> methodKeys(ClassFile classFile) {
        Set<String> keys = new LinkedHashSet<>();
        for (MethodEntry method : classFile.getMethods()) {
            keys.add(method.getName() + method.getDesc());
        }
        return keys;
    }

    private static Set<String> fieldKeys(ClassFile classFile) {
        Set<String> keys = new LinkedHashSet<>();
        for (FieldEntry field : classFile.getFields()) {
            keys.add(field.getName() + " " + field.getDesc());
        }
        return keys;
    }

    private static String describeMismatch(Set<String> runningMethods, Set<String> editedMethods,
                                           Set<String> runningFields, Set<String> editedFields) {
        StringBuilder sb = new StringBuilder(
                "the recompiled class has a different member set than the running class, which live redefine "
                        + "cannot apply (it allows method-body changes only - not adding/removing members). This is "
                        + "usually a decompile/recompile changing synthetic members; editing at the bytecode level "
                        + "avoids it.");
        appendDiff(sb, "Added methods", minus(editedMethods, runningMethods));
        appendDiff(sb, "Removed methods", minus(runningMethods, editedMethods));
        appendDiff(sb, "Added fields", minus(editedFields, runningFields));
        appendDiff(sb, "Removed fields", minus(runningFields, editedFields));
        return sb.toString();
    }

    private static void appendDiff(StringBuilder sb, String label, Set<String> values) {
        if (!values.isEmpty()) {
            sb.append(' ').append(label).append(": ").append(values).append('.');
        }
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }
}
