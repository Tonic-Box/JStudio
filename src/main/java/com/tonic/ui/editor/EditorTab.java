package com.tonic.ui.editor;

import com.tonic.ui.editor.bytecode.BytecodeView;
import com.tonic.ui.editor.hex.HexView;
import com.tonic.ui.editor.ir.IRView;
import com.tonic.ui.editor.source.SourceCodeView;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;

/**
 * A single editor tab that can show source, bytecode, or IR view.
 */
public class EditorTab extends JPanel {

    /**
     * -- GETTER --
     *  Get the class entry for this tab.
     */
    @Getter
    private final ClassEntryModel classEntry;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    /**
     * -- GETTER --
     *  Get the breadcrumb bar for this tab.
     */
    @Getter
    private final BreadcrumbBar breadcrumbBar;

    private final SourceCodeView sourceView;
    /**
     * -- GETTER --
     *  Get the BytecodeView for direct access.
     */
    @Getter
    private final BytecodeView bytecodeView;
    private final IRView irView;
    private final HexView hexView;

    private ViewMode currentMode = ViewMode.SOURCE;

    public EditorTab(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        // Breadcrumb bar at top
        breadcrumbBar = new BreadcrumbBar();
        breadcrumbBar.setClass(classEntry);
        add(breadcrumbBar, BorderLayout.NORTH);

        // Card layout to switch between views
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(JStudioTheme.getBgTertiary());

        // Create views lazily
        sourceView = new SourceCodeView(classEntry);
        cardPanel.add(sourceView, ViewMode.SOURCE.name());

        bytecodeView = new BytecodeView(classEntry);
        cardPanel.add(bytecodeView, ViewMode.BYTECODE.name());

        irView = new IRView(classEntry);
        cardPanel.add(irView, ViewMode.IR.name());

        hexView = new HexView(classEntry);
        cardPanel.add(hexView, ViewMode.HEX.name());

        add(cardPanel, BorderLayout.CENTER);

        // Show source view by default
        setViewMode(ViewMode.SOURCE);
    }

    /**
     * Get the current view mode.
     */
    public ViewMode getViewMode() {
        return currentMode;
    }

    /**
     * Set the view mode.
     */
    public void setViewMode(ViewMode mode) {
        this.currentMode = mode;
        cardLayout.show(cardPanel, mode.name());

        // Refresh the view when switching to it
        switch (mode) {
            case SOURCE:
                sourceView.refresh();
                break;
            case BYTECODE:
                bytecodeView.refresh();
                break;
            case IR:
                irView.refresh();
                break;
            case HEX:
                hexView.refresh();
                break;
        }
    }

    /**
     * Set whether to omit annotations from decompiled output display.
     */
    public void setOmitAnnotations(boolean omit) {
        sourceView.setOmitAnnotations(omit);
    }

    /**
     * Refresh the current view.
     */
    public void refresh() {
        switch (currentMode) {
            case SOURCE:
                sourceView.refresh();
                break;
            case BYTECODE:
                bytecodeView.refresh();
                break;
            case IR:
                irView.refresh();
                break;
            case HEX:
                hexView.refresh();
                break;
        }
    }

    /**
     * Get the title for this tab.
     */
    public String getTitle() {
        return sanitize(classEntry.getSimpleName());
    }

    /**
     * Get the tooltip for this tab.
     */
    public String getTooltip() {
        return sanitize(classEntry.getClassName());
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Copy the current selection.
     */
    public void copySelection() {
        switch (currentMode) {
            case SOURCE:
                sourceView.copySelection();
                break;
            case BYTECODE:
                bytecodeView.copySelection();
                break;
            case IR:
                irView.copySelection();
                break;
            case HEX:
                hexView.copySelection();
                break;
        }
    }

    /**
     * Get current text content.
     */
    public String getText() {
        switch (currentMode) {
            case SOURCE:
                return sourceView.getText();
            case BYTECODE:
                return bytecodeView.getText();
            case IR:
                return irView.getText();
            case HEX:
                return hexView.getText();
            default:
                return "";
        }
    }

    /**
     * Go to a specific line.
     */
    public void goToLine(int line) {
        switch (currentMode) {
            case SOURCE:
                sourceView.goToLine(line);
                break;
            case BYTECODE:
                bytecodeView.goToLine(line);
                break;
            case IR:
                irView.goToLine(line);
                break;
            case HEX:
                hexView.goToLine(line);
                break;
        }
    }

    /**
     * Show find dialog.
     */
    public void showFindDialog() {
        switch (currentMode) {
            case SOURCE:
                sourceView.showFindDialog();
                break;
            case BYTECODE:
                bytecodeView.showFindDialog();
                break;
            case IR:
                irView.showFindDialog();
                break;
            case HEX:
                hexView.showFindDialog();
                break;
        }
    }

    /**
     * Get the currently selected method (if determinable from caret position).
     * This is a best-effort implementation.
     */
    public MethodEntryModel getCurrentMethod() {
        // For now, return the first method if we have any
        // A more sophisticated implementation would track caret position
        if (classEntry.getMethods() != null && !classEntry.getMethods().isEmpty()) {
            return classEntry.getMethods().get(0);
        }
        return null;
    }

    /**
     * Get the selected text from the current view.
     */
    public String getSelectedText() {
        switch (currentMode) {
            case SOURCE:
                return sourceView.getSelectedText();
            case BYTECODE:
                return bytecodeView.getSelectedText();
            case IR:
                return irView.getSelectedText();
            case HEX:
                return hexView.getSelectedText();
            default:
                return null;
        }
    }

    /**
     * Scroll to show a specific method.
     */
    public void scrollToMethod(MethodEntryModel method) {
        // Search for the method name in the text and scroll to it
        String methodName = method.getMethodEntry().getName();
        switch (currentMode) {
            case SOURCE:
                sourceView.scrollToText(methodName);
                break;
            case BYTECODE:
                bytecodeView.scrollToText(methodName);
                break;
            case IR:
                irView.scrollToText(methodName);
                break;
            case HEX:
                hexView.scrollToText(methodName);
                break;
        }
    }

    /**
     * Scroll to show a specific field (source view only).
     */
    public void scrollToField(FieldEntryModel field) {
        setViewMode(ViewMode.SOURCE);
        sourceView.scrollToText(field.getName());
    }

    /**
     * Set font size for all views.
     */
    public void setFontSize(int size) {
        sourceView.setFontSize(size);
        bytecodeView.setFontSize(size);
        irView.setFontSize(size);
        hexView.setFontSize(size);
    }

    /**
     * Set word wrap for all views.
     */
    public void setWordWrap(boolean enabled) {
        sourceView.setWordWrap(enabled);
        bytecodeView.setWordWrap(enabled);
        irView.setWordWrap(enabled);
        hexView.setWordWrap(enabled);
    }

    /**
     * Set the project model for navigation features.
     */
    public void setProjectModel(ProjectModel projectModel) {
        sourceView.setProjectModel(projectModel);
    }

    /**
     * Navigate to a specific bytecode offset within a method.
     * Automatically switches to bytecode view.
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset
     * @return true if navigation succeeded
     */
    public boolean navigateToPC(String methodName, String methodDesc, int pc) {
        setViewMode(ViewMode.BYTECODE);
        return bytecodeView.highlightPC(methodName, methodDesc, pc);
    }

    /**
     * Navigate to a specific method.
     * @param methodName the method name
     * @param methodDesc the method descriptor (can be null)
     * @return true if navigation succeeded
     */
    public boolean navigateToMethod(String methodName, String methodDesc) {
        switch (currentMode) {
            case BYTECODE:
                return bytecodeView.scrollToMethod(methodName, methodDesc);
            case SOURCE:
                sourceView.scrollToText(methodName);
                return true;
            case IR:
                irView.scrollToText(methodName);
                return true;
            default:
                return false;
        }
    }

}
