package com.tonic.ui.editor;

import com.tonic.ui.editor.ast.ASTView;
import com.tonic.ui.editor.bytecode.BytecodeView;
import com.tonic.ui.editor.constpool.ConstPoolView;
import com.tonic.ui.editor.graph.CPGView;
import com.tonic.ui.editor.graph.PDGView;
import com.tonic.ui.editor.graph.SDGView;
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
    private final ConstPoolView constPoolView;
    private final IRView irView;
    private final ASTView astView;
    private final HexView hexView;
    private final PDGView pdgView;
    private final SDGView sdgView;
    private final CPGView cpgView;

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

        constPoolView = new ConstPoolView(classEntry);
        cardPanel.add(constPoolView, ViewMode.CONSTPOOL.name());

        irView = new IRView(classEntry);
        cardPanel.add(irView, ViewMode.IR.name());

        astView = new ASTView(classEntry);
        cardPanel.add(astView, ViewMode.AST.name());

        hexView = new HexView(classEntry);
        cardPanel.add(hexView, ViewMode.HEX.name());

        pdgView = new PDGView(classEntry);
        cardPanel.add(pdgView, ViewMode.PDG.name());

        sdgView = new SDGView(classEntry);
        cardPanel.add(sdgView, ViewMode.SDG.name());

        cpgView = new CPGView(classEntry);
        cardPanel.add(cpgView, ViewMode.CPG.name());

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

        switch (mode) {
            case SOURCE:
                sourceView.refresh();
                break;
            case BYTECODE:
                bytecodeView.refresh();
                break;
            case CONSTPOOL:
                constPoolView.refresh();
                break;
            case IR:
                irView.refresh();
                break;
            case AST:
                astView.refresh();
                break;
            case HEX:
                hexView.refresh();
                break;
            case PDG:
                pdgView.refresh();
                break;
            case SDG:
                sdgView.refresh();
                break;
            case CPG:
                cpgView.refresh();
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
            case CONSTPOOL:
                constPoolView.refresh();
                break;
            case IR:
                irView.refresh();
                break;
            case AST:
                astView.refresh();
                break;
            case HEX:
                hexView.refresh();
                break;
            case PDG:
                pdgView.refresh();
                break;
            case SDG:
                sdgView.refresh();
                break;
            case CPG:
                cpgView.refresh();
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
            case CONSTPOOL:
                constPoolView.copySelection();
                break;
            case IR:
                irView.copySelection();
                break;
            case AST:
                astView.copySelection();
                break;
            case HEX:
                hexView.copySelection();
                break;
            case PDG:
                pdgView.copySelection();
                break;
            case SDG:
                sdgView.copySelection();
                break;
            case CPG:
                cpgView.copySelection();
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
            case CONSTPOOL:
                return constPoolView.getText();
            case IR:
                return irView.getText();
            case AST:
                return astView.getText();
            case HEX:
                return hexView.getText();
            case PDG:
                return pdgView.getText();
            case SDG:
                return sdgView.getText();
            case CPG:
                return cpgView.getText();
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
            case CONSTPOOL:
                constPoolView.goToLine(line);
                break;
            case IR:
                irView.goToLine(line);
                break;
            case AST:
                astView.goToLine(line);
                break;
            case HEX:
                hexView.goToLine(line);
                break;
            case PDG:
                pdgView.goToLine(line);
                break;
            case SDG:
                sdgView.goToLine(line);
                break;
            case CPG:
                cpgView.goToLine(line);
                break;
        }
    }

    /**
     * Highlight a specific line, scroll to it, and place caret one line above.
     */
    public void highlightLine(int line) {
        switch (currentMode) {
            case SOURCE:
                sourceView.highlightLine(line - 1);
                break;
            case BYTECODE:
                bytecodeView.highlightLine(line);
                break;
            case CONSTPOOL:
                constPoolView.highlightLine(line);
                break;
            default:
                goToLine(line);
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
            case CONSTPOOL:
                constPoolView.showFindDialog();
                break;
            case IR:
                irView.showFindDialog();
                break;
            case AST:
                astView.showFindDialog();
                break;
            case HEX:
                hexView.showFindDialog();
                break;
            case PDG:
                pdgView.showFindDialog();
                break;
            case SDG:
                sdgView.showFindDialog();
                break;
            case CPG:
                cpgView.showFindDialog();
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
            case CONSTPOOL:
                return constPoolView.getSelectedText();
            case IR:
                return irView.getSelectedText();
            case AST:
                return astView.getSelectedText();
            case HEX:
                return hexView.getSelectedText();
            case PDG:
                return pdgView.getSelectedText();
            case SDG:
                return sdgView.getSelectedText();
            case CPG:
                return cpgView.getSelectedText();
            default:
                return null;
        }
    }

    /**
     * Scroll to and highlight a specific method declaration.
     * Only highlights in SOURCE and BYTECODE views.
     */
    public void scrollToMethod(MethodEntryModel method) {
        String methodName = method.getMethodEntry().getName();
        String methodDesc = method.getMethodEntry().getDesc();
        switch (currentMode) {
            case SOURCE:
                sourceView.scrollToMethodDeclaration(methodName, methodDesc);
                break;
            case BYTECODE:
                bytecodeView.scrollToMethod(methodName, methodDesc);
                break;
            case CONSTPOOL:
                constPoolView.scrollToText(methodName);
                break;
            case IR:
                irView.scrollToText(methodName);
                break;
            case AST:
                astView.scrollToText(methodName);
                break;
            case HEX:
                hexView.scrollToText(methodName);
                break;
            case PDG:
                pdgView.scrollToText(methodName);
                break;
            case SDG:
                sdgView.scrollToText(methodName);
                break;
            case CPG:
                cpgView.scrollToText(methodName);
                break;
        }
    }

    /**
     * Scroll to and highlight a specific field declaration.
     * Only highlights in SOURCE and BYTECODE views.
     */
    public void scrollToField(FieldEntryModel field) {
        String fieldName = field.getName();
        switch (currentMode) {
            case SOURCE:
                sourceView.scrollToFieldDeclaration(fieldName);
                break;
            case BYTECODE:
                bytecodeView.scrollToField(fieldName);
                break;
            default:
                // Other views just scroll to text
                break;
        }
    }

    /**
     * Set font size for all views.
     */
    public void setFontSize(int size) {
        sourceView.setFontSize(size);
        bytecodeView.setFontSize(size);
        constPoolView.setFontSize(size);
        irView.setFontSize(size);
        astView.setFontSize(size);
        hexView.setFontSize(size);
        pdgView.setFontSize(size);
        sdgView.setFontSize(size);
        cpgView.setFontSize(size);
    }

    /**
     * Set word wrap for all views.
     */
    public void setWordWrap(boolean enabled) {
        sourceView.setWordWrap(enabled);
        bytecodeView.setWordWrap(enabled);
        irView.setWordWrap(enabled);
        astView.setWordWrap(enabled);
        hexView.setWordWrap(enabled);
        pdgView.setWordWrap(enabled);
        sdgView.setWordWrap(enabled);
        cpgView.setWordWrap(enabled);
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
            case CONSTPOOL:
                constPoolView.scrollToText(methodName);
                return true;
            case IR:
                irView.scrollToText(methodName);
                return true;
            case AST:
                astView.scrollToText(methodName);
                return true;
            case PDG:
                pdgView.scrollToText(methodName);
                return true;
            case SDG:
                sdgView.scrollToText(methodName);
                return true;
            case CPG:
                cpgView.scrollToText(methodName);
                return true;
            default:
                return false;
        }
    }

}
