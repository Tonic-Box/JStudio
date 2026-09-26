package com.tonic.plugin.api.ui;

import java.util.List;

/** Supplies a plugin's navigator context-menu entries, asked afresh on the EDT each time the menu opens so they can depend on what was right-clicked. */
@FunctionalInterface
public interface NavigatorActionProvider
{

    /**
     * Returns the entries to show for a selection; a throw is swallowed and contributes nothing.
     *
     * @param context what was right-clicked
     * @return the entries in menu order; empty or null contributes nothing
     */
    List<NavigatorAction> actionsFor(NavigatorContext context);
}
