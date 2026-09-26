package com.tonic.service.deadcode;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.analysis.xref.XrefType;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.LibraryOverrides;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.service.XrefQueryService;
import com.tonic.util.AccessFlags;

import java.util.*;
import java.util.function.Consumer;

/** Whole-project reachability analysis that finds dead classes, methods and fields; inheritance-aware but blind to reflection. */
public final class DeadCodeAnalyzer
{

    private final ProjectModel project;
    private final DeadCodeConfig config;
    private final ClassPool pool;
    private final Set<String> userClasses;
    private final Set<String> skip;
    private final LibraryOverrides libraryOverrides;
    private Consumer<String> progress = m ->
    {
    };

    /**
     * Creates an analyzer over a project.
     *
     * @param project the project to analyze
     * @param config the entry-point, keep and skip settings
     */
    public DeadCodeAnalyzer(ProjectModel project, DeadCodeConfig config)
    {
        this.project = project;
        this.config = config;
        this.pool = project.getClassPool();
        this.userClasses = project.getUserClassNames();
        this.skip = config.skipClasses();
        this.libraryOverrides = new LibraryOverrides(project);
    }

    /**
     * Sets the listener told of each analysis phase; it is called on the analysis thread.
     *
     * @param listener receives each phase's description, or null for none
     */
    public void setProgressListener(Consumer<String> listener)
    {
        this.progress = listener != null ? listener : m ->
        {
        };
    }

    /**
     * Runs the analysis; builds the call graph and xref database, so call it off the EDT.
     *
     * @return the dead classes, methods and fields found
     */
    public DeadCodeReport analyze()
    {
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
        for (ClassEntryModel entry : project.getUserClasses())
        {
            String owner = entry.getClassName();
            if (owner == null || skip.contains(owner))
            {
                continue;
            }
            if (!liveClasses.contains(owner))
            {
                report.getDeadClasses().add(DeadItem.ofClass(owner));
                continue;
            }
            ClassFile cf = entry.getClassFile();
            for (MethodEntry m : cf.getMethods())
            {
                MethodReference ref = new MethodReference(owner, m.getName(), m.getDesc());
                if (!reachable.contains(ref))
                {
                    report.getDeadMethods().add(DeadItem.ofMethod(owner, m.getName(), m.getDesc()));
                }
            }
            for (FieldEntry f : cf.getFields())
            {
                classifyField(xref, reachable, report, owner, f);
            }
        }
        return report;
    }

    private Set<MethodReference> collectRoots()
    {
        Set<MethodReference> roots = new HashSet<>();
        for (ClassEntryModel entry : project.getUserClasses())
        {
            String owner = entry.getClassName();
            if (owner == null || skip.contains(owner))
            {
                continue;
            }
            for (MethodEntry m : entry.getClassFile().getMethods())
            {
                String name = m.getName();
                String desc = m.getDesc();
                int access = m.getAccess();
                boolean root = name.equals("<clinit>")
                        || (name.equals("main") && desc.equals("([Ljava/lang/String;)V") && AccessFlags.isStatic(access))
                        || (config.isPublicAsEntryPoints() && AccessFlags.isPublic(access))
                        || config.keeps(owner, name, desc)
                        || libraryOverrides.overridesLibrary(owner, name, desc);
                if (root)
                {
                    roots.add(new MethodReference(owner, name, desc));
                }
            }
        }
        return roots;
    }

    private void classifyField(XrefDatabase xref, Set<MethodReference> reachable, DeadCodeReport report, String owner, FieldEntry f)
    {
        String name = f.getName();
        String desc = f.getDesc();
        if (config.keeps(owner, name, desc) || isInlinableConstant(f))
        {
            return;
        }
        boolean reachableRead = false;
        List<MethodReference> writers = new ArrayList<>();
        for (Xref ref : xref.getRefsToField(owner, name, desc))
        {
            MethodReference source = sourceRef(ref);
            if (source == null || !reachable.contains(source))
            {
                continue;
            }
            if (ref.getType() == XrefType.FIELD_READ)
            {
                reachableRead = true;
                break;
            }
            if (ref.getType() == XrefType.FIELD_WRITE && !writers.contains(source))
            {
                writers.add(source);
            }
        }
        if (reachableRead)
        {
            return;
        }
        report.getDeadFields().add(DeadItem.ofField(owner, name, desc, !writers.isEmpty(), writers));
    }

    private Set<String> computeLiveClasses(CallGraph callGraph, XrefDatabase xref, Set<MethodReference> reachable)
    {
        Set<String> live = new HashSet<>();
        for (MethodReference ref : reachable)
        {
            if (userClasses.contains(ref.getOwner()))
            {
                live.add(ref.getOwner());
            }
        }
        for (ClassEntryModel entry : project.getUserClasses())
        {
            String owner = entry.getClassName();
            if (owner == null || skip.contains(owner) || live.contains(owner))
            {
                continue;
            }
            for (Xref ref : xref.getRefsToClass(owner))
            {
                XrefType type = ref.getType();
                if (type.isTypeRef() && !type.isInheritanceRef())
                {
                    MethodReference source = sourceRef(ref);
                    if (source != null && reachable.contains(source))
                    {
                        live.add(owner);
                        break;
                    }
                }
            }
        }
        Deque<String> worklist = new ArrayDeque<>(live);
        ClassHierarchy hierarchy = callGraph.getHierarchy();
        while (!worklist.isEmpty())
        {
            ClassNode node = hierarchy.getNode(worklist.poll());
            if (node == null)
            {
                continue;
            }
            for (ClassNode ancestor : node.getAllAncestors())
            {
                String an = ancestor.getName();
                if (userClasses.contains(an) && !skip.contains(an) && live.add(an))
                {
                    worklist.add(an);
                }
            }
        }
        return live;
    }

    private MethodReference sourceRef(Xref ref)
    {
        if (ref.getSourceMethod() == null)
        {
            return null;
        }
        return new MethodReference(ref.getSourceClass(), ref.getSourceMethod(), ref.getSourceMethodDesc());
    }

    private static boolean isInlinableConstant(FieldEntry f)
    {
        int access = f.getAccess();
        if (!AccessFlags.isStatic(access) || !AccessFlags.isFinal(access))
        {
            return false;
        }
        String desc = f.getDesc();
        return desc.length() == 1 || desc.equals("Ljava/lang/String;");
    }
}
