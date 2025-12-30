package com.tonic.ui.model;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.theme.Icons;
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
    private String irStringCache;  // Cached formatted IR string

    // Display data
    private String displaySignature;
    private Icon icon;

    public MethodEntryModel(MethodEntry methodEntry, ClassEntryModel owner) {
        this.methodEntry = methodEntry;
        this.owner = owner;
        buildDisplayData();
    }

    private void buildDisplayData() {
        // Build a readable signature
        StringBuilder sb = new StringBuilder();
        sb.append(methodEntry.getName());

        // Parse descriptor to show parameter types
        String desc = methodEntry.getDesc();
        sb.append("(");
        int paramStart = desc.indexOf('(') + 1;
        int paramEnd = desc.indexOf(')');
        if (paramStart < paramEnd) {
            String params = desc.substring(paramStart, paramEnd);
            sb.append(formatParams(params));
        }
        sb.append(")");

        this.displaySignature = sb.toString();

        // Determine icon based on access
        int access = methodEntry.getAccess();
        if ((access & 0x0001) != 0) {        // public
            this.icon = Icons.getIcon("method_public");
        } else if ((access & 0x0002) != 0) { // private
            this.icon = Icons.getIcon("method_private");
        } else if ((access & 0x0004) != 0) { // protected
            this.icon = Icons.getIcon("method_protected");
        } else {                              // package-private
            this.icon = Icons.getIcon("method_package");
        }
    }

    private String formatParams(String params) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        boolean first = true;
        while (i < params.length()) {
            if (!first) result.append(", ");
            first = false;

            char c = params.charAt(i);
            switch (c) {
                case 'B': result.append("byte"); i++; break;
                case 'C': result.append("char"); i++; break;
                case 'D': result.append("double"); i++; break;
                case 'F': result.append("float"); i++; break;
                case 'I': result.append("int"); i++; break;
                case 'J': result.append("long"); i++; break;
                case 'S': result.append("short"); i++; break;
                case 'Z': result.append("boolean"); i++; break;
                case 'V': result.append("void"); i++; break;
                case '[':
                    int arrayDim = 0;
                    while (i < params.length() && params.charAt(i) == '[') {
                        arrayDim++;
                        i++;
                    }
                    if (i < params.length()) {
                        String elem = formatParams(params.substring(i, i + 1));
                        if (params.charAt(i) == 'L') {
                            int semi = params.indexOf(';', i);
                            elem = formatClassName(params.substring(i + 1, semi));
                            i = semi + 1;
                        } else {
                            i++;
                        }
                        result.append(elem);
                        for (int d = 0; d < arrayDim; d++) {
                            result.append("[]");
                        }
                    }
                    break;
                case 'L':
                    int semicolon = params.indexOf(';', i);
                    if (semicolon > i) {
                        result.append(formatClassName(params.substring(i + 1, semicolon)));
                        i = semicolon + 1;
                    } else {
                        i++;
                    }
                    break;
                default:
                    i++;
                    break;
            }
        }
        return result.toString();
    }

    private String formatClassName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash >= 0) {
            return internalName.substring(lastSlash + 1);
        }
        return internalName;
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
        return (methodEntry.getAccess() & 0x0008) != 0;
    }

    public boolean isAbstract() {
        return (methodEntry.getAccess() & 0x0400) != 0;
    }

    public boolean isNative() {
        return (methodEntry.getAccess() & 0x0100) != 0;
    }

    public boolean isSynchronized() {
        return (methodEntry.getAccess() & 0x0020) != 0;
    }

    public boolean isFinal() {
        return (methodEntry.getAccess() & 0x0010) != 0;
    }

    public boolean isPublic() {
        return (methodEntry.getAccess() & 0x0001) != 0;
    }

    public boolean isPrivate() {
        return (methodEntry.getAccess() & 0x0002) != 0;
    }

    public boolean isProtected() {
        return (methodEntry.getAccess() & 0x0004) != 0;
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
    }

    public void invalidateIRCache() {
        this.cachedIR = null;
        this.irCacheTimestamp = 0;
        this.irStringCache = null;
        this.analysisState = AnalysisState.NOT_ANALYZED;
    }

    public String getIrCache() {
        return irStringCache;
    }

    public void setIrCache(String irString) {
        this.irStringCache = irString;
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
