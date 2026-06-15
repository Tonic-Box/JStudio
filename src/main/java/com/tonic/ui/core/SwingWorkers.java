package com.tonic.ui.core;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Small helper for the common "run something off the EDT, then update the UI" pattern, removing the
 * repeated {@link SwingWorker} subclass boilerplate. {@code work} runs on a background thread; exactly one
 * of {@code onSuccess}/{@code onError} is then invoked on the EDT (the error receives the unwrapped cause).
 */
public final class SwingWorkers {

    private SwingWorkers() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                T value;
                try {
                    value = get();
                } catch (Exception e) {
                    onError.accept(e.getCause() != null ? e.getCause() : e);
                    return;
                }
                onSuccess.accept(value);
            }
        }.execute();
    }
}
