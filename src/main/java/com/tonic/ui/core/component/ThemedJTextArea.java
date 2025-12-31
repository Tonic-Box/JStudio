package com.tonic.ui.core.component;

import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JTextArea;

public class ThemedJTextArea extends JTextArea implements ThemeChangeListener {

    private boolean useCodeFont = true;

    public ThemedJTextArea() {
        super();
        initialize();
    }

    public ThemedJTextArea(String text) {
        super(text);
        initialize();
    }

    public ThemedJTextArea(int rows, int cols) {
        super(rows, cols);
        initialize();
    }

    public ThemedJTextArea(String text, int rows, int cols) {
        super(text, rows, cols);
        initialize();
    }

    public void setUseCodeFont(boolean useCodeFont) {
        this.useCodeFont = useCodeFont;
        applyTheme();
    }

    private void initialize() {
        applyTheme();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        applyTheme();
        repaint();
    }

    protected void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        setForeground(JStudioTheme.getTextPrimary());
        setCaretColor(JStudioTheme.getTextPrimary());
        setSelectionColor(JStudioTheme.getSelection());

        if (useCodeFont) {
            setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        } else {
            setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
