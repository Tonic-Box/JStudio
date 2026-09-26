package com.tonic.ui.live;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveEvent;
import com.tonic.service.ProjectService;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Streams classes the target loads at runtime into the live project, so generated classes that never existed on disk show up. */
public final class LiveCaptureService
{

    private final LiveSession session;
    private final Consumer<LiveEvent> hook = this::onEvent;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r ->
            {
                Thread t = new Thread(r, "live-capture");
                t.setDaemon(true);
                return t;
            });
    private volatile Consumer<String> onCaptured;
    @Getter
    private volatile boolean armed;

    /**
     * Creates a disarmed capture service.
     *
     * @param session the session whose class loads are captured
     */
    public LiveCaptureService(LiveSession session)
    {
        this.session = session;
    }

    /**
     * Sets the callback run after a captured class is added to the project.
     *
     * @param onCaptured receives the captured class's internal name, or null for none
     */
    public void setOnCaptured(Consumer<String> onCaptured)
    {
        this.onCaptured = onCaptured;
    }

    /**
     * Starts streaming runtime class loads into the project; does nothing if already armed.
     *
     * @throws Exception if the target cannot be told to capture loads
     */
    public void arm() throws Exception
    {
        if (armed)
        {
            return;
        }
        session.addEventListener(hook);
        session.setCaptureLoads(true);
        armed = true;
    }

    /** Stops streaming runtime class loads; does nothing if not armed. */
    public void disarm()
    {
        if (!armed)
        {
            return;
        }
        armed = false;
        try
        {
            session.setCaptureLoads(false);
        }
        catch (Exception ignored)
        {
        }
        session.removeEventListener(hook);
    }

    /** Disarms capture and stops the parsing worker. */
    public void dispose()
    {
        disarm();
        worker.shutdownNow();
    }

    private void onEvent(LiveEvent e)
    {
        if (e.getKind() != LiveEvent.Kind.CLASS_LOADED)
        {
            return;
        }
        final String name = e.getClassName();
        final byte[] bytes = e.getClassBytes();
        worker.submit(() ->
        {
            if (ProjectService.getInstance().addCapturedLiveClass(name, bytes) != null)
            {
                Consumer<String> cb = onCaptured;
                if (cb != null)
                {
                    cb.accept(name);
                }
            }
        });
    }
}
