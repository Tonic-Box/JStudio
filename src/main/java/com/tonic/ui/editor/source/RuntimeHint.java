package com.tonic.ui.editor.source;

import java.awt.Rectangle;

final class RuntimeHint
{

    private RuntimeHint()
    {
    }

    static final class HintEntry
    {
        final int line;
        final String text;
        final String fullText;
        final long refHandle;
        final boolean array;
        final int arrayLength;
        Rectangle hitBox;

        HintEntry(int line, String text, String fullText, long refHandle, boolean array, int arrayLength)
        {
            this.line = line;
            this.text = text;
            this.fullText = fullText;
            this.refHandle = refHandle;
            this.array = array;
            this.arrayLength = arrayLength;
        }
    }
}
