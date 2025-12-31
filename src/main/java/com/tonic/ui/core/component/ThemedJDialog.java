package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JDialog;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;

public class ThemedJDialog extends JDialog implements ThemeChangeListener {

    public ThemedJDialog() {
        super();
        initialize();
    }

    public ThemedJDialog(Frame owner) {
        super(owner);
        initialize();
    }

    public ThemedJDialog(Frame owner, boolean modal) {
        super(owner, modal);
        initialize();
    }

    public ThemedJDialog(Frame owner, String title) {
        super(owner, title);
        initialize();
    }

    public ThemedJDialog(Frame owner, String title, boolean modal) {
        super(owner, title, modal);
        initialize();
    }

    public ThemedJDialog(Dialog owner) {
        super(owner);
        initialize();
    }

    public ThemedJDialog(Dialog owner, boolean modal) {
        super(owner, modal);
        initialize();
    }

    public ThemedJDialog(Dialog owner, String title) {
        super(owner, title);
        initialize();
    }

    public ThemedJDialog(Dialog owner, String title, boolean modal) {
        super(owner, title, modal);
        initialize();
    }

    public ThemedJDialog(Window owner) {
        super(owner);
        initialize();
    }

    public ThemedJDialog(Window owner, ModalityType modalityType) {
        super(owner, modalityType);
        initialize();
    }

    public ThemedJDialog(Window owner, String title) {
        super(owner, title);
        initialize();
    }

    public ThemedJDialog(Window owner, String title, ModalityType modalityType) {
        super(owner, title, modalityType);
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
        getContentPane().setBackground(JStudioTheme.getBgPrimary());
    }

    @Override
    public void dispose() {
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.dispose();
    }
}
