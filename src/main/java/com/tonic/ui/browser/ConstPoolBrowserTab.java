package com.tonic.ui.browser;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.constpool.Item;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConstPoolBrowserTab extends JPanel implements ThemeManager.ThemeChangeListener {

    private final ProjectModel projectModel;
    private ClassEntryModel currentClass;

    private JComboBox<ClassEntryModel> classSelector;
    private DefaultComboBoxModel<ClassEntryModel> classSelectorModel;
    private JButton refreshButton;

    private JTabbedPane cpViewTabs;
    private ConstPoolTableView tableView;
    private ConstPoolTreeView treeView;
    private AttributesBrowserPanel attributesPanel;
    private DetailsPanel detailsPanel;

    private JSplitPane mainSplit;
    private JSplitPane leftSplit;

    public ConstPoolBrowserTab(ProjectModel projectModel) {
        this.projectModel = projectModel;
        initComponents();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    public ConstPoolBrowserTab(ProjectModel projectModel, ClassEntryModel initialClass) {
        this.projectModel = projectModel;
        this.currentClass = initialClass;
        initComponents();
        if (initialClass != null) {
            classSelectorModel.setSelectedItem(initialClass);
            loadClass(initialClass);
        }
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgSecondary());

        add(createToolbar(), BorderLayout.NORTH);

        tableView = new ConstPoolTableView();
        treeView = new ConstPoolTreeView();
        attributesPanel = new AttributesBrowserPanel();
        detailsPanel = new DetailsPanel();

        tableView.setSelectionListener(this::onItemSelected);
        treeView.setSelectionListener(this::onItemSelected);
        attributesPanel.setSelectionListener(this::onAttributeSelected);

        cpViewTabs = new JTabbedPane(JTabbedPane.TOP);
        cpViewTabs.setBackground(JStudioTheme.getBgSecondary());
        cpViewTabs.setForeground(JStudioTheme.getTextPrimary());
        cpViewTabs.addTab("Table", createScrollPane(tableView));
        cpViewTabs.addTab("Tree", createScrollPane(treeView));

        JPanel cpPanel = new JPanel(new BorderLayout());
        cpPanel.setBackground(JStudioTheme.getBgSecondary());
        cpPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Constant Pool",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                JStudioTheme.getUIFont(11),
                JStudioTheme.getTextSecondary()
        ));
        cpPanel.add(cpViewTabs, BorderLayout.CENTER);

        JPanel attrPanel = new JPanel(new BorderLayout());
        attrPanel.setBackground(JStudioTheme.getBgSecondary());
        attrPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Attributes",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                JStudioTheme.getUIFont(11),
                JStudioTheme.getTextSecondary()
        ));
        attrPanel.add(createScrollPane(attributesPanel), BorderLayout.CENTER);

        leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cpPanel, attrPanel);
        leftSplit.setResizeWeight(0.6);
        leftSplit.setDividerSize(4);
        leftSplit.setBorder(null);
        leftSplit.setContinuousLayout(true);

        JPanel detailsContainer = new JPanel(new BorderLayout());
        detailsContainer.setBackground(JStudioTheme.getBgSecondary());
        detailsContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Details",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                JStudioTheme.getUIFont(11),
                JStudioTheme.getTextSecondary()
        ));
        detailsContainer.add(createScrollPane(detailsPanel), BorderLayout.CENTER);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, detailsContainer);
        mainSplit.setResizeWeight(0.6);
        mainSplit.setDividerSize(4);
        mainSplit.setBorder(null);
        mainSplit.setContinuousLayout(true);

        add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        JLabel classLabel = new JLabel("Class:");
        classLabel.setForeground(JStudioTheme.getTextPrimary());
        classLabel.setFont(JStudioTheme.getUIFont(11));
        toolbar.add(classLabel);

        classSelectorModel = new DefaultComboBoxModel<>();
        classSelector = new JComboBox<>(classSelectorModel);
        classSelector.setBackground(JStudioTheme.getBgTertiary());
        classSelector.setForeground(JStudioTheme.getTextPrimary());
        classSelector.setPreferredSize(new Dimension(400, 24));
        classSelector.setRenderer(new ClassComboRenderer());
        classSelector.addActionListener(e -> {
            ClassEntryModel selected = (ClassEntryModel) classSelector.getSelectedItem();
            if (selected != null && selected != currentClass) {
                loadClass(selected);
            }
        });

        populateClassSelector();
        toolbar.add(classSelector);

        refreshButton = new JButton(Icons.getIcon("refresh"));
        refreshButton.setToolTipText("Refresh");
        refreshButton.setBackground(JStudioTheme.getBgTertiary());
        refreshButton.setFocusable(false);
        refreshButton.addActionListener(e -> refresh());
        toolbar.add(refreshButton);

        return toolbar;
    }

    private void populateClassSelector() {
        classSelectorModel.removeAllElements();
        if (projectModel != null) {
            List<ClassEntryModel> classes = new ArrayList<>(projectModel.getAllClasses());
            classes.sort(Comparator.comparing(ClassEntryModel::getClassName));
            for (ClassEntryModel cls : classes) {
                classSelectorModel.addElement(cls);
            }
        }
    }

    private JScrollPane createScrollPane(java.awt.Component component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    public void loadClass(ClassEntryModel classEntry) {
        if (classEntry == null) return;
        this.currentClass = classEntry;

        ClassFile cf = classEntry.getClassFile();
        ConstPool constPool = cf.getConstPool();

        tableView.loadConstPool(constPool);
        treeView.loadConstPool(constPool);
        attributesPanel.loadClass(cf);
        detailsPanel.clear();
    }

    public void refresh() {
        if (currentClass != null) {
            loadClass(currentClass);
        }
    }

    private void onItemSelected(Item<?> item, int index) {
        if (item != null && currentClass != null) {
            detailsPanel.showItem(item, index, currentClass.getClassFile().getConstPool());
        }
    }

    private void onAttributeSelected(Attribute attribute, String context) {
        if (attribute != null && currentClass != null) {
            detailsPanel.showAttribute(attribute, context, currentClass.getClassFile().getConstPool());
        }
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgSecondary());
        repaint();
    }

    public String getTitle() {
        if (currentClass != null) {
            return currentClass.getSimpleName();
        }
        return "Class Browser";
    }

    public String getTooltip() {
        if (currentClass != null) {
            return currentClass.getClassName().replace('/', '.');
        }
        return "Browse constant pool and attributes";
    }

    public ClassEntryModel getCurrentClass() {
        return currentClass;
    }

    private static class ClassComboRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                               int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ClassEntryModel) {
                ClassEntryModel cls = (ClassEntryModel) value;
                setText(cls.getClassName().replace('/', '.'));
                setIcon(cls.getIcon());
            }
            setBackground(isSelected ? JStudioTheme.getSelection() : JStudioTheme.getBgTertiary());
            setForeground(JStudioTheme.getTextPrimary());
            return this;
        }
    }
}
