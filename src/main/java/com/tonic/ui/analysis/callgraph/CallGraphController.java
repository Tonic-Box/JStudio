package com.tonic.ui.analysis.callgraph;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.util.JdkClassFilter;

import javax.swing.JComboBox;
import javax.swing.SwingWorker;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class CallGraphController {

    private final ProjectModel project;
    private final CallGraphModel model;
    private final CallGraphRenderer renderer;
    private final CallGraphContextMenu contextMenu;
    private final JComboBox<String> focusCombo;
    private final Consumer<String> statusCallback;

    public CallGraphController(ProjectModel project, CallGraphModel model,
                               CallGraphRenderer renderer, mxGraph graph,
                               mxGraphComponent graphComponent,
                               JComboBox<String> focusCombo,
                               Consumer<String> statusCallback) {
        this.project = project;
        this.model = model;
        this.renderer = renderer;
        this.focusCombo = focusCombo;
        this.statusCallback = statusCallback;

        this.contextMenu = new CallGraphContextMenu(
                graph, model,
                this::handleFocusMethod,
                this::navigateToMethod,
                statusCallback
        );

        setupMouseListeners(graphComponent);
    }

    private void setupMouseListeners(mxGraphComponent graphComponent) {
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                if (cell == null) return;

                MethodReference method = model.getMethodForCell(cell);
                if (method == null) return;

                if (e.getClickCount() == 2) {
                    handleFocusMethod(method);
                } else if (e.getClickCount() == 1) {
                    showMethodInfo(method);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                    if (cell != null && model.hasCellMapping(cell)) {
                        contextMenu.show(graphComponent, e.getX(), e.getY());
                    }
                }
            }
        });
    }

    public void buildCallGraph() {
        ClassPool classPool = project.getClassPool();
        if (classPool == null) {
            statusCallback.accept("No project loaded. Open a JAR or class file first.");
            return;
        }

        statusCallback.accept("Building call graph...");

        SwingWorker<CallGraph, Void> worker = new SwingWorker<>() {
            @Override
            protected CallGraph doInBackground() {
                return CallGraph.build(classPool);
            }

            @Override
            protected void done() {
                try {
                    CallGraph callGraph = get();
                    model.setCallGraph(callGraph);
                    populateFocusCombo();

                    if (focusCombo.getItemCount() > 1) {
                        focusCombo.setSelectedIndex(1);
                    }

                    statusCallback.accept("Call graph built: " + callGraph.size() + " methods, " +
                            callGraph.edgeCount() + " edges. Select a method to explore.");
                } catch (Exception e) {
                    statusCallback.accept("Failed to build call graph: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    public void populateFocusCombo() {
        focusCombo.removeAllItems();
        focusCombo.addItem("(Select method to focus)");

        CallGraph callGraph = model.getCallGraph();
        if (callGraph == null) return;

        for (CallGraphNode node : callGraph.getPoolNodes()) {
            MethodReference ref = node.getReference();
            if (JdkClassFilter.isJdkClass(ref.getOwner())) {
                continue;
            }
            String label = ref.getOwner() + "." + ref.getName();
            focusCombo.addItem(label);
        }
    }

    public void updateFocus() {
        CallGraph callGraph = model.getCallGraph();
        if (callGraph == null) return;

        String selected = (String) focusCombo.getSelectedItem();
        if (selected == null || selected.startsWith("(")) {
            model.setFocusMethod(null);
            return;
        }

        for (CallGraphNode node : callGraph.getPoolNodes()) {
            MethodReference ref = node.getReference();
            String label = ref.getOwner() + "." + ref.getName();
            if (label.equals(selected)) {
                model.setFocusMethod(ref);
                visualizeAndUpdateStatus();
                return;
            }
        }
    }

    public void handleFocusMethod(MethodReference method) {
        model.setFocusMethod(method);
        visualizeAndUpdateStatus();
        updateComboSelection(method);
        statusCallback.accept("Focused on: " + method.getOwner() + "." + method.getName());
    }

    public void focusOnMethod(MethodEntry method) {
        if (model.getCallGraph() == null) {
            buildCallGraph();
        }
        MethodReference ref = new MethodReference(method.getOwnerName(), method.getName(), method.getDesc());
        model.setFocusMethod(ref);
        visualizeAndUpdateStatus();

        String label = method.getOwnerName() + "." + method.getName();
        for (int i = 0; i < focusCombo.getItemCount(); i++) {
            if (label.equals(focusCombo.getItemAt(i))) {
                focusCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    public void visualizeAndUpdateStatus() {
        renderer.render();

        MethodReference focusMethod = model.getFocusMethod();
        if (focusMethod != null) {
            CallGraphRenderer.RenderStats stats = renderer.getLastRenderStats();
            statusCallback.accept("Showing call graph for: " + focusMethod.getOwner() + "." +
                    focusMethod.getName() + " (" + stats.getCallerCount() + " callers, " +
                    stats.getCalleeCount() + " callees)");
        }
    }

    public void refresh() {
        if (model.hasCallGraph()) {
            populateFocusCombo();
        }
    }

    public void onDepthChanged(int newDepth) {
        model.setMaxDepth(newDepth);
        if (model.hasFocusMethod()) {
            visualizeAndUpdateStatus();
        }
    }

    private void showMethodInfo(MethodReference method) {
        CallGraph callGraph = model.getCallGraph();
        if (callGraph == null) return;

        CallGraphNode node = callGraph.getNode(method);
        StringBuilder sb = new StringBuilder();
        sb.append(method.getOwner()).append(".")
          .append(method.getName()).append(method.getDescriptor()).append("\n");

        if (node != null) {
            sb.append("Callers: ").append(node.getCallCount()).append(", ");
            sb.append("Callees: ").append(node.getCalleeCount()).append(", ");
            sb.append("In pool: ").append(node.isInPool() ? "yes" : "no (external)");
        }

        statusCallback.accept(sb.toString());
    }

    private void navigateToMethod(MethodReference method) {
        for (ClassEntryModel classEntry : project.getUserClasses()) {
            if (classEntry.getClassName().equals(method.getOwner())) {
                for (MethodEntryModel methodModel : classEntry.getMethods()) {
                    MethodEntry me = methodModel.getMethodEntry();
                    if (me.getName().equals(method.getName()) &&
                        me.getDesc().equals(method.getDescriptor())) {
                        EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
                        return;
                    }
                }
                EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
                return;
            }
        }
        statusCallback.accept("Method not found in project: " + method.getOwner() + "." + method.getName());
    }

    private void updateComboSelection(MethodReference method) {
        String label = method.getOwner() + "." + method.getName();
        for (int i = 0; i < focusCombo.getItemCount(); i++) {
            if (label.equals(focusCombo.getItemAt(i))) {
                focusCombo.setSelectedIndex(i);
                break;
            }
        }
    }
}
