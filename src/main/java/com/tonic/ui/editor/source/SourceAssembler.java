package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.model.ClassEntryModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reassembles a full class source from independently-cleaned pieces, using the decompiler's per-method line
 * spans ({@link ClassEntryModel#getMethodSpans()}). A cleaned "shell" replaces the region before the first
 * method (class declaration + fields), each cleaned method body replaces its span, and every other line (imports,
 * gaps between members, any method without a cleaned body) is kept verbatim from the original - so a failed or
 * skipped piece never drops a member. Plugin-safe: takes only {@link ClassEntryModel} + Strings (spans are YABR
 * types the plugin can't reach directly).
 */
public final class SourceAssembler
{

    private SourceAssembler()
    {
    }

    /** The region before the first method (package/imports/class decl + fields), or the whole source if no methods. */
    public static String headerSource(ClassEntryModel classEntry, String source)
    {
        String[] lines = source.split("\n", -1);
        int firstStart = firstMethodStart(classEntry, lines.length + 1);
        return join(lines, 1, firstStart - 1);
    }

    /** One method's full source slice (declaration through closing brace), or null when it has no span. */
    public static String methodSource(ClassEntryModel classEntry, String source, String name, String desc)
    {
        Map<String, DecompileResult.MethodSpan> spans = classEntry.getMethodSpans();
        if (spans == null)
        {
            return null;
        }
        DecompileResult.MethodSpan span = spans.get(name + desc);
        if (span == null)
        {
            return null;
        }
        return join(source.split("\n", -1), span.getStartLine(), span.getEndLine());
    }

    /**
     * Reassembles the class: {@code cleanedShell} replaces the header region, each cleaned body (keyed by
     * {@code name + descriptor}) replaces its method span, and everything else is kept from {@code source}. A
     * method with no entry in {@code cleanedBodiesByKey} keeps its original slice.
     */
    public static String assemble(ClassEntryModel classEntry, String source, String cleanedShell, Map<String, String> cleanedBodiesByKey)
    {
        String[] lines = source.split("\n", -1);
        List<Span> ordered = orderedSpans(classEntry);
        int firstStart = ordered.isEmpty() ? lines.length + 1 : ordered.get(0).start;

        List<String> segments = new ArrayList<>();
        segments.add(cleanedShell != null ? cleanedShell : join(lines, 1, firstStart - 1));

        int i = firstStart;
        int spanIdx = 0;
        while (i <= lines.length)
        {
            if (spanIdx < ordered.size() && ordered.get(spanIdx).start == i)
            {
                Span span = ordered.get(spanIdx);
                String body = cleanedBodiesByKey != null ? cleanedBodiesByKey.get(span.key) : null;
                segments.add(body != null ? body : join(lines, span.start, span.end));
                i = span.end + 1;
                spanIdx++;
            }
            else
            {
                segments.add(lines[i - 1]);
                i++;
            }
        }
        return String.join("\n", segments);
    }

    private static List<Span> orderedSpans(ClassEntryModel classEntry)
    {
        List<Span> ordered = new ArrayList<>();
        Map<String, DecompileResult.MethodSpan> spans = classEntry.getMethodSpans();
        if (spans != null)
        {
            for (Map.Entry<String, DecompileResult.MethodSpan> e : spans.entrySet())
            {
                ordered.add(new Span(e.getKey(), e.getValue().getStartLine(), e.getValue().getEndLine()));
            }
            ordered.sort(Comparator.comparingInt(a -> a.start));
        }
        return ordered;
    }

    private static int firstMethodStart(ClassEntryModel classEntry, int fallback)
    {
        Map<String, DecompileResult.MethodSpan> spans = classEntry.getMethodSpans();
        if (spans == null || spans.isEmpty())
        {
            return fallback;
        }
        int min = Integer.MAX_VALUE;
        for (DecompileResult.MethodSpan span : spans.values())
        {
            min = Math.min(min, span.getStartLine());
        }
        return min;
    }

    /** Lines {@code [from, to]} (1-based inclusive) joined by newline; empty when the range is empty. */
    private static String join(String[] lines, int from, int to)
    {
        int start = Math.max(from, 1);
        int end = Math.min(to, lines.length);
        if (start > end)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++)
        {
            if (i > start)
            {
                sb.append('\n');
            }
            sb.append(lines[i - 1]);
        }
        return sb.toString();
    }

    private static final class Span
    {
        final String key;
        final int start;
        final int end;

        Span(String key, int start, int end)
        {
            this.key = key;
            this.start = start;
            this.end = end;
        }
    }
}
