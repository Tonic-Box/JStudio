package com.tonic.ui.file;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.parser.ClassFile;
import com.tonic.service.ProjectDatabaseService;
import com.tonic.service.ProjectService;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.model.Snapshot;
import com.tonic.service.run.ProjectJarExporter;
import com.tonic.ui.JStudio;
import com.tonic.ui.MainFrame;
import com.tonic.ui.StatusBar;
import com.tonic.ui.bottom.BottomPanel;
import com.tonic.ui.console.ConsolePanel;
import com.tonic.ui.dialog.filechooser.ExtensionFileFilter;
import com.tonic.ui.dialog.filechooser.FileChooserDialog;
import com.tonic.ui.dialog.filechooser.FileChooserResult;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.navigator.NavigatorPanel;
import com.tonic.ui.util.RecentFilesManager;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The main window's file and project I/O: opening and appending JARs, classes and directories, exporting, and the .jstudio project open, save and close lifecycle; it mutates the workspace the MainFrame owns. */
public final class FileOperationsController
{

    private final MainFrame mainFrame;

    /**
     * Creates the controller for a main window.
     *
     * @param mainFrame the window whose workspace this controller loads into
     */
    public FileOperationsController(MainFrame mainFrame)
    {
        this.mainFrame = mainFrame;
    }

    private NavigatorPanel navigatorPanel()
    {
        return mainFrame.getNavigatorPanel();
    }

    private EditorPanel editorPanel()
    {
        return mainFrame.getEditorPanel();
    }

    private ConsolePanel consolePanel()
    {
        return mainFrame.getConsolePanel();
    }

    private StatusBar statusBar()
    {
        return mainFrame.getStatusBar();
    }

    private BottomPanel sidePanel()
    {
        return mainFrame.getSidePanel();
    }

    /** Asks for JAR, class or directory files to open and, when a project is already open, whether to append to it or replace it. */
    public void showOpenDialog()
    {
        FileChooserResult result = FileChooserDialog.showOpenDialog(mainFrame, "Open JAR or Class File", ExtensionFileFilter.javaFiles());

        if (result.isApproved())
        {
            List<File> files = result.getSelectedFiles();
            if (ProjectService.getInstance().hasProject())
            {
                int choice = showAppendOrReplaceDialog(files.size());
                if (choice == 0)
                {
                    appendFiles(files);
                }
                else if (choice == 1)
                {
                    editorPanel().closeAllTabs();
                    navigatorPanel().clear();
                    mainFrame.clearNavigationHistory();
                    mainFrame.disposeAnalysisDialog();
                    for (File file : files)
                    {
                        openFile(file.getAbsolutePath());
                    }
                }
            }
            else
            {
                for (File file : files)
                {
                    openFile(file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Loads a JAR, class file or directory as the current project in the background, detaching any live session first; failures are shown to the user.
     *
     * @param path the file or directory to load
     */
    public void openFile(String path)
    {
        File file = new File(path);
        if (!file.exists())
        {
            mainFrame.showError("File not found: " + path);
            return;
        }

        if (LiveAttachService.getInstance().isAttached())
        {
            mainFrame.detachLive();
        }

        statusBar().showProgress("Loading " + file.getName() + "...");

        SwingWorker<ProjectModel, Void> worker = new SwingWorker<>()
        {
            @Override
            protected ProjectModel doInBackground() throws Exception
            {
                if (file.isDirectory())
                {
                    return ProjectService.getInstance().loadDirectory(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar().showProgress(current, total, msg)));
                }
                else if (file.getName().endsWith(".jar"))
                {
                    return ProjectService.getInstance().loadJar(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar().showProgress(current, total, msg)));
                }
                else if (file.getName().endsWith(".class"))
                {
                    return ProjectService.getInstance().loadClassFile(file);
                }
                else
                {
                    throw new IllegalArgumentException("Unsupported file type: " + file.getName());
                }
            }

            @Override
            protected void done()
            {
                statusBar().hideProgress();
                try
                {
                    ProjectModel project = get();
                    navigatorPanel().loadProject(project);
                    consolePanel().log("Loaded " + project.getClassCount() + " classes from " + project.getProjectName());

                    RecentFilesManager.getInstance().addFile(file);
                }
                catch (Exception e)
                {
                    mainFrame.showError("Failed to load file: " + e.getMessage());
                    consolePanel().logError("Load failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Accepts a drop of JARs, class files and directories, ignoring anything else, and opens or appends them as the open dialog does.
     *
     * @param dtde the drop event, completed by this method
     */
    @SuppressWarnings("unchecked")
    public void handleFileDrop(DropTargetDropEvent dtde)
    {
        try
        {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            {
                List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                List<File> validFiles = new ArrayList<>();
                for (File file : droppedFiles)
                {
                    if (file.isDirectory() ||
                            file.getName().endsWith(".jar") ||
                            file.getName().endsWith(".class"))
                    {
                        validFiles.add(file);
                    }
                }

                if (validFiles.isEmpty())
                {
                    mainFrame.showWarning("No valid files dropped. Only JAR files, class files, and directories are supported.");
                    dtde.dropComplete(false);
                    return;
                }

                if (ProjectService.getInstance().hasProject())
                {
                    int choice = showAppendOrReplaceDialog(validFiles.size());
                    if (choice == 0)
                    {
                        appendFiles(validFiles);
                    }
                    else if (choice == 1)
                    {
                        editorPanel().closeAllTabs();
                        navigatorPanel().clear();
                        mainFrame.clearNavigationHistory();
                        mainFrame.disposeAnalysisDialog();
                        for (File file : validFiles)
                        {
                            openFile(file.getAbsolutePath());
                        }
                    }
                }
                else
                {
                    for (File file : validFiles)
                    {
                        openFile(file.getAbsolutePath());
                    }
                }

                dtde.dropComplete(true);
            }
            else
            {
                dtde.dropComplete(false);
            }
        }
        catch (Exception e)
        {
            mainFrame.showError("Failed to process dropped files: " + e.getMessage());
            dtde.dropComplete(false);
        }
    }

    private int showAppendOrReplaceDialog(int fileCount)
    {
        String message = fileCount == 1
                ? "A project is already open. What would you like to do?"
                : fileCount + " files dropped. A project is already open. What would you like to do?";

        String[] options = {"Append to Current", "Replace Current", "Cancel"};
        return JOptionPane.showOptionDialog(mainFrame, message, "Project Already Open", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    private void appendFiles(List<File> files)
    {
        statusBar().showProgress("Appending files...");

        SwingWorker<Integer, Void> worker = new SwingWorker<>()
        {
            @Override
            protected Integer doInBackground() throws Exception
            {
                int totalAdded = 0;
                for (File file : files)
                {
                    if (file.isDirectory())
                    {
                        totalAdded += ProjectService.getInstance().appendDirectory(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar().showProgress(current, total, msg)));
                    }
                    else if (file.getName().endsWith(".jar"))
                    {
                        totalAdded += ProjectService.getInstance().appendJar(file, (current, total, msg) -> SwingUtilities.invokeLater(() -> statusBar().showProgress(current, total, msg)));
                    }
                    else if (file.getName().endsWith(".class"))
                    {
                        totalAdded += ProjectService.getInstance().appendClassFile(file);
                    }
                }
                return totalAdded;
            }

            @Override
            protected void done()
            {
                statusBar().hideProgress();
                try
                {
                    int added = get();
                    consolePanel().log("Appended " + added + " classes");
                    navigatorPanel().loadProject(ProjectService.getInstance().getCurrentProject());
                }
                catch (Exception e)
                {
                    mainFrame.showError("Failed to append files: " + e.getMessage());
                    consolePanel().logError("Append failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /** Exports the class in the front editor tab to a .class file the user picks, or warns when no class is open. */
    public void exportCurrentClass()
    {
        ClassEntryModel currentClass = editorPanel().getCurrentClass();
        if (currentClass == null)
        {
            mainFrame.showWarning("No class selected to export.");
            return;
        }
        exportClass(currentClass);
    }

    /**
     * Writes a class to a .class file the user picks; does nothing for null.
     *
     * @param classEntry the class to export
     */
    public void exportClass(ClassEntryModel classEntry)
    {
        if (classEntry == null) return;

        FileChooserResult result = FileChooserDialog.showSaveDialog(mainFrame, classEntry.getSimpleName() + ".class", ExtensionFileFilter.classFiles());

        if (result.isApproved())
        {
            File outputFile = result.getSelectedFile();
            try
            {
                ClassFile cf = classEntry.getClassFile();
                byte[] data = cf.write();
                try (FileOutputStream fos = new FileOutputStream(outputFile))
                {
                    fos.write(data);
                }
                consolePanel().log("Exported " + classEntry.getClassName() + " to " + outputFile.getName());
            }
            catch (IOException e)
            {
                mainFrame.showError("Export failed: " + e.getMessage());
                consolePanel().logError("Export failed: " + e.getMessage());
            }
        }
    }

    /** Writes every user class as a .class file under a directory the user picks, laid out by package. */
    public void exportAllClasses()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassCount() == 0)
        {
            mainFrame.showWarning("No project loaded to export.");
            return;
        }

        FileChooserResult result = FileChooserDialog.showDirectoryDialog(mainFrame, "Select Export Directory");

        if (result.isApproved())
        {
            File outputDir = result.getSelectedFile();
            int count = 0;
            int errors = 0;

            for (ClassEntryModel classEntry : project.getUserClasses())
            {
                try
                {
                    ClassFile cf = classEntry.getClassFile();
                    byte[] data = cf.write();

                    String className = classEntry.getClassName();
                    int lastSlash = className.lastIndexOf('/');
                    File targetDir = outputDir;
                    if (lastSlash > 0)
                    {
                        String packageDir = className.substring(0, lastSlash);
                        targetDir = new File(outputDir, packageDir);
                        if (!targetDir.mkdirs() && !targetDir.isDirectory())
                        {
                            throw new IOException("Could not create directory: " + targetDir);
                        }
                    }

                    String simpleName = lastSlash > 0 ? className.substring(lastSlash + 1) : className;
                    File outputFile = new File(targetDir, simpleName + ".class");

                    try (FileOutputStream fos = new FileOutputStream(outputFile))
                    {
                        fos.write(data);
                    }
                    count++;
                }
                catch (IOException e)
                {
                    consolePanel().logError("Failed to export " + classEntry.getClassName() + ": " + e.getMessage());
                    errors++;
                }
            }

            consolePanel().log("Exported " + count + " classes" + (errors > 0 ? " (" + errors + " errors)" : ""));
            mainFrame.showInfo("Exported " + count + " classes to " + outputDir.getName());
        }
    }

    /** Writes the project's user classes and resources to a JAR the user picks. */
    public void exportAsJar()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showInfo("No project is currently open.");
            return;
        }

        List<ClassEntryModel> classes = project.getUserClasses();
        Collection<ResourceEntryModel> resources = project.getAllResources();

        if (classes.isEmpty() && resources.isEmpty())
        {
            mainFrame.showInfo("No classes or resources to export.");
            return;
        }

        FileChooserResult result = FileChooserDialog.showSaveDialog(mainFrame, "output.jar", ExtensionFileFilter.jarFiles());

        if (!result.isApproved())
        {
            return;
        }

        File outputFile = result.getSelectedFile();

        try
        {
            ProjectJarExporter.export(project, outputFile);
            consolePanel().log("Exported " + classes.size() + " classes and " + resources.size() + " resources to " + outputFile.getName());
            mainFrame.showInfo("Successfully exported to " + outputFile.getName());
        }
        catch (IOException e)
        {
            consolePanel().logError("Failed to export JAR: " + e.getMessage());
            mainFrame.showError("Export failed: " + e.getMessage());
        }
    }

    /** Closes the project after offering to save unsaved changes, clearing the navigator, tabs, history and title. */
    public void closeProject()
    {
        if (!confirmCloseIfDirty())
        {
            return;
        }
        ProjectService.getInstance().closeProject();
        ProjectDatabaseService.getInstance().close();
        navigatorPanel().clear();
        editorPanel().closeAllTabs();
        sidePanel().closeAllTabs();
        mainFrame.clearNavigationHistory();
        mainFrame.disposeAnalysisDialog();

        mainFrame.setTitle(JStudio.APP_NAME + " " + JStudio.APP_VERSION);
    }

    /** Opens a .jstudio project the user picks and loads the file it targets, warning when that file is missing. */
    public void openProjectFile()
    {
        FileChooserResult result = FileChooserDialog.showOpenDialog(mainFrame, "Open JStudio Project", new ExtensionFileFilter("JStudio Project", "jstudio"));

        if (result.isApproved())
        {
            File file = result.getSelectedFile();
            try
            {
                ProjectDatabaseService.getInstance().open(file);
                String targetPath = ProjectDatabaseService.getInstance().getDatabase().getTargetPath();
                if (targetPath != null)
                {
                    File targetFile = new File(targetPath);
                    if (targetFile.exists())
                    {
                        openFile(targetPath);
                    }
                    else
                    {
                        mainFrame.showWarning("Target file not found: " + targetPath + "\nThe project annotations have been loaded but no classes are available.");
                    }
                }
                updateTitleBar();
            }
            catch (IOException e)
            {
                mainFrame.showError("Failed to open project: " + e.getMessage());
            }
        }
    }

    /** Saves the project database, creating it for the loaded file if none exists, and takes a local history snapshot. */
    public void saveProject()
    {
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (!dbService.hasDatabase())
        {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            if (project != null && project.getSourceFile() != null)
            {
                dbService.create(project.getSourceFile());
            }
            else
            {
                mainFrame.showWarning("No project loaded to save.");
                return;
            }
        }
        try
        {
            dbService.save();
            LocalHistoryService.getInstance().snapshot(Snapshot.Trigger.SAVE.getDefaultLabel(), Snapshot.Trigger.SAVE);
            LocalHistoryService.getInstance().flush();
            updateTitleBar();
            consolePanel().log("Project saved: " + dbService.getProjectFile().getName());
        }
        catch (IOException e)
        {
            mainFrame.showError("Failed to save project: " + e.getMessage());
        }
    }

    /** Saves the project database to a .jstudio file the user picks, creating the database for the loaded file if none exists. */
    public void saveProjectAs()
    {
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (!dbService.hasDatabase())
        {
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            if (project != null && project.getSourceFile() != null)
            {
                dbService.create(project.getSourceFile());
            }
            else
            {
                mainFrame.showWarning("No project loaded to save.");
                return;
            }
        }

        String defaultName = "project.jstudio";
        if (dbService.getProjectFile() != null)
        {
            defaultName = dbService.getProjectFile().getName();
        }
        else if (dbService.getDatabase() != null)
        {
            defaultName = dbService.getDatabase().getTargetFileName().replace(".jar", "") + ".jstudio";
        }

        FileChooserResult result = FileChooserDialog.showSaveDialog(mainFrame, defaultName, new ExtensionFileFilter("JStudio Project", "jstudio"));

        if (result.isApproved())
        {
            File file = result.getSelectedFile();
            if (!file.getName().endsWith(".jstudio"))
            {
                file = new File(file.getAbsolutePath() + ".jstudio");
            }
            try
            {
                dbService.saveAs(file);
                updateTitleBar();
                consolePanel().log("Project saved as: " + file.getName());
            }
            catch (IOException e)
            {
                mainFrame.showError("Failed to save project: " + e.getMessage());
            }
        }
    }

    /**
     * Offers to save when the project database has unsaved changes.
     *
     * @return true when it is safe to proceed (saved, declined, or nothing to save), false when the user cancelled
     */
    public boolean confirmCloseIfDirty()
    {
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (dbService.isDirty())
        {
            int result = JOptionPane.showConfirmDialog(mainFrame, "You have unsaved project changes. Would you like to save before closing?", "Save Changes?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION)
            {
                saveProject();
                return true;
            }
            else return result == JOptionPane.NO_OPTION;
        }
        return true;
    }

    /** Updates the window title to reflect the current project name and dirty state. */
    public void updateTitleBar()
    {
        StringBuilder title = new StringBuilder(JStudio.APP_NAME);
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            title.append(" - ").append(project.getProjectName());
        }
        ProjectDatabaseService dbService = ProjectDatabaseService.getInstance();
        if (dbService.isDirty())
        {
            title.append(" *");
        }
        mainFrame.setTitle(title.toString());
    }
}
