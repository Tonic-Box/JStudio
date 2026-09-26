package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.model.ClassEntryModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceAssemblerTest
{

    private static final String SOURCE = String.join("\n", "package x;", "public class C {", "  void foo() {", "    a();", "  }", "", "  int bar() {", "    return b;", "  }", "}");

    private static DecompileResult.MethodSpan span(int start, int end)
    {
        DecompileResult.MethodSpan s = mock(DecompileResult.MethodSpan.class);
        when(s.getStartLine()).thenReturn(start);
        when(s.getEndLine()).thenReturn(end);
        return s;
    }

    private static ClassEntryModel classWithSpans()
    {
        Map<String, DecompileResult.MethodSpan> spans = new HashMap<>();
        spans.put("foo()V", span(3, 5));
        spans.put("bar()I", span(7, 9));
        ClassEntryModel cls = mock(ClassEntryModel.class);
        when(cls.getMethodSpans()).thenReturn(spans);
        return cls;
    }

    @Test
    void splicesShellAndBodiesKeepingGapsAndFooter()
    {
        ClassEntryModel cls = classWithSpans();
        Map<String, String> bodies = new HashMap<>();
        bodies.put("foo()V", "  void foo() {\n    doThing();\n  }");
        bodies.put("bar()I", "  int bar() {\n    return cachedSize;\n  }");

        String out = SourceAssembler.assemble(cls, SOURCE, "package x;\npublic class C {", bodies);

        assertTrue(out.contains("doThing();"), out);
        assertTrue(out.contains("return cachedSize;"), out);
        assertTrue(out.contains("package x;"));
        assertFalse(out.contains("a();"));
        assertFalse(out.contains("return b;"));
        assertTrue(out.endsWith("}"));
        assertTrue(out.contains("  }\n\n  int bar()"), out);
    }

    @Test
    void missingBodyKeepsOriginalSlice()
    {
        ClassEntryModel cls = classWithSpans();
        Map<String, String> bodies = new HashMap<>();
        bodies.put("foo()V", "  void foo() {\n    doThing();\n  }");

        String out = SourceAssembler.assemble(cls, SOURCE, "package x;\npublic class C {", bodies);

        assertTrue(out.contains("doThing();"));
        assertTrue(out.contains("return b;"), out);
    }

    @Test
    void headerAndMethodSlices()
    {
        ClassEntryModel cls = classWithSpans();
        assertEquals("package x;\npublic class C {", SourceAssembler.headerSource(cls, SOURCE));
        assertEquals("  void foo() {\n    a();\n  }", SourceAssembler.methodSource(cls, SOURCE, "foo", "()V"));
        assertNull(SourceAssembler.methodSource(cls, SOURCE, "nope", "()V"));
    }
}
