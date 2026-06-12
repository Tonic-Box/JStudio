package com.tonic.model;

import com.tonic.analysis.source.decompile.DecompileResult;

import java.util.Map;
import java.util.NavigableMap;

/**
 * Pure helpers translating between bytecode offsets and decompiled-source line numbers using the
 * per-method maps the decompiler produces ({@link ClassEntryModel#getSourceLineMaps()} and
 * {@link ClassEntryModel#getMethodSpans()}). Centralizing the math here keeps the source view, the
 * dual view, and any future consumer on one implementation rather than duplicating ceiling/floor
 * and inversion logic per call site.
 */
public final class SourceLineMaps {

    private SourceLineMaps() {
    }

    /**
     * The 1-based source line for a bytecode offset, or -1 when the map is null or empty. Prefers
     * the ceiling entry (an inlined expression is emitted by its later-offset consumer statement)
     * and falls back to the floor entry.
     */
    public static int sourceLineForPc(NavigableMap<Integer, Integer> offsetToLine, int pc) {
        if (offsetToLine == null || offsetToLine.isEmpty()) {
            return -1;
        }
        Map.Entry<Integer, Integer> ceiling = offsetToLine.ceilingEntry(pc);
        if (ceiling != null) {
            return ceiling.getValue();
        }
        Map.Entry<Integer, Integer> floor = offsetToLine.floorEntry(pc);
        return floor != null ? floor.getValue() : -1;
    }

    /**
     * The inclusive bytecode-offset span {@code [lo, hi]} a 1-based source line owns, or null when
     * no offset maps to that line. A statement is anchored at its <em>defining</em> instruction (e.g.
     * the {@code ireturn} of {@code return f(x)}), so the instructions that evaluate its sub-expressions
     * sit before that anchor: the span runs from just after the previous statement's anchor up to and
     * including this line's last anchor. This mirrors the ceiling attribution of {@link #sourceLineForPc}
     * — every offset in {@code [lo, hi]} resolves back to this line — so the two directions stay consistent.
     */
    public static int[] pcSpanForSourceLine(NavigableMap<Integer, Integer> offsetToLine, int oneBasedLine) {
        if (offsetToLine == null || offsetToLine.isEmpty()) {
            return null;
        }
        Integer firstForLine = null;
        Integer lastForLine = null;
        for (Map.Entry<Integer, Integer> entry : offsetToLine.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == oneBasedLine) {
                if (firstForLine == null) {
                    firstForLine = entry.getKey();
                }
                lastForLine = entry.getKey();
            }
        }
        if (firstForLine == null) {
            return null;
        }
        Integer previousAnchor = offsetToLine.lowerKey(firstForLine);
        int lo = previousAnchor != null ? previousAnchor + 1 : 0;
        return new int[]{lo, lastForLine};
    }

    /**
     * The {@code name + desc} key of the method whose source span contains the given 1-based line,
     * or null when the line falls outside every method (class header, fields, blank lines). The
     * innermost containing span wins when spans overlap.
     */
    public static String methodKeyForSourceLine(Map<String, DecompileResult.MethodSpan> methodSpans,
                                                int oneBasedLine) {
        if (methodSpans == null) {
            return null;
        }
        String best = null;
        int bestStart = Integer.MIN_VALUE;
        for (Map.Entry<String, DecompileResult.MethodSpan> entry : methodSpans.entrySet()) {
            DecompileResult.MethodSpan span = entry.getValue();
            if (span != null && span.contains(oneBasedLine) && span.getStartLine() > bestStart) {
                best = entry.getKey();
                bestStart = span.getStartLine();
            }
        }
        return best;
    }
}
