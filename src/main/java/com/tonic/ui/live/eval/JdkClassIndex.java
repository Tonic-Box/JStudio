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

/**
 * Index of common JDK classes (simple name -> fully-qualified name) built once by scanning the running
 * runtime's {@code jrt:} image (the {@code java.base} module), restricted to a curated set of everyday
 * packages. Used to offer JDK types in scratch-pad completion and to auto-import them when referenced by
 * simple name. Scoping to {@code java.base} keeps the scan fast and avoids cross-module simple-name clashes
 * (e.g. {@code java.awt.List}), so the common names resolve unambiguously.
 *
 * <p>If the {@code jrt} image is unavailable (non-modular runtime), the index is empty and the feature simply
 * degrades to project-only completion.
 */
public final class JdkClassIndex {

    private static final Set<String> PACKAGES = Set.of(
            "java.lang", "java.lang.reflect",
            "java.util", "java.util.stream", "java.util.function", "java.util.regex",
            "java.util.concurrent", "java.util.concurrent.atomic", "java.util.concurrent.locks",
            "java.io", "java.nio", "java.nio.file", "java.nio.charset",
            "java.time", "java.time.format", "java.time.temporal",
            "java.math", "java.text", "java.net");

    private static volatile Map<String, List<String>> index;

    private JdkClassIndex() {
    }

    /** Simple class name -> matching fully-qualified names (usually one) across the curated packages. */
    public static Map<String, List<String>> simpleToFqn() {
        Map<String, List<String>> local = index;
        if (local == null) {
            synchronized (JdkClassIndex.class) {
                local = index;
                if (local == null) {
                    local = build();
                    index = local;
                }
            }
        }
        return local;
    }

    private static Map<String, List<String>> build() {
        Map<String, List<String>> result = new HashMap<>();
        try {
            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path base = fs.getPath("/modules/java.base");
            try (Stream<Path> walk = Files.walk(base)) {
                walk.forEach(p -> {
                    String rel = base.relativize(p).toString().replace('\\', '/');
                    if (!rel.endsWith(".class")) {
                        return;
                    }
                    rel = rel.substring(0, rel.length() - ".class".length());
                    int slash = rel.lastIndexOf('/');
                    if (slash < 0) {
                        return;
                    }
                    String simple = rel.substring(slash + 1);
                    if (simple.indexOf('$') >= 0 || "package-info".equals(simple) || "module-info".equals(simple)) {
                        return;
                    }
                    String pkg = rel.substring(0, slash).replace('/', '.');
                    if (!PACKAGES.contains(pkg)) {
                        return;
                    }
                    result.computeIfAbsent(simple, k -> new ArrayList<>()).add(pkg + "." + simple);
                });
            }
        } catch (Exception | LinkageError ignored) {
        }
        return result;
    }
}
