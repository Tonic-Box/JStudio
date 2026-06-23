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

/**
 * Side-by-side bytecode (left) and decompiled-source (right) view with bidirectional double-click
 * line linking, coordinated by a {@link SourceBytecodeLinker}. Hosts its own pane instances rather
 * than reparenting the tab's primary views (which would be error-prone under the tab's CardLayout).
 *
 * <p>Per-view editor operations dispatched by {@code EditorTab} are routed to whichever pane
 * currently holds focus (source by default); font/word-wrap apply to both; method scrolling scrolls
 * both. The source pane keeps annotations on so its line numbers stay aligned with the decompiler's
 * offset/line maps that the linking depends on.
 */
public class DualView extends JPanel implements EditorView {

    private final BytecodeView bytecodeView;
    private final SourceCodeView sourceView;

    public DualView(ClassEntryModel classEntry) {
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

    public void refresh() {
        bytecodeView.refresh();
        sourceView.refresh();
    }

    public void reload() {
        bytecodeView.reload();
        sourceView.reload();
    }

    public void copySelection() {
        if (isBytecodeFocused()) {
            bytecodeView.copySelection();
        } else {
            sourceView.copySelection();
        }
    }

    public String getText() {
        return isBytecodeFocused() ? bytecodeView.getText() : sourceView.getText();
    }

    public String getSelectedText() {
        return isBytecodeFocused() ? bytecodeView.getSelectedText() : sourceView.getSelectedText();
    }

    public void showFindDialog() {
        if (isBytecodeFocused()) {
            bytecodeView.showFindDialog();
        } else {
            sourceView.showFindDialog();
        }
    }

    public void goToLine(int line) {
        if (isBytecodeFocused()) {
            bytecodeView.goToLine(line);
        } else {
            sourceView.goToLine(line);
        }
    }

    public void scrollToText(String text) {
        if (isBytecodeFocused()) {
            bytecodeView.scrollToText(text);
        } else {
            sourceView.scrollToText(text);
        }
    }

    /** Highlights a 1-based line in the source pane (the dual view's primary pane). */
    public void highlightLine(int line) {
        sourceView.highlightLine(line - 1);
    }

    public void scrollToMethod(String methodName, String methodDesc) {
        bytecodeView.scrollToMethod(methodName, methodDesc);
        sourceView.scrollToMethodDeclaration(methodName, methodDesc);
    }

    public void setFontSize(int size) {
        bytecodeView.setFontSize(size);
        sourceView.setFontSize(size);
    }

    public void setWordWrap(boolean enabled) {
        bytecodeView.setWordWrap(enabled);
        sourceView.setWordWrap(enabled);
    }

    public void setProjectModel(ProjectModel projectModel) {
        sourceView.setProjectModel(projectModel);
    }

    public void setUsageLensEnabled(boolean enabled) {
        sourceView.setUsageLensEnabled(enabled);
    }

    private boolean isBytecodeFocused() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner != null && SwingUtilities.isDescendingFrom(owner, bytecodeView);
    }
}
