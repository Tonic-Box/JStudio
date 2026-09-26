package com.tonic.plugin.api.ui;

/** One context-menu entry a plugin adds to the navigator tree; its action runs on the EDT, and a throw shows an error dialog instead of propagating. */
public final class NavigatorAction
{

    private final String label;
    private final Runnable action;

    /**
     * Creates an entry.
     *
     * @param label the menu text
     * @param action what clicking the entry runs
     */
    public NavigatorAction(String label, Runnable action)
    {
        this.label = label;
        this.action = action;
    }

    /** @return the menu text */
    public String label()
    {
        return label;
    }

    /** @return what clicking the entry runs */
    public Runnable action()
    {
        return action;
    }
}
