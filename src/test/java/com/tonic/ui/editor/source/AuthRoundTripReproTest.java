package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reproduces the reported decompile->recompile corruption WITHOUT the GUI: loads the demo jar, decompiles
 * AuthenticationCoordinator with YABR, recompiles the WHOLE-class source through JStudio's {@link SourceCompiler}
 * (the exact path the editor's recompile button uses), then decompiles the result. Dumps source1/source2 and the
 * recompiled .class so the intermediate bytecode can be inspected (separating recompiler corruption from
 * decompiler misrender). Diagnostic only - prints, does not assert.
 */
class AuthRoundTripReproTest {

    private static final String JAR = "C:/Users/zacke/IdeaProjects/JStudio/DemoJar.jar";
    private static final String TARGET = "osrs/dev/auth/AuthenticationCoordinator";
    private static final Path OUT = Path.of("build", "repro");

    @Test
    void roundTripAuthenticationCoordinator() throws Exception {
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(OUT);

        ClassPool pool = new ClassPool();
        try (JarFile jar = new JarFile(JAR)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.getName().endsWith(".class")) {
                    try (InputStream in = jar.getInputStream(e)) {
                        pool.loadClass(in.readAllBytes());
                    }
                }
            }
        }

        ClassFile cf = pool.get(TARGET);
        String source1 = ClassDecompiler.decompile(cf);
        Files.writeString(OUT.resolve("source1.java"), source1);

        // Determinism probe: decompile the ORIGINAL bytecode a second time, fresh from bytes.
        ClassFile cfAgain = pool.loadClass(originalBytes());
        String source1b = ClassDecompiler.decompile(cfAgain);
        System.out.println("[repro] decompile deterministic (same bytes twice): " + source1.equals(source1b));

        CompilationResult result = new SourceCompiler().compile(source1, cf, pool);
        System.out.println("[repro] recompile success: " + result.isSuccess()
                + "  errors=" + result.getErrorCount() + " warnings=" + result.getWarningCount());
        for (CompilationError err : result.getErrors()) {
            System.out.println("    [" + (err.isError() ? "ERROR" : "WARN") + "] " + err);
        }

        ClassFile compiled = result.getCompiledClass();
        byte[] b2 = compiled.write();
        Files.write(OUT.resolve("AuthenticationCoordinator.recompiled.class"), b2);

        ClassFile cf2 = pool.loadClass(b2);
        String source2 = ClassDecompiler.decompile(cf2);
        Files.writeString(OUT.resolve("source2.java"), source2);

        System.out.println("[repro] source1 == source2 (round-trip fixpoint): " + source1.equals(source2));
        System.out.println("[repro] source1 has 'static {': " + source1.contains("static {")
                + " | source2 has 'static {': " + source2.contains("static {"));
        System.out.println("[repro] source1 has 'new LoginDialog(arg': " + source1.contains("new LoginDialog(arg")
                + " | source2 has 'new LoginDialog()': " + source2.contains("new LoginDialog()"));
        System.out.println("[repro] source2 has broken ternary '? local': " + source2.contains("? local"));
        System.out.println("[repro] artifacts written under: " + OUT.toAbsolutePath());
    }

    private static byte[] originalBytes() throws Exception {
        try (JarFile jar = new JarFile(JAR)) {
            JarEntry e = jar.getJarEntry(TARGET + ".class");
            try (InputStream in = jar.getInputStream(e)) {
                return in.readAllBytes();
            }
        }
    }
}
