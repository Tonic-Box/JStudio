package com.tonic.service.run;

import com.tonic.event.EventBus;
import com.tonic.event.events.RunStateEvent;

/**
 * Tracks the single in-flight {@link RunService} process so UI can reflect run state (e.g. the editor gutter shows
 * a stop badge while a run is active) and terminate it. Posts a {@link RunStateEvent} on every change. Singleton.
 */
public final class RunStateService {

    private static final RunStateService INSTANCE = new RunStateService();

    private Process process;

    private RunStateService() {
    }

    public static RunStateService getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /** Records the active run process and notifies listeners. */
    public void setProcess(Process process) {
        synchronized (this) {
            this.process = process;
        }
        EventBus.getInstance().post(new RunStateEvent(this, process != null && process.isAlive()));
    }

    /** Clears the active process when {@code exited} is still the current one (ignores a superseded run's exit). */
    public void clearIf(Process exited) {
        synchronized (this) {
            if (process != exited) {
                return;
            }
            process = null;
        }
        EventBus.getInstance().post(new RunStateEvent(this, false));
    }

    /** Forcibly terminates the active run process, if any. */
    public void terminate() {
        Process active;
        synchronized (this) {
            active = process;
        }
        RunService.terminate(active);
    }
}
