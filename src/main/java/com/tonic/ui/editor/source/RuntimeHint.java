package com.tonic.ui.editor.source;

import java.awt.Rectangle;

/** One inline runtime-value hint: {@code name = value} on a 1-based source line, optionally expandable. */
final class RuntimeHint {

    private RuntimeHint() {
    }

    static final class HintEntry {
        final int line;          // 1-based source line to annotate
        final String text;       // inline form, value truncated, e.g. "key = 42"
        final String fullText;   // untruncated "name = value", listed in the values dialog
        final long refHandle;    // > 0 when the value is an expandable object/array
        final boolean array;     // a non-char array: gets the element preview tooltip
        final int arrayLength;   // element count when array, else 0
        Rectangle hitBox;        // painted bounds, set by the overlay (for hover/click)

        HintEntry(int line, String text, String fullText, long refHandle, boolean array, int arrayLength) {
            this.line = line;
            this.text = text;
            this.fullText = fullText;
            this.refHandle = refHandle;
            this.array = array;
            this.arrayLength = arrayLength;
        }
    }
}
