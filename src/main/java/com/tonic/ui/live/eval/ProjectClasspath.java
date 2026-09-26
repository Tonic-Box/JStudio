package com.tonic.ui.live.eval;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The snippet compiler's classpath backed by the open project's classes, excluding JDK platform packages so javac resolves those itself. */
public final class ProjectClasspath implements SnippetCompiler.Classpath
{

    private final ProjectModel project;
    private final Map<String, Set<String>> packageToBinaryNames;

    /**
     * Indexes the project's non-platform classes by package.
     *
     * @param project the project whose classes snippets compile against
     */
    public ProjectClasspath(ProjectModel project)
    {
        this.project = project;
        this.packageToBinaryNames = buildIndex(project);
    }

    private static Map<String, Set<String>> buildIndex(ProjectModel project)
    {
        Map<String, Set<String>> index = new HashMap<>();
        for (ClassEntryModel entry : project.getAllClasses())
        {
            String internal = entry.getClassName();
            if (internal == null || isPlatform(internal))
            {
                continue;
            }
            String binary = internal.replace('/', '.');
            int lastDot = binary.lastIndexOf('.');
            String pkg = lastDot >= 0 ? binary.substring(0, lastDot) : "";
            index.computeIfAbsent(pkg, k -> new HashSet<>()).add(binary);
        }
        return index;
    }

    @Override
    public Set<String> classesInPackage(String packageName)
    {
        return packageToBinaryNames.getOrDefault(packageName, Set.of());
    }

    @Override
    public Set<String> allClassNames()
    {
        Set<String> all = new HashSet<>();
        for (Set<String> names : packageToBinaryNames.values())
        {
            all.addAll(names);
        }
        for (List<String> fqns : JdkClassIndex.simpleToFqn().values())
        {
            all.addAll(fqns);
        }
        return all;
    }

    @Override
    public byte[] classBytes(String binaryName)
    {
        if (binaryName == null)
        {
            return null;
        }
        ClassEntryModel entry = project.getClass(binaryName.replace('.', '/'));
        if (entry == null)
        {
            return null;
        }
        try
        {
            return entry.getClassFile().write();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static boolean isPlatform(String internal)
    {
        return internal.startsWith("java/")
                || internal.startsWith("javax/")
                || internal.startsWith("jdk/")
                || internal.startsWith("sun/")
                || internal.startsWith("com/sun/");
    }
}
