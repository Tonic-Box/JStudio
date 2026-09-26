package com.tonic.ui.theme;

/** A callback for theme switches. */
public interface ThemeChangeListener
{
    /**
     * Called after the current theme changes and has been applied.
     *
     * @param newTheme the theme now current
     */
    void onThemeChanged(Theme newTheme);
}
