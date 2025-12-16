package com.tonic.ui.editor;

import com.tonic.ui.MainFrame;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Tabbed editor panel for viewing classes.
 */
public class EditorPanel extends JPanel implements ThemeManager.ThemeChangeListener {

    private static final String WELCOME_TAB_KEY = "__welcome__";

    private final MainFrame mainFrame;
    private final JTabbedPane tabbedPane;
    private final Map<String, EditorTab> openTabs = new HashMap<>();
    private ProjectModel projectModel;
    private WelcomeTab welcomeTab;

    private ViewMode currentViewMode = ViewMode.SOURCE;

    public EditorPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        // Create tabbed pane
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        // Create welcome tab (non-closable)
        welcomeTab = new WelcomeTab(mainFrame);
        tabbedPane.addTab("Welcome", Icons.getIcon("home"), welcomeTab);
        tabbedPane.setTabComponentAt(0, createWelcomeTabComponent());

        add(tabbedPane, BorderLayout.CENTER);

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());

        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());

        repaint();
    }

    private JPanel createWelcomeTabComponent() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);

        // Home icon
        JLabel iconLabel = new JLabel(Icons.getIcon("home"));
        panel.add(iconLabel);

        // Title
        JLabel titleLabel = new JLabel("Welcome");
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(11));
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
        if (projectModel != null) {
            tab.setProjectModel(projectModel);
        }
        openTabs.put(key, tab);

        // Add tab with close button
        tabbedPane.addTab(tab.getTitle(), classEntry.getIcon(), tab, tab.getTooltip());
        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(index, createTabComponent(tab));
        tabbedPane.setSelectedIndex(index);
    }

    private JPanel createTabComponent(EditorTab tab) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);

        // Icon
        JLabel iconLabel = new JLabel(tab.getClassEntry().getIcon());
        panel.add(iconLabel);

        // Title
        JLabel titleLabel = new JLabel(tab.getTitle());
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(11));
        panel.add(titleLabel);

        // Close button
        JButton closeButton = new JButton(Icons.getIcon("close"));
        closeButton.setPreferredSize(new Dimension(16, 16));
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

        return panel;
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

    /**
     * Close all tabs (except Welcome tab).
     */
    public void closeAllTabs() {
        // Remove all tabs except the Welcome tab (index 0)
        while (tabbedPane.getTabCount() > 1) {
            tabbedPane.removeTabAt(1);
        }
        openTabs.clear();
        tabbedPane.setSelectedIndex(0); // Switch to Welcome tab
    }

    /**
     * Close all tabs except the specified one.
     */
    public void closeOtherTabs(EditorTab keepTab) {
        // Collect tabs to close (avoid concurrent modification)
        java.util.List<EditorTab> tabsToClose = new java.util.ArrayList<>();
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
        java.util.List<EditorTab> tabsToClose = new java.util.ArrayList<>();
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
        java.util.List<EditorTab> tabsToClose = new java.util.ArrayList<>();
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
     * Set view mode for all tabs.
     */
    public void setViewMode(ViewMode mode) {
        this.currentViewMode = mode;
        EditorTab current = getCurrentTab();
        if (current != null) {
            current.setViewMode(mode);
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
}
