package com.tonic.model;

import com.tonic.analysis.source.decompile.DecompileResult;

import java.util.Map;
import java.util.NavigableMap;

/** Pure helpers translating between bytecode offsets and decompiled-source lines using the per-method maps the decompiler produces. */
public final class SourceLineMaps
{

    private SourceLineMaps()
    {
    }

    /**
     * Maps a bytecode offset to its source line, preferring the next mapped offset at or after it and falling back to the one before.
     *
     * @param offsetToLine the method's map from bytecode offset to 1-based source line
     * @param pc the bytecode offset
     * @return the 1-based source line, or -1 when the map is null or empty
     */
    public static int sourceLineForPc(NavigableMap<Integer, Integer> offsetToLine, int pc)
    {
        if (offsetToLine == null || offsetToLine.isEmpty())
        {
            return -1;
        }
        Map.Entry<Integer, Integer> ceiling = offsetToLine.ceilingEntry(pc);
        if (ceiling != null)
        {
            return ceiling.getValue();
        }
        Map.Entry<Integer, Integer> floor = offsetToLine.floorEntry(pc);
        return floor != null ? floor.getValue() : -1;
    }

    /**
     * Finds the bytecode offsets a source line owns: from just after the previous line's last anchor through this line's last anchor.
     *
     * @param offsetToLine the method's map from bytecode offset to 1-based source line
     * @param oneBasedLine the 1-based source line
     * @return the inclusive span as a two-element array of low and high offsets, or null when no offset maps to the line
     */
    public static int[] pcSpanForSourceLine(NavigableMap<Integer, Integer> offsetToLine, int oneBasedLine)
    {
        if (offsetToLine == null || offsetToLine.isEmpty())
        {
            return null;
        }
        Integer firstForLine = null;
        Integer lastForLine = null;
        for (Map.Entry<Integer, Integer> entry : offsetToLine.entrySet())
        {
            if (entry.getValue() != null && entry.getValue() == oneBasedLine)
            {
                if (firstForLine == null)
                {
                    firstForLine = entry.getKey();
                }
                lastForLine = entry.getKey();
            }
        }
        if (firstForLine == null)
        {
            return null;
        }
        Integer previousAnchor = offsetToLine.lowerKey(firstForLine);
        int lo = previousAnchor != null ? previousAnchor + 1 : 0;
        return new int[]{lo, lastForLine};
    }

    /**
     * Finds the method whose source span contains a line, the innermost one when spans nest.
     *
     * @param methodSpans the method spans, keyed by name plus descriptor
     * @param oneBasedLine the 1-based source line
     * @return the method's name plus descriptor key, or null when the line is outside every method or the map is null
     */
    public static String methodKeyForSourceLine(Map<String, DecompileResult.MethodSpan> methodSpans, int oneBasedLine)
    {
        if (methodSpans == null)
        {
            return null;
        }
        String best = null;
        int bestStart = Integer.MIN_VALUE;
        for (Map.Entry<String, DecompileResult.MethodSpan> entry : methodSpans.entrySet())
        {
            DecompileResult.MethodSpan span = entry.getValue();
            if (span != null && span.contains(oneBasedLine) && span.getStartLine() > bestStart)
            {
                best = entry.getKey();
                bestStart = span.getStartLine();
            }
        }
        return best;
    }
}
