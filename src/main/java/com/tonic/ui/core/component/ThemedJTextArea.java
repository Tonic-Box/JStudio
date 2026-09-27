package com.tonic.ui.core.component;

import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JTextArea;

/** A text area on the tertiary background whose colors and font follow the theme; it uses the code font unless told otherwise. */
public class ThemedJTextArea extends JTextArea implements ThemeChangeListener
{

    private boolean useCodeFont = true;

    /** Creates an empty text area and registers it for theme changes. */
    public ThemedJTextArea()
    {
        super();
        initialize();
    }

    /**
     * Creates a text area with initial text and registers it for theme changes.
     *
     * @param text the initial text
     */
    public ThemedJTextArea(String text)
    {
        super(text);
        initialize();
    }

    /**
     * Creates an empty text area of a given size and registers it for theme changes.
     *
     * @param rows the row count
     * @param cols the column count
     */
    public ThemedJTextArea(int rows, int cols)
    {
        super(rows, cols);
        initialize();
    }

    /**
     * Creates a text area with initial text and a given size and registers it for theme changes.
     *
     * @param text the initial text
     * @param rows the row count
     * @param cols the column count
     */
    public ThemedJTextArea(String text, int rows, int cols)
    {
        super(text, rows, cols);
        initialize();
    }

    /**
     * Chooses between the code font and the UI font and reapplies the theme.
     *
     * @param useCodeFont true for the code font, false for the UI font
     */
    public void setUseCodeFont(boolean useCodeFont)
    {
        this.useCodeFont = useCodeFont;
        applyTheme();
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
        setBackground(JStudioTheme.getBgTertiary());
        setForeground(JStudioTheme.getTextPrimary());
        setCaretColor(JStudioTheme.getTextPrimary());
        setSelectionColor(JStudioTheme.getSelection());

        if (useCodeFont)
        {
            setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        }
        else
        {
            setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        }
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
