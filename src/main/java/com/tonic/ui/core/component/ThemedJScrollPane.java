package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import java.awt.Component;

public class ThemedJScrollPane extends JScrollPane implements ThemeManager.ThemeChangeListener {

    public ThemedJScrollPane() {
        super();
        initialize();
    }

    public ThemedJScrollPane(Component view) {
        super(view);
        initialize();
    }

    public ThemedJScrollPane(int vsbPolicy, int hsbPolicy) {
        super(vsbPolicy, hsbPolicy);
        initialize();
    }

    public ThemedJScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
        super(view, vsbPolicy, hsbPolicy);
        initialize();
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
        setBackground(JStudioTheme.getBgPrimary());
        getViewport().setBackground(JStudioTheme.getBgPrimary());
        setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
