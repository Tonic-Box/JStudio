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

/** Paints usage-count lenses as ghost text over the source text area and resolves clicks against their painted bounds, never modifying the document. */
public final class UsageLensOverlay
{

    private static final int LENS_FONT_SIZE = 11;
    private static final int END_OF_LINE_GAP = 24;

    private List<UsageLens.LensEntry> entries = Collections.emptyList();

    /**
     * Replaces the lenses to paint.
     *
     * @param entries the lenses; null clears them
     */
    public void setEntries(List<UsageLens.LensEntry> entries)
    {
        this.entries = entries != null ? entries : Collections.emptyList();
    }

    /** Removes all lenses. */
    public void clear()
    {
        entries = Collections.emptyList();
    }

    /**
     * Tells whether there are no lenses to paint.
     *
     * @return true when there are no lenses
     */
    public boolean isEmpty()
    {
        return entries.isEmpty();
    }

    /**
     * Finds the lens under a point.
     *
     * @param p the point in text area coordinates
     * @return the lens whose last painted bounds contain the point, or null
     */
    public UsageLens.LensEntry hitTest(Point p)
    {
        for (UsageLens.LensEntry entry : entries)
        {
            if (entry.hitBox != null && entry.hitBox.contains(p))
            {
                return entry;
            }
        }
        return null;
    }

    /**
     * Paints all lenses and records their bounds for hit-testing; call after the text area's own painting.
     *
     * @param g the graphics to paint with
     * @param textArea the text area the lenses sit over
     */
    public void paint(Graphics2D g, RSyntaxTextArea textArea)
    {
        if (entries.isEmpty())
        {
            return;
        }
        Font lensFont = JStudioTheme.getCodeFont(LENS_FONT_SIZE);
        FontMetrics lensMetrics = textArea.getFontMetrics(lensFont);
        FontMetrics textMetrics = textArea.getFontMetrics(textArea.getFont());

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(lensFont);
        g.setColor(JStudioTheme.getTextSecondary());

        List<UsageLens.LensEntry> snapshot = new ArrayList<>(entries);
        for (UsageLens.LensEntry entry : snapshot)
        {
            try
            {
                paintEntry(g, textArea, entry, lensMetrics, textMetrics);
            }
            catch (Exception e)
            {
                entry.hitBox = null;
            }
        }
    }

    private void paintEntry(Graphics2D g, RSyntaxTextArea textArea, UsageLens.LensEntry entry, FontMetrics lensMetrics, FontMetrics textMetrics) throws Exception
    {
        if (entry.anchorLine >= textArea.getLineCount()
                || entry.declarationLine >= textArea.getLineCount())
        {
            entry.hitBox = null;
            return;
        }
        int anchorStart = textArea.getLineStartOffset(entry.anchorLine);
        Rectangle2D anchorRect = textArea.modelToView2D(anchorStart);
        if (anchorRect == null)
        {
            entry.hitBox = null;
            return;
        }

        double x;
        if (entry.endOfLine)
        {
            int lineEnd = Math.max(textArea.getLineEndOffset(entry.anchorLine) - 1, anchorStart);
            Rectangle2D endRect = textArea.modelToView2D(lineEnd);
            x = (endRect != null ? endRect.getMaxX() : anchorRect.getX()) + END_OF_LINE_GAP;
        }
        else
        {
            x = indentX(textArea, entry.declarationLine, anchorRect.getX());
        }

        int baseline = (int) Math.round(anchorRect.getY()) + textMetrics.getAscent();
        g.drawString(entry.text, (float) x, baseline);

        int width = lensMetrics.stringWidth(entry.text);
        entry.hitBox = new Rectangle((int) Math.round(x), (int) Math.round(anchorRect.getY()), width, (int) Math.round(anchorRect.getHeight()));
    }

    private double indentX(RSyntaxTextArea textArea, int declarationLine, double fallback) throws Exception
    {
        int start = textArea.getLineStartOffset(declarationLine);
        int end = textArea.getLineEndOffset(declarationLine);
        String text = textArea.getText(start, end - start);
        int firstNonWs = 0;
        while (firstNonWs < text.length() && Character.isWhitespace(text.charAt(firstNonWs)))
        {
            firstNonWs++;
        }
        Rectangle2D rect = textArea.modelToView2D(start + firstNonWs);
        return rect != null ? rect.getX() : fallback;
    }
}
