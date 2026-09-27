package com.tonic.ui.editor;

import com.tonic.ui.MainFrame;
import com.tonic.ui.editor.resource.ResourceEditorTab;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.ui.layout.Stacks;
import com.tonic.ui.layout.TabLook;
import com.tonic.ui.layout.ViewHost;
import com.tonic.ui.layout.ViewId;
import com.tonic.ui.layout.ViewSpec;
import com.tonic.ui.layout.Views;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.RunnableOverlayIcon;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.event.MouseEvent;

/** The owner of the document tabs: classes, resources and plugin views, and which tab shows what. */
public class EditorPanel
{

    private final TabRegistry registry;
    private ProjectModel projectModel;
    private final WelcomeTab welcomeTab;
    private final TabContextMenu contextMenu;

    private ViewHost host;
    private boolean omitAnnotations = false;

    /**
     * Creates the editor with its Welcome tab.
     *
     * @param mainFrame the window the Welcome tab acts on
     */
    public EditorPanel(MainFrame mainFrame)
    {
        registry = new TabRegistry();
        contextMenu = new TabContextMenu(registry, this::closeTab, this::closeResourceTab, this::closeCustomView);
        welcomeTab = new WelcomeTab(mainFrame);
        registry.setWelcomeTab(welcomeTab);
    }

    /**
     * Attaches the layout and opens the pinned Welcome tab.
     *
     * @param value the layout the tabs live in
     */
    public void setHost(ViewHost value)
    {
        this.host = value;
        contextMenu.setHost(value);
        value.addListener(new ViewHost.Listener()
        {
            @Override
            public void menuRequested(ViewId view, MouseEvent event)
            {
                contextMenu.showFor(view, event);
            }
        });
        value.open(new ViewSpec(Views.WELCOME, TabLook.of("Welcome", Icons.getIcon("home")).pinned(), ViewSpec.Kind.DOCUMENT, Stacks.DOCUMENTS, welcomeTab, () ->
        {
        }));
    }

    private static Icon classIcon(ClassEntryModel classEntry)
    {
        final Icon icon = Icons.getIcon(classEntry.getIconKey());
        return classEntry.hasMainMethod() ? new RunnableOverlayIcon(icon) : icon;
    }

    /**
     * Opens a class in its tab, or brings its tab forward and switches it to the view mode.
     *
     * @param classEntry the class
     * @param viewMode how to show it
     */
    public void openClass(ClassEntryModel classEntry, ViewMode viewMode)
    {
        String key = classEntry.getClassName();

        EditorTab existingTab = registry.getClassTab(key);
        if (existingTab != null)
        {
            host.select(ViewId.forClass(key));
            existingTab.setViewMode(viewMode);
            return;
        }

        EditorTab tab = new EditorTab(classEntry);
        tab.setViewMode(viewMode);
        tab.setOmitAnnotations(omitAnnotations);
        if (projectModel != null)
        {
            tab.setProjectModel(projectModel);
        }
        registry.putClassTab(key, tab);

        host.open(ViewSpec.document(ViewId.forClass(key), TabLook.of(tab.getTitle(), classIcon(classEntry)).withTooltip(tab.getTooltip()), tab).closedBy(() -> closeTab(tab)));
    }

    /**
     * Opens a resource in its tab, or brings its tab forward.
     *
     * @param resource the resource
     */
    public void openResource(ResourceEntryModel resource)
    {
        String key = resource.getPath();

        ResourceEditorTab existingTab = registry.getResourceTab(key);
        if (existingTab != null)
        {
            host.select(ViewId.forResource(key));
            return;
        }

        ResourceEditorTab tab = new ResourceEditorTab(resource);
        registry.putResourceTab(key, tab);

        host.open(ViewSpec.document(ViewId.forResource(key), TabLook.of(tab.getTitle(), Icons.getIcon(resource.getIconKey())).withTooltip(tab.getTooltip()), tab).closedBy(() -> closeResourceTab(tab)));
    }

    /**
     * Closes a resource tab, showing Welcome when no class or resource tab is left.
     *
     * @param tab the tab
     */
    public void closeResourceTab(ResourceEditorTab tab)
    {
        final String path = tab.getResource().getPath();
        if (registry.getResourceTab(path) == null)
        {
            return;
        }
        registry.removeResourceTab(path);
        close(ViewId.forResource(path));
        if (registry.noClassOrResourceTabs())
        {
            showWelcomeTab();
        }
    }

    /**
     * Opens a plugin's document tab, or brings it forward where it is already open.
     *
     * @param id the plugin's id for the view
     * @param title the tab's title
     * @param icon the tab's icon, or null
     * @param view the panel
     */
    public void openCustomView(String id, String title, Icon icon, JComponent view)
    {
        openCustomView(id, title, icon, view, null);
    }

    /**
     * Opens a plugin's document tab with an action to run when it closes, or brings it forward where it is already open.
     *
     * @param id the plugin's id for the view
     * @param title the tab's title
     * @param icon the tab's icon, or null
     * @param view the panel
     * @param onClose run when the tab closes by any route, or null
     */
    public void openCustomView(String id, String title, Icon icon, JComponent view, Runnable onClose)
    {
        JComponent existing = registry.getCustomView(id);
        if (existing != null)
        {
            host.select(ViewId.custom(id));
            return;
        }

        registry.putCustomView(id, view, onClose);
        host.open(ViewSpec.document(ViewId.custom(id), TabLook.of(title, icon).withTooltip(title), view).closedBy(() -> closeCustomView(id)));
    }

    /**
     * Tells whether a plugin's document tab is open.
     *
     * @param id the plugin's id for the view
     * @return true while a tab with that id is open
     */
    public boolean hasCustomView(String id)
    {
        return registry.getCustomView(id) != null;
    }

    /**
     * Closes a plugin's document tab and runs its close action; an id that is not open is ignored.
     *
     * @param id the plugin's id for the view
     */
    public void closeCustomView(String id)
    {
        JComponent view = registry.getCustomView(id);
        if (view == null)
        {
            return;
        }
        Runnable onClose = registry.removeCustomView(id);
        close(ViewId.custom(id));
        if (registry.isEmpty())
        {
            showWelcomeTab();
        }
        if (onClose != null)
        {
            onClose.run();
        }
    }

    /**
     * Closes a class tab, showing Welcome when no class tab is left.
     *
     * @param tab the tab
     */
    public void closeTab(EditorTab tab)
    {
        final String className = tab.getClassEntry().getClassName();
        if (registry.getClassTab(className) == null)
        {
            return;
        }
        registry.removeClassTab(className);
        close(ViewId.forClass(className));
        if (registry.noClassTabs())
        {
            showWelcomeTab();
        }
    }

    private void close(ViewId view)
    {
        if (host != null && host.isOpen(view))
        {
            host.close(view);
        }
    }

    /**
     * Closes the tab of a class where it is open.
     *
     * @param className the class's name
     */
    public void closeTabForClass(String className)
    {
        EditorTab tab = registry.getClassTab(className);
        if (tab != null)
        {
            closeTab(tab);
        }
    }

    /**
     * Closes the tab of a resource where it is open.
     *
     * @param path the resource's path
     */
    public void closeTabForResource(String path)
    {
        ResourceEditorTab tab = registry.getResourceTab(path);
        if (tab != null)
        {
            closeResourceTab(tab);
        }
    }

    /** Closes every document tab except Welcome, wherever each has been dragged. */
    public void closeAllTabs()
    {
        contextMenu.closeAllTabs();
    }

    /**
     * Closes every closable tab beside one, in the pane it is in.
     *
     * @param keepTab the tab body to keep
     */
    public void closeOtherTabs(Component keepTab)
    {
        contextMenu.closeOtherTabs(keepTab);
    }

    /**
     * Closes every closable tab to the left of one, in the pane it is in.
     *
     * @param referenceTab the tab body to count from
     */
    public void closeTabsToLeft(Component referenceTab)
    {
        contextMenu.closeTabsToLeft(referenceTab);
    }

    /**
     * Closes every closable tab to the right of one, in the pane it is in.
     *
     * @param referenceTab the tab body to count from
     */
    public void closeTabsToRight(Component referenceTab)
    {
        contextMenu.closeTabsToRight(referenceTab);
    }

    /**
     * Finds the class tab in front of the documents stack.
     *
     * @return the tab, or null where the front tab is not a class or nothing is open
     */
    public EditorTab getCurrentTab()
    {
        if (host == null)
        {
            return null;
        }
        final Component selected = host.frontOf(Stacks.DOCUMENTS)
                .flatMap(host::bodyOf)
                .orElse(null);
        return selected instanceof EditorTab ? (EditorTab) selected : null;
    }

    /**
     * Finds the class in the front class tab.
     *
     * @return the class, or null where no class tab is in front
     */
    public ClassEntryModel getCurrentClass()
    {
        EditorTab tab = getCurrentTab();
        return tab != null ? tab.getClassEntry() : null;
    }

    /**
     * Switches every open class tab to a view mode.
     *
     * @param mode the view mode
     */
    public void setViewMode(ViewMode mode)
    {
        for (EditorTab tab : registry.classTabs())
        {
            tab.setViewMode(mode);
        }
    }

    /**
     * Finds the view mode of the front class tab.
     *
     * @return its view mode, or SOURCE where no class tab is in front
     */
    public ViewMode getViewMode()
    {
        EditorTab current = getCurrentTab();
        if (current != null)
        {
            return current.getViewMode();
        }
        return ViewMode.SOURCE;
    }

    /**
     * Sets whether decompiled output omits annotations, in open tabs and tabs opened later.
     *
     * @param omit true to omit annotations
     */
    public void setOmitAnnotations(boolean omit)
    {
        this.omitAnnotations = omit;
        for (EditorTab tab : registry.classTabs())
        {
            tab.setOmitAnnotations(omit);
        }
    }

    /**
     * Turns usage-count lenses on or off in every open class tab.
     *
     * @param enabled true to show the lenses
     */
    public void setUsageLensEnabled(boolean enabled)
    {
        for (EditorTab tab : registry.classTabs())
        {
            tab.setUsageLensEnabled(enabled);
        }
    }

    /** Refreshes the front class tab. */
    public void refreshCurrentTab()
    {
        EditorTab tab = getCurrentTab();
        if (tab != null)
        {
            tab.refresh();
        }
    }

    /** Reloads every open class tab from the current bytecode, dropping stale decompilation. */
    public void reloadAllTabs()
    {
        for (EditorTab tab : registry.classTabs())
        {
            tab.reload();
        }
    }

    /** Redraws the breakpoint gutters of every open class tab. */
    public void refreshBreakpointGutters()
    {
        for (EditorTab tab : registry.classTabs())
        {
            tab.refreshBreakpointGutters();
        }
    }

    /** Copies the selection in the front class tab. */
    public void copySelection()
    {
        EditorTab tab = getCurrentTab();
        if (tab != null)
        {
            tab.copySelection();
        }
    }

    /** Shows the find dialog for the front class tab. */
    public void showFindDialog()
    {
        EditorTab tab = getCurrentTab();
        if (tab != null)
        {
            tab.showFindDialog();
        }
    }

    /** Asks for a line number and moves the front class tab to it; input that is not a number is ignored. */
    public void showGoToLineDialog()
    {
        EditorTab tab = getCurrentTab();
        if (tab == null) return;

        String input = JOptionPane.showInputDialog(tab, "Go to line:", "Go to Line", JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.isEmpty())
        {
            try
            {
                int line = Integer.parseInt(input.trim());
                tab.goToLine(line);
            }
            catch (NumberFormatException ignored)
            {
            }
        }
    }

    /**
     * Finds the method at the caret of the front class tab.
     *
     * @return the method, or null where there is none
     */
    public MethodEntryModel getCurrentMethod()
    {
        EditorTab tab = getCurrentTab();
        return tab != null ? tab.getCurrentMethod() : null;
    }

    /**
     * Reads the selection in the front class tab.
     *
     * @return the selected text, or null where no class tab is in front
     */
    public String getSelectedText()
    {
        EditorTab tab = getCurrentTab();
        return tab != null ? tab.getSelectedText() : null;
    }

    /**
     * Scrolls the front class tab to a method.
     *
     * @param method the method
     */
    public void scrollToMethod(MethodEntryModel method)
    {
        EditorTab tab = getCurrentTab();
        if (tab != null)
        {
            tab.scrollToMethod(method);
        }
    }

    /**
     * Scrolls the front class tab to a field.
     *
     * @param field the field
     */
    public void scrollToField(FieldEntryModel field)
    {
        EditorTab tab = getCurrentTab();
        if (tab != null)
        {
            tab.scrollToField(field);
        }
    }

    /**
     * Moves the front class tab to a line and highlights it; lines below 1 are ignored.
     *
     * @param line the line number, from 1
     */
    public void goToLineAndHighlight(int line)
    {
        EditorTab tab = getCurrentTab();
        if (tab != null && line > 0)
        {
            tab.highlightLine(line);
        }
    }

    /**
     * Sets the font size of every open class tab.
     *
     * @param size the font size in points
     */
    public void setFontSize(int size)
    {
        for (EditorTab tab : registry.classTabs())
        {
            tab.setFontSize(size);
        }
    }

    /**
     * Turns word wrap on or off in every open class tab.
     *
     * @param enabled true to wrap
     */
    public void setWordWrap(boolean enabled)
    {
        for (EditorTab tab : registry.classTabs())
        {
            tab.setWordWrap(enabled);
        }
    }

    /**
     * Sets the project that open tabs and the Welcome tab navigate within.
     *
     * @param projectModel the project
     */
    public void setProjectModel(ProjectModel projectModel)
    {
        this.projectModel = projectModel;
        for (EditorTab tab : registry.classTabs())
        {
            tab.setProjectModel(projectModel);
        }
        if (welcomeTab != null)
        {
            welcomeTab.setProjectModel(projectModel);
        }
    }

    /** Refreshes the Welcome tab after classes are loaded. */
    public void refreshWelcomeTab()
    {
        if (welcomeTab != null)
        {
            welcomeTab.refresh();
        }
    }

    /** Brings the Welcome tab forward. */
    public void showWelcomeTab()
    {
        if (host != null)
        {
            host.select(Views.WELCOME);
        }
    }

    /**
     * Opens a class in bytecode view and highlights an instruction.
     *
     * @param classEntry the class containing the method
     * @param methodName the method's name
     * @param methodDesc the method's descriptor
     * @param pc the bytecode offset
     * @return true where the instruction was found
     */
    public boolean navigateToPC(ClassEntryModel classEntry, String methodName, String methodDesc, int pc)
    {
        openClass(classEntry, ViewMode.BYTECODE);

        EditorTab tab = registry.getClassTab(classEntry.getClassName());
        if (tab != null)
        {
            return tab.navigateToPC(methodName, methodDesc, pc);
        }
        return false;
    }

    /**
     * Opens a class in source view at the statement for a bytecode offset, selecting a token on that line.
     *
     * @param classEntry the class containing the method
     * @param methodName the method's name
     * @param methodDesc the method's descriptor
     * @param pc the bytecode offset
     * @param selectToken the text to select on the line, such as a referenced member's name
     * @return true where the statement was found
     */
    public boolean navigateToSourceOffset(ClassEntryModel classEntry, String methodName, String methodDesc, int pc, String selectToken)
    {
        openClass(classEntry, ViewMode.SOURCE);

        EditorTab tab = registry.getClassTab(classEntry.getClassName());
        if (tab != null)
        {
            return tab.navigateToSourceOffset(methodName, methodDesc, pc, selectToken);
        }
        return false;
    }

    /**
     * Opens a class and scrolls to a method.
     *
     * @param classEntry the class containing the method
     * @param methodName the method's name
     * @param methodDesc the method's descriptor, or null to match by name
     * @param viewMode how to show the class
     * @return true where the method was found
     */
    public boolean navigateToMethod(ClassEntryModel classEntry, String methodName, String methodDesc, ViewMode viewMode)
    {
        openClass(classEntry, viewMode);

        EditorTab tab = registry.getClassTab(classEntry.getClassName());
        if (tab != null)
        {
            return tab.navigateToMethod(methodName, methodDesc);
        }
        return false;
    }

    /**
     * Finds the open tab of a class.
     *
     * @param className the class's name
     * @return its tab, or null where it is not open
     */
    public EditorTab getTab(String className)
    {
        return registry.getClassTab(className);
    }
}
