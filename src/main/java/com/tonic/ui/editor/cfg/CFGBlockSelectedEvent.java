package com.tonic.ui.editor.cfg;

import com.tonic.event.Event;
import lombok.Getter;

@Getter
public class CFGBlockSelectedEvent extends Event {
    private final CFGBlockVertex vertex;

    public CFGBlockSelectedEvent(CFGBlockVertex vertex) {
        super(vertex);
        this.vertex = vertex;
    }
}
