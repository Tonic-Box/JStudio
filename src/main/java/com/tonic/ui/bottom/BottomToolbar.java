package com.tonic.ui.bottom;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;

public class BottomToolbar extends ThemedJPanel implements ThemeChangeListener {

    private final JButton bookmarksButton;
    private final JButton commentsButton;

    private Runnable onBookmarksClicked;
    private Runnable onCommentsClicked;

    public BottomToolbar() {
        super(BackgroundStyle.SECONDARY, new FlowLayout(FlowLayout.RIGHT, 4, 2));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

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

        ThemeManager.getInstance().addThemeChangeListener(this);
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

    public void setOnBookmarksClicked(Runnable callback) {
        this.onBookmarksClicked = callback;
    }

    public void setOnCommentsClicked(Runnable callback) {
        this.onCommentsClicked = callback;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyThemeToComponents);
    }

    private void applyThemeToComponents() {
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        applyButtonTheme(bookmarksButton);
        applyButtonTheme(commentsButton);
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
