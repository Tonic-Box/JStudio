package com.tonic.ui.deobfuscation;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.deobfuscation.model.DecryptorCandidate;
import com.tonic.ui.deobfuscation.model.DeobfuscationResult;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeobfuscationService {

    private static final DeobfuscationService INSTANCE = new DeobfuscationService();

    private ClassPool classPool;
    private ClassResolver classResolver;
    private SimpleHeapManager heapManager;

    private final int maxInstructions = 1_000_000;
    private final int maxCallDepth = 100;

    private DeobfuscationService() {
    }

    public static DeobfuscationService getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            throw new IllegalStateException("No project loaded");
        }

        this.classPool = project.getClassPool();
        this.classResolver = new ClassResolver(classPool);
        resetHeap();
    }

    public void resetHeap() {
        this.heapManager = new SimpleHeapManager();
        this.heapManager.setClassResolver(classResolver);
    }

    public boolean isInitialized() {
        return classPool != null;
    }

    public String executeDecryptor(MethodEntry decryptor, String encryptedValue) {
        if (!isInitialized()) {
            initialize();
        }

        resetHeap();

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .mode(ExecutionMode.RECURSIVE)
            .maxInstructions(maxInstructions)
            .maxCallDepth(maxCallDepth)
            .build();

        BytecodeEngine engine = new BytecodeEngine(ctx);

        try {
            ObjectInstance stringArg = heapManager.internString(encryptedValue);
            ConcreteValue[] args = new ConcreteValue[] { ConcreteValue.reference(stringArg) };

            BytecodeResult result = engine.execute(decryptor, args);

            if (!result.isSuccess()) {
                throw new RuntimeException("Execution failed: " +
                    (result.getException() != null ? result.getException().toString() : "Unknown error"));
            }

            ConcreteValue returnVal = result.getReturnValue();
            if (returnVal == null || returnVal.isNull()) {
                return null;
            }

            ObjectInstance returnObj = returnVal.asReference();
            return heapManager.extractString(returnObj);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    public String executeDecryptor(MethodEntry decryptor, int index) {
        if (!isInitialized()) {
            initialize();
        }

        resetHeap();

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .mode(ExecutionMode.RECURSIVE)
            .maxInstructions(maxInstructions)
            .maxCallDepth(maxCallDepth)
            .build();

        BytecodeEngine engine = new BytecodeEngine(ctx);

        try {
            ConcreteValue[] args = new ConcreteValue[] { ConcreteValue.intValue(index) };

            BytecodeResult result = engine.execute(decryptor, args);

            if (!result.isSuccess()) {
                throw new RuntimeException("Execution failed: " +
                    (result.getException() != null ? result.getException().toString() : "Unknown error"));
            }

            ConcreteValue returnVal = result.getReturnValue();
            if (returnVal == null || returnVal.isNull()) {
                return null;
            }

            ObjectInstance returnObj = returnVal.asReference();
            return heapManager.extractString(returnObj);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    public DeobfuscationResult decryptString(String className, int cpIndex,
                                              String encryptedValue,
                                              DecryptorCandidate decryptor) {
        long startTime = System.currentTimeMillis();

        try {
            String decrypted;
            switch (decryptor.getType()) {
                case STRING_TO_STRING:
                case STRING_INT_TO_STRING:
                    decrypted = executeDecryptor(decryptor.getMethod(), encryptedValue);
                    break;
                case INT_TO_STRING:
                    decrypted = executeDecryptor(decryptor.getMethod(), cpIndex);
                    break;
                default:
                    decrypted = executeDecryptor(decryptor.getMethod(), encryptedValue);
            }

            long elapsed = System.currentTimeMillis() - startTime;

            if (decrypted != null) {
                return DeobfuscationResult.success(className, cpIndex, encryptedValue,
                    decrypted, decryptor.getMethod(), elapsed);
            } else {
                return DeobfuscationResult.failure(className, cpIndex, encryptedValue,
                    "Decryptor returned null");
            }

        } catch (Exception e) {
            return DeobfuscationResult.failure(className, cpIndex, encryptedValue, e.getMessage());
        }
    }

    public Map<String, Object> executeClinitAndCaptureFields(ClassFile classFile) {
        if (!isInitialized()) {
            initialize();
        }

        resetHeap();

        BytecodeContext ctx = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .mode(ExecutionMode.RECURSIVE)
            .maxInstructions(maxInstructions)
            .maxCallDepth(maxCallDepth)
            .build();

        BytecodeEngine engine = new BytecodeEngine(ctx);

        MethodEntry clinit = null;
        for (MethodEntry method : classFile.getMethods()) {
            if ("<clinit>".equals(method.getName())) {
                clinit = method;
                break;
            }
        }

        if (clinit == null) {
            return new HashMap<>();
        }

        try {
            BytecodeResult result = engine.execute(clinit, new ConcreteValue[0]);

            if (!result.isSuccess()) {
                System.out.println("[DeobfuscationService] <clinit> execution failed: " + result.getException());
                return new HashMap<>();
            }

            Map<String, Object> capturedFields = new HashMap<>();
            String className = classFile.getClassName();

            for (var field : classFile.getFields()) {
                String fieldName = field.getName();
                String fieldDesc = field.getDesc();

                if (heapManager.hasStaticField(className, fieldName, fieldDesc)) {
                    Object value = heapManager.getStaticField(className, fieldName, fieldDesc);

                    if (value instanceof ObjectInstance) {
                        ObjectInstance obj = (ObjectInstance) value;
                        String stringVal = heapManager.extractString(obj);
                        if (stringVal != null) {
                            value = stringVal;
                        }
                    }

                    capturedFields.put(fieldName, value);
                }
            }

            return capturedFields;

        } catch (Exception e) {
            System.out.println("[DeobfuscationService] Error executing <clinit>: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public List<DeobfuscationResult> batchDecrypt(List<String> encryptedStrings,
                                                   Map<String, Integer> cpIndexMap,
                                                   String className,
                                                   DecryptorCandidate decryptor) {
        List<DeobfuscationResult> results = new ArrayList<>();

        for (String encrypted : encryptedStrings) {
            int cpIndex = cpIndexMap.getOrDefault(encrypted, -1);
            DeobfuscationResult result = decryptString(className, cpIndex, encrypted, decryptor);
            results.add(result);
        }

        return results;
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    public SimpleHeapManager getHeapManager() {
        return heapManager;
    }
}
