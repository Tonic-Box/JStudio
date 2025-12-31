package com.tonic.analysis;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class BytecodeEngineTest {

    @Test
    void testArrayListClinit() throws Exception {
        ClassPool classPool = createClassPoolWithJdk();
        ClassResolver classResolver = new ClassResolver(classPool);

        ClassFile arrayListCf = classPool.get("java/util/ArrayList");
        assertNotNull(arrayListCf, "ArrayList should be in the class pool");

        MethodEntry clinit = null;
        for (MethodEntry m : arrayListCf.getMethods()) {
            if (m.getName().equals("<clinit>")) {
                clinit = m;
                break;
            }
        }
        assertNotNull(clinit, "<clinit> method should exist in ArrayList");

        SimpleHeapManager heapManager = new SimpleHeapManager();
        heapManager.setClassResolver(classResolver);

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .mode(ExecutionMode.RECURSIVE)
            .maxCallDepth(100)
            .maxInstructions(1_000_000)
            .build();

        BytecodeEngine engine = new BytecodeEngine(ctx);

        System.out.println("[TEST] Starting execution of ArrayList.<clinit>");
        BytecodeResult result = engine.execute(clinit);
        System.out.println("[TEST] Execution completed: " + result.getStatus());
        System.out.println("[TEST] Instructions executed: " + result.getInstructionsExecuted());

        if (result.getException() != null) {
            System.out.println("[TEST] Exception: " + result.getException());
        }

        assertTrue(result.isSuccess(), "ArrayList <clinit> execution should succeed");
    }

    @Test
    void testArrayListConstructor() throws Exception {
        ClassPool classPool = createClassPoolWithJdk();
        ClassResolver classResolver = new ClassResolver(classPool);

        ClassFile arrayListCf = classPool.get("java/util/ArrayList");
        assertNotNull(arrayListCf, "ArrayList should be in the class pool");

        MethodEntry constructor = null;
        for (MethodEntry m : arrayListCf.getMethods()) {
            if (m.getName().equals("<init>") && m.getDesc().equals("()V")) {
                constructor = m;
                break;
            }
        }
        assertNotNull(constructor, "ArrayList() constructor should exist");

        SimpleHeapManager heapManager = new SimpleHeapManager();
        heapManager.setClassResolver(classResolver);

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .mode(ExecutionMode.RECURSIVE)
            .maxCallDepth(100)
            .maxInstructions(1_000_000)
            .build();

        BytecodeEngine engine = new BytecodeEngine(ctx);

        System.out.println("[TEST] Creating ArrayList instance on heap");
        var arrayListInstance = heapManager.newObject("java/util/ArrayList");

        System.out.println("[TEST] Starting execution of ArrayList.<init>()V");
        BytecodeResult result = engine.execute(constructor,
                com.tonic.analysis.execution.state.ConcreteValue.reference(arrayListInstance));
        System.out.println("[TEST] Execution completed: " + result.getStatus());
        System.out.println("[TEST] Instructions executed: " + result.getInstructionsExecuted());

        if (result.getException() != null) {
            System.out.println("[TEST] Exception: " + result.getException());
        }

        assertTrue(result.isSuccess(), "ArrayList constructor execution should succeed");
    }

    private ClassPool createClassPoolWithJdk() throws IOException {
        return new ClassPool();
    }
}
