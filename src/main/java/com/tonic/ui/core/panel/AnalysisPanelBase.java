package com.tonic.ui.core.panel;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJTextArea;
import com.tonic.ui.core.util.ErrorHandler;
import com.tonic.ui.core.util.LayoutHelper;
import com.tonic.ui.model.ProjectModel;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public abstract class AnalysisPanelBase extends ThemedJPanel {

    protected final ProjectModel project;
    protected JPanel toolbarPanel;
    protected ThemedJTextArea statusArea;
    private SwingWorker<?, ?> currentWorker;

    protected AnalysisPanelBase(ProjectModel project) {
        this(project, BackgroundStyle.SECONDARY);
    }

    protected AnalysisPanelBase(ProjectModel project, BackgroundStyle style) {
        super(style, new BorderLayout());
        this.project = project;
        initializeLayout();
    }

    private void initializeLayout() {
        toolbarPanel = LayoutHelper.createToolbarWithBorder();
        buildToolbar(toolbarPanel);
        add(toolbarPanel, BorderLayout.NORTH);

        JPanel contentPanel = createContentPanel();
        add(contentPanel, BorderLayout.CENTER);

        if (hasStatusArea()) {
            statusArea = LayoutHelper.createStatusArea(3);
            add(LayoutHelper.createScrollPane(statusArea), BorderLayout.SOUTH);
        }
    }

    protected abstract JPanel createContentPanel();

    protected abstract void buildToolbar(JPanel toolbar);

    public abstract void refresh();

    protected boolean hasStatusArea() {
        return false;
    }

    protected void updateStatus(String message) {
        if (statusArea != null) {
            SwingUtilities.invokeLater(() -> {
                statusArea.append(message + "\n");
                statusArea.setCaretPosition(statusArea.getDocument().getLength());
            });
        }
    }

    protected void clearStatus() {
        if (statusArea != null) {
            SwingUtilities.invokeLater(() -> statusArea.setText(""));
        }
    }

    protected <T> void runAsync(String loadingMessage, Callable<T> task, Consumer<T> onSuccess) {
        runAsync(loadingMessage, task, onSuccess, this::handleError);
    }

    protected <T> void runAsync(String loadingMessage, Callable<T> task,
                                 Consumer<T> onSuccess, Consumer<Exception> onError) {
        cancelCurrentWorker();

        if (loadingMessage != null) {
            updateStatus(loadingMessage);
        }

        currentWorker = new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                try {
                    if (!isCancelled()) {
                        T result = get();
                        onSuccess.accept(result);
                    }
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        };
        currentWorker.execute();
    }

    protected void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    protected void handleError(Exception e) {
        ErrorHandler.handle(e, getClass().getSimpleName());
        updateStatus("Error: " + e.getMessage());
    }

    protected ProjectModel getProject() {
        return project;
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        cancelCurrentWorker();
    }
}
