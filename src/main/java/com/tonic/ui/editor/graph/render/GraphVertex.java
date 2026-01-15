package com.tonic.ui.editor.graph.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GraphVertex<T> {

    private final T data;
    private final GraphVertexRenderer<T> renderer;
    private String cachedHtml;

    @Override
    public String toString() {
        if (cachedHtml == null) {
            cachedHtml = renderer.renderHtml(data);
        }
        return cachedHtml;
    }

    public void invalidateCache() {
        cachedHtml = null;
    }

    public String getStyle() {
        return renderer.getNodeStyle(data);
    }
}
