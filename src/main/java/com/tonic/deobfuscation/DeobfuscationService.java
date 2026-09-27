package com.tonic.deobfuscation;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.deobfuscation.model.DecryptorCandidate;
import com.tonic.deobfuscation.model.DeobfuscationResult;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;
import com.tonic.ui.vm.VmValueConverter;
import com.tonic.util.DescriptorParser;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
     * Runs a decryptor on a fresh heap with arguments converted to its parameter types, initializing the service first if needed.
     *
     * @param decryptor the static decryptor method
     * @param args the arguments as host values; arrays may be Object arrays of their elements
     * @return the string it returned, a byte or char array return decoded as text, or null when it returned null
     * @throws IllegalStateException if the service is not initialized and no project is loaded, or the decryptor fails or returns something other than a string or byte or char array
     * @throws IllegalArgumentException if an argument cannot be passed as its parameter type
     */
    public String executeDecryptor(MethodEntry decryptor, Object... args)
    {
        if (!isInitialized())
        {
            initialize();
        }
        resetHeap();

        VmValueConverter converter = new VmValueConverter(heapManager, classResolver, maxCallDepth, maxInstructions);
        ConcreteValue[] vmArgs = converter.toConcreteAll(args, DescriptorParser.parameterDescriptors(decryptor.getDesc()));
        BytecodeContext ctx = new BytecodeContext.Builder()
                .heapManager(heapManager)
                .classResolver(classResolver)
                .mode(ExecutionMode.RECURSIVE)
                .maxInstructions(maxInstructions)
                .maxCallDepth(maxCallDepth)
                .build();
        BytecodeResult result = new BytecodeEngine(ctx).execute(decryptor, vmArgs);
        if (!result.isSuccess())
        {
            throw new IllegalStateException("Decryptor failed: " + (result.getException() != null ? result.getException().toString() : result.getStatus().toString()));
        }

        String returnType = DescriptorParser.returnDescriptor(decryptor.getDesc());
        Object returned = converter.toHost(result.getReturnValue(), returnType);
        if (returned == null || returned instanceof String)
        {
            return (String) returned;
        }
        if (returned instanceof byte[])
        {
            return new String((byte[]) returned, StandardCharsets.UTF_8);
        }
        if (returned instanceof char[])
        {
            return new String((char[]) returned);
        }
        throw new IllegalStateException("Decryptor returned " + returned + ", not a string");
    }

    /**
     * Decrypts every call a class makes to a decryptor, running the decryptor with each call site's constant arguments.
     *
     * @param classFile the class whose calls are decrypted
     * @param decryptor the decryptor to look for and run
     * @return one result per call site, a failure where the arguments are not constants or the run fails; never throws for a single site
     */
    public List<DeobfuscationResult> decryptCallSites(ClassFile classFile, DecryptorCandidate decryptor)
    {
        MethodEntry method = decryptor.getMethod();
        List<DeobfuscationResult> results = new ArrayList<>();
        for (CallSites.CallSite site : CallSites.find(classFile, method.getOwnerName(), method.getName(), method.getDesc()))
        {
            results.add(decrypt(classFile, site, decryptor));
        }
        return results;
    }

    /**
     * Decrypts one call site by running the decryptor with its constant arguments.
     *
     * @param classFile the class holding the call
     * @param site the call site
     * @param decryptor the decryptor the site calls
     * @return a success result with the decrypted text and timing, or a failure result with the reason; never throws
     */
    public DeobfuscationResult decrypt(ClassFile classFile, CallSites.CallSite site, DecryptorCandidate decryptor)
    {
        DeobfuscationResult result = DeobfuscationResult.forCallSite(classFile.getClassName(), site, stringArgumentIndex(classFile, site, decryptor));
        if (!site.isResolved())
        {
            result.setErrorMessage("Arguments are not constants: " + site.getUnresolvedReason());
            return result;
        }
        long startTime = System.currentTimeMillis();
        try
        {
            String decrypted = executeDecryptor(decryptor.getMethod(), site.getArguments());
            if (decrypted == null)
            {
                result.setErrorMessage("Decryptor returned null");
                return result;
            }
            result.setDecryptedValue(decrypted);
            result.setDecryptorUsed(decryptor.getMethod());
            result.setSuccess(true);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        }
        catch (RuntimeException e)
        {
            result.setErrorMessage(e.getMessage());
        }
        return result;
    }

    private static int stringArgumentIndex(ClassFile classFile, CallSites.CallSite site, DecryptorCandidate decryptor)
    {
        if (!site.isResolved() || !decryptor.getMethod().getDesc().startsWith("(Ljava/lang/String;)"))
        {
            return -1;
        }
        Object argument = site.getArguments()[0];
        if (!(argument instanceof String))
        {
            return -1;
        }
        List<Item<?>> items = classFile.getConstPool().getItems();
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i) instanceof StringRefItem)
            {
                int utf8 = ((StringRefItem) items.get(i)).getValue();
                if (utf8 > 0 && utf8 < items.size() && items.get(utf8) instanceof Utf8Item && argument.equals(((Utf8Item) items.get(utf8)).getValue()))
                {
                    return i;
                }
            }
        }
        return -1;
    }
}
