package com.tonic.ui.history;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.editor.source.JavaEditorFactory;
import com.tonic.ui.theme.JStudioTheme;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * A side-by-side "old vs new" source diff shown as a center tab. Two themed Java editors are aligned row-for-row by
 * {@link LineDiff} (filler lines fill the gaps), changed lines are tinted (removed = red, added = green), and the two
 * editors scroll together. Source is supplied already-decompiled by the opener.
 */
public final class HistoryDiffView extends ThemedJPanel {

    private static final Color REMOVED = new Color(255, 85, 85, 48);
    private static final Color ADDED = new Color(90, 200, 90, 48);
    private static final Color FILLER = new Color(128, 128, 128, 26);

    public HistoryDiffView(String leftLabel, String rightLabel, String oldSource, String newSource) {
        super(BackgroundStyle.PRIMARY, new BorderLayout());

        List<String> oldLines = Arrays.asList(oldSource.split("\n", -1));
        List<String> newLines = Arrays.asList(newSource.split("\n", -1));
        List<LineDiff.Row> rows = LineDiff.diff(oldLines, newLines);

        RSyntaxTextArea left = JavaEditorFactory.createEditor(false);
        RSyntaxTextArea right = JavaEditorFactory.createEditor(false);
        RTextScrollPane leftScroll = JavaEditorFactory.createScrollPane(left);
        RTextScrollPane rightScroll = JavaEditorFactory.createScrollPane(right);

        StringBuilder leftText = new StringBuilder();
        StringBuilder rightText = new StringBuilder();
        for (LineDiff.Row row : rows) {
            leftText.append(row.left != null ? row.left : "").append('\n');
            rightText.append(row.right != null ? row.right : "").append('\n');
        }
        left.setText(leftText.toString());
        right.setText(rightText.toString());
        left.setCaretPosition(0);
        right.setCaretPosition(0);

        JavaEditorFactory.applyTheme(left, leftScroll);
        JavaEditorFactory.applyTheme(right, rightScroll);
        highlight(left, right, rows);

        add(buildHeader(leftLabel, rightLabel, rows), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titled(leftLabel, leftScroll), titled(rightLabel, rightScroll));
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setDividerSize(4);
        add(split, BorderLayout.CENTER);

        syncScroll(leftScroll.getVerticalScrollBar(), rightScroll.getVerticalScrollBar());
    }

    private void highlight(RSyntaxTextArea left, RSyntaxTextArea right, List<LineDiff.Row> rows) {
        for (int i = 0; i < rows.size(); i++) {
            LineDiff.Row row = rows.get(i);
            switch (row.type) {
                case DELETE:
                    addLine(left, i, REMOVED);
                    addLine(right, i, FILLER);
                    break;
                case INSERT:
                    addLine(left, i, FILLER);
                    addLine(right, i, ADDED);
                    break;
                case CHANGE:
                    addLine(left, i, REMOVED);
                    addLine(right, i, ADDED);
                    break;
                default:
                    break;
            }
        }
    }

    private static void addLine(RSyntaxTextArea editor, int line, Color color) {
        try {
            editor.addLineHighlight(line, color);
        } catch (BadLocationException | RuntimeException ignored) {
            // line out of range (trailing) - skip
        }
    }

    private JPanel buildHeader(String leftLabel, String rightLabel, List<LineDiff.Row> rows) {
        int added = 0;
        int removed = 0;
        int changed = 0;
        for (LineDiff.Row row : rows) {
            if (row.type == LineDiff.Type.INSERT) {
                added++;
            } else if (row.type == LineDiff.Type.DELETE) {
                removed++;
            } else if (row.type == LineDiff.Type.CHANGE) {
                changed++;
            }
        }
        JLabel label = new JLabel(leftLabel + "  vs  " + rightLabel
                + "      +" + added + " added, -" + removed + " removed, ~" + changed + " changed");
        label.setForeground(JStudioTheme.getTextSecondary());
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(label, BorderLayout.WEST);
        return header;
    }

    private JPanel titled(String title, RTextScrollPane scroll) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void syncScroll(JScrollBar a, JScrollBar b) {
        boolean[] guard = {false};
        a.addAdjustmentListener(e -> {
            if (!guard[0]) {
                guard[0] = true;
                b.setValue(e.getValue());
                guard[0] = false;
            }
        });
        b.addAdjustmentListener(e -> {
            if (!guard[0]) {
                guard[0] = true;
                a.setValue(e.getValue());
                guard[0] = false;
            }
        });
    }
}
