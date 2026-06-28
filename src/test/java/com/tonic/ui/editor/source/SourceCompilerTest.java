package com.tonic.ui.editor.source;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.live.MethodBodyDiff;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
    void editsToConstructorBodyArePersisted() throws Exception {
        // Establish a class with a default constructor and a field, mirroring a decompiled target.
        ClassFile cf = ClassFactory.createClass(pool, "test/gen/CtorEdit", new AccessBuilder().setPublic().build());
        String withField = "package test.gen;\n\npublic class CtorEdit {\n"
            + "    public int x;\n"
            + "    public CtorEdit() { }\n}\n";
        ClassFile base = new SourceCompiler().compile(withField, cf, pool).getCompiledClass();

        // Edit the constructor body, exactly as the editor does: diff the bodies, then recompile with
        // the resulting changedMethods so only <init> is re-lowered.
        String baseline = ClassDecompiler.decompile(base);
        String edited = baseline.replace("public CtorEdit() {", "public CtorEdit() { this.x = 42;");
        assertNotEquals(baseline, edited, "edit must actually change the constructor body");

        Set<String> changed = MethodBodyDiff.changedMethods(baseline, edited, pool, "test/gen/CtorEdit");
        assertTrue(changed.contains("<init>()V"),
            "constructor body change must be detected as a changed method: " + changed);

        CompilationResult result = new SourceCompiler().compile(edited, base, pool, changed);
        assertTrue(result.isSuccess(), "constructor recompile should succeed and verify");

        String out = ClassDecompiler.decompile(result.getCompiledClass());
        assertTrue(out.replaceAll("\\s+", "").contains("x=42"),
            "edited constructor body must persist in the recompiled <init>:\n" + out);
    }

    @Test
    void recompileLowersIntoCopyLeavingInputUntouched() throws Exception {
        // Transactional recompile: compile() must lower into a working copy, never mutating the input
        // ClassFile in place. A successful edit returns a distinct object and leaves the input byte-identical,
        // which is what guarantees a FAILED recompile cannot corrupt the model.
        ClassFile cf = ClassFactory.createClass(pool, "test/gen/Tx", new AccessBuilder().setPublic().build());
        ClassFile base = new SourceCompiler().compile(withMembers(cf,
            "    static int ok() { return 1; }\n"), cf, pool).getCompiledClass();

        String baseline = ClassDecompiler.decompile(base);
        String edited = baseline.replace("return 1", "return 2");
        Set<String> changed = MethodBodyDiff.changedMethods(baseline, edited, pool, "test/gen/Tx");

        byte[] beforeBytes = base.write();
        CompilationResult result = new SourceCompiler().compile(edited, base, pool, changed);

        assertTrue(result.isSuccess(), "edit should recompile");
        assertNotSame(base, result.getCompiledClass(), "result must be a working copy, not the input object");
        assertArrayEquals(beforeBytes, base.write(),
            "the input ClassFile must be untouched by recompile (lowered into a copy)");
        assertTrue(ClassDecompiler.decompile(result.getCompiledClass()).contains("return 2"),
            "the working copy must carry the edit");
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
