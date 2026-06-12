package com.tonic.ui.editor.dual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeLineIndexTest {

    /**
     * Mirrors the {@code BytecodeView}/{@code BytecodeFormatter} layout: structural comments and the
     * method header sit at column 0, while disassembly lines (instructions and every verbose comment)
     * carry the formatter's two-space indent. Exercises line numbers, frames, signature, exception
     * table, an inline local-variable comment, and a tableswitch with continuation lines.
     */
    private static final String SAMPLE =
            "// Class: com/example/Foo\n" +
            "// Super: java/lang/Object\n" +
            "\n" +
            "// =====\n" +
            "// Method 1 of 2\n" +
            "// =====\n" +
            "\n" +
            "// public foo(I)V\n" +
            "  // signature: foo(n: I)\n" +
            "  // max_stack = 2, max_locals = 2\n" +
            "  // line 10\n" +
            "  0000: iload             1  // n: I\n" +
            "  0001: ifle              0008\n" +
            "  // line 11\n" +
            "  0004: iconst_1\n" +
            "  0005: ireturn\n" +
            "  // line 13\n" +
            "  0008: tableswitch       default=0020, low=1, high=1, count=1\n" +
            "          case[1] => offset 0020\n" +
            "  0020: iconst_0\n" +
            "  0021: ireturn\n" +
            "  // Exception table:\n" +
            "  //   from     to       target   type\n" +
            "  //   0        4        8        any\n" +
            "\n" +
            "// =====\n" +
            "// Method 2 of 2\n" +
            "// =====\n" +
            "\n" +
            "// static <clinit>()V\n" +
            "  // line 20\n" +
            "  0000: return\n";

    private static String[] lines() {
        return SAMPLE.split("\n", -1);
    }

    private static int lineContaining(String needle) {
        String[] lines = lines();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i;
            }
        }
        throw new AssertionError("not found: " + needle);
    }

    @Test
    void mapsInstructionLinesToOffsetsAndMethods() {
        BytecodeLineIndex index = BytecodeLineIndex.parse(SAMPLE);

        BcLocation iload = index.locationAtLine(lineContaining("iload"));
        assertNotNull(iload);
        assertEquals("foo", iload.getMethodName());
        assertEquals("(I)V", iload.getMethodDesc());
        assertEquals(0, iload.getPc());

        BcLocation ifle = index.locationAtLine(lineContaining("ifle"));
        assertNotNull(ifle);
        assertEquals(1, ifle.getPc());

        BcLocation tableswitch = index.locationAtLine(lineContaining("tableswitch"));
        assertNotNull(tableswitch);
        assertEquals(8, tableswitch.getPc());
    }

    @Test
    void mapsSecondMethodWithSpecialName() {
        BytecodeLineIndex index = BytecodeLineIndex.parse(SAMPLE);
        BcLocation ret = index.locationAtLine(lineContaining("0000: return"));
        assertNotNull(ret);
        assertEquals("<clinit>", ret.getMethodName());
        assertEquals("()V", ret.getMethodDesc());
        assertEquals(0, ret.getPc());
    }

    @Test
    void verboseExtrasAreNotInstructions() {
        BytecodeLineIndex index = BytecodeLineIndex.parse(SAMPLE);
        assertNull(index.locationAtLine(lineContaining("// line 10")));
        assertNull(index.locationAtLine(lineContaining("// signature")));
        assertNull(index.locationAtLine(lineContaining("// public foo")));
        assertNull(index.locationAtLine(lineContaining("Exception table")));
        assertNull(index.locationAtLine(lineContaining("from     to")));
        assertNull(index.locationAtLine(lineContaining("0        4        8")));
        assertNull(index.locationAtLine(lineContaining("case[1]")));
    }

    @Test
    void displayLinesForPcRangeReturnsInstructionLines() {
        BytecodeLineIndex index = BytecodeLineIndex.parse(SAMPLE);
        List<Integer> rangeLines = index.displayLinesForPcRange("foo(I)V", 0, 1);
        assertEquals(List.of(lineContaining("iload"), lineContaining("ifle")), rangeLines);

        assertTrue(index.displayLinesForPcRange("missing()V", 0, 99).isEmpty());
        assertTrue(index.displayLinesForPcRange("foo(I)V", 5, 1).isEmpty());
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(BytecodeLineIndex.parse(null).locationAtLine(0));
        assertNull(BytecodeLineIndex.parse("").locationAtLine(0));
        assertTrue(BytecodeLineIndex.parse("").displayLinesForPcRange("x()V", 0, 9).isEmpty());
    }
}
