package com.tonic.ui.bottom;

import com.tonic.event.EventBus;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.model.ProjectModel;
import com.tonic.ui.analysis.BookmarksPanel;
import com.tonic.ui.analysis.CommentsPanel;
import com.tonic.ui.analysis.FindUsagesResultsPanel;
import com.tonic.ui.console.ConsolePanel;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.editor.cfg.CFGBlockDetailPanel;
import com.tonic.ui.editor.cfg.CFGBlockSelectedEvent;
import com.tonic.ui.history.LocalHistoryPanel;
import com.tonic.ui.layout.Stacks;
import com.tonic.ui.layout.TabLook;
import com.tonic.ui.layout.ViewHost;
import com.tonic.ui.layout.ViewId;
import com.tonic.ui.layout.ViewSpec;
import com.tonic.ui.layout.Views;
import com.tonic.ui.run.RunConsolePanel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import lombok.Setter;

/** The registry of dock tabs; each is a view homed in the bottom stack, registered eagerly and opened when asked for. */
public class BottomPanel
{

    @Setter
    private ProjectModel project;

    @Setter
    private EditorPanel editorPanel;

    private final Map<String, JComponent> registered = new LinkedHashMap<>();

    private BookmarksPanel bookmarksPanel;
    private LocalHistoryPanel localHistoryPanel;
    private CommentsPanel commentsPanel;
    private CFGBlockDetailPanel cfgBlockDetailPanel;
    private RunConsolePanel runConsolePanel;
    private ScriptConsolePanel scriptConsolePanel;
    private ConsolePanel consolePanel;

    private ViewHost host;
    private Runnable onAllTabsClosed;
    private Runnable onTabOpened;
    private CollapseHost collapseHost;

    /** Creates the registry and listens for CFG block selections to show in Block Details. */
    public BottomPanel()
    {
        EventBus.getInstance().register(CFGBlockSelectedEvent.class, this::onCFGBlockSelected);
    }

    /**
     * Attaches the layout; no tab opens until something asks for it.
     *
     * @param value the layout the tabs live in
     */
    public void setHost(ViewHost value)
    {
        this.host = value;
        value.addListener(new ViewHost.Listener()
        {
            @Override
            public void headerPressed(ViewId view, boolean inFront)
            {
                titleOf(view).ifPresent(title -> pressed(inFront));
            }
        });
    }

    /**
     * Registers the console, which the shell builds so logging survives its tab closing.
     *
     * @param panel the console panel, or null for none
     */
    public void setConsolePanel(ConsolePanel panel)
    {
        this.consolePanel = panel;
        if (panel != null)
        {
            addTab("Console", panel);
        }
    }

    /** Puts the dock away or lets it out when a reader presses its tabs: the tab in front puts it away, any tab lets it out. */
    public interface CollapseHost
    {
        /**
         * Tells whether the dock is put away.
         *
         * @return true where it is put away
         */
        boolean isCollapsed();

        /** Puts the dock away. */
        void collapse();

        /** Lets the dock out. */
        void expand();
    }

    /**
     * Sets what puts the dock away and lets it out.
     *
     * @param value the collapse handler
     */
    public void setCollapseHost(CollapseHost value)
    {
        this.collapseHost = value;
    }

    /**
     * Sets what runs when the last open tab closes.
     *
     * @param callback the action
     */
    public void setOnAllTabsClosed(Runnable callback)
    {
        this.onAllTabsClosed = callback;
    }

    /**
     * Sets what runs whenever a tab opens.
     *
     * @param callback the action
     */
    public void setOnTabOpened(Runnable callback)
    {
        this.onTabOpened = callback;
    }

    /**
     * Registers a panel under a title, closing the open tab of any panel it replaces.
     *
     * @param title the tab's title
     * @param panel the panel
     */
    public void addTab(String title, JComponent panel)
    {
        final JComponent had = registered.get(title);
        if (had != null && had != panel && isOpen(title))
        {
            host.close(Views.inDock(title));
        }
        registered.put(title, panel);
    }

    /**
     * Opens a registered tab, or brings it forward where it is already open; an unregistered title is ignored.
     *
     * @param title the tab's title
     */
    public void openTab(String title)
    {
        final JComponent panel = registered.get(title);
        if (panel == null || host == null)
        {
            return;
        }
        if (isOpen(title))
        {
            host.select(Views.inDock(title));
        }
        else
        {
            host.open(ViewSpec.utility(Views.inDock(title), TabLook.of(title), Stacks.BOTTOM, panel));
        }
        if (onTabOpened != null)
        {
            onTabOpened.run();
        }
    }

    /**
     * Takes a tab off the window, leaving it registered.
     *
     * @param title the tab's title
     */
    public void closeTab(String title)
    {
        if (!isOpen(title))
        {
            return;
        }
        host.close(Views.inDock(title));
        if (!hasTabs() && onAllTabsClosed != null)
        {
            onAllTabsClosed.run();
        }
    }

    /**
     * Opens a tab, or reveals it where it is already open, putting the dock away only where it is already in front.
     *
     * @param title the tab's title
     */
    public void toggleTab(String title)
    {
        if (isOpen(title))
        {
            host.reveal(Views.inDock(title));
        }
        else
        {
            openTab(title);
        }
    }

    private void removeTab(String title)
    {
        closeTab(title);
        registered.remove(title);
    }

    /**
     * Tells whether a registered tab is on the window.
     *
     * @param title the tab's title
     * @return true where it is open
     */
    public boolean isOpen(String title)
    {
        return registered.containsKey(title) && host != null
                && host.isOpen(Views.inDock(title));
    }

    /**
     * Tells whether any registered tab is on the window.
     *
     * @return true where at least one is open
     */
    public boolean hasTabs()
    {
        for (String title : registered.keySet())
        {
            if (isOpen(title))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Shows usages in the tab for their target, one tab per target, replacing its earlier results.
     *
     * @param event the search and its results
     */
    public void openFindUsagesTab(FindUsagesEvent event)
    {
        final FindUsagesResultsPanel panel = new FindUsagesResultsPanel();
        panel.setProject(project);
        panel.setEditorPanel(editorPanel);
        panel.showUsages(event);
        final String title = event.getTargetDisplay();
        addTab(title, panel);
        openTab(title);
    }

    /**
     * Registers and opens a plugin's tab, numbering the title where it is already taken.
     *
     * @param title the requested title
     * @param content the plugin's panel, which removePluginTab later takes
     */
    public void addPluginTab(String title, JComponent content)
    {
        final String actual = unique(title, content);
        addTab(actual, content);
        openTab(actual);
    }

    /**
     * Closes and unregisters a plugin's tab.
     *
     * @param content the panel passed to addPluginTab
     */
    public void removePluginTab(JComponent content)
    {
        titleFor(content).ifPresent(this::removeTab);
    }

    /**
     * Opens the Run output tab, creating it the first time.
     *
     * @return the Run output panel
     */
    public RunConsolePanel openRunConsole()
    {
        if (runConsolePanel == null)
        {
            runConsolePanel = new RunConsolePanel();
            addTab("Run", runConsolePanel);
        }
        openTab("Run");
        return runConsolePanel;
    }

    /**
     * Opens the Script Console tab, creating it the first time.
     *
     * @return the script console panel
     */
    public ScriptConsolePanel openScriptConsole()
    {
        if (scriptConsolePanel == null)
        {
            scriptConsolePanel = new ScriptConsolePanel();
            addTab("Script Console", scriptConsolePanel);
        }
        openTab("Script Console");
        return scriptConsolePanel;
    }

    /** Opens or reveals the Console tab. */
    public void toggleConsoleTab()
    {
        if (consolePanel != null)
        {
            toggleTab("Console");
        }
    }

    /** Opens or reveals the Bookmarks tab, creating it for the current project the first time. */
    public void toggleBookmarksTab()
    {
        if (bookmarksPanel == null)
        {
            bookmarksPanel = new BookmarksPanel(project);
            addTab("Bookmarks", bookmarksPanel);
        }
        toggleTab("Bookmarks");
    }

    /** Opens or reveals the Comments tab, creating it for the current project the first time. */
    public void toggleCommentsTab()
    {
        if (commentsPanel == null)
        {
            commentsPanel = new CommentsPanel(project);
            addTab("Comments", commentsPanel);
        }
        toggleTab("Comments");
    }

    /** Opens or reveals the Local History tab, creating it the first time. */
    public void toggleLocalHistoryTab()
    {
        if (localHistoryPanel == null)
        {
            localHistoryPanel = new LocalHistoryPanel();
            addTab("Local History", localHistoryPanel);
        }
        toggleTab("Local History");
    }

    private void onCFGBlockSelected(CFGBlockSelectedEvent event)
    {
        if (cfgBlockDetailPanel == null)
        {
            cfgBlockDetailPanel = new CFGBlockDetailPanel();
            addTab("Block Details", cfgBlockDetailPanel);
        }
        cfgBlockDetailPanel.showBlock(event.getVertex());
        openTab("Block Details");
    }

    /** Takes every tab off the window and drops the project-bound Bookmarks and Comments panels. */
    public void closeAllTabs()
    {
        for (String title : List.copyOf(registered.keySet()))
        {
            closeTab(title);
        }
        bookmarksPanel = null;
        commentsPanel = null;
        registered.remove("Bookmarks");
        registered.remove("Comments");
    }

    private void pressed(boolean inFront)
    {
        if (collapseHost == null)
        {
            return;
        }
        if (collapseHost.isCollapsed())
        {
            collapseHost.expand();
        }
        else if (inFront)
        {
            collapseHost.collapse();
        }
    }

    private Optional<String> titleOf(ViewId view)
    {
        final String title = Views.title(view);
        return Views.inDock(title).equals(view) && registered.containsKey(title)
                ? Optional.of(title)
                : Optional.empty();
    }

    private Optional<String> titleFor(JComponent content)
    {
        for (Map.Entry<String, JComponent> entry : registered.entrySet())
        {
            if (entry.getValue() == content)
            {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private String unique(String title, JComponent content)
    {
        final JComponent had = registered.get(title);
        if (had == null || had == content)
        {
            return title;
        }
        int next = 2;
        while (registered.containsKey(title + " (" + next + ")"))
        {
            next++;
        }
        return title + " (" + next + ")";
    }
}
