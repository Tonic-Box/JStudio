package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import java.awt.Component;

/** A borderless scroll pane whose background and viewport follow the theme's primary background. */
public class ThemedJScrollPane extends JScrollPane implements ThemeChangeListener
{

    /** Creates an empty scroll pane and registers it for theme changes. */
    public ThemedJScrollPane()
    {
        super();
        initialize();
    }

    /**
     * Creates a scroll pane over a view and registers it for theme changes.
     *
     * @param view the component to scroll
     */
    public ThemedJScrollPane(Component view)
    {
        super(view);
        initialize();
    }

    /**
     * Creates an empty scroll pane with the given scrollbar policies and registers it for theme changes.
     *
     * @param vsbPolicy the vertical scrollbar policy
     * @param hsbPolicy the horizontal scrollbar policy
     */
    public ThemedJScrollPane(int vsbPolicy, int hsbPolicy)
    {
        super(vsbPolicy, hsbPolicy);
        initialize();
    }

    /**
     * Creates a scroll pane over a view with the given scrollbar policies and registers it for theme changes.
     *
     * @param view the component to scroll
     * @param vsbPolicy the vertical scrollbar policy
     * @param hsbPolicy the horizontal scrollbar policy
     */
    public ThemedJScrollPane(Component view, int vsbPolicy, int hsbPolicy)
    {
        super(view, vsbPolicy, hsbPolicy);
        initialize();
    }

    private void initialize()
    {
    }

    @Override
    public void onThemeChanged(Theme newTheme)
    {
        applyTheme();
        repaint();
    }

    protected void applyTheme()
    {
        setBackground(JStudioTheme.getBgPrimary());
        getViewport().setBackground(JStudioTheme.getBgPrimary());
        setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        ThemeManager.getInstance().addThemeChangeListener(this);
        onThemeChanged(ThemeManager.getInstance().getCurrentTheme());
    }

    @Override
    public void removeNotify()
    {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
