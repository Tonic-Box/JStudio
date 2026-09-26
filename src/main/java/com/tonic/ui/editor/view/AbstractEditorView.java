package com.tonic.ui.editor.view;

import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.core.component.ThemedJPanel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** The base for editor tab content views: theme lifecycle, shared async-load scaffolding, and no-op defaults for the whole view contract so each view implements only what it supports. */
public abstract class AbstractEditorView extends ThemedJPanel implements EditorView
{

    protected boolean loaded = false;
    protected SwingWorker<?, ?> currentWorker;
    protected final LoadingOverlay loadingOverlay = new LoadingOverlay();

    protected AbstractEditorView()
    {
        super(BackgroundStyle.TERTIARY, new java.awt.BorderLayout());
    }

    protected AbstractEditorView(java.awt.LayoutManager layout)
    {
        super(BackgroundStyle.TERTIARY, layout);
    }

    protected final void cancelCurrentWorker()
    {
        if (currentWorker != null && !currentWorker.isDone())
        {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    protected final JPanel overlayWrap(JComponent content)
    {
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

    protected final void copyToClipboard(String text)
    {
        if (text != null && !text.isEmpty())
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    }

    @Override
    public void refresh()
    {
    }

    @Override
    public void reload()
    {
        loaded = false;
        refresh();
    }

    @Override
    public String getText()
    {
        return "";
    }

    @Override
    public void copySelection()
    {
    }

    @Override
    public String getSelectedText()
    {
        return null;
    }

    @Override
    public void goToLine(int line)
    {
    }

    @Override
    public void showFindDialog()
    {
    }

    @Override
    public void scrollToText(String text)
    {
    }

    @Override
    public void highlightLine(int line)
    {
        goToLine(line);
    }

    @Override
    public void setFontSize(int size)
    {
    }

    @Override
    public void setWordWrap(boolean enabled)
    {
    }
}
