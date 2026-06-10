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

/** Verifies the {@code IN [a, b, c]} set-membership operand end-to-end. */
class SetOperandQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Sets {\n" +
            "    static int num() { return 7; }\n" +
            "    static Object obj() { return new Object(); }\n" +
            "}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null);
        Path dir = Files.createTempDirectory("query-set");
        Path src = dir.resolve("Sets.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0);
        pool = new ClassPool(true);
        pool.loadClass(Files.readAllBytes(dir.resolve("t").resolve("Sets.class")));
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
    void opcodeMembership() throws Exception {
        assertEquals(List.of("num", "obj"),
                methods("FIND methods WHERE HAS insn WHERE (opcode IN [ireturn, areturn]) AND method.name matches /^(num|obj)$/"));
        assertEquals(List.of("obj"),
                methods("FIND methods WHERE HAS insn WHERE (opcode IN [areturn]) AND method.name matches /^(num|obj)$/"));
    }

    @Test
    void nameMembershipWithStrings() throws Exception {
        assertEquals(List.of("num"),
                methods("FIND methods WHERE method.name IN [\"num\", \"missing\"]"));
    }

    @Test
    void emptyOrNonMatchingSet() throws Exception {
        assertEquals(List.of(), methods("FIND methods WHERE HAS insn WHERE (opcode IN [tableswitch, lookupswitch])"));
    }
}
