package com.tonic.ui.analysis.callgraph;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** The call graph view's state: the graph, the focus method, the traversal depth and which drawn cell is which method. */
public class CallGraphModel
{

    @Getter
    private CallGraph callGraph;
    @Getter
    private MethodReference focusMethod;
    @Getter
    private int maxDepth = 3;
    private final Map<Object, MethodReference> cellToMethodMap = new HashMap<>();

    /**
     * Replaces the graph.
     *
     * @param callGraph the new graph, or null for none
     */
    public void setCallGraph(CallGraph callGraph)
    {
        this.callGraph = callGraph;
    }

    /**
     * Sets the method the view centers on.
     *
     * @param focusMethod the method, or null for none
     */
    public void setFocusMethod(MethodReference focusMethod)
    {
        this.focusMethod = focusMethod;
    }

    /**
     * Sets the traversal depth.
     *
     * @param maxDepth how many call levels to show on each side of the focus method
     */
    public void setMaxDepth(int maxDepth)
    {
        this.maxDepth = maxDepth;
    }

    /** Forgets every cell-to-method mapping. */
    public void clearCellMap()
    {
        cellToMethodMap.clear();
    }

    /**
     * Records which method a drawn cell stands for.
     *
     * @param cell the graph cell
     * @param method the method it draws
     */
    public void mapCellToMethod(Object cell, MethodReference method)
    {
        cellToMethodMap.put(cell, method);
    }

    /**
     * Looks up the method a cell stands for.
     *
     * @param cell the graph cell
     * @return the method, or null if the cell is not a mapped node
     */
    public MethodReference getMethodForCell(Object cell)
    {
        return cellToMethodMap.get(cell);
    }

    /**
     * Tells whether a cell is a mapped node.
     *
     * @param cell the graph cell
     * @return true if the cell stands for a method
     */
    public boolean hasCellMapping(Object cell)
    {
        return cellToMethodMap.containsKey(cell);
    }

    /**
     * Exposes the cell-to-method mappings.
     *
     * @return a read-only view of the mappings
     */
    public Map<Object, MethodReference> getCellToMethodMap()
    {
        return Collections.unmodifiableMap(cellToMethodMap);
    }

    /**
     * Tells whether a graph has been built.
     *
     * @return true if there is a graph
     */
    public boolean hasCallGraph()
    {
        return callGraph != null;
    }

    /**
     * Tells whether a method is focused.
     *
     * @return true if there is a focus method
     */
    public boolean hasFocusMethod()
    {
        return focusMethod != null;
    }
}
