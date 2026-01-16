package com.tonic.ui.vm.heap;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.heap.model.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class ObjectDetailPanel extends ThemedJPanel {

    private final JLabel headerLabel;
    private final JTextArea provenanceArea;
    private final JTree fieldsTree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTextArea mutationArea;

    private HeapForensicsTracker tracker;

    public ObjectDetailPanel() {
        super(BackgroundStyle.SECONDARY, new BorderLayout(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        headerLabel = new JLabel("No object selected");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, UIConstants.FONT_SIZE_LARGE));
        headerLabel.setForeground(JStudioTheme.getTextPrimary());
        headerLabel.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));
        add(headerLabel, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());

        JPanel infoPanel = new JPanel(new BorderLayout(5, 5));
        infoPanel.setBackground(JStudioTheme.getBgSecondary());

        provenanceArea = new JTextArea(4, 30);
        provenanceArea.setEditable(false);
        provenanceArea.setBackground(JStudioTheme.getBgPrimary());
        provenanceArea.setForeground(JStudioTheme.getTextPrimary());
        provenanceArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        provenanceArea.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Provenance",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            null,
            JStudioTheme.getTextSecondary()
        ));
        infoPanel.add(new JScrollPane(provenanceArea), BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode("Fields");
        treeModel = new DefaultTreeModel(rootNode);
        fieldsTree = new JTree(treeModel);
        fieldsTree.setBackground(JStudioTheme.getBgSecondary());
        fieldsTree.setRootVisible(false);
        fieldsTree.setShowsRootHandles(true);
        fieldsTree.setCellRenderer(new FieldTreeCellRenderer());

        JScrollPane fieldsScroll = new JScrollPane(fieldsTree);
        fieldsScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Fields",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            null,
            JStudioTheme.getTextSecondary()
        ));
        fieldsScroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        infoPanel.add(fieldsScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Info", infoPanel);

        mutationArea = new JTextArea();
        mutationArea.setEditable(false);
        mutationArea.setBackground(JStudioTheme.getBgPrimary());
        mutationArea.setForeground(JStudioTheme.getTextPrimary());
        mutationArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));

        JScrollPane mutationScroll = new JScrollPane(mutationArea);
        mutationScroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        tabbedPane.addTab("Mutations", mutationScroll);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void setTracker(HeapForensicsTracker tracker) {
        this.tracker = tracker;
    }

    public void setObject(HeapObject object) {

        if (object == null) {
            headerLabel.setText("No object selected");
            provenanceArea.setText("");
            rootNode.removeAllChildren();
            treeModel.reload();
            mutationArea.setText("");
            return;
        }

        String typeIcon = "";
        if (object.isLambda()) typeIcon = "λ ";
        else if (object.isArray()) typeIcon = "[] ";
        else if (object.isString()) typeIcon = "\" ";

        headerLabel.setText(typeIcon + object.getClassName() + " #" + object.getId());

        updateProvenanceArea(object);
        updateFieldsTree(object);
        updateMutationArea(object);
    }

    private void updateProvenanceArea(HeapObject object) {
        ProvenanceInfo prov = object.getProvenance();
        if (prov == null) {
            provenanceArea.setText("Provenance not tracked");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Allocated at: ").append(prov.getMethodSignature()).append("\n");
        sb.append("PC: ").append(prov.getPc());
        if (prov.getLineNumber() > 0) {
            sb.append(" (line ").append(prov.getLineNumber()).append(")");
        }
        sb.append("\n");
        sb.append("Instruction: ").append(object.getAllocationTime());

        List<ProvenanceInfo.StackFrameInfo> callStack = prov.getCallStack();
        if (callStack != null && !callStack.isEmpty()) {
            sb.append("\n\nCall Stack:");
            for (ProvenanceInfo.StackFrameInfo frame : callStack) {
                sb.append("\n  at ").append(frame.getClassName())
                  .append(".").append(frame.getMethodName());
                if (frame.getLineNumber() > 0) {
                    sb.append(":").append(frame.getLineNumber());
                }
            }
        }

        provenanceArea.setText(sb.toString());
        provenanceArea.setCaretPosition(0);
    }

    private void updateFieldsTree(HeapObject object) {
        rootNode.removeAllChildren();

        Map<String, FieldValue> fields = object.getFields();
        if (fields.isEmpty()) {
            rootNode.add(new DefaultMutableTreeNode("(no fields)"));
        } else {
            for (FieldValue field : fields.values()) {
                DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode(field);

                if (field.hasReferenceId() && tracker != null) {
                    fieldNode.add(new DefaultMutableTreeNode("-> Object #" + field.getReferenceId()));
                }

                rootNode.add(fieldNode);
            }
        }

        treeModel.reload();
        for (int i = 0; i < fieldsTree.getRowCount(); i++) {
            fieldsTree.expandRow(i);
        }
    }

    private void updateMutationArea(HeapObject object) {
        List<MutationEvent> mutations = object.getMutations();
        if (mutations.isEmpty()) {
            mutationArea.setText("No mutations recorded");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Mutation History (").append(mutations.size()).append(" events):\n\n");

        int index = 1;
        for (MutationEvent mut : mutations) {
            sb.append("#").append(index++).append(": ");
            sb.append(mut.getFieldName());
            sb.append(" <- ");
            sb.append(formatValue(mut.getOldValue()));
            sb.append(" -> ");
            sb.append(formatValue(mut.getNewValue()));
            sb.append("\n    @ instruction ").append(mut.getInstructionCount());
            if (mut.getProvenance() != null) {
                sb.append(" in ").append(mut.getProvenance().getMethodName());
            }
            sb.append("\n\n");
        }

        mutationArea.setText(sb.toString());
        mutationArea.setCaretPosition(0);
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof com.tonic.analysis.execution.heap.ObjectInstance) {
            com.tonic.analysis.execution.heap.ObjectInstance obj =
                (com.tonic.analysis.execution.heap.ObjectInstance) value;
            return obj.getClassName() + " #" + obj.getId();
        }
        return String.valueOf(value);
    }

    private static class FieldTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            setBackgroundNonSelectionColor(JStudioTheme.getBgSecondary());
            setBackgroundSelectionColor(JStudioTheme.getAccent());
            setTextNonSelectionColor(JStudioTheme.getTextPrimary());
            setTextSelectionColor(JStudioTheme.getTextPrimary());

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof FieldValue) {
                    FieldValue fv = (FieldValue) userObject;
                    String display = fv.getName() + ": " + fv.getDisplayValue();
                    setText(display);

                    if (fv.isReference()) {
                        setForeground(JStudioTheme.getInfo());
                    }
                }
            }

            setIcon(null);
            return this;
        }
    }
}
