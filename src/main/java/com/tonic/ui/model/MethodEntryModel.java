package com.tonic.ui.model;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.simulation.metrics.ComplexityMetrics;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.util.AccessFlags;
import com.tonic.ui.util.DescriptorParser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import javax.swing.Icon;

@Getter
public class MethodEntryModel {

    private final MethodEntry methodEntry;
    private final ClassEntryModel owner;

    // UI state
    @Setter
    private boolean selected;
    @Setter
    private boolean bookmarked;
    @Setter
    private String userNotes;

    // Analysis state
    @Setter
    private AnalysisState analysisState = AnalysisState.NOT_ANALYZED;
    private IRMethod cachedIR;
    private long irCacheTimestamp;
    private String irStringCache;
    @Getter(AccessLevel.NONE)
    private ComplexityMetrics complexityMetrics;

    // Display data
    private String displaySignature;
    private Icon icon;

    public MethodEntryModel(MethodEntry methodEntry, ClassEntryModel owner) {
        this.methodEntry = methodEntry;
        this.owner = owner;
        buildDisplayData();
    }

    private void buildDisplayData() {
        this.displaySignature = methodEntry.getName() + "(" +
            DescriptorParser.formatMethodParams(methodEntry.getDesc()) + ")";

        int access = methodEntry.getAccess();
        if (AccessFlags.isPublic(access)) {
            this.icon = Icons.getIcon("method_public");
        } else if (AccessFlags.isPrivate(access)) {
            this.icon = Icons.getIcon("method_private");
        } else if (AccessFlags.isProtected(access)) {
            this.icon = Icons.getIcon("method_protected");
        } else {
            this.icon = Icons.getIcon("method_package");
        }
    }

    // MethodEntry delegated methods

    public String getName() {
        return methodEntry.getName();
    }

    public String getDescriptor() {
        return methodEntry.getDesc();
    }

    public int getAccessFlags() {
        return methodEntry.getAccess();
    }

    public boolean isStatic() {
        return AccessFlags.isStatic(methodEntry.getAccess());
    }

    public boolean isAbstract() {
        return AccessFlags.isAbstract(methodEntry.getAccess());
    }

    public boolean isNative() {
        return AccessFlags.isNative(methodEntry.getAccess());
    }

    public boolean isSynchronized() {
        return AccessFlags.isSynchronized(methodEntry.getAccess());
    }

    public boolean isFinal() {
        return AccessFlags.isFinal(methodEntry.getAccess());
    }

    public boolean isPublic() {
        return AccessFlags.isPublic(methodEntry.getAccess());
    }

    public boolean isPrivate() {
        return AccessFlags.isPrivate(methodEntry.getAccess());
    }

    public boolean isProtected() {
        return AccessFlags.isProtected(methodEntry.getAccess());
    }

    public boolean hasCode() {
        return methodEntry.getCodeAttribute() != null;
    }

    public boolean isConstructor() {
        return "<init>".equals(methodEntry.getName());
    }

    public boolean isStaticInitializer() {
        return "<clinit>".equals(methodEntry.getName());
    }

    public void setCachedIR(IRMethod cachedIR) {
        this.cachedIR = cachedIR;
        this.irCacheTimestamp = System.currentTimeMillis();
        this.complexityMetrics = null;
    }

    public void invalidateIRCache() {
        this.cachedIR = null;
        this.irCacheTimestamp = 0;
        this.irStringCache = null;
        this.complexityMetrics = null;
        this.analysisState = AnalysisState.NOT_ANALYZED;
    }

    public String getIrCache() {
        return irStringCache;
    }

    public void setIrCache(String irString) {
        this.irStringCache = irString;
    }

    public ComplexityMetrics getComplexityMetrics() {
        if (complexityMetrics == null && cachedIR != null) {
            complexityMetrics = new ComplexityMetrics(cachedIR);
        }
        return complexityMetrics;
    }

    public ComplexityMetrics computeComplexityMetrics() {
        if (complexityMetrics != null) {
            return complexityMetrics;
        }
        if (cachedIR == null && methodEntry.getCodeAttribute() != null) {
            try {
                SSA ssa = new SSA(owner.getClassFile().getConstPool());
                cachedIR = ssa.lift(methodEntry);
                irCacheTimestamp = System.currentTimeMillis();
            } catch (Exception e) {
                return null;
            }
        }
        if (cachedIR != null) {
            complexityMetrics = new ComplexityMetrics(cachedIR);
        }
        return complexityMetrics;
    }

    @Override
    public String toString() {
        return displaySignature;
    }

    /**
     * Analysis state for a method.
     */
    public enum AnalysisState {
        NOT_ANALYZED,
        IR_LIFTED,
        DECOMPILED,
        TRANSFORMED,
        ERROR
    }
}
