package com.tonic.script.bridge;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.ContentionEdge;
import com.tonic.live.protocol.ThreadInfo;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.script.engine.ScriptFunction;
import com.tonic.script.engine.ScriptInterpreter;
import com.tonic.script.engine.ScriptValue;
import com.tonic.service.ProjectService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the attached live JVM to scripts as the global {@code live} object. Operations are synchronous
 * protocol calls (run on the script thread). The Java agent supports thread enumeration, deadlock
 * detection, runtime class capture, and live patch (redefine).
 *
 * <p>Example: {@code let dl = live.deadlocks(); live.redefineFromProject("com/foo/Bar");}
 */
public final class LiveBridge extends AbstractBridge {

    private final LiveSession session;

    public LiveBridge(ScriptInterpreter interpreter, LiveSession session) {
        super(interpreter, null);
        this.session = session;
    }

    @Override
    public ScriptValue createBridgeObject() {
        Map<String, ScriptValue> p = new HashMap<>();
        p.put("threads", ScriptValue.function(ScriptFunction.native0("threads", this::threads)));
        p.put("deadlocks", ScriptValue.function(ScriptFunction.native0("deadlocks", this::deadlocks)));
        p.put("captureLoads", ScriptValue.function(ScriptFunction.native1("captureLoads", this::captureLoads)));
        p.put("redefineFromProject", ScriptValue.function(
                ScriptFunction.native1("redefineFromProject", this::redefineFromProject)));
        return ScriptValue.object(p);
    }

    private ScriptValue threads() {
        try {
            List<ScriptValue> out = new ArrayList<>();
            for (ThreadInfo t : session.getThreads()) {
                Map<String, ScriptValue> tm = new HashMap<>();
                tm.put("id", ScriptValue.number(t.getId()));
                tm.put("name", ScriptValue.string(t.getName()));
                tm.put("state", ScriptValue.number(t.getState()));
                out.add(ScriptValue.object(tm));
            }
            return ScriptValue.array(out);
        } catch (Exception e) {
            log("threads failed: " + e.getMessage());
            return ScriptValue.NULL;
        }
    }

    private ScriptValue deadlocks() {
        try {
            List<ContentionEdge> edges = session.getContention();
            List<List<ContentionEdge>> cycles = com.tonic.live.Deadlocks.find(edges);
            List<ScriptValue> out = new ArrayList<>();
            for (List<ContentionEdge> cycle : cycles) {
                List<ScriptValue> ring = new ArrayList<>();
                for (ContentionEdge e : cycle) {
                    ring.add(ScriptValue.string(e.toString()));
                }
                out.add(ScriptValue.array(ring));
            }
            return ScriptValue.array(out);
        } catch (Exception e) {
            log("deadlocks failed: " + e.getMessage());
            return ScriptValue.NULL;
        }
    }

    private ScriptValue captureLoads(ScriptValue onVal) {
        try {
            session.setCaptureLoads(onVal.asBoolean());
            return ScriptValue.bool(true);
        } catch (Exception e) {
            log("captureLoads failed: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }

    /** Pushes the current project's (possibly edited) bytes for {@code internalName} to the live JVM. */
    private ScriptValue redefineFromProject(ScriptValue clsVal) {
        try {
            String internalName = clsVal.asString();
            ProjectModel project = ProjectService.getInstance().getCurrentProject();
            ClassEntryModel entry = project == null ? null : project.getClass(internalName);
            if (entry == null) {
                log("redefineFromProject: class not in project: " + internalName);
                return ScriptValue.bool(false);
            }
            session.redefineClass(internalName, entry.getClassFile().write());
            return ScriptValue.bool(true);
        } catch (Exception e) {
            log("redefineFromProject failed: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }
}
