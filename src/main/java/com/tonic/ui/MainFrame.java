package com.tonic.ui;

import com.tonic.parser.ClassFile;
import com.tonic.ui.analysis.AnalysisPanel;
import com.tonic.ui.console.ConsolePanel;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.editor.ViewMode;
import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.event.events.MethodSelectedEvent;
import com.tonic.event.events.ProjectLoadedEvent;
import com.tonic.event.events.ProjectRenamedEvent;
import com.tonic.event.events.ScriptConsoleEvent;
import com.tonic.event.events.ScriptWrittenEvent;
import com.tonic.event.events.ProjectUpdatedEvent;
import com.tonic.event.events.ResourceSelectedEvent;
import com.tonic.ui.bottom.BottomPanel;
import com.tonic.ui.bottom.BottomToolbar;
import com.tonic.model.Bookmark;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.Comment;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.navigator.NavigatorPanel;
import com.tonic.ui.properties.PropertiesPanel;
import com.tonic.plugin.gui.GuiPluginManager;
import com.tonic.service.ProjectDatabaseService;
import com.tonic.service.ProjectService;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.model.Snapshot;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.script.ScriptEditorDialog;
import com.tonic.ui.update.UpdateManager;
import com.tonic.util.Settings;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.dialog.ExecuteMethodDialog;

import com.tonic.parser.ClassPool;
import com.tonic.renamer.Renamer;
import com.tonic.renamer.exception.RenameException;
import com.tonic.ui.dialog.DeobfuscateNamesDialog;
import com.tonic.ui.dialog.DialogManager;
import com.tonic.ui.file.FileOperationsController;
import com.tonic.ui.layout.Arrangement;
import com.tonic.ui.layout.LayoutBook;
import com.tonic.ui.layout.LayoutController;
import com.tonic.ui.layout.Presets;
import com.tonic.ui.layout.RearrangeOverlay;
import com.tonic.ui.layout.Stacks;
import com.tonic.ui.dialog.RenameClassDialog;
import com.tonic.ui.dialog.RenameFieldDialog;
import com.tonic.ui.dialog.RenameMethodDialog;
import com.tonic.model.FieldEntryModel;
import com.tonic.ui.query.QueryExplorerPanel;
import com.tonic.ui.core.component.ToolWindowPane;
import com.tonic.event.events.LiveSessionEvent;
import com.tonic.event.events.ScanSeedEvent;
import com.tonic.ui.live.threads.LiveThreadsPanel;
import com.tonic.ui.live.profiler.LiveProfilerPanel;
import com.tonic.ui.live.scanner.LiveValueScannerPanel;
import com.tonic.live.LiveSession;
import com.tonic.event.events.DebugPausedEvent;
import com.tonic.event.events.DebugSessionEvent;
import com.tonic.live.debug.DebugLocation;
import com.tonic.ui.debug.BreakpointService;
import com.tonic.ui.debug.DebugManager;
import com.tonic.ui.debug.DebuggerPanel;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.live.recorder.LiveRecorderPanel;
import com.tonic.ui.run.RunConfigDialog;
import com.tonic.ui.run.RunConsolePanel;
import com.tonic.service.run.RunService;
import com.tonic.service.run.RunStateService;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.live.LiveAttachDialog;
import com.tonic.ui.live.recorder.jfr.JfrAnalysisWindow;
import com.tonic.ui.live.eval.LiveScratchPadDialog;
import com.tonic.ui.live.LiveHeapService;
import com.tonic.ui.live.LiveCaptureService;
import com.tonic.live.protocol.ContentionEdge;
import com.tonic.live.Deadlocks;
import com.tonic.ui.live.LivePatch;
import com.tonic.analysis.query.planner.QueryTarget;
import lombok.Getter;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** The main application window. */
public class MainFrame extends JFrame
{

    @Getter
    private NavigatorPanel navigatorPanel;
    @Getter
    private EditorPanel editorPanel;
    @Getter
    private PropertiesPanel propertiesPanel;
    @Getter
    private ConsolePanel consolePanel;
    @Getter
    private StatusBar statusBar;
    @Getter
    private ToolbarBuilder toolbarBuilder;

    private DialogManager dialogManager;
    private FileOperationsController fileOps;
    private final UpdateManager updateManager;
    private QueryExplorerPanel queryExplorerPanel;
    @Getter
    private ToolWindowPane rightToolWindow;
    private ToolWindowMover toolWindowMover;

    @Getter
    private BottomPanel sidePanel;

    private LayoutController layoutController;
    private BottomToolbar bottomToolbar;
    private RearrangeOverlay rearrange;

    private final NavigationHistory navigationHistory = new NavigationHistory();

    private ViewMode currentViewMode = ViewMode.SOURCE;
    @Getter
    private boolean omitAnnotations = false;

    private int currentFontSize = 13;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 32;
    private static final int DEFAULT_FONT_SIZE = 13;
    private boolean wordWrapEnabled = false;

    /** Creates the main window, restores its layout and settings, and loads plugins once it is built. */
    public MainFrame()
    {
        super(JStudio.APP_NAME + " " + JStudio.APP_VERSION);
        initializeFrame();
        initializeComponents();
        initializeLayout();
        initializeEventHandlers();

        updateManager = new UpdateManager(this);
        SwingUtilities.invokeLater(updateManager::checkOnStartup);

        SwingUtilities.invokeLater(() -> GuiPluginManager.getInstance().bootstrap(this));
    }

    /** Checks for a newer release now, reporting the result either way. */
    public void checkForUpdates()
    {
        updateManager.checkNow();
    }

    private void initializeFrame()
    {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 600));

        try
        {
            URL iconUrl = getClass().getResource("/com/tonic/ui/icon.png");
            if (iconUrl != null)
            {
                setIconImage(ImageIO.read(iconUrl));
            }
        }
        catch (Exception ignored)
        {
        }

        Settings settings = Settings.getInstance();
        int x = settings.getWindowX();
        int y = settings.getWindowY();
        int width = settings.getWindowWidth();
        int height = settings.getWindowHeight();

        if (x >= 0 && y >= 0)
        {
            setLocation(x, y);
        }
        else
        {
            setLocationRelativeTo(null);
        }
        setSize(width, height);

        if (settings.isWindowMaximized())
        {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        currentFontSize = settings.getFontSize();
        wordWrapEnabled = settings.isWordWrapEnabled();

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                exitApplication();
            }
        });

        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter()
        {
            @Override
            public void drop(DropTargetDropEvent dtde)
            {
                fileOps.handleFileDrop(dtde);
            }
        });
    }

    private void initializeComponents()
    {
        navigatorPanel = new NavigatorPanel(this);
        editorPanel = new EditorPanel(this);
        propertiesPanel = new PropertiesPanel();
        consolePanel = new ConsolePanel();
        statusBar = new StatusBar();

        queryExplorerPanel = new QueryExplorerPanel(this);
        rightToolWindow = new ToolWindowPane();
        sidePanel = new BottomPanel();
        bottomToolbar = new BottomToolbar();

        layoutController = new LayoutController(navigatorPanel, bottomToolbar);
        editorPanel.setHost(layoutController);
        sidePanel.setHost(layoutController);
        rightToolWindow.setHost(layoutController);
        toolWindowMover = new ToolWindowMover(layoutController);
        rightToolWindow.setMoveListener(toolWindowMover::move);

        rightToolWindow.addTool("Inspector", propertiesPanel);
        rightToolWindow.addTool("Query", queryExplorerPanel);

        EventBus.getInstance().register(LiveSessionEvent.class, e ->
        {
            if (e.isAttached())
            {
                if (liveThreadsPanel == null)
                {
                    liveThreadsPanel = new LiveThreadsPanel(this);
                }
                rightToolWindow.addTool("Threads", liveThreadsPanel);
                if (liveProfilerPanel == null)
                {
                    liveProfilerPanel = new LiveProfilerPanel();
                }
                rightToolWindow.addTool("Profiler", liveProfilerPanel);
                if (liveValueScannerPanel == null)
                {
                    liveValueScannerPanel = new LiveValueScannerPanel(this);
                }
                rightToolWindow.addTool("Value Scanner", liveValueScannerPanel);
                LiveSession session = LiveAttachService.getInstance().getSession();
                if (session != null && session.supportsJfr())
                {
                    if (liveRecorderPanel == null)
                    {
                        liveRecorderPanel = new LiveRecorderPanel(this);
                    }
                    rightToolWindow.addTool("Recorder", liveRecorderPanel);
                }
            }
            else
            {
                for (String tool : new String[]{"Threads", "Profiler", "Recorder", "Value Scanner"})
                {
                    rightToolWindow.removeTool(tool);
                }
            }
        });

        EventBus.getInstance().register(DebugSessionEvent.class, e ->
        {
            if (e.isConnected())
            {
                if (debuggerPanel == null)
                {
                    debuggerPanel = new DebuggerPanel(this);
                }
                rightToolWindow.addTool("Debugger", debuggerPanel);
            }
            else
            {
                rightToolWindow.removeTool("Debugger");
            }
            editorPanel.refreshBreakpointGutters();
        });

        EventBus.getInstance().register(DebugPausedEvent.class, e ->
        {
            navigateToDebugLocation(e.getLocation());
            rightToolWindow.select("Debugger");
        });

        EventBus.getInstance().register(ScanSeedEvent.class, e ->
        {
            if (liveValueScannerPanel == null)
            {
                return;
            }
            rightToolWindow.select("Value Scanner");
            liveValueScannerPanel.seed(e.getValueType(), e.getValue(), e.getPackageFilter());
        });

        sidePanel.setEditorPanel(editorPanel);
        sidePanel.setConsolePanel(consolePanel);
        sidePanel.setOnAllTabsClosed(() -> layoutController.collapseBottom());
        sidePanel.setOnTabOpened(() -> layoutController.expandBottom());
        sidePanel.setCollapseHost(new BottomPanel.CollapseHost()
        {
            @Override
            public boolean isCollapsed()
            {
                return layoutController.isBottomCollapsed();
            }

            @Override
            public void collapse()
            {
                layoutController.collapseBottom();
            }

            @Override
            public void expand()
            {
                layoutController.expandBottom();
            }
        });

        bottomToolbar.setOnConsoleClicked(() -> sidePanel.toggleConsoleTab());
        bottomToolbar.setOnBookmarksClicked(() ->
        {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            sidePanel.setProject(project);
            sidePanel.toggleBookmarksTab();
        });
        bottomToolbar.setOnCommentsClicked(() ->
        {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            sidePanel.setProject(project);
            sidePanel.toggleCommentsTab();
        });
        bottomToolbar.setOnLocalHistoryClicked(() -> sidePanel.toggleLocalHistoryTab());

        dialogManager = new DialogManager(this, editorPanel);
        fileOps = new FileOperationsController(this);
    }

    private void initializeLayout()
    {
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(JStudioTheme.getBgPrimary());

        MenuBarBuilder menuBarBuilder = new MenuBarBuilder(this);
        setJMenuBar(menuBarBuilder.build());

        toolbarBuilder = new ToolbarBuilder(this);
        contentPane.add(toolbarBuilder.build(), BorderLayout.NORTH);

        layoutController.show(Presets.asLaunched(LayoutBook.load(LayoutBook.defaultPath())));
        contentPane.add(layoutController.buildCenter(), BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private void initializeEventHandlers()
    {
        EventBus.getInstance().register(ClassSelectedEvent.class, event ->
        {
            ClassEntryModel classEntry = event.getClassEntry();
            if (classEntry != null)
            {
                openClassInEditor(classEntry);
                if (event.hasScrollTarget())
                {
                    SwingUtilities.invokeLater(() ->
                    {
                        if (event.getHighlightLine() > 0)
                        {
                            editorPanel.goToLineAndHighlight(event.getHighlightLine());
                        }
                    });
                }
            }
        });

        EventBus.getInstance().register(MethodSelectedEvent.class, event ->
        {
            MethodEntryModel method = event.getMethodEntry();
            if (method != null)
            {
                SwingUtilities.invokeLater(() -> editorPanel.scrollToMethod(method));
            }
        });

        EventBus.getInstance().register(ProjectLoadedEvent.class, event ->
        {
            ProjectModel project = event.getProject();
            editorPanel.closeAllTabs();
            sidePanel.closeAllTabs();
            navigatorPanel.loadProject(project);
            editorPanel.setProjectModel(project);
            editorPanel.refreshWelcomeTab();
            ProjectDatabaseService.getInstance().initializeForProject(project);
            if (LocalHistoryService.getInstance().attach(project))
            {
                Snapshot saved = LocalHistoryService.getInstance().newest();
                if (saved != null && LocalHistoryService.getInstance().restore(saved))
                {
                    refreshAfterProjectChange();
                }
            }
            fileOps.updateTitleBar();
        });

        EventBus.getInstance().register(ScriptWrittenEvent.class, event -> SwingUtilities.invokeLater(() ->
        {
            showScriptEditor();
            ScriptEditorDialog dialog = dialogManager.getScriptEditorDialog();
            if (dialog != null)
            {
                dialog.getEditorPanel().selectScriptByName(event.getScriptName());
            }
        }));

        EventBus.getInstance().register(ScriptConsoleEvent.class, event -> SwingUtilities.invokeLater(() -> sidePanel.openScriptConsole().handle(event)));

        EventBus.getInstance().register(ProjectRenamedEvent.class, event -> SwingUtilities.invokeLater(() ->
        {
            if (event.getKind() == ProjectRenamedEvent.Kind.CLASS)
            {
                refreshAfterRename(event.getOldClass(), event.getNewClass());
            }
            else
            {
                refreshAfterProjectChange();
            }
        }));

        EventBus.getInstance().register(ProjectUpdatedEvent.class, event ->
        {
            ProjectModel project = event.getProject();
            if (project != null)
            {
                navigatorPanel.loadProject(project);
                editorPanel.setProjectModel(project);
                editorPanel.refreshWelcomeTab();
            }
        });

        EventBus.getInstance().register(ResourceSelectedEvent.class, event ->
        {
            if (event.getResource() != null)
            {
                editorPanel.openResource(event.getResource());
            }
        });

        EventBus.getInstance().register(FindUsagesEvent.class, event ->
        {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            if (project != null)
            {
                sidePanel.setProject(project);
                sidePanel.openFindUsagesTab(event);
            }
        });
    }

    /** Asks for a file and opens it as the project. */
    public void showOpenDialog()
    {
        fileOps.showOpenDialog();
    }

    /**
     * Opens a file as the project.
     *
     * @param path the file's path
     */
    public void openFile(String path)
    {
        fileOps.openFile(path);
    }

    /** Exports the front class to a file. */
    public void exportCurrentClass()
    {
        fileOps.exportCurrentClass();
    }

    /**
     * Exports a class to a file.
     *
     * @param classEntry the class
     */
    public void exportClass(ClassEntryModel classEntry)
    {
        fileOps.exportClass(classEntry);
    }

    /** Exports every class to a directory. */
    public void exportAllClasses()
    {
        fileOps.exportAllClasses();
    }

    /** Exports every class and resource as a JAR. */
    public void exportAsJar()
    {
        fileOps.exportAsJar();
    }

    /**
     * Runs a class's main method in a separate JVM with output in the Run tab, attaching the live agent and the debugger where possible; unavailable while attached to a live JVM.
     *
     * @param classEntry the class to run
     */
    public void runMainClass(ClassEntryModel classEntry)
    {
        if (LiveAttachService.getInstance().isAttached())
        {
            showWarning("Run is unavailable while attached to a live JVM.");
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || classEntry == null || !classEntry.hasMainMethod())
        {
            return;
        }
        File defaultDir = project.getSourceFile() != null ? project.getSourceFile().getParentFile() : null;
        RunConfigDialog.RunConfig config =
                RunConfigDialog.show(this, classEntry.getSimpleName(), defaultDir);
        if (config == null)
        {
            return;
        }
        String internalName = classEntry.getClassName();
        RunConsolePanel panel = sidePanel.openRunConsole();
        Runnable launch = () -> launchWithLiveDebug(project, internalName, config, panel);
        panel.setRerunAction(launch);
        launch.run();
    }

    private void launchWithLiveDebug(ProjectModel project, String internalName, RunConfigDialog.RunConfig config, RunConsolePanel panel)
    {
        List<String> vmOptions = new ArrayList<>(config.vmOptions);
        int port = -1;
        int jdwpPort = -1;
        File agentJar = LiveAttachService.getInstance().resolveAgentJar();
        if (agentJar == null)
        {
            consolePanel.log("Live debugging unavailable (agent jar not found); running without it.");
        }
        else if (config.javaFeature > 0 && config.javaFeature < 11)
        {
            consolePanel.log("Live debugging needs Java 11+ on the selected JDK; running without it.");
        }
        else
        {
            try (ServerSocket probe = new ServerSocket(0))
            {
                port = probe.getLocalPort();
            }
            catch (IOException ignored)
            {
            }
            if (port > 0)
            {
                vmOptions.add(0, "-javaagent:" + agentJar.getAbsolutePath() + "=port=" + port);
            }
            try (ServerSocket probe = new ServerSocket(0))
            {
                jdwpPort = probe.getLocalPort();
            }
            catch (IOException ignored)
            {
            }
            if (jdwpPort > 0)
            {
                String suspend = BreakpointService.getInstance().all().isEmpty() ? "n" : "y";
                vmOptions.add(0, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend + ",address=127.0.0.1:" + jdwpPort);
            }
        }

        Process process = RunService.run(project, internalName, config.programArgs, vmOptions, config.workingDir, config.javaHome, panel);
        panel.setProcess(process);
        if (process != null)
        {
            RunStateService.getInstance().setProcess(process);
            process.onExit().thenAccept(p -> RunStateService.getInstance().clearIf(process));
        }
        if (process == null)
        {
            return;
        }

        String pid = String.valueOf(process.pid());
        if (port > 0)
        {
            int agentPort = port;
            SwingWorkers.run(() -> LiveSession.connect(pid, agentPort), session ->
            {
                LiveAttachService.getInstance().adoptRunSession(session);
                setLiveCaptureEnabled(true);
            }, err -> consolePanel.log("Live debugging not attached: " + err.getMessage()));
        }
        if (jdwpPort > 0)
        {
            int debugPort = jdwpPort;
            SwingWorkers.run(() ->
            {
                DebugManager.getInstance().connectWithRetry("127.0.0.1", debugPort);
                return Boolean.TRUE;
            }, ok -> consolePanel.log("Debugger attached (JDI)."), err -> consolePanel.log("Debugger not attached: " + err.getMessage()));
        }
        process.onExit().thenAccept(p -> SwingUtilities.invokeLater(this::onRunProcessExited));
    }

    private void onRunProcessExited()
    {
        DebugManager.getInstance().disconnect();
        LiveAttachService svc = LiveAttachService.getInstance();
        if (svc.isRunSession())
        {
            detachLive();
        }
    }

    /** Closes the project, asking first where it has unsaved changes. */
    public void closeProject()
    {
        fileOps.closeProject();
    }

    /** Asks for a saved project file and opens it. */
    public void openProjectFile()
    {
        fileOps.openProjectFile();
    }

    /** Saves the project database. */
    public void saveProject()
    {
        fileOps.saveProject();
    }

    /** Saves the project database to a file the user chooses. */
    public void saveProjectAs()
    {
        fileOps.saveProjectAs();
    }

    /** Saves settings and the layout, shuts plugins down and exits, unless the user cancels over unsaved changes. */
    public void exitApplication()
    {
        if (!fileOps.confirmCloseIfDirty())
        {
            return;
        }

        saveSettings();

        GuiPluginManager.getInstance().shutdown();
        statusBar.dispose();
        toolbarBuilder.dispose();
        queryExplorerPanel.shutdown();
        if (layoutController != null)
        {
            layoutController.closeTornOut();
        }
        dispose();
        System.exit(0);
    }

    private void saveSettings()
    {
        Settings settings = Settings.getInstance();

        boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        settings.setWindowMaximized(maximized);

        if (!maximized)
        {
            settings.saveWindowBounds(getX(), getY(), getWidth(), getHeight(), false);
        }

        layoutController.saveLayout();

        settings.setFontSize(currentFontSize);
        settings.setWordWrapEnabled(wordWrapEnabled);

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null && project.getSourceFile() != null)
        {
            settings.setLastProject(project.getSourceFile().getAbsolutePath());
        }
    }

    /**
     * Opens a class in the current view mode and records it in the navigation history.
     *
     * @param classEntry the class
     */
    public void openClassInEditor(ClassEntryModel classEntry)
    {
        editorPanel.openClass(classEntry, currentViewMode);
        navigationHistory.push(classEntry);
        statusBar.setPosition(classEntry.getClassName());
    }

    /** Opens the previous class in the navigation history. */
    public void navigateBack()
    {
        ClassEntryModel entry = navigationHistory.back();
        if (entry != null)
        {
            editorPanel.openClass(entry, currentViewMode);
        }
    }

    /** Opens the next class in the navigation history. */
    public void navigateForward()
    {
        ClassEntryModel entry = navigationHistory.forward();
        if (entry != null)
        {
            editorPanel.openClass(entry, currentViewMode);
        }
    }

    /** Clears the back and forward navigation history. */
    public void clearNavigationHistory()
    {
        navigationHistory.clear();
    }

    /** Disposes the cached analysis dialog. */
    public void disposeAnalysisDialog()
    {
        dialogManager.disposeAnalysisDialog();
    }

    /**
     * Switches every class tab, the status bar and the toolbar to a view mode.
     *
     * @param mode the view mode
     */
    public void switchToView(ViewMode mode)
    {
        currentViewMode = mode;
        editorPanel.setViewMode(mode);
        statusBar.setMode(mode.getDisplayName());
        toolbarBuilder.setViewMode(mode);
    }

    /** Switches to source view. */
    public void switchToSourceView()
    {
        switchToView(ViewMode.SOURCE);
    }

    /** Switches to bytecode view. */
    public void switchToBytecodeView()
    {
        switchToView(ViewMode.BYTECODE);
    }

    /** Switches to IR view. */
    public void switchToIRView()
    {
        switchToView(ViewMode.IR);
    }

    /** Switches to hex view. */
    public void switchToHexView()
    {
        switchToView(ViewMode.HEX);
    }

    /**
     * Sets whether decompiled output omits annotations.
     *
     * @param omit true to omit annotations
     */
    public void setOmitAnnotations(boolean omit)
    {
        this.omitAnnotations = omit;
        editorPanel.setOmitAnnotations(omit);
    }

    /** Shows the navigator, or flips it between put away and open. */
    public void toggleNavigatorPanel()
    {
        layoutController.toggleNavigatorPanel();
    }

    /** Shows the tool windows, or flips them between put away and open. */
    public void togglePropertiesPanel()
    {
        layoutController.toggle(Stacks.TOOLS);
    }

    /** Turns rearrange mode on or off, creating its overlay the first time. */
    public void toggleRearranging()
    {
        if (rearrange == null)
        {
            rearrange = new RearrangeOverlay(getRootPane(), layoutController);
        }
        if (rearrange.isRearranging())
        {
            rearrange.end();
        }
        else
        {
            rearrange.begin();
        }
    }

    /**
     * Draws an arrangement in place of the current one.
     *
     * @param arrangement the arrangement to draw
     */
    public void applyLayout(Arrangement arrangement)
    {
        layoutController.show(arrangement);
    }

    /** Opens or reveals the Console tab. */
    public void toggleConsolePanel()
    {
        sidePanel.toggleConsoleTab();
    }

    /** Refreshes the front class tab. */
    public void refreshCurrentView()
    {
        editorPanel.refreshCurrentTab();
    }

    /**
     * Closes a renamed class's old tab and refreshes after the project change.
     *
     * @param oldClassName the class's internal name before the rename
     * @param newClassName the class's internal name after the rename
     */
    public void refreshAfterRename(String oldClassName, String newClassName)
    {
        editorPanel.closeTabForClass(oldClassName);
        refreshAfterProjectChange();
        statusBar.setMessage("Renamed: " + oldClassName.replace('/', '.') + " -> " + newClassName.replace('/', '.'));
    }

    /** Drops every class's decompilation cache, reloads all open class tabs and rebuilds the navigator, after a change that can affect references across classes. */
    public void refreshAfterProjectChange()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            project.invalidateAllDecompilationCaches();
        }
        editorPanel.reloadAllTabs();
        navigatorPanel.refresh();
        navigatorPanel.setLoading(false);
        editorPanel.refreshWelcomeTab();
    }

    /** Performs the user-invoked full refresh: every class is decompiled again from its current bytecode. */
    public void fullRefresh()
    {
        refreshAfterProjectChange();
        statusBar.setMessage("Refreshed - re-decompiled all classes from current bytecode");
    }

    /**
     * Closes a class's tab where it is open.
     *
     * @param className the class's internal name
     */
    public void closeEditorForClass(String className)
    {
        editorPanel.closeTabForClass(className);
    }

    /**
     * Closes a resource's tab where it is open.
     *
     * @param path the resource's path
     */
    public void closeEditorForResource(String path)
    {
        editorPanel.closeTabForResource(path);
    }

    /**
     * Closes the old tabs of renamed classes and refreshes the navigator after a bulk rename.
     *
     * @param oldClassNames the internal names of the renamed classes before renaming
     * @param totalRenamed how many items were renamed, for the status bar
     */
    public void refreshAfterBulkRename(Set<String> oldClassNames, int totalRenamed)
    {
        for (String oldName : oldClassNames)
        {
            editorPanel.closeTabForClass(oldName);
        }

        navigatorPanel.refresh();
        navigatorPanel.setLoading(false);
        editorPanel.refreshWelcomeTab();

        statusBar.setMessage("Deobfuscation complete: " + totalRenamed + " items renamed");
    }

    /**
     * Shows or hides the navigator's loading state.
     *
     * @param loading true while loading
     */
    public void setNavigatorLoading(boolean loading)
    {
        navigatorPanel.setLoading(loading);
    }

    /** Increases the editor font size by two points, up to the maximum. */
    public void increaseFontSize()
    {
        if (currentFontSize < MAX_FONT_SIZE)
        {
            currentFontSize += 2;
            editorPanel.setFontSize(currentFontSize);
            statusBar.setMessage("Font size: " + currentFontSize);
        }
    }

    /** Decreases the editor font size by two points, down to the minimum. */
    public void decreaseFontSize()
    {
        if (currentFontSize > MIN_FONT_SIZE)
        {
            currentFontSize -= 2;
            editorPanel.setFontSize(currentFontSize);
            statusBar.setMessage("Font size: " + currentFontSize);
        }
    }

    /** Resets the editor font size to the default. */
    public void resetFontSize()
    {
        currentFontSize = DEFAULT_FONT_SIZE;
        editorPanel.setFontSize(currentFontSize);
        statusBar.setMessage("Font size reset to " + currentFontSize);
    }

    /**
     * Turns word wrap on or off in the editor.
     *
     * @param enabled true to wrap
     */
    public void toggleWordWrap(boolean enabled)
    {
        wordWrapEnabled = enabled;
        editorPanel.setWordWrap(enabled);
        statusBar.setMessage("Word wrap " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Turns usage-count lenses on or off and remembers the choice.
     *
     * @param enabled true to show the lenses
     */
    public void toggleUsageLens(boolean enabled)
    {
        Settings.getInstance().setUsageLensEnabled(enabled);
        editorPanel.setUsageLensEnabled(enabled);
        statusBar.setMessage("Usage counts " + (enabled ? "enabled" : "disabled"));
    }

    /** Copies the selection in the front class tab. */
    public void copySelection()
    {
        editorPanel.copySelection();
    }

    /** Shows the find dialog for the front class tab. */
    public void showFindDialog()
    {
        editorPanel.showFindDialog();
    }

    /** Shows the find-in-project dialog. */
    public void showFindInProjectDialog()
    {
        dialogManager.showFindInProjectDialog();
    }

    /** Focuses the navigator's search field. */
    public void showGoToClassDialog()
    {
        navigatorPanel.focusSearchField();
    }

    /** Asks for a line number and moves the front class tab to it. */
    public void showGoToLineDialog()
    {
        editorPanel.showGoToLineDialog();
    }

    /** Asks for a name and bookmarks the front class. */
    public void addBookmarkAtCurrentLocation()
    {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null)
        {
            showWarning("No class selected. Open a class first to add a bookmark.");
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty())
        {
            return;
        }

        Bookmark bookmark = new Bookmark(currentClass.getClassName(), name.trim());
        ProjectDatabaseService.getInstance().addBookmark(bookmark);
        consolePanel.log("Added bookmark: " + name.trim() + " -> " + currentClass.getSimpleName());
    }

    /** Asks for text and adds a class comment to the front class. */
    public void addCommentAtCurrentLocation()
    {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null)
        {
            showWarning("No class selected. Open a class first to add a comment.");
            return;
        }

        JTextArea textArea = new JTextArea(5, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Add Comment for " + currentClass.getSimpleName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !textArea.getText().trim().isEmpty())
        {
            Comment comment = new Comment(currentClass.getClassName(), -1, textArea.getText().trim());
            comment.setType(Comment.Type.CLASS);
            ProjectDatabaseService.getInstance().addComment(comment);
            consolePanel.log("Added comment to " + currentClass.getSimpleName());
        }
    }

    /** Opens or reveals the Local History tab. */
    public void showLocalHistoryPanel()
    {
        sidePanel.toggleLocalHistoryTab();
    }

    /** Snapshots the current project into local history as a manual checkpoint. */
    public void createHistoryCheckpoint()
    {
        Snapshot created = LocalHistoryService.getInstance().snapshot(Snapshot.Trigger.MANUAL.getDefaultLabel(), Snapshot.Trigger.MANUAL);
        if (created == null && LocalHistoryService.getInstance().isEnabled())
        {
            statusBar.setMessage("No changes since the last snapshot");
        }
        else if (created != null)
        {
            statusBar.setMessage("Checkpoint created");
        }
    }

    /** Opens or reveals the Bookmarks tab for the current project. */
    public void showBookmarksPanel()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            sidePanel.setProject(project);
            sidePanel.toggleBookmarksTab();
        }
    }

    /** Opens or reveals the Comments tab for the current project. */
    public void showCommentsPanel()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            sidePanel.setProject(project);
            sidePanel.toggleCommentsTab();
        }
    }

    /** Opens the analysis dialog and runs its analysis. */
    public void runAnalysis()
    {
        dialogManager.runAnalysis();
    }

    /** Opens the similarity analysis. */
    public void showSimilarityAnalysis()
    {
        dialogManager.showSimilarityAnalysis();
    }

    /** Opens the search analysis. */
    public void showSearchAnalysis()
    {
        dialogManager.showSearchAnalysis();
    }

    /** Opens the strings analysis. */
    public void showStringsAnalysis()
    {
        dialogManager.showStringsAnalysis();
    }

    /** Opens the Remove Dead Code dialog, reusing one instance. */
    public void showRemoveDeadCodeDialog()
    {
        dialogManager.showRemoveDeadCodeDialog();
    }

    /**
     * Closes the tabs of classes dead-code removal deleted and reloads the navigator and editor.
     *
     * @param removedClassesInternal the internal names of the removed classes
     */
    public void refreshAfterDeadCodeRemoval(Collection<String> removedClassesInternal)
    {
        dialogManager.refreshAfterDeadCodeRemoval(removedClassesInternal);
    }

    /** Opens the transform dialog. */
    public void showTransformDialog()
    {
        dialogManager.showTransformDialog();
    }

    /** Shows the script editor dialog. */
    public void showScriptEditor()
    {
        dialogManager.showScriptEditor();
    }

    /** Opens the deobfuscation panel. */
    public void showDeobfuscationPanel()
    {
        dialogManager.showDeobfuscationPanel();
    }

    /** Opens the Deobfuscate Names dialog for the loaded project. */
    public void showDeobfuscateNamesDialog()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            showWarning("No project loaded. Load a project first.");
            return;
        }

        DeobfuscateNamesDialog dialog = new DeobfuscateNamesDialog(this);
        dialog.setVisible(true);
    }

    /**
     * Asks for a new class name and renames the class across the project.
     *
     * @param classEntry the class to rename
     */
    public void showRenameClassDialog(ClassEntryModel classEntry)
    {
        if (classEntry == null)
        {
            showWarning("No class selected.");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            showWarning("No project loaded.");
            return;
        }

        String oldName = classEntry.getClassName();
        RenameClassDialog dialog = new RenameClassDialog(this, oldName);
        dialog.setVisible(true);

        if (!dialog.isConfirmed())
        {
            return;
        }

        String newName = dialog.getNewClassName();
        if (newName.equals(oldName))
        {
            return;
        }

        setNavigatorLoading(true);
        ClassPool classPool = project.getClassPool();

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                LocalHistoryService.getInstance().snapshot("Rename class " + classEntry.getSimpleName(), Snapshot.Trigger.RENAME);
                Renamer renamer = new Renamer(classPool);
                renamer.mapClass(oldName, newName).apply();
                project.notifyClassRenamed(oldName, newName);
                refreshAfterRename(oldName, newName);
                consolePanel.log("Renamed class: " + oldName.replace('/', '.') + " -> " + newName.replace('/', '.'));
            }
            catch (RenameException e)
            {
                setNavigatorLoading(false);
                showError("Rename failed: " + e.getMessage());
                consolePanel.logError("Rename failed: " + e.getMessage());
            }
        });
    }

    /**
     * Asks for a new method name and renames the method across the project.
     *
     * @param classEntry the declaring class
     * @param method the method to rename
     */
    public void showRenameMethodDialog(ClassEntryModel classEntry, MethodEntryModel method)
    {
        if (classEntry == null || method == null)
        {
            showWarning("No method selected.");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            showWarning("No project loaded.");
            return;
        }

        String className = classEntry.getClassName();
        String oldName = method.getName();
        String desc = method.getMethodEntry().getDesc();

        RenameMethodDialog dialog = new RenameMethodDialog(this, oldName, desc);
        dialog.setVisible(true);

        if (!dialog.isConfirmed())
        {
            return;
        }

        String newName = dialog.getNewMethodName();
        if (newName.equals(oldName))
        {
            return;
        }

        setNavigatorLoading(true);
        ClassPool classPool = project.getClassPool();

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                LocalHistoryService.getInstance().snapshot("Rename method " + oldName, Snapshot.Trigger.RENAME);
                Renamer renamer = new Renamer(classPool);
                renamer.mapMethod(className, oldName, desc, newName).apply();
                refreshAfterProjectChange();
                consolePanel.log("Renamed method: " + oldName + " -> " + newName + " in " + classEntry.getSimpleName());
                statusBar.setMessage("Renamed method: " + oldName + " -> " + newName);
            }
            catch (RenameException e)
            {
                setNavigatorLoading(false);
                showError("Rename failed: " + e.getMessage());
                consolePanel.logError("Rename failed: " + e.getMessage());
            }
        });
    }

    /**
     * Asks for a new field name and renames the field across the project.
     *
     * @param classEntry the declaring class
     * @param field the field to rename
     */
    public void showRenameFieldDialog(ClassEntryModel classEntry, FieldEntryModel field)
    {
        if (classEntry == null || field == null)
        {
            showWarning("No field selected.");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            showWarning("No project loaded.");
            return;
        }

        String className = classEntry.getClassName();
        String oldName = field.getName();
        String desc = field.getFieldEntry().getDesc();

        RenameFieldDialog dialog = new RenameFieldDialog(this, oldName, desc);
        dialog.setVisible(true);

        if (!dialog.isConfirmed())
        {
            return;
        }

        String newName = dialog.getNewFieldName();
        if (newName.equals(oldName))
        {
            return;
        }

        setNavigatorLoading(true);
        ClassPool classPool = project.getClassPool();

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                LocalHistoryService.getInstance().snapshot("Rename field " + oldName, Snapshot.Trigger.RENAME);
                Renamer renamer = new Renamer(classPool);
                renamer.mapField(className, oldName, desc, newName).apply();
                refreshAfterProjectChange();
                consolePanel.log("Renamed field: " + oldName + " -> " + newName + " in " + classEntry.getSimpleName());
                statusBar.setMessage("Renamed field: " + oldName + " -> " + newName);
            }
            catch (RenameException e)
            {
                setNavigatorLoading(false);
                showError("Rename failed: " + e.getMessage());
                consolePanel.logError("Rename failed: " + e.getMessage());
            }
        });
    }

    /**
     * Applies a named transform to the project.
     *
     * @param transformName the transform's name
     */
    public void applyTransform(String transformName)
    {
        dialogManager.applyTransform(transformName);
    }

    /** Rebuilds the front class's class file in the background and refreshes its tab. */
    public void recomputeStackFrames()
    {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null)
        {
            showWarning("No class selected.");
            return;
        }

        statusBar.showProgress("Rebuilding class...");

        SwingWorker<Void, Void> worker = new SwingWorker<>()
        {
            @Override
            protected Void doInBackground() throws Exception
            {
                ClassFile cf = currentClass.getClassFile();
                cf.write();
                return null;
            }

            @Override
            protected void done()
            {
                statusBar.hideProgress();
                try
                {
                    get();
                    consolePanel.log("Rebuilt class " + currentClass.getClassName());
                    editorPanel.refreshCurrentTab();
                }
                catch (Exception e)
                {
                    showError("Rebuild failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /** Opens the keyboard shortcuts reference as a document tab. */
    public void showKeyboardShortcuts()
    {
        editorPanel.openCustomView("keyboard-shortcuts", "Keyboard Shortcuts", Icons.getIcon("info"), new KeyboardShortcutsView());
    }

    /** Shows the About dialog. */
    public void showAboutDialog()
    {
        String message = JStudio.APP_NAME + " " + JStudio.APP_VERSION + "\n\n" +
                "A professional Java reverse engineering and analysis suite.";

        JOptionPane.showMessageDialog(this, message, "About " + JStudio.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Opens the preferences dialog. */
    public void showPreferencesDialog()
    {
        dialogManager.showPreferencesDialog();
    }

    /** Applies the font size from settings to the editor. */
    public void applyFontSizeFromSettings()
    {
        currentFontSize = Settings.getInstance().getFontSize();
        editorPanel.setFontSize(currentFontSize);
    }

    /**
     * Shows an information message.
     *
     * @param message the text
     */
    public void showInfo(String message)
    {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows a warning message.
     *
     * @param message the text
     */
    public void showWarning(String message)
    {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Shows an error message.
     *
     * @param message the text
     */
    public void showError(String message)
    {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Finds the analysis panel.
     *
     * @return the panel, or null where the analysis dialog has not been opened
     */
    public AnalysisPanel getAnalysisPanel()
    {
        return dialogManager.getAnalysisPanel();
    }

    /** Runs simulation analysis on the current method or class. */
    public void runCodeAnalysis()
    {
        dialogManager.runCodeAnalysis();
    }

    /** Opens the Attach to Live JVM dialog, where attaching replaces the project with the target's loaded classes. */
    public void showLiveAttachDialog()
    {
        new LiveAttachDialog(this).setVisible(true);
    }

    /**
     * Opens the class of a debug location and highlights its source line; a location outside the project is ignored.
     *
     * @param loc the location, or null
     */
    public void navigateToDebugLocation(DebugLocation loc)
    {
        if (loc == null)
        {
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            return;
        }
        ClassEntryModel ce = project.getClass(loc.getClassName().replace('.', '/'));
        if (ce == null)
        {
            ce = project.findClassByName(loc.getClassName());
        }
        if (ce == null)
        {
            return;
        }
        ClassEntryModel target = ce;
        SwingUtilities.invokeLater(() -> editorPanel.navigateToSourceOffset(target, loc.getMethodName(), loc.getMethodDescriptor(), (int) loc.getCodeIndex(), null));
    }

    /** Loads the JDWP agent into the attached JVM and connects the debugger, reporting failure in the console. */
    public void enableDebuggerOnAttached()
    {
        if (DebugManager.getInstance().isConnected())
        {
            return;
        }
        LiveSession s = LiveAttachService.getInstance().getSession();
        if (s == null)
        {
            showWarning("Attach to a live JVM first (Attach -> Attach to Live JVM).");
            return;
        }
        String pid = s.getPid();
        int dp = -1;
        try (ServerSocket probe = new ServerSocket(0))
        {
            dp = probe.getLocalPort();
        }
        catch (IOException ignored)
        {
        }
        if (dp <= 0)
        {
            showWarning("Could not allocate a debug port.");
            return;
        }
        int debugPort = dp;
        consolePanel.log("Enabling debugger (JDI) on pid " + pid + "...");
        SwingWorkers.run(() ->
        {
            DebugManager.getInstance().connectExternal(pid, debugPort);
            return Boolean.TRUE;
        }, ok -> consolePanel.log("Debugger attached (JDI) to pid " + pid + "."), err -> consolePanel.log("Debugger unavailable on this JVM: " + err.getMessage()));
    }

    /**
     * Opens a Flight Recorder recording in the JFR analysis window, reusing one window.
     *
     * @param jfr the recording
     */
    public void showJfrAnalysis(File jfr)
    {
        if (jfrAnalysisWindow == null)
        {
            jfrAnalysisWindow = new JfrAnalysisWindow(this);
        }
        jfrAnalysisWindow.load(jfr);
        jfrAnalysisWindow.setVisible(true);
        jfrAnalysisWindow.toFront();
    }

    /** Opens the scratch pad that compiles and runs Java inside the attached JVM, reusing one dialog. */
    public void showLiveScratchPad()
    {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached())
        {
            showWarning("Attach to a live JVM first (Attach -> Attach to Live JVM).");
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            showWarning("No project loaded.");
            return;
        }
        if (liveScratchPadDialog == null)
        {
            liveScratchPadDialog = new LiveScratchPadDialog(this);
        }
        liveScratchPadDialog.setProject(project);
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null && project.isUserClass(currentClass.getClassName()))
        {
            liveScratchPadDialog.setContextClass(currentClass.getClassName());
        }
        liveScratchPadDialog.setVisible(true);
        liveScratchPadDialog.toFront();
    }

    /** Disconnects the debugger and detaches from the live JVM, keeping the pulled classes for offline browsing. */
    public void detachLive()
    {
        DebugManager.getInstance().disconnect();
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached())
        {
            return;
        }
        svc.detach();
        LiveHeapService.get().clear();
        consolePanel.log("Detached from live JVM.");
    }

    private LiveScratchPadDialog liveScratchPadDialog;
    private LiveRecorderPanel liveRecorderPanel;
    private JfrAnalysisWindow jfrAnalysisWindow;
    private LiveCaptureService liveCaptureService;
    private LiveSession liveCaptureSession;
    private LiveThreadsPanel liveThreadsPanel;
    private LiveProfilerPanel liveProfilerPanel;
    private LiveValueScannerPanel liveValueScannerPanel;
    private DebuggerPanel debuggerPanel;

    /** @return the Value Scanner tool, or null where no live session has been attached */
    public LiveValueScannerPanel getValueScannerPanel()
    {
        return liveValueScannerPanel;
    }

    /**
     * Arms or disarms capture of classes as the attached JVM loads them; without a session it only warns.
     *
     * @param enabled true to arm capture
     */
    public void setLiveCaptureEnabled(boolean enabled)
    {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached())
        {
            if (enabled)
            {
                showWarning("Attach to a live JVM first (VM -> Attach to Live JVM).");
            }
            return;
        }
        LiveSession session = svc.getSession();
        if (liveCaptureService != null && liveCaptureSession != session)
        {
            liveCaptureService.dispose();
            liveCaptureService = null;
        }
        try
        {
            if (enabled)
            {
                if (liveCaptureService == null)
                {
                    liveCaptureService = new LiveCaptureService(session);
                    liveCaptureSession = session;
                }
                liveCaptureService.arm();
            }
            else if (liveCaptureService != null)
            {
                liveCaptureService.disarm();
            }
        }
        catch (Exception e)
        {
            showWarning("Live capture toggle failed: " + e.getMessage());
        }
    }

    /**
     * Tells whether class-load capture is armed.
     *
     * @return true while capture is armed
     */
    public boolean isLiveCaptureEnabled()
    {
        return liveCaptureService != null && liveCaptureService.isArmed();
    }

    /**
     * Opens a stack frame's method in source view, matching the method by name alone.
     *
     * @param internalName the declaring class's internal name
     * @param methodName the method's name
     */
    public void openLiveFrame(String internalName, String methodName)
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            return;
        }
        ClassEntryModel entry = project.getClass(internalName);
        if (entry == null)
        {
            consolePanel.log("Class not in project (not pulled from the target): " + internalName);
            return;
        }
        editorPanel.navigateToMethod(entry, methodName, "", ViewMode.SOURCE);
    }

    /** Snapshots the attached JVM's wait-for graph and reports any deadlock cycles. */
    public void findLiveDeadlocks()
    {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached())
        {
            showWarning("Attach to a live JVM first (VM -> Attach to Live JVM).");
            return;
        }
        LiveSession session = svc.getSession();
        new SwingWorker<String, Void>()
        {
            @Override
            protected String doInBackground()
            {
                try
                {
                    List<ContentionEdge> edges = session.getContention();
                    List<List<ContentionEdge>> cycles =
                            Deadlocks.find(edges);
                    if (cycles.isEmpty())
                    {
                        return "No deadlocks. " + edges.size() + " thread(s) currently blocked on a monitor.";
                    }
                    StringBuilder sb = new StringBuilder(cycles.size() + " deadlock(s) detected:\n");
                    int n = 1;
                    for (List<ContentionEdge> cycle : cycles)
                    {
                        sb.append("\nDeadlock ").append(n++).append(":\n");
                        for (ContentionEdge e : cycle)
                        {
                            sb.append("  ").append(e).append('\n');
                        }
                    }
                    return sb.toString();
                }
                catch (Exception e)
                {
                    return "Deadlock scan failed: " + e.getMessage();
                }
            }

            @Override
            protected void done()
            {
                try
                {
                    showInfo(get());
                }
                catch (Exception ignored)
                {
                }
            }
        }.execute();
    }

    /** Redefines the front class in the attached JVM from its current bytecode; the JVM rejects structural changes. */
    public void patchLiveClass()
    {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached())
        {
            showWarning("Attach to a live JVM first (VM -> Attach to Live JVM).");
            return;
        }
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null)
        {
            showWarning("No class selected to patch.");
            return;
        }
        final String internalName = currentClass.getClassName();
        final ClassFile edited = currentClass.getClassFile();
        final LiveSession session = svc.getSession();
        consolePanel.log("Patching live class " + internalName + "...");
        SwingWorkers.run(() ->
        {
            byte[] bytes = LivePatch.buildRedefineBytes(session, internalName, edited);
            session.redefineClass(internalName, bytes);
            return null;
        }, ignored -> consolePanel.log("Live patch applied to " + internalName + "."), err ->
        {
            consolePanel.log("Live patch failed: " + err.getMessage());
            showWarning("Live patch failed: " + err.getMessage() + "\n(Only method-body changes are supported; structural changes are rejected.)");
        });
    }

    /** Opens the VM console. */
    public void showVMConsole()
    {
        dialogManager.showVMConsole();
    }

    /** Opens the bytecode debugger. */
    public void showBytecodeDebugger()
    {
        dialogManager.showBytecodeDebugger();
    }

    /** Opens the Execute Method dialog for the method at the caret, or for choosing one. */
    public void showExecuteMethodDialog()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            showWarning("No project loaded. Load a project before executing methods.");
            return;
        }

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        ExecuteMethodDialog dialog;
        if (currentMethod != null)
        {
            dialog = new ExecuteMethodDialog(this, currentMethod);
            consolePanel.log("Execute Method: Opened for " + currentMethod.getMethodEntry().getName());
        }
        else
        {
            dialog = new ExecuteMethodDialog(this);
            consolePanel.log("Execute Method: Opened - select a method to execute");
        }
        dialog.setVisible(true);
        statusBar.setMessage("Execute Method dialog opened");
    }

    /**
     * Opens the Execute Method dialog for a method.
     *
     * @param method the method to execute
     */
    public void openExecuteMethodDialog(MethodEntryModel method)
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            showWarning("No project loaded. Load a project before executing methods.");
            return;
        }

        ExecuteMethodDialog dialog = new ExecuteMethodDialog(this, method);
        consolePanel.log("Execute Method: Opened for " + method.getMethodEntry().getName());
        dialog.setVisible(true);
        statusBar.setMessage("Execute Method dialog opened");
    }

    /** Initializes the bytecode VM over the loaded project. */
    public void initializeVM()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            showWarning("No project loaded. Load a project before initializing the VM.");
            return;
        }

        try
        {
            VMExecutionService.getInstance().initialize();
            consolePanel.log("VM initialized successfully with " + project.getClassCount() + " classes");
            statusBar.setMessage("VM initialized");
        }
        catch (Exception e)
        {
            showError("Failed to initialize VM: " + e.getMessage());
            consolePanel.logError("VM initialization failed: " + e.getMessage());
        }
    }

    /** Resets the bytecode VM. */
    public void resetVM()
    {
        if (!VMExecutionService.getInstance().isInitialized())
        {
            showInfo("VM is not initialized.");
            return;
        }

        try
        {
            VMExecutionService.getInstance().reset();
            consolePanel.log("VM reset successfully");
            statusBar.setMessage("VM reset");
        }
        catch (Exception e)
        {
            showError("Failed to reset VM: " + e.getMessage());
            consolePanel.logError("VM reset failed: " + e.getMessage());
        }
    }

    /** Shows the bytecode VM's status. */
    public void showVMStatus()
    {
        VMExecutionService vmService = VMExecutionService.getInstance();
        String status = vmService.getVMStatus();
        JOptionPane.showMessageDialog(this, status, "VM Status", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Opens the heap forensics view. */
    public void showHeapForensics()
    {
        dialogManager.showHeapForensics();
    }

    /**
     * Opens a class by internal or binary name.
     *
     * @param className the class's name, with slashes or dots
     * @return true where the class was found
     */
    public boolean navigateToClass(String className)
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return false;

        String normalizedName = className.replace('.', '/');
        ClassEntryModel classEntry = project.getClass(normalizedName);
        if (classEntry == null)
        {
            classEntry = project.findClassByName(className);
        }
        if (classEntry != null)
        {
            openClassInEditor(classEntry);
            return true;
        }
        return false;
    }

    /**
     * Opens a class and scrolls to a method.
     *
     * @param className the class's name, with slashes or dots
     * @param methodName the method's name
     * @param methodDesc the method's descriptor, or null to match by name
     * @return true where the method was found
     */
    public boolean navigateToMethod(String className, String methodName, String methodDesc)
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return false;

        String normalizedName = className.replace('.', '/');
        ClassEntryModel classEntry = project.getClass(normalizedName);
        if (classEntry == null)
        {
            classEntry = project.findClassByName(className);
        }
        if (classEntry != null)
        {
            return editorPanel.navigateToMethod(classEntry, methodName, methodDesc, currentViewMode);
        }
        return false;
    }

    /**
     * Switches to bytecode view, opens a class and highlights an instruction.
     *
     * @param className the class's name, with slashes or dots
     * @param methodName the method's name
     * @param methodDesc the method's descriptor
     * @param pc the bytecode offset
     * @return true where the instruction was found
     */
    public boolean navigateToPC(String className, String methodName, String methodDesc, int pc)
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return false;

        String normalizedName = className.replace('.', '/');
        ClassEntryModel classEntry = project.getClass(normalizedName);
        if (classEntry == null)
        {
            classEntry = project.findClassByName(className);
        }
        if (classEntry != null)
        {
            switchToBytecodeView();
            return editorPanel.navigateToPC(classEntry, methodName, methodDesc, pc);
        }
        return false;
    }

    /**
     * Navigates to a class, method or instruction from a query result.
     *
     * @param target the result's target
     * @return true where the target was found
     */
    public boolean navigateToTarget(QueryTarget target)
    {
        if (target instanceof QueryTarget.ClassTarget)
        {
            QueryTarget.ClassTarget ct =
                    (QueryTarget.ClassTarget) target;
            return navigateToClass(ct.className());
        }
        else if (target instanceof QueryTarget.MethodTarget)
        {
            QueryTarget.MethodTarget mt =
                    (QueryTarget.MethodTarget) target;
            return navigateToMethod(mt.className(), mt.methodName(), mt.descriptor());
        }
        else if (target instanceof QueryTarget.PCTarget)
        {
            QueryTarget.PCTarget pt =
                    (QueryTarget.PCTarget) target;
            return navigateToPC(pt.className(), pt.methodName(), pt.descriptor(), pt.pc());
        }
        return false;
    }
}
