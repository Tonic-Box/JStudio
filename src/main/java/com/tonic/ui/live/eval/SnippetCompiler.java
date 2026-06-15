package com.tonic.ui.live.eval;

import lombok.Getter;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles a Java snippet to bytecode with the JDK's {@code javac}, resolving types against the attached JVM's
 * classes via an in-memory file manager (no temp files; class bytes are fetched lazily, only for what javac
 * actually reads). The snippet is wrapped as {@code static Object run()} of a fresh class in the <b>default
 * package</b> (so default-package application classes are directly referenceable); named-package classes used
 * by simple name are auto-imported. The result carries every compiled class (the wrapper plus any
 * anonymous/local classes) so the agent can define them all together.
 *
 * <p>UI-free and pool-agnostic: the application classpath is supplied via {@link Classpath}.
 */
public final class SnippetCompiler {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** Supplies application class bytes (the attached JVM's pulled classes) to the compiler. */
    public interface Classpath {
        /** Binary names of all classes directly in {@code packageName} (dot-separated; {@code ""} = default). */
        Set<String> classesInPackage(String packageName);

        /** Raw class bytes for a binary name, or null if unknown. Called lazily, only for read classes. */
        byte[] classBytes(String binaryName);

        /** All known class binary names (dot-separated). Used to auto-import classes referenced by simple name. */
        Set<String> allClassNames();
    }

    /** Outcome of a compile: the classes to ship (on success) and any compiler messages (snippet-relative). */
    @Getter
    public static final class Result {
        private final boolean success;
        /**
         * -- GETTER --
         * Compiled classes by binary name (wrapper + nested); empty on failure.
         */
        private final Map<String, byte[]> classes;
        /**
         * -- GETTER --
         * Binary name of the wrapper class whose
         *  is invoked.
         */
        private final String mainBinaryName;
        /**
         * -- GETTER --
         * Formatted compiler diagnostics (errors/warnings), with body line numbers relative to the snippet.
         */
        private final List<String> messages;

        private Result(boolean success, Map<String, byte[]> classes, String mainBinaryName, List<String> messages) {
            this.success = success;
            this.classes = classes;
            this.mainBinaryName = mainBinaryName;
            this.messages = messages;
        }

    }

    private final Classpath classpath;
    private int counter;

    public SnippetCompiler(Classpath classpath) {
        this.classpath = classpath;
    }

    /** Compiles {@code snippet} (a sequence of statements, optionally preceded by {@code import} lines). */
    public synchronized Result compile(String snippet) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            return new Result(false, Map.of(), null,
                    List.of("No system Java compiler is available (JStudio must run on a JDK, not a JRE)."));
        }

        String binaryName = "Scratch_" + (++counter);
        Wrapped wrapped = wrap(snippet, binaryName);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        Map<String, byte[]> outputs = new LinkedHashMap<>();
        try (PoolFileManager fileManager = new PoolFileManager(standard, classpath, outputs)) {
            JavaFileObject source = new SourceObject(binaryName, wrapped.source);
            boolean ok = javac.getTask(null, fileManager, diagnostics,
                    List.of("-proc:none"), null, List.of(source)).call();
            List<String> messages = formatDiagnostics(diagnostics, wrapped.preambleLines);
            if (!ok || !outputs.containsKey(binaryName)) {
                return new Result(false, Map.of(), null, messages);
            }
            return new Result(true, outputs, binaryName, messages);
        } catch (IOException e) {
            return new Result(false, Map.of(), null, List.of("Compile failed: " + e.getMessage()));
        }
    }

    /** Splits leading {@code import} lines from the body, auto-imports referenced classes, and assembles the source. */
    private Wrapped wrap(String snippet, String simpleName) {
        String[] lines = snippet.split("\n", -1);
        List<String> imports = new ArrayList<>();
        int firstBody = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                firstBody = i + 1;
                continue;
            }
            if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                imports.add(trimmed);
                firstBody = i + 1;
                continue;
            }
            break;
        }
        String body = String.join("\n", Arrays.asList(lines).subList(Math.min(firstBody, lines.length), lines.length));
        List<String> autoImports = autoImports(body, imports);

        StringBuilder w = new StringBuilder();
        for (String imp : imports) {
            w.append(imp).append('\n');
        }
        for (String imp : autoImports) {
            w.append(imp).append('\n');
        }
        w.append("public final class ").append(simpleName).append(" {\n");
        w.append("    public static Object run() throws Throwable { if (true) {\n");
        int preambleLines = 2 + imports.size() + autoImports.size();
        w.append(body);
        if (!body.endsWith("\n")) {
            w.append('\n');
        }
        w.append("    } return Boolean.TRUE; }\n}\n");
        return new Wrapped(w.toString(), preambleLines);
    }

    /**
     * Derives {@code import} statements for project classes referenced by simple name in {@code body}: a token
     * matching exactly one named-package class (not already imported by the user) is imported. Default-package
     * classes need no import (the wrapper shares the default package); ambiguous simple names are left alone.
     * Unused imports that result from false-positive token matches are harmless (javac does not error on them).
     */
    private List<String> autoImports(String body, List<String> userImports) {
        Map<String, List<String>> simpleToBinaries = simpleNameIndex();
        Set<String> userImported = new HashSet<>();
        for (String imp : userImports) {
            String spec = imp.replace("import", "").replace("static", "").replace(";", "").trim();
            int dot = spec.lastIndexOf('.');
            userImported.add(dot >= 0 ? spec.substring(dot + 1) : spec);
        }

        List<String> result = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        Matcher m = IDENTIFIER.matcher(body);
        while (m.find()) {
            String token = m.group();
            if (!handled.add(token) || userImported.contains(token)) {
                continue;
            }
            List<String> binaries = simpleToBinaries.get(token);
            if (binaries == null || binaries.size() != 1) {
                continue;
            }
            String binary = binaries.get(0);
            int dot = binary.lastIndexOf('.');
            if (dot < 0 || binary.indexOf('$') >= 0 || "java.lang".equals(binary.substring(0, dot))) {
                continue;
            }
            result.add("import " + binary + ";");
        }
        return result;
    }

    /** Index of simple class name -> matching binary names, across all project classes. */
    private Map<String, List<String>> simpleNameIndex() {
        Map<String, List<String>> index = new HashMap<>();
        for (String binary : classpath.allClassNames()) {
            int dot = binary.lastIndexOf('.');
            String simple = dot >= 0 ? binary.substring(dot + 1) : binary;
            index.computeIfAbsent(simple, k -> new ArrayList<>()).add(binary);
        }
        return index;
    }

    private static List<String> formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics, int preambleLines) {
        List<String> messages = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            String text = d.getMessage(Locale.ROOT);
            long line = d.getLineNumber();
            String prefix = d.getKind() + ": ";
            if (line >= 1 && line > preambleLines) {
                prefix += "line " + (line - preambleLines) + ": ";
            }
            messages.add(prefix + text);
        }
        return messages;
    }

    private static final class Wrapped {
        final String source;
        final int preambleLines;

        Wrapped(String source, int preambleLines) {
            this.source = source;
            this.preambleLines = preambleLines;
        }
    }

    /** Serves application classes from {@link Classpath} on the compiler classpath; captures compiled output. */
    private static final class PoolFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Classpath classpath;
        private final Map<String, byte[]> outputs;

        PoolFileManager(StandardJavaFileManager delegate, Classpath classpath, Map<String, byte[]> outputs) {
            super(delegate);
            this.classpath = classpath;
            this.outputs = outputs;
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName,
                                             Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
                List<JavaFileObject> result = new ArrayList<>();
                for (String binaryName : classpath.classesInPackage(packageName)) {
                    result.add(new InputClass(binaryName, classpath));
                }
                return result;
            }
            return super.list(location, packageName, kinds, recurse);
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            if (file instanceof InputClass) {
                return ((InputClass) file).binaryName;
            }
            return super.inferBinaryName(location, file);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            return new OutputClass(className, outputs);
        }
    }

    /** A classpath class file backed by lazily-fetched bytes. */
    private static final class InputClass extends SimpleJavaFileObject {
        private final String binaryName;
        private final Classpath classpath;

        InputClass(String binaryName, Classpath classpath) {
            super(URI.create("pool:///" + binaryName.replace('.', '/') + ".class"), Kind.CLASS);
            this.binaryName = binaryName;
            this.classpath = classpath;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            byte[] bytes = classpath.classBytes(binaryName);
            if (bytes == null) {
                throw new IOException("class bytes unavailable: " + binaryName);
            }
            return new ByteArrayInputStream(bytes);
        }
    }

    /** Captures one compiled class's bytes into the shared output map. */
    private static final class OutputClass extends SimpleJavaFileObject {
        private final String className;
        private final Map<String, byte[]> outputs;

        OutputClass(String className, Map<String, byte[]> outputs) {
            super(URI.create("out:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
            this.className = className;
            this.outputs = outputs;
        }

        @Override
        public OutputStream openOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() {
                    outputs.put(className, toByteArray());
                }
            };
        }
    }

    private static final class SourceObject extends SimpleJavaFileObject {
        private final String code;

        SourceObject(String binaryName, String code) {
            super(URI.create("string:///" + binaryName.replace('.', '/') + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
