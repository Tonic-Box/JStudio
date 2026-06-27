package com.tonic.ui.editor.source;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the member add/remove behavior of {@link SourceCompiler#compile}: editing a class's
 * decompiled source and recompiling should add newly declared members and drop ones the source
 * no longer declares, while leaving constructors and compiler-generated members intact.
 */
class SourceCompilerTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = ClassPool.getDefault();
    }

    private static boolean hasMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String withMembers(ClassFile cf, String members) {
        String base = ClassDecompiler.decompile(cf);
        int lastBrace = base.lastIndexOf('}');
        return base.substring(0, lastBrace) + members + "}\n";
    }

    @Test
    void addsNewMethodsAndFields() throws Exception {
        ClassFile cf = pool.createNewClass("test/gen/Adder", new AccessBuilder().setPublic().build());
        String source = withMembers(cf,
            "    private static int counter = 0;\n"
                + "    static int inc() { counter = counter + 1; return counter; }\n");

        CompilationResult result = new SourceCompiler().compile(source, cf, pool);

        assertTrue(result.isSuccess(), "recompile should succeed");
        ClassFile out = result.getCompiledClass();
        assertTrue(hasMethod(out, "inc"), "new method should be added");
        assertTrue(out.getFields().stream().anyMatch(f -> f.getName().equals("counter")),
            "new field should be added");
    }

    @Test
    void removesMethodsDroppedFromSource() throws Exception {
        ClassFile cf = pool.createNewClass("test/gen/Remover", new AccessBuilder().setPublic().build());
        ClassFile withBoth = new SourceCompiler().compile(withMembers(cf,
            "    static int keep(int x) { return x + 1; }\n"
                + "    static int drop(int x) { return x - 1; }\n"), cf, pool).getCompiledClass();
        assertTrue(hasMethod(withBoth, "keep"));
        assertTrue(hasMethod(withBoth, "drop"));

        String onlyKeep = "package test.gen;\n\npublic class Remover {\n"
            + "    static int keep(int x) { return x + 1; }\n}\n";
        ClassFile out = new SourceCompiler().compile(onlyKeep, withBoth, pool).getCompiledClass();

        assertTrue(hasMethod(out, "keep"), "method present in source should remain");
        assertFalse(hasMethod(out, "drop"), "method absent from source should be removed");
    }

    @Test
    void capturingLambdaRoundTripsAndRuns() throws Exception {
        String source = "package test.gen;\n\npublic class Lam {\n"
            + "    static void apply(Runnable r) { r.run(); }\n"
            + "    public static int run() {\n"
            + "        int[] box = new int[1];\n"
            + "        apply(() -> { box[0] = 42; });\n"
            + "        return box[0];\n"
            + "    }\n}\n";
        ClassFile cf = pool.createNewClass("test/gen/Lam", new AccessBuilder().setPublic().build());
        var result = new SourceCompiler().compile(source, cf, pool);
        assertTrue(result.isSuccess());

        ClassFile out = result.getCompiledClass();
        long lambdaCount = out.getMethods().stream().filter(m -> m.getName().startsWith("lambda$")).count();
        assertEquals(1, lambdaCount, "exactly one synthetic lambda method, no duplicate");

        Class<?> loaded = defineClass("test.gen.Lam", out.write());
        var run = loaded.getDeclaredMethod("run");
        run.setAccessible(true);
        assertEquals(42, run.invoke(null), "recompiled capturing lambda must execute correctly");
    }

    @Test
    void lambdaCapturingMethodReceiverInNestedStatementRoundTrips() throws Exception {
        // The capture is used only as a method-call receiver inside a var-decl initializer and an
        // if-branch — statement forms the old ExprStmt-only capture walk skipped. Exercises full
        // body traversal + qualified capture typing so the synthetic descriptor matches.
        String source = "package test.gen;\n"
            + "import java.util.concurrent.atomic.AtomicInteger;\n"
            + "public class LamCap {\n"
            + "    static void apply(Runnable r) { r.run(); }\n"
            + "    public static int run() {\n"
            + "        AtomicInteger a = new AtomicInteger(0);\n"
            + "        apply(() -> { int v = a.incrementAndGet(); if (v > 0) { a.incrementAndGet(); } });\n"
            + "        return a.get();\n"
            + "    }\n}\n";
        ClassFile cf = pool.createNewClass("test/gen/LamCap", new AccessBuilder().setPublic().build());
        var result = new SourceCompiler().compile(source, cf, pool);
        assertTrue(result.isSuccess());

        ClassFile out = result.getCompiledClass();
        assertEquals(1, out.getMethods().stream().filter(m -> m.getName().startsWith("lambda$")).count(),
            "capture detected via full body traversal => single synthetic, no duplicate");

        Class<?> loaded = defineClass("test.gen.LamCap", out.write());
        var run = loaded.getDeclaredMethod("run");
        run.setAccessible(true);
        assertEquals(2, run.invoke(null), "lambda must capture and mutate the AtomicInteger");
    }

    private static Class<?> defineClass(String name, byte[] bytes) throws Exception {
        java.lang.reflect.Method def = ClassLoader.class.getDeclaredMethod(
            "defineClass", String.class, byte[].class, int.class, int.class);
        def.setAccessible(true);
        return (Class<?>) def.invoke(new ClassLoader() {}, name, bytes, 0, bytes.length);
    }

    @Test
    void keepsConstructorWhenNotDeclaredInSource() throws Exception {
        ClassFile cf = pool.createNewClass("test/gen/Ctor", new AccessBuilder().setPublic().build());
        ClassFactory.createMethodWithBody(cf, new AccessBuilder().setPublic().build(), "<init>", "()V");

        String source = "package test.gen;\n\npublic class Ctor {\n"
            + "    static int v() { return 1; }\n}\n";
        ClassFile out = new SourceCompiler().compile(source, cf, pool).getCompiledClass();

        assertTrue(hasMethod(out, "<init>"), "constructor must never be auto-removed");
        assertTrue(hasMethod(out, "v"));
    }
}
