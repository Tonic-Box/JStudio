package com.tonic.service.deadcode;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.analysis.xref.XrefType;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.service.XrefQueryService;
import com.tonic.util.AccessFlags;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * Whole-project reachability analysis that finds unused methods, fields, and entire classes (inheritance-aware,
 * reflection-unaware). A member is dead if it is unreachable from the entry-point roots: every {@code main} and
 * {@code <clinit>}, every method that overrides one declared outside the user code base (JDK/library - so
 * framework callbacks like {@code KeyListener.keyPressed} are never removed), the configured keep-list, and
 * optionally all {@code public} members.
 *
 * <p>Reuses YABR's {@link CallGraph} (virtual-dispatch-aware reachability) and {@link XrefDatabase} (field
 * read/write + type references), and {@code ClassPool.loadSystemClass}/{@code loadPlatformClass} to resolve
 * external supertypes for the override rule.
 */
public final class DeadCodeAnalyzer {

    private final ProjectModel project;
    private final DeadCodeConfig config;
    private final ClassPool pool;
    private final Set<String> userClasses;
    private final Set<String> skip;
    private final Map<String, ExternalInfo> externalSignatureCache = new HashMap<>();
    private Consumer<String> progress = m -> {
    };

    public DeadCodeAnalyzer(ProjectModel project, DeadCodeConfig config) {
        this.project = project;
        this.config = config;
        this.pool = project.getClassPool();
        this.userClasses = project.getUserClassNames();
        this.skip = config.skipClasses();
    }

    /** Sets a listener notified of each analysis phase (fired off the EDT; marshal to the EDT to display). */
    public void setProgressListener(Consumer<String> listener) {
        this.progress = listener != null ? listener : m -> {
        };
    }

    /** Runs the analysis. Builds the call graph + xref database, so call off the EDT. */
    public DeadCodeReport analyze() {
        progress.accept("Building call graph...");
        CallGraph callGraph = CallGraph.build(pool);
        progress.accept("Building cross-references...");
        XrefDatabase xref = XrefQueryService.ensureDatabase(project);

        progress.accept("Computing entry points...");
        Set<MethodReference> roots = collectRoots();
        progress.accept("Computing reachability...");
        Set<MethodReference> reachable = new HashSet<>(callGraph.getReachableFrom(roots));
        reachable.addAll(roots);

        progress.accept("Finding dead code...");
        Set<String> liveClasses = computeLiveClasses(callGraph, xref, reachable);

        DeadCodeReport report = new DeadCodeReport();
        for (ClassEntryModel entry : project.getUserClasses()) {
            String owner = entry.getClassName();
            if (owner == null || skip.contains(owner)) {
                continue;
            }
            if (!liveClasses.contains(owner)) {
                report.getDeadClasses().add(DeadItem.ofClass(owner));
                continue;
            }
            ClassFile cf = entry.getClassFile();
            for (MethodEntry m : cf.getMethods()) {
                MethodReference ref = new MethodReference(owner, m.getName(), m.getDesc());
                if (!reachable.contains(ref)) {
                    report.getDeadMethods().add(DeadItem.ofMethod(owner, m.getName(), m.getDesc()));
                }
            }
            for (FieldEntry f : cf.getFields()) {
                classifyField(xref, reachable, report, owner, f);
            }
        }
        return report;
    }

    private Set<MethodReference> collectRoots() {
        Set<MethodReference> roots = new HashSet<>();
        for (ClassEntryModel entry : project.getUserClasses()) {
            String owner = entry.getClassName();
            if (owner == null || skip.contains(owner)) {
                continue;
            }
            for (MethodEntry m : entry.getClassFile().getMethods()) {
                String name = m.getName();
                String desc = m.getDesc();
                int access = m.getAccess();
                boolean root = name.equals("<clinit>")
                        || (name.equals("main") && desc.equals("([Ljava/lang/String;)V") && AccessFlags.isStatic(access))
                        || (config.isPublicAsEntryPoints() && AccessFlags.isPublic(access))
                        || config.keeps(owner, name, desc)
                        || overridesExternal(owner, name, desc);
                if (root) {
                    roots.add(new MethodReference(owner, name, desc));
                }
            }
        }
        return roots;
    }

    private void classifyField(XrefDatabase xref, Set<MethodReference> reachable, DeadCodeReport report,
                               String owner, FieldEntry f) {
        String name = f.getName();
        String desc = f.getDesc();
        if (config.keeps(owner, name, desc) || isInlinableConstant(f)) {
            return;
        }
        boolean reachableRead = false;
        List<MethodReference> writers = new ArrayList<>();
        for (Xref ref : xref.getRefsToField(owner, name, desc)) {
            MethodReference source = sourceRef(ref);
            if (source == null || !reachable.contains(source)) {
                continue;
            }
            if (ref.getType() == XrefType.FIELD_READ) {
                reachableRead = true;
                break;
            }
            if (ref.getType() == XrefType.FIELD_WRITE && !writers.contains(source)) {
                writers.add(source);
            }
        }
        if (reachableRead) {
            return;
        }
        report.getDeadFields().add(DeadItem.ofField(owner, name, desc, !writers.isEmpty(), writers));
    }

    /** A class is live if it has a reachable method, is referenced as a type by reachable code, or is a (user) supertype of a live class. */
    private Set<String> computeLiveClasses(CallGraph callGraph, XrefDatabase xref, Set<MethodReference> reachable) {
        Set<String> live = new HashSet<>();
        for (MethodReference ref : reachable) {
            if (userClasses.contains(ref.getOwner())) {
                live.add(ref.getOwner());
            }
        }
        for (ClassEntryModel entry : project.getUserClasses()) {
            String owner = entry.getClassName();
            if (owner == null || skip.contains(owner) || live.contains(owner)) {
                continue;
            }
            for (Xref ref : xref.getRefsToClass(owner)) {
                XrefType type = ref.getType();
                if (type.isTypeRef() && !type.isInheritanceRef()) {
                    MethodReference source = sourceRef(ref);
                    if (source != null && reachable.contains(source)) {
                        live.add(owner);
                        break;
                    }
                }
            }
        }
        // A live class needs its (user) supertypes kept too, or the hierarchy breaks.
        Deque<String> worklist = new ArrayDeque<>(live);
        ClassHierarchy hierarchy = callGraph.getHierarchy();
        while (!worklist.isEmpty()) {
            ClassNode node = hierarchy.getNode(worklist.poll());
            if (node == null) {
                continue;
            }
            for (ClassNode ancestor : node.getAllAncestors()) {
                String an = ancestor.getName();
                if (userClasses.contains(an) && !skip.contains(an) && live.add(an)) {
                    worklist.add(an);
                }
            }
        }
        return live;
    }

    // ---- external-override rule ---------------------------------------------------------------------

    /** True if {@code name+desc} on {@code owner} overrides a method declared by a non-user (JDK/library) supertype. */
    private boolean overridesExternal(String owner, String name, String desc) {
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return false;
        }
        ExternalInfo info = externalSignatureCache.computeIfAbsent(owner, this::computeExternalSignatures);
        return info.unresolved || info.signatures.contains(name + ' ' + desc);
    }

    /**
     * Collects the name+desc of every method declared by an external (non-user) supertype of {@code owner}.
     * User supertypes are walked via their bytecode (to reach the external boundary higher up); external
     * supertypes are resolved by reflection, which reliably covers the JDK and anything on the classpath
     * regardless of whether the project's pool loaded JDK classes.
     */
    private ExternalInfo computeExternalSignatures(String owner) {
        Set<String> signatures = new HashSet<>();
        boolean[] unresolved = {false};
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        pushUserSupertypes(project.getClass(owner), stack);
        while (!stack.isEmpty()) {
            String c = stack.pop();
            if (!visited.add(c)) {
                continue;
            }
            if (userClasses.contains(c)) {
                pushUserSupertypes(project.getClass(c), stack);
            } else if (!collectReflective(c, signatures)) {
                unresolved[0] = true;
            }
        }
        return new ExternalInfo(signatures, unresolved[0]);
    }

    private static void pushUserSupertypes(ClassEntryModel entry, Deque<String> stack) {
        if (entry == null || entry.getClassFile() == null) {
            return;
        }
        ClassFile cf = entry.getClassFile();
        String superName = cf.getSuperClassName();
        if (superName != null && !superName.isEmpty()) {
            stack.push(superName);
        }
        List<String> interfaces = cf.getInterfaceNames();
        if (interfaces != null) {
            for (String iface : interfaces) {
                if (iface != null && !iface.isEmpty()) {
                    stack.push(iface);
                }
            }
        }
    }

    /** Adds every method (any access, full hierarchy) of an external class to {@code signatures} via reflection. */
    private boolean collectReflective(String internalName, Set<String> signatures) {
        try {
            Class<?> root = Class.forName(internalName.replace('/', '.'), false, getClass().getClassLoader());
            Set<Class<?>> visited = new HashSet<>();
            Deque<Class<?>> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                Class<?> k = queue.poll();
                if (!visited.add(k)) {
                    continue;
                }
                for (Method m : k.getDeclaredMethods()) {
                    signatures.add(m.getName() + ' ' + methodDescriptor(m));
                }
                if (k.getSuperclass() != null) {
                    queue.add(k.getSuperclass());
                }
                Collections.addAll(queue, k.getInterfaces());
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String methodDescriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(typeDescriptor(p));
        }
        return sb.append(')').append(typeDescriptor(m.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + typeDescriptor(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private MethodReference sourceRef(Xref ref) {
        if (ref.getSourceMethod() == null) {
            return null;
        }
        return new MethodReference(ref.getSourceClass(), ref.getSourceMethod(), ref.getSourceMethodDesc());
    }

    /** Compile-time constants (static final primitive/String) inline at use sites, so they look unreferenced; keep them. */
    private static boolean isInlinableConstant(FieldEntry f) {
        int access = f.getAccess();
        if (!AccessFlags.isStatic(access) || !AccessFlags.isFinal(access)) {
            return false;
        }
        String desc = f.getDesc();
        return desc.length() == 1 || desc.equals("Ljava/lang/String;");
    }

    private static final class ExternalInfo {
        private final Set<String> signatures;
        private final boolean unresolved;

        ExternalInfo(Set<String> signatures, boolean unresolved) {
            this.signatures = signatures;
            this.unresolved = unresolved;
        }
    }
}
