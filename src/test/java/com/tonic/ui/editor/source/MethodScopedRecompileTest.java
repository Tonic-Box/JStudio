package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.live.MethodBodyDiff;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the core fix for the reported bug: editing ONE method must not perturb the others. With method-scoped
 * recompile, only the edited method is re-lowered; every sibling keeps its original bytecode byte-for-byte.
 */
class MethodScopedRecompileTest {

    private static final String JAR = "C:/Users/zacke/IdeaProjects/JStudio/DemoJar.jar";
    private static final String CLASS = "osrs/dev/auth/AuthenticationCoordinator";

    @Test
    void editingOneMethodLeavesSiblingsByteIdentical() throws Exception {
        System.setProperty("java.awt.headless", "true");
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

        ClassFile cf = pool.get(CLASS);
        String source = ClassDecompiler.decompile(cf);

        byte[] siblingBefore = methodCode(cf, "performAuthenticationFlow");
        assertNotNull(siblingBefore, "sibling method must exist before recompile");

        // Edit ONLY validateCredentials (a string literal unique to it); performAuthenticationFlow is untouched.
        String edited = source.replace("Invalid credentials", "Bad credentials");
        assertNotEquals(edited, source, "the edit must actually change the source");

        Set<String> changed = MethodBodyDiff.changedMethods(source, edited, pool, CLASS);
        CompilationResult result = new SourceCompiler().compile(edited, cf, pool,
                changed.isEmpty() ? null : changed);

        byte[] siblingAfter = methodCode(result.getCompiledClass(), "performAuthenticationFlow");
        assertArrayEquals(siblingBefore, siblingAfter,
                "an unedited sibling method must keep its original bytecode under method-scoped recompile");
    }

    private static byte[] methodCode(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getCodeAttribute() != null) {
                return m.getCodeAttribute().getCode().clone();
            }
        }
        return null;
    }
}
