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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RuntimeHintOverlay
{

    private static final int HINT_FONT_SIZE = 11;
    private static final int END_OF_LINE_GAP = 24;
    private static final int SEPARATOR = 14;

    private List<RuntimeHint.HintEntry> entries = Collections.emptyList();

    void setEntries(List<RuntimeHint.HintEntry> entries)
    {
        this.entries = entries != null ? entries : Collections.emptyList();
    }

    void clear()
    {
        entries = Collections.emptyList();
    }

    boolean isEmpty()
    {
        return entries.isEmpty();
    }

    RuntimeHint.HintEntry hitTest(Point p)
    {
        for (RuntimeHint.HintEntry e : entries)
        {
            if (e.refHandle > 0 && e.hitBox != null && e.hitBox.contains(p))
            {
                return e;
            }
        }
        return null;
    }

    RuntimeHint.HintEntry hitTestAny(Point p)
    {
        for (RuntimeHint.HintEntry e : entries)
        {
            if (e.hitBox != null && e.hitBox.contains(p))
            {
                return e;
            }
        }
        return null;
    }

    List<RuntimeHint.HintEntry> entries()
    {
        return new ArrayList<>(entries);
    }

    void paint(Graphics2D g, RSyntaxTextArea textArea)
    {
        if (entries.isEmpty())
        {
            return;
        }
        Font hintFont = JStudioTheme.getCodeFont(HINT_FONT_SIZE);
        FontMetrics hintMetrics = textArea.getFontMetrics(hintFont);
        FontMetrics textMetrics = textArea.getFontMetrics(textArea.getFont());

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(hintFont);

        Map<Integer, Double> nextX = new HashMap<>();
        for (RuntimeHint.HintEntry entry : new ArrayList<>(entries))
        {
            try
            {
                paintEntry(g, textArea, entry, hintMetrics, textMetrics, nextX);
            }
            catch (Exception e)
            {
                entry.hitBox = null;
            }
        }
    }

    private void paintEntry(Graphics2D g, RSyntaxTextArea textArea, RuntimeHint.HintEntry entry, FontMetrics hintMetrics, FontMetrics textMetrics, Map<Integer, Double> nextX)
            throws Exception
    {
        int line0 = entry.line - 1;
        if (line0 < 0 || line0 >= textArea.getLineCount())
        {
            entry.hitBox = null;
            return;
        }
        int start = textArea.getLineStartOffset(line0);
        Rectangle2D rowRect = textArea.modelToView2D(start);
        if (rowRect == null)
        {
            entry.hitBox = null;
            return;
        }

        double x = advanceX(textArea, line0, start, entry, hintMetrics, nextX);
        int baseline = (int) Math.round(rowRect.getY()) + textMetrics.getAscent();
        g.setColor(entry.refHandle > 0 ? JStudioTheme.getAccent() : JStudioTheme.getTextSecondary());
        g.drawString(entry.text, (float) x, baseline);

        entry.hitBox = new Rectangle((int) Math.round(x), (int) Math.round(rowRect.getY()), hintMetrics.stringWidth(entry.text), (int) Math.round(rowRect.getHeight()));
    }

    private double advanceX(RSyntaxTextArea textArea, int line0, int start, RuntimeHint.HintEntry entry, FontMetrics hintMetrics, Map<Integer, Double> nextX) throws Exception
    {
        double x = nextX.containsKey(line0) ? nextX.get(line0) : endX(textArea, line0, start) + END_OF_LINE_GAP;
        nextX.put(line0, x + hintMetrics.stringWidth(entry.text) + SEPARATOR);
        return x;
    }

    int requiredWidth(RSyntaxTextArea textArea)
    {
        if (entries.isEmpty())
        {
            return 0;
        }
        FontMetrics hintMetrics = textArea.getFontMetrics(JStudioTheme.getCodeFont(HINT_FONT_SIZE));
        Map<Integer, Double> nextX = new HashMap<>();
        double max = 0;
        for (RuntimeHint.HintEntry entry : new ArrayList<>(entries))
        {
            try
            {
                int line0 = entry.line - 1;
                if (line0 < 0 || line0 >= textArea.getLineCount())
                {
                    continue;
                }
                int start = textArea.getLineStartOffset(line0);
                double x = advanceX(textArea, line0, start, entry, hintMetrics, nextX);
                max = Math.max(max, x + hintMetrics.stringWidth(entry.text));
            }
            catch (Exception ignored)
            {
            }
        }
        return (int) Math.ceil(max);
    }

    private double endX(RSyntaxTextArea textArea, int line0, int start) throws Exception
    {
        int lineEnd = Math.max(textArea.getLineEndOffset(line0) - 1, start);
        Rectangle2D endRect = textArea.modelToView2D(lineEnd);
        return endRect != null ? endRect.getMaxX() : 0;
    }
}
