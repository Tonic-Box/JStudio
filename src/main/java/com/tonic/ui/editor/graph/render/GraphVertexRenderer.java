package com.tonic.ui.editor.graph.render;

/** Turns a graph's node data into a vertex label and a style name. */
public interface GraphVertexRenderer<T>
{

    /**
     * Renders a node's label.
     *
     * @param nodeData the node
     * @return the label as HTML
     */
    String renderHtml(T nodeData);

    /**
     * Picks a node's style.
     *
     * @param nodeData the node
     * @return the name of a style registered on the graph
     */
    String getNodeStyle(T nodeData);
}
