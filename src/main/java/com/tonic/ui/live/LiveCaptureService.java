package com.tonic.ui.live;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveEvent;
import com.tonic.service.ProjectService;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Arms runtime class-load capture on a live session and feeds each captured class (a CLASS_LOADED event
 * carrying real bytes) into the live project. This surfaces runtime-generated classes - packer output,
 * {@code defineHiddenClass}, ASM-emitted glue - that never existed on disk.
 *
 * <p>Threading: the capture event arrives on the client's reader thread, which must not issue protocol
 * requests. Adding a class uses only the bytes already delivered (no request), but ClassFile parsing is
 * offloaded to a single-thread executor to keep the reader thread responsive.
 */
public final class LiveCaptureService {

    private final LiveSession session;
    private final Consumer<LiveEvent> hook = this::onEvent;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "live-capture");
                t.setDaemon(true);
                return t;
            });
    private volatile Consumer<String> onCaptured;
    @Getter
    private volatile boolean armed;

    public LiveCaptureService(LiveSession session) {
        this.session = session;
    }

    /** Optional callback (internal class name) invoked after a captured class is added to the project. */
    public void setOnCaptured(Consumer<String> onCaptured) {
        this.onCaptured = onCaptured;
    }

    /** Begin streaming runtime class loads into the project. */
    public void arm() throws Exception {
        if (armed) {
            return;
        }
        session.addEventListener(hook);
        session.setCaptureLoads(true);
        armed = true;
    }

    /** Stop streaming runtime class loads. */
    public void disarm() {
        if (!armed) {
            return;
        }
        armed = false;
        try {
            session.setCaptureLoads(false);
        } catch (Exception ignored) {
        }
        session.removeEventListener(hook);
    }

    public void dispose() {
        disarm();
        worker.shutdownNow();
    }

    private void onEvent(LiveEvent e) {
        if (e.getKind() != LiveEvent.Kind.CLASS_LOADED) {
            return;
        }
        final String name = e.getClassName();
        final byte[] bytes = e.getClassBytes();
        worker.submit(() -> {
            if (ProjectService.getInstance().addCapturedLiveClass(name, bytes) != null) {
                Consumer<String> cb = onCaptured;
                if (cb != null) {
                    cb.accept(name);
                }
            }
        });
    }
}
