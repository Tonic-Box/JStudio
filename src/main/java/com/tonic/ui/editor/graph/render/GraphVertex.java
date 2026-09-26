package com.tonic.ui.editor.graph.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** A graph cell value pairing node data with its renderer; its label is rendered on first use and cached. */
@Getter
@RequiredArgsConstructor
public class GraphVertex<T>
{

    private final T data;
    private final GraphVertexRenderer<T> renderer;
    private String cachedHtml;

    @Override
    public String toString()
    {
        if (cachedHtml == null)
        {
            cachedHtml = renderer.renderHtml(data);
        }
        return cachedHtml;
    }

    /** Drops the cached label so the next use renders it again. */
    public void invalidateCache()
    {
        cachedHtml = null;
    }

    /**
     * Asks the renderer for the node's style.
     *
     * @return the style name
     */
    public String getStyle()
    {
        return renderer.getNodeStyle(data);
    }
}
