package com.tonic.ui.editor.dual;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.editor.bytecode.BytecodeView;
import com.tonic.ui.editor.source.SourceCodeView;
import com.tonic.ui.editor.view.EditorView;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;

/** The side-by-side bytecode and decompiled source view, with double-click line linking both ways; editor operations go to the focused pane, source by default. */
public class DualView extends JPanel implements EditorView
{

    private final BytecodeView bytecodeView;
    private final SourceCodeView sourceView;

    /**
     * Creates the view with its own bytecode and source panes, linked to each other.
     *
     * @param classEntry the class to show
     */
    public DualView(ClassEntryModel classEntry)
    {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        bytecodeView = new BytecodeView(classEntry);
        sourceView = new SourceCodeView(classEntry);

        new SourceBytecodeLinker(classEntry, bytecodeView, sourceView);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bytecodeView, sourceView);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setBackground(JStudioTheme.getBgTertiary());
        add(split, BorderLayout.CENTER);
    }

    /** Refreshes both panes. */
    public void refresh()
    {
        bytecodeView.refresh();
        sourceView.refresh();
    }

    /** Reloads both panes. */
    public void reload()
    {
        bytecodeView.reload();
        sourceView.reload();
    }

    /** Copies the focused pane's selection to the system clipboard. */
    public void copySelection()
    {
        if (isBytecodeFocused())
        {
            bytecodeView.copySelection();
        }
        else
        {
            sourceView.copySelection();
        }
    }

    /**
     * Gets the focused pane's text.
     *
     * @return the bytecode pane's text when it has focus, otherwise the source pane's
     */
    public String getText()
    {
        return isBytecodeFocused() ? bytecodeView.getText() : sourceView.getText();
    }

    /**
     * Gets the focused pane's selected text.
     *
     * @return the selection in the bytecode pane when it has focus, otherwise in the source pane; null when nothing is selected
     */
    public String getSelectedText()
    {
        return isBytecodeFocused() ? bytecodeView.getSelectedText() : sourceView.getSelectedText();
    }

    /** Opens the focused pane's find panel. */
    public void showFindDialog()
    {
        if (isBytecodeFocused())
        {
            bytecodeView.showFindDialog();
        }
        else
        {
            sourceView.showFindDialog();
        }
    }

    /**
     * Moves the focused pane's caret to a line.
     *
     * @param line the 1-based line number
     */
    public void goToLine(int line)
    {
        if (isBytecodeFocused())
        {
            bytecodeView.goToLine(line);
        }
        else
        {
            sourceView.goToLine(line);
        }
    }

    /**
     * Scrolls the focused pane to the first occurrence of a text.
     *
     * @param text the text to find
     */
    public void scrollToText(String text)
    {
        if (isBytecodeFocused())
        {
            bytecodeView.scrollToText(text);
        }
        else
        {
            sourceView.scrollToText(text);
        }
    }

    /**
     * Highlights a line in the source pane.
     *
     * @param line the 1-based line number
     */
    public void highlightLine(int line)
    {
        sourceView.highlightLine(line - 1);
    }

    /**
     * Scrolls both panes to a method.
     *
     * @param methodName the method's name
     * @param methodDesc the method's descriptor
     */
    public void scrollToMethod(String methodName, String methodDesc)
    {
        bytecodeView.scrollToMethod(methodName, methodDesc);
        sourceView.scrollToMethodDeclaration(methodName, methodDesc);
    }

    /**
     * Sets the font size of both panes.
     *
     * @param size the font size in points
     */
    public void setFontSize(int size)
    {
        bytecodeView.setFontSize(size);
        sourceView.setFontSize(size);
    }

    /**
     * Turns word wrap on or off in both panes.
     *
     * @param enabled whether to wrap
     */
    public void setWordWrap(boolean enabled)
    {
        bytecodeView.setWordWrap(enabled);
        sourceView.setWordWrap(enabled);
    }

    /**
     * Gives the source pane the project it resolves references against.
     *
     * @param projectModel the open project
     */
    public void setProjectModel(ProjectModel projectModel)
    {
        sourceView.setProjectModel(projectModel);
    }

    /**
     * Turns the source pane's usage lens on or off.
     *
     * @param enabled whether to show usage counts
     */
    public void setUsageLensEnabled(boolean enabled)
    {
        sourceView.setUsageLensEnabled(enabled);
    }

    private boolean isBytecodeFocused()
    {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner != null && SwingUtilities.isDescendingFrom(owner, bytecodeView);
    }
}
