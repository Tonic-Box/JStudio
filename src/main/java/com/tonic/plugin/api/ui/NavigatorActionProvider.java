package com.tonic.plugin.api.ui;

import java.util.List;

/**
 * Contributes context-menu entries to the navigator tree. Consulted each time the menu opens, with the current
 * selection, so the entries can depend on what was right-clicked.
 */
@FunctionalInterface
public interface NavigatorActionProvider {

    /** Returns the entries to show for this selection (possibly empty). */
    List<NavigatorAction> actionsFor(NavigatorContext context);
}
