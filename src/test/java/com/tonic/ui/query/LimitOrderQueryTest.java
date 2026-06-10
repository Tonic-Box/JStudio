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

/** Verifies {@code ORDER BY <col> [ASC|DESC]} sorts and {@code LIMIT n} truncates the result set. */
class LimitOrderQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Ord {\n" +
            "    static int charlie() { return 1; }\n" +
            "    static int alpha() { return 2; }\n" +
            "    static int bravo() { return 3; }\n" +
            "}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null);
        Path dir = Files.createTempDirectory("query-ord");
        Path src = dir.resolve("Ord.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0);
        pool = new ClassPool(true);
        pool.loadClass(Files.readAllBytes(dir.resolve("t").resolve("Ord.class")));
    }

    /** Order-preserving (no .sorted()) so the query's own ordering is what's asserted. */
    private List<String> methodsInOrder(String query) throws Exception {
        Query parsed = new QueryParser().parse(query);
        ProbePlan plan = new QueryPlanner().plan(parsed);
        return new QueryBatchRunner(pool).run(plan, null).matchingRows().stream()
                .map(r -> String.valueOf(r.getColumn("method")))
                .collect(Collectors.toList());
    }

    private static final String LOWER = "FIND methods WHERE method.name matches /^[a-z]/";

    @Test
    void orderByAscending() throws Exception {
        assertEquals(List.of("alpha", "bravo", "charlie"), methodsInOrder(LOWER + " ORDER BY method"));
    }

    @Test
    void orderByDescending() throws Exception {
        assertEquals(List.of("charlie", "bravo", "alpha"), methodsInOrder(LOWER + " ORDER BY method DESC"));
    }

    @Test
    void orderByThenLimit() throws Exception {
        assertEquals(List.of("alpha", "bravo"), methodsInOrder(LOWER + " ORDER BY method LIMIT 2"));
    }

    @Test
    void limitTruncates() throws Exception {
        assertEquals(1, methodsInOrder(LOWER + " LIMIT 1").size());
    }
}
