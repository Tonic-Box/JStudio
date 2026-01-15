package com.tonic.ui.editor.graph.render;

public interface GraphVertexRenderer<T> {

    String renderHtml(T nodeData);

    String getNodeStyle(T nodeData);
}
