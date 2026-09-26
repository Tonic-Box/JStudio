package com.tonic.ui.core;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Helper for running work off the EDT and then handing its result or failure back to the EDT. */
public final class SwingWorkers
{

    private SwingWorkers()
    {
    }

    /**
     * Runs work on a background thread, then calls exactly one of the two callbacks on the EDT.
     *
     * @param <T> the result type
     * @param work the work to run
     * @param onSuccess receives the work's result
     * @param onError receives what the work threw, unwrapped from the execution exception
     */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError)
    {
        new SwingWorker<T, Void>()
        {
            @Override
            protected T doInBackground() throws Exception
            {
                return work.call();
            }

            @Override
            protected void done()
            {
                T value;
                try
                {
                    value = get();
                }
                catch (Exception e)
                {
                    onError.accept(e.getCause() != null ? e.getCause() : e);
                    return;
                }
                onSuccess.accept(value);
            }
        }.execute();
    }
}
