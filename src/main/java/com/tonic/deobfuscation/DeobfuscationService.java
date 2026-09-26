package com.tonic.deobfuscation;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.deobfuscation.model.DecryptorCandidate;
import com.tonic.deobfuscation.model.DeobfuscationResult;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;
import lombok.Getter;

import java.util.*;

/** The shared string-decryption service: runs suspected decryptor methods in the bytecode emulator over the current project, with a fresh heap per call. */
public class DeobfuscationService
{

    private static final DeobfuscationService INSTANCE = new DeobfuscationService();

    @Getter
    private ClassPool classPool;
    private ClassResolver classResolver;
    @Getter
    private SimpleHeapManager heapManager;

    private final int maxInstructions = 1_000_000;
    private final int maxCallDepth = 100;

    private DeobfuscationService()
    {
    }

    /** @return the shared service */
    public static DeobfuscationService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Binds the service to the current project's class pool and starts a fresh heap.
     *
     * @throws IllegalStateException if no project with a class pool is loaded
     */
    public void initialize()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            throw new IllegalStateException("No project loaded");
        }

        this.classPool = project.getClassPool();
        this.classResolver = new ClassResolver(classPool);
        resetHeap();
    }

    /** Replaces the emulator heap with an empty one. */
    public void resetHeap()
    {
        this.heapManager = new SimpleHeapManager();
        this.heapManager.setClassResolver(classResolver);
    }

    /**
     * Tells whether the service is bound to a class pool.
     *
     * @return true once initialized
     */
    public boolean isInitialized()
    {
        return classPool != null;
    }

    /**
     * Runs a decryptor with one string argument on a fresh heap, initializing the service first if needed.
     *
     * @param decryptor the static decryptor method
     * @param encryptedValue the string to pass it
     * @return the string it returned, or null when it returned null
     * @throws IllegalStateException if the service is not initialized and no project is loaded
     * @throws RuntimeException if execution fails or the return value is not a string
     */
    public String executeDecryptor(MethodEntry decryptor, String encryptedValue)
    {
        if (!isInitialized())
        {
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

        try
        {
            ObjectInstance stringArg = heapManager.internString(encryptedValue);
            ConcreteValue[] args = new ConcreteValue[]{ConcreteValue.reference(stringArg)};

            BytecodeResult result = engine.execute(decryptor, args);

            if (!result.isSuccess())
            {
                throw new RuntimeException("Execution failed: " + (result.getException() != null ? result.getException().toString() : "Unknown error"));
            }

            ConcreteValue returnVal = result.getReturnValue();
            if (returnVal == null || returnVal.isNull())
            {
                return null;
            }

            ObjectInstance returnObj = returnVal.asReference();
            return heapManager.extractString(returnObj);

        }
        catch (Exception e)
        {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a decryptor with one int argument on a fresh heap, initializing the service first if needed.
     *
     * @param decryptor the static decryptor method
     * @param index the int to pass it
     * @return the string it returned, or null when it returned null
     * @throws IllegalStateException if the service is not initialized and no project is loaded
     * @throws RuntimeException if execution fails or the return value is not a string
     */
    public String executeDecryptor(MethodEntry decryptor, int index)
    {
        if (!isInitialized())
        {
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

        try
        {
            ConcreteValue[] args = new ConcreteValue[]{ConcreteValue.intValue(index)};

            BytecodeResult result = engine.execute(decryptor, args);

            if (!result.isSuccess())
            {
                throw new RuntimeException("Execution failed: " + (result.getException() != null ? result.getException().toString() : "Unknown error"));
            }

            ConcreteValue returnVal = result.getReturnValue();
            if (returnVal == null || returnVal.isNull())
            {
                return null;
            }

            ObjectInstance returnObj = returnVal.asReference();
            return heapManager.extractString(returnObj);

        }
        catch (Exception e)
        {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts one constant-pool string with a candidate decryptor, passing the constant-pool index to int decryptors and the string to all others.
     *
     * @param className the internal name of the class holding the string
     * @param cpIndex the string's constant-pool index
     * @param encryptedValue the encrypted string
     * @param decryptor the decryptor to run
     * @return a success result with the decrypted text and timing, or a failure result with the error; never throws
     */
    public DeobfuscationResult decryptString(String className, int cpIndex, String encryptedValue, DecryptorCandidate decryptor)
    {
        long startTime = System.currentTimeMillis();

        try
        {
            String decrypted;
            if (Objects.requireNonNull(decryptor.getType()) == DecryptorCandidate.DecryptorType.INT_TO_STRING)
            {
                decrypted = executeDecryptor(decryptor.getMethod(), cpIndex);
            }
            else
            {
                decrypted = executeDecryptor(decryptor.getMethod(), encryptedValue);
            }

            long elapsed = System.currentTimeMillis() - startTime;

            if (decrypted != null)
            {
                return DeobfuscationResult.success(className, cpIndex, encryptedValue, decrypted, decryptor.getMethod(), elapsed);
            }
            else
            {
                return DeobfuscationResult.failure(className, cpIndex, encryptedValue, "Decryptor returned null");
            }

        }
        catch (Exception e)
        {
            return DeobfuscationResult.failure(className, cpIndex, encryptedValue, e.getMessage());
        }
    }

}
