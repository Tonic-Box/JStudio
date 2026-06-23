package com.tonic.ui.navigator;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.renamer.Renamer;
import com.tonic.renamer.exception.RenameException;
import com.tonic.ui.MainFrame;
import com.tonic.ui.dialog.NewClassDialog;
import com.tonic.ui.dialog.RenameClassDialog;
import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.event.events.ProjectLoadedEvent;
import com.tonic.event.events.ProjectUpdatedEvent;
import com.tonic.event.events.ResourceSelectedEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.service.ClassCreationService;
import com.tonic.service.ClassCreationService.ClassCreationParams;
import com.tonic.service.ProjectService;
import com.tonic.ui.vm.testgen.FuzzTestGeneratorDialog;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Holds the navigator's mutating user actions (create/rename/delete of classes and resources, plus the fuzz-test
 * launch), driven from the tree's context menu. Dialogs parent to a supplied component, and project changes are
 * announced over the {@link EventBus}; the host panel is told to (re)select a class or toggle its loading overlay
 * via injected callbacks.
 */
final class NavigatorActions {

    private final JComponent parent;
    private final MainFrame mainFrame;
    private final Consumer<String> selectClass;
    private final Consumer<Boolean> setLoading;

    NavigatorActions(JComponent parent, MainFrame mainFrame, Consumer<String> selectClass,
                     Consumer<Boolean> setLoading) {
        this.parent = parent;
        this.mainFrame = mainFrame;
        this.selectClass = selectClass;
        this.setLoading = setLoading;
    }

    void showNewClassDialog(String packageName) {
        ProjectModel existingProject = ProjectService.getInstance().getCurrentProject();

        String internalPackageName = packageName.replace('.', '/');
        NewClassDialog dialog = new NewClassDialog(
                SwingUtilities.getWindowAncestor(parent), internalPackageName);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        ClassCreationParams params = dialog.getCreationParams();

        try {
            ClassFile classFile = ClassCreationService.getInstance().createClass(params);

            // With no project open, creating a class spins up a new project to hold it. Done only
            // after the dialog is confirmed so cancelling leaves the "No Project" state untouched.
            boolean newProject = existingProject == null;
            ProjectModel project = newProject
                    ? ProjectService.getInstance().createProject("Untitled")
                    : existingProject;

            ClassEntryModel entry = project.addClass(classFile);

            if (newProject) {
                EventBus.getInstance().post(new ProjectLoadedEvent(this, project));
            } else {
                EventBus.getInstance().post(new ProjectUpdatedEvent(this, project, 1));
            }

            SwingUtilities.invokeLater(() -> {
                selectClass.accept(entry.getClassName());
                EventBus.getInstance().post(new ClassSelectedEvent(this, entry));
            });

        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent,
                    "Failed to create class: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    void showNewResourceFileDialog(String folderPath) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(parent,
                    "No project loaded",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String filename = JOptionPane.showInputDialog(parent,
                "Enter filename (with extension):",
                "New Resource File",
                JOptionPane.PLAIN_MESSAGE);

        if (filename == null || filename.trim().isEmpty()) {
            return;
        }

        filename = filename.trim();
        if (!isValidFilename(filename)) {
            JOptionPane.showMessageDialog(parent,
                    "Invalid filename",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String path = folderPath.isEmpty() ? filename : folderPath + "/" + filename;

        if (project.getResource(path) != null) {
            JOptionPane.showMessageDialog(parent,
                    "A resource with this name already exists",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        ResourceEntryModel resource = new ResourceEntryModel(path, new byte[0]);
        project.addResource(resource);

        EventBus.getInstance().post(new ProjectUpdatedEvent(this, project, 0));

        SwingUtilities.invokeLater(() ->
                EventBus.getInstance().post(new ResourceSelectedEvent(this, resource)));
    }

    void showImportResourceDialog(String folderPath) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            JOptionPane.showMessageDialog(parent,
                    "No project loaded",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import Resource File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);

        int result = fileChooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File[] selectedFiles = fileChooser.getSelectedFiles();
        int importedCount = 0;

        for (File file : selectedFiles) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                String path = folderPath.isEmpty() ? file.getName() : folderPath + "/" + file.getName();

                if (project.getResource(path) != null) {
                    int overwrite = JOptionPane.showConfirmDialog(parent,
                            "Resource '" + path + "' already exists. Overwrite?",
                            "Confirm Overwrite",
                            JOptionPane.YES_NO_OPTION);
                    if (overwrite != JOptionPane.YES_OPTION) {
                        continue;
                    }
                }

                ResourceEntryModel resource = new ResourceEntryModel(path, data);
                project.addResource(resource);
                importedCount++;

            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent,
                        "Failed to import " + file.getName() + ": " + e.getMessage(),
                        "Import Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        if (importedCount > 0) {
            EventBus.getInstance().post(new ProjectUpdatedEvent(this, project, 0));
            JOptionPane.showMessageDialog(parent,
                    "Imported " + importedCount + " file(s)",
                    "Import Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String invalidChars = "<>:\"/\\|?*";
        for (char c : invalidChars.toCharArray()) {
            if (filename.indexOf(c) >= 0) {
                return false;
            }
        }
        return !filename.equals(".") && !filename.equals("..");
    }

    void renameClass(ClassEntryModel classEntry) {
        String oldName = classEntry.getClassName();

        RenameClassDialog dialog = new RenameClassDialog(
                SwingUtilities.getWindowAncestor(parent), oldName);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        String newName = dialog.getNewClassName();
        if (newName.equals(oldName)) {
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            JOptionPane.showMessageDialog(parent,
                    "No project loaded",
                    "Rename Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        ClassPool classPool = project.getClassPool();

        setLoading.accept(true);

        try {
            com.tonic.service.history.LocalHistoryService.getInstance()
                    .snapshot("Rename class", com.tonic.model.Snapshot.Trigger.RENAME);
            Renamer renamer = new Renamer(classPool);
            renamer.mapClass(oldName, newName).apply();

            project.notifyClassRenamed(oldName, newName);

            mainFrame.refreshAfterRename(oldName, newName);

            SwingUtilities.invokeLater(() -> {
                selectClass.accept(newName);
                JOptionPane.showMessageDialog(parent,
                        "Class renamed successfully:\n" +
                                oldName.replace('/', '.') + " -> " + newName.replace('/', '.'),
                        "Rename Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            });

        } catch (RenameException e) {
            setLoading.accept(false);
            JOptionPane.showMessageDialog(parent,
                    "Rename failed: " + e.getMessage(),
                    "Rename Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            setLoading.accept(false);
            JOptionPane.showMessageDialog(parent,
                    "Unexpected error during rename: " + e.getMessage(),
                    "Rename Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    void deleteClass(ClassEntryModel classEntry) {
        String className = classEntry.getClassName();
        String displayName = className.replace('/', '.');

        int confirm = JOptionPane.showConfirmDialog(parent,
                "Delete class '" + displayName + "'?\n\nThis cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }

        com.tonic.service.history.LocalHistoryService.getInstance()
                .snapshot("Delete class " + displayName, com.tonic.model.Snapshot.Trigger.DELETE);
        if (project.removeClass(className)) {
            mainFrame.closeEditorForClass(className);
            EventBus.getInstance().post(new ProjectUpdatedEvent(this, project, -1));
        }
    }

    void deleteResource(ResourceEntryModel resource) {
        String path = resource.getPath();

        int confirm = JOptionPane.showConfirmDialog(parent,
                "Delete resource '" + path + "'?\n\nThis cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }

        if (project.removeResource(path)) {
            mainFrame.closeEditorForResource(path);
            EventBus.getInstance().post(new ProjectUpdatedEvent(this, project, 0));
        }
    }

    void openFuzzTestDialog(MethodEntryModel method) {
        FuzzTestGeneratorDialog dialog = new FuzzTestGeneratorDialog(
            SwingUtilities.getWindowAncestor(parent));
        dialog.setMethod(
            method.getOwner().getClassName(),
            method.getName(),
            method.getDescriptor());
        dialog.setVisible(true);
    }
}
