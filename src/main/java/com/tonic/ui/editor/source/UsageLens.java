package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.event.events.FindUsagesEvent;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Placement logic for the editor's usage-count lenses. For each member (method, field, or class) the
 * lens text goes on the blank line directly above the declaration (the decompiler separates members
 * with blank lines, giving an IntelliJ-style inlay without touching the document), falling back to
 * the end of the declaration line itself when no blank line exists. One placement algorithm serves
 * all member kinds; painting and hit-testing live in {@link UsageLensOverlay}.
 */
public final class UsageLens {

    /** A member to lens: its kind, identity, declaration span, and usage count. */
    public static final class LensTarget {
        final FindUsagesEvent.TargetType type;
        final String memberName;
        final String memberDescriptor;
        final DecompileResult.MemberSpan span;
        final int count;

        public LensTarget(FindUsagesEvent.TargetType type, String memberName, String memberDescriptor,
                          DecompileResult.MemberSpan span, int count) {
            this.type = type;
            this.memberName = memberName;
            this.memberDescriptor = memberDescriptor;
            this.span = span;
            this.count = count;
        }
    }

    /** One lens to render: where it anchors, what it says, and which member it opens usages for. */
    public static final class LensEntry {
        /** 0-based line the text is painted on (blank line above, or the declaration itself). */
        public final int anchorLine;
        /** 0-based declaration line; supplies the indent for above-line placement. */
        public final int declarationLine;
        /** True when there was no blank line and the text is appended after the declaration text. */
        public final boolean endOfLine;
        public final String text;
        public final FindUsagesEvent.TargetType targetType;
        public final String memberName;
        public final String memberDescriptor;
        /** Last painted bounds, used for hit-testing; null until painted. */
        public Rectangle hitBox;

        LensEntry(int anchorLine, int declarationLine, boolean endOfLine, String text,
                  FindUsagesEvent.TargetType targetType, String memberName, String memberDescriptor) {
            this.anchorLine = anchorLine;
            this.declarationLine = declarationLine;
            this.endOfLine = endOfLine;
            this.text = text;
            this.targetType = targetType;
            this.memberName = memberName;
            this.memberDescriptor = memberDescriptor;
        }
    }

    private UsageLens() {
    }

    /**
     * Builds lens entries for every target whose span lands within the displayed source, sorted by
     * anchor line. {@code sourceLines} is the displayed source split on {@code \n}.
     */
    public static List<LensEntry> compute(String[] sourceLines, List<LensTarget> targets) {
        return compute(sourceLines, targets, null);
    }

    /**
     * As {@link #compute(String[], List)} but for a filtered view: {@code lineMap} (from {@code AnnotationFilter})
     * translates each original 0-based line to its 0-based line in {@code sourceLines} (-1 if removed), so lenses
     * land correctly when annotations are hidden. A null map is the identity (unfiltered source).
     */
    public static List<LensEntry> compute(String[] sourceLines, List<LensTarget> targets, int[] lineMap) {
        List<LensEntry> entries = new ArrayList<>();
        if (sourceLines == null || targets == null) {
            return entries;
        }
        for (LensTarget target : targets) {
            if (target.span == null) {
                continue;
            }
            int declLine = mapLine(target.span.getStartLine() - 1, lineMap);
            if (declLine < 0 || declLine >= sourceLines.length) {
                continue;
            }
            int above = declLine - 1;
            boolean blankAbove = above >= 0 && sourceLines[above].trim().isEmpty();
            entries.add(new LensEntry(
                    blankAbove ? above : declLine,
                    declLine,
                    !blankAbove,
                    lensText(target.count),
                    target.type,
                    target.memberName,
                    target.memberDescriptor));
        }
        entries.sort(Comparator.comparingInt(e -> e.anchorLine));
        return entries;
    }

    /**
     * Translates a 0-based original declaration line to its 0-based line in the displayed source. Identity when
     * {@code lineMap} is null. If the exact line was removed (e.g. the span points at an annotation line), falls
     * back to the next kept line; returns -1 if none remains.
     */
    private static int mapLine(int originalLine, int[] lineMap) {
        if (originalLine < 0) {
            return -1;
        }
        if (lineMap == null) {
            return originalLine;
        }
        for (int i = originalLine; i < lineMap.length; i++) {
            if (lineMap[i] >= 0) {
                return lineMap[i];
            }
        }
        return -1;
    }

    private static String lensText(int count) {
        if (count == 0) {
            return "no usages";
        }
        return count == 1 ? "1 usage" : count + " usages";
    }
}
