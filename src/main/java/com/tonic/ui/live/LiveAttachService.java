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

/** The single live JVM session: attaches the bundled Java agent, loads the target's classes as a project, and refreshes or detaches it. */
@Getter
public final class LiveAttachService
{

    private static final LiveAttachService INSTANCE = new LiveAttachService();

    private LiveSession session;
    /**
     * -- GETTER --
     * @return whether the active session was auto-attached to a process launched by Run
     */
    @Getter
    private boolean runSession;

    private LiveAttachService()
    {
    }

    /** @return the single instance */
    public static LiveAttachService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Tells whether a session is attached.
     *
     * @return true while a session is held
     */
    public boolean isAttached()
    {
        return session != null;
    }

    /**
     * Holds a session already connected to a process launched by Run and turns the live features on, keeping the current project; call on the EDT.
     *
     * @param adopted the connected session
     */
    public void adoptRunSession(LiveSession adopted)
    {
        detach();
        this.session = adopted;
        this.runSession = true;
        EventBus.getInstance().post(new LiveSessionEvent(this, true));
    }

    /**
     * Extracts the bundled agent jar to a temporary file, reusing an earlier copy of the same size, or falls back to the development build.
     *
     * @return the agent jar, or null where none is found
     */
    public File resolveAgentJar()
    {
        try (InputStream in = LiveAttachService.class.getResourceAsStream("/agent/live-agent.bin"))
        {
            if (in == null)
            {
                File dev = new File("live-agent/build/libs/live-agent.jar").getAbsoluteFile();
                return dev.isFile() ? dev : null;
            }
            byte[] data = in.readAllBytes();
            File dir = new File(System.getProperty("java.io.tmpdir"), "jstudio-live");
            dir.mkdirs();
            File out = new File(dir, "live-agent.jar");
            if (!out.isFile() || out.length() != data.length)
            {
                Files.write(out.toPath(), data);
            }
            return out.isFile() ? out : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /**
     * Attaches the agent to a JVM and loads its classes as a new project, replacing any earlier session.
     *
     * @param pid the target process id
     * @param includeJdk whether to load JDK classes too
     * @param progress told how loading goes
     * @throws IllegalStateException if the agent jar cannot be found
     * @throws Exception if attaching or loading fails
     */
    public void attach(String pid, boolean includeJdk, ProjectService.ProgressCallback progress) throws Exception
    {
        detach();
        File agent = resolveAgentJar();
        if (agent == null)
        {
            throw new IllegalStateException("Live agent jar not found. Rebuild JStudio so the agent is bundled (agent/live-agent.bin).");
        }
        LiveSession s = LiveSession.attach(pid, agent.getAbsolutePath());
        try
        {
            ProjectService.getInstance().loadLiveProject(s, includeJdk, progress);
            this.session = s;
            EventBus.getInstance().post(new LiveSessionEvent(this, true));
        }
        catch (Exception e)
        {
            s.close();
            throw e;
        }
    }

    /**
     * Loads the classes the target has loaded since the last attach or refresh.
     *
     * @param includeJdk whether to load JDK classes too
     * @param progress told how loading goes
     * @return the number of classes added, or 0 where no session is attached
     * @throws Exception if loading fails
     */
    public int refresh(boolean includeJdk, ProjectService.ProgressCallback progress) throws Exception
    {
        if (session == null)
        {
            return 0;
        }
        return ProjectService.getInstance().refreshLiveProject(session, includeJdk, progress);
    }

    /** Closes the session and turns the live features off; safe to call when nothing is attached. */
    public void detach()
    {
        if (session != null)
        {
            try
            {
                session.close();
            }
            catch (Exception ignored)
            {
            }
            session = null;
            runSession = false;
            EventBus.getInstance().post(new LiveSessionEvent(this, false));
        }
    }
}
