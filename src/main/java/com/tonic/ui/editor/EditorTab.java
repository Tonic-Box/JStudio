package com.tonic.ui.editor;

import com.tonic.ui.editor.ast.ASTView;
import com.tonic.ui.editor.attributes.AttributesView;
import com.tonic.ui.editor.bytecode.BytecodeView;
import com.tonic.ui.editor.callgraph.CallGraphView;
import com.tonic.ui.editor.statistics.StatisticsView;
import com.tonic.ui.editor.cfg.ControlFlowView;
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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

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
    private final HexView hexView;

    private volatile IRView irView;
    private volatile ASTView astView;
    private volatile PDGView pdgView;
    private volatile SDGView sdgView;
    private volatile CPGView cpgView;
    private volatile ControlFlowView controlFlowView;
    private volatile CallGraphView callGraphView;
    private volatile AttributesView attributesView;
    private volatile StatisticsView statisticsView;

    private int fontSize = 12;
    private boolean wordWrap = false;
    private final Set<ViewMode> loadingViews = new HashSet<>();
    private static final String LOADING_CARD = "LOADING";

    private ViewMode currentMode = ViewMode.SOURCE;

    public EditorTab(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        breadcrumbBar = new BreadcrumbBar();
        breadcrumbBar.setClass(classEntry);
        add(breadcrumbBar, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(JStudioTheme.getBgTertiary());

        JPanel loadingPanel = new JPanel(new BorderLayout());
        loadingPanel.setBackground(JStudioTheme.getBgTertiary());
        JLabel loadingLabel = new JLabel("Loading view...", SwingConstants.CENTER);
        loadingLabel.setForeground(JStudioTheme.getTextSecondary());
        loadingPanel.add(loadingLabel, BorderLayout.CENTER);
        cardPanel.add(loadingPanel, LOADING_CARD);

        sourceView = new SourceCodeView(classEntry);
        cardPanel.add(sourceView, ViewMode.SOURCE.name());

        bytecodeView = new BytecodeView(classEntry);
        cardPanel.add(bytecodeView, ViewMode.BYTECODE.name());

        constPoolView = new ConstPoolView(classEntry);
        cardPanel.add(constPoolView, ViewMode.CONSTPOOL.name());

        hexView = new HexView(classEntry);
        cardPanel.add(hexView, ViewMode.HEX.name());

        add(cardPanel, BorderLayout.CENTER);
    }

    private boolean isViewReady(ViewMode mode) {
        switch (mode) {
            case SOURCE:
            case BYTECODE:
            case CONSTPOOL:
            case HEX:
                return true;
            case IR:
                return irView != null;
            case AST:
                return astView != null;
            case PDG:
                return pdgView != null;
            case SDG:
                return sdgView != null;
            case CPG:
                return cpgView != null;
            case CFG:
                return controlFlowView != null;
            case CALLGRAPH:
                return callGraphView != null;
            case ATTRIBUTES:
                return attributesView != null;
            case STATISTICS:
                return statisticsView != null;
            default:
                return false;
        }
    }

    private <T extends JPanel> void loadViewInBackground(ViewMode mode, Supplier<T> viewFactory, java.util.function.Consumer<T> viewSetter) {
        if (loadingViews.contains(mode)) {
            return;
        }
        loadingViews.add(mode);

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return viewFactory.get();
            }

            @Override
            protected void done() {
                try {
                    T view = get();
                    applySettingsToView(view);

                    viewSetter.accept(view);
                    cardPanel.add(view, mode.name());
                    loadingViews.remove(mode);

                    if (currentMode == mode) {
                        refreshView(mode);
                        cardLayout.show(cardPanel, mode.name());
                    }
                } catch (Exception e) {
                    loadingViews.remove(mode);
                }
            }
        }.execute();
    }

    private void applySettingsToView(JPanel view) {
        if (view instanceof IRView) {
            ((IRView) view).setFontSize(fontSize);
            ((IRView) view).setWordWrap(wordWrap);
        } else if (view instanceof ASTView) {
            ((ASTView) view).setFontSize(fontSize);
            ((ASTView) view).setWordWrap(wordWrap);
        } else if (view instanceof PDGView) {
            ((PDGView) view).setFontSize(fontSize);
            ((PDGView) view).setWordWrap(wordWrap);
        } else if (view instanceof SDGView) {
            ((SDGView) view).setFontSize(fontSize);
            ((SDGView) view).setWordWrap(wordWrap);
        } else if (view instanceof CPGView) {
            ((CPGView) view).setFontSize(fontSize);
            ((CPGView) view).setWordWrap(wordWrap);
        } else if (view instanceof ControlFlowView) {
            ((ControlFlowView) view).setFontSize(fontSize);
            ((ControlFlowView) view).setWordWrap(wordWrap);
        } else if (view instanceof CallGraphView) {
            ((CallGraphView) view).setFontSize(fontSize);
            ((CallGraphView) view).setWordWrap(wordWrap);
        } else if (view instanceof AttributesView) {
            ((AttributesView) view).setFontSize(fontSize);
            ((AttributesView) view).setWordWrap(wordWrap);
        } else if (view instanceof StatisticsView) {
            ((StatisticsView) view).setFontSize(fontSize);
            ((StatisticsView) view).setWordWrap(wordWrap);
        }
    }

    private void ensureViewLoaded(ViewMode mode) {
        switch (mode) {
            case IR:
                if (irView == null) loadViewInBackground(mode, () -> new IRView(classEntry), v -> irView = v);
                break;
            case AST:
                if (astView == null) loadViewInBackground(mode, () -> new ASTView(classEntry), v -> astView = v);
                break;
            case PDG:
                if (pdgView == null) loadViewInBackground(mode, () -> new PDGView(classEntry), v -> pdgView = v);
                break;
            case SDG:
                if (sdgView == null) loadViewInBackground(mode, () -> new SDGView(classEntry), v -> sdgView = v);
                break;
            case CPG:
                if (cpgView == null) loadViewInBackground(mode, () -> new CPGView(classEntry), v -> cpgView = v);
                break;
            case CFG:
                if (controlFlowView == null) loadViewInBackground(mode, () -> new ControlFlowView(classEntry), v -> controlFlowView = v);
                break;
            case CALLGRAPH:
                if (callGraphView == null) loadViewInBackground(mode, () -> new CallGraphView(classEntry), v -> callGraphView = v);
                break;
            case ATTRIBUTES:
                if (attributesView == null) loadViewInBackground(mode, () -> new AttributesView(classEntry), v -> attributesView = v);
                break;
            case STATISTICS:
                if (statisticsView == null) loadViewInBackground(mode, () -> new StatisticsView(classEntry), v -> statisticsView = v);
                break;
            default:
                break;
        }
    }

    private void refreshView(ViewMode mode) {
        switch (mode) {
            case SOURCE: sourceView.refresh(); break;
            case BYTECODE: bytecodeView.refresh(); break;
            case CONSTPOOL: constPoolView.refresh(); break;
            case HEX: hexView.refresh(); break;
            case IR: if (irView != null) irView.refresh(); break;
            case AST: if (astView != null) astView.refresh(); break;
            case PDG: if (pdgView != null) pdgView.refresh(); break;
            case SDG: if (sdgView != null) sdgView.refresh(); break;
            case CPG: if (cpgView != null) cpgView.refresh(); break;
            case CFG: if (controlFlowView != null) controlFlowView.refresh(); break;
            case CALLGRAPH: if (callGraphView != null) callGraphView.refresh(); break;
            case ATTRIBUTES: if (attributesView != null) attributesView.refresh(); break;
            case STATISTICS: if (statisticsView != null) statisticsView.refresh(); break;
        }
    }

    public ViewMode getViewMode() {
        return currentMode;
    }

    public void setViewMode(ViewMode mode) {
        this.currentMode = mode;

        if (isViewReady(mode)) {
            refreshView(mode);
            cardLayout.show(cardPanel, mode.name());
        } else {
            cardLayout.show(cardPanel, LOADING_CARD);
            ensureViewLoaded(mode);
        }
    }

    /**
     * Set whether to omit annotations from decompiled output display.
     */
    public void setOmitAnnotations(boolean omit) {
        sourceView.setOmitAnnotations(omit);
    }

    public void refresh() {
        if (isViewReady(currentMode)) {
            refreshView(currentMode);
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

    public void copySelection() {
        switch (currentMode) {
            case SOURCE: sourceView.copySelection(); break;
            case BYTECODE: bytecodeView.copySelection(); break;
            case CONSTPOOL: constPoolView.copySelection(); break;
            case HEX: hexView.copySelection(); break;
            case IR: if (irView != null) irView.copySelection(); break;
            case AST: if (astView != null) astView.copySelection(); break;
            case PDG: if (pdgView != null) pdgView.copySelection(); break;
            case SDG: if (sdgView != null) sdgView.copySelection(); break;
            case CPG: if (cpgView != null) cpgView.copySelection(); break;
            case CFG: if (controlFlowView != null) controlFlowView.copySelection(); break;
            case CALLGRAPH: if (callGraphView != null) callGraphView.copySelection(); break;
            case ATTRIBUTES: if (attributesView != null) attributesView.copySelection(); break;
            case STATISTICS: if (statisticsView != null) statisticsView.copySelection(); break;
        }
    }

    public String getText() {
        switch (currentMode) {
            case SOURCE: return sourceView.getText();
            case BYTECODE: return bytecodeView.getText();
            case CONSTPOOL: return constPoolView.getText();
            case HEX: return hexView.getText();
            case IR: return irView != null ? irView.getText() : "";
            case AST: return astView != null ? astView.getText() : "";
            case PDG: return pdgView != null ? pdgView.getText() : "";
            case SDG: return sdgView != null ? sdgView.getText() : "";
            case CPG: return cpgView != null ? cpgView.getText() : "";
            case CFG: return controlFlowView != null ? controlFlowView.getText() : "";
            case CALLGRAPH: return callGraphView != null ? callGraphView.getText() : "";
            case ATTRIBUTES: return attributesView != null ? attributesView.getText() : "";
            case STATISTICS: return statisticsView != null ? statisticsView.getText() : "";
            default: return "";
        }
    }

    public void goToLine(int line) {
        switch (currentMode) {
            case SOURCE: sourceView.goToLine(line); break;
            case BYTECODE: bytecodeView.goToLine(line); break;
            case CONSTPOOL: constPoolView.goToLine(line); break;
            case HEX: hexView.goToLine(line); break;
            case IR: if (irView != null) irView.goToLine(line); break;
            case AST: if (astView != null) astView.goToLine(line); break;
            case PDG: if (pdgView != null) pdgView.goToLine(line); break;
            case SDG: if (sdgView != null) sdgView.goToLine(line); break;
            case CPG: if (cpgView != null) cpgView.goToLine(line); break;
            case CFG: if (controlFlowView != null) controlFlowView.goToLine(line); break;
            case CALLGRAPH: if (callGraphView != null) callGraphView.goToLine(line); break;
            case ATTRIBUTES: if (attributesView != null) attributesView.goToLine(line); break;
            case STATISTICS: if (statisticsView != null) statisticsView.goToLine(line); break;
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

    public void showFindDialog() {
        switch (currentMode) {
            case SOURCE: sourceView.showFindDialog(); break;
            case BYTECODE: bytecodeView.showFindDialog(); break;
            case CONSTPOOL: constPoolView.showFindDialog(); break;
            case HEX: hexView.showFindDialog(); break;
            case IR: if (irView != null) irView.showFindDialog(); break;
            case AST: if (astView != null) astView.showFindDialog(); break;
            case PDG: if (pdgView != null) pdgView.showFindDialog(); break;
            case SDG: if (sdgView != null) sdgView.showFindDialog(); break;
            case CPG: if (cpgView != null) cpgView.showFindDialog(); break;
            case CFG: if (controlFlowView != null) controlFlowView.showFindDialog(); break;
            case CALLGRAPH: if (callGraphView != null) callGraphView.showFindDialog(); break;
            case ATTRIBUTES: if (attributesView != null) attributesView.showFindDialog(); break;
            case STATISTICS: if (statisticsView != null) statisticsView.showFindDialog(); break;
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

    public String getSelectedText() {
        switch (currentMode) {
            case SOURCE: return sourceView.getSelectedText();
            case BYTECODE: return bytecodeView.getSelectedText();
            case CONSTPOOL: return constPoolView.getSelectedText();
            case HEX: return hexView.getSelectedText();
            case IR: return irView != null ? irView.getSelectedText() : null;
            case AST: return astView != null ? astView.getSelectedText() : null;
            case PDG: return pdgView != null ? pdgView.getSelectedText() : null;
            case SDG: return sdgView != null ? sdgView.getSelectedText() : null;
            case CPG: return cpgView != null ? cpgView.getSelectedText() : null;
            case CFG: return controlFlowView != null ? controlFlowView.getSelectedText() : null;
            case CALLGRAPH: return callGraphView != null ? callGraphView.getSelectedText() : null;
            case ATTRIBUTES: return attributesView != null ? attributesView.getSelectedText() : null;
            case STATISTICS: return statisticsView != null ? statisticsView.getSelectedText() : null;
            default: return null;
        }
    }

    public void scrollToMethod(MethodEntryModel method) {
        String methodName = method.getMethodEntry().getName();
        String methodDesc = method.getMethodEntry().getDesc();
        switch (currentMode) {
            case SOURCE: sourceView.scrollToMethodDeclaration(methodName, methodDesc); break;
            case BYTECODE: bytecodeView.scrollToMethod(methodName, methodDesc); break;
            case CONSTPOOL: constPoolView.scrollToText(methodName); break;
            case HEX: hexView.scrollToText(methodName); break;
            case IR: if (irView != null) irView.scrollToText(methodName); break;
            case AST: if (astView != null) astView.scrollToText(methodName); break;
            case PDG: if (pdgView != null) pdgView.scrollToText(methodName); break;
            case SDG: if (sdgView != null) sdgView.scrollToText(methodName); break;
            case CPG: if (cpgView != null) cpgView.scrollToText(methodName); break;
            case CFG: if (controlFlowView != null) controlFlowView.scrollToText(methodName); break;
            case CALLGRAPH: if (callGraphView != null) callGraphView.scrollToText(methodName); break;
            case ATTRIBUTES: if (attributesView != null) attributesView.scrollToText(methodName); break;
            case STATISTICS: if (statisticsView != null) statisticsView.scrollToText(methodName); break;
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
        this.fontSize = size;
        sourceView.setFontSize(size);
        bytecodeView.setFontSize(size);
        constPoolView.setFontSize(size);
        hexView.setFontSize(size);
        if (irView != null) irView.setFontSize(size);
        if (astView != null) astView.setFontSize(size);
        if (pdgView != null) pdgView.setFontSize(size);
        if (sdgView != null) sdgView.setFontSize(size);
        if (cpgView != null) cpgView.setFontSize(size);
        if (controlFlowView != null) controlFlowView.setFontSize(size);
        if (callGraphView != null) callGraphView.setFontSize(size);
        if (attributesView != null) attributesView.setFontSize(size);
        if (statisticsView != null) statisticsView.setFontSize(size);
    }

    /**
     * Set word wrap for all views.
     */
    public void setWordWrap(boolean enabled) {
        this.wordWrap = enabled;
        sourceView.setWordWrap(enabled);
        bytecodeView.setWordWrap(enabled);
        hexView.setWordWrap(enabled);
        if (irView != null) irView.setWordWrap(enabled);
        if (astView != null) astView.setWordWrap(enabled);
        if (pdgView != null) pdgView.setWordWrap(enabled);
        if (sdgView != null) sdgView.setWordWrap(enabled);
        if (cpgView != null) cpgView.setWordWrap(enabled);
        if (controlFlowView != null) controlFlowView.setWordWrap(enabled);
        if (callGraphView != null) callGraphView.setWordWrap(enabled);
        if (attributesView != null) attributesView.setWordWrap(enabled);
        if (statisticsView != null) statisticsView.setWordWrap(enabled);
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

    public boolean navigateToMethod(String methodName, String methodDesc) {
        switch (currentMode) {
            case BYTECODE: return bytecodeView.scrollToMethod(methodName, methodDesc);
            case SOURCE: sourceView.scrollToText(methodName); return true;
            case CONSTPOOL: constPoolView.scrollToText(methodName); return true;
            case HEX: return false;
            case IR: if (irView != null) { irView.scrollToText(methodName); return true; } return false;
            case AST: if (astView != null) { astView.scrollToText(methodName); return true; } return false;
            case PDG: if (pdgView != null) { pdgView.scrollToText(methodName); return true; } return false;
            case SDG: if (sdgView != null) { sdgView.scrollToText(methodName); return true; } return false;
            case CPG: if (cpgView != null) { cpgView.scrollToText(methodName); return true; } return false;
            case CFG: if (controlFlowView != null) { controlFlowView.scrollToText(methodName); return true; } return false;
            case CALLGRAPH: if (callGraphView != null) { callGraphView.scrollToText(methodName); return true; } return false;
            case ATTRIBUTES: if (attributesView != null) { attributesView.scrollToText(methodName); return true; } return false;
            case STATISTICS: if (statisticsView != null) { statisticsView.scrollToText(methodName); return true; } return false;
            default: return false;
        }
    }

}
