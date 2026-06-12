package com.tonic.ui.editor.source;

import com.tonic.ui.theme.JStudioTheme;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paints usage-count lens entries as small ghost text over the source text area and resolves
 * clicks against the painted bounds. Above-line entries align with the declaration's indentation;
 * end-of-line entries trail the declaration text. Purely visual — the document is never modified,
 * so the decompiler's line maps stay valid.
 */
public final class UsageLensOverlay {

    private static final int LENS_FONT_SIZE = 11;
    private static final int END_OF_LINE_GAP = 24;

    private List<UsageLens.LensEntry> entries = Collections.emptyList();

    public void setEntries(List<UsageLens.LensEntry> entries) {
        this.entries = entries != null ? entries : Collections.emptyList();
    }

    public void clear() {
        entries = Collections.emptyList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The lens whose painted bounds contain the point, or null. */
    public UsageLens.LensEntry hitTest(Point p) {
        for (UsageLens.LensEntry entry : entries) {
            if (entry.hitBox != null && entry.hitBox.contains(p)) {
                return entry;
            }
        }
        return null;
    }

    /** Paints all entries; call after the text area's own painting. */
    public void paint(Graphics2D g, RSyntaxTextArea textArea) {
        if (entries.isEmpty()) {
            return;
        }
        Font lensFont = JStudioTheme.getCodeFont(LENS_FONT_SIZE);
        FontMetrics lensMetrics = textArea.getFontMetrics(lensFont);
        FontMetrics textMetrics = textArea.getFontMetrics(textArea.getFont());

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(lensFont);
        g.setColor(JStudioTheme.getTextSecondary());

        List<UsageLens.LensEntry> snapshot = new ArrayList<>(entries);
        for (UsageLens.LensEntry entry : snapshot) {
            try {
                paintEntry(g, textArea, entry, lensMetrics, textMetrics);
            } catch (Exception e) {
                entry.hitBox = null;
            }
        }
    }

    private void paintEntry(Graphics2D g, RSyntaxTextArea textArea, UsageLens.LensEntry entry,
                            FontMetrics lensMetrics, FontMetrics textMetrics) throws Exception {
        if (entry.anchorLine >= textArea.getLineCount()
                || entry.declarationLine >= textArea.getLineCount()) {
            entry.hitBox = null;
            return;
        }
        int anchorStart = textArea.getLineStartOffset(entry.anchorLine);
        Rectangle2D anchorRect = textArea.modelToView2D(anchorStart);
        if (anchorRect == null) {
            entry.hitBox = null;
            return;
        }

        double x;
        if (entry.endOfLine) {
            int lineEnd = Math.max(textArea.getLineEndOffset(entry.anchorLine) - 1, anchorStart);
            Rectangle2D endRect = textArea.modelToView2D(lineEnd);
            x = (endRect != null ? endRect.getMaxX() : anchorRect.getX()) + END_OF_LINE_GAP;
        } else {
            x = indentX(textArea, entry.declarationLine, anchorRect.getX());
        }

        int baseline = (int) Math.round(anchorRect.getY()) + textMetrics.getAscent();
        g.drawString(entry.text, (float) x, baseline);

        int width = lensMetrics.stringWidth(entry.text);
        entry.hitBox = new Rectangle((int) Math.round(x), (int) Math.round(anchorRect.getY()),
                width, (int) Math.round(anchorRect.getHeight()));
    }

    /** The x of the declaration line's first non-whitespace character, for indent alignment. */
    private double indentX(RSyntaxTextArea textArea, int declarationLine, double fallback) throws Exception {
        int start = textArea.getLineStartOffset(declarationLine);
        int end = textArea.getLineEndOffset(declarationLine);
        String text = textArea.getText(start, end - start);
        int firstNonWs = 0;
        while (firstNonWs < text.length() && Character.isWhitespace(text.charAt(firstNonWs))) {
            firstNonWs++;
        }
        Rectangle2D rect = textArea.modelToView2D(start + firstNonWs);
        return rect != null ? rect.getX() : fallback;
    }
}
