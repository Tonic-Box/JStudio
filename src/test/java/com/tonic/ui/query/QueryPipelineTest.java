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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end test of the live query pipeline: text -> {@link QueryParser} -> {@link QueryPlanner} ->
 * {@link QueryBatchRunner} over a {@link ClassPool}, asserting the flagship query selects the right
 * method through the whole stack (not just the evaluator in isolation).
 */
class QueryPipelineTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Target {\n" +
            "    static void foo(int x) {}\n" +
            "    static void bar(int a, int b) {}\n" +
            "    void match() { foo(999); }\n" +
            "    void wrongValue() { foo(998); }\n" +
            "    void wrongArity() { bar(999, 1); }\n" +
            "}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("query-pipeline");
        Path src = dir.resolve("Target.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0);
        pool = new ClassPool(true);
        pool.loadClass(Files.readAllBytes(dir.resolve("t").resolve("Target.class")));
    }

    private List<String> runForMethods(String query) throws Exception {
        Query parsed = new QueryParser().parse(query);
        ProbePlan plan = new QueryPlanner().plan(parsed);
        QueryBatchRunner.QueryBatchResult result = new QueryBatchRunner(pool).run(plan, null);
        return result.matchingRows().stream()
                .map(r -> String.valueOf(r.getColumn("method")))
                .collect(Collectors.toList());
    }

    @Test
    void flagshipSelectsOnlyTheMatchingMethod() throws Exception {
        List<String> methods = runForMethods(
                "FIND methods WHERE has call where (count(arg) == 1 and arg(0).type == int and arg(0).value == 999)");
        assertEquals(List.of("match"), methods);
    }

    @Test
    void regexNameAndModifiersCompose() throws Exception {
        List<String> methods = runForMethods("FIND methods WHERE method.name matches /^wrong/");
        assertTrue(methods.contains("wrongValue"));
        assertTrue(methods.contains("wrongArity"));
        assertFalse(methods.contains("match"));
    }

    @Test
    void callOwnerAndNameMatch() throws Exception {
        List<String> methods = runForMethods(
                "FIND methods WHERE has call where (name == \"bar\" and count(arg) == 2)");
        assertEquals(List.of("wrongArity"), methods);
    }
}
