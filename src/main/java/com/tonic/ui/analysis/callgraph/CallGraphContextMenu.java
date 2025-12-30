package com.tonic.ui.analysis.callgraph;

import com.mxgraph.view.mxGraph;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.Set;
import java.util.function.Consumer;

public class CallGraphContextMenu {

    private final JPopupMenu menu;
    private final mxGraph graph;
    private final CallGraphModel model;
    private final Consumer<MethodReference> onFocusMethod;
    private final Consumer<MethodReference> onNavigate;
    private final Consumer<String> statusCallback;

    public CallGraphContextMenu(mxGraph graph, CallGraphModel model,
                                Consumer<MethodReference> onFocusMethod,
                                Consumer<MethodReference> onNavigate,
                                Consumer<String> statusCallback) {
        this.graph = graph;
        this.model = model;
        this.onFocusMethod = onFocusMethod;
        this.onNavigate = onNavigate;
        this.statusCallback = statusCallback;
        this.menu = createMenu();
    }

    private JPopupMenu createMenu() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(JStudioTheme.getBgSecondary());
        popup.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        popup.add(createFocusMenuItem());
        popup.add(createShowCallersMenuItem());
        popup.add(createShowCalleesMenuItem());
        popup.add(createNavigateMenuItem());

        return popup;
    }

    private JMenuItem createFocusMenuItem() {
        JMenuItem item = createMenuItem("Focus on this method");
        item.addActionListener(e -> {
            MethodReference method = getSelectedMethod();
            if (method != null) {
                onFocusMethod.accept(method);
            }
        });
        return item;
    }

    private JMenuItem createShowCallersMenuItem() {
        JMenuItem item = createMenuItem("Show all callers");
        item.addActionListener(e -> {
            MethodReference method = getSelectedMethod();
            CallGraph callGraph = model.getCallGraph();
            if (method != null && callGraph != null) {
                Set<MethodReference> callers = callGraph.getCallers(method);
                StringBuilder sb = new StringBuilder();
                sb.append("Callers of ").append(method.getName()).append(":\n");
                for (MethodReference caller : callers) {
                    sb.append("  - ").append(caller.getOwner())
                      .append(".").append(caller.getName()).append("\n");
                }
                statusCallback.accept(sb.toString());
            }
        });
        return item;
    }

    private JMenuItem createShowCalleesMenuItem() {
        JMenuItem item = createMenuItem("Show all callees");
        item.addActionListener(e -> {
            MethodReference method = getSelectedMethod();
            CallGraph callGraph = model.getCallGraph();
            if (method != null && callGraph != null) {
                Set<MethodReference> callees = callGraph.getCallees(method);
                StringBuilder sb = new StringBuilder();
                sb.append("Callees of ").append(method.getName()).append(":\n");
                for (MethodReference callee : callees) {
                    sb.append("  - ").append(callee.getOwner())
                      .append(".").append(callee.getName()).append("\n");
                }
                statusCallback.accept(sb.toString());
            }
        });
        return item;
    }

    private JMenuItem createNavigateMenuItem() {
        JMenuItem item = createMenuItem("Navigate to source");
        item.addActionListener(e -> {
            MethodReference method = getSelectedMethod();
            if (method != null) {
                onNavigate.accept(method);
            }
        });
        return item;
    }

    private JMenuItem createMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        return item;
    }

    private MethodReference getSelectedMethod() {
        Object cell = graph.getSelectionCell();
        return cell != null ? model.getMethodForCell(cell) : null;
    }

    public void show(java.awt.Component invoker, int x, int y) {
        menu.show(invoker, x, y);
    }
}
