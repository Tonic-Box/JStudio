package com.tonic.ui.live.eval;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** A lazily built index from simple name to fully qualified names of everyday java.base classes; empty on a runtime without a jrt image. */
public final class JdkClassIndex
{

    private static final Set<String> PACKAGES = Set.of("java.lang", "java.lang.reflect", "java.util", "java.util.stream", "java.util.function", "java.util.regex", "java.util.concurrent", "java.util.concurrent.atomic", "java.util.concurrent.locks", "java.io", "java.nio", "java.nio.file", "java.nio.charset", "java.time", "java.time.format", "java.time.temporal", "java.math", "java.text", "java.net");

    private static volatile Map<String, List<String>> index;

    private JdkClassIndex()
    {
    }

    /**
     * Returns the index, building it on first use.
     *
     * @return a map from simple class name to its fully qualified names, usually one; empty if the jrt image is unavailable
     */
    public static Map<String, List<String>> simpleToFqn()
    {
        Map<String, List<String>> local = index;
        if (local == null)
        {
            synchronized (JdkClassIndex.class)
            {
                local = index;
                if (local == null)
                {
                    local = build();
                    index = local;
                }
            }
        }
        return local;
    }

    private static Map<String, List<String>> build()
    {
        Map<String, List<String>> result = new HashMap<>();
        try
        {
            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path base = fs.getPath("/modules/java.base");
            try (Stream<Path> walk = Files.walk(base))
            {
                walk.forEach(p ->
                {
                    String rel = base.relativize(p).toString().replace('\\', '/');
                    if (!rel.endsWith(".class"))
                    {
                        return;
                    }
                    rel = rel.substring(0, rel.length() - ".class".length());
                    int slash = rel.lastIndexOf('/');
                    if (slash < 0)
                    {
                        return;
                    }
                    String simple = rel.substring(slash + 1);
                    if (simple.indexOf('$') >= 0 || "package-info".equals(simple) || "module-info".equals(simple))
                    {
                        return;
                    }
                    String pkg = rel.substring(0, slash).replace('/', '.');
                    if (!PACKAGES.contains(pkg))
                    {
                        return;
                    }
                    result.computeIfAbsent(simple, k -> new ArrayList<>()).add(pkg + "." + simple);
                });
            }
        }
        catch (Exception | LinkageError ignored)
        {
        }
        return result;
    }
}
