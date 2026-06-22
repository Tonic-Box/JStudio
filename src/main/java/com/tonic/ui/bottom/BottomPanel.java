package com.tonic.ui.bottom;

import com.tonic.ui.analysis.BookmarksPanel;
import com.tonic.ui.analysis.CommentsPanel;
import com.tonic.ui.analysis.FindUsagesResultsPanel;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.editor.cfg.CFGBlockDetailPanel;
import com.tonic.event.EventBus;
import com.tonic.ui.editor.cfg.CFGBlockSelectedEvent;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.model.ProjectModel;
import com.tonic.ui.console.ConsolePanel;
import com.tonic.ui.run.RunConsolePanel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class BottomPanel extends ThemedJPanel implements ThemeChangeListener {

    @Setter
    private ProjectModel project;

    @Setter
    private EditorPanel editorPanel;

    private final JTabbedPane tabbedPane;

    private BookmarksPanel bookmarksPanel;
    private CommentsPanel commentsPanel;
    private CFGBlockDetailPanel cfgBlockDetailPanel;
    private RunConsolePanel runConsolePanel;
    private ScriptConsolePanel scriptConsolePanel;
    @Setter
    private ConsolePanel consolePanel;

    private Runnable onAllTabsClosed;
    private Runnable onTabOpened;
    private CollapseHost collapseHost;

    public BottomPanel() {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabbedPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(0, 200));
        setMinimumSize(new Dimension(0, 100));

        ThemeManager.getInstance().addThemeChangeListener(this);
        EventBus.getInstance().register(CFGBlockSelectedEvent.class, this::onCFGBlockSelected);
    }

    private void onCFGBlockSelected(CFGBlockSelectedEvent event) {
        if (cfgBlockDetailPanel == null) {
            cfgBlockDetailPanel = new CFGBlockDetailPanel();
        }
        cfgBlockDetailPanel.showBlock(event.getVertex());

        int existingIndex = tabbedPane.indexOfComponent(cfgBlockDetailPanel);
        if (existingIndex == -1) {
            addClosableTab("Block Details", cfgBlockDetailPanel);
        }
        tabbedPane.setSelectedComponent(cfgBlockDetailPanel);
        notifyExpanded();
    }

    public void setOnAllTabsClosed(Runnable callback) {
        this.onAllTabsClosed = callback;
    }

    public void setOnTabOpened(Runnable callback) {
        this.onTabOpened = callback;
    }

    /**
     * Bridges tab-strip clicks to the host's collapse/expand of the bottom dock (the split divider lives in the host).
     * Clicking the active tab collapses the dock to just its strip; clicking any tab while collapsed expands it again.
     */
    public interface CollapseHost {
        boolean isCollapsed();

        void collapse();

        void expand();
    }

    public void setCollapseHost(CollapseHost host) {
        this.collapseHost = host;
    }

    public void openFindUsagesTab(FindUsagesEvent event) {
        FindUsagesResultsPanel panel = new FindUsagesResultsPanel();
        panel.setProject(project);
        panel.setEditorPanel(editorPanel);
        panel.showUsages(event);

        String title = event.getTargetDisplay();
        addClosableTab(title, panel);
        tabbedPane.setSelectedComponent(panel);

        notifyExpanded();
    }

    /**
     * Adds (or reveals) a plugin-contributed closable tab showing {@code content}. Pass the same component to
     * {@link #removePluginTab(JComponent)} to close it.
     */
    public void addPluginTab(String title, JComponent content) {
        if (isTabOpen(content)) {
            tabbedPane.setSelectedComponent(content);
            notifyExpanded();
            return;
        }
        addClosableTab(title, content);
        tabbedPane.setSelectedComponent(content);
        notifyExpanded();
    }

    /** Removes a plugin tab previously added with {@link #addPluginTab}. No-op if not open. */
    public void removePluginTab(JComponent content) {
        closeTab(content);
    }

    /** Opens (or reveals) the single Run output tab and returns its panel. */
    public RunConsolePanel openRunConsole() {
        if (runConsolePanel == null) {
            runConsolePanel = new RunConsolePanel();
        }
        if (!isTabOpen(runConsolePanel)) {
            addClosableTab("Run", runConsolePanel);
        }
        tabbedPane.setSelectedComponent(runConsolePanel);
        notifyExpanded();
        return runConsolePanel;
    }

    /** Opens (or reveals) the single Script Console output tab and returns its panel. */
    public ScriptConsolePanel openScriptConsole() {
        if (scriptConsolePanel == null) {
            scriptConsolePanel = new ScriptConsolePanel();
        }
        if (!isTabOpen(scriptConsolePanel)) {
            addClosableTab("Script Console", scriptConsolePanel);
        }
        tabbedPane.setSelectedComponent(scriptConsolePanel);
        notifyExpanded();
        return scriptConsolePanel;
    }

    /** Toggles the Console tab. The panel instance is owned by MainFrame (so logging persists when closed). */
    public void toggleConsoleTab() {
        if (consolePanel == null) {
            return;
        }
        if (isTabOpen(consolePanel)) {
            closeTab(consolePanel);
        } else {
            addClosableTab("Console", consolePanel);
            tabbedPane.setSelectedComponent(consolePanel);
            notifyExpanded();
        }
    }

    public void toggleBookmarksTab() {
        if (bookmarksPanel != null && isTabOpen(bookmarksPanel)) {
            closeTab(bookmarksPanel);
        } else {
            if (bookmarksPanel == null) {
                bookmarksPanel = new BookmarksPanel(project);
            }
            if (!isTabOpen(bookmarksPanel)) {
                addClosableTab("Bookmarks", bookmarksPanel);
            }
            tabbedPane.setSelectedComponent(bookmarksPanel);
            notifyExpanded();
        }
    }

    public void toggleCommentsTab() {
        if (commentsPanel != null && isTabOpen(commentsPanel)) {
            closeTab(commentsPanel);
        } else {
            if (commentsPanel == null) {
                commentsPanel = new CommentsPanel(project);
            }
            if (!isTabOpen(commentsPanel)) {
                addClosableTab("Comments", commentsPanel);
            }
            tabbedPane.setSelectedComponent(commentsPanel);
            notifyExpanded();
        }
    }

    private boolean isTabOpen(Component component) {
        return tabbedPane.indexOfComponent(component) >= 0;
    }

    private void addClosableTab(String title, Component content) {
        tabbedPane.addTab(title, content);
        int index = tabbedPane.indexOfComponent(content);
        tabbedPane.setTabComponentAt(index, new ClosableTabComponent(title, content));
    }

    private void closeTab(Component tab) {
        int index = tabbedPane.indexOfComponent(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            checkIfAllTabsClosed();
        }
    }

    private void onTabClosed(Component tab) {
        tabbedPane.remove(tab);
        checkIfAllTabsClosed();
    }

    private void checkIfAllTabsClosed() {
        if (tabbedPane.getTabCount() == 0) {
            if (onAllTabsClosed != null) {
                onAllTabsClosed.run();
            }
        }
    }

    private void notifyExpanded() {
        if (onTabOpened != null) {
            onTabOpened.run();
        }
    }

    public boolean hasTabs() {
        return tabbedPane.getTabCount() > 0;
    }

    /** The panel height that shows only the tab strip; the host collapses the split to this so the tabs stay clickable. */
    public int collapsedHeight() {
        if (tabbedPane.getTabCount() == 0) {
            return 0;
        }
        Rectangle bounds = tabbedPane.getBoundsAt(0);
        int strip = bounds != null && bounds.height > 0 ? bounds.height : 28;
        Insets insets = getInsets();
        return strip + insets.top + insets.bottom + 4;
    }

    /** Tab-strip click: collapse when re-clicking the active tab, expand + select when collapsed, else just switch. */
    private void onTabHeaderClicked(Component tabComponent) {
        int clicked = tabbedPane.indexOfTabComponent(tabComponent);
        if (clicked < 0) {
            return;
        }
        if (collapseHost != null && collapseHost.isCollapsed()) {
            tabbedPane.setSelectedIndex(clicked);
            collapseHost.expand();
        } else if (clicked == tabbedPane.getSelectedIndex()) {
            if (collapseHost != null) {
                collapseHost.collapse();
            }
        } else {
            tabbedPane.setSelectedIndex(clicked);
        }
    }

    public void closeAllTabs() {
        tabbedPane.removeAll();
        bookmarksPanel = null;
        commentsPanel = null;
        checkIfAllTabsClosed();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyThemeToComponents);
    }

    private void applyThemeToComponents() {
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());

        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component tabComponent = tabbedPane.getTabComponentAt(i);
            if (tabComponent instanceof ClosableTabComponent) {
                ((ClosableTabComponent) tabComponent).applyTheme();
            }
        }
    }

    private class ClosableTabComponent extends JPanel {
        private final JLabel label;
        private final JButton closeBtn;

        public ClosableTabComponent(String title, Component associatedTab) {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));

            label = new JLabel(title);
            label.setForeground(JStudioTheme.getTextPrimary());
            label.setFont(JStudioTheme.getUIFont(11));
            add(label);

            closeBtn = new JButton(Icons.getIcon("close", 10));
            closeBtn.setPreferredSize(new Dimension(16, 16));
            closeBtn.setBorderPainted(false);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setFocusable(false);
            closeBtn.setToolTipText("Close");
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    closeBtn.setContentAreaFilled(true);
                    closeBtn.setBackground(JStudioTheme.getSelection());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    closeBtn.setContentAreaFilled(false);
                }
            });
            closeBtn.addActionListener(e -> onTabClosed(associatedTab));
            add(closeBtn);

            MouseAdapter clickHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onTabHeaderClicked(ClosableTabComponent.this);
                }
            };
            addMouseListener(clickHandler);
            label.addMouseListener(clickHandler);
        }

        public void applyTheme() {
            label.setForeground(JStudioTheme.getTextPrimary());
        }
    }
}
