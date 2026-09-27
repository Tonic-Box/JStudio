package com.tonic.plugin.api.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

/** The UI contribution surface a UiPlugin reaches through JStudioHost.ui; call it on the EDT, and every contribution is removed automatically when the plugin is disabled, reloaded or the app exits. */
public interface UiApi
{

    /**
     * Adds a tab to the right tool-window dock, themed with the app, renaming it with a " (2)" style suffix when the name is taken.
     *
     * @param name the tab's name
     * @param component the panel to show
     * @return a handle that removes the tab, safe to call more than once
     */
    Registration addToolWindow(String name, JComponent component);

    /**
     * Opens a document tab in the center editor area, or brings the tab forward when one with the id is already open, in which case view is neither shown nor themed.
     *
     * @param id identifies the tab for de-duplication and removal
     * @param title the tab's title and tooltip
     * @param icon the tab's icon, or null
     * @param view the panel to show
     * @return a handle that closes the tab with that id, whichever call opened it; safe to call more than once
     */
    Registration openCenterView(String id, String title, Icon icon, JComponent view);

    /**
     * Adds a tab to the bottom panel and selects it, making the title unique when it is taken.
     *
     * @param title the tab's title
     * @param component the panel to show
     * @return a handle that removes the tab, safe to call more than once
     */
    Registration addBottomTab(String title, JComponent component);

    /**
     * Adds an item to the top-level menu with the given name, matched ignoring case and created when absent; a throw from the action shows an error dialog.
     *
     * @param menuName the menu's text
     * @param itemText the item's text
     * @param action run on the EDT when the item is clicked
     * @return a handle that removes the item, and the menu too when this API created it and it is now empty; safe to call more than once
     * @throws IllegalStateException if the main window has no menu bar
     */
    Registration addMenuItem(String menuName, String itemText, Runnable action);

    /**
     * Adds a button to the main toolbar; a throw from the action shows an error dialog.
     *
     * @param icon the button's icon
     * @param tooltip the button's tooltip
     * @param action run on the EDT when the button is clicked
     * @return a handle that removes the button, safe to call more than once
     */
    Registration addToolbarButton(Icon icon, String tooltip, Runnable action);

    /**
     * Adds entries to the navigator tree's context menu, asking the provider each time the menu opens; a throw from an entry's action shows an error dialog.
     *
     * @param provider supplies the entries for each right-clicked node
     * @return a handle that removes the provider, safe to call more than once
     */
    Registration addNavigatorAction(NavigatorActionProvider provider);

    /**
     * Shows a message in the status bar.
     *
     * @param message the text to show
     */
    void setStatus(String message);
}
