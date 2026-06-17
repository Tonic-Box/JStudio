package com.tonic.service.run;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers installed JDKs/JREs to offer in the Run dialog (IntelliJ-style): the JVM running JStudio first,
 * then {@code JAVA_HOME} and JDKs found under common install directories (Adoptium/Corretto/Zulu/..., plus
 * {@code ~/.jdks} and {@code ~/.sdkman}). Users can also browse to any JDK home.
 */
public final class JdkDetector {

    private JdkDetector() {
    }

    /** All detected JDKs, the running JVM first; de-duplicated by canonical path. */
    public static List<Jdk> detect() {
        Map<String, Jdk> byPath = new LinkedHashMap<>();
        File running = new File(System.getProperty("java.home"));
        add(byPath, running, "java " + Runtime.version().feature() + " (running)", Runtime.version().feature());

        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null && !javaHomeEnv.isEmpty()) {
            addHome(byPath, new File(javaHomeEnv));
        }
        for (File parent : candidateParents()) {
            File[] children = parent.listFiles(File::isDirectory);
            if (children == null) {
                continue;
            }
            for (File child : children) {
                addHome(byPath, child);
            }
        }
        return new ArrayList<>(byPath.values());
    }

    /** Builds a {@link Jdk} for a home directory you browsed to, or null if it isn't a JDK/JRE home. */
    public static Jdk fromHome(File home) {
        File resolved = normalizeHome(home);
        if (!hasJava(resolved)) {
            return null;
        }
        int feature = featureOf(resolved);
        return new Jdk(resolved, label(resolved, feature), feature);
    }

    private static void addHome(Map<String, Jdk> map, File dir) {
        File home = normalizeHome(dir);
        if (hasJava(home)) {
            add(map, home, label(home, featureOf(home)), featureOf(home));
        }
    }

    private static void add(Map<String, Jdk> map, File home, String label, int feature) {
        String key;
        try {
            key = home.getCanonicalPath();
        } catch (IOException e) {
            key = home.getAbsolutePath();
        }
        map.putIfAbsent(key, new Jdk(home, label, feature));
    }

    private static String label(File home, int feature) {
        return home.getName() + (feature > 0 ? "  (java " + feature + ")" : "");
    }

    /** macOS JDK bundles keep the home under {@code Contents/Home}. */
    private static File normalizeHome(File dir) {
        File macHome = new File(dir, "Contents/Home");
        return macHome.isDirectory() ? macHome : dir;
    }

    static boolean hasJava(File home) {
        return new File(home, "bin/java").isFile() || new File(home, "bin/java.exe").isFile();
    }

    static int featureOf(File home) {
        File release = new File(home, "release");
        if (release.isFile()) {
            try {
                for (String line : Files.readAllLines(release.toPath())) {
                    if (line.startsWith("JAVA_VERSION")) {
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            return featureFromVersion(line.substring(eq + 1));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return 0;
    }

    /** Maps a version string ("1.8.0_352", "11.0.19", "17.0.5") to its feature version (8, 11, 17). */
    static int featureFromVersion(String version) {
        if (version == null) {
            return 0;
        }
        String[] parts = version.replace("\"", "").trim().split("[._-]");
        try {
            int first = Integer.parseInt(parts[0]);
            if (first == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return first;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<File> candidateParents() {
        List<File> dirs = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        File userHome = new File(System.getProperty("user.home"));
        if (os.contains("win")) {
            for (String programFiles : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"),
                    "C:\\Program Files", "C:\\Program Files (x86)"}) {
                if (programFiles == null) {
                    continue;
                }
                for (String vendor : new String[]{"Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft",
                        "Amazon Corretto", "Zulu", "BellSoft", "AdoptOpenJDK", "Semeru"}) {
                    dirs.add(new File(programFiles, vendor));
                }
            }
            dirs.add(new File(userHome, ".jdks"));
        } else if (os.contains("mac")) {
            dirs.add(new File("/Library/Java/JavaVirtualMachines"));
            dirs.add(new File(userHome, "Library/Java/JavaVirtualMachines"));
            dirs.add(new File(userHome, ".jdks"));
            dirs.add(new File(userHome, ".sdkman/candidates/java"));
        } else {
            dirs.add(new File("/usr/lib/jvm"));
            dirs.add(new File("/usr/java"));
            dirs.add(new File(userHome, ".jdks"));
            dirs.add(new File(userHome, ".sdkman/candidates/java"));
        }
        return dirs;
    }
}
