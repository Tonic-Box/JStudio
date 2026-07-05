package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.model.ClassEntryModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceAssemblerTest {

    // Lines: 1 package | 2 class{ | 3-5 foo | 6 blank | 7-9 bar | 10 }
    private static final String SOURCE = String.join("\n",
            "package x;",           // 1
            "public class C {",     // 2
            "  void foo() {",       // 3
            "    a();",             // 4
            "  }",                  // 5
            "",                     // 6
            "  int bar() {",        // 7
            "    return b;",        // 8
            "  }",                  // 9
            "}");                   // 10

    private static DecompileResult.MethodSpan span(int start, int end) {
        DecompileResult.MethodSpan s = mock(DecompileResult.MethodSpan.class);
        when(s.getStartLine()).thenReturn(start);
        when(s.getEndLine()).thenReturn(end);
        return s;
    }

    private static ClassEntryModel classWithSpans() {
        Map<String, DecompileResult.MethodSpan> spans = new HashMap<>();
        spans.put("foo()V", span(3, 5));
        spans.put("bar()I", span(7, 9));
        ClassEntryModel cls = mock(ClassEntryModel.class);
        when(cls.getMethodSpans()).thenReturn(spans);
        return cls;
    }

    @Test
    void splicesShellAndBodiesKeepingGapsAndFooter() {
        ClassEntryModel cls = classWithSpans();
        Map<String, String> bodies = new HashMap<>();
        bodies.put("foo()V", "  void foo() {\n    doThing();\n  }");
        bodies.put("bar()I", "  int bar() {\n    return cachedSize;\n  }");

        String out = SourceAssembler.assemble(cls, SOURCE, "package x;\npublic class C {", bodies);

        assertTrue(out.contains("doThing();"), out);
        assertTrue(out.contains("return cachedSize;"), out);
        assertTrue(out.contains("package x;"));
        assertFalse(out.contains("a();"));           // foo body replaced
        assertFalse(out.contains("return b;"));      // bar body replaced
        assertTrue(out.endsWith("}"));               // footer preserved
        // blank line between methods preserved (gap line 6)
        assertTrue(out.contains("  }\n\n  int bar()"), out);
    }

    @Test
    void missingBodyKeepsOriginalSlice() {
        ClassEntryModel cls = classWithSpans();
        Map<String, String> bodies = new HashMap<>();
        bodies.put("foo()V", "  void foo() {\n    doThing();\n  }");
        // bar not provided -> original kept

        String out = SourceAssembler.assemble(cls, SOURCE, "package x;\npublic class C {", bodies);

        assertTrue(out.contains("doThing();"));
        assertTrue(out.contains("return b;"), out);  // bar kept verbatim
    }

    @Test
    void headerAndMethodSlices() {
        ClassEntryModel cls = classWithSpans();
        assertEquals("package x;\npublic class C {", SourceAssembler.headerSource(cls, SOURCE));
        assertEquals("  void foo() {\n    a();\n  }", SourceAssembler.methodSource(cls, SOURCE, "foo", "()V"));
        assertNull(SourceAssembler.methodSource(cls, SOURCE, "nope", "()V"));
    }
}
