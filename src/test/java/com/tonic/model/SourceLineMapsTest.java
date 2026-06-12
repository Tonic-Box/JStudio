package com.tonic.model;

import com.tonic.analysis.source.decompile.DecompileResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class SourceLineMapsTest {

    private static NavigableMap<Integer, Integer> map(int... offsetLinePairs) {
        NavigableMap<Integer, Integer> m = new TreeMap<>();
        for (int i = 0; i < offsetLinePairs.length; i += 2) {
            m.put(offsetLinePairs[i], offsetLinePairs[i + 1]);
        }
        return m;
    }

    @Test
    void sourceLineForPcPrefersCeiling() {
        NavigableMap<Integer, Integer> m = map(0, 10, 5, 11, 12, 13);
        assertEquals(11, SourceLineMaps.sourceLineForPc(m, 5));
        assertEquals(11, SourceLineMaps.sourceLineForPc(m, 3));
        assertEquals(10, SourceLineMaps.sourceLineForPc(m, 0));
    }

    @Test
    void sourceLineForPcFallsBackToFloorPastLastOffset() {
        NavigableMap<Integer, Integer> m = map(0, 10, 5, 11);
        assertEquals(11, SourceLineMaps.sourceLineForPc(m, 99));
    }

    @Test
    void sourceLineForPcHandlesNullAndEmpty() {
        assertEquals(-1, SourceLineMaps.sourceLineForPc(null, 0));
        assertEquals(-1, SourceLineMaps.sourceLineForPc(new TreeMap<>(), 0));
    }

    @Test
    void pcSpanForSourceLineExtendsBackToPreviousAnchor() {
        // line 11 is anchored at 5 and 8; the previous anchor (line 10) is at 0, so line 11 owns the
        // sub-expression instructions from offset 1 up to its last anchor at 8.
        NavigableMap<Integer, Integer> m = map(0, 10, 5, 11, 8, 11, 12, 13);
        assertArrayEquals(new int[]{1, 8}, SourceLineMaps.pcSpanForSourceLine(m, 11));
    }

    @Test
    void pcSpanForSourceLineCoversInstructionsBeforeTheAnchor() {
        // A `return f(x)` line whose only anchor is the trailing ireturn at 8; its argument-eval and
        // invoke instructions (after the previous anchor at 2) must be included, not just offset 8.
        NavigableMap<Integer, Integer> m = map(0, 10, 2, 10, 8, 11);
        assertArrayEquals(new int[]{3, 8}, SourceLineMaps.pcSpanForSourceLine(m, 11));
    }

    @Test
    void pcSpanForSourceLineStartsAtZeroForFirstLine() {
        NavigableMap<Integer, Integer> m = map(3, 10, 9, 11);
        assertArrayEquals(new int[]{0, 3}, SourceLineMaps.pcSpanForSourceLine(m, 10));
    }

    @Test
    void pcSpanForSourceLineReturnsNullForUnmappedLine() {
        NavigableMap<Integer, Integer> m = map(0, 10, 5, 11);
        assertNull(SourceLineMaps.pcSpanForSourceLine(m, 42));
        assertNull(SourceLineMaps.pcSpanForSourceLine(null, 11));
    }

    @Test
    void methodKeyForSourceLineFindsContainingSpan() {
        Map<String, DecompileResult.MethodSpan> spans = new LinkedHashMap<>();
        spans.put("a()V", span(5, 10));
        spans.put("b()V", span(12, 20));

        assertEquals("a()V", SourceLineMaps.methodKeyForSourceLine(spans, 7));
        assertEquals("b()V", SourceLineMaps.methodKeyForSourceLine(spans, 12));
        assertNull(SourceLineMaps.methodKeyForSourceLine(spans, 11));
        assertNull(SourceLineMaps.methodKeyForSourceLine(spans, 99));
        assertNull(SourceLineMaps.methodKeyForSourceLine(null, 7));
    }

    @Test
    void methodKeyForSourceLinePicksInnermostOnOverlap() {
        Map<String, DecompileResult.MethodSpan> spans = new LinkedHashMap<>();
        spans.put("outer()V", span(5, 30));
        spans.put("inner()V", span(10, 20));
        assertEquals("inner()V", SourceLineMaps.methodKeyForSourceLine(spans, 15));
    }

    private static DecompileResult.MethodSpan span(int start, int end) {
        DecompileResult.MethodSpan span = mock(DecompileResult.MethodSpan.class);
        when(span.getStartLine()).thenReturn(start);
        when(span.getEndLine()).thenReturn(end);
        when(span.contains(anyInt())).thenAnswer(inv -> {
            int line = inv.getArgument(0);
            return line >= start && line <= end;
        });
        return span;
    }
}
