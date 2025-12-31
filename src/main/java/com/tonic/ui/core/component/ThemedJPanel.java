package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Getter;

import javax.swing.JPanel;
import java.awt.LayoutManager;

public class ThemedJPanel extends JPanel implements ThemeChangeListener {

    public enum BackgroundStyle {
        PRIMARY,
        SECONDARY,
        TERTIARY,
        SURFACE
    }

    @Getter
    private BackgroundStyle backgroundStyle;
    private boolean themeApplied = false;

    public ThemedJPanel() {
        this(BackgroundStyle.PRIMARY);
    }

    public ThemedJPanel(BackgroundStyle style) {
        this(style, null);
    }

    public ThemedJPanel(LayoutManager layout) {
        this(BackgroundStyle.PRIMARY, layout);
    }

    public ThemedJPanel(BackgroundStyle style, LayoutManager layout) {
        super(layout);
        this.backgroundStyle = style;
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!themeApplied) {
            applyTheme();
            themeApplied = true;
        }
    }

    public void setBackgroundStyle(BackgroundStyle style) {
        this.backgroundStyle = style;
        applyTheme();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        applyTheme();
        repaint();
    }

    protected void applyTheme() {
        switch (backgroundStyle) {
            case PRIMARY:
                setBackground(JStudioTheme.getBgPrimary());
                break;
            case SECONDARY:
                setBackground(JStudioTheme.getBgSecondary());
                break;
            case TERTIARY:
                setBackground(JStudioTheme.getBgTertiary());
                break;
            case SURFACE:
                setBackground(JStudioTheme.getBgSurface());
                break;
        }
        applyChildThemes();
    }

    protected void applyChildThemes() {
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
