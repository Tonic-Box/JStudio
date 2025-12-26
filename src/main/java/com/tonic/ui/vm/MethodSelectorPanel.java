package com.tonic.ui.vm;

import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ProjectLoadedEvent;
import com.tonic.ui.event.events.ProjectUpdatedEvent;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.navigator.ClassTreeCellRenderer;
import com.tonic.ui.navigator.ClassTreeModel;
import com.tonic.ui.navigator.NavigatorNode;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class MethodSelectorPanel extends JPanel {

    private final JTree tree;
    private final ClassTreeModel treeModel;
    private final JTextField searchField;
    private final JLabel selectedLabel;
    private MethodEntryModel selectedMethod;
    private Consumer<MethodEntryModel> onMethodSelected;

    public MethodSelectorPanel() {
        this(null);
    }

    public MethodSelectorPanel(String title) {
        setLayout(new BorderLayout(5, 5));
        setBackground(JStudioTheme.getBgPrimary());

        if (title != null) {
            setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                null,
                JStudioTheme.getTextPrimary()
            ));
        }

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(JStudioTheme.getBgPrimary());

        searchField = new JTextField();
        searchField.setBackground(JStudioTheme.getBgSecondary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        searchField.setToolTipText("Filter by class or method name");

        JLabel searchIcon = new JLabel(Icons.getIcon("search", 14));
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(JStudioTheme.getBgPrimary());
        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        topPanel.add(searchPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        treeModel = new ClassTreeModel();
        tree = new JTree(treeModel);
        tree.setCellRenderer(new ClassTreeCellRenderer());
        tree.setBackground(JStudioTheme.getBgSecondary());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        ToolTipManager.sharedInstance().registerComponent(tree);

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(JStudioTheme.getBgPrimary());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        selectedLabel = new JLabel("No method selected");
        selectedLabel.setForeground(JStudioTheme.getTextSecondary());
        selectedLabel.setFont(JStudioTheme.getUIFont(11));
        bottomPanel.add(selectedLabel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        setupListeners();
        loadCurrentProject();

        EventBus.getInstance().register(ProjectLoadedEvent.class, this::onProjectLoaded);
        EventBus.getInstance().register(ProjectUpdatedEvent.class, this::onProjectUpdated);
    }

    private void onProjectLoaded(ProjectLoadedEvent event) {
        refresh();
    }

    private void onProjectUpdated(ProjectUpdatedEvent event) {
        refresh();
    }

    private void setupListeners() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterTree();
            }
        });

        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) {
                return;
            }

            Object node = path.getLastPathComponent();
            if (node instanceof NavigatorNode.MethodNode) {
                MethodEntryModel method = ((NavigatorNode.MethodNode) node).getMethodEntry();
                selectMethod(method);
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        Object node = path.getLastPathComponent();
                        if (node instanceof NavigatorNode.MethodNode) {
                            MethodEntryModel method = ((NavigatorNode.MethodNode) node).getMethodEntry();
                            selectMethod(method);
                            if (onMethodSelected != null) {
                                onMethodSelected.accept(method);
                            }
                        }
                    }
                }
            }
        });
    }

    private void filterTree() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            treeModel.clearFilter();
        } else {
            treeModel.setFilter(text);
        }
        expandAll();
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void loadCurrentProject() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            setProject(project);
        }
    }

    public void setProject(ProjectModel project) {
        if (project != null) {
            treeModel.loadProject(project);
        } else {
            treeModel.clear();
        }
    }

    public void refresh() {
        loadCurrentProject();
    }

    private void selectMethod(MethodEntryModel method) {
        this.selectedMethod = method;
        if (method != null) {
            String className = method.getMethodEntry().getOwnerName();
            int lastSlash = className.lastIndexOf('/');
            String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
            selectedLabel.setText(simpleName + "." + method.getDisplaySignature());
            selectedLabel.setForeground(JStudioTheme.getTextPrimary());
        } else {
            selectedLabel.setText("No method selected");
            selectedLabel.setForeground(JStudioTheme.getTextSecondary());
        }
    }

    public MethodEntryModel getSelectedMethod() {
        return selectedMethod;
    }

    public void setOnMethodSelected(Consumer<MethodEntryModel> callback) {
        this.onMethodSelected = callback;
    }

    public void clearSelection() {
        tree.clearSelection();
        this.selectedMethod = null;
        selectedLabel.setText("No method selected");
        selectedLabel.setForeground(JStudioTheme.getTextSecondary());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        return new Dimension(Math.max(250, pref.width), Math.max(200, pref.height));
    }
}
