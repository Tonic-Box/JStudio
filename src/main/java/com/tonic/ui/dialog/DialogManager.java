package com.tonic.ui.dialog;

import com.tonic.event.EventBus;
import com.tonic.event.events.ProjectUpdatedEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;
import com.tonic.ui.MainFrame;
import com.tonic.ui.analysis.AnalysisPanel;
import com.tonic.ui.deadcode.RemoveDeadCodeDialog;
import com.tonic.ui.deobfuscation.DeobfuscationPanel;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.script.ScriptEditorDialog;
import com.tonic.ui.transform.TransformPanel;
import com.tonic.ui.vm.VMConsolePanel;
import com.tonic.ui.vm.debugger.DebuggerPanel;
import com.tonic.ui.vm.heap.HeapForensicsPanel;

import javax.swing.JDialog;
import javax.swing.JFrame;
import java.util.Collection;

import lombok.Getter;

/** Owns the main window's tool dialogs, creating each on first use and reusing it after, and brings them forward on request. */
public final class DialogManager
{

    private final MainFrame mainFrame;
    private final EditorPanel editorPanel;

    private JDialog analysisDialog;
    @Getter
    private AnalysisPanel analysisPanel;
    private ProjectModel analysisProjectRef;
    private JDialog transformDialog;
    private TransformPanel transformPanel;
    private FindInFilesDialog findInFilesDialog;
    /** The script editor dialog, or null if it has not been opened yet. */
    @Getter
    private ScriptEditorDialog scriptEditorDialog;
    private PreferencesDialog preferencesDialog;
    private JDialog vmConsoleDialog;
    private VMConsolePanel vmConsolePanel;
    private JFrame debuggerFrame;
    private DebuggerPanel debuggerPanel;
    private JDialog heapForensicsDialog;
    private HeapForensicsPanel heapForensicsPanel;
    private JDialog deobfuscationDialog;
    private DeobfuscationPanel deobfuscationPanel;
    private RemoveDeadCodeDialog removeDeadCodeDialog;

    /**
     * Creates the manager; no dialog is created until first shown.
     *
     * @param mainFrame the main window, which parents the dialogs and supplies the console, status bar and refreshes
     * @param editorPanel the editor, which supplies the current class, method and selection
     */
    public DialogManager(MainFrame mainFrame, EditorPanel editorPanel)
    {
        this.mainFrame = mainFrame;
        this.editorPanel = editorPanel;
    }

    /** Disposes the cached analysis dialog, for when the project is closed or replaced; does nothing if there is none. */
    public void disposeAnalysisDialog()
    {
        if (analysisDialog != null)
        {
            analysisDialog.dispose();
            analysisDialog = null;
            analysisPanel = null;
            analysisProjectRef = null;
        }
    }

    /** Logs the current class and opens the analysis dialog; warns if no project is loaded. */
    public void runAnalysis()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null)
        {
            mainFrame.getConsolePanel().log("Running analysis on " + currentClass.getClassName() + "...");
        }
        else
        {
            mainFrame.getConsolePanel().log("Opening analysis tools...");
        }
        showAnalysisDialog();
    }

    private void showAnalysisDialog()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }

        boolean needsRecreate = analysisDialog == null || analysisPanel == null || analysisProjectRef != project;

        if (needsRecreate)
        {
            if (analysisDialog != null)
            {
                analysisDialog.dispose();
            }
            analysisPanel = new AnalysisPanel(project);
            analysisProjectRef = project;
            analysisDialog = new JDialog(mainFrame, "Analysis", false);
            analysisDialog.setSize(900, 600);
            analysisDialog.setLocationRelativeTo(mainFrame);
            analysisDialog.add(analysisPanel);
        }

        analysisDialog.setVisible(true);
        analysisDialog.toFront();
    }

    /** Opens the analysis dialog on its similarity tab; warns if no project is loaded. */
    public void showSimilarityAnalysis()
    {
        if (openAnalysisDialog())
        {
            analysisPanel.showSimilarity();
        }
    }

    /** Opens the analysis dialog on its search tab; warns if no project is loaded. */
    public void showSearchAnalysis()
    {
        if (openAnalysisDialog())
        {
            analysisPanel.showSearch();
        }
    }

    /** Opens the analysis dialog on its strings tab; warns if no project is loaded. */
    public void showStringsAnalysis()
    {
        if (openAnalysisDialog())
        {
            analysisPanel.showStrings();
        }
    }

    private boolean openAnalysisDialog()
    {
        if (ProjectService.getInstance().getCurrentProject() == null)
        {
            mainFrame.showWarning("No project loaded.");
            return false;
        }
        showAnalysisDialog();
        return true;
    }

    /** Opens the simulation tab and simulates the current method, or the current class if no method is selected; warns if no project is loaded. */
    public void runCodeAnalysis()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }

        showAnalysisDialog();
        analysisPanel.showSimulation();

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        if (currentMethod != null)
        {
            analysisPanel.getSimulationPanel().analyzeMethod(currentMethod);
        }
        else
        {
            ClassEntryModel currentClass = editorPanel.getCurrentClass();
            if (currentClass != null)
            {
                analysisPanel.getSimulationPanel().analyzeClass(currentClass);
            }
            else
            {
                mainFrame.getConsolePanel().log("Select a method or class to analyze.");
            }
        }
    }

    /** Opens the remove dead code dialog; warns if no project is loaded. */
    public void showRemoveDeadCodeDialog()
    {
        if (ProjectService.getInstance().getCurrentProject() == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }
        if (removeDeadCodeDialog == null)
        {
            removeDeadCodeDialog = new RemoveDeadCodeDialog(mainFrame);
        }
        removeDeadCodeDialog.setVisible(true);
        removeDeadCodeDialog.toFront();
    }

    /**
     * Closes the tabs of removed classes, posts a project update and refreshes the current view.
     *
     * @param removedClassesInternal the removed classes' internal names, with slashes
     */
    public void refreshAfterDeadCodeRemoval(Collection<String> removedClassesInternal)
    {
        for (String internal : removedClassesInternal)
        {
            editorPanel.closeTabForClass(internal);
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            EventBus.getInstance().post(new ProjectUpdatedEvent(mainFrame, project, -removedClassesInternal.size()));
        }
        mainFrame.refreshCurrentView();
    }

    /** Opens the SSA transforms dialog on the current class; warns if no project is loaded. */
    public void showTransformDialog()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }

        if (transformDialog == null || transformPanel == null)
        {
            transformPanel = new TransformPanel(project);
            transformPanel.setTransformCallback(mainFrame::refreshCurrentView);
            transformDialog = new JDialog(mainFrame, "SSA Transforms", false);
            transformDialog.setSize(800, 500);
            transformDialog.setLocationRelativeTo(mainFrame);
            transformDialog.add(transformPanel);
        }

        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null)
        {
            transformPanel.setClass(currentClass);
        }

        transformDialog.setVisible(true);
        transformDialog.toFront();
    }

    /** Opens the script editor on the current project and class; warns if no project is loaded. */
    public void showScriptEditor()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }

        if (scriptEditorDialog == null)
        {
            scriptEditorDialog = new ScriptEditorDialog(mainFrame);
            scriptEditorDialog.setOnTransformComplete(mainFrame::refreshCurrentView);
        }

        scriptEditorDialog.setProjectModel(project);

        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass != null)
        {
            scriptEditorDialog.setClass(currentClass);
        }

        scriptEditorDialog.setVisible(true);
        scriptEditorDialog.toFront();
    }

    /** Opens the string deobfuscation dialog on the current project; warns if no project is loaded. */
    public void showDeobfuscationPanel()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded. Load a project before using deobfuscation tools.");
            return;
        }

        if (deobfuscationDialog == null || deobfuscationPanel == null)
        {
            deobfuscationPanel = new DeobfuscationPanel(project);
            deobfuscationDialog = new JDialog(mainFrame, "String Deobfuscation", false);
            deobfuscationDialog.setSize(1000, 700);
            deobfuscationDialog.setLocationRelativeTo(mainFrame);
            deobfuscationDialog.add(deobfuscationPanel);
        }
        else
        {
            deobfuscationPanel.setProject(project);
        }

        deobfuscationDialog.setVisible(true);
        deobfuscationDialog.toFront();
        mainFrame.getConsolePanel().log("String Deobfuscation panel opened");
        mainFrame.getStatusBar().setMessage("String Deobfuscation");
    }

    /**
     * Logs the transform against the current class and opens the SSA transforms dialog; warns if no class is open.
     *
     * @param transformName the transform's name, used only in the log line
     */
    public void applyTransform(String transformName)
    {
        ClassEntryModel currentClass = editorPanel.getCurrentClass();
        if (currentClass == null)
        {
            mainFrame.showWarning("No class selected for transformation.");
            return;
        }

        mainFrame.getConsolePanel().log("Applying " + transformName + " to " + currentClass.getClassName() + "...");

        showTransformDialog();
    }

    /** Opens the find in files dialog, prefilled with the editor selection when it is shorter than 100 characters; warns if no project is loaded. */
    public void showFindInProjectDialog()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded.");
            return;
        }

        if (findInFilesDialog == null)
        {
            findInFilesDialog = new FindInFilesDialog(mainFrame, project);
        }

        String selectedText = editorPanel.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty() && selectedText.length() < 100)
        {
            findInFilesDialog.showDialog(selectedText.trim());
        }
        else
        {
            findInFilesDialog.showDialog();
        }
    }

    /** Opens the preferences dialog, which reapplies the font size to the main window when settings are applied. */
    public void showPreferencesDialog()
    {
        if (preferencesDialog == null)
        {
            preferencesDialog = new PreferencesDialog(mainFrame);
            preferencesDialog.setOnApply(mainFrame::applyFontSizeFromSettings);
        }
        preferencesDialog.setVisible(true);
    }

    /** Opens the VM console and focuses its input; warns if no project is loaded. */
    public void showVMConsole()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded. Load a project before using the VM Console.");
            return;
        }

        if (vmConsoleDialog == null || vmConsolePanel == null)
        {
            vmConsolePanel = new VMConsolePanel();
            vmConsoleDialog = new JDialog(mainFrame, "VM Console", false);
            vmConsoleDialog.setSize(800, 500);
            vmConsoleDialog.setLocationRelativeTo(mainFrame);
            vmConsoleDialog.add(vmConsolePanel);
        }

        vmConsoleDialog.setVisible(true);
        vmConsoleDialog.toFront();
        vmConsolePanel.focusInput();
    }

    /** Opens the bytecode debugger window on the current method, if any; warns if no project is loaded. */
    public void showBytecodeDebugger()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            mainFrame.showWarning("No project loaded. Load a project before using the Bytecode Debugger.");
            return;
        }

        if (debuggerFrame == null || debuggerPanel == null)
        {
            debuggerPanel = new DebuggerPanel();
            debuggerFrame = new JFrame("Bytecode Debugger");
            debuggerFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            debuggerFrame.setIconImages(mainFrame.getIconImages());
            debuggerFrame.setSize(1100, 700);
            debuggerFrame.setLocationRelativeTo(mainFrame);
            debuggerFrame.add(debuggerPanel);
        }

        MethodEntryModel currentMethod = editorPanel.getCurrentMethod();
        if (currentMethod != null)
        {
            debuggerPanel.setMethod(currentMethod.getMethodEntry());
            mainFrame.getConsolePanel().log("Bytecode Debugger: Opened for " + currentMethod.getMethodEntry().getName());
        }
        else
        {
            mainFrame.getConsolePanel().log("Bytecode Debugger: Opened - select a method to debug");
        }

        debuggerFrame.setVisible(true);
        debuggerFrame.toFront();
        mainFrame.getStatusBar().setMessage("Bytecode Debugger opened");
    }

    /** Opens the heap forensics dialog. */
    public void showHeapForensics()
    {
        if (heapForensicsDialog == null || heapForensicsPanel == null)
        {
            heapForensicsPanel = new HeapForensicsPanel();
            heapForensicsDialog = new JDialog(mainFrame, "Heap Forensics", false);
            heapForensicsDialog.setSize(1200, 800);
            heapForensicsDialog.setLocationRelativeTo(mainFrame);
            heapForensicsDialog.add(heapForensicsPanel);
        }
        heapForensicsDialog.setVisible(true);
    }
}
