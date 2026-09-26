package com.tonic.ui.editor.dual;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.SourceLineMaps;
import com.tonic.ui.editor.bytecode.BytecodeView;
import com.tonic.ui.editor.source.SourceCodeView;

import java.util.Map;
import java.util.NavigableMap;

/** The link between a bytecode pane and a source pane: a double-click on either highlights the matching lines on the other, or only its own line when no mapping exists. */
public final class SourceBytecodeLinker
{

    private final ClassEntryModel classEntry;
    private final BytecodeView bytecodeView;
    private final SourceCodeView sourceView;

    /**
     * Links two panes by installing line-activation handlers on both.
     *
     * @param classEntry the class both panes show, whose decompiler line maps drive the link
     * @param bytecodeView the bytecode pane
     * @param sourceView the source pane
     */
    public SourceBytecodeLinker(ClassEntryModel classEntry, BytecodeView bytecodeView, SourceCodeView sourceView)
    {
        this.classEntry = classEntry;
        this.bytecodeView = bytecodeView;
        this.sourceView = sourceView;
        bytecodeView.setOnLineActivated(this::onBytecodeLineActivated);
        sourceView.setOnLineActivated(this::onSourceLineActivated);
    }

    private void onBytecodeLineActivated(int displayLine)
    {
        BcLocation location = bytecodeView.locationAtLine(displayLine);
        if (location == null)
        {
            return;
        }
        int sourceLine = SourceLineMaps.sourceLineForPc(lineMapFor(location.key()), location.getPc());
        clearBoth();
        bytecodeView.addHighlight(displayLine);
        if (sourceLine > 0)
        {
            sourceView.highlightLinkedLine(sourceLine - 1);
        }
    }

    private void onSourceLineActivated(int displayLine)
    {
        int oneBasedLine = displayLine + 1;
        clearBoth();
        sourceView.highlightLinkedLine(displayLine);

        String methodKey = SourceLineMaps.methodKeyForSourceLine(classEntry.getMethodSpans(), oneBasedLine);
        if (methodKey == null)
        {
            return;
        }
        int[] span = SourceLineMaps.pcSpanForSourceLine(lineMapFor(methodKey), oneBasedLine);
        if (span != null)
        {
            bytecodeView.highlightPcSpan(methodKey, span[0], span[1]);
        }
    }

    private NavigableMap<Integer, Integer> lineMapFor(String methodKey)
    {
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        return maps == null ? null : maps.get(methodKey);
    }

    private void clearBoth()
    {
        bytecodeView.clearHighlights();
        sourceView.clearHighlight();
    }
}
