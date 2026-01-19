package com.tonic.ui.editor.cfg;

import com.tonic.ui.core.component.FilterableComboBox;
import com.tonic.ui.editor.graph.BaseGraphView;
import com.tonic.ui.event.Event;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.CFGBlockSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControlFlowView extends BaseGraphView {

    private final CFGBuilder cfgBuilder;

    private FilterableComboBox<MethodEntryModel> cfgMethodSelector;

    private MethodEntryModel currentMethod;
    private List<CFGBlock> currentBlocks;
    private boolean showIR = false;

    public ControlFlowView(ClassEntryModel classEntry) {
        super(classEntry);
        this.cfgBuilder = new CFGBuilder();
        hideMethodFilter();
        populateCFGMethodSelector();
    }

    @Override
    protected void createAdditionalToolbarItems() {
        toolbar.add(new JLabel(" Method: "));
        cfgMethodSelector = new FilterableComboBox<>(m -> m.getName() + m.getMethodEntry().getDesc());
        cfgMethodSelector.setFont(JStudioTheme.getCodeFont(11));
        cfgMethodSelector.setMaximumSize(new Dimension(250, 25));
        cfgMethodSelector.setPreferredSize(new Dimension(200, 25));
        cfgMethodSelector.addActionListener(e -> onCFGMethodSelected());
        toolbar.add(cfgMethodSelector);

        toolbar.addSeparator();

        ButtonGroup modeGroup = new ButtonGroup();
        JToggleButton bytecodeToggle = new JToggleButton("Bytecode", true);
        bytecodeToggle.setFont(JStudioTheme.getCodeFont(11));
        bytecodeToggle.addActionListener(e -> {
            showIR = false;
            if (currentBlocks != null) {
                rebuildGraphInternal();
            }
        });
        modeGroup.add(bytecodeToggle);
        toolbar.add(bytecodeToggle);

        JToggleButton irToggle = new JToggleButton("IR");
        irToggle.setFont(JStudioTheme.getCodeFont(11));
        irToggle.addActionListener(e -> {
            showIR = true;
            if (currentBlocks != null) {
                rebuildGraphInternal();
            }
        });
        modeGroup.add(irToggle);
        toolbar.add(irToggle);

        toolbar.addSeparator();
    }

    private void populateCFGMethodSelector() {
        List<MethodEntryModel> methods = new ArrayList<>();
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (method.getMethodEntry().getCodeAttribute() != null) {
                methods.add(method);
            }
        }
        cfgMethodSelector.setAllItems(methods);
    }

    private void onCFGMethodSelected() {
        if (cfgMethodSelector.isFiltering()) return;
        Object selected = cfgMethodSelector.getSelectedItem();
        if (!(selected instanceof MethodEntryModel)) {
            return;
        }
        currentMethod = (MethodEntryModel) selected;
        loaded = false;
        refresh();
    }

    @Override
    protected void prepareGraphData() {
        currentBlocks = null;
        if (currentMethod != null) {
            currentBlocks = cfgBuilder.buildCFG(currentMethod.getMethodEntry());
        }
    }

    @Override
    protected void rebuildGraph() {
        rebuildGraphInternal();
    }

    private void rebuildGraphInternal() {
        clearGraph();

        if (currentBlocks == null || currentBlocks.isEmpty()) {
            return;
        }

        graph.getModel().beginUpdate();
        try {
            Map<CFGBlock, Object> cellMap = new HashMap<>();
            Object parent = graph.getDefaultParent();

            for (CFGBlock block : currentBlocks) {
                CFGBlockVertex vertex = new CFGBlockVertex(block, currentMethod.getMethodEntry(),
                        showIR, classEntry.getClassFile().getConstPool());

                String style = getBlockStyle(block);
                Object cell = graph.insertVertex(parent, null, vertex, 0, 0, 150, 60, style);
                graph.updateCellSize(cell);
                cellMap.put(block, cell);
            }

            for (CFGBlock block : currentBlocks) {
                Object source = cellMap.get(block);
                for (CFGEdge edge : block.getOutEdges()) {
                    Object target = cellMap.get(edge.getTarget());
                    if (target != null) {
                        String edgeStyle = "strokeColor=" + edge.getType().getColor();
                        graph.insertEdge(parent, null, null, source, target, edgeStyle);
                    }
                }
            }

            applyHierarchicalLayout();
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private String getBlockStyle(CFGBlock block) {
        if (block.getStartOffset() == 0) {
            return "ENTRY";
        } else if (block.isExceptionHandler()) {
            return "HANDLER";
        }
        return "BLOCK";
    }

    @Override
    protected void handleDoubleClick(MouseEvent e) {
        Object cell = graphComponent.getCellAt(e.getX(), e.getY());
        if (cell != null) {
            Object value = graph.getModel().getValue(cell);
            if (value instanceof CFGBlockVertex) {
                CFGBlockVertex vertex = (CFGBlockVertex) value;
                EventBus.getInstance().post(new CFGBlockSelectedEvent(vertex));
            }
        }
    }

    private void navigateToBytecode(int offset) {
        EventBus.getInstance().post(new NavigateToBytecodeRequest(
                classEntry, currentMethod, offset));
    }

    @Override
    protected String generateDOT() {
        if (currentBlocks == null || currentBlocks.isEmpty()) {
            return "// No CFG data available\n// Select a method to build the graph";
        }

        String methodName = currentMethod != null
            ? currentMethod.getName() + currentMethod.getMethodEntry().getDesc()
            : null;

        CFGDOTExporter exporter = new CFGDOTExporter();
        return exporter.export(currentBlocks, methodName);
    }

    @Override
    public void refresh() {
        if (!loaded && cfgMethodSelector.getItemCount() > 0 && currentMethod == null) {
            cfgMethodSelector.setSelectedIndex(0);
        } else {
            super.refresh();
        }
    }

    @Getter
    public static class NavigateToBytecodeRequest extends Event {
        private final ClassEntryModel classEntry;
        private final MethodEntryModel method;
        private final int offset;

        public NavigateToBytecodeRequest(ClassEntryModel classEntry, MethodEntryModel method, int offset) {
            super(classEntry);
            this.classEntry = classEntry;
            this.method = method;
            this.offset = offset;
        }
    }
}
