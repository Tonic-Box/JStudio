package com.tonic.ui;

import com.tonic.ui.core.component.ToolWindowPane;
import com.tonic.ui.layout.Stacks;
import com.tonic.ui.layout.ViewHost;
import com.tonic.ui.layout.ViewId;

/** The mover that sends a tool window into the document area or out into a window of its own. */
public final class ToolWindowMover
{

    private final ViewHost host;

    /**
     * Creates a mover over the layout the tools live in.
     *
     * @param host the layout that owns the tool views
     */
    public ToolWindowMover(ViewHost host)
    {
        this.host = host;
    }

    /**
     * Moves an open tool where the target says; a tool that is not open is left alone.
     *
     * @param title the tool's title
     * @param target where the tool goes
     */
    public void move(String title, ToolWindowPane.MoveTarget target)
    {
        final ViewId view = ToolWindowPane.view(title);
        if (!host.isOpen(view))
        {
            return;
        }
        if (target == ToolWindowPane.MoveTarget.WINDOW)
        {
            host.tearOut(view, null);
        }
        else
        {
            host.moveTo(view, Stacks.DOCUMENTS);
        }
    }
}
