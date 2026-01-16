package com.tonic.ui.event.events;

import com.tonic.ui.editor.cfg.CFGBlockVertex;
import com.tonic.ui.event.Event;
import lombok.Getter;

@Getter
public class CFGBlockSelectedEvent extends Event {
    private final CFGBlockVertex vertex;

    public CFGBlockSelectedEvent(CFGBlockVertex vertex) {
        super(vertex);
        this.vertex = vertex;
    }
}
