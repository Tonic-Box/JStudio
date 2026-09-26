package com.tonic.ui.editor.cfg;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** An outgoing control flow edge: its target block and kind. */
@Getter
@RequiredArgsConstructor
public class CFGEdge
{
    private final CFGBlock target;
    private final CFGEdgeType type;
}
