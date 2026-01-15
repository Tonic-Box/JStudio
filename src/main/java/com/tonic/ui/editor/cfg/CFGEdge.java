package com.tonic.ui.editor.cfg;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CFGEdge {
    private final CFGBlock target;
    private final CFGEdgeType type;
}
