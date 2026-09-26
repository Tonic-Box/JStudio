package com.tonic.ui.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.builder.ClassBuilder;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassPool;
import com.tonic.service.ProjectService;
import com.tonic.type.AccessFlags;
import com.tonic.ui.vm.heap.model.HeapArray;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.testgen.MethodFuzzer;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.ConstructorCall;
import com.tonic.ui.vm.testgen.objectspec.ObjectSpec;
import com.tonic.ui.vm.testgen.objectspec.ParamSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VM runs receive typed arguments, arrays and constructed receivers")
class VmArgumentTest implements AccessFlags
{

    private ClassPool pool;

    @BeforeEach
    void setUp()
    {
        pool = new ClassPool(true);
        ProjectModel project = ProjectService.getInstance().createProject("vm-arguments");
        project.setClassPool(pool);
        project.addClass(ClassBuilder.create("a/Counter").access(ACC_PUBLIC).addField(ACC_PRIVATE, "base", "I").end().addMethod(ACC_PUBLIC, "<init>", "(I)V").code().aload(0).invokespecial("java/lang/Object", "<init>", "()V").aload(0).iload(1).putfield("a/Counter", "base", "I").vreturn().end().end().addMethod(ACC_PUBLIC, "add", "(I)I").code().aload(0).getfield("a/Counter", "base", "I").iload(1).iadd().ireturn().end().end().addMethod(ACC_PUBLIC | ACC_STATIC, "first", "([I)I").code().aload(0).iconst(0).iaload().ireturn().end().end().addMethod(ACC_PUBLIC | ACC_STATIC, "count", "([Ljava/lang/String;)I").code().aload(0).arraylength().ireturn().end().end().addMethod(ACC_PUBLIC | ACC_STATIC, "narrow", "(SC)I").code().iload(0).iload(1).iadd().ireturn().end().end().build());
        VMExecutionService.getInstance().reset();
    }

    @AfterEach
    void tearDown()
    {
        VMExecutionService.getInstance().shutdown();
        ProjectService.getInstance().closeProject();
    }

    @Test
    @DisplayName("host arrays of either shape become VM arrays of the declared element type")
    void arraysReachTheVm()
    {
        VMExecutionService vm = VMExecutionService.getInstance();

        assertEquals(7, vm.executeStaticMethod("a/Counter", "first", "([I)I", (Object) new int[]{7, 8}).getReturnValue());
        assertEquals(9, vm.executeStaticMethod("a/Counter", "first", "([I)I", (Object) new Object[]{9}).getReturnValue());
        assertEquals(2, vm.executeStaticMethod("a/Counter", "count", "([Ljava/lang/String;)I", (Object) new String[]{"x", null}).getReturnValue());
    }

    @Test
    @DisplayName("boxed values are narrowed to the declared parameter type")
    void primitivesAreNarrowed()
    {
        ExecutionResult result = VMExecutionService.getInstance().executeStaticMethod("a/Counter", "narrow", "(SC)I", 70000, 65);

        assertEquals((short) 70000 + 65, result.getReturnValue());
    }

    @Test
    @DisplayName("an unsupported argument fails the run instead of passing null")
    void unsupportedArgumentFails()
    {
        ExecutionResult result = VMExecutionService.getInstance().executeStaticMethod("a/Counter", "first", "([I)I", new StringBuilder("x"));

        assertFalse(result.isSuccess());
        assertTrue(result.getException() instanceof IllegalArgumentException);
    }

    @Test
    @DisplayName("an instance method is fuzzed on receivers built by its constructor")
    void instanceMethodFuzzes()
    {
        MethodFuzzer.FuzzConfig config = new MethodFuzzer.FuzzConfig();
        config.setIterationsPerType(2);
        MethodFuzzer fuzzer = new MethodFuzzer("a/Counter", "add", "(I)I", false, config);
        ObjectSpec receiver = ObjectSpec.withConstructor("a/Counter", "(I)V");
        receiver.addConstructorArg(ParamSpec.fuzz("base", "I"));
        fuzzer.setReceiverSpec(ParamSpec.object("this", "La/Counter;", receiver));

        List<MethodFuzzer.FuzzResult> results = fuzzer.runFuzz(null);

        assertFalse(results.isEmpty());
        for (MethodFuzzer.FuzzResult result : results)
        {
            assertTrue(result.getResult().isSuccess(), String.valueOf(result.getResult().getException()));
            int base = (Integer) ((ConstructorCall) result.getReceiver()).getArgs()[0];
            int arg = (Integer) result.getInputs()[0];
            assertEquals(base + arg, result.getResult().getReturnValue());
        }
    }

    @Test
    @DisplayName("the default receiver spec prefers the no-argument constructor")
    void defaultReceiverPrefersNoArgConstructor()
    {
        ParamSpec spec = MethodFuzzer.defaultReceiverSpec(pool.get("a/Counter"));

        assertEquals("()V", spec.getNestedObjectSpec().getConstructorDescriptor());
    }

    @Test
    @DisplayName("combining many large value lists stays within the cap instead of overflowing")
    void combinationCountSaturates()
    {
        List<List<Object>> lists = new ArrayList<>();
        for (int i = 0; i < 8; i++)
        {
            List<Object> values = new ArrayList<>();
            for (int v = 0; v < 20; v++)
            {
                values.add(v);
            }
            lists.add(values);
        }

        assertEquals(15, ObjectFactory.combinations(lists, 15).size());
    }

    @Test
    @DisplayName("a position with no values contributes null rather than removing every combination")
    void emptyValueListYieldsNull()
    {
        List<Object[]> combos = ObjectFactory.combinations(List.of(Collections.emptyList(), List.of(1, 2)), 10);

        assertEquals(2, combos.size());
        assertEquals(null, combos.get(0)[0]);
    }

    @Test
    @DisplayName("a multi-dimensional primitive array names its type without recursing forever")
    void multiDimensionalPrimitiveName()
    {
        ArrayInstance outer = new SimpleHeapManager().newArray("[I", 0);

        assertEquals("int[]", HeapArray.fromArrayInstance(outer, 0, null, null).getComponentTypeName());
    }
}
