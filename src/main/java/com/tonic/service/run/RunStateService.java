package com.tonic.service.run;

import com.tonic.event.EventBus;
import com.tonic.event.events.RunStateEvent;

/** The singleton tracker of the one in-flight run process; posts a RunStateEvent on every change. */
public final class RunStateService
{

    private static final RunStateService INSTANCE = new RunStateService();

    private Process process;

    private RunStateService()
    {
    }

    /** @return the shared instance */
    public static RunStateService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Reports whether a run is in progress.
     *
     * @return true if a run process is set and still alive
     */
    public synchronized boolean isRunning()
    {
        return process != null && process.isAlive();
    }

    /**
     * Records the active run process and notifies listeners.
     *
     * @param process the new run process, or null for none
     */
    public void setProcess(Process process)
    {
        synchronized (this)
        {
            this.process = process;
        }
        EventBus.getInstance().post(new RunStateEvent(this, process != null && process.isAlive()));
    }

    /**
     * Clears the active process if it is still the one that exited, so a superseded run's exit is ignored.
     *
     * @param exited the process that exited
     */
    public void clearIf(Process exited)
    {
        synchronized (this)
        {
            if (process != exited)
            {
                return;
            }
            process = null;
        }
        EventBus.getInstance().post(new RunStateEvent(this, false));
    }

    /** Forcibly terminates the active run process, if any. */
    public void terminate()
    {
        Process active;
        synchronized (this)
        {
            active = process;
        }
        RunService.terminate(active);
    }
}
