package com.tonic.plugin.api.ui;

/**
 * A single context-menu entry a plugin contributes to the navigator tree, produced by a
 * {@link NavigatorActionProvider} for a given {@link NavigatorContext}. The action runs on the EDT, guarded by the
 * host so a failure surfaces as a dialog rather than an uncaught exception.
 */
public final class NavigatorAction {

    private final String label;
    private final Runnable action;

    public NavigatorAction(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public String label() {
        return label;
    }

    public Runnable action() {
        return action;
    }
}
