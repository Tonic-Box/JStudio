package com.tonic.ui.editor.cfg;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when a block is selected in the control flow graph. */
@Getter
public class CFGBlockSelectedEvent extends Event
{
    private final CFGBlockVertex vertex;

    /**
     * Creates the event.
     *
     * @param vertex the selected block vertex
     */
    public CFGBlockSelectedEvent(CFGBlockVertex vertex)
    {
        super(vertex);
        this.vertex = vertex;
    }
}
