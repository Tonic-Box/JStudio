package com.tonic.ui.vm.dialog.result;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.MethodCall;
import com.tonic.ui.vm.testgen.TestGeneratorDialog;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class CallTracePanel extends ThemedJPanel {

    private final JToggleButton treeViewBtn;
    private final JToggleButton listViewBtn;
    private final JTextField filterField;
    private final JButton expandAllBtn;
    private final JButton collapseAllBtn;

    private final CardLayout cardLayout;
    private final JPanel viewContainer;

    private final JTree callTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    private final JTextPane listPane;
    private final StyledDocument listDoc;

    private List<MethodCall> currentCalls = new ArrayList<>();

    private static Color entryColor() {
        return JStudioTheme.getInfo();
    }

    private static Color exitColor() {
        return JStudioTheme.getSuccess();
    }

    private static Color exceptionColor() {
        return JStudioTheme.getError();
    }

    public CallTracePanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL + 1, 3));
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        ButtonGroup viewGroup = new ButtonGroup();
        treeViewBtn = new JToggleButton("Tree", true);
        treeViewBtn.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        treeViewBtn.addActionListener(e -> showTreeView());

        listViewBtn = new JToggleButton("List");
        listViewBtn.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        listViewBtn.addActionListener(e -> showListView());

        viewGroup.add(treeViewBtn);
        viewGroup.add(listViewBtn);

        filterField = new JTextField(15);
        filterField.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        filterField.setToolTipText("Filter by method name");
        filterField.addActionListener(e -> applyFilter());

        expandAllBtn = new JButton("Expand All");
        expandAllBtn.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        expandAllBtn.addActionListener(e -> expandAll());

        collapseAllBtn = new JButton("Collapse");
        collapseAllBtn.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        collapseAllBtn.addActionListener(e -> collapseAll());

        toolbar.add(treeViewBtn);
        toolbar.add(listViewBtn);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(new JLabel("Filter:"));
        toolbar.add(filterField);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(expandAllBtn);
        toolbar.add(collapseAllBtn);

        add(toolbar, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        viewContainer = new JPanel(cardLayout);
        viewContainer.setBackground(JStudioTheme.getBgPrimary());

        rootNode = new DefaultMutableTreeNode("Call Trace");
        treeModel = new DefaultTreeModel(rootNode);
        callTree = new JTree(treeModel);
        callTree.setBackground(JStudioTheme.getBgPrimary());
        callTree.setForeground(JStudioTheme.getTextPrimary());
        callTree.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        callTree.setCellRenderer(new CallTreeCellRenderer());
        callTree.setRootVisible(false);
        callTree.setShowsRootHandles(true);
        callTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleTreePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleTreePopup(e);
            }
        });

        JScrollPane treeScroll = new JScrollPane(callTree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        treeScroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        viewContainer.add(treeScroll, "tree");

        listPane = new JTextPane();
        listPane.setEditable(false);
        listPane.setBackground(JStudioTheme.getBgPrimary());
        listPane.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        listDoc = listPane.getStyledDocument();
        initListStyles();

        JScrollPane listScroll = new JScrollPane(listPane);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        viewContainer.add(listScroll, "list");

        add(viewContainer, BorderLayout.CENTER);

        showEmpty();
    }

    private void initListStyles() {
        Style defaultStyle = listPane.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, JStudioTheme.getTextPrimary());

        Style entryStyle = listPane.addStyle("entry", defaultStyle);
        StyleConstants.setForeground(entryStyle, entryColor());

        Style exitStyle = listPane.addStyle("exit", defaultStyle);
        StyleConstants.setForeground(exitStyle, exitColor());

        Style exceptionStyle = listPane.addStyle("exception", defaultStyle);
        StyleConstants.setForeground(exceptionStyle, exceptionColor());
        StyleConstants.setBold(exceptionStyle, true);
    }

    public void showEmpty() {
        rootNode.removeAllChildren();
        treeModel.reload();
        try {
            listDoc.remove(0, listDoc.getLength());
            listDoc.insertString(0, "(No trace data)", listPane.getStyle("default"));
        } catch (BadLocationException ignored) {}
        currentCalls = new ArrayList<>();
    }

    public void update(List<MethodCall> calls) {
        this.currentCalls = calls != null ? new ArrayList<>(calls) : new ArrayList<>();
        buildTreeView();
        buildListView();
    }

    private void buildTreeView() {
        rootNode.removeAllChildren();

        if (currentCalls.isEmpty()) {
            treeModel.reload();
            return;
        }

        Deque<DefaultMutableTreeNode> stack = new ArrayDeque<>();
        stack.push(rootNode);

        for (MethodCall call : currentCalls) {
            int depth = call.getDepth();

            while (stack.size() > depth + 1) {
                stack.pop();
            }

            DefaultMutableTreeNode node = new DefaultMutableTreeNode(call);
            stack.peek().add(node);
            stack.push(node);
        }

        treeModel.reload();
        expandAll();
    }

    private void buildListView() {
        try {
            listDoc.remove(0, listDoc.getLength());

            if (currentCalls.isEmpty()) {
                listDoc.insertString(0, "(No trace data)", listPane.getStyle("default"));
                return;
            }

            for (MethodCall call : currentCalls) {
                StringBuilder indent = new StringBuilder();
                for (int i = 0; i < call.getDepth(); i++) {
                    indent.append("  ");
                }

                String arrow = "\u2192 ";
                Style style = listPane.getStyle("entry");

                String line = indent + arrow + call.getShortSignature();

                Object[] args = call.getArguments();
                if (args.length > 0) {
                    line += "(";
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) line += ", ";
                        line += formatArg(args[i]);
                    }
                    line += ")";
                }

                line += "\n";
                listDoc.insertString(listDoc.getLength(), line, style);

                if (call.getReturnValue() != null || call.isExceptional()) {
                    String returnLine = indent + "\u2190 " + call.getShortSignature();
                    if (call.isExceptional()) {
                        returnLine += " [EXCEPTION]";
                        style = listPane.getStyle("exception");
                    } else {
                        returnLine += " \u2192 " + formatArg(call.getReturnValue());
                        style = listPane.getStyle("exit");
                    }
                    returnLine += "\n";
                    listDoc.insertString(listDoc.getLength(), returnLine, style);
                }
            }

            listPane.setCaretPosition(0);
        } catch (BadLocationException ignored) {}
    }

    private String formatArg(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof String) {
            String s = (String) arg;
            if (s.length() > 30) return "\"" + s.substring(0, 27) + "...\"";
            return "\"" + s + "\"";
        }
        if (arg instanceof Character) return "'" + arg + "'";
        return String.valueOf(arg);
    }

    private void showTreeView() {
        cardLayout.show(viewContainer, "tree");
        expandAllBtn.setEnabled(true);
        collapseAllBtn.setEnabled(true);
    }

    private void showListView() {
        cardLayout.show(viewContainer, "list");
        expandAllBtn.setEnabled(false);
        collapseAllBtn.setEnabled(false);
    }

    private void expandAll() {
        for (int i = 0; i < callTree.getRowCount(); i++) {
            callTree.expandRow(i);
        }
    }

    private void collapseAll() {
        for (int i = callTree.getRowCount() - 1; i >= 0; i--) {
            callTree.collapseRow(i);
        }
    }

    private void applyFilter() {
        String filter = filterField.getText().toLowerCase().trim();
        if (filter.isEmpty()) {
            buildTreeView();
            buildListView();
            return;
        }

        List<MethodCall> filtered = new ArrayList<>();
        for (MethodCall call : currentCalls) {
            if (call.getMethodName().toLowerCase().contains(filter) ||
                call.getOwnerClass().toLowerCase().contains(filter)) {
                filtered.add(call);
            }
        }

        List<MethodCall> original = currentCalls;
        currentCalls = filtered;
        buildTreeView();
        buildListView();
        currentCalls = original;
    }

    private void handleTreePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }

        TreePath path = callTree.getClosestPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        callTree.setSelectionPath(path);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (!(userObj instanceof MethodCall)) {
            return;
        }

        MethodCall call = (MethodCall) userObj;

        JPopupMenu popup = new JPopupMenu();
        JMenuItem generateTestItem = new JMenuItem("Generate JUnit Test...");
        generateTestItem.addActionListener(ev -> openTestDialogForCall(call));
        popup.add(generateTestItem);

        popup.show(callTree, e.getX(), e.getY());
    }

    private void openTestDialogForCall(MethodCall call) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        TestGeneratorDialog dialog = new TestGeneratorDialog(owner);
        dialog.setMethodCall(call);
        dialog.setVisible(true);
    }

    private class CallTreeCellRenderer extends DefaultTreeCellRenderer {
        public CallTreeCellRenderer() {
            setBackgroundNonSelectionColor(JStudioTheme.getBgPrimary());
            setBackgroundSelectionColor(JStudioTheme.getSelection());
            setTextNonSelectionColor(JStudioTheme.getTextPrimary());
            setTextSelectionColor(JStudioTheme.getTextPrimary());
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(null);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof MethodCall) {
                    MethodCall call = (MethodCall) userObj;
                    StringBuilder text = new StringBuilder();
                    text.append(call.getShortSignature());

                    Object ret = call.getReturnValue();
                    if (ret != null) {
                        text.append(" \u2192 ").append(formatArg(ret));
                    }

                    long durationNanos = call.getDurationNanos();
                    if (durationNanos > 0) {
                        double ms = durationNanos / 1_000_000.0;
                        text.append(String.format("  [%.2fms]", ms));
                    }

                    setText(text.toString());

                    if (!sel) {
                        if (call.isExceptional()) {
                            setForeground(exceptionColor());
                        } else {
                            setForeground(entryColor());
                        }
                    }
                }
            }

            return this;
        }
    }
}
