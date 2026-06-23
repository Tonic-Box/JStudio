package com.tonic.ui.editor;

import com.tonic.ui.editor.ast.ASTView;
import com.tonic.ui.editor.attributes.AttributesView;
import com.tonic.ui.editor.bytecode.BytecodeView;
import com.tonic.ui.editor.callgraph.CallGraphView;
import com.tonic.ui.editor.statistics.StatisticsView;
import com.tonic.ui.editor.cfg.ControlFlowView;
import com.tonic.ui.editor.constpool.ConstPoolView;
import com.tonic.ui.editor.dual.DualView;
import com.tonic.ui.editor.graph.CPGView;
import com.tonic.ui.editor.graph.PDGView;
import com.tonic.ui.editor.graph.SDGView;
import com.tonic.ui.editor.hex.HexView;
import com.tonic.ui.editor.ir.IRView;
import com.tonic.ui.editor.llvm.LLVMView;
import com.tonic.ui.editor.source.SourceCodeView;
import com.tonic.ui.editor.view.EditorView;
import com.tonic.ui.live.heap.LiveInstancesView;
import com.tonic.ui.live.statics.LiveStaticsView;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
    private volatile LLVMView llvmView;
    private volatile ASTView astView;
    private volatile PDGView pdgView;
    private volatile SDGView sdgView;
    private volatile CPGView cpgView;
    private volatile ControlFlowView controlFlowView;
    private volatile CallGraphView callGraphView;
    private volatile AttributesView attributesView;
    private volatile StatisticsView statisticsView;
    private volatile DualView dualView;
    private volatile LiveInstancesView liveInstancesView;
    private volatile LiveStaticsView liveStaticsView;

    /** All instantiated views keyed by mode, for polymorphic dispatch (kept in sync with the typed fields). */
    private final Map<ViewMode, EditorView> views = new EnumMap<>(ViewMode.class);

    private int fontSize = 12;
    private boolean wordWrap = false;
    private final Set<ViewMode> loadingViews = new HashSet<>();
    private static final String LOADING_CARD = "LOADING";

    private ViewMode currentMode = ViewMode.SOURCE;
    private ProjectModel projectModel;

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
        sourceView.setOnRecompiled(this::onClassRecompiled);
        cardPanel.add(sourceView, ViewMode.SOURCE.name());

        bytecodeView = new BytecodeView(classEntry);
        cardPanel.add(bytecodeView, ViewMode.BYTECODE.name());

        constPoolView = new ConstPoolView(classEntry);
        cardPanel.add(constPoolView, ViewMode.CONSTPOOL.name());

        hexView = new HexView(classEntry);
        cardPanel.add(hexView, ViewMode.HEX.name());

        views.put(ViewMode.SOURCE, sourceView);
        views.put(ViewMode.BYTECODE, bytecodeView);
        views.put(ViewMode.CONSTPOOL, constPoolView);
        views.put(ViewMode.HEX, hexView);

        add(cardPanel, BorderLayout.CENTER);
    }

    private boolean isViewReady(ViewMode mode) {
        return views.containsKey(mode);
    }

    private <T extends JPanel> void loadViewInBackground(ViewMode mode, Supplier<T> viewFactory, Consumer<T> viewSetter) {
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
                    applySettingsToView((EditorView) view);

                    viewSetter.accept(view);
                    cardPanel.add(view, mode.name());
                    views.put(mode, (EditorView) view);
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

    private void applySettingsToView(EditorView view) {
        view.setFontSize(fontSize);
        view.setWordWrap(wordWrap);
    }

    private void ensureViewLoaded(ViewMode mode) {
        switch (mode) {
            case IR:
                if (irView == null) loadViewInBackground(mode, () -> new IRView(classEntry), v -> irView = v);
                break;
            case LLVM:
                if (llvmView == null) loadViewInBackground(mode, () -> new LLVMView(classEntry), v -> llvmView = v);
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
                if (callGraphView == null) loadViewInBackground(mode, () -> new CallGraphView(classEntry), v -> {
                    callGraphView = v;
                    if (projectModel != null) v.setProjectModel(projectModel);
                });
                break;
            case ATTRIBUTES:
                if (attributesView == null) loadViewInBackground(mode, () -> new AttributesView(classEntry), v -> attributesView = v);
                break;
            case STATISTICS:
                if (statisticsView == null) loadViewInBackground(mode, () -> new StatisticsView(classEntry), v -> statisticsView = v);
                break;
            case DUAL:
                if (dualView == null) loadViewInBackground(mode, () -> new DualView(classEntry), v -> {
                    dualView = v;
                    if (projectModel != null) v.setProjectModel(projectModel);
                });
                break;
            case LIVE_INSTANCES:
                if (liveInstancesView == null) loadViewInBackground(mode, () -> new LiveInstancesView(classEntry), v -> liveInstancesView = v);
                break;
            case LIVE_STATICS:
                if (liveStaticsView == null) loadViewInBackground(mode, () -> new LiveStaticsView(classEntry), v -> liveStaticsView = v);
                break;
            default:
                break;
        }
    }

    private void refreshView(ViewMode mode) {
        EditorView view = views.get(mode);
        if (view != null) {
            view.refresh();
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

    /**
     * Enable or disable usage-count lenses in the source view (and the dual view's source pane).
     */
    public void setUsageLensEnabled(boolean enabled) {
        sourceView.setUsageLensEnabled(enabled);
        if (dualView != null) {
            dualView.setUsageLensEnabled(enabled);
        }
    }

    public void refresh() {
        if (isViewReady(currentMode)) {
            refreshView(currentMode);
        }
    }

    /**
     * Forces a full reload after the underlying class was mutated externally (e.g. an AI rename or script run):
     * drops the decompilation cache and refreshes every instantiated view - including the source view, whose
     * {@code refresh()} otherwise re-displays the now-stale cached source - so nothing keeps showing old output.
     */
    public void reload() {
        classEntry.invalidateDecompilationCache();
        breadcrumbBar.setClass(classEntry);
        if (sourceView != null) sourceView.reload();
        if (bytecodeView != null) bytecodeView.refresh();
        if (constPoolView != null) constPoolView.refresh();
        if (hexView != null) hexView.reload();
        if (irView != null) irView.refresh();
        if (llvmView != null) llvmView.refresh();
        if (astView != null) astView.refresh();
        if (pdgView != null) pdgView.refresh();
        if (sdgView != null) sdgView.refresh();
        if (cpgView != null) cpgView.refresh();
        if (controlFlowView != null) controlFlowView.refresh();
        if (callGraphView != null) callGraphView.refresh();
        if (attributesView != null) attributesView.refresh();
        if (statisticsView != null) statisticsView.refresh();
        if (dualView != null) dualView.refresh();
    }

    private void onClassRecompiled() {
        breadcrumbBar.setClass(classEntry);
        if (bytecodeView != null) bytecodeView.refresh();
        if (constPoolView != null) constPoolView.refresh();
        if (hexView != null) hexView.refresh();
        if (irView != null) irView.refresh();
        if (astView != null) astView.refresh();
        if (pdgView != null) pdgView.refresh();
        if (sdgView != null) sdgView.refresh();
        if (cpgView != null) cpgView.refresh();
        if (controlFlowView != null) controlFlowView.refresh();
        if (callGraphView != null) callGraphView.refresh();
        if (attributesView != null) attributesView.refresh();
        if (statisticsView != null) statisticsView.refresh();
        if (dualView != null) dualView.refresh();
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
        EditorView view = views.get(currentMode);
        if (view != null) {
            view.copySelection();
        }
    }

    public String getText() {
        EditorView view = views.get(currentMode);
        return view != null ? view.getText() : "";
    }

    public void goToLine(int line) {
        EditorView view = views.get(currentMode);
        if (view != null) {
            view.goToLine(line);
        }
    }

    /**
     * Highlight a specific line, scroll to it, and place caret one line above.
     */
    public void highlightLine(int line) {
        EditorView view = views.get(currentMode);
        if (view != null) {
            view.highlightLine(currentMode == ViewMode.SOURCE ? line - 1 : line);
        }
    }

    public void showFindDialog() {
        EditorView view = views.get(currentMode);
        if (view != null) {
            view.showFindDialog();
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
        EditorView view = views.get(currentMode);
        return view != null ? view.getSelectedText() : null;
    }

    public void scrollToMethod(MethodEntryModel method) {
        String methodName = method.getMethodEntry().getName();
        String methodDesc = method.getMethodEntry().getDesc();
        switch (currentMode) {
            case SOURCE: sourceView.scrollToMethodDeclaration(methodName, methodDesc); break;
            case BYTECODE: bytecodeView.scrollToMethod(methodName, methodDesc); break;
            case DUAL: if (dualView != null) dualView.scrollToMethod(methodName, methodDesc); break;
            default: {
                EditorView view = views.get(currentMode);
                if (view != null) view.scrollToText(methodName);
                break;
            }
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
        for (EditorView view : views.values()) {
            view.setFontSize(size);
        }
    }

    /**
     * Set word wrap for all views.
     */
    public void setWordWrap(boolean enabled) {
        this.wordWrap = enabled;
        for (EditorView view : views.values()) {
            view.setWordWrap(enabled);
        }
    }

    /**
     * Set the project model for navigation features.
     */
    public void setProjectModel(ProjectModel projectModel) {
        this.projectModel = projectModel;
        sourceView.setProjectModel(projectModel);
        if (callGraphView != null) {
            callGraphView.setProjectModel(projectModel);
        }
        if (dualView != null) {
            dualView.setProjectModel(projectModel);
        }
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

    public boolean navigateToSourceOffset(String methodName, String methodDesc, int pc, String selectToken) {
        setViewMode(ViewMode.SOURCE);
        return sourceView.scrollToSourceOffset(methodName, methodDesc, pc, selectToken);
    }

    public boolean navigateToMethod(String methodName, String methodDesc) {
        switch (currentMode) {
            case BYTECODE: return bytecodeView.scrollToMethod(methodName, methodDesc);
            case DUAL: if (dualView != null) { dualView.scrollToMethod(methodName, methodDesc); return true; } return false;
            case HEX: return false;
            default: {
                EditorView view = views.get(currentMode);
                if (view != null) { view.scrollToText(methodName); return true; }
                return false;
            }
        }
    }

}
