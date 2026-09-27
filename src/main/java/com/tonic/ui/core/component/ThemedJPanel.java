package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Getter;

import javax.swing.JPanel;
import java.awt.LayoutManager;

/** A panel whose background follows one of the theme's background colors; while displayable it listens for theme changes, and it reapplies the theme each time it is added, catching up on changes made while it was detached. */
public class ThemedJPanel extends JPanel implements ThemeChangeListener
{

    /** Which theme background a panel uses. */
    public enum BackgroundStyle
    {
        PRIMARY,
        SECONDARY,
        TERTIARY,
        SURFACE
    }

    @Getter
    private BackgroundStyle backgroundStyle;

    /** Creates a panel with the primary background and the default flow layout. */
    public ThemedJPanel()
    {
        this(BackgroundStyle.PRIMARY);
    }

    /**
     * Creates a panel with the default flow layout.
     *
     * @param style which theme background to use
     */
    public ThemedJPanel(BackgroundStyle style)
    {
        this(style, null);
    }

    /**
     * Creates a panel with the primary background.
     *
     * @param layout the layout, or null for the default flow layout
     */
    public ThemedJPanel(LayoutManager layout)
    {
        this(BackgroundStyle.PRIMARY, layout);
    }

    /**
     * Creates a panel; it starts following theme changes when it is added to a displayable container.
     *
     * @param style which theme background to use
     * @param layout the layout, or null for the default flow layout
     */
    public ThemedJPanel(BackgroundStyle style, LayoutManager layout)
    {
        super(layout);
        this.backgroundStyle = style;
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        ThemeManager.getInstance().addThemeChangeListener(this);
        applyTheme();
    }

    /**
     * Switches to another theme background and reapplies the theme.
     *
     * @param style which theme background to use
     */
    public void setBackgroundStyle(BackgroundStyle style)
    {
        this.backgroundStyle = style;
        applyTheme();
    }

    @Override
    public void onThemeChanged(Theme newTheme)
    {
        applyTheme();
        repaint();
    }

    protected void applyTheme()
    {
        switch (backgroundStyle)
        {
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

    protected void applyChildThemes()
    {
    }

    @Override
    public void removeNotify()
    {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
