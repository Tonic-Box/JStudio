package com.tonic.ui.editor.view;

import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.core.component.ThemedJPanel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Base for editor tab content views. Provides the theme lifecycle (via {@link ThemedJPanel}: register in ctor,
 * unregister in {@code removeNotify}, re-theme through {@code applyChildThemes}), the shared async-load scaffolding
 * ({@link #loadingOverlay}, the {@link #loaded} flag, {@link #cancelCurrentWorker()}, {@link #overlayWrap}), and
 * no-op/fallback defaults for the whole {@link EditorView} contract so views only implement what they support.
 */
public abstract class AbstractEditorView extends ThemedJPanel implements EditorView {

    protected boolean loaded = false;
    protected SwingWorker<?, ?> currentWorker;
    protected final LoadingOverlay loadingOverlay = new LoadingOverlay();

    protected AbstractEditorView() {
        super(BackgroundStyle.TERTIARY, new java.awt.BorderLayout());
    }

    protected AbstractEditorView(java.awt.LayoutManager layout) {
        super(BackgroundStyle.TERTIARY, layout);
    }

    /** Cancels any in-flight load worker and hides the loading spinner. */
    protected final void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    /** Wraps {@code content} and the loading overlay in a centered {@link OverlayLayout} panel. */
    protected final JPanel overlayWrap(JComponent content) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new OverlayLayout(wrapper));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        content.setAlignmentX(0.5f);
        content.setAlignmentY(0.5f);
        wrapper.add(loadingOverlay);
        wrapper.add(content);
        return wrapper;
    }

    /** Copies {@code text} to the system clipboard when non-empty. */
    protected final void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    }

    // ---- EditorView defaults (views override only what they support) ----

    @Override
    public void refresh() {
    }

    @Override
    public void reload() {
        loaded = false;
        refresh();
    }

    @Override
    public String getText() {
        return "";
    }

    @Override
    public void copySelection() {
    }

    @Override
    public String getSelectedText() {
        return null;
    }

    @Override
    public void goToLine(int line) {
    }

    @Override
    public void showFindDialog() {
    }

    @Override
    public void scrollToText(String text) {
    }

    @Override
    public void highlightLine(int line) {
        goToLine(line);
    }

    @Override
    public void setFontSize(int size) {
    }

    @Override
    public void setWordWrap(boolean enabled) {
    }
}
