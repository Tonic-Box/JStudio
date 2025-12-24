package com.tonic.ui;

import com.tonic.analysis.pattern.PatternSearch;
import com.tonic.analysis.pattern.SearchResult;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.analysis.AnalysisPanel;
import com.tonic.ui.browser.ConstPoolBrowserTab;
import com.tonic.ui.console.ConsolePanel;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.editor.ViewMode;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.MethodSelectedEvent;
import com.tonic.ui.event.events.ProjectLoadedEvent;
import com.tonic.ui.event.events.ProjectUpdatedEvent;
import com.tonic.ui.event.events.StatusMessageEvent;
import com.tonic.ui.model.Bookmark;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.Comment;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.navigator.NavigatorPanel;
import com.tonic.ui.properties.PropertiesPanel;
import com.tonic.ui.service.ProjectDatabaseService;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.transform.TransformPanel;
import com.tonic.ui.script.ScriptEditorDialog;
import com.tonic.ui.util.RecentFilesManager;
import com.tonic.ui.util.Settings;
import com.tonic.ui.vm.VMConsolePanel;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.debugger.DebuggerPanel;
import com.tonic.ui.vm.dialog.ExecuteMethodDialog;

import com.tonic.ui.dialog.FindInFilesDialog;
import com.tonic.ui.dialog.PreferencesDialog;
import com.tonic.ui.dialog.filechooser.ExtensionFileFilter;
import com.tonic.ui.dialog.filechooser.FileChooserDialog;
import com.tonic.ui.dialog.filechooser.FileChooserResult;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application window for JStudio.
 */
public class MainFrame extends JFrame {

    // UI Components
    private NavigatorPanel navigatorPanel;
    private EditorPanel editorPanel;
    private PropertiesPanel propertiesPanel;
    private ConsolePanel consolePanel;
    private StatusBar statusBar;
    private ToolbarBuilder toolbarBuilder;

    // Analysis and transform dialogs
    private JDialog analysisDialog;
    private AnalysisPanel analysisPanel;
    private ProjectModel analysisProjectRef;
    private JDialog transformDialog;
    private TransformPanel transformPanel;
    private FindInFilesDialog findInFilesDialog;
    private ScriptEditorDialog scriptEditorDialog;
    private PreferencesDialog preferencesDialog;
    private JDialog classBrowserDialog;
    private ConstPoolBrowserTab classBrowserTab;
    private JDialog vmConsoleDialog;
    private VMConsolePanel vmConsolePanel;
    private JDialog debuggerDialog;
    private DebuggerPanel debuggerPanel;

    // Split panes for layout
    private JSplitPane mainHorizontalSplit;
    private JSplitPane rightVerticalSplit;
    private JSplitPane leftRightSplit;

    // Navigation history
    private List<ClassEntryModel> navigationHistory = new ArrayList<>();
    private int historyIndex = -1;

    // Current state
    private ViewMode currentViewMode = ViewMode.SOURCE;
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
    private int savedConsoleDivider = -1;

    public MainFrame() {
        super(JStudio.APP_NAME + " " + JStudio.APP_VERSION);
        initializeFrame();
        initializeComponents();
        initializeLayout();
        initializeEventHandlers();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 600));

        // Set window icon
        try {
            java.net.URL iconUrl = getClass().getResource("/com/tonic/ui/icon.png");
            if (iconUrl != null) {
                setIconImage(javax.imageio.ImageIO.read(iconUrl));
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
        // Right: Properties
        // Bottom: Console

        // Right side vertical split (Properties over Console)
        rightVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                propertiesPanel, consolePanel);
        rightVerticalSplit.setResizeWeight(0.7);
        rightVerticalSplit.setDividerSize(4);
        rightVerticalSplit.setBorder(null);
        rightVerticalSplit.setContinuousLayout(true);

        // Center + Right horizontal split
        leftRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                editorPanel, rightVerticalSplit);
        leftRightSplit.setResizeWeight(0.75);
        leftRightSplit.setDividerSize(4);
        leftRightSplit.setBorder(null);
        leftRightSplit.setContinuousLayout(true);

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

        statusBar.showProgress("Loading " + file.getName() + "...");

        SwingWorker<ProjectModel, Void> worker = new SwingWorker<ProjectModel, Void>() {
            @Override
            protected ProjectModel doInBackground() throws Exception {
                if (file.isDirectory()) {
                    return ProjectService.getInstance().loadDirectory(file, (current, total, msg) -> {
                        SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg));
                    });
                } else if (file.getName().endsWith(".jar")) {
                    return ProjectService.getInstance().loadJar(file, (current, total, msg) -> {
                        SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg));
                    });
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

        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int totalAdded = 0;
                for (File file : files) {
                    if (file.isDirectory()) {
                        totalAdded += ProjectService.getInstance().appendDirectory(file, (current, total, msg) -> {
                            SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg));
                        });
                    } else if (file.getName().endsWith(".jar")) {
                        totalAdded += ProjectService.getInstance().appendJar(file, (current, total, msg) -> {
                            SwingUtilities.invokeLater(() -> statusBar.showProgress(current, total, msg));
                        });
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

        FileChooserResult result = FileChooserDialog.showSaveDialog(this,
                currentClass.getSimpleName() + ".class",
                ExtensionFileFilter.classFiles());

        if (result.isApproved()) {
            File outputFile = result.getSelectedFile();
            try {
                ClassFile cf = currentClass.getClassFile();
                byte[] data = cf.write();
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(data);
                }
                consolePanel.log("Exported " + currentClass.getClassName() + " to " + outputFile.getName());
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

            for (ClassEntryModel classEntry : project.getAllClasses()) {
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
                        targetDir.mkdirs();
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

    public void closeProject() {
        if (!confirmCloseIfDirty()) {
            return;
        }
        ProjectService.getInstance().closeProject();
        ProjectDatabaseService.getInstance().close();
        navigatorPanel.clear();
        editorPanel.closeAllTabs();
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
            } else if (result == JOptionPane.NO_OPTION) {
                return true;
            } else {
                return false;
            }
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

        statusBar.dispose();
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

        // Save divider positions
        settings.saveDividerPositions(
                mainHorizontalSplit.getDividerLocation(),
                leftRightSplit.getWidth() - leftRightSplit.getDividerLocation(),
                rightVerticalSplit.getHeight() - rightVerticalSplit.getDividerLocation()
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

    public void switchToSourceView() {
        currentViewMode = ViewMode.SOURCE;
        editorPanel.setViewMode(ViewMode.SOURCE);
        statusBar.setMode("Source");
        toolbarBuilder.setViewMode(ToolbarBuilder.ViewMode.SOURCE);
    }

    public void switchToBytecodeView() {
        currentViewMode = ViewMode.BYTECODE;
        editorPanel.setViewMode(ViewMode.BYTECODE);
        statusBar.setMode("Bytecode");
        toolbarBuilder.setViewMode(ToolbarBuilder.ViewMode.BYTECODE);
    }

    public void switchToIRView() {
        currentViewMode = ViewMode.IR;
        editorPanel.setViewMode(ViewMode.IR);
        statusBar.setMode("IR");
        toolbarBuilder.setViewMode(ToolbarBuilder.ViewMode.IR);
    }

    public void switchToHexView() {
        currentViewMode = ViewMode.HEX;
        editorPanel.setViewMode(ViewMode.HEX);
        statusBar.setMode("Hex");
        // Note: Toolbar doesn't have HEX mode yet, leave it as is
    }

    public void setOmitAnnotations(boolean omit) {
        this.omitAnnotations = omit;
        editorPanel.setOmitAnnotations(omit);
    }

    public boolean isOmitAnnotations() {
        return omitAnnotations;
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
        if (propertiesPanel.isVisible()) {
            savedPropertiesDivider = leftRightSplit.getDividerLocation();
            propertiesPanel.setVisible(false);
            rightVerticalSplit.setDividerLocation(rightVerticalSplit.getHeight());
            leftRightSplit.setDividerLocation(leftRightSplit.getWidth());
        } else {
            propertiesPanel.setVisible(true);
            if (savedPropertiesDivider > 0) {
                leftRightSplit.setDividerLocation(savedPropertiesDivider);
            } else {
                leftRightSplit.setDividerLocation((int)(leftRightSplit.getWidth() * 0.75));
            }
            rightVerticalSplit.setDividerLocation((int)(rightVerticalSplit.getHeight() * 0.7));
        }
        leftRightSplit.revalidate();
        rightVerticalSplit.revalidate();
    }

    public void toggleConsolePanel() {
        if (consolePanel.isVisible()) {
            savedConsoleDivider = rightVerticalSplit.getDividerLocation();
            consolePanel.setVisible(false);
            rightVerticalSplit.setDividerLocation(rightVerticalSplit.getHeight());
        } else {
            consolePanel.setVisible(true);
            if (savedConsoleDivider > 0) {
                rightVerticalSplit.setDividerLocation(savedConsoleDivider);
            } else {
                rightVerticalSplit.setDividerLocation((int)(rightVerticalSplit.getHeight() * 0.7));
            }
        }
        rightVerticalSplit.revalidate();
    }

    public void refreshCurrentView() {
        editorPanel.refreshCurrentTab();
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
        consolePanel.log("Added bookmark: " + name.trim() + " → " + currentClass.getSimpleName());
    }

    public void addCommentAtCurrentLocation() {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected. Open a class first to add a comment.");
            return;
        }

        javax.swing.JTextArea textArea = new javax.swing.JTextArea(5, 30);
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
        showAnalysisDialog();
        if (analysisPanel != null) {
            analysisPanel.showBookmarks();
            analysisPanel.getBookmarksPanel().refresh();
        }
    }

    public void showCommentsPanel() {
        showAnalysisDialog();
        if (analysisPanel != null) {
            analysisPanel.showComments();
            analysisPanel.getCommentsPanel().refresh();
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

    public void showCallGraph() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showCallGraph();

        // Build the call graph if not already built
        if (analysisPanel.getCallGraphPanel().getCallGraph() == null) {
            analysisPanel.getCallGraphPanel().buildCallGraph();
        }

        // If a method is selected, focus on it
        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        if (currentMethod != null) {
            analysisPanel.getCallGraphPanel().focusOnMethod(currentMethod.getMethodEntry());
        }
    }

    public void showCallGraphForMethod(MethodEntry method) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showCallGraph();

        if (analysisPanel.getCallGraphPanel().getCallGraph() == null) {
            analysisPanel.getCallGraphPanel().buildCallGraph();
        }

        if (method != null) {
            analysisPanel.getCallGraphPanel().focusOnMethod(method);
        }
    }

    public void showDependencies() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showDependencies();

        // Build dependencies if not already built
        analysisPanel.getDependencyPanel().buildDependencyGraph();

        // If a class is selected, focus on it
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null) {
            analysisPanel.getDependencyPanel().focusOnClass(currentClass.getClassName());
        }
    }

    public void showDependenciesForClass(String className) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showDependencies();
        analysisPanel.getDependencyPanel().buildDependencyGraph();

        if (className != null) {
            analysisPanel.getDependencyPanel().focusOnClass(className);
        }
    }

    public void showXrefsForMethod(String className, String methodName, String methodDesc) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showXrefsForMethod(className, methodName, methodDesc);
    }

    public void showXrefsForField(String className, String fieldName, String fieldDesc) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showXrefsForField(className, fieldName, fieldDesc);
    }

    public void showXrefsForClass(String className) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showXrefsForClass(className);
    }

    public void showUsagesForMethod(String methodName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.findUsages(methodName, "Method");
    }

    public void showUsagesForField(String fieldName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.findUsages(fieldName, "Field");
    }

    public void showUsagesForClass(String className) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.findUsages(className.replace('/', '.'), "Class");
    }

    public void findUsages() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null) {
            showWarning("No class selected.");
            return;
        }

        // Show results in a dialog
        statusBar.showProgress("Finding usages...");

        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<List<SearchResult>, Void>() {
            @Override
            protected List<SearchResult> doInBackground() throws Exception {
                PatternSearch search = new PatternSearch(project.getClassPool())
                        .inAllClasses()
                        .limit(100);

                List<SearchResult> results = new ArrayList<>();

                // Search for method calls to this class
                String className = currentClass.getClassName();
                results.addAll(search.findMethodCalls(className, ".*"));

                // Search for field accesses
                PatternSearch fieldSearch = new PatternSearch(project.getClassPool())
                        .inAllClasses()
                        .limit(50);
                results.addAll(fieldSearch.findFieldAccesses(className));

                // Search for allocations
                PatternSearch allocSearch = new PatternSearch(project.getClassPool())
                        .inAllClasses()
                        .limit(50);
                results.addAll(allocSearch.findAllocations(className));

                return results;
            }

            @Override
            protected void done() {
                statusBar.hideProgress();
                try {
                    List<SearchResult> results = get();
                    showUsageResults(currentClass.getClassName(), results);
                } catch (Exception e) {
                    showError("Find usages failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void showUsageResults(String className, List<SearchResult> results) {
        if (results.isEmpty()) {
            showInfo("No usages found for " + className);
            return;
        }

        // Show in analysis panel's search tab
        showAnalysisDialog();
        analysisPanel.showSearch();

        // Log results to console
        consolePanel.log("Found " + results.size() + " usages of " + className);
        for (SearchResult result : results) {
            String location = "";
            if (result.getClassFile() != null) {
                location = result.getClassFile().getClassName();
            }
            if (result.getMethod() != null) {
                location += "." + result.getMethod().getName();
            }
            consolePanel.log("  " + location + " - " + result.getDescription());
        }
    }

    public void goToDefinition() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        // Get selected text from editor
        String selectedText = editorPanel.getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            showInfo("Select a class name, method name, or field name to go to its definition.");
            return;
        }

        String searchTerm = selectedText.trim();

        // First try to find it as a class
        for (ClassEntryModel classEntry : project.getAllClasses()) {
            String simpleName = classEntry.getSimpleName();
            String fullName = classEntry.getClassName();

            if (simpleName.equals(searchTerm) || fullName.equals(searchTerm) ||
                fullName.replace('/', '.').equals(searchTerm)) {
                openClassInEditor(classEntry);
                return;
            }
        }

        // Try to find as method name
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null) {
            for (MethodEntryModel method : currentClass.getMethods()) {
                if (method.getMethodEntry().getName().equals(searchTerm)) {
                    // Scroll to method in editor
                    editorPanel.scrollToMethod(method);
                    return;
                }
            }
        }

        showInfo("Could not find definition for: " + searchTerm);
    }

    // === Transform Operations ===

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

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
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

    // === Search Operations ===

    public void searchClasses(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        navigatorPanel.filterByName(query.trim());
    }

    // === Help Operations ===

    public void showKeyboardShortcuts() {
        String mod = System.getProperty("os.name").toLowerCase().contains("mac") ? "Cmd" : "Ctrl";
        StringBuilder sb = new StringBuilder();
        sb.append("Keyboard Shortcuts:\n\n");
        sb.append("File:\n");
        sb.append("  ").append(mod).append("+O       Open JAR/Class\n");
        sb.append("  ").append(mod).append("+W       Close Tab\n");
        sb.append("  ").append(mod).append("+Shift+W Close Project\n");
        sb.append("  ").append(mod).append("+Q       Exit\n\n");
        sb.append("Navigation:\n");
        sb.append("  ").append(mod).append("+G       Go to Class\n");
        sb.append("  ").append(mod).append("+L       Go to Line\n");
        sb.append("  Alt+Left     Navigate Back\n");
        sb.append("  Alt+Right    Navigate Forward\n\n");
        sb.append("Edit:\n");
        sb.append("  ").append(mod).append("+C       Copy\n");
        sb.append("  ").append(mod).append("+F       Find\n");
        sb.append("  ").append(mod).append("+Shift+F Find in Project\n\n");
        sb.append("Views:\n");
        sb.append("  F5           Source View\n");
        sb.append("  F6           Bytecode View\n");
        sb.append("  F7           IR View\n");
        sb.append("  ").append(mod).append("+F5      Refresh\n\n");
        sb.append("Panels:\n");
        sb.append("  ").append(mod).append("+1       Toggle Navigator\n");
        sb.append("  ").append(mod).append("+2       Toggle Properties\n");
        sb.append("  ").append(mod).append("+3       Toggle Console\n\n");
        sb.append("Analysis:\n");
        sb.append("  F9           Run Analysis\n");
        sb.append("  ").append(mod).append("+Shift+G Call Graph\n");
        sb.append("  ").append(mod).append("+Shift+T Transforms\n");

        JOptionPane.showMessageDialog(this, sb.toString(), "Keyboard Shortcuts",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void showAboutDialog() {
        String message = JStudio.APP_NAME + " " + JStudio.APP_VERSION + "\n\n" +
                "A professional Java reverse engineering and analysis suite.\n\n" +
                "Features decompilation, SSA IR analysis, call graphs,\n" +
                "and bytecode transformations.";

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

    public EditorPanel getEditorPanel() {
        return editorPanel;
    }

    public NavigatorPanel getNavigatorPanel() {
        return navigatorPanel;
    }

    public PropertiesPanel getPropertiesPanel() {
        return propertiesPanel;
    }

    public ConsolePanel getConsolePanel() {
        return consolePanel;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public AnalysisPanel getAnalysisPanel() {
        return analysisPanel;
    }

    /**
     * Run simulation analysis on the current method or class.
     */
    public void runSimulationAnalysis() {
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

    /**
     * Show the class browser dialog for viewing constant pool and attributes.
     */
    public void showClassBrowser() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        ClassEntryModel currentClass = editorPanel.getCurrentClass();

        if (classBrowserDialog == null || classBrowserTab == null) {
            if (currentClass != null) {
                classBrowserTab = new ConstPoolBrowserTab(project, currentClass);
            } else {
                classBrowserTab = new ConstPoolBrowserTab(project);
            }
            classBrowserDialog = new JDialog(this, "Class Browser", false);
            classBrowserDialog.setSize(1000, 700);
            classBrowserDialog.setLocationRelativeTo(this);
            classBrowserDialog.add(classBrowserTab);
        } else if (currentClass != null && currentClass != classBrowserTab.getCurrentClass()) {
            classBrowserTab.loadClass(currentClass);
        }

        classBrowserDialog.setVisible(true);
        classBrowserDialog.toFront();
    }

    /**
     * Show class browser for a specific class.
     */
    public void showClassBrowser(ClassEntryModel classEntry) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            showWarning("No project loaded.");
            return;
        }

        if (classBrowserDialog == null || classBrowserTab == null) {
            classBrowserTab = new ConstPoolBrowserTab(project, classEntry);
            classBrowserDialog = new JDialog(this, "Class Browser", false);
            classBrowserDialog.setSize(1000, 700);
            classBrowserDialog.setLocationRelativeTo(this);
            classBrowserDialog.add(classBrowserTab);
        } else {
            classBrowserTab.loadClass(classEntry);
        }

        classBrowserDialog.setVisible(true);
        classBrowserDialog.toFront();
    }

    // === VM Operations ===

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

        if (debuggerDialog == null || debuggerPanel == null) {
            debuggerPanel = new DebuggerPanel();
            debuggerDialog = new JDialog(this, "Bytecode Debugger", false);
            debuggerDialog.setSize(1100, 700);
            debuggerDialog.setLocationRelativeTo(this);
            debuggerDialog.add(debuggerPanel);
        }

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        if (currentMethod != null) {
            debuggerPanel.setMethod(currentMethod.getMethodEntry());
            consolePanel.log("Bytecode Debugger: Opened for " + currentMethod.getMethodEntry().getName());
        } else {
            consolePanel.log("Bytecode Debugger: Opened - select a method to debug");
        }

        debuggerDialog.setVisible(true);
        debuggerDialog.toFront();
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
}
