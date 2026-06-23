package com.tonic.ui.bottom;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;

public class BottomToolbar extends ThemedJPanel {

    private final JButton consoleButton;
    private final JButton bookmarksButton;
    private final JButton commentsButton;
    private final JButton historyButton;

    private Runnable onConsoleClicked;
    private Runnable onBookmarksClicked;
    private Runnable onCommentsClicked;
    private Runnable onLocalHistoryClicked;

    public BottomToolbar() {
        super(BackgroundStyle.SECONDARY, new FlowLayout(FlowLayout.RIGHT, 4, 2));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        consoleButton = createButton("Console", "console");
        consoleButton.addActionListener(e -> {
            if (onConsoleClicked != null) onConsoleClicked.run();
        });
        add(consoleButton);

        historyButton = createButton("History", "undo");
        historyButton.addActionListener(e -> {
            if (onLocalHistoryClicked != null) onLocalHistoryClicked.run();
        });
        add(historyButton);

        bookmarksButton = createButton("Bookmarks", "bookmark");
        bookmarksButton.addActionListener(e -> {
            if (onBookmarksClicked != null) onBookmarksClicked.run();
        });
        add(bookmarksButton);

        commentsButton = createButton("Comments", "comment");
        commentsButton.addActionListener(e -> {
            if (onCommentsClicked != null) onCommentsClicked.run();
        });
        add(commentsButton);
    }

    private JButton createButton(String text, String iconName) {
        JButton button = new JButton(text, Icons.getIcon(iconName, 12));
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusable(false);
        button.setFont(JStudioTheme.getUIFont(11));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        return button;
    }

    public void setOnConsoleClicked(Runnable callback) {
        this.onConsoleClicked = callback;
    }

    public void setOnBookmarksClicked(Runnable callback) {
        this.onBookmarksClicked = callback;
    }

    public void setOnCommentsClicked(Runnable callback) {
        this.onCommentsClicked = callback;
    }

    public void setOnLocalHistoryClicked(Runnable callback) {
        this.onLocalHistoryClicked = callback;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyThemeToComponents);
    }

    private void applyThemeToComponents() {
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        applyButtonTheme(consoleButton);
        applyButtonTheme(bookmarksButton);
        applyButtonTheme(commentsButton);
        applyButtonTheme(historyButton);
    }

    private void applyButtonTheme(JButton button) {
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
    }
}
