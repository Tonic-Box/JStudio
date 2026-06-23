package com.tonic.ui.editor;

import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.editor.resource.ResourceEditorTab;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Tabbed editor panel for viewing classes.
 */
public class EditorPanel extends ThemedJPanel {
    private final JTabbedPane tabbedPane;
    private final TabRegistry registry;
    private ProjectModel projectModel;
    private final WelcomeTab welcomeTab;
    private final TabHeaderFactory headerFactory;
    private final TabContextMenu contextMenu;

    private boolean omitAnnotations = false;

    public EditorPanel(MainFrame mainFrame) {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        registry = new TabRegistry(tabbedPane);
        contextMenu = new TabContextMenu(tabbedPane, registry, this::closeTab, this::closeResourceTab,
                this::closeCustomView);
        TabDragController dragController = new TabDragController(tabbedPane);
        headerFactory = new TabHeaderFactory(tabbedPane, dragController, contextMenu::headerListener);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        welcomeTab = new WelcomeTab(mainFrame);
        registry.setWelcomeTab(welcomeTab);
        tabbedPane.addTab("Welcome", Icons.getIcon("home"), welcomeTab);
        tabbedPane.setTabComponentAt(0, headerFactory.createWelcomeTabComponent());

        add(tabbedPane, BorderLayout.CENTER);
    }

    @Override
    protected void applyChildThemes() {
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
    }

    /**
     * Open a class in a new or existing tab.
     */
    public void openClass(ClassEntryModel classEntry, ViewMode viewMode) {
        String key = classEntry.getClassName();

        // Check if already open
        EditorTab existingTab = registry.getClassTab(key);
        if (existingTab != null) {
            // Switch to existing tab
            int index = registry.findTabIndex(existingTab);
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
        registry.putClassTab(key, tab);

        // Add tab with close button
        tabbedPane.addTab(tab.getTitle(), TabHeaderFactory.classIcon(classEntry), tab, tab.getTooltip());
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, headerFactory.createTabComponent(tab, () -> closeTab(tab)));
        tabbedPane.setSelectedIndex(index);
    }

    public void openResource(ResourceEntryModel resource) {
        String key = resource.getPath();

        ResourceEditorTab existingTab = registry.getResourceTab(key);
        if (existingTab != null) {
            int index = registry.findResourceTabIndex(existingTab);
            if (index >= 0) {
                tabbedPane.setSelectedIndex(index);
            }
            return;
        }

        ResourceEditorTab tab = new ResourceEditorTab(resource);
        registry.putResourceTab(key, tab);

        tabbedPane.addTab(tab.getTitle(), Icons.getIcon(resource.getIconKey()), tab, tab.getTooltip());
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, headerFactory.createResourceTabComponent(tab, () -> closeResourceTab(tab)));
        tabbedPane.setSelectedIndex(index);
    }

    public void closeResourceTab(ResourceEditorTab tab) {
        int index = registry.findResourceTabIndex(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            registry.removeResourceTab(tab.getResource().getPath());

            if (registry.noClassOrResourceTabs()) {
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
        JComponent existing = registry.getCustomView(id);
        if (existing != null) {
            int index = registry.findComponentIndex(existing);
            if (index >= 0) {
                tabbedPane.setSelectedIndex(index);
            }
            return;
        }

        registry.putCustomView(id, view, onClose);
        tabbedPane.addTab(title, icon, view, title);
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, headerFactory.createCustomTabComponent(title, icon, view, () -> closeCustomView(id)));
        tabbedPane.setSelectedIndex(index);
    }

    /** Closes a plugin-contributed center tab (running its close hook, if any). No-op if {@code id} is not open. */
    public void closeCustomView(String id) {
        JComponent view = registry.getCustomView(id);
        if (view == null) {
            return;
        }
        Runnable onClose = registry.removeCustomView(id);
        int index = registry.findComponentIndex(view);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            if (registry.isEmpty()) {
                tabbedPane.setSelectedIndex(0);
            }
        }
        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * Close a tab.
     */
    public void closeTab(EditorTab tab) {
        int index = registry.findTabIndex(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            registry.removeClassTab(tab.getClassEntry().getClassName());

            // Switch to Welcome tab if no other tabs
            if (registry.noClassTabs()) {
                tabbedPane.setSelectedIndex(0); // Welcome tab is always at index 0
            }
        }
    }

    public void closeTabForClass(String className) {
        EditorTab tab = registry.getClassTab(className);
        if (tab != null) {
            closeTab(tab);
        }
    }

    public void closeTabForResource(String path) {
        ResourceEditorTab tab = registry.getResourceTab(path);
        if (tab != null) {
            closeResourceTab(tab);
        }
    }

    /**
     * Close all tabs (except Welcome tab).
     */
    public void closeAllTabs() {
        contextMenu.closeAllTabs();
    }

    /**
     * Close every closable tab except the specified one (all tab kinds, not just class editors; the Welcome tab is
     * always kept).
     */
    public void closeOtherTabs(Component keepTab) {
        contextMenu.closeOtherTabs(keepTab);
    }

    /**
     * Close every closable tab to the left of the specified tab (all tab kinds; the Welcome tab is always kept).
     */
    public void closeTabsToLeft(Component referenceTab) {
        contextMenu.closeTabsToLeft(referenceTab);
    }

    /**
     * Close every closable tab to the right of the specified tab (all tab kinds; the Welcome tab is always kept).
     */
    public void closeTabsToRight(Component referenceTab) {
        contextMenu.closeTabsToRight(referenceTab);
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
        for (EditorTab tab : registry.classTabs()) {
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
        for (EditorTab tab : registry.classTabs()) {
            tab.setOmitAnnotations(omit);
        }
    }

    /**
     * Enable or disable usage-count lenses in all open tabs.
     */
    public void setUsageLensEnabled(boolean enabled) {
        for (EditorTab tab : registry.classTabs()) {
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
        for (EditorTab tab : registry.classTabs()) {
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
        for (EditorTab tab : registry.classTabs()) {
            tab.setFontSize(size);
        }
    }

    /**
     * Set word wrap for all open tabs.
     */
    public void setWordWrap(boolean enabled) {
        for (EditorTab tab : registry.classTabs()) {
            tab.setWordWrap(enabled);
        }
    }

    /**
     * Set the project model for navigation features.
     */
    public void setProjectModel(ProjectModel projectModel) {
        this.projectModel = projectModel;
        for (EditorTab tab : registry.classTabs()) {
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

        EditorTab tab = registry.getClassTab(classEntry.getClassName());
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

        EditorTab tab = registry.getClassTab(classEntry.getClassName());
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

        EditorTab tab = registry.getClassTab(classEntry.getClassName());
        if (tab != null) {
            return tab.navigateToMethod(methodName, methodDesc);
        }
        return false;
    }

    /**
     * Get an open tab by class name.
     */
    public EditorTab getTab(String className) {
        return registry.getClassTab(className);
    }
}
