package com.tonic.ui.editor.cfg;

import lombok.Getter;

@Getter
public enum CFGEdgeType {
    NORMAL("#808080"),
    UNCONDITIONAL("#f39c12"),
    CONDITIONAL_TRUE("#27ae60"),
    CONDITIONAL_FALSE("#e74c3c"),
    SWITCH_DEFAULT("#1abc9c"),
    SWITCH_CASE("#9b59b6"),
    EXCEPTION("#e67e22");

    private final String color;

    CFGEdgeType(String color) {
        this.color = color;
    }

}
