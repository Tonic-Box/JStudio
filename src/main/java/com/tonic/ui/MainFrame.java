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
import com.tonic.ui.layout.LayoutController;
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

/**
 * Main application window for JStudio.
 */
public class MainFrame extends JFrame {

    // UI Components
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

    // Tool dialogs (lazily created, cached) are owned by the DialogManager collaborator.
    private DialogManager dialogManager;
    // File/project I/O is owned by the FileOperationsController collaborator.
    private FileOperationsController fileOps;
    private final UpdateManager updateManager;
    private QueryExplorerPanel queryExplorerPanel;
    @Getter
    private ToolWindowPane rightToolWindow;
    private ToolWindowMover toolWindowMover;

    // Bottom panel with tabbed results
    @Getter
    private BottomPanel sidePanel;

    // Split panes + collapse/divider math are owned by the LayoutController collaborator.
    private LayoutController layoutController;

    // Navigation history
    private final NavigationHistory navigationHistory = new NavigationHistory();

    // Current state
    private ViewMode currentViewMode = ViewMode.SOURCE;
    @Getter
    private boolean omitAnnotations = false;

    // Editor settings
    private int currentFontSize = 13;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 32;
    private static final int DEFAULT_FONT_SIZE = 13;
    private boolean wordWrapEnabled = false;

    public MainFrame() {
        super(JStudio.APP_NAME + " " + JStudio.APP_VERSION);
        initializeFrame();
        initializeComponents();
        initializeLayout();
        initializeEventHandlers();

        updateManager = new UpdateManager(this);
        SwingUtilities.invokeLater(updateManager::checkOnStartup);

        // Load GUI plugins once the window is fully constructed.
        SwingUtilities.invokeLater(() -> GuiPluginManager.getInstance().bootstrap(this));
    }

    /**
     * Manually checks for a newer release (Help menu), reporting the result either way.
     */
    public void checkForUpdates() {
        updateManager.checkNow();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 600));

        // Set window icon
        try {
            URL iconUrl = getClass().getResource("/com/tonic/ui/icon.png");
            if (iconUrl != null) {
                setIconImage(ImageIO.read(iconUrl));
            }
        } catch (Exception e) {
            // Ignore - use default icon
        }

        // Restore window bounds from settings
        Settings settings = Settings.getInstance();
        int x = settings.getWindowX();
        int y = settings.getWindowY();
        int width = settings.getWindowWidth();
        int height = settings.getWindowHeight();

        if (x >= 0 && y >= 0) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }
        setSize(width, height);

        if (settings.isWindowMaximized()) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        // Restore editor settings
        currentFontSize = settings.getFontSize();
        wordWrapEnabled = settings.isWordWrapEnabled();

        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        // Setup drag-and-drop support
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                fileOps.handleFileDrop(dtde);
            }
        });
    }

    private void initializeComponents() {
        // Create panels
        navigatorPanel = new NavigatorPanel(this);
        editorPanel = new EditorPanel(this);
        propertiesPanel = new PropertiesPanel();
        consolePanel = new ConsolePanel();
        statusBar = new StatusBar();

        // Right-edge tool windows: Inspector (default) over a vertical tab stripe, plus Query Explorer
        queryExplorerPanel = new QueryExplorerPanel(this);
        rightToolWindow = new ToolWindowPane();
        rightToolWindow.addTool("Inspector", propertiesPanel);
        rightToolWindow.addTool("Query", queryExplorerPanel);
        toolWindowMover = new ToolWindowMover(rightToolWindow, editorPanel, this);
        rightToolWindow.setMoveListener(toolWindowMover::move);

        // The live "Threads" tool is only present while attached to a running JVM.
        EventBus.getInstance().register(LiveSessionEvent.class, e -> {
            if (e.isAttached()) {
                if (liveThreadsPanel == null) {
                    liveThreadsPanel = new LiveThreadsPanel(this);
                }
                rightToolWindow.addTool("Threads", liveThreadsPanel);
                if (liveProfilerPanel == null) {
                    liveProfilerPanel = new LiveProfilerPanel();
                }
                rightToolWindow.addTool("Profiler", liveProfilerPanel);
                if (liveValueScannerPanel == null) {
                    liveValueScannerPanel = new LiveValueScannerPanel(this);
                }
                rightToolWindow.addTool("Value Scanner", liveValueScannerPanel);
                LiveSession session = LiveAttachService.getInstance().getSession();
                if (session != null && session.supportsJfr()) {
                    if (liveRecorderPanel == null) {
                        liveRecorderPanel = new LiveRecorderPanel(this);
                    }
                    rightToolWindow.addTool("Recorder", liveRecorderPanel);
                }
            } else {
                // A live tool may have been moved to a tab/window; tear that float down (the session is gone) before
                // the dock removal, which is then a clean no-op.
                for (String tool : new String[]{"Threads", "Profiler", "Recorder", "Value Scanner"}) {
                    toolWindowMover.closeFloat(tool);
                    rightToolWindow.removeTool(tool);
                }
            }
        });

        // The JDI "Debugger" tool is present only while a debug session is connected.
        EventBus.getInstance().register(DebugSessionEvent.class, e -> {
            if (e.isConnected()) {
                if (debuggerPanel == null) {
                    debuggerPanel = new DebuggerPanel(this);
                }
                rightToolWindow.addTool("Debugger", debuggerPanel);
            } else {
                toolWindowMover.closeFloat("Debugger");
                rightToolWindow.removeTool("Debugger");
            }
            // Re-arm breakpoint gutters on already-open tabs (they were opened before the session existed).
            editorPanel.refreshBreakpointGutters();
        });

        // On a breakpoint hit: navigate to the current line and focus the Debugger tool.
        EventBus.getInstance().register(DebugPausedEvent.class, e -> {
            navigateToDebugLocation(e.getLocation());
            rightToolWindow.select("Debugger");
        });

        // "Scan for this value" from the live heap/statics views: focus the scanner tool and seed its scan bar.
        EventBus.getInstance().register(ScanSeedEvent.class, e -> {
            if (liveValueScannerPanel == null) {
                return;
            }
            rightToolWindow.select("Value Scanner");
            liveValueScannerPanel.seed(e.getValueType(), e.getValue(), e.getPackageFilter());
        });

        // Bottom panel with tabbed results (Find Usages, Console, Bookmarks, Comments)
        sidePanel = new BottomPanel();
        sidePanel.setEditorPanel(editorPanel);
        sidePanel.setConsolePanel(consolePanel);
        sidePanel.setOnAllTabsClosed(() -> layoutController.collapseBottom());
        sidePanel.setOnTabOpened(() -> layoutController.expandBottom());
        sidePanel.setCollapseHost(new BottomPanel.CollapseHost() {
            @Override
            public boolean isCollapsed() {
                return layoutController.isBottomCollapsed();
            }

            @Override
            public void collapse() {
                layoutController.collapseBottom();
            }

            @Override
            public void expand() {
                layoutController.expandBottom();
            }
        });

        // Bottom toolbar (always visible, outside split pane)
        BottomToolbar bottomToolbar = new BottomToolbar();
        bottomToolbar.setOnConsoleClicked(() -> sidePanel.toggleConsoleTab());
        bottomToolbar.setOnBookmarksClicked(() -> {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            sidePanel.setProject(project);
            sidePanel.toggleBookmarksTab();
        });
        bottomToolbar.setOnCommentsClicked(() -> {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            sidePanel.setProject(project);
            sidePanel.toggleCommentsTab();
        });
        bottomToolbar.setOnLocalHistoryClicked(() -> sidePanel.toggleLocalHistoryTab());

        dialogManager = new DialogManager(this, editorPanel);
        fileOps = new FileOperationsController(this);
        layoutController = new LayoutController(editorPanel, sidePanel, navigatorPanel, rightToolWindow, bottomToolbar);
    }

    private void initializeLayout() {
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBackground(JStudioTheme.getBgPrimary());

        // Menu bar
        MenuBarBuilder menuBarBuilder = new MenuBarBuilder(this);
        setJMenuBar(menuBarBuilder.build());

        // Toolbar
        toolbarBuilder = new ToolbarBuilder(this);
        contentPane.add(toolbarBuilder.build(), BorderLayout.NORTH);

        // Main content area with split panes (navigator | (editor-over-bottom-dock | right tool window))
        contentPane.add(layoutController.buildCenter(), BorderLayout.CENTER);
        contentPane.add(statusBar, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private void initializeEventHandlers() {
        // Handle class selection from navigator
        EventBus.getInstance().register(ClassSelectedEvent.class, event -> {
            ClassEntryModel classEntry = event.getClassEntry();
            if (classEntry != null) {
                openClassInEditor(classEntry);
                if (event.hasScrollTarget()) {
                    SwingUtilities.invokeLater(() -> {
                        if (event.getHighlightLine() > 0) {
                            editorPanel.goToLineAndHighlight(event.getHighlightLine());
                        }
                    });
                }
            }
        });

        // Handle method selection from navigator - scroll to method after class is opened
        EventBus.getInstance().register(MethodSelectedEvent.class, event -> {
            MethodEntryModel method = event.getMethodEntry();
            if (method != null) {
                SwingUtilities.invokeLater(() -> editorPanel.scrollToMethod(method));
            }
        });

        // Handle project loaded
        EventBus.getInstance().register(ProjectLoadedEvent.class, event -> {
            ProjectModel project = event.getProject();
            editorPanel.closeAllTabs();
            sidePanel.closeAllTabs();
            navigatorPanel.loadProject(project);
            editorPanel.setProjectModel(project);
            editorPanel.refreshWelcomeTab();
            ProjectDatabaseService.getInstance().initializeForProject(project);
            if (LocalHistoryService.getInstance().attach(project)) {
                Snapshot saved = LocalHistoryService.getInstance().newest();
                if (saved != null && LocalHistoryService.getInstance().restore(saved)) {
                    refreshAfterProjectChange();
                }
            }
            fileOps.updateTitleBar();
        });

        // AI assistant wrote a script: refresh the Script Editor's list and open to it.
        EventBus.getInstance().register(ScriptWrittenEvent.class, event ->
            SwingUtilities.invokeLater(() -> {
                showScriptEditor();
                ScriptEditorDialog dialog = dialogManager.getScriptEditorDialog();
                if (dialog != null) {
                    dialog.getEditorPanel().selectScriptByName(event.getScriptName());
                }
            }));

        // AI assistant ran a script: stream its console output into the bottom Script Console tab.
        EventBus.getInstance().register(ScriptConsoleEvent.class, event ->
            SwingUtilities.invokeLater(() -> sidePanel.openScriptConsole().handle(event)));

        // AI assistant renamed a class/method/field: refresh the navigator + open editors to the new names.
        EventBus.getInstance().register(ProjectRenamedEvent.class, event ->
            SwingUtilities.invokeLater(() -> {
                if (event.getKind() == ProjectRenamedEvent.Kind.CLASS) {
                    refreshAfterRename(event.getOldClass(), event.getNewClass());
                } else {
                    refreshAfterProjectChange();
                }
            }));

        // Handle project updated (classes appended)
        EventBus.getInstance().register(ProjectUpdatedEvent.class, event -> {
            ProjectModel project = event.getProject();
            if (project != null) {
                navigatorPanel.loadProject(project);
                editorPanel.setProjectModel(project);
                editorPanel.refreshWelcomeTab();
            }
        });

        // Handle resource selection from navigator
        EventBus.getInstance().register(ResourceSelectedEvent.class, event -> {
            if (event.getResource() != null) {
                editorPanel.openResource(event.getResource());
            }
        });

        // Handle Find Usages requests
        EventBus.getInstance().register(FindUsagesEvent.class, event -> {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            if (project != null) {
                sidePanel.setProject(project);
                sidePanel.openFindUsagesTab(event);
            }
        });
    }

    // === File Operations ===

    public void showOpenDialog() {
        fileOps.showOpenDialog();
    }

    public void openFile(String path) {
        fileOps.openFile(path);
    }

    public void exportCurrentClass() {
        fileOps.exportCurrentClass();
    }

    public void exportClass(ClassEntryModel classEntry) {
        fileOps.exportClass(classEntry);
    }

    public void exportAllClasses() {
        fileOps.exportAllClasses();
    }

    public void exportAsJar() {
        fileOps.exportAsJar();
    }

    /**
     * Launches the given class's {@code main} in a separate JVM (so its System.exit/crash can't affect JStudio),
     * streaming output into the Run panel. Unavailable while attached to a live JVM.
     */
    public void runMainClass(ClassEntryModel classEntry) {
        if (LiveAttachService.getInstance().isAttached()) {
            showWarning("Run is unavailable while attached to a live JVM.");
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || classEntry == null || !classEntry.hasMainMethod()) {
            return;
        }
        File defaultDir = project.getSourceFile() != null ? project.getSourceFile().getParentFile() : null;
        RunConfigDialog.RunConfig config =
                RunConfigDialog.show(this, classEntry.getSimpleName(), defaultDir);
        if (config == null) {
            return;
        }
        String internalName = classEntry.getClassName();
        RunConsolePanel panel = sidePanel.openRunConsole();
        Runnable launch = () -> launchWithLiveDebug(project, internalName, config, panel);
        panel.setRerunAction(launch);
        launch.run();
    }

    /**
     * Launches the run, loading the JStudio agent (premain) and auto-attaching a live session to the child JVM
     * so the live features apply to the running app. Degrades gracefully if the agent jar is unavailable.
     */
    private void launchWithLiveDebug(ProjectModel project, String internalName,
                                     RunConfigDialog.RunConfig config,
                                     RunConsolePanel panel) {
        List<String> vmOptions = new ArrayList<>(config.vmOptions);
        int port = -1;
        int jdwpPort = -1;
        File agentJar = LiveAttachService.getInstance().resolveAgentJar();
        if (agentJar == null) {
            consolePanel.log("Live debugging unavailable (agent jar not found); running without it.");
        } else if (config.javaFeature > 0 && config.javaFeature < 11) {
            consolePanel.log("Live debugging needs Java 11+ on the selected JDK; running without it.");
        } else {
            try (ServerSocket probe = new ServerSocket(0)) {
                port = probe.getLocalPort();
            } catch (IOException ignored) {
            }
            if (port > 0) {
                vmOptions.add(0, "-javaagent:" + agentJar.getAbsolutePath() + "=port=" + port);
            }
            try (ServerSocket probe = new ServerSocket(0)) {
                jdwpPort = probe.getLocalPort();
            } catch (IOException ignored) {
            }
            if (jdwpPort > 0) {
                // Suspend at VMStart only when breakpoints are pre-set, so they can catch app startup; the
                // debugger installs them on connect and resumes. No breakpoints -> run immediately.
                String suspend = BreakpointService.getInstance().all().isEmpty() ? "n" : "y";
                vmOptions.add(0, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend
                        + ",address=127.0.0.1:" + jdwpPort);
            }
        }

        Process process = RunService.run(
                project, internalName, config.programArgs, vmOptions, config.workingDir, config.javaHome, panel);
        panel.setProcess(process);
        if (process != null) {
            RunStateService.getInstance().setProcess(process);
            process.onExit().thenAccept(p -> RunStateService.getInstance().clearIf(process));
        }
        if (process == null) {
            return;
        }

        String pid = String.valueOf(process.pid());
        if (port > 0) {
            int agentPort = port;
            SwingWorkers.run(
                    () -> LiveSession.connect(pid, agentPort),
                    session -> {
                        LiveAttachService.getInstance().adoptRunSession(session);
                        // Default runtime class-load capture ON for a Run auto-attach (the running app is the project).
                        setLiveCaptureEnabled(true);
                    },
                    err -> consolePanel.log("Live debugging not attached: " + err.getMessage()));
        }
        if (jdwpPort > 0) {
            int debugPort = jdwpPort;
            SwingWorkers.run(
                    () -> {
                        DebugManager.getInstance().connectWithRetry("127.0.0.1", debugPort);
                        return Boolean.TRUE;
                    },
                    ok -> consolePanel.log("Debugger attached (JDI)."),
                    err -> consolePanel.log("Debugger not attached: " + err.getMessage()));
        }
        process.onExit().thenAccept(p -> SwingUtilities.invokeLater(this::onRunProcessExited));
    }

    /** When a launched run process exits, drop its auto-attached live session (if it is still the active one). */
    private void onRunProcessExited() {
        DebugManager.getInstance().disconnect();
        LiveAttachService svc = LiveAttachService.getInstance();
        if (svc.isRunSession()) {
            detachLive();
        }
    }

    public void closeProject() {
        fileOps.closeProject();
    }

    public void openProjectFile() {
        fileOps.openProjectFile();
    }

    public void saveProject() {
        fileOps.saveProject();
    }

    public void saveProjectAs() {
        fileOps.saveProjectAs();
    }

    public void exitApplication() {
        if (!fileOps.confirmCloseIfDirty()) {
            return;
        }

        // Save settings before exit
        saveSettings();

        GuiPluginManager.getInstance().shutdown();
        statusBar.dispose();
        toolbarBuilder.dispose();
        queryExplorerPanel.shutdown();
        if (toolWindowMover != null) {
            toolWindowMover.disposeAll();
        }
        dispose();
        System.exit(0);
    }

    private void saveSettings() {
        Settings settings = Settings.getInstance();

        // Save window bounds
        boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        settings.setWindowMaximized(maximized);

        if (!maximized) {
            settings.saveWindowBounds(getX(), getY(), getWidth(), getHeight(), false);
        }

        // Save divider positions (console is no longer a separate split; preserve its stored height)
        layoutController.saveDividers(settings);

        // Save editor settings
        settings.setFontSize(currentFontSize);
        settings.setWordWrapEnabled(wordWrapEnabled);

        // Save last project
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null && project.getSourceFile() != null) {
            settings.setLastProject(project.getSourceFile().getAbsolutePath());
        }
    }

    // === Editor Operations ===

    public void openClassInEditor(ClassEntryModel classEntry) {
        editorPanel.openClass(classEntry, currentViewMode);
        navigationHistory.push(classEntry);
        statusBar.setPosition(classEntry.getClassName());
    }

    public void navigateBack() {
        ClassEntryModel entry = navigationHistory.back();
        if (entry != null) {
            editorPanel.openClass(entry, currentViewMode);
        }
    }

    public void navigateForward() {
        ClassEntryModel entry = navigationHistory.forward();
        if (entry != null) {
            editorPanel.openClass(entry, currentViewMode);
        }
    }

    /** Resets the back/forward navigation history (used by the file controller when the workspace changes). */
    public void clearNavigationHistory() {
        navigationHistory.clear();
    }

    /** Disposes the cached analysis dialog (used by the file controller when the project is replaced/closed). */
    public void disposeAnalysisDialog() {
        dialogManager.disposeAnalysisDialog();
    }

    // === View Operations ===

    public void switchToView(ViewMode mode) {
        currentViewMode = mode;
        editorPanel.setViewMode(mode);
        statusBar.setMode(mode.getDisplayName());
        toolbarBuilder.setViewMode(mode);
    }

    public void switchToSourceView() {
        switchToView(ViewMode.SOURCE);
    }

    public void switchToBytecodeView() {
        switchToView(ViewMode.BYTECODE);
    }

    public void switchToIRView() {
        switchToView(ViewMode.IR);
    }

    public void switchToHexView() {
        switchToView(ViewMode.HEX);
    }

    public void setOmitAnnotations(boolean omit) {
        this.omitAnnotations = omit;
        editorPanel.setOmitAnnotations(omit);
    }

    public void toggleNavigatorPanel() {
        layoutController.toggleNavigatorPanel();
    }

    public void togglePropertiesPanel() {
        layoutController.togglePropertiesPanel();
    }

    /** Opens or closes the Console tab in the bottom panel (View menu / keyboard shortcut). */
    public void toggleConsolePanel() {
        sidePanel.toggleConsoleTab();
    }

    public void refreshCurrentView() {
        editorPanel.refreshCurrentTab();
    }

    public void refreshAfterRename(String oldClassName, String newClassName) {
        editorPanel.closeTabForClass(oldClassName);
        refreshAfterProjectChange();
        statusBar.setMessage("Renamed: " + oldClassName.replace('/', '.') +
                " -> " + newClassName.replace('/', '.'));
    }

    /**
     * Full UI refresh after the project's bytecode was mutated (rename, script transform, ...): drops every class's
     * decompilation cache and reloads all open editor tabs from current bytecode, so no view keeps showing stale
     * source, then rebuilds the navigator. Use this for any change that can affect references across classes.
     */
    public void refreshAfterProjectChange() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            project.invalidateAllDecompilationCaches();
        }
        editorPanel.reloadAllTabs();
        navigatorPanel.refresh();
        navigatorPanel.setLoading(false);
        editorPanel.refreshWelcomeTab();
    }

    /**
     * User-invoked Refresh (toolbar / menu / Ctrl+F5): the FULL refresh - drop every class's decompilation cache and
     * force-re-decompile all open tabs from current bytecode, identical to what happens after the AI rename tools (or
     * any project mutation). Use this instead of {@link #refreshCurrentView()} when stale decompiled output needs to
     * be regenerated.
     */
    public void fullRefresh() {
        refreshAfterProjectChange();
        statusBar.setMessage("Refreshed - re-decompiled all classes from current bytecode");
    }

    public void closeEditorForClass(String className) {
        editorPanel.closeTabForClass(className);
    }

    public void closeEditorForResource(String path) {
        editorPanel.closeTabForResource(path);
    }

    public void refreshAfterBulkRename(Set<String> oldClassNames, int totalRenamed) {
        for (String oldName : oldClassNames) {
            editorPanel.closeTabForClass(oldName);
        }

        navigatorPanel.refresh();
        navigatorPanel.setLoading(false);
        editorPanel.refreshWelcomeTab();

        statusBar.setMessage("Deobfuscation complete: " + totalRenamed + " items renamed");
    }

    public void setNavigatorLoading(boolean loading) {
        navigatorPanel.setLoading(loading);
    }

    // === Font Size Operations ===

    public void increaseFontSize() {
        if (currentFontSize < MAX_FONT_SIZE) {
            currentFontSize += 2;
            editorPanel.setFontSize(currentFontSize);
            statusBar.setMessage("Font size: " + currentFontSize);
        }
    }

    public void decreaseFontSize() {
        if (currentFontSize > MIN_FONT_SIZE) {
            currentFontSize -= 2;
            editorPanel.setFontSize(currentFontSize);
            statusBar.setMessage("Font size: " + currentFontSize);
        }
    }

    public void resetFontSize() {
        currentFontSize = DEFAULT_FONT_SIZE;
        editorPanel.setFontSize(currentFontSize);
        statusBar.setMessage("Font size reset to " + currentFontSize);
    }

    public void toggleWordWrap(boolean enabled) {
        wordWrapEnabled = enabled;
        editorPanel.setWordWrap(enabled);
        statusBar.setMessage("Word wrap " + (enabled ? "enabled" : "disabled"));
    }

    public void toggleUsageLens(boolean enabled) {
        Settings.getInstance().setUsageLensEnabled(enabled);
        editorPanel.setUsageLensEnabled(enabled);
        statusBar.setMessage("Usage counts " + (enabled ? "enabled" : "disabled"));
    }

    // === Edit Operations ===

    public void copySelection() {
        editorPanel.copySelection();
    }

    public void showFindDialog() {
        editorPanel.showFindDialog();
    }

    public void showFindInProjectDialog() {
        dialogManager.showFindInProjectDialog();
    }

    public void showGoToClassDialog() {
        navigatorPanel.focusSearchField();
    }

    public void showGoToLineDialog() {
        editorPanel.showGoToLineDialog();
    }

    // === Bookmarks & Comments ===

    public void addBookmarkAtCurrentLocation() {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected. Open a class first to add a bookmark.");
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        Bookmark bookmark = new Bookmark(currentClass.getClassName(), name.trim());
        ProjectDatabaseService.getInstance().addBookmark(bookmark);
        consolePanel.log("Added bookmark: " + name.trim() + " -> " + currentClass.getSimpleName());
    }

    public void addCommentAtCurrentLocation() {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected. Open a class first to add a comment.");
            return;
        }

        JTextArea textArea = new JTextArea(5, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(this, scrollPane,
                "Add Comment for " + currentClass.getSimpleName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !textArea.getText().trim().isEmpty()) {
            Comment comment = new Comment(currentClass.getClassName(), -1, textArea.getText().trim());
            comment.setType(Comment.Type.CLASS);
            ProjectDatabaseService.getInstance().addComment(comment);
            consolePanel.log("Added comment to " + currentClass.getSimpleName());
        }
    }

    /** Opens (or toggles) the Local History bottom tab. */
    public void showLocalHistoryPanel() {
        sidePanel.toggleLocalHistoryTab();
    }

    /** Creates a manual history checkpoint of the current project state. */
    public void createHistoryCheckpoint() {
        Snapshot created = LocalHistoryService.getInstance().snapshot(Snapshot.Trigger.MANUAL.getDefaultLabel(),
                Snapshot.Trigger.MANUAL);
        if (created == null && LocalHistoryService.getInstance().isEnabled()) {
            statusBar.setMessage("No changes since the last snapshot");
        } else if (created != null) {
            statusBar.setMessage("Checkpoint created");
        }
    }

    public void showBookmarksPanel() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            sidePanel.setProject(project);
            sidePanel.toggleBookmarksTab();
        }
    }

    public void showCommentsPanel() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            sidePanel.setProject(project);
            sidePanel.toggleCommentsTab();
        }
    }

    // === Analysis Operations ===

    public void runAnalysis() {
        dialogManager.runAnalysis();
    }

    public void showSimilarityAnalysis() {
        dialogManager.showSimilarityAnalysis();
    }

    public void showSearchAnalysis() {
        dialogManager.showSearchAnalysis();
    }

    public void showStringsAnalysis() {
        dialogManager.showStringsAnalysis();
    }

    // === Transform Operations ===

    /** Opens (reusing one instance) the Remove Dead Code analysis dialog. */
    public void showRemoveDeadCodeDialog() {
        dialogManager.showRemoveDeadCodeDialog();
    }

    /** After dead-code removal: close tabs of removed classes and reload the navigator/editor. */
    public void refreshAfterDeadCodeRemoval(Collection<String> removedClassesInternal) {
        dialogManager.refreshAfterDeadCodeRemoval(removedClassesInternal);
    }

    public void showTransformDialog() {
        dialogManager.showTransformDialog();
    }

    /**
     * Shows the script editor dialog.
     */
    public void showScriptEditor() {
        dialogManager.showScriptEditor();
    }

    public void showDeobfuscationPanel() {
        dialogManager.showDeobfuscationPanel();
    }

    public void showDeobfuscateNamesDialog() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project first.");
            return;
        }

        DeobfuscateNamesDialog dialog = new DeobfuscateNamesDialog(this);
        dialog.setVisible(true);
    }

    public void showRenameClassDialog(ClassEntryModel classEntry) {
        if (classEntry == null) {
            showWarning("No class selected.");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            showWarning("No project loaded.");
            return;
        }

        String oldName = classEntry.getClassName();
        RenameClassDialog dialog = new RenameClassDialog(this, oldName);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        String newName = dialog.getNewClassName();
        if (newName.equals(oldName)) {
            return;
        }

        setNavigatorLoading(true);
        ClassPool classPool = project.getClassPool();

        SwingUtilities.invokeLater(() -> {
            try {
                LocalHistoryService.getInstance().snapshot("Rename class " + classEntry.getSimpleName(),
                        Snapshot.Trigger.RENAME);
                Renamer renamer = new Renamer(classPool);
                renamer.mapClass(oldName, newName).apply();
                project.notifyClassRenamed(oldName, newName);
                refreshAfterRename(oldName, newName);
                consolePanel.log("Renamed class: " + oldName.replace('/', '.') + " -> " + newName.replace('/', '.'));
            } catch (RenameException e) {
                setNavigatorLoading(false);
                showError("Rename failed: " + e.getMessage());
                consolePanel.logError("Rename failed: " + e.getMessage());
            }
        });
    }

    public void showRenameMethodDialog(ClassEntryModel classEntry, MethodEntryModel method) {
        if (classEntry == null || method == null) {
            showWarning("No method selected.");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            showWarning("No project loaded.");
            return;
        }

        String className = classEntry.getClassName();
        String oldName = method.getName();
        String desc = method.getMethodEntry().getDesc();

        RenameMethodDialog dialog = new RenameMethodDialog(this, oldName, desc);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        String newName = dialog.getNewMethodName();
        if (newName.equals(oldName)) {
            return;
        }

        setNavigatorLoading(true);
        ClassPool classPool = project.getClassPool();

        SwingUtilities.invokeLater(() -> {
            try {
                LocalHistoryService.getInstance().snapshot("Rename method " + oldName, Snapshot.Trigger.RENAME);
                Renamer renamer = new Renamer(classPool);
                renamer.mapMethod(className, oldName, desc, newName).apply();
                refreshAfterProjectChange();
                consolePanel.log("Renamed method: " + oldName + " -> " + newName + " in " + classEntry.getSimpleName());
                statusBar.setMessage("Renamed method: " + oldName + " -> " + newName);
            } catch (RenameException e) {
                setNavigatorLoading(false);
                showError("Rename failed: " + e.getMessage());
                consolePanel.logError("Rename failed: " + e.getMessage());
            }
        });
    }

    public void showRenameFieldDialog(ClassEntryModel classEntry, FieldEntryModel field) {
        if (classEntry == null || field == null) {
            showWarning("No field selected.");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            showWarning("No project loaded.");
            return;
        }

        String className = classEntry.getClassName();
        String oldName = field.getName();
        String desc = field.getFieldEntry().getDesc();

        RenameFieldDialog dialog = new RenameFieldDialog(this, oldName, desc);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        String newName = dialog.getNewFieldName();
        if (newName.equals(oldName)) {
            return;
        }

        setNavigatorLoading(true);
        ClassPool classPool = project.getClassPool();

        SwingUtilities.invokeLater(() -> {
            try {
                LocalHistoryService.getInstance().snapshot("Rename field " + oldName, Snapshot.Trigger.RENAME);
                Renamer renamer = new Renamer(classPool);
                renamer.mapField(className, oldName, desc, newName).apply();
                refreshAfterProjectChange();
                consolePanel.log("Renamed field: " + oldName + " -> " + newName + " in " + classEntry.getSimpleName());
                statusBar.setMessage("Renamed field: " + oldName + " -> " + newName);
            } catch (RenameException e) {
                setNavigatorLoading(false);
                showError("Rename failed: " + e.getMessage());
                consolePanel.logError("Rename failed: " + e.getMessage());
            }
        });
    }

    public void applyTransform(String transformName) {
        dialogManager.applyTransform(transformName);
    }

    public void recomputeStackFrames() {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected.");
            return;
        }

        // Note: Stack frame computation is typically done automatically by the SSA transform system
        // This method provides a manual trigger to rebuild the class file
        statusBar.showProgress("Rebuilding class...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                ClassFile cf = currentClass.getClassFile();
                // Rebuild the class file (this recalculates any internal structures)
                cf.write();
                return null;
            }

            @Override
            protected void done() {
                statusBar.hideProgress();
                try {
                    get();
                    consolePanel.log("Rebuilt class " + currentClass.getClassName());
                    // Refresh the current view
                    editorPanel.refreshCurrentTab();
                } catch (Exception e) {
                    showError("Rebuild failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    // === Help Operations ===

    public void showKeyboardShortcuts() {
        editorPanel.openCustomView("keyboard-shortcuts", "Keyboard Shortcuts",
                Icons.getIcon("info"), new KeyboardShortcutsView());
    }

    public void showAboutDialog() {
        String message = JStudio.APP_NAME + " " + JStudio.APP_VERSION + "\n\n" +
                "A professional Java reverse engineering and analysis suite.";

        JOptionPane.showMessageDialog(this, message, "About " + JStudio.APP_NAME,
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void showPreferencesDialog() {
        dialogManager.showPreferencesDialog();
    }

    /** Re-reads the font size from settings and applies it to the editor (Preferences "Apply" callback). */
    public void applyFontSizeFromSettings() {
        currentFontSize = Settings.getInstance().getFontSize();
        editorPanel.setFontSize(currentFontSize);
    }

    // === Utility Methods ===

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Information",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning",
                JOptionPane.WARNING_MESSAGE);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    // === Getters ===

    /** The analysis tool panel, or {@code null} if the analysis dialog has not been opened yet. */
    public AnalysisPanel getAnalysisPanel() {
        return dialogManager.getAnalysisPanel();
    }

    /**
     * Run simulation analysis on the current method or class.
     */
    public void runCodeAnalysis() {
        dialogManager.runCodeAnalysis();
    }

    // === VM Operations ===

    /**
     * Opens the "Attach to Live JVM" dialog. Attaching loads the Java agent into the chosen
     * process and replaces the current project with one built from that JVM's loaded classes.
     */
    public void showLiveAttachDialog() {
        new LiveAttachDialog(this).setVisible(true);
    }

    /**
     * Opens the class for a JDI debug location and scrolls/highlights its source line, reusing the same
     * offset-to-source navigation that Find Usages uses. Called on a breakpoint hit and on a call-stack click.
     */
    public void navigateToDebugLocation(DebugLocation loc) {
        if (loc == null) {
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        ClassEntryModel ce = project.getClass(loc.getClassName().replace('.', '/'));
        if (ce == null) {
            ce = project.findClassByName(loc.getClassName());
        }
        if (ce == null) {
            return;
        }
        ClassEntryModel target = ce;
        SwingUtilities.invokeLater(() -> editorPanel.navigateToSourceOffset(
                target, loc.getMethodName(), loc.getMethodDescriptor(), (int) loc.getCodeIndex(), null));
    }

    /**
     * External opt-in for the JDI debugger: late-loads the JDWP agent into the currently attached JVM and
     * connects the debugger. Degrades gracefully (a console message) if the target blocks agent loading.
     */
    public void enableDebuggerOnAttached() {
        if (DebugManager.getInstance().isConnected()) {
            return;
        }
        LiveSession s = LiveAttachService.getInstance().getSession();
        if (s == null) {
            showWarning("Attach to a live JVM first (Attach -> Attach to Live JVM).");
            return;
        }
        String pid = s.getPid();
        int dp = -1;
        try (ServerSocket probe = new ServerSocket(0)) {
            dp = probe.getLocalPort();
        } catch (IOException ignored) {
        }
        if (dp <= 0) {
            showWarning("Could not allocate a debug port.");
            return;
        }
        int debugPort = dp;
        consolePanel.log("Enabling debugger (JDI) on pid " + pid + "...");
        SwingWorkers.run(
                () -> {
                    DebugManager.getInstance().connectExternal(pid, debugPort);
                    return Boolean.TRUE;
                },
                ok -> consolePanel.log("Debugger attached (JDI) to pid " + pid + "."),
                err -> consolePanel.log("Debugger unavailable on this JVM: " + err.getMessage()));
    }

    /** Opens (reusing one window) the JFR analysis view for a captured {@code .jfr} recording. */
    public void showJfrAnalysis(File jfr) {
        if (jfrAnalysisWindow == null) {
            jfrAnalysisWindow = new JfrAnalysisWindow(this);
        }
        jfrAnalysisWindow.load(jfr);
        jfrAnalysisWindow.setVisible(true);
        jfrAnalysisWindow.toFront();
    }

    /**
     * Opens the live Java scratch pad: compile-and-run arbitrary Java inside the attached JVM. Requires an
     * active attach session and a loaded project (the target's pulled classes). Holds one non-modal dialog.
     */
    public void showLiveScratchPad() {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached()) {
            showWarning("Attach to a live JVM first (Attach -> Attach to Live JVM).");
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }
        if (liveScratchPadDialog == null) {
            liveScratchPadDialog = new LiveScratchPadDialog(this);
        }
        liveScratchPadDialog.setProject(project);
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null && project.isUserClass(currentClass.getClassName())) {
            liveScratchPadDialog.setContextClass(currentClass.getClassName());
        }
        liveScratchPadDialog.setVisible(true);
        liveScratchPadDialog.toFront();
    }

    /** Detaches from the live JVM (closes the session); the pulled classes remain for offline browsing. */
    public void detachLive() {
        DebugManager.getInstance().disconnect();
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached()) {
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

    /** The live Value Scanner tool (present only while attached); null when not attached. */
    public LiveValueScannerPanel getValueScannerPanel() {
        return liveValueScannerPanel;
    }

    /**
     * Arms or disarms runtime class-load capture on the active live session. Captured classes (packers,
     * defineHiddenClass, ASM glue) stream into the project as they load. No-op without an attach session.
     */
    public void setLiveCaptureEnabled(boolean enabled) {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached()) {
            if (enabled) {
                showWarning("Attach to a live JVM first (VM -> Attach to Live JVM).");
            }
            return;
        }
        LiveSession session = svc.getSession();
        if (liveCaptureService != null && liveCaptureSession != session) {
            liveCaptureService.dispose();
            liveCaptureService = null;
        }
        try {
            if (enabled) {
                if (liveCaptureService == null) {
                    liveCaptureService = new LiveCaptureService(session);
                    liveCaptureSession = session;
                }
                liveCaptureService.arm();
            } else if (liveCaptureService != null) {
                liveCaptureService.disarm();
            }
        } catch (Exception e) {
            showWarning("Live capture toggle failed: " + e.getMessage());
        }
    }

    /** Whether runtime class-load capture is currently armed on the active session. */
    public boolean isLiveCaptureEnabled() {
        return liveCaptureService != null && liveCaptureService.isArmed();
    }

    /**
     * Opens the decompiled source for a thread-stack frame: resolves the declaring class in the current
     * project and navigates to the method. Navigation is method-level (a stack frame carries no descriptor).
     */
    public void openLiveFrame(String internalName, String methodName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        ClassEntryModel entry = project.getClass(internalName);
        if (entry == null) {
            consolePanel.log("Class not in project (not pulled from the target): " + internalName);
            return;
        }
        editorPanel.navigateToMethod(entry, methodName, "", ViewMode.SOURCE);
    }

    /** Snapshots the attached JVM's wait-for graph and reports any deadlock cycles. */
    public void findLiveDeadlocks() {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached()) {
            showWarning("Attach to a live JVM first (VM -> Attach to Live JVM).");
            return;
        }
        LiveSession session = svc.getSession();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    List<ContentionEdge> edges = session.getContention();
                    List<List<ContentionEdge>> cycles =
                            Deadlocks.find(edges);
                    if (cycles.isEmpty()) {
                        return "No deadlocks. " + edges.size() + " thread(s) currently blocked on a monitor.";
                    }
                    StringBuilder sb = new StringBuilder(cycles.size() + " deadlock(s) detected:\n");
                    int n = 1;
                    for (List<ContentionEdge> cycle : cycles) {
                        sb.append("\nDeadlock ").append(n++).append(":\n");
                        for (ContentionEdge e : cycle) {
                            sb.append("  ").append(e).append('\n');
                        }
                    }
                    return sb.toString();
                } catch (Exception e) {
                    return "Deadlock scan failed: " + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    showInfo(get());
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    /**
     * Pushes the currently-open class's bytecode to the attached JVM via live redefinition ("patch &
     * continue"). Method-body-only changes apply immediately; structural changes (add/remove fields or
     * methods, hierarchy) are rejected by the JVM and surfaced as an error.
     */
    public void patchLiveClass() {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached()) {
            showWarning("Attach to a live JVM first (VM -> Attach to Live JVM).");
            return;
        }
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected to patch.");
            return;
        }
        final String internalName = currentClass.getClassName();
        final ClassFile edited = currentClass.getClassFile();
        final LiveSession session = svc.getSession();
        consolePanel.log("Patching live class " + internalName + "...");
        SwingWorkers.run(
                () -> {
                    byte[] bytes = LivePatch.buildRedefineBytes(session, internalName, edited);
                    session.redefineClass(internalName, bytes);
                    return null;
                },
                ignored -> consolePanel.log("Live patch applied to " + internalName + "."),
                err -> {
                    consolePanel.log("Live patch failed: " + err.getMessage());
                    showWarning("Live patch failed: " + err.getMessage()
                            + "\n(Only method-body changes are supported; structural changes are rejected.)");
                });
    }

    public void showVMConsole() {
        dialogManager.showVMConsole();
    }

    public void showBytecodeDebugger() {
        dialogManager.showBytecodeDebugger();
    }

    public void showExecuteMethodDialog() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project before executing methods.");
            return;
        }

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        ExecuteMethodDialog dialog;
        if (currentMethod != null) {
            dialog = new ExecuteMethodDialog(this, currentMethod);
            consolePanel.log("Execute Method: Opened for " + currentMethod.getMethodEntry().getName());
        } else {
            dialog = new ExecuteMethodDialog(this);
            consolePanel.log("Execute Method: Opened - select a method to execute");
        }
        dialog.setVisible(true);
        statusBar.setMessage("Execute Method dialog opened");
    }

    public void openExecuteMethodDialog(MethodEntryModel method) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project before executing methods.");
            return;
        }

        ExecuteMethodDialog dialog = new ExecuteMethodDialog(this, method);
        consolePanel.log("Execute Method: Opened for " + method.getMethodEntry().getName());
        dialog.setVisible(true);
        statusBar.setMessage("Execute Method dialog opened");
    }

    public void initializeVM() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project before initializing the VM.");
            return;
        }

        try {
            VMExecutionService.getInstance().initialize();
            consolePanel.log("VM initialized successfully with " + project.getClassCount() + " classes");
            statusBar.setMessage("VM initialized");
        } catch (Exception e) {
            showError("Failed to initialize VM: " + e.getMessage());
            consolePanel.logError("VM initialization failed: " + e.getMessage());
        }
    }

    public void resetVM() {
        if (!VMExecutionService.getInstance().isInitialized()) {
            showInfo("VM is not initialized.");
            return;
        }

        try {
            VMExecutionService.getInstance().reset();
            consolePanel.log("VM reset successfully");
            statusBar.setMessage("VM reset");
        } catch (Exception e) {
            showError("Failed to reset VM: " + e.getMessage());
            consolePanel.logError("VM reset failed: " + e.getMessage());
        }
    }

    public void showVMStatus() {
        VMExecutionService vmService = VMExecutionService.getInstance();
        String status = vmService.getVMStatus();
        JOptionPane.showMessageDialog(this, status, "VM Status", JOptionPane.INFORMATION_MESSAGE);
    }

    public void showHeapForensics() {
        dialogManager.showHeapForensics();
    }

    /**
     * Navigate to a specific class.
     * @param className the internal class name (e.g., "com/example/MyClass")
     * @return true if navigation succeeded
     */
    public boolean navigateToClass(String className) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return false;

        String normalizedName = className.replace('.', '/');
        ClassEntryModel classEntry = project.getClass(normalizedName);
        if (classEntry == null) {
            classEntry = project.findClassByName(className);
        }
        if (classEntry != null) {
            openClassInEditor(classEntry);
            return true;
        }
        return false;
    }

    /**
     * Navigate to a specific method in a class.
     * @param className the internal class name
     * @param methodName the method name
     * @param methodDesc the method descriptor (can be null)
     * @return true if navigation succeeded
     */
    public boolean navigateToMethod(String className, String methodName, String methodDesc) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return false;

        String normalizedName = className.replace('.', '/');
        ClassEntryModel classEntry = project.getClass(normalizedName);
        if (classEntry == null) {
            classEntry = project.findClassByName(className);
        }
        if (classEntry != null) {
            return editorPanel.navigateToMethod(classEntry, methodName, methodDesc, currentViewMode);
        }
        return false;
    }

    /**
     * Navigate to a specific bytecode offset within a method.
     * Opens bytecode view and highlights the instruction at the given PC.
     * @param className the internal class name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset
     * @return true if navigation succeeded
     */
    public boolean navigateToPC(String className, String methodName, String methodDesc, int pc) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return false;

        String normalizedName = className.replace('.', '/');
        ClassEntryModel classEntry = project.getClass(normalizedName);
        if (classEntry == null) {
            classEntry = project.findClassByName(className);
        }
        if (classEntry != null) {
            // Switch the view (and toolbar/status) to bytecode FIRST, so the highlight applied by
            // navigateToPC is the last operation - otherwise a follow-up setViewMode refresh would
            // rebuild the view and clear the just-selected instruction line.
            switchToBytecodeView();
            return editorPanel.navigateToPC(classEntry, methodName, methodDesc, pc);
        }
        return false;
    }

    /**
     * Navigate using a QueryTarget from query results.
     */
    public boolean navigateToTarget(QueryTarget target) {
        if (target instanceof QueryTarget.ClassTarget) {
            QueryTarget.ClassTarget ct =
                (QueryTarget.ClassTarget) target;
            return navigateToClass(ct.className());
        } else if (target instanceof QueryTarget.MethodTarget) {
            QueryTarget.MethodTarget mt =
                (QueryTarget.MethodTarget) target;
            return navigateToMethod(mt.className(), mt.methodName(), mt.descriptor());
        } else if (target instanceof QueryTarget.PCTarget) {
            QueryTarget.PCTarget pt =
                (QueryTarget.PCTarget) target;
            return navigateToPC(pt.className(), pt.methodName(), pt.descriptor(), pt.pc());
        }
        return false;
    }
}
