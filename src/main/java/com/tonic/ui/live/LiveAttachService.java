package com.tonic.ui.live;

import com.tonic.event.EventBus;
import com.tonic.event.events.LiveSessionEvent;
import com.tonic.live.LiveSession;
import com.tonic.service.ProjectService;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Orchestrates a live JVM session for the UI: resolves the bundled pure-Java agent jar, attaches it to a
 * target, builds a fresh project from its loaded classes, and supports on-demand refresh and detach. Holds
 * the single active {@link LiveSession}. The Java agent works on any OS/arch with no native build.
 */
@Getter
public final class LiveAttachService {

    private static final LiveAttachService INSTANCE = new LiveAttachService();

    private LiveSession session;
    /**
     * -- GETTER --
     * Whether the active session is one auto-attached to a process launched by the Run feature.
     */
    @Getter
    private boolean runSession;

    private LiveAttachService() {
    }

    public static LiveAttachService getInstance() {
        return INSTANCE;
    }

    public boolean isAttached() {
        return session != null;
    }

    /**
     * Adopts an already-connected session for a JVM launched by the Run feature: holds it and lights up the
     * live features, but does NOT pull/replace the project (the running app is the current project). Call on
     * the EDT - it posts {@link LiveSessionEvent}, which toggles Swing docks.
     */
    public void adoptRunSession(LiveSession adopted) {
        detach();
        this.session = adopted;
        this.runSession = true;
        EventBus.getInstance().post(new LiveSessionEvent(this, true));
    }

    /**
     * Extracts the bundled Java agent ({@code agent/live-agent.bin}) from the classpath to a temp file (the
     * attach API needs a real path) and returns it. Reuses an already-extracted copy of the same size.
     * Falls back to the dev-tree jar. Returns null if not found.
     */
    public File resolveAgentJar() {
        try (InputStream in = LiveAttachService.class.getResourceAsStream("/agent/live-agent.bin")) {
            if (in == null) {
                File dev = new File("live-agent/build/libs/live-agent.jar").getAbsoluteFile();
                return dev.isFile() ? dev : null;
            }
            byte[] data = in.readAllBytes();
            File dir = new File(System.getProperty("java.io.tmpdir"), "jstudio-live");
            dir.mkdirs();
            File out = new File(dir, "live-agent.jar");
            if (!out.isFile() || out.length() != data.length) {
                Files.write(out.toPath(), data);
            }
            return out.isFile() ? out : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Attaches the Java agent to {@code pid} and builds a fresh project from its loaded classes. The opened
     * session is owned by this service (held in {@code session}, closed by {@link #detach()}); access it via
     * {@link #getSession()}.
     */
    public void attach(String pid, boolean includeJdk,
                       ProjectService.ProgressCallback progress) throws Exception {
        detach();
        File agent = resolveAgentJar();
        if (agent == null) {
            throw new IllegalStateException(
                    "Live agent jar not found. Rebuild JStudio so the agent is bundled (agent/live-agent.bin).");
        }
        LiveSession s = LiveSession.attach(pid, agent.getAbsolutePath());
        try {
            ProjectService.getInstance().loadLiveProject(s, includeJdk, progress);
            this.session = s;
            EventBus.getInstance().post(new LiveSessionEvent(this, true));
        } catch (Exception e) {
            s.close();
            throw e;
        }
    }

    /** Re-enumerates the target and pulls any classes loaded since attach/last refresh. */
    public int refresh(boolean includeJdk, ProjectService.ProgressCallback progress) throws Exception {
        if (session == null) {
            return 0;
        }
        return ProjectService.getInstance().refreshLiveProject(session, includeJdk, progress);
    }

    public void detach() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
            session = null;
            runSession = false;
            EventBus.getInstance().post(new LiveSessionEvent(this, false));
        }
    }
}
