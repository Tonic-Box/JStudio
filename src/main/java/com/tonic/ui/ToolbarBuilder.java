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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Builds the main toolbar for JStudio.
 */
public class ToolbarBuilder implements ThemeChangeListener {

    private final MainFrame mainFrame;
    /**
     * -- GETTER --
     * The built toolbar (null until
     *  has run).
     */
    @Getter
    private JToolBar toolbar;
    private ViewModeComboBox viewModeCombo;
    private JToggleButton omitAnnotationsButton;
    private JButton scratchPadButton;

    public ToolbarBuilder(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        if (toolbar != null) {
            toolbar.setBackground(JStudioTheme.getBgPrimary());
            toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        }
        themeViewModeCombo();
    }

    /** Themes the view dropdown explicitly so it isn't the L&F default on first show (and follows theme switches). */
    private void themeViewModeCombo() {
        if (viewModeCombo != null) {
            viewModeCombo.setBackground(JStudioTheme.getBgTertiary());
            viewModeCombo.setForeground(JStudioTheme.getTextPrimary());
        }
    }

    public JToolBar build() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        toolbar.setBackground(JStudioTheme.getBgPrimary());

        // File operations
        toolbar.add(createButton(Icons.getIcon("open"), "Open JAR/Class (Ctrl+O)", e -> mainFrame.showOpenDialog()));
        toolbar.add(createButton(Icons.getIcon("save"), "Export Class (Ctrl+Shift+E)", e -> mainFrame.exportCurrentClass()));
        toolbar.addSeparator();

        // Navigation
        toolbar.add(createButton(Icons.getIcon("back"), "Navigate Back (Alt+Left)", e -> mainFrame.navigateBack()));
        toolbar.add(createButton(Icons.getIcon("forward"), "Navigate Forward (Alt+Right)", e -> mainFrame.navigateForward()));
        toolbar.addSeparator();

        // View mode dropdown
        viewModeCombo = new ViewModeComboBox();
        viewModeCombo.setToolTipText("View mode - how the selected class is shown (Decompiled, Bytecode, Hex, ...)");
        themeViewModeCombo();
        viewModeCombo.addActionListener(e -> {
            ViewMode mode = viewModeCombo.getSelectedViewMode();
            mainFrame.switchToView(mode);
        });
        viewModeCombo.setLiveViewsAvailable(LiveAttachService.getInstance().isAttached());
        EventBus.getInstance().register(LiveSessionEvent.class, e -> {
            viewModeCombo.setLiveViewsAvailable(e.isAttached());
            if (scratchPadButton != null) {
                scratchPadButton.setVisible(e.isAttached());
            }
        });
        toolbar.add(viewModeCombo);
        toolbar.addSeparator();

        omitAnnotationsButton = new JToggleButton(Icons.getIcon("annotation"));
        omitAnnotationsButton.setToolTipText("Hide Annotations");
        omitAnnotationsButton.setFocusable(false);
        omitAnnotationsButton.setBorderPainted(false);
        omitAnnotationsButton.setPreferredSize(new Dimension(32, 32));
        omitAnnotationsButton.addActionListener(e ->
                mainFrame.setOmitAnnotations(omitAnnotationsButton.isSelected()));
        toolbar.add(omitAnnotationsButton);

        toolbar.addSeparator();

        // Bookmarks & Comments
        toolbar.add(createButton(Icons.getIcon("bookmark"), "Add Bookmark (Ctrl+B)", e -> mainFrame.addBookmarkAtCurrentLocation()));
        toolbar.add(createButton(Icons.getIcon("comment"), "Add Comment (Ctrl+;)", e -> mainFrame.addCommentAtCurrentLocation()));
        toolbar.addSeparator();

        // Analysis
        toolbar.add(createButton(Icons.getIcon("analyze"), "Run Analysis (F9)", e -> mainFrame.runAnalysis()));
        toolbar.add(createButton(Icons.getIcon("transform"), "Apply Transforms (Ctrl+Shift+T)", e -> mainFrame.showTransformDialog()));
        toolbar.add(createButton(Icons.getIcon("debug"), "Bytecode Debugger (F11)", e -> mainFrame.showBytecodeDebugger()));
        scratchPadButton = createButton(Icons.getIcon("console"), "Java Scratch Pad (run code in the attached JVM)",
                e -> mainFrame.showLiveScratchPad());
        scratchPadButton.setVisible(LiveAttachService.getInstance().isAttached());
        toolbar.add(scratchPadButton);
        toolbar.addSeparator();

        // Refresh - full: invalidate all decompilation caches + re-decompile every open tab (same as after AI rename)
        toolbar.add(createButton(Icons.getIcon("refresh"),
                "Refresh - re-decompile all & clear caches (Ctrl+F5)", e -> mainFrame.fullRefresh()));

        return toolbar;
    }

    private JButton createButton(Icon icon, String tooltip, ActionListener action) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(32, 32));
        button.addActionListener(action);

        // Add hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });

        return button;
    }

    public void setViewMode(ViewMode mode) {
        viewModeCombo.setSelectedViewMode(mode);
    }

    /**
     * Appends a plugin-contributed button to the toolbar, styled like the built-in buttons. Returns the button so
     * it can later be passed to {@link #removePluginButton(JButton)}.
     */
    public JButton addPluginButton(Icon icon, String tooltip, ActionListener action) {
        JButton button = createButton(icon, tooltip, action);
        toolbar.add(button);
        toolbar.revalidate();
        toolbar.repaint();
        return button;
    }

    /** Removes a plugin button previously added with {@link #addPluginButton}. */
    public void removePluginButton(JButton button) {
        toolbar.remove(button);
        toolbar.revalidate();
        toolbar.repaint();
    }
}
