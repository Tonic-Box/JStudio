package com.tonic.plugin.api.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * The UI contribution surface handed to a {@link UiPlugin} via {@link JStudioHost#ui()}. Every mutator returns a
 * {@link Registration} that removes the contribution; the host also records it and removes it automatically when
 * the plugin is disabled or the app exits. All methods must be called on the Swing event dispatch thread (the
 * host already calls {@link UiPlugin#start(JStudioHost)} on the EDT).
 */
public interface UiApi {

    /**
     * Adds a right-dock side tab (an IntelliJ-style tool window) showing {@code component}. If the name collides
     * with an existing tool it is made unique; the returned {@link Registration} removes the actual registered tool.
     */
    Registration addToolWindow(String name, JComponent component);

    /**
     * Opens a custom tab in the center editor area (not tied to a class/resource). {@code id} identifies the tab
     * for de-duplication and removal; opening an already-open id re-focuses it.
     */
    Registration openCenterView(String id, String title, Icon icon, JComponent view);

    /** Adds a closable tab to the bottom results panel. */
    Registration addBottomTab(String title, JComponent component);

    /**
     * Adds a menu item to the named top-level menu (found case-insensitively, or created if absent). The item runs
     * {@code action} on the EDT, guarded so a failure surfaces as a dialog rather than an uncaught exception.
     */
    Registration addMenuItem(String menuName, String itemText, Runnable action);

    /** Adds a button to the main toolbar. {@code action} is guarded like {@link #addMenuItem}. */
    Registration addToolbarButton(Icon icon, String tooltip, Runnable action);

    /**
     * Contributes context-menu entries to the navigator tree. The provider is consulted each time the menu opens
     * with the current selection; it may return an empty list to contribute nothing for that selection.
     */
    Registration addNavigatorAction(NavigatorActionProvider provider);

    /** Shows a transient message in the status bar. */
    void setStatus(String message);
}
