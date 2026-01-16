package com.tonic.ui.editor.bytecode;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class BytecodeSearchPanel extends ThemedJPanel {

    private final RSyntaxTextArea textArea;
    private final JTextField searchField;
    private final JLabel matchCountLabel;
    private final JCheckBox caseSensitiveBox;
    private final JCheckBox wholeWordBox;
    private final JCheckBox regexBox;

    private final SearchContext searchContext;

    public BytecodeSearchPanel(RSyntaxTextArea textArea) {
        super(BackgroundStyle.SECONDARY);
        this.textArea = textArea;

        searchContext = new SearchContext();
        searchContext.setMarkAll(true);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM)
        ));
        setVisible(false);

        JLabel findLabel = new JLabel("Find:");
        findLabel.setForeground(JStudioTheme.getTextPrimary());
        add(findLabel);
        add(Box.createHorizontalStrut(8));

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
            @Override
            public void insertUpdate(DocumentEvent e) { onSearchTextChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onSearchTextChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onSearchTextChanged(); }
        });

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hidePanel();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        findPrevious();
                    } else {
                        findNext();
                    }
                }
            }
        });

        add(searchField);
        add(Box.createHorizontalStrut(8));

        JButton prevBtn = createToolButton(Icons.getIcon("arrow_up", 12), "Previous (Shift+Enter)");
        prevBtn.addActionListener(e -> findPrevious());
        add(prevBtn);

        JButton nextBtn = createToolButton(Icons.getIcon("arrow_down", 12), "Next (Enter)");
        nextBtn.addActionListener(e -> findNext());
        add(nextBtn);

        add(Box.createHorizontalStrut(8));

        matchCountLabel = new JLabel("");
        matchCountLabel.setForeground(JStudioTheme.getTextSecondary());
        add(matchCountLabel);

        add(Box.createHorizontalStrut(16));

        caseSensitiveBox = createCheckBox("Aa", "Case Sensitive");
        caseSensitiveBox.addActionListener(e -> onSearchTextChanged());
        add(caseSensitiveBox);

        wholeWordBox = createCheckBox("W", "Whole Word");
        wholeWordBox.addActionListener(e -> onSearchTextChanged());
        add(wholeWordBox);

        regexBox = createCheckBox(".*", "Regular Expression");
        regexBox.addActionListener(e -> onSearchTextChanged());
        add(regexBox);

        add(Box.createHorizontalGlue());

        JButton closeBtn = createToolButton(Icons.getIcon("close", 12), "Close (Esc)");
        closeBtn.addActionListener(e -> hidePanel());
        add(closeBtn);
    }

    private JButton createToolButton(javax.swing.Icon icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setMaximumSize(new Dimension(24, 24));
        return btn;
    }

    private JCheckBox createCheckBox(String text, String tooltip) {
        JCheckBox cb = new JCheckBox(text);
        cb.setToolTipText(tooltip);
        cb.setFocusable(false);
        cb.setBackground(JStudioTheme.getBgSecondary());
        cb.setForeground(JStudioTheme.getTextPrimary());
        cb.setFont(cb.getFont().deriveFont(11f));
        return cb;
    }

    public void showPanel() {
        setVisible(true);
        searchField.requestFocusInWindow();
        searchField.selectAll();

        String selected = textArea.getSelectedText();
        if (selected != null && !selected.isEmpty() && !selected.contains("\n")) {
            searchField.setText(selected);
            onSearchTextChanged();
        }
    }

    public void hidePanel() {
        setVisible(false);
        SearchEngine.markAll(textArea, new SearchContext());
        textArea.requestFocusInWindow();
    }

    private void onSearchTextChanged() {
        String searchText = searchField.getText();

        if (searchText.isEmpty()) {
            matchCountLabel.setText("");
            SearchEngine.markAll(textArea, new SearchContext());
            return;
        }

        updateSearchContext(searchText);
        SearchResult result = SearchEngine.markAll(textArea, searchContext);

        int count = result.getMarkedCount();
        if (count > 0) {
            matchCountLabel.setText(count + " matches");
            matchCountLabel.setForeground(JStudioTheme.getTextSecondary());
            findNext();
        } else {
            matchCountLabel.setText("No matches");
            matchCountLabel.setForeground(new Color(255, 100, 100));
        }
    }

    private void updateSearchContext(String searchText) {
        searchContext.setSearchFor(searchText);
        searchContext.setMatchCase(caseSensitiveBox.isSelected());
        searchContext.setWholeWord(wholeWordBox.isSelected());
        searchContext.setRegularExpression(regexBox.isSelected());
        searchContext.setSearchForward(true);
        searchContext.setMarkAll(true);
    }

    private void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        updateSearchContext(searchText);
        searchContext.setSearchForward(true);
        SearchResult result = SearchEngine.find(textArea, searchContext);

        if (!result.wasFound()) {
            textArea.setCaretPosition(0);
            SearchEngine.find(textArea, searchContext);
        }

        updateMatchLabel();
    }

    private void findPrevious() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        updateSearchContext(searchText);
        searchContext.setSearchForward(false);
        SearchResult result = SearchEngine.find(textArea, searchContext);

        if (!result.wasFound()) {
            textArea.setCaretPosition(textArea.getText().length());
            SearchEngine.find(textArea, searchContext);
        }

        updateMatchLabel();
    }

    private void updateMatchLabel() {
        SearchResult markResult = SearchEngine.markAll(textArea, searchContext);
        int total = markResult.getMarkedCount();
        if (total > 0) {
            matchCountLabel.setText(total + " matches");
            matchCountLabel.setForeground(JStudioTheme.getTextSecondary());
        } else {
            matchCountLabel.setText("No matches");
            matchCountLabel.setForeground(new Color(255, 100, 100));
        }
    }
}
