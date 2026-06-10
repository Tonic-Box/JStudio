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
 * Exercises the {@code SEQUENCE [...]} instruction-pattern matcher and the {@code opcodes} regex
 * shorthand end-to-end. Fixture opcodes are chosen to be stable across javac versions:
 * {@code make}=new/dup/invokespecial/areturn, {@code makeStr}=new/dup/ldc/invokespecial/areturn,
 * {@code plain}=const/ireturn (no new), {@code fact}=recursive invokestatic.
 */
class SequenceQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Seq {\n" +
            "    static Object make() { return new Object(); }\n" +
            "    static String makeStr() { return new String(\"x\"); }\n" +
            "    static int plain() { return 42; }\n" +
            "    static int fact(int n) { return n <= 1 ? 1 : n * fact(n - 1); }\n" +
            "}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("query-seq");
        Path src = dir.resolve("Seq.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0);
        pool = new ClassPool(true);
        pool.loadClass(Files.readAllBytes(dir.resolve("t").resolve("Seq.class")));
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
    void adjacentOpcodes() throws Exception {
        assertEquals(List.of("make", "makeStr"), methods("FIND methods WHERE SEQUENCE [ new, dup ]"));
    }

    @Test
    void adjacencyIsStrict() throws Exception {
        // make has dup immediately before invokespecial; makeStr has an ldc in between.
        assertEquals(List.of("make"), methods("FIND methods WHERE SEQUENCE [ dup, invokespecial ]"));
    }

    @Test
    void anyOneWildcard() throws Exception {
        // exactly one instruction between dup and invokespecial -> only makeStr (the ldc).
        assertEquals(List.of("makeStr"), methods("FIND methods WHERE SEQUENCE [ dup, _, invokespecial ]"));
    }

    @Test
    void starRepetition() throws Exception {
        assertEquals(List.of("make", "makeStr"),
                methods("FIND methods WHERE SEQUENCE [ new, dup, _*, invokespecial ]"));
    }

    @Test
    void gap() throws Exception {
        assertEquals(List.of("make", "makeStr"),
                methods("FIND methods WHERE SEQUENCE [ new, .., invokespecial ]"));
    }

    @Test
    void predicateStepComposedWithFlag() throws Exception {
        assertEquals(List.of("fact"),
                methods("FIND methods WHERE recursive AND SEQ [ (opcode matches /^invoke/) ]"));
    }

    @Test
    void negativeMatchesNothing() throws Exception {
        assertEquals(List.of(), methods("FIND methods WHERE SEQUENCE [ dup, dup ]"));
    }

    @Test
    void opcodesRegexShorthand() throws Exception {
        assertEquals(List.of("make", "makeStr"),
                methods("FIND methods WHERE opcodes matches /new .* invokespecial/"));
    }
}
