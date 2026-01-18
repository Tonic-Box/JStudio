package com.tonic.ui.editor.callgraph;

import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxStylesheet;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.ClassPool;
import com.tonic.ui.core.component.FilterableComboBox;
import com.tonic.ui.editor.graph.BaseGraphView;
import com.tonic.ui.editor.graph.render.GraphVertex;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CallGraphView extends BaseGraphView {

    private FilterableComboBox<MethodEntryModel> methodSelector;
    private JSpinner depthSpinner;

    private ProjectModel projectModel;
    private CallGraph callGraph;
    private MethodEntryModel currentMethod;
    private MethodReference focusRef;

    private Set<MethodReference> callers = Collections.emptySet();
    private Set<MethodReference> callees = Collections.emptySet();

    private String prepareError = null;
    private int pendingDepth = 3;

    public CallGraphView(ClassEntryModel classEntry) {
        super(classEntry);
        hideMethodFilter();
        populateMethodSelector();
    }

    public void setProjectModel(ProjectModel projectModel) {
        this.projectModel = projectModel;
        this.callGraph = null;
    }

    @Override
    protected void createAdditionalToolbarItems() {
        toolbar.add(new JLabel(" Method: "));
        methodSelector = new FilterableComboBox<>(m -> m.getName() + m.getMethodEntry().getDesc());
        methodSelector.setFont(JStudioTheme.getCodeFont(11));
        methodSelector.setMaximumSize(new Dimension(250, 25));
        methodSelector.setPreferredSize(new Dimension(200, 25));
        methodSelector.addActionListener(e -> onMethodSelected());
        toolbar.add(methodSelector);

        toolbar.addSeparator();

        toolbar.add(new JLabel(" Depth: "));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(3, 1, 25, 1);
        depthSpinner = new JSpinner(spinnerModel);
        depthSpinner.setFont(JStudioTheme.getCodeFont(11));
        depthSpinner.setMaximumSize(new Dimension(60, 25));
        depthSpinner.addChangeListener(e -> onSettingsChanged());
        toolbar.add(depthSpinner);

        toolbar.addSeparator();
    }

    @Override
    protected void setupGraphStyles() {
        super.setupGraphStyles();
        mxStylesheet stylesheet = graph.getStylesheet();

        Map<String, Object> focusStyle = new HashMap<>(stylesheet.getStyles().get("NODE"));
        focusStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getAccent()));
        focusStyle.put(mxConstants.STYLE_STROKEWIDTH, 3);
        stylesheet.putCellStyle("FOCUS", focusStyle);

        Map<String, Object> externalStyle = new HashMap<>(stylesheet.getStyles().get("NODE"));
        externalStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getBgTertiary()));
        externalStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextSecondary()));
        externalStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getTextSecondary()));
        stylesheet.putCellStyle("EXTERNAL", externalStyle);
    }

    private void populateMethodSelector() {
        List<MethodEntryModel> methods = new ArrayList<>();
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (method.getMethodEntry().getCodeAttribute() != null) {
                methods.add(method);
            }
        }
        methodSelector.setAllItems(methods);
    }

    private void onMethodSelected() {
        if (initializing) return;
        Object selected = methodSelector.getSelectedItem();
        if (!(selected instanceof MethodEntryModel)) {
            return;
        }
        currentMethod = (MethodEntryModel) selected;
        pendingDepth = (Integer) depthSpinner.getValue();
        loaded = false;
        refresh();
    }

    private void onSettingsChanged() {
        if (initializing || currentMethod == null) return;
        pendingDepth = (Integer) depthSpinner.getValue();
        loaded = false;
        refresh();
    }

    private void ensureCallGraph() {
        if (callGraph == null) {
            ClassPool pool = new ClassPool(true);
            if (projectModel != null && projectModel.getClassPool() != null) {
                for (com.tonic.parser.ClassFile cf : projectModel.getClassPool().getClasses()) {
                    if (projectModel.isUserClass(cf.getClassName())) {
                        pool.put(cf);
                    }
                }
            } else {
                pool.put(classEntry.getClassFile());
            }
            callGraph = CallGraph.build(pool);
        }
    }

    private boolean isUserDefinedClass(MethodReference ref) {
        if (projectModel == null) {
            return true;
        }
        return projectModel.isUserClass(ref.getOwner());
    }

    @Override
    protected void prepareGraphData() {
        callers = Collections.emptySet();
        callees = Collections.emptySet();
        focusRef = null;
        prepareError = null;

        if (currentMethod == null) {
            return;
        }

        try {
            ensureCallGraph();

            focusRef = new MethodReference(
                classEntry.getClassFile().getClassName(),
                currentMethod.getName(),
                currentMethod.getMethodEntry().getDesc()
            );

            callers = collectCallers(callGraph, focusRef, pendingDepth);
            callees = collectCallees(callGraph, focusRef, pendingDepth);
        } catch (Exception e) {
            prepareError = "Failed to build call graph: " + e.getMessage();
        }
    }

    private Set<MethodReference> collectCallers(CallGraph graph, MethodReference method, int depth) {
        Set<MethodReference> result = new LinkedHashSet<>();
        collectCallersRecursive(graph, method, depth, result);
        return result;
    }

    private void collectCallersRecursive(CallGraph graph, MethodReference method,
                                          int depth, Set<MethodReference> result) {
        if (depth <= 0) return;
        Set<MethodReference> methodCallers = graph.getCallers(method);
        for (MethodReference caller : methodCallers) {
            if (result.add(caller)) {
                if (isUserDefinedClass(caller)) {
                    collectCallersRecursive(graph, caller, depth - 1, result);
                }
            }
        }
    }

    private Set<MethodReference> collectCallees(CallGraph graph, MethodReference method, int depth) {
        Set<MethodReference> result = new LinkedHashSet<>();
        collectCalleesRecursive(graph, method, depth, result);
        return result;
    }

    private void collectCalleesRecursive(CallGraph graph, MethodReference method,
                                          int depth, Set<MethodReference> result) {
        if (depth <= 0) return;
        Set<MethodReference> methodCallees = graph.getCallees(method);
        for (MethodReference callee : methodCallees) {
            if (result.add(callee)) {
                if (isUserDefinedClass(callee)) {
                    collectCalleesRecursive(graph, callee, depth - 1, result);
                }
            }
        }
    }

    @Override
    protected void rebuildGraph() {
        clearGraph();

        if (prepareError != null) {
            showError(prepareError);
            return;
        }

        if (focusRef == null) {
            return;
        }

        graph.getModel().beginUpdate();
        try {
            Object parent = graph.getDefaultParent();
            Map<MethodReference, Object> nodeMap = new HashMap<>();

            CallGraphVertexRenderer focusRenderer = new CallGraphVertexRenderer(callGraph, focusRef, true);
            GraphVertex<MethodReference> focusVertex = new GraphVertex<>(focusRef, focusRenderer);
            Object focusCell = graph.insertVertex(parent, null, focusVertex, 0, 0, 150, 50, "FOCUS");
            graph.updateCellSize(focusCell);
            nodeMap.put(focusRef, focusCell);

            for (MethodReference caller : callers) {
                if (!nodeMap.containsKey(caller)) {
                    createNode(parent, caller, nodeMap);
                }
            }

            for (MethodReference callee : callees) {
                if (!nodeMap.containsKey(callee)) {
                    createNode(parent, callee, nodeMap);
                }
            }

            createCallerEdges(parent, callers, nodeMap);
            createCalleeEdges(parent, callees, nodeMap);

            applyHierarchicalLayout();
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void createNode(Object parent, MethodReference ref, Map<MethodReference, Object> nodeMap) {
        CallGraphVertexRenderer renderer = new CallGraphVertexRenderer(callGraph, focusRef, false);
        GraphVertex<MethodReference> vertex = new GraphVertex<>(ref, renderer);

        String style = isExternal(ref) ? "EXTERNAL" : "NODE";
        Object cell = graph.insertVertex(parent, null, vertex, 0, 0, 150, 50, style);
        graph.updateCellSize(cell);
        nodeMap.put(ref, cell);
    }

    private boolean isExternal(MethodReference ref) {
        return callGraph.getNode(ref) == null;
    }

    private void createCallerEdges(Object parent, Set<MethodReference> callerSet,
                                    Map<MethodReference, Object> nodeMap) {
        for (MethodReference caller : callerSet) {
            if (callGraph.calls(caller, focusRef)) {
                String edgeStyle = getEdgeStyle(caller, focusRef);
                graph.insertEdge(parent, null, "", nodeMap.get(caller), nodeMap.get(focusRef), edgeStyle);
            }

            for (MethodReference otherCaller : callerSet) {
                if (!caller.equals(otherCaller) && callGraph.calls(caller, otherCaller)) {
                    String edgeStyle = getEdgeStyle(caller, otherCaller);
                    graph.insertEdge(parent, null, "", nodeMap.get(caller), nodeMap.get(otherCaller), edgeStyle);
                }
            }
        }
    }

    private void createCalleeEdges(Object parent, Set<MethodReference> calleeSet,
                                    Map<MethodReference, Object> nodeMap) {
        for (MethodReference callee : calleeSet) {
            if (callGraph.calls(focusRef, callee)) {
                String edgeStyle = getEdgeStyle(focusRef, callee);
                graph.insertEdge(parent, null, "", nodeMap.get(focusRef), nodeMap.get(callee), edgeStyle);
            }

            for (MethodReference otherCallee : calleeSet) {
                if (!callee.equals(otherCallee) && callGraph.calls(callee, otherCallee)) {
                    String edgeStyle = getEdgeStyle(callee, otherCallee);
                    graph.insertEdge(parent, null, "", nodeMap.get(callee), nodeMap.get(otherCallee), edgeStyle);
                }
            }
        }
    }

    private String getEdgeStyle(MethodReference caller, MethodReference callee) {
        String strokeColor = "#888888";

        CallGraphNode node = callGraph.getNode(caller);
        if (node != null) {
            for (CallSite site : node.getOutgoingCalls()) {
                if (site.getTarget().equals(callee)) {
                    switch (site.getInvokeType()) {
                        case VIRTUAL:
                            strokeColor = "#00DD00";
                            break;
                        case STATIC:
                            strokeColor = "#00DDDD";
                            break;
                        case SPECIAL:
                            strokeColor = "#DDDD00";
                            break;
                        case INTERFACE:
                            strokeColor = "#DD00DD";
                            break;
                        case DYNAMIC:
                            strokeColor = "#DD8800";
                            break;
                    }
                    break;
                }
            }
        }

        return mxConstants.STYLE_STROKECOLOR + "=" + strokeColor + ";" +
               mxConstants.STYLE_STROKEWIDTH + "=2;" +
               mxConstants.STYLE_ENDARROW + "=" + mxConstants.ARROW_CLASSIC + ";" +
               mxConstants.STYLE_ROUNDED + "=1;";
    }

    @Override
    protected String generateDOT() {
        if (focusRef == null) {
            return "// No method selected\n// Select a method from the dropdown";
        }

        CallGraphDOTExporter exporter = new CallGraphDOTExporter();
        return exporter.export(focusRef, callers, callees, callGraph);
    }

    @Override
    public void refresh() {
        if (!loaded && methodSelector.getItemCount() > 0 && currentMethod == null) {
            methodSelector.setSelectedIndex(0);
        } else {
            super.refresh();
        }
    }
}
