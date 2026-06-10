package com.tonic.ui.query.eval;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.query.ast.Condition;
import com.tonic.ui.query.parser.ConditionParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end proof of the composable engine: the flagship query
 * "any method call with exactly one int parameter whose value is 999" parses and matches the right
 * method while rejecting the wrong-value and wrong-arity calls.
 */
class FlagshipQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "public class Target {\n" +
            "    static void foo(int x) {}\n" +
            "    static void bar(int a, int b) {}\n" +
            "    void match() { foo(999); }\n" +
            "    void wrongValue() { foo(998); }\n" +
            "    void wrongArity() { bar(999, 1); }\n" +
            "}\n";

    private static final String FLAGSHIP =
            "has call where (count(arg) == 1 and arg(0).type == int and arg(0).value == 999)";

    private static ClassFile classFile;

    @BeforeAll
    static void compileFixture() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("query-flagship");
        Path src = dir.resolve("Target.java");
        Files.writeString(src, SOURCE);
        int rc = compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString());
        assumeTrue(rc == 0, "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("t").resolve("Target.class"));
        classFile = new ClassFile(new ByteArrayInputStream(bytes));
    }

    private boolean matches(String methodName, String query) throws Exception {
        Condition condition = ConditionParser.parse(query);
        MethodEntry method = classFile.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow();
        ConditionEvaluator evaluator = new ConditionEvaluator(DefaultAttributes.create());
        EvalContext ctx = new EvalContext(classFile, method, new EvidenceCollector());
        return evaluator.eval(condition, new Subject.MethodSubject(method, ctx));
    }

    @Test
    void matchesExactlyOneIntArgWith999() throws Exception {
        assertTrue(matches("match", FLAGSHIP), "foo(999) must match");
    }

    @Test
    void rejectsWrongValue() throws Exception {
        assertFalse(matches("wrongValue", FLAGSHIP), "foo(998) must not match");
    }

    @Test
    void rejectsWrongArity() throws Exception {
        assertFalse(matches("wrongArity", FLAGSHIP), "bar(999, 1) must not match");
    }

    @Test
    void argTypeAndCountCompose() throws Exception {
        assertTrue(matches("match", "has call where (arg(0).value == 999)"));
        assertTrue(matches("match", "has call where (call.name == \"foo\")"));
        assertFalse(matches("match", "has call where (count(arg) == 2)"));
    }
}
