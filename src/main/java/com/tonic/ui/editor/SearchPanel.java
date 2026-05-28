package com.tonic.ui.editor;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JViewport;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Unified live-search panel for all RSyntaxTextArea-based editor views.
 * Design:
 *  - Typing debounces 200 ms, then kicks off a SwingWorker that scans off the EDT
 *  - All match offsets are stored in a sorted List<int[]>; navigation is O(log N)
 *  - Highlighting uses textArea.markAll(List<DocumentRange>) — RSyntaxTextArea's
 *    native API — but restricted to the visible viewport ± a 2-screen buffer.
 *    This keeps the per-search createPosition() cost to ~O(200²) ≈ 1 ms regardless
 *    of how many total matches exist (e.g., spaces in a 50k-line file).
 *  - The viewport highlight set is refreshed via a 50 ms debounced scroll listener.
 *  - No SearchEngine / SearchContext calls anywhere; those are O(K²) for K matches.
 */
public class SearchPanel extends ThemedJPanel {

    private static final int   DEBOUNCE_SEARCH_MS   = 200;
    private static final int   DEBOUNCE_SCROLL_MS    = 50;
    /** Hard cap on markAll() entries to keep createPosition() cost O(MAX²) at worst. */
    private static final int   MAX_VIEWPORT_MARKS   = 1000;

    /** Pale background used for all matches in the visible viewport. */
    private static final Color MATCH_COLOR         = new Color(255, 235,  80,  55);
    /** Vivid orange — clearly distinct hue and fully opaque vs the pale yellow above. */
    private static final Color CURRENT_MATCH_COLOR = new Color(255, 120,   0);

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;

    private final JTextField searchField;
    private final JLabel     matchCountLabel;
    private final JCheckBox  caseSensitiveBox;
    private final JCheckBox  wholeWordBox;
    private final JCheckBox  regexBox;

    private List<int[]> matches = Collections.emptyList();
    private String      cachedText;

    private SwingWorker<List<int[]>, Void> searchWorker;
    private Timer  debounceTimer;
    private Timer  scrollDebounceTimer;

    private int    currentMatchIndex = -1;
    private Object currentMatchTag;           // Highlighter tag for the bright current-match highlight

    public SearchPanel(RSyntaxTextArea textArea, RTextScrollPane scrollPane) {
        super(BackgroundStyle.SECONDARY);
        this.textArea   = textArea;
        this.scrollPane = scrollPane;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM,
                                            UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM)
        ));
        setVisible(false);

        // All mass-match highlights use the pale colour; focused match uses CURRENT_MATCH_COLOR
        textArea.setMarkAllHighlightColor(MATCH_COLOR);

        // Invalidate text snapshot whenever document content changes (e.g. new class loaded)
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { cachedText = null; }
            @Override public void removeUpdate(DocumentEvent e)  { cachedText = null; }
            @Override public void changedUpdate(DocumentEvent e) { cachedText = null; }
        });

        // Refresh visible highlights when the user scrolls (debounced)
        if (scrollPane != null) {
            scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> scheduleHighlightUpdate());
        }

        // ── Find label ────────────────────────────────────────────────────────
        JLabel findLabel = new JLabel("Find:");
        findLabel.setForeground(JStudioTheme.getTextPrimary());
        add(findLabel);
        add(Box.createHorizontalStrut(8));

        // ── Search field ──────────────────────────────────────────────────────
        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(250, 28));
        searchField.setPreferredSize(new Dimension(200, 28));
        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { scheduleSearch(); }
            @Override public void removeUpdate(DocumentEvent e)  { scheduleSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
        });

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if      (e.getKeyCode() == KeyEvent.VK_ESCAPE) hidePanel();
                else if (e.getKeyCode() == KeyEvent.VK_ENTER)  {
                    if (e.isShiftDown()) findPrevious();
                    else                findNext();
                }
            }
        });

        add(searchField);
        add(Box.createHorizontalStrut(8));

        // ── Prev / Next buttons ───────────────────────────────────────────────
        JButton prevBtn = makeButton(Icons.getIcon("arrow_up",   12), "Previous (Shift+Enter)");
        prevBtn.addActionListener(e -> findPrevious());
        add(prevBtn);

        JButton nextBtn = makeButton(Icons.getIcon("arrow_down", 12), "Next (Enter)");
        nextBtn.addActionListener(e -> findNext());
        add(nextBtn);

        add(Box.createHorizontalStrut(8));

        // ── Match count ───────────────────────────────────────────────────────
        matchCountLabel = new JLabel("");
        matchCountLabel.setForeground(JStudioTheme.getTextSecondary());
        add(matchCountLabel);
        add(Box.createHorizontalStrut(16));

        // ── Option checkboxes ─────────────────────────────────────────────────
        caseSensitiveBox = makeCheckBox("Aa", "Case Sensitive");
        caseSensitiveBox.addActionListener(e -> triggerSearch());
        add(caseSensitiveBox);

        wholeWordBox = makeCheckBox("W", "Whole Word");
        wholeWordBox.addActionListener(e -> triggerSearch());
        add(wholeWordBox);

        regexBox = makeCheckBox(".*", "Regular Expression");
        regexBox.addActionListener(e -> triggerSearch());
        add(regexBox);

        add(Box.createHorizontalGlue());

        // ── Close button ──────────────────────────────────────────────────────
        JButton closeBtn = makeButton(Icons.getIcon("close", 12), "Close (Esc)");
        closeBtn.addActionListener(e -> hidePanel());
        add(closeBtn);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void showPanel() {
        setVisible(true);
        searchField.requestFocusInWindow();
        searchField.selectAll();

        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) {
            searchField.setText(sel);
        }
        if (!searchField.getText().isEmpty()) {
            triggerSearch();
        }
    }

    public void hidePanel() {
        cancelWorker();
        stopTimers();
        clearCurrentMatchHighlight();
        textArea.clearMarkAllHighlights();
        setVisible(false);
        textArea.requestFocusInWindow();
        matches = Collections.emptyList();
        currentMatchIndex = -1;
        matchCountLabel.setText("");
    }

    // ── Search scheduling ─────────────────────────────────────────────────────

    private void scheduleSearch() {
        if (debounceTimer != null) debounceTimer.stop();
        debounceTimer = new Timer(DEBOUNCE_SEARCH_MS, e -> triggerSearch());
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void triggerSearch() {
        if (debounceTimer != null) debounceTimer.stop();

        String query = searchField.getText();
        if (query.isEmpty()) {
            cancelWorker();
            clearCurrentMatchHighlight();
            currentMatchIndex = -1;
            textArea.clearMarkAllHighlights();
            matches = Collections.emptyList();
            matchCountLabel.setText("");
            return;
        }

        // Snapshot state on the EDT before handing off to the worker
        if (cachedText == null) cachedText = textArea.getText();
        final String  snapshot      = cachedText;
        final boolean caseSensitive = caseSensitiveBox.isSelected();
        final boolean wholeWord     = wholeWordBox.isSelected();
        final boolean useRegex      = regexBox.isSelected();
        final int     caretAtLaunch = textArea.getCaretPosition();

        cancelWorker();

        searchWorker = new SwingWorker<>() {
            @Override protected List<int[]> doInBackground() {
                return scan(snapshot, query, caseSensitive, wholeWord, useRegex);
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    clearCurrentMatchHighlight();
                    currentMatchIndex = -1;
                    matches = get();
                    updateMatchLabel();
                    updateHighlights();
                    if (!matches.isEmpty()) {
                        navigateTo(firstIndexAtOrAfter(caretAtLaunch));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ignored) {}
            }
        };
        searchWorker.execute();
    }

    // ── Highlight management ──────────────────────────────────────────────────

    /**
     * Builds a DocumentRange list for the visible viewport ± 2-screen buffer and
     * passes it to textArea.markAll(). Bounded to ~200 ranges regardless of total
     * match count, so createPosition() cost stays O(200²) ≈ 1 ms.
     */
    private void updateHighlights() {
        if (matches.isEmpty()) {
            textArea.clearMarkAllHighlights();
            return;
        }

        int visStart = 0;
        int visEnd   = cachedText != null ? cachedText.length() : 0;

        if (scrollPane != null) {
            try {
                JViewport vp     = scrollPane.getViewport();
                Point     vpPos  = vp.getViewPosition();
                Dimension vpSize = vp.getExtentSize();
                if (vpSize.height > 0) {
                    visStart = textArea.viewToModel2D(
                                   new Point2D.Float(vpPos.x, vpPos.y));
                    visEnd   = textArea.viewToModel2D(
                                   new Point2D.Float(vpPos.x + vpSize.width,
                                                     vpPos.y + vpSize.height));
                }
            } catch (Exception ignored) {}
        }

        // Buffer = 2 screen heights worth of document characters
        int buf   = Math.max(500, (visEnd - visStart) * 2);
        int lo    = firstIndexAtOrAfter(Math.max(0, visStart - buf));
        int hiEnd = visEnd + buf;

        List<DocumentRange> toMark = new ArrayList<>();
        for (int i = lo; i < matches.size(); i++) {
            if (matches.get(i)[0] > hiEnd) break;
            if (toMark.size() >= MAX_VIEWPORT_MARKS) break; // keeps createPosition cost O(MAX²)
            if (i == currentMatchIndex) continue; // bright highlight handled separately
            toMark.add(new DocumentRange(matches.get(i)[0], matches.get(i)[1]));
        }
        textArea.markAll(toMark);
    }

    private void scheduleHighlightUpdate() {
        if (matches.isEmpty()) return;
        if (scrollDebounceTimer != null) scrollDebounceTimer.stop();
        scrollDebounceTimer = new Timer(DEBOUNCE_SCROLL_MS, e -> updateHighlights());
        scrollDebounceTimer.setRepeats(false);
        scrollDebounceTimer.start();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void findNext() {
        if (matches.isEmpty()) return;
        navigateTo(firstIndexAtOrAfter(textArea.getCaretPosition() + 1));
    }

    private void findPrevious() {
        if (matches.isEmpty()) return;
        navigateTo(lastIndexBefore(textArea.getCaretPosition()));
    }

    private void navigateTo(int idx) {
        if (idx < 0 || idx >= matches.size()) return;
        currentMatchIndex = idx;
        clearCurrentMatchHighlight();

        int[] m = matches.get(idx);
        textArea.setCaretPosition(m[0]);

        try {
            currentMatchTag = textArea.getHighlighter().addHighlight(
                m[0], m[1],
                new DefaultHighlighter.DefaultHighlightPainter(CURRENT_MATCH_COLOR));
        } catch (BadLocationException ignored) {}

        updateHighlights();   // re-draws pale highlights, skipping currentMatchIndex
        updateMatchLabel();   // refresh "x / N" counter
    }

    private void clearCurrentMatchHighlight() {
        if (currentMatchTag != null) {
            textArea.getHighlighter().removeHighlight(currentMatchTag);
            currentMatchTag = null;
        }
    }

    /** First index where match[0] >= offset; wraps to 0 if past end. */
    private int firstIndexAtOrAfter(int offset) {
        int lo = 0, hi = matches.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (matches.get(mid)[0] < offset) lo = mid + 1;
            else hi = mid;
        }
        return lo < matches.size() ? lo : 0;
    }

    /** Last index where match[0] < offset; wraps to last. */
    private int lastIndexBefore(int offset) {
        int lo = 0, hi = matches.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (matches.get(mid)[0] < offset) lo = mid + 1;
            else hi = mid;
        }
        int idx = lo - 1;
        return idx >= 0 ? idx : matches.size() - 1;
    }

    // ── Off-EDT text scanning ─────────────────────────────────────────────────

    private List<int[]> scan(String text, String query,
                              boolean caseSensitive, boolean wholeWord, boolean useRegex) {
        if (text.isEmpty() || query.isEmpty()) return Collections.emptyList();
        List<int[]> result = new ArrayList<>();

        if (useRegex) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                Matcher m = Pattern.compile(query, flags).matcher(text);
                while (m.find()) {
                    if (Thread.currentThread().isInterrupted()) break;
                    if (!wholeWord || isWholeWord(text, m.start(), m.end()))
                        result.add(new int[]{m.start(), m.end()});
                }
            } catch (PatternSyntaxException ignored) {}
        } else {
            String haystack = caseSensitive ? text  : text.toLowerCase();
            String needle   = caseSensitive ? query : query.toLowerCase();
            int pos = 0, len = needle.length();
            while ((pos = haystack.indexOf(needle, pos)) >= 0) {
                if (Thread.currentThread().isInterrupted()) break;
                if (!wholeWord || isWholeWord(text, pos, pos + len))
                    result.add(new int[]{pos, pos + len});
                pos++;
            }
        }
        return result;
    }

    private boolean isWholeWord(String text, int start, int end) {
        if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) return false;
        return end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cancelWorker() {
        if (searchWorker != null && !searchWorker.isDone())
            searchWorker.cancel(true);
    }

    private void stopTimers() {
        if (debounceTimer       != null) debounceTimer.stop();
        if (scrollDebounceTimer != null) scrollDebounceTimer.stop();
    }

    private void updateMatchLabel() {
        int n = matches.size();
        if (n == 0) {
            matchCountLabel.setText("No matches");
            matchCountLabel.setForeground(new Color(255, 100, 100));
        } else if (currentMatchIndex >= 0 && currentMatchIndex < n) {
            matchCountLabel.setText((currentMatchIndex + 1) + " / " + n);
            matchCountLabel.setForeground(JStudioTheme.getTextSecondary());
        } else {
            matchCountLabel.setText(n + " matches");
            matchCountLabel.setForeground(JStudioTheme.getTextSecondary());
        }
    }

    private JButton makeButton(javax.swing.Icon icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setMaximumSize(new Dimension(24, 24));
        return btn;
    }

    private JCheckBox makeCheckBox(String text, String tooltip) {
        JCheckBox cb = new JCheckBox(text);
        cb.setToolTipText(tooltip);
        cb.setFocusable(false);
        cb.setBackground(JStudioTheme.getBgSecondary());
        cb.setForeground(JStudioTheme.getTextPrimary());
        cb.setFont(cb.getFont().deriveFont(11f));
        return cb;
    }
}
