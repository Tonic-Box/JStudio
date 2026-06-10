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
 * Exercises the {@code flowsTo}/{@code flowsFrom} data-flow relations through the live pipeline:
 * forward def-use reachability from a {@code param(n)} value to a {@code return}, evaluated over the
 * lazily-lifted SSA IR (no YABR change — uses {@code IRMethod.getParameters()} + {@code DefUseChains}).
 */
class DataFlowQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Flow {\n" +
            "    static int echo(int n) { return n; }\n" +
            "    static int derived(int n) { return n * 3 + 7; }\n" +
            "    static int ignore(int n) { return 42; }\n" +
            "    static int second(int a, int b) { return b; }\n" +
            "    static int id(int x) { return x; }\n" +
            "    static int wrap(int n) { return id(n); }\n" +
            "    static int wrapConst(int n) { return id(5); }\n" +
            "}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("query-flow");
        Path src = dir.resolve("Flow.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0);
        pool = new ClassPool(true);
        pool.loadClass(Files.readAllBytes(dir.resolve("t").resolve("Flow.class")));
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
    void firstParamFlowsToReturn() throws Exception {
        assertEquals(List.of("derived", "echo", "id", "wrap"),
                methods("FIND methods WHERE param(0) flowsTo return"));
    }

    @Test
    void secondParamFlowsToReturn() throws Exception {
        assertEquals(List.of("second"), methods("FIND methods WHERE param(1) flowsTo return"));
    }

    @Test
    void flowsFromIsTheSwappedRelation() throws Exception {
        assertEquals(List.of("derived", "echo", "id", "wrap"),
                methods("FIND methods WHERE return flowsFrom param(0)"));
    }

    @Test
    void callArgFlowsFromParam() throws Exception {
        assertEquals(List.of("wrap"),
                methods("FIND methods WHERE has call where (arg(0) flowsFrom param(0))"));
    }
}
