package com.tonic.ui.editor;

import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.editor.resource.ResourceEditorTab;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabbed editor panel for viewing classes.
 */
public class EditorPanel extends ThemedJPanel {
    private final JTabbedPane tabbedPane;
    private final Map<String, EditorTab> openTabs = new HashMap<>();
    private final Map<String, ResourceEditorTab> openResourceTabs = new HashMap<>();
    /** Plugin-contributed center tabs, keyed by the plugin-supplied view id. */
    private final Map<String, JComponent> customViews = new HashMap<>();
    /** Optional per-id close hook for a custom view, run when it is closed (by the tab's close button or programmatically). */
    private final Map<String, Runnable> customViewCloseHooks = new HashMap<>();
    private static final int DRAG_THRESHOLD = 5;
    private ProjectModel projectModel;
    private final WelcomeTab welcomeTab;

    private boolean omitAnnotations = false;

    public EditorPanel(MainFrame mainFrame) {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        welcomeTab = new WelcomeTab(mainFrame);
        tabbedPane.addTab("Welcome", Icons.getIcon("home"), welcomeTab);
        tabbedPane.setTabComponentAt(0, createWelcomeTabComponent());

        add(tabbedPane, BorderLayout.CENTER);
    }

    @Override
    protected void applyChildThemes() {
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
    }

    private JPanel createWelcomeTabComponent() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
        panel.setOpaque(false);

        JLabel iconLabel = new JLabel(Icons.getIcon("home"));
        panel.add(iconLabel);

        JLabel titleLabel = new JLabel("Welcome");
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        panel.add(titleLabel);

        // No close button - this tab cannot be closed

        // Click on panel/icon/title should select tab
        MouseAdapter tabSelector = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = tabbedPane.indexOfTabComponent(panel);
                if (index != -1) {
                    tabbedPane.setSelectedIndex(index);
                }
            }
        };
        panel.addMouseListener(tabSelector);
        iconLabel.addMouseListener(tabSelector);
        titleLabel.addMouseListener(tabSelector);

        return panel;
    }

    /**
     * Open a class in a new or existing tab.
     */
    public void openClass(ClassEntryModel classEntry, ViewMode viewMode) {
        String key = classEntry.getClassName();

        // Check if already open
        EditorTab existingTab = openTabs.get(key);
        if (existingTab != null) {
            // Switch to existing tab
            int index = findTabIndex(existingTab);
            if (index >= 0) {
                tabbedPane.setSelectedIndex(index);
                existingTab.setViewMode(viewMode);
            }
            return;
        }

        // Create new tab
        EditorTab tab = new EditorTab(classEntry);
        tab.setViewMode(viewMode);
        tab.setOmitAnnotations(omitAnnotations);
        if (projectModel != null) {
            tab.setProjectModel(projectModel);
        }
        openTabs.put(key, tab);

        // Add tab with close button
        tabbedPane.addTab(tab.getTitle(), Icons.getIcon(classEntry.getIconKey()), tab, tab.getTooltip());
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, createTabComponent(tab));
        tabbedPane.setSelectedIndex(index);
    }

    public void openResource(ResourceEntryModel resource) {
        String key = resource.getPath();

        ResourceEditorTab existingTab = openResourceTabs.get(key);
        if (existingTab != null) {
            int index = findResourceTabIndex(existingTab);
            if (index >= 0) {
                tabbedPane.setSelectedIndex(index);
            }
            return;
        }

        ResourceEditorTab tab = new ResourceEditorTab(resource);
        openResourceTabs.put(key, tab);

        tabbedPane.addTab(tab.getTitle(), Icons.getIcon(resource.getIconKey()), tab, tab.getTooltip());
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, createResourceTabComponent(tab));
        tabbedPane.setSelectedIndex(index);
    }

    private int findResourceTabIndex(ResourceEditorTab tab) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) == tab) {
                return i;
            }
        }
        return -1;
    }

    public void closeResourceTab(ResourceEditorTab tab) {
        int index = findResourceTabIndex(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            openResourceTabs.remove(tab.getResource().getPath());

            if (openTabs.isEmpty() && openResourceTabs.isEmpty()) {
                tabbedPane.setSelectedIndex(0);
            }
        }
    }

    /**
     * Opens a plugin-contributed center tab (not tied to a class/resource). Opening an already-open {@code id}
     * re-focuses its tab. The {@code icon} may be null.
     */
    public void openCustomView(String id, String title, Icon icon, JComponent view) {
        openCustomView(id, title, icon, view, null);
    }

    /**
     * As {@link #openCustomView(String, String, Icon, JComponent)}, but {@code onClose} (nullable) runs when the view
     * is closed - by the tab's close button, {@link #closeCustomView(String)}, or {@link #closeAllTabs()}.
     */
    public void openCustomView(String id, String title, Icon icon, JComponent view, Runnable onClose) {
        JComponent existing = customViews.get(id);
        if (existing != null) {
            int index = findComponentIndex(existing);
            if (index >= 0) {
                tabbedPane.setSelectedIndex(index);
            }
            return;
        }

        customViews.put(id, view);
        if (onClose != null) {
            customViewCloseHooks.put(id, onClose);
        }
        tabbedPane.addTab(title, icon, view, title);
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, createCustomTabComponent(id, title, icon));
        tabbedPane.setSelectedIndex(index);
    }

    /** Closes a plugin-contributed center tab (running its close hook, if any). No-op if {@code id} is not open. */
    public void closeCustomView(String id) {
        JComponent view = customViews.remove(id);
        if (view == null) {
            return;
        }
        int index = findComponentIndex(view);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            if (openTabs.isEmpty() && openResourceTabs.isEmpty() && customViews.isEmpty()) {
                tabbedPane.setSelectedIndex(0);
            }
        }
        Runnable onClose = customViewCloseHooks.remove(id);
        if (onClose != null) {
            onClose.run();
        }
    }

    private int findComponentIndex(Component component) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) == component) {
                return i;
            }
        }
        return -1;
    }

    private JPanel createCustomTabComponent(String id, String title, Icon icon) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
        panel.setOpaque(false);

        if (icon != null) {
            panel.add(new JLabel(icon));
        }

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        panel.add(titleLabel);

        JButton closeButton = new JButton(Icons.getIcon("close"));
        closeButton.setPreferredSize(new Dimension(UIConstants.ICON_SIZE_SMALL, UIConstants.ICON_SIZE_SMALL));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setContentAreaFilled(true);
                closeButton.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setContentAreaFilled(false);
            }
        });
        closeButton.addActionListener(e -> closeCustomView(id));
        panel.add(closeButton);

        MouseAdapter tabClickListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = tabbedPane.indexOfTabComponent(panel);
                if (index != -1) {
                    tabbedPane.setSelectedIndex(index);
                }
            }
        };
        panel.addMouseListener(tabClickListener);
        titleLabel.addMouseListener(tabClickListener);

        installTabDragHandler(panel, titleLabel);
        return panel;
    }

    private JPanel createResourceTabComponent(ResourceEditorTab tab) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
        panel.setOpaque(false);

        JLabel iconLabel = new JLabel(Icons.getIcon(tab.getResource().getIconKey()));
        panel.add(iconLabel);

        JLabel titleLabel = new JLabel(tab.getTitle());
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        panel.add(titleLabel);

        JButton closeButton = new JButton(Icons.getIcon("close"));
        closeButton.setPreferredSize(new Dimension(UIConstants.ICON_SIZE_SMALL, UIConstants.ICON_SIZE_SMALL));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setContentAreaFilled(true);
                closeButton.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setContentAreaFilled(false);
            }
        });
        closeButton.addActionListener(e -> closeResourceTab(tab));
        panel.add(closeButton);

        MouseAdapter tabClickListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = tabbedPane.indexOfTabComponent(panel);
                if (index != -1) {
                    tabbedPane.setSelectedIndex(index);
                }
            }
        };
        panel.addMouseListener(tabClickListener);
        iconLabel.addMouseListener(tabClickListener);
        titleLabel.addMouseListener(tabClickListener);

        installTabDragHandler(panel, iconLabel, titleLabel);
        return panel;
    }

    private JPanel createTabComponent(EditorTab tab) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
        panel.setOpaque(false);

        JLabel iconLabel = new JLabel(Icons.getIcon(tab.getClassEntry().getIconKey()));
        panel.add(iconLabel);

        JLabel titleLabel = new JLabel(tab.getTitle());
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        panel.add(titleLabel);

        JButton closeButton = new JButton(Icons.getIcon("close"));
        closeButton.setPreferredSize(new Dimension(UIConstants.ICON_SIZE_SMALL, UIConstants.ICON_SIZE_SMALL));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setContentAreaFilled(true);
                closeButton.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setContentAreaFilled(false);
            }
        });
        closeButton.addActionListener(e -> closeTab(tab));
        panel.add(closeButton);

        // Click on panel/icon/title should select tab and handle context menu
        MouseAdapter tabClickListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabContextMenu(tab, e);
                } else {
                    int index = tabbedPane.indexOfTabComponent(panel);
                    if (index != -1) {
                        tabbedPane.setSelectedIndex(index);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabContextMenu(tab, e);
                }
            }
        };
        panel.addMouseListener(tabClickListener);
        iconLabel.addMouseListener(tabClickListener);
        titleLabel.addMouseListener(tabClickListener);

        installTabDragHandler(panel, iconLabel, titleLabel);
        return panel;
    }

    /** Live drag-to-reorder for a tab header (and its child labels). The pinned Welcome tab (index 0) gets no handler. */
    private void installTabDragHandler(JPanel header, JComponent... extraTargets) {
        MouseAdapter drag = new MouseAdapter() {
            private Point pressPoint;
            private boolean dragging;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(e)) {
                    pressPoint = null;
                    return;
                }
                pressPoint = e.getPoint();
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint == null) {
                    return;
                }
                if (!dragging) {
                    if (Math.abs(e.getX() - pressPoint.x) < DRAG_THRESHOLD
                            && Math.abs(e.getY() - pressPoint.y) < DRAG_THRESHOLD) {
                        return;
                    }
                    dragging = true;
                    header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                Point inPane = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), tabbedPane);
                int to = tabbedPane.indexAtLocation(inPane.x, inPane.y);
                int from = tabbedPane.indexOfTabComponent(header);
                if (to >= 1 && from >= 1 && to != from) {
                    moveTab(from, to);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressPoint = null;
                if (dragging) {
                    dragging = false;
                    header.setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        for (JComponent target : extraTargets) {
            target.addMouseListener(drag);
            target.addMouseMotionListener(drag);
        }
    }

    /** Moves the tab at {@code from} to {@code to}, preserving header/content/icon/tooltip. Tab 0 (Welcome) is pinned. */
    private void moveTab(int from, int to) {
        int count = tabbedPane.getTabCount();
        if (from < 1 || to < 1 || from == to || from >= count || to >= count) {
            return;
        }
        String title = tabbedPane.getTitleAt(from);
        Icon icon = tabbedPane.getIconAt(from);
        String tip = tabbedPane.getToolTipTextAt(from);
        Component comp = tabbedPane.getComponentAt(from);
        Component header = tabbedPane.getTabComponentAt(from);
        tabbedPane.removeTabAt(from);
        tabbedPane.insertTab(title, icon, comp, tip, to);
        tabbedPane.setTabComponentAt(to, header);
        tabbedPane.setSelectedIndex(to);
    }

    private void showTabContextMenu(EditorTab tab, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        // Close
        JMenuItem closeItem = createMenuItem("Close", () -> closeTab(tab));
        menu.add(closeItem);

        // Close Others
        JMenuItem closeOthersItem = createMenuItem("Close Others", () -> closeOtherTabs(tab));
        closeOthersItem.setEnabled(openTabs.size() > 1);
        menu.add(closeOthersItem);

        // Close All
        JMenuItem closeAllItem = createMenuItem("Close All", this::closeAllTabs);
        menu.add(closeAllItem);

        menu.addSeparator();

        // Close Tabs to the Left
        int tabIndex = findTabIndex(tab);
        JMenuItem closeLeftItem = createMenuItem("Close Tabs to the Left", () -> closeTabsToLeft(tab));
        closeLeftItem.setEnabled(tabIndex > 0);
        menu.add(closeLeftItem);

        // Close Tabs to the Right
        JMenuItem closeRightItem = createMenuItem("Close Tabs to the Right", () -> closeTabsToRight(tab));
        closeRightItem.setEnabled(tabIndex < tabbedPane.getTabCount() - 1);
        menu.add(closeRightItem);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem createMenuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        item.addActionListener(e -> action.run());
        return item;
    }

    private int findTabIndex(EditorTab tab) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) == tab) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Close a tab.
     */
    public void closeTab(EditorTab tab) {
        int index = findTabIndex(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            openTabs.remove(tab.getClassEntry().getClassName());

            // Switch to Welcome tab if no other tabs
            if (openTabs.isEmpty()) {
                tabbedPane.setSelectedIndex(0); // Welcome tab is always at index 0
            }
        }
    }

    public void closeTabForClass(String className) {
        EditorTab tab = openTabs.get(className);
        if (tab != null) {
            closeTab(tab);
        }
    }

    public void closeTabForResource(String path) {
        ResourceEditorTab tab = openResourceTabs.get(path);
        if (tab != null) {
            closeResourceTab(tab);
        }
    }

    /**
     * Close all tabs (except Welcome tab).
     */
    public void closeAllTabs() {
        // Remove all tabs except the Welcome tab (index 0)
        while (tabbedPane.getTabCount() > 1) {
            tabbedPane.removeTabAt(1);
        }
        openTabs.clear();
        openResourceTabs.clear();
        java.util.List<Runnable> hooks = new java.util.ArrayList<>(customViewCloseHooks.values());
        customViewCloseHooks.clear();
        customViews.clear();
        tabbedPane.setSelectedIndex(0); // Switch to Welcome tab
        for (Runnable hook : hooks) {
            hook.run();
        }
    }

    /**
     * Close all tabs except the specified one.
     */
    public void closeOtherTabs(EditorTab keepTab) {
        // Collect tabs to close (avoid concurrent modification)
        List<EditorTab> tabsToClose = new ArrayList<>();
        for (EditorTab tab : openTabs.values()) {
            if (tab != keepTab) {
                tabsToClose.add(tab);
            }
        }
        for (EditorTab tab : tabsToClose) {
            closeTab(tab);
        }
    }

    /**
     * Close all tabs to the left of the specified tab.
     */
    public void closeTabsToLeft(EditorTab referenceTab) {
        int refIndex = findTabIndex(referenceTab);
        if (refIndex <= 0) return;

        // Collect tabs to close
        List<EditorTab> tabsToClose = new ArrayList<>();
        for (int i = 0; i < refIndex; i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof EditorTab) {
                tabsToClose.add((EditorTab) comp);
            }
        }
        for (EditorTab tab : tabsToClose) {
            closeTab(tab);
        }
    }

    /**
     * Close all tabs to the right of the specified tab.
     */
    public void closeTabsToRight(EditorTab referenceTab) {
        int refIndex = findTabIndex(referenceTab);
        if (refIndex < 0 || refIndex >= tabbedPane.getTabCount() - 1) return;

        // Collect tabs to close
        List<EditorTab> tabsToClose = new ArrayList<>();
        for (int i = refIndex + 1; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof EditorTab) {
                tabsToClose.add((EditorTab) comp);
            }
        }
        for (EditorTab tab : tabsToClose) {
            closeTab(tab);
        }
    }

    /**
     * Get the currently selected tab.
     */
    public EditorTab getCurrentTab() {
        Component selected = tabbedPane.getSelectedComponent();
        if (selected instanceof EditorTab) {
            return (EditorTab) selected;
        }
        return null;
    }

    /**
     * Get the class of the currently selected tab.
     */
    public ClassEntryModel getCurrentClass() {
        EditorTab tab = getCurrentTab();
        return tab != null ? tab.getClassEntry() : null;
    }

    /**
     * Set view mode for all open tabs.
     */
    public void setViewMode(ViewMode mode) {
        for (EditorTab tab : openTabs.values()) {
            tab.setViewMode(mode);
        }
    }

    /**
     * Get the current view mode.
     */
    public ViewMode getViewMode() {
        EditorTab current = getCurrentTab();
        if (current != null) {
            return current.getViewMode();
        }
        return ViewMode.SOURCE;
    }

    /**
     * Set whether to omit annotations from decompiled output display.
     */
    public void setOmitAnnotations(boolean omit) {
        this.omitAnnotations = omit;
        for (EditorTab tab : openTabs.values()) {
            tab.setOmitAnnotations(omit);
        }
    }

    /**
     * Enable or disable usage-count lenses in all open tabs.
     */
    public void setUsageLensEnabled(boolean enabled) {
        for (EditorTab tab : openTabs.values()) {
            tab.setUsageLensEnabled(enabled);
        }
    }

    /**
     * Refresh the current tab.
     */
    public void refreshCurrentTab() {
        EditorTab tab = getCurrentTab();
        if (tab != null) {
            tab.refresh();
        }
    }

    /** Reloads every open class tab from current bytecode, dropping stale decompilation - after a project mutation. */
    public void reloadAllTabs() {
        for (EditorTab tab : openTabs.values()) {
            tab.reload();
        }
    }

    /**
     * Copy selection from current tab.
     */
    public void copySelection() {
        EditorTab tab = getCurrentTab();
        if (tab != null) {
            tab.copySelection();
        }
    }

    /**
     * Show find dialog in current tab.
     */
    public void showFindDialog() {
        EditorTab tab = getCurrentTab();
        if (tab != null) {
            tab.showFindDialog();
        }
    }

    /**
     * Show go to line dialog.
     */
    public void showGoToLineDialog() {
        EditorTab tab = getCurrentTab();
        if (tab == null) return;

        String input = JOptionPane.showInputDialog(this, "Go to line:", "Go to Line",
                JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.isEmpty()) {
            try {
                int line = Integer.parseInt(input.trim());
                tab.goToLine(line);
            } catch (NumberFormatException e) {
                // Ignore invalid input
            }
        }
    }

    /**
     * Get the currently selected method (if any).
     */
    public MethodEntryModel getCurrentMethod() {
        EditorTab tab = getCurrentTab();
        return tab != null ? tab.getCurrentMethod() : null;
    }

    /**
     * Get the selected text from the current editor.
     */
    public String getSelectedText() {
        EditorTab tab = getCurrentTab();
        return tab != null ? tab.getSelectedText() : null;
    }

    /**
     * Scroll to the specified method in the current tab.
     */
    public void scrollToMethod(MethodEntryModel method) {
        EditorTab tab = getCurrentTab();
        if (tab != null) {
            tab.scrollToMethod(method);
        }
    }

    /**
     * Scroll to the specified field in the current tab.
     */
    public void scrollToField(FieldEntryModel field) {
        EditorTab tab = getCurrentTab();
        if (tab != null) {
            tab.scrollToField(field);
        }
    }

    /**
     * Go to a specific line and highlight it.
     */
    public void goToLineAndHighlight(int line) {
        EditorTab tab = getCurrentTab();
        if (tab != null && line > 0) {
            tab.highlightLine(line);
        }
    }

    /**
     * Set the font size for all open tabs.
     */
    public void setFontSize(int size) {
        for (EditorTab tab : openTabs.values()) {
            tab.setFontSize(size);
        }
    }

    /**
     * Set word wrap for all open tabs.
     */
    public void setWordWrap(boolean enabled) {
        for (EditorTab tab : openTabs.values()) {
            tab.setWordWrap(enabled);
        }
    }

    /**
     * Set the project model for navigation features.
     */
    public void setProjectModel(ProjectModel projectModel) {
        this.projectModel = projectModel;
        for (EditorTab tab : openTabs.values()) {
            tab.setProjectModel(projectModel);
        }
        // Update welcome tab with project info
        if (welcomeTab != null) {
            welcomeTab.setProjectModel(projectModel);
        }
    }

    /**
     * Refresh the welcome tab (call after loading new classes).
     */
    public void refreshWelcomeTab() {
        if (welcomeTab != null) {
            welcomeTab.refresh();
        }
    }

    /**
     * Switch to the welcome tab.
     */
    public void showWelcomeTab() {
        tabbedPane.setSelectedIndex(0);
    }

    /**
     * Navigate to a specific PC within a method in a class.
     * Opens the class if not already open, switches to bytecode view, and highlights the PC.
     * @param classEntry the class containing the method
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset
     * @return true if navigation succeeded
     */
    public boolean navigateToPC(ClassEntryModel classEntry, String methodName, String methodDesc, int pc) {
        openClass(classEntry, ViewMode.BYTECODE);

        EditorTab tab = openTabs.get(classEntry.getClassName());
        if (tab != null) {
            return tab.navigateToPC(methodName, methodDesc, pc);
        }
        return false;
    }

    /**
     * Navigate the source view to the statement at a bytecode offset, selecting the given token
     * (e.g. the referenced method or field name) on the resolved line.
     */
    public boolean navigateToSourceOffset(ClassEntryModel classEntry, String methodName,
                                          String methodDesc, int pc, String selectToken) {
        openClass(classEntry, ViewMode.SOURCE);

        EditorTab tab = openTabs.get(classEntry.getClassName());
        if (tab != null) {
            return tab.navigateToSourceOffset(methodName, methodDesc, pc, selectToken);
        }
        return false;
    }

    /**
     * Navigate to a specific method in a class.
     * Opens the class if not already open and scrolls to the method.
     * @param classEntry the class containing the method
     * @param methodName the method name
     * @param methodDesc the method descriptor (can be null)
     * @param viewMode the view mode to use
     * @return true if navigation succeeded
     */
    public boolean navigateToMethod(ClassEntryModel classEntry, String methodName, String methodDesc, ViewMode viewMode) {
        openClass(classEntry, viewMode);

        EditorTab tab = openTabs.get(classEntry.getClassName());
        if (tab != null) {
            return tab.navigateToMethod(methodName, methodDesc);
        }
        return false;
    }

    /**
     * Get an open tab by class name.
     */
    public EditorTab getTab(String className) {
        return openTabs.get(className);
    }
}
