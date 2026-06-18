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
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.transform.TransformPanel;
import com.tonic.ui.deobfuscation.DeobfuscationPanel;
import com.tonic.ui.script.ScriptEditorDialog;
import com.tonic.ui.util.RecentFilesManager;
import com.tonic.ui.update.UpdateManager;
import com.tonic.util.Settings;
import com.tonic.ui.vm.VMConsolePanel;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.debugger.DebuggerPanel;
import com.tonic.ui.vm.dialog.ExecuteMethodDialog;

import com.tonic.parser.ClassPool;
import com.tonic.renamer.Renamer;
import com.tonic.renamer.exception.RenameException;
import com.tonic.ui.dialog.DeobfuscateNamesDialog;
import com.tonic.ui.dialog.FindInFilesDialog;
import com.tonic.ui.dialog.PreferencesDialog;
import com.tonic.ui.dialog.RenameClassDialog;
import com.tonic.ui.dialog.RenameFieldDialog;
import com.tonic.ui.dialog.RenameMethodDialog;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.ui.dialog.filechooser.ExtensionFileFilter;
import com.tonic.ui.dialog.filechooser.FileChooserDialog;
import com.tonic.ui.dialog.filechooser.FileChooserResult;
import com.tonic.ui.vm.heap.HeapForensicsPanel;
import com.tonic.ui.query.QueryExplorerPanel;
import com.tonic.ui.core.component.ToolWindowPane;
import com.tonic.event.events.LiveSessionEvent;
import com.tonic.ui.live.threads.LiveThreadsPanel;
import com.tonic.ui.live.profiler.LiveProfilerPanel;
import com.tonic.live.LiveSession;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.live.recorder.LiveRecorderPanel;
import com.tonic.service.run.ProjectJarExporter;
import com.tonic.ui.run.RunConfigDialog;
import com.tonic.ui.run.RunConsolePanel;
import com.tonic.service.run.RunService;
import com.tonic.service.run.RunStateService;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.deadcode.RemoveDeadCodeDialog;
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
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
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

    // Analysis and transform dialogs
    private JDialog analysisDialog;
    @Getter
    private AnalysisPanel analysisPanel;
    private ProjectModel analysisProjectRef;
    private JDialog transformDialog;
    private TransformPanel transformPanel;
    private FindInFilesDialog findInFilesDialog;
    private ScriptEditorDialog scriptEditorDialog;
    private PreferencesDialog preferencesDialog;
    private final UpdateManager updateManager;
    private JDialog vmConsoleDialog;
    private VMConsolePanel vmConsolePanel;
    private JFrame debuggerFrame;
    private DebuggerPanel debuggerPanel;
    private JDialog heapForensicsDialog;
    private HeapForensicsPanel heapForensicsPanel;
    private JDialog deobfuscationDialog;
    private DeobfuscationPanel deobfuscationPanel;
    private QueryExplorerPanel queryExplorerPanel;
    @Getter
    private ToolWindowPane rightToolWindow;

    // Bottom panel with tabbed results
    @Getter
    private BottomPanel sidePanel;
    private BottomToolbar bottomToolbar;
    private JSplitPane editorBottomSplit;
    private boolean bottomPanelCollapsed = true;

    // Split panes for layout
    private JSplitPane mainHorizontalSplit;
    private JSplitPane leftRightSplit;

    // Navigation history
    private List<ClassEntryModel> navigationHistory = new ArrayList<>();
    private int historyIndex = -1;

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

    // Panel visibility state - saved divider positions for restore
    private int savedNavigatorDivider = 250;
    private int savedPropertiesDivider = -1;

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
                handleFileDrop(dtde);
            }
        });
    }

    private void initializeComponents() {
        // Create panels
        navigatorPanel = new NavigatorPanel(this);
        editorPanel = new EditorPanel(this);
        propertiesPanel = new PropertiesPanel(this);
        consolePanel = new ConsolePanel();
        statusBar = new StatusBar();

        // Right-edge tool windows: Inspector (default) over a vertical tab stripe, plus Query Explorer
        queryExplorerPanel = new QueryExplorerPanel(this);
        rightToolWindow = new ToolWindowPane();
        rightToolWindow.addTool("Inspector", propertiesPanel);
        rightToolWindow.addTool("Query", queryExplorerPanel);

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
                LiveSession session = LiveAttachService.getInstance().getSession();
                if (session != null && session.supportsJfr()) {
                    if (liveRecorderPanel == null) {
                        liveRecorderPanel = new LiveRecorderPanel(this);
                    }
                    rightToolWindow.addTool("Recorder", liveRecorderPanel);
                }
            } else {
                rightToolWindow.removeTool("Threads");
                rightToolWindow.removeTool("Profiler");
                rightToolWindow.removeTool("Recorder");
            }
        });

        // Bottom panel with tabbed results (Find Usages, Console, Bookmarks, Comments)
        sidePanel = new BottomPanel();
        sidePanel.setEditorPanel(editorPanel);
        sidePanel.setConsolePanel(consolePanel);
        sidePanel.setOnAllTabsClosed(() -> {
            bottomPanelCollapsed = true;
            editorBottomSplit.setDividerLocation(editorBottomSplit.getHeight());
        });
        sidePanel.setOnTabOpened(() -> {
            bottomPanelCollapsed = false;
            int height = editorBottomSplit.getHeight();
            if (editorBottomSplit.getDividerLocation() > height - 50) {
                editorBottomSplit.setDividerLocation(height - 200);
            }
        });

        // Bottom toolbar (always visible, outside split pane)
        bottomToolbar = new BottomToolbar();
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

        // Main content area with split panes
        // Left: Navigator
        // Center: Editor
        // Right: tool windows (Inspector / Query / AI Chat)
        // Bottom (editor area): tabbed results incl. Console

        // Editor + side panel vertical split
        editorBottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, sidePanel);
        editorBottomSplit.setResizeWeight(1.0);
        editorBottomSplit.setDividerSize(4);
        editorBottomSplit.setBorder(null);
        editorBottomSplit.setContinuousLayout(true);
        // Keep bottom panel collapsed on resize if it has no tabs
        editorBottomSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (bottomPanelCollapsed && editorBottomSplit.getHeight() > 0) {
                    editorBottomSplit.setDividerLocation(editorBottomSplit.getHeight());
                }
            }
        });

        // Wrapper for split pane + always-visible toolbar
        JPanel editorAreaWrapper = new JPanel(new BorderLayout());
        editorAreaWrapper.add(editorBottomSplit, BorderLayout.CENTER);
        editorAreaWrapper.add(bottomToolbar, BorderLayout.SOUTH);

        // Center + Right horizontal split
        leftRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                editorAreaWrapper, rightToolWindow);
        leftRightSplit.setResizeWeight(0.75);
        leftRightSplit.setDividerSize(4);
        leftRightSplit.setBorder(null);
        leftRightSplit.setContinuousLayout(true);

        // The right tool window starts collapsed (stripe only); clicking a stripe tab opens/closes it.
        rightToolWindow.setCollapseListener(this::applyRightPanelCollapsed);
        leftRightSplit.addComponentListener(new ComponentAdapter() {
            private boolean applied = false;

            @Override
            public void componentResized(ComponentEvent e) {
                if (leftRightSplit.getWidth() <= 0) {
                    return;
                }
                if (!applied) {
                    applied = true;
                    applyRightPanelCollapsed(rightToolWindow.isCollapsed());
                } else if (rightToolWindow.isCollapsed()) {
                    int stripe = rightToolWindow.getStripeWidth() + leftRightSplit.getDividerSize();
                    leftRightSplit.setDividerLocation(Math.max(0, leftRightSplit.getWidth() - stripe));
                }
            }
        });

        // Navigator + (Center + Right) horizontal split
        mainHorizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                navigatorPanel, leftRightSplit);
        mainHorizontalSplit.setDividerLocation(250);
        mainHorizontalSplit.setDividerSize(4);
        mainHorizontalSplit.setBorder(null);
        mainHorizontalSplit.setContinuousLayout(true);

        contentPane.add(mainHorizontalSplit, BorderLayout.CENTER);
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
            updateTitleBar();
        });

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
        FileChooserResult result = FileChooserDialog.showOpenDialog(this,
                "Open JAR or Class File",
                ExtensionFileFilter.javaFiles());

        if (result.isApproved()) {
            List<File> files = result.getSelectedFiles();
            if (ProjectService.getInstance().hasProject()) {
                int choice = showAppendOrReplaceDialog(files.size());
                if (choice == 0) {
                    // Append
                    appendFiles(files);
                } else if (choice == 1) {
                    // Replace - close existing workspace first
                    editorPanel.closeAllTabs();
                    navigatorPanel.clear();
                    navigationHistory.clear();
                    historyIndex = -1;
                    if (analysisDialog != null) {
                        analysisDialog.dispose();
                        analysisDialog = null;
                        analysisPanel = null;
                        analysisProjectRef = null;
                    }
                    for (File file : files) {
                        openFile(file.getAbsolutePath());
                    }
                }
                // choice == 2 is Cancel - do nothing
            } else {
                for (File file : files) {
                    openFile(file.getAbsolutePath());
                }
            }
        }
    }

    public void openFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            showError("File not found: " + path);
            return;
        }

        // Opening a file replaces the current workspace; if attached to a live JVM, drop that session first
        // (a no-op when not attached) so the live project doesn't linger under the newly-loaded one.
        if (LiveAttachService.getInstance().isAttached()) {
            detachLive();
        }

        statusBar.showProgress("Loading " + file.getName() + "...");

        SwingWorker<ProjectModel, Void> worker = new SwingWorker<>() {
            @Override
            protected ProjectModel doInBackground() throws Exception {
                if (file.isDirectory()) {
                    return ProjectService.getInstance().loadDirectory(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg)));
                } else if (file.getName().endsWith(".jar")) {
                    return ProjectService.getInstance().loadJar(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg)));
                } else if (file.getName().endsWith(".class")) {
                    return ProjectService.getInstance().loadClassFile(file);
                } else {
                    throw new IllegalArgumentException("Unsupported file type: " + file.getName());
                }
            }

            @Override
            protected void done() {
                statusBar.hideProgress();
                try {
                    ProjectModel project = get();
                    navigatorPanel.loadProject(project);
                    consolePanel.log("Loaded " + project.getClassCount() + " classes from " + project.getProjectName());

                    // Add to recent files
                    RecentFilesManager.getInstance().addFile(file);
                } catch (Exception e) {
                    showError("Failed to load file: " + e.getMessage());
                    consolePanel.logError("Load failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    @SuppressWarnings("unchecked")
    private void handleFileDrop(DropTargetDropEvent dtde) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                // Filter for supported file types
                List<File> validFiles = new ArrayList<>();
                for (File file : droppedFiles) {
                    if (file.isDirectory() ||
                        file.getName().endsWith(".jar") ||
                        file.getName().endsWith(".class")) {
                        validFiles.add(file);
                    }
                }

                if (validFiles.isEmpty()) {
                    showWarning("No valid files dropped. Only JAR files, class files, and directories are supported.");
                    dtde.dropComplete(false);
                    return;
                }

                // Check if project is already open
                if (ProjectService.getInstance().hasProject()) {
                    int choice = showAppendOrReplaceDialog(validFiles.size());
                    if (choice == 0) {
                        // Append
                        appendFiles(validFiles);
                    } else if (choice == 1) {
                        // Replace - close existing workspace first
                        editorPanel.closeAllTabs();
                        navigatorPanel.clear();
                        navigationHistory.clear();
                        historyIndex = -1;
                        if (analysisDialog != null) {
                            analysisDialog.dispose();
                            analysisDialog = null;
                            analysisPanel = null;
                            analysisProjectRef = null;
                        }
                        for (File file : validFiles) {
                            openFile(file.getAbsolutePath());
                        }
                    }
                    // choice == 2 is Cancel - do nothing
                } else {
                    // No project open, just open files
                    for (File file : validFiles) {
                        openFile(file.getAbsolutePath());
                    }
                }

                dtde.dropComplete(true);
            } else {
                dtde.dropComplete(false);
            }
        } catch (Exception e) {
            showError("Failed to process dropped files: " + e.getMessage());
            dtde.dropComplete(false);
        }
    }

    private int showAppendOrReplaceDialog(int fileCount) {
        String message = fileCount == 1
                ? "A project is already open. What would you like to do?"
                : fileCount + " files dropped. A project is already open. What would you like to do?";

        String[] options = {"Append to Current", "Replace Current", "Cancel"};
        return JOptionPane.showOptionDialog(
                this,
                message,
                "Project Already Open",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
    }

    private void appendFiles(List<File> files) {
        statusBar.showProgress("Appending files...");

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int totalAdded = 0;
                for (File file : files) {
                    if (file.isDirectory()) {
                        totalAdded += ProjectService.getInstance().appendDirectory(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg)));
                    } else if (file.getName().endsWith(".jar")) {
                        totalAdded += ProjectService.getInstance().appendJar(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg)));
                    } else if (file.getName().endsWith(".class")) {
                        totalAdded += ProjectService.getInstance().appendClassFile(file);
                    }
                }
                return totalAdded;
            }

            @Override
            protected void done() {
                statusBar.hideProgress();
                try {
                    int added = get();
                    consolePanel.log("Appended " + added + " classes");
                    // Refresh navigator
                    navigatorPanel.loadProject(ProjectService.getInstance().getCurrentProject());
                } catch (Exception e) {
                    showError("Failed to append files: " + e.getMessage());
                    consolePanel.logError("Append failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    public void exportCurrentClass() {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected to export.");
            return;
        }
        exportClass(currentClass);
    }

    public void exportClass(ClassEntryModel classEntry) {
        if (classEntry == null) return;

        FileChooserResult result = FileChooserDialog.showSaveDialog(this,
                classEntry.getSimpleName() + ".class",
                ExtensionFileFilter.classFiles());

        if (result.isApproved()) {
            File outputFile = result.getSelectedFile();
            try {
                ClassFile cf = classEntry.getClassFile();
                byte[] data = cf.write();
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(data);
                }
                consolePanel.log("Exported " + classEntry.getClassName() + " to " + outputFile.getName());
            } catch (IOException e) {
                showError("Export failed: " + e.getMessage());
                consolePanel.logError("Export failed: " + e.getMessage());
            }
        }
    }

    public void exportAllClasses() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassCount() == 0) {
            showWarning("No project loaded to export.");
            return;
        }

        FileChooserResult result = FileChooserDialog.showDirectoryDialog(this,
                "Select Export Directory");

        if (result.isApproved()) {
            File outputDir = result.getSelectedFile();
            int count = 0;
            int errors = 0;

            for (ClassEntryModel classEntry : project.getUserClasses()) {
                try {
                    ClassFile cf = classEntry.getClassFile();
                    byte[] data = cf.write();

                    // Create subdirectories for package
                    String className = classEntry.getClassName();
                    int lastSlash = className.lastIndexOf('/');
                    File targetDir = outputDir;
                    if (lastSlash > 0) {
                        String packageDir = className.substring(0, lastSlash);
                        targetDir = new File(outputDir, packageDir);
                        if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
                            throw new IOException("Could not create directory: " + targetDir);
                        }
                    }

                    String simpleName = lastSlash > 0 ? className.substring(lastSlash + 1) : className;
                    File outputFile = new File(targetDir, simpleName + ".class");

                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(data);
                    }
                    count++;
                } catch (IOException e) {
                    consolePanel.logError("Failed to export " + classEntry.getClassName() + ": " + e.getMessage());
                    errors++;
                }
            }

            consolePanel.log("Exported " + count + " classes" + (errors > 0 ? " (" + errors + " errors)" : ""));
            showInfo("Exported " + count + " classes to " + outputDir.getName());
        }
    }

    public void exportAsJar() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showInfo("No project is currently open.");
            return;
        }

        List<ClassEntryModel> classes = project.getUserClasses();
        Collection<ResourceEntryModel> resources = project.getAllResources();

        if (classes.isEmpty() && resources.isEmpty()) {
            showInfo("No classes or resources to export.");
            return;
        }

        FileChooserResult result = FileChooserDialog.showSaveDialog(this,
                "output.jar", ExtensionFileFilter.jarFiles());

        if (!result.isApproved()) {
            return;
        }

        File outputFile = result.getSelectedFile();

        try {
            ProjectJarExporter.export(project, outputFile);
            consolePanel.log("Exported " + classes.size() + " classes and " +
                    resources.size() + " resources to " + outputFile.getName());
            showInfo("Successfully exported to " + outputFile.getName());
        } catch (IOException e) {
            consolePanel.logError("Failed to export JAR: " + e.getMessage());
            showError("Export failed: " + e.getMessage());
        }
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
        }

        Process process = RunService.run(
                project, internalName, config.programArgs, vmOptions, config.workingDir, config.javaHome, panel);
        panel.setProcess(process);
        if (process != null) {
            RunStateService.getInstance().setProcess(process);
            process.onExit().thenAccept(p -> RunStateService.getInstance().clearIf(process));
        }
        if (process == null || port <= 0) {
            return;
        }

        int agentPort = port;
        String pid = String.valueOf(process.pid());
        SwingWorkers.run(
                () -> LiveSession.connect(pid, agentPort),
                LiveAttachService.getInstance()::adoptRunSession,
                err -> consolePanel.log("Live debugging not attached: " + err.getMessage()));
        process.onExit().thenAccept(p -> SwingUtilities.invokeLater(this::onRunProcessExited));
    }

    /** When a launched run process exits, drop its auto-attached live session (if it is still the active one). */
    private void onRunProcessExited() {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (svc.isRunSession()) {
            detachLive();
        }
    }

    public void closeProject() {
        if (!confirmCloseIfDirty()) {
            return;
        }
        ProjectService.getInstance().closeProject();
        ProjectDatabaseService.getInstance().close();
        navigatorPanel.clear();
        editorPanel.closeAllTabs();
        sidePanel.closeAllTabs();
        navigationHistory.clear();
        historyIndex = -1;

        if (analysisDialog != null) {
            analysisDialog.dispose();
            analysisDialog = null;
            analysisPanel = null;
            analysisProjectRef = null;
        }

        setTitle(JStudio.APP_NAME + " " + JStudio.APP_VERSION);
    }

    public void openProjectFile() {
        FileChooserResult result = FileChooserDialog.showOpenDialog(this,
                "Open JStudio Project",
                new ExtensionFileFilter("JStudio Project", "jstudio"));

        if (result.isApproved()) {
            File file = result.getSelectedFile();
            try {
                ProjectDatabaseService.getInstance().open(file);
                String targetPath = ProjectDatabaseService.getInstance().getDatabase().getTargetPath();
                if (targetPath != null) {
                    File targetFile = new File(targetPath);
                    if (targetFile.exists()) {
                        openFile(targetPath);
                    } else {
                        showWarning("Target file not found: " + targetPath + "\nThe project annotations have been loaded but no classes are available.");
                    }
                }
                updateTitleBar();
            } catch (IOException e) {
                showError("Failed to open project: " + e.getMessage());
            }
        }
    }

    public void saveProject() {
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (!dbService.hasDatabase()) {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            if (project != null && project.getSourceFile() != null) {
                dbService.create(project.getSourceFile());
            } else {
                showWarning("No project loaded to save.");
                return;
            }
        }
        try {
            dbService.save();
            updateTitleBar();
            consolePanel.log("Project saved: " + dbService.getProjectFile().getName());
        } catch (IOException e) {
            showError("Failed to save project: " + e.getMessage());
        }
    }

    public void saveProjectAs() {
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (!dbService.hasDatabase()) {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            if (project != null && project.getSourceFile() != null) {
                dbService.create(project.getSourceFile());
            } else {
                showWarning("No project loaded to save.");
                return;
            }
        }

        String defaultName = "project.jstudio";
        if (dbService.getProjectFile() != null) {
            defaultName = dbService.getProjectFile().getName();
        } else if (dbService.getDatabase() != null) {
            defaultName = dbService.getDatabase().getTargetFileName().replace(".jar", "") + ".jstudio";
        }

        FileChooserResult result = FileChooserDialog.showSaveDialog(this,
                defaultName,
                new ExtensionFileFilter("JStudio Project", "jstudio"));

        if (result.isApproved()) {
            File file = result.getSelectedFile();
            if (!file.getName().endsWith(".jstudio")) {
                file = new File(file.getAbsolutePath() + ".jstudio");
            }
            try {
                dbService.saveAs(file);
                updateTitleBar();
                consolePanel.log("Project saved as: " + file.getName());
            } catch (IOException e) {
                showError("Failed to save project: " + e.getMessage());
            }
        }
    }

    private boolean confirmCloseIfDirty() {
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (dbService.isDirty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "You have unsaved project changes. Would you like to save before closing?",
                    "Save Changes?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                saveProject();
                return true;
            } else return result == JOptionPane.NO_OPTION;
        }
        return true;
    }

    private void updateTitleBar() {
        StringBuilder title = new StringBuilder(JStudio.APP_NAME);
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            title.append(" - ").append(project.getProjectName());
        }
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (dbService.isDirty()) {
            title.append(" *");
        }
        setTitle(title.toString());
    }

    public void exitApplication() {
        if (!confirmCloseIfDirty()) {
            return;
        }

        // Save settings before exit
        saveSettings();

        GuiPluginManager.getInstance().shutdown();
        statusBar.dispose();
        queryExplorerPanel.shutdown();
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
        settings.saveDividerPositions(
                mainHorizontalSplit.getDividerLocation(),
                leftRightSplit.getWidth() - leftRightSplit.getDividerLocation(),
                settings.getConsoleHeight()
        );

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

        // Add to navigation history
        if (historyIndex < navigationHistory.size() - 1) {
            // Remove forward history
            navigationHistory = new ArrayList<>(navigationHistory.subList(0, historyIndex + 1));
        }
        navigationHistory.add(classEntry);
        historyIndex = navigationHistory.size() - 1;

        // Update status
        statusBar.setPosition(classEntry.getClassName());
    }

    public void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            ClassEntryModel entry = navigationHistory.get(historyIndex);
            editorPanel.openClass(entry, currentViewMode);
        }
    }

    public void navigateForward() {
        if (historyIndex < navigationHistory.size() - 1) {
            historyIndex++;
            ClassEntryModel entry = navigationHistory.get(historyIndex);
            editorPanel.openClass(entry, currentViewMode);
        }
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
        if (navigatorPanel.isVisible()) {
            savedNavigatorDivider = mainHorizontalSplit.getDividerLocation();
            navigatorPanel.setVisible(false);
            mainHorizontalSplit.setDividerLocation(0);
        } else {
            navigatorPanel.setVisible(true);
            mainHorizontalSplit.setDividerLocation(savedNavigatorDivider > 0 ? savedNavigatorDivider : 250);
        }
        mainHorizontalSplit.revalidate();
    }

    public void togglePropertiesPanel() {
        rightToolWindow.setCollapsed(!rightToolWindow.isCollapsed());
        leftRightSplit.revalidate();
    }

    /**
     * Reacts to the right tool window collapsing/expanding: when collapsed, the right column shrinks to just
     * the stripe; when expanded, the saved layout is restored.
     */
    private void applyRightPanelCollapsed(boolean collapsed) {
        if (collapsed) {
            int loc = leftRightSplit.getDividerLocation();
            if (loc > 0 && loc < leftRightSplit.getWidth()) {
                savedPropertiesDivider = loc;
            }
            int stripe = rightToolWindow.getStripeWidth() + leftRightSplit.getDividerSize();
            leftRightSplit.setDividerLocation(Math.max(0, leftRightSplit.getWidth() - stripe));
        } else {
            if (savedPropertiesDivider > 0) {
                leftRightSplit.setDividerLocation(savedPropertiesDivider);
            } else {
                leftRightSplit.setDividerLocation((int) (leftRightSplit.getWidth() * 0.75));
            }
        }
        leftRightSplit.revalidate();
    }

    /** Opens or closes the Console tab in the bottom panel (View menu / keyboard shortcut). */
    public void toggleConsolePanel() {
        sidePanel.toggleConsoleTab();
    }

    public void refreshCurrentView() {
        editorPanel.refreshCurrentTab();
    }

    public void refreshAfterRename(String oldClassName, String newClassName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) return;

        editorPanel.closeTabForClass(oldClassName);
        navigatorPanel.refresh();
        navigatorPanel.setLoading(false);
        editorPanel.refreshWelcomeTab();

        statusBar.setMessage("Renamed: " + oldClassName.replace('/', '.') +
                " -> " + newClassName.replace('/', '.'));
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
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        // Create the Find in Files dialog if it doesn't exist
        if (findInFilesDialog == null) {
            findInFilesDialog = new FindInFilesDialog(this, project);
        }

        // Pre-fill with selected text if any
        String selectedText = editorPanel.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty() && selectedText.length() < 100) {
            findInFilesDialog.showDialog(selectedText.trim());
        } else {
            findInFilesDialog.showDialog();
        }
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
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null) {
            consolePanel.log("Running analysis on " + currentClass.getClassName() + "...");
        } else {
            consolePanel.log("Opening analysis tools...");
        }
        showAnalysisDialog();
    }

    /**
     * Show the analysis dialog (creates it if needed).
     * Recreates the panel if the project has changed.
     */
    private void showAnalysisDialog() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        boolean needsRecreate = analysisDialog == null || analysisPanel == null || analysisProjectRef != project;

        if (needsRecreate) {
            if (analysisDialog != null) {
                analysisDialog.dispose();
            }
            analysisPanel = new AnalysisPanel(project);
            analysisProjectRef = project;
            analysisDialog = new JDialog(this, "Analysis", false);
            analysisDialog.setSize(900, 600);
            analysisDialog.setLocationRelativeTo(this);
            analysisDialog.add(analysisPanel);
        }

        analysisDialog.setVisible(true);
        analysisDialog.toFront();
    }

    public void showSimilarityAnalysis() {
        if (openAnalysisDialog()) {
            analysisPanel.showSimilarity();
        }
    }

    public void showSearchAnalysis() {
        if (openAnalysisDialog()) {
            analysisPanel.showSearch();
        }
    }

    public void showStringsAnalysis() {
        if (openAnalysisDialog()) {
            analysisPanel.showStrings();
        }
    }

    private boolean openAnalysisDialog() {
        if (ProjectService.getInstance().getCurrentProject() == null) {
            showWarning("No project loaded.");
            return false;
        }
        showAnalysisDialog();
        return true;
    }

    // === Transform Operations ===

    private RemoveDeadCodeDialog removeDeadCodeDialog;

    /** Opens (reusing one instance) the Remove Dead Code analysis dialog. */
    public void showRemoveDeadCodeDialog() {
        if (ProjectService.getInstance().getCurrentProject() == null) {
            showWarning("No project loaded.");
            return;
        }
        if (removeDeadCodeDialog == null) {
            removeDeadCodeDialog = new RemoveDeadCodeDialog(this);
        }
        removeDeadCodeDialog.setVisible(true);
        removeDeadCodeDialog.toFront();
    }

    /** After dead-code removal: close tabs of removed classes and reload the navigator/editor. */
    public void refreshAfterDeadCodeRemoval(Collection<String> removedClassesInternal) {
        for (String internal : removedClassesInternal) {
            editorPanel.closeTabForClass(internal);
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            EventBus.getInstance().post(
                    new ProjectUpdatedEvent(this, project, -removedClassesInternal.size()));
        }
        refreshCurrentView();
    }

    public void showTransformDialog() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        if (transformDialog == null || transformPanel == null) {
            transformPanel = new TransformPanel(project);
            // Set callback to refresh the editor when transforms are applied
            transformPanel.setTransformCallback(this::refreshCurrentView);
            transformDialog = new JDialog(this, "SSA Transforms", false);
            transformDialog.setSize(800, 500);
            transformDialog.setLocationRelativeTo(this);
            transformDialog.add(transformPanel);
        }

        // Set the current class (this populates the method dropdown)
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null) {
            transformPanel.setClass(currentClass);
        }

        transformDialog.setVisible(true);
        transformDialog.toFront();
    }

    /**
     * Shows the script editor dialog.
     */
    public void showScriptEditor() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        if (scriptEditorDialog == null) {
            scriptEditorDialog = new ScriptEditorDialog(this);
            scriptEditorDialog.setOnTransformComplete(this::refreshCurrentView);
        }

        scriptEditorDialog.setProjectModel(project);

        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null) {
            scriptEditorDialog.setClass(currentClass);
        }

        scriptEditorDialog.setVisible(true);
        scriptEditorDialog.toFront();
    }

    public void showDeobfuscationPanel() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project before using deobfuscation tools.");
            return;
        }

        if (deobfuscationDialog == null || deobfuscationPanel == null) {
            deobfuscationPanel = new DeobfuscationPanel(project);
            deobfuscationDialog = new JDialog(this, "String Deobfuscation", false);
            deobfuscationDialog.setSize(1000, 700);
            deobfuscationDialog.setLocationRelativeTo(this);
            deobfuscationDialog.add(deobfuscationPanel);
        } else {
            deobfuscationPanel.setProject(project);
        }

        deobfuscationDialog.setVisible(true);
        deobfuscationDialog.toFront();
        consolePanel.log("String Deobfuscation panel opened");
        statusBar.setMessage("String Deobfuscation");
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
                Renamer renamer = new Renamer(classPool);
                renamer.mapMethod(className, oldName, desc, newName).apply();
                classEntry.invalidateDecompilationCache();
                navigatorPanel.refresh();
                editorPanel.refreshCurrentTab();
                setNavigatorLoading(false);
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
                Renamer renamer = new Renamer(classPool);
                renamer.mapField(className, oldName, desc, newName).apply();
                classEntry.invalidateDecompilationCache();
                navigatorPanel.refresh();
                editorPanel.refreshCurrentTab();
                setNavigatorLoading(false);
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
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected for transformation.");
            return;
        }

        consolePanel.log("Applying " + transformName + " to " + currentClass.getClassName() + "...");

        // Show transform dialog with the specified transform selected
        showTransformDialog();
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
        String mod = System.getProperty("os.name").toLowerCase().contains("mac") ? "Cmd" : "Ctrl";
        String sb = "Keyboard Shortcuts:\n\n" +
                "File:\n" +
                "  " + mod + "+O         Open JAR/Class\n" +
                "  " + mod + "+Shift+O   Open Recent\n" +
                "  " + mod + "+S         Save Project\n" +
                "  " + mod + "+Shift+S   Save Project As\n" +
                "  " + mod + "+Alt+E     Export Class\n" +
                "  " + mod + "+Shift+J   Export as JAR\n" +
                "  " + mod + "+W         Close Tab\n" +
                "  " + mod + "+Shift+W   Close Project\n" +
                "  " + mod + "+Q         Exit\n\n" +
                "Navigation:\n" +
                "  " + mod + "+G         Go to Class\n" +
                "  " + mod + "+L         Go to Line\n" +
                "  Alt+Left       Navigate Back\n" +
                "  Alt+Right      Navigate Forward\n\n" +
                "Edit:\n" +
                "  " + mod + "+C         Copy\n" +
                "  " + mod + "+F         Find in File\n" +
                "  " + mod + "+Shift+F   Find in Project\n" +
                "  " + mod + "+B         Add Bookmark\n" +
                "  " + mod + "+Shift+B   View Bookmarks\n" +
                "  " + mod + "+;         Add Comment\n" +
                "  " + mod + "+,         Preferences\n\n" +
                "Views:\n" +
                "  F5             Source View\n" +
                "  F6             Bytecode View\n" +
                "  F7             IR View\n" +
                "  F8             Hex View\n" +
                "  " + mod + "+F5        Refresh\n" +
                "  Alt+Z          Word Wrap\n\n" +
                "Panels:\n" +
                "  " + mod + "+1         Toggle Navigator\n" +
                "  " + mod + "+2         Toggle Properties\n" +
                "  " + mod + "+3         Toggle Console\n\n" +
                "Font:\n" +
                "  " + mod + "+=         Increase Font\n" +
                "  " + mod + "+-         Decrease Font\n" +
                "  " + mod + "+0         Reset Font\n\n" +
                "Analysis:\n" +
                "  F9             Run Analysis\n" +
                "  F10            Simulation Analysis\n" +
                "  " + mod + "+Shift+G   Call Graph\n\n" +
                "Transform:\n" +
                "  " + mod + "+Shift+T   Apply Transforms\n" +
                "  " + mod + "+Alt+S     Script Editor\n" +
                "  " + mod + "+Shift+D   String Deobfuscation\n\n" +
                "VM:\n" +
                "  F11            Bytecode Debugger\n" +
                "  " + mod + "+Shift+C   VM Console\n" +
                "  " + mod + "+Shift+E   Execute Method\n" +
                "  " + mod + "+Shift+H   Heap Forensics\n";

        JOptionPane.showMessageDialog(this, sb, "Keyboard Shortcuts",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void showAboutDialog() {
        String message = JStudio.APP_NAME + " " + JStudio.APP_VERSION + "\n\n" +
                "A professional Java reverse engineering and analysis suite.";

        JOptionPane.showMessageDialog(this, message, "About " + JStudio.APP_NAME,
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void showPreferencesDialog() {
        if (preferencesDialog == null) {
            preferencesDialog = new PreferencesDialog(this);
            preferencesDialog.setOnApply(() -> {
                currentFontSize = Settings.getInstance().getFontSize();
                editorPanel.setFontSize(currentFontSize);
            });
        }
        preferencesDialog.setVisible(true);
    }

    // === Utility Methods ===

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Information",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    // === Getters ===

    /**
     * Run simulation analysis on the current method or class.
     */
    public void runCodeAnalysis() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showSimulation();

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        if (currentMethod != null) {
            analysisPanel.getSimulationPanel().analyzeMethod(currentMethod);
        } else {
            ClassEntryModel currentClass = editorPanel.getCurrentClass();
            if (currentClass != null) {
                analysisPanel.getSimulationPanel().analyzeClass(currentClass);
            } else {
                consolePanel.log("Select a method or class to analyze.");
            }
        }
    }

    // === VM Operations ===

    /**
     * Opens the "Attach to Live JVM" dialog. Attaching loads the Java agent into the chosen
     * process and replaces the current project with one built from that JVM's loaded classes.
     */
    public void showLiveAttachDialog() {
        new LiveAttachDialog(this).setVisible(true);
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
    public void openLiveFrame(String internalName, String methodName, int line) {
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
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project before using the VM Console.");
            return;
        }

        if (vmConsoleDialog == null || vmConsolePanel == null) {
            vmConsolePanel = new VMConsolePanel();
            vmConsoleDialog = new JDialog(this, "VM Console", false);
            vmConsoleDialog.setSize(800, 500);
            vmConsoleDialog.setLocationRelativeTo(this);
            vmConsoleDialog.add(vmConsolePanel);
        }

        vmConsoleDialog.setVisible(true);
        vmConsoleDialog.toFront();
        vmConsolePanel.focusInput();
    }

    public void showBytecodeDebugger() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded. Load a project before using the Bytecode Debugger.");
            return;
        }

        if (debuggerFrame == null || debuggerPanel == null) {
            debuggerPanel = new DebuggerPanel();
            // A JFrame (unlike a JDialog) gets native minimize/maximize window controls.
            debuggerFrame = new JFrame("Bytecode Debugger");
            debuggerFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            debuggerFrame.setIconImages(getIconImages());
            debuggerFrame.setSize(1100, 700);
            debuggerFrame.setLocationRelativeTo(this);
            debuggerFrame.add(debuggerPanel);
        }

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        if (currentMethod != null) {
            debuggerPanel.setMethod(currentMethod.getMethodEntry());
            consolePanel.log("Bytecode Debugger: Opened for " + currentMethod.getMethodEntry().getName());
        } else {
            consolePanel.log("Bytecode Debugger: Opened - select a method to debug");
        }

        debuggerFrame.setVisible(true);
        debuggerFrame.toFront();
        statusBar.setMessage("Bytecode Debugger opened");
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
        if (heapForensicsDialog == null || heapForensicsPanel == null) {
            heapForensicsPanel = new HeapForensicsPanel();
            heapForensicsDialog = new JDialog(this, "Heap Forensics", false);
            heapForensicsDialog.setSize(1200, 800);
            heapForensicsDialog.setLocationRelativeTo(this);
            heapForensicsDialog.add(heapForensicsPanel);
        }
        heapForensicsDialog.setVisible(true);
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
