package com.tonic.ui;

import com.tonic.event.EventBus;
import com.tonic.event.events.LiveSessionEvent;
import com.tonic.ui.editor.ViewMode;
import com.tonic.ui.editor.ViewModeComboBox;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.*;
import lombok.Getter;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.ActionListener;

/**
 * Builds the main toolbar for JStudio.
 */
public class ToolbarBuilder implements ThemeChangeListener
{

    private final MainFrame mainFrame;
    /**
     * -- GETTER --
     * The built toolbar; null until build has run.
     */
    @Getter
    private JToolBar toolbar;
    private ViewModeComboBox viewModeCombo;
    private JToggleButton omitAnnotationsButton;
    private JButton scratchPadButton;
    private EventBus.EventHandler<LiveSessionEvent> liveSessionHandler;

    /**
     * Creates the builder and registers it for theme changes.
     *
     * @param mainFrame the window whose actions the buttons invoke
     */
    public ToolbarBuilder(MainFrame mainFrame)
    {
        this.mainFrame = mainFrame;
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme)
    {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    /** Unregisters this builder's theme + live-session listeners (called when the main window closes). */
    public void dispose()
    {
        ThemeManager.getInstance().removeThemeChangeListener(this);
        if (liveSessionHandler != null)
        {
            EventBus.getInstance().unregister(LiveSessionEvent.class, liveSessionHandler);
        }
    }

    private void applyTheme()
    {
        if (toolbar != null)
        {
            toolbar.setBackground(JStudioTheme.getBgPrimary());
            toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        }
        themeViewModeCombo();
    }

    private void themeViewModeCombo()
    {
        if (viewModeCombo != null)
        {
            viewModeCombo.setBackground(JStudioTheme.getBgTertiary());
            viewModeCombo.setForeground(JStudioTheme.getTextPrimary());
        }
    }

    /**
     * Builds the toolbar and subscribes it to live-session events, which toggle the live view modes and the scratch pad button.
     *
     * @return the toolbar
     */
    public JToolBar build()
    {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        toolbar.setBackground(JStudioTheme.getBgPrimary());

        toolbar.add(createButton(Icons.getIcon("open"), "Open JAR/Class (Ctrl+O)", e -> mainFrame.showOpenDialog()));
        toolbar.add(createButton(Icons.getIcon("save"), "Export Class (Ctrl+Shift+E)", e -> mainFrame.exportCurrentClass()));
        toolbar.addSeparator();

        toolbar.add(createButton(Icons.getIcon("back"), "Navigate Back (Alt+Left)", e -> mainFrame.navigateBack()));
        toolbar.add(createButton(Icons.getIcon("forward"), "Navigate Forward (Alt+Right)", e -> mainFrame.navigateForward()));
        toolbar.addSeparator();

        viewModeCombo = new ViewModeComboBox();
        viewModeCombo.setToolTipText("View mode - how the selected class is shown (Decompiled, Bytecode, Hex, ...)");
        themeViewModeCombo();
        viewModeCombo.addActionListener(e ->
        {
            ViewMode mode = viewModeCombo.getSelectedViewMode();
            mainFrame.switchToView(mode);
        });
        viewModeCombo.setLiveViewsAvailable(LiveAttachService.getInstance().isAttached());
        liveSessionHandler = e ->
        {
            viewModeCombo.setLiveViewsAvailable(e.isAttached());
            if (scratchPadButton != null)
            {
                scratchPadButton.setVisible(e.isAttached());
            }
        };
        EventBus.getInstance().register(LiveSessionEvent.class, liveSessionHandler);
        toolbar.add(viewModeCombo);
        toolbar.addSeparator();

        omitAnnotationsButton = new JToggleButton(Icons.getIcon("annotation"));
        omitAnnotationsButton.setToolTipText("Hide Annotations");
        omitAnnotationsButton.setFocusable(false);
        omitAnnotationsButton.setBorderPainted(false);
        omitAnnotationsButton.setPreferredSize(new Dimension(32, 32));
        omitAnnotationsButton.addActionListener(e -> mainFrame.setOmitAnnotations(omitAnnotationsButton.isSelected()));
        toolbar.add(omitAnnotationsButton);

        toolbar.addSeparator();

        toolbar.add(createButton(Icons.getIcon("bookmark"), "Add Bookmark (Ctrl+B)", e -> mainFrame.addBookmarkAtCurrentLocation()));
        toolbar.add(createButton(Icons.getIcon("comment"), "Add Comment (Ctrl+;)", e -> mainFrame.addCommentAtCurrentLocation()));
        toolbar.addSeparator();

        toolbar.add(createButton(Icons.getIcon("analyze"), "Run Analysis (F9)", e -> mainFrame.runAnalysis()));
        toolbar.add(createButton(Icons.getIcon("transform"), "Apply Transforms (Ctrl+Shift+T)", e -> mainFrame.showTransformDialog()));
        toolbar.add(createButton(Icons.getIcon("debug"), "Bytecode Debugger (F11)", e -> mainFrame.showBytecodeDebugger()));
        scratchPadButton = createButton(Icons.getIcon("console"), "Java Scratch Pad (run code in the attached JVM)", e -> mainFrame.showLiveScratchPad());
        scratchPadButton.setVisible(LiveAttachService.getInstance().isAttached());
        toolbar.add(scratchPadButton);
        toolbar.addSeparator();

        toolbar.add(createButton(Icons.getIcon("refresh"), "Refresh - re-decompile all & clear caches (Ctrl+F5)", e -> mainFrame.fullRefresh()));

        return toolbar;
    }

    private JButton createButton(Icon icon, String tooltip, ActionListener action)
    {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(32, 32));
        button.addActionListener(action);

        ThemeStyles.addFillHoverEffect(button);

        return button;
    }

    /**
     * Selects a view mode in the combo box.
     *
     * @param mode the mode to select
     */
    public void setViewMode(ViewMode mode)
    {
        viewModeCombo.setSelectedViewMode(mode);
    }

    /**
     * Appends a plugin button to the toolbar, styled like the built-in buttons.
     *
     * @param icon the button icon
     * @param tooltip the tooltip text
     * @param action what a click runs
     * @return the button, for a later removePluginButton
     */
    public JButton addPluginButton(Icon icon, String tooltip, ActionListener action)
    {
        JButton button = createButton(icon, tooltip, action);
        toolbar.add(button);
        toolbar.revalidate();
        toolbar.repaint();
        return button;
    }

    /**
     * Removes a button previously added with addPluginButton.
     *
     * @param button the button to remove
     */
    public void removePluginButton(JButton button)
    {
        toolbar.remove(button);
        toolbar.revalidate();
        toolbar.repaint();
    }
}
