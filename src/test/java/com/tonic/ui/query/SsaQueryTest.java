package com.tonic.ui.query;

import com.tonic.parser.ClassPool;
import com.tonic.ui.query.ast.Query;
import com.tonic.ui.query.exec.QueryBatchRunner;
import com.tonic.ui.query.parser.QueryParser;
import com.tonic.ui.query.planner.ProbePlan;
import com.tonic.ui.query.planner.QueryPlanner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the SSA/CFG tier through the live pipeline: {@code recursive} (static self-call scan) and
 * {@code method.loops} (lazy SSA loop analysis) select the right methods.
 */
class SsaQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Cfg {\n" +
            "    static int fact(int n) { return n <= 1 ? 1 : n * fact(n - 1); }\n" +
            "    static int sum(int n) { int s = 0; for (int i = 0; i < n; i++) s += i; return s; }\n" +
            "    static int plain(int x) { return x + 1; }\n" +
            "    static int loopCall(int n) { int s = 0; for (int i = 0; i < n; i++) s += Math.abs(i); return s; }\n" +
            "    static int outsideCall(int n) { return Math.abs(n); }\n" +
            "}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("query-ssa");
        Path src = dir.resolve("Cfg.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0);
        pool = new ClassPool(true);
        pool.loadClass(Files.readAllBytes(dir.resolve("t").resolve("Cfg.class")));
    }

    private List<String> methods(String query) throws Exception {
        Query parsed = new QueryParser().parse(query);
        ProbePlan plan = new QueryPlanner().plan(parsed);
        return new QueryBatchRunner(pool).run(plan, null).matchingRows().stream()
                .map(r -> String.valueOf(r.getColumn("method")))
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    void recursiveSelectsSelfCallingMethod() throws Exception {
        assertEquals(List.of("fact"), methods("FIND methods WHERE recursive"));
    }

    @Test
    void loopsSelectsLoopingMethod() throws Exception {
        assertEquals(List.of("loopCall", "sum"), methods("FIND methods WHERE method.loops > 0"));
    }

    @Test
    void inLoopSelectsCallInsideLoop() throws Exception {
        assertEquals(List.of("loopCall"), methods("FIND methods WHERE has call where (inLoop)"));
    }
}
