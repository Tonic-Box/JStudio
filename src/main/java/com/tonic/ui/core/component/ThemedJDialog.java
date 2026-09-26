package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JDialog;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;

/** A dialog whose content pane follows the theme background; it stops listening for theme changes on dispose. */
public class ThemedJDialog extends JDialog implements ThemeChangeListener
{

    /** Creates the dialog and registers it for theme changes. */
    public ThemedJDialog()
    {
        super();
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     */
    public ThemedJDialog(Frame owner)
    {
        super(owner);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param modal whether the dialog blocks its owner
     */
    public ThemedJDialog(Frame owner, boolean modal)
    {
        super(owner, modal);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param title the title
     */
    public ThemedJDialog(Frame owner, String title)
    {
        super(owner, title);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param title the title
     * @param modal whether the dialog blocks its owner
     */
    public ThemedJDialog(Frame owner, String title, boolean modal)
    {
        super(owner, title, modal);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     */
    public ThemedJDialog(Dialog owner)
    {
        super(owner);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param modal whether the dialog blocks its owner
     */
    public ThemedJDialog(Dialog owner, boolean modal)
    {
        super(owner, modal);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param title the title
     */
    public ThemedJDialog(Dialog owner, String title)
    {
        super(owner, title);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param title the title
     * @param modal whether the dialog blocks its owner
     */
    public ThemedJDialog(Dialog owner, String title, boolean modal)
    {
        super(owner, title, modal);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     */
    public ThemedJDialog(Window owner)
    {
        super(owner);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param modalityType the modality
     */
    public ThemedJDialog(Window owner, ModalityType modalityType)
    {
        super(owner, modalityType);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param title the title
     */
    public ThemedJDialog(Window owner, String title)
    {
        super(owner, title);
        initialize();
    }

    /**
     * Creates the dialog and registers it for theme changes.
     *
     * @param owner the owning window
     * @param title the title
     * @param modalityType the modality
     */
    public ThemedJDialog(Window owner, String title, ModalityType modalityType)
    {
        super(owner, title, modalityType);
        initialize();
    }

    private void initialize()
    {
        applyTheme();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme)
    {
        applyTheme();
        repaint();
    }

    protected void applyTheme()
    {
        getContentPane().setBackground(JStudioTheme.getBgPrimary());
    }

    @Override
    public void dispose()
    {
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.dispose();
    }
}
