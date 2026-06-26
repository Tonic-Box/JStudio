package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.debug.Breakpoint;
import com.tonic.ui.debug.BreakpointMapper;

import java.util.Map;
import java.util.NavigableMap;

/**
 * Maps the decompiled source view's lines to breakpoints via the decompiler's per-method offset-to-line maps:
 * a clicked line resolves to the lowest bytecode offset emitted onto it, and a breakpoint resolves back to the
 * line its offset maps to.
 */
final class SourceBreakpointMapper implements BreakpointMapper {

    private final ClassEntryModel classEntry;

    SourceBreakpointMapper(ClassEntryModel classEntry) {
        this.classEntry = classEntry;
    }

    @Override
    public String className() {
        return classEntry.getClassName().replace('/', '.');
    }

    /**
     * The per-method offset-to-line maps for this class, regenerating them from current bytecode if they were
     * dropped (e.g. a cache invalidation) while a tab keeps showing its source - so breakpoints resolve without
     * forcing a reopen. The bytecode is unchanged, so the regenerated maps match the displayed source.
     */
    private Map<String, NavigableMap<Integer, Integer>> lineMaps() {
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        if (maps == null) {
            try {
                DecompileResult r = new ClassDecompiler(classEntry.getClassFile()).decompileWithLineMap();
                classEntry.setDecompilationCache(r.getSource(), r.getLineMaps(), r.getMethodSpans(),
                        r.getFieldSpans(), r.getClassSpan());
                maps = classEntry.getSourceLineMaps();
            } catch (Exception ignored) {
            }
        }
        return maps;
    }

    @Override
    public Breakpoint breakpointAtLine(int line) {
        Map<String, NavigableMap<Integer, Integer>> maps = lineMaps();
        if (maps == null) {
            return null;
        }
        String bestKey = null;
        int bestOffset = Integer.MAX_VALUE;
        for (Map.Entry<String, NavigableMap<Integer, Integer>> method : maps.entrySet()) {
            for (Map.Entry<Integer, Integer> e : method.getValue().entrySet()) {
                if (e.getValue() == line && e.getKey() < bestOffset) {
                    bestOffset = e.getKey();
                    bestKey = method.getKey();
                }
            }
        }
        if (bestKey == null) {
            return null;
        }
        int paren = bestKey.indexOf('(');
        if (paren <= 0) {
            return null;
        }
        return new Breakpoint(className(), bestKey.substring(0, paren), bestKey.substring(paren), bestOffset);
    }

    @Override
    public int lineForBreakpoint(Breakpoint bp) {
        Map<String, NavigableMap<Integer, Integer>> maps = lineMaps();
        if (maps == null) {
            return -1;
        }
        NavigableMap<Integer, Integer> m = maps.get(bp.methodName + bp.methodDesc);
        if (m == null) {
            return -1;
        }
        Integer line = m.get((int) bp.pc);
        if (line == null) {
            Map.Entry<Integer, Integer> floor = m.floorEntry((int) bp.pc);
            line = floor != null ? floor.getValue() : null;
        }
        return line == null ? -1 : line;
    }
}
