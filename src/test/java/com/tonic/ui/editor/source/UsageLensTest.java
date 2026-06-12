package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.event.events.FindUsagesEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageLensTest {

    private static final String[] SOURCE = {
            "package com.example;",          // line 1
            "",                              // line 2 (blank above class)
            "public class Foo {",            // line 3
            "    private int x;",            // line 4 (field, no blank line above)
            "",                              // line 5 (blank above method)
            "    public void bar() {",       // line 6
            "    }",                         // line 7
            "}"                              // line 8
    };

    private static DecompileResult.MemberSpan span(int startLine) {
        DecompileResult.MemberSpan span = mock(DecompileResult.MemberSpan.class);
        when(span.getStartLine()).thenReturn(startLine);
        return span;
    }

    private static UsageLens.LensTarget target(FindUsagesEvent.TargetType type, String name,
                                               String desc, int startLine, int count) {
        return new UsageLens.LensTarget(type, name, desc, span(startLine), count);
    }

    @Test
    void fieldWithoutBlankAboveUsesEndOfLine() {
        List<UsageLens.LensEntry> entries = UsageLens.compute(SOURCE,
                List.of(target(FindUsagesEvent.TargetType.FIELD, "x", "I", 4, 2)));

        assertEquals(1, entries.size());
        UsageLens.LensEntry e = entries.get(0);
        assertTrue(e.endOfLine);
        assertEquals(3, e.anchorLine);
        assertEquals(3, e.declarationLine);
        assertEquals(FindUsagesEvent.TargetType.FIELD, e.targetType);
        assertEquals("x", e.memberName);
        assertEquals("I", e.memberDescriptor);
        assertEquals("2 usages", e.text);
    }

    @Test
    void classWithBlankAboveUsesAboveLine() {
        List<UsageLens.LensEntry> entries = UsageLens.compute(SOURCE,
                List.of(target(FindUsagesEvent.TargetType.CLASS, "com/example/Foo", null, 3, 5)));

        assertEquals(1, entries.size());
        UsageLens.LensEntry e = entries.get(0);
        assertFalse(e.endOfLine);
        assertEquals(1, e.anchorLine);
        assertEquals(2, e.declarationLine);
        assertEquals(FindUsagesEvent.TargetType.CLASS, e.targetType);
        assertEquals("5 usages", e.text);
    }

    @Test
    void mixedTargetsSortedByAnchorLine() {
        List<UsageLens.LensEntry> entries = UsageLens.compute(SOURCE, List.of(
                target(FindUsagesEvent.TargetType.METHOD, "bar", "()V", 6, 1),
                target(FindUsagesEvent.TargetType.CLASS, "com/example/Foo", null, 3, 5),
                target(FindUsagesEvent.TargetType.FIELD, "x", "I", 4, 2)));

        assertEquals(3, entries.size());
        assertEquals(FindUsagesEvent.TargetType.CLASS, entries.get(0).targetType);
        assertEquals(FindUsagesEvent.TargetType.FIELD, entries.get(1).targetType);
        assertEquals(FindUsagesEvent.TargetType.METHOD, entries.get(2).targetType);
    }

    @Test
    void pluralizesAndHandlesZero() {
        assertEquals("1 usage", UsageLens.compute(SOURCE,
                List.of(target(FindUsagesEvent.TargetType.METHOD, "bar", "()V", 6, 1))).get(0).text);
        assertEquals("no usages", UsageLens.compute(SOURCE,
                List.of(target(FindUsagesEvent.TargetType.METHOD, "bar", "()V", 6, 0))).get(0).text);
    }

    @Test
    void skipsNullSpanAndOutOfRange() {
        UsageLens.LensTarget noSpan =
                new UsageLens.LensTarget(FindUsagesEvent.TargetType.METHOD, "bar", "()V", null, 3);
        assertTrue(UsageLens.compute(SOURCE, List.of(noSpan)).isEmpty());
        assertTrue(UsageLens.compute(SOURCE,
                List.of(target(FindUsagesEvent.TargetType.METHOD, "bar", "()V", 99, 1))).isEmpty());
    }

    @Test
    void handlesNullInputs() {
        assertTrue(UsageLens.compute(null, List.of()).isEmpty());
        assertTrue(UsageLens.compute(SOURCE, null).isEmpty());
    }
}
