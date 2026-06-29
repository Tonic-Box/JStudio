package com.tonic.ui.editor.source;

import com.tonic.analysis.ClassFactory;
import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.ui.live.MethodBodyDiff;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adding a method call (e.g. {@code System.out.println(...)}) to a constructor must round-trip through the
 * recompiler in both the changed-methods path and the whole-class fallback - neither may drop the body.
 */
class CtorPrintlnReproTest {

    @Test
    void printlnInConstructorViaChangedMethods() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        ClassFile cf = ClassFactory.createClass(pool, "con/tonic/Test", new AccessBuilder().setPublic().build());
        ClassFile base = new SourceCompiler().compile(
            "package con.tonic;\n\npublic class Test {\n    public Test() { }\n}\n", cf, pool).getCompiledClass();

        String baseline = ClassDecompiler.decompile(base);
        String edited = baseline.replace("public Test() {",
            "public Test() { System.out.println(\"Hello World\");");
        Set<String> changed = MethodBodyDiff.changedMethods(baseline, edited, pool, "con/tonic/Test");
        assertTrue(changed.contains("<init>()V"), "constructor edit must be detected: " + changed);

        CompilationResult result = new SourceCompiler().compile(edited, base, pool, changed);
        assertTrue(result.isSuccess(), "constructor recompile must succeed: " + result.getErrors());
        assertTrue(ClassDecompiler.decompile(result.getCompiledClass()).contains("System.out.println"),
            "the println must persist in the recompiled constructor");
    }

    @Test
    void printlnInConstructorViaWholeClass() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        ClassFile cf = ClassFactory.createClass(pool, "con/tonic/Fresh", new AccessBuilder().setPublic().build());
        String full = "package con.tonic;\n\npublic class Fresh {\n"
            + "    public Fresh() { System.out.println(\"Hello World\"); }\n}\n";

        CompilationResult result = new SourceCompiler().compile(full, cf, pool);
        assertTrue(result.isSuccess(), "whole-class recompile must succeed: " + result.getErrors());
        assertTrue(ClassDecompiler.decompile(result.getCompiledClass()).contains("System.out.println"),
            "the whole-class fallback must not drop the constructor body");
    }
}
