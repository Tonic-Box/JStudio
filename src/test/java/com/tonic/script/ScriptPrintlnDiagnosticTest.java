package com.tonic.script;
import com.tonic.analysis.ClassFactory;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.editor.ASTEditor;
import com.tonic.analysis.source.editor.Replacement;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.script.bridge.ASTBridge;
import com.tonic.script.bridge.CommonAPI;
import com.tonic.script.engine.ScriptInterpreter;
import com.tonic.script.engine.ScriptLexer;
import com.tonic.script.engine.ScriptParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the "Remove Debug Prints" AST script end-to-end: it matches println calls, removes them, and cements the
 * change to bytecode for methods YABR can round-trip - while the verify-and-restore guard leaves methods it cannot
 * round-trip (complex control flow) untouched instead of corrupting them.
 */
public class ScriptPrintlnDiagnosticTest {

    private static final String SCRIPT =
            "ast.onMethodCall((call) => {\n" +
            "    if (call.owner == \"java/io/PrintStream\"\n" +
            "        && (call.name == \"println\" || call.name == \"print\")) {\n" +
            "        return ast.remove();\n" +
            "    }\n" +
            "});";

    private static ClassPool loadPool() throws Exception {
        ClassPool pool = new ClassPool();
        try (JarFile jar = new JarFile(new File("DemoJar.jar"))) {
            pool.loadJar(jar);
        }
        return pool;
    }

    /** The pure IR lift->lower round-trip is faithful, so the restore path is sound. */
    @Test
    public void irRoundTripIsFaithful() throws Exception {
        ClassFile cf = loadPool().get("osrs/dev/Main");
        MethodEntry m = method(cf, "main");
        int before = countCalls(cf, m, false);
        SSA ssa = new SSA(cf.getConstPool());
        ssa.lower(ssa.lift(m), m);
        ClassFactory.computeFrames(cf);
        assertEquals(before, countCalls(cf, m, false), "IR lift->lower must preserve every call");
    }

    /** Normal method: the script removes the println and every other call survives. */
    @Test
    public void removesPrintlnFromSimpleMethod() throws Exception {
        ClassPool pool = loadPool();
        ClassFile cf = pool.get("osrs/dev/Logger");
        MethodEntry m = method(cf, "log");
        int printlnBefore = countCalls(cf, m, true);
        int totalBefore = countCalls(cf, m, false);
        assertTrue(printlnBefore > 0, "Logger.log has a println to remove");

        int mods = applyGuarded(pool, cf, m);
        System.out.println("Logger.log: mods=" + mods + ", println " + printlnBefore + "->"
                + countCalls(cf, m, true) + ", calls " + totalBefore + "->" + countCalls(cf, m, false));
        assertEquals(printlnBefore, mods, "every println removed");
        assertEquals(0, countCalls(cf, m, true), "no println remains");
        assertEquals(totalBefore - printlnBefore, countCalls(cf, m, false), "other calls preserved");
    }

    /** Complex method YABR can't round-trip: the guard restores it unchanged instead of corrupting it. */
    @Test
    public void guardLeavesUnroundtrippableMethodUntouched() throws Exception {
        ClassPool pool = loadPool();
        ClassFile cf = pool.get("osrs/dev/Main");
        MethodEntry m = method(cf, "main");
        int printlnBefore = countCalls(cf, m, true);
        int totalBefore = countCalls(cf, m, false);

        int mods = applyGuarded(pool, cf, m);
        System.out.println("Main.main: mods=" + mods + ", println " + printlnBefore + "->"
                + countCalls(cf, m, true) + ", calls " + totalBefore + "->" + countCalls(cf, m, false));
        assertEquals(0, mods, "guard skips a method it cannot safely round-trip");
        assertEquals(printlnBefore, countCalls(cf, m, true), "method left unchanged (println intact)");
        assertEquals(totalBefore, countCalls(cf, m, false), "method left unchanged (all calls intact)");
    }

    /** Mirrors ScriptRunner.runASTMode incl. the verify-and-restore guard; returns modifications cemented. */
    private static int applyGuarded(ClassPool pool, ClassFile cf, MethodEntry m) {
        ScriptInterpreter interp = new ScriptInterpreter();
        CommonAPI api = new CommonAPI();
        api.setContext(cf.getClassName(), m.getName(), m.getDesc());
        api.registerIn(interp);
        ASTBridge astBridge = new ASTBridge(interp);
        interp.getGlobalContext().defineConstant("ast", astBridge.createAstObject());
        interp.execute(new ScriptParser(new ScriptLexer(SCRIPT).tokenize()).parse());

        SSA ssa = new SSA(cf.getConstPool());
        IRMethod ir = ssa.lift(m);
        BlockStmt body = MethodRecoverer.recoverMethod(ir, m);
        int count = astBridge.applyTo(new ASTEditor(body, m.getName(), m.getDesc(), cf.getClassName()));
        if (count == 0) return 0;

        java.util.List<String> intended = sigs(body, m, cf.getClassName());
        IRMethod pristine = new SSA(cf.getConstPool()).lift(m);
        new ASTLowerer(cf.getConstPool(), pool).replaceBody(body, ir);
        ssa.lower(ir, m);
        ClassFactory.computeFrames(cf);

        BlockStmt check = MethodRecoverer.recoverMethod(new SSA(cf.getConstPool()).lift(m), m);
        java.util.List<String> actual = check == null ? null : sigs(check, m, cf.getClassName());
        if (actual == null || !actual.equals(intended)) {
            new SSA(cf.getConstPool()).lower(pristine, m);
            ClassFactory.computeFrames(cf);
            return 0;
        }
        return count;
    }

    private static java.util.List<String> sigs(BlockStmt body, MethodEntry m, String className) {
        java.util.List<String> s = new java.util.ArrayList<>();
        ASTEditor e = new ASTEditor(body, m.getName(), m.getDesc(), className);
        e.onMethodCall((ctx, call) -> {
            s.add(call.getOwnerClass() + "#" + call.getMethodName());
            return Replacement.keep();
        });
        e.apply();
        java.util.Collections.sort(s);
        return s;
    }

    private static MethodEntry method(ClassFile cf, String name) {
        return cf.getMethods().stream()
                .filter(x -> x.getName().equals(name) && x.getCodeAttribute() != null)
                .findFirst().orElseThrow();
    }

    private static int countCalls(ClassFile cf, MethodEntry method, boolean printlnOnly) {
        SSA ssa = new SSA(cf.getConstPool());
        BlockStmt body = MethodRecoverer.recoverMethod(ssa.lift(method), method);
        int[] n = {0};
        ASTEditor editor = new ASTEditor(body, method.getName(), method.getDesc(), cf.getClassName());
        editor.onMethodCall((ctx, call) -> {
            boolean isPrint = "java/io/PrintStream".equals(call.getOwnerClass())
                    && ("println".equals(call.getMethodName()) || "print".equals(call.getMethodName()));
            if (!printlnOnly || isPrint) {
                n[0]++;
            }
            return Replacement.keep();
        });
        editor.apply();
        return n[0];
    }
}
