package com.tonic.model;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.MethodEntry;
import com.tonic.simulation.metrics.ComplexityMetrics;
import com.tonic.util.AccessFlags;
import com.tonic.util.DescriptorParser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** The UI-side model of one method: its parsed entry, owning class, display signature, analysis state and cached IR, IR text, LLVM text and complexity metrics. */
@Getter
public class MethodEntryModel
{

    private final MethodEntry methodEntry;
    private final ClassEntryModel owner;

    @Setter
    private boolean selected;
    @Setter
    private boolean bookmarked;
    @Setter
    private String userNotes;

    @Setter
    private AnalysisState analysisState = AnalysisState.NOT_ANALYZED;
    private IRMethod cachedIR;
    private long irCacheTimestamp;
    private String irStringCache;
    private String llvmStringCache;
    @Getter(AccessLevel.NONE)
    private ComplexityMetrics complexityMetrics;

    private String displaySignature;
    private String iconKey;

    /**
     * Wraps a parsed method and builds its display signature and icon.
     *
     * @param methodEntry the parsed method
     * @param owner the model of the declaring class
     */
    public MethodEntryModel(MethodEntry methodEntry, ClassEntryModel owner)
    {
        this.methodEntry = methodEntry;
        this.owner = owner;
        buildDisplayData();
    }

    private void buildDisplayData()
    {
        this.displaySignature = methodEntry.getName() + "(" +
                DescriptorParser.formatMethodParams(methodEntry.getDesc()) + ")";

        int access = methodEntry.getAccess();
        if (AccessFlags.isPublic(access))
        {
            this.iconKey = "method_public";
        }
        else if (AccessFlags.isPrivate(access))
        {
            this.iconKey = "method_private";
        }
        else if (AccessFlags.isProtected(access))
        {
            this.iconKey = "method_protected";
        }
        else
        {
            this.iconKey = "method_package";
        }
    }

    /**
     * The method's name.
     *
     * @return the method name
     */
    public String getName()
    {
        return methodEntry.getName();
    }

    /**
     * The method's descriptor.
     *
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return methodEntry.getDesc();
    }

    /**
     * The method's access flags.
     *
     * @return the raw access flags
     */
    public int getAccessFlags()
    {
        return methodEntry.getAccess();
    }

    /**
     * Reports whether the method is static.
     *
     * @return true if the static flag is set
     */
    public boolean isStatic()
    {
        return AccessFlags.isStatic(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is abstract.
     *
     * @return true if the abstract flag is set
     */
    public boolean isAbstract()
    {
        return AccessFlags.isAbstract(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is native.
     *
     * @return true if the native flag is set
     */
    public boolean isNative()
    {
        return AccessFlags.isNative(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is synchronized.
     *
     * @return true if the synchronized flag is set
     */
    public boolean isSynchronized()
    {
        return AccessFlags.isSynchronized(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is final.
     *
     * @return true if the final flag is set
     */
    public boolean isFinal()
    {
        return AccessFlags.isFinal(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is public.
     *
     * @return true if the public flag is set
     */
    public boolean isPublic()
    {
        return AccessFlags.isPublic(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is private.
     *
     * @return true if the private flag is set
     */
    public boolean isPrivate()
    {
        return AccessFlags.isPrivate(methodEntry.getAccess());
    }

    /**
     * Reports whether the method is protected.
     *
     * @return true if the protected flag is set
     */
    public boolean isProtected()
    {
        return AccessFlags.isProtected(methodEntry.getAccess());
    }

    /**
     * Reports whether the method has a body.
     *
     * @return true if it has a code attribute
     */
    public boolean hasCode()
    {
        return methodEntry.getCodeAttribute() != null;
    }

    /**
     * Reports whether the method is a constructor.
     *
     * @return true if it is named init
     */
    public boolean isConstructor()
    {
        return "<init>".equals(methodEntry.getName());
    }

    /**
     * Reports whether the method is the static initializer.
     *
     * @return true if it is named clinit
     */
    public boolean isStaticInitializer()
    {
        return "<clinit>".equals(methodEntry.getName());
    }

    /**
     * Caches the lifted IR, stamping the time and dropping the complexity metrics derived from the old IR.
     *
     * @param cachedIR the lifted method
     */
    public void setCachedIR(IRMethod cachedIR)
    {
        this.cachedIR = cachedIR;
        this.irCacheTimestamp = System.currentTimeMillis();
        this.complexityMetrics = null;
    }

    /** Drops the cached IR, IR text, LLVM text and metrics, and resets the analysis state to not analyzed. */
    public void invalidateIRCache()
    {
        this.cachedIR = null;
        this.irCacheTimestamp = 0;
        this.irStringCache = null;
        this.llvmStringCache = null;
        this.complexityMetrics = null;
        this.analysisState = AnalysisState.NOT_ANALYZED;
    }

    /** @return the cached IR text, or null if none */
    public String getIrCache()
    {
        return irStringCache;
    }

    /**
     * Caches the rendered IR text.
     *
     * @param irString the IR text
     */
    public void setIrCache(String irString)
    {
        this.irStringCache = irString;
    }

    /** @return the cached LLVM text, or null if none */
    public String getLlvmCache()
    {
        return llvmStringCache;
    }

    /**
     * Caches the rendered LLVM text.
     *
     * @param llvmString the LLVM text
     */
    public void setLlvmCache(String llvmString)
    {
        this.llvmStringCache = llvmString;
    }

    /**
     * Computes the complexity metrics from the cached IR on first call and caches them.
     *
     * @return the metrics, or null if no IR is cached
     */
    public ComplexityMetrics getComplexityMetrics()
    {
        if (complexityMetrics == null && cachedIR != null)
        {
            complexityMetrics = new ComplexityMetrics(cachedIR);
        }
        return complexityMetrics;
    }

    @Override
    public String toString()
    {
        return displaySignature;
    }

    /**
     * Analysis state for a method.
     */
    public enum AnalysisState
    {
        NOT_ANALYZED,
        IR_LIFTED,
        DECOMPILED,
        TRANSFORMED,
        ERROR
    }
}
