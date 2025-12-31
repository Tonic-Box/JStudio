package com.tonic.ui.analysis.callgraph;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CallGraphModel {

    @Getter
    private CallGraph callGraph;
    @Getter
    private MethodReference focusMethod;
    @Getter
    private int maxDepth = 3;
    private final Map<Object, MethodReference> cellToMethodMap = new HashMap<>();

    public void setCallGraph(CallGraph callGraph) {
        this.callGraph = callGraph;
    }

    public void setFocusMethod(MethodReference focusMethod) {
        this.focusMethod = focusMethod;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void clearCellMap() {
        cellToMethodMap.clear();
    }

    public void mapCellToMethod(Object cell, MethodReference method) {
        cellToMethodMap.put(cell, method);
    }

    public MethodReference getMethodForCell(Object cell) {
        return cellToMethodMap.get(cell);
    }

    public boolean hasCellMapping(Object cell) {
        return cellToMethodMap.containsKey(cell);
    }

    public Map<Object, MethodReference> getCellToMethodMap() {
        return Collections.unmodifiableMap(cellToMethodMap);
    }

    public boolean hasCallGraph() {
        return callGraph != null;
    }

    public boolean hasFocusMethod() {
        return focusMethod != null;
    }
}
