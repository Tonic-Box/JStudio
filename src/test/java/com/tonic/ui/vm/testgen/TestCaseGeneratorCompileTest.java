package com.tonic.ui.vm.testgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.HeapReference;
import com.tonic.ui.vm.model.MethodCall;
import com.tonic.ui.vm.testgen.TestCaseGenerator.TestCase;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.ConstructorCall;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("generated tests compile and pass against the code they recorded")
class TestCaseGeneratorCompileTest
{

    private static final String TARGET = "package a;\n"
            + "public class Target {\n"
            + "    private final int base;\n"
            + "    public Target(int base) { this.base = base; }\n"
            + "    public int add(int x) { return base + x; }\n"
            + "    public static float mix(short s, char c, double d, byte b) { return s + c + (float) d + b; }\n"
            + "    public static String echo(String s, int[] xs, String[] ys) { return s + xs.length + ys.length; }\n"
            + "    public static boolean flag(boolean z) { return z; }\n"
            + "    public static long big(long j) { return j; }\n"
            + "    public static int[] pair(int a) { return new int[]{a, a}; }\n"
            + "    public static Object make() { return new Object(); }\n"
            + "    public static void boom(Integer boxed) { throw new ArithmeticException(); }\n"
            + "}\n";

    @TempDir
    Path dir;

    private static ExecutionResult returned(Object value)
    {
        return ExecutionResult.builder().success(true).returnValue(value).build();
    }

    @Test
    @DisplayName("typed literals, arrays, receivers and exceptions produce a test that compiles and passes")
    void generatedTestCompilesAndPasses() throws Exception
    {
        List<TestCase> cases = List.of(TestCase.fromResult(returned(8), "a/Target", "add", "(I)I", false, new ConstructorCall("a/Target", "(I)V", new Object[]{5}), new Object[]{3}), TestCase.fromResult(returned(3f + 'A' + 1.5f + 2), "a/Target", "mix", "(SCDB)F", true, null, new Object[]{3, 'A', 1.5, 2}), TestCase.fromResult(returned("q\"\né2" + 1), "a/Target", "echo", "(Ljava/lang/String;[I[Ljava/lang/String;)Ljava/lang/String;", true, null, new Object[]{"q\"\né", new Object[]{1, 2}, new String[]{null}}), TestCase.fromResult(returned(true), "a/Target", "flag", "(Z)Z", true, null, new Object[]{true}), TestCase.fromResult(returned(Long.MIN_VALUE), "a/Target", "big", "(J)J", true, null, new Object[]{Long.MIN_VALUE}), TestCase.fromResult(returned(new int[]{4, 4}), "a/Target", "pair", "(I)[I", true, null, new Object[]{4}), TestCase.fromResult(returned(new HeapReference("java/lang/Object", 3)), "a/Target", "make", "()Ljava/lang/Object;", true, null, new Object[0]), TestCase.fromResult(ExecutionResult.builder().success(false).exception(new RuntimeException("VM Exception: java/lang/ArithmeticException@12")).build(), "a/Target", "boom", "(Ljava/lang/Integer;)V", true, null, new Object[]{null}));

        String source = new TestCaseGenerator().generate(cases, TestCaseGenerator.JUnitVersion.JUNIT5, "TargetTest", "testTarget").getCode();

        Path sources = Files.createDirectories(dir.resolve("src/a"));
        Files.writeString(sources.resolve("Target.java"), TARGET, StandardCharsets.UTF_8);
        Files.writeString(sources.resolve("TargetTest.java"), source, StandardCharsets.UTF_8);
        Path classes = Files.createDirectories(dir.resolve("classes"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = compiler.run(null, null, errors, "-encoding", "UTF-8", "-cp", System.getProperty("java.class.path"), "-d", classes.toString(), sources.resolve("Target.java").toString(), sources.resolve("TargetTest.java").toString());
        assertEquals(0, status, errors.toString(StandardCharsets.UTF_8) + "\n" + source);

        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader()))
        {
            Class<?> testClass = loader.loadClass("a.TargetTest");
            Object instance = testClass.getDeclaredConstructor().newInstance();
            int run = 0;
            for (Method method : testClass.getDeclaredMethods())
            {
                if (method.getName().startsWith("testTarget"))
                {
                    method.setAccessible(true);
                    method.invoke(instance);
                    run++;
                }
            }
            assertEquals(cases.size(), run);
        }
    }

    @Test
    @DisplayName("an instance call from a trace is refused because its receiver was not recorded")
    void tracedInstanceCallIsRefused()
    {
        MethodCall call = new MethodCall("a/Target", "add", "(I)I", new Object[]{1}, false, 0);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> TestCase.fromCall(call));

        assertTrue(thrown.getMessage().contains("receiver"));
    }

    @Test
    @DisplayName("a run stopped outside the VM, such as by a failed argument conversion, is not turned into an exception test")
    void hostFailureIsRefused()
    {
        ExecutionResult failed = ExecutionResult.builder().success(false).exception(new IllegalArgumentException("Cannot pass StringBuilder")).build();

        assertThrows(IllegalArgumentException.class, () -> TestCase.fromResult(failed, "a/Target", "flag", "(Z)Z", true, null, new Object[]{true}));
    }
}
