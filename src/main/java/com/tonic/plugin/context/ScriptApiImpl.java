package com.tonic.plugin.context;

import com.tonic.event.EventBus;
import com.tonic.event.events.ScriptConsoleEvent;
import com.tonic.event.events.ScriptWrittenEvent;
import com.tonic.live.LiveSession;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.ScriptApi;
import com.tonic.script.engine.Script;
import com.tonic.script.engine.ScriptRunner;
import com.tonic.script.store.ScriptStore;
import com.tonic.service.ProjectService;
import com.tonic.ui.live.LiveAttachService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Host-side {@link ScriptApi}: persists scripts via {@link ScriptStore}, runs them via {@link ScriptRunner}, and
 * drives the UI (editor refresh, bottom Script Console tab) through {@link EventBus} so it needs no {@code MainFrame}
 * handle. Called off the EDT (the chat worker thread); the {@code MainFrame} event handlers marshal UI work.
 */
public class ScriptApiImpl implements ScriptApi {

    @Override
    public String write(String name, String mode, String content) {
        Script script = new Script(safeName(name), parseMode(mode, content), content == null ? "" : content);
        try {
            ScriptStore.saveToUserDirectory(script);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save script: " + e.getMessage(), e);
        }
        EventBus.getInstance().post(new ScriptWrittenEvent(this, script.getName()));
        return script.getName();
    }

    @Override
    public List<ScriptInfo> list() {
        List<ScriptInfo> out = new ArrayList<>();
        for (Script s : ScriptStore.loadUserScripts()) {
            out.add(new ScriptInfo(s.getName(), s.getMode().name().toLowerCase(), s.getDescription()));
        }
        return out;
    }

    @Override
    public String read(String name) {
        Script s = findByName(name);
        return s == null ? null : s.getContent();
    }

    @Override
    public RunResult run(String name, String content, String mode, String scope,
                         String className, String methodName, String methodDescriptor) {
        String source;
        Script.Mode runMode;
        if (name != null && !name.isBlank()) {
            Script saved = findByName(name);
            if (saved == null) {
                return new RunResult(0, "No saved script named \"" + name + "\".", true);
            }
            source = saved.getContent();
            runMode = saved.getMode();
        } else if (content != null && !content.isBlank()) {
            source = content;
            runMode = parseMode(mode, content);
        } else {
            return new RunResult(0, "Provide a saved script name or inline content to run.", true);
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return new RunResult(0, "No project is loaded.", true);
        }
        LiveSession live = LiveAttachService.getInstance().getSession();

        String scopeNorm = scope == null || scope.isBlank() ? "all" : scope.trim().toLowerCase();
        ScriptRunner.Scope runScope = ScriptRunner.Scope.ALL;
        ClassEntryModel targetClass = null;
        MethodEntryModel targetMethod = null;
        if ("class".equals(scopeNorm) || "method".equals(scopeNorm)) {
            targetClass = project.getClass(className == null ? null : className.replace('.', '/'));
            if (targetClass == null) {
                return new RunResult(0, "Class not found: " + className
                        + " (use the internal name, e.g. com/foo/Bar).", true);
            }
            if ("method".equals(scopeNorm)) {
                runScope = ScriptRunner.Scope.METHOD;
                targetMethod = findMethod(targetClass, methodName, methodDescriptor);
                if (targetMethod == null) {
                    return new RunResult(0, "Method not found: " + methodName
                            + (methodDescriptor == null ? "" : methodDescriptor) + " in " + className, true);
                }
            } else {
                runScope = ScriptRunner.Scope.CLASS;
            }
        }

        String label = name != null && !name.isBlank() ? name : "inline script";
        EventBus bus = EventBus.getInstance();
        bus.post(new ScriptConsoleEvent(this, ScriptConsoleEvent.Kind.START,
                "Running " + label + " (" + runMode.name() + ", scope=" + scopeNorm + ")", 0));

        StringBuilder buf = new StringBuilder();
        boolean[] sawError = {false};
        int mods = ScriptRunner.run(source, runMode, project, live, runScope, targetClass, targetMethod, line -> {
            buf.append(line);
            if (line.startsWith("ERROR") || line.contains("error:")) {
                sawError[0] = true;
            }
            bus.post(new ScriptConsoleEvent(this, ScriptConsoleEvent.Kind.LINE, line, 0));
        });

        bus.post(new ScriptConsoleEvent(this, ScriptConsoleEvent.Kind.DONE, label, mods));
        return new RunResult(mods, buf.toString(), sawError[0]);
    }

    private static Script findByName(String name) {
        if (name == null) {
            return null;
        }
        for (Script s : ScriptStore.loadUserScripts()) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        return null;
    }

    private static MethodEntryModel findMethod(ClassEntryModel cls, String methodName, String descriptor) {
        if (methodName == null) {
            return null;
        }
        for (MethodEntryModel m : cls.getMethods()) {
            if (methodName.equals(m.getMethodEntry().getName())
                    && (descriptor == null || descriptor.isBlank()
                        || descriptor.equals(m.getMethodEntry().getDesc()))) {
                return m;
            }
        }
        return null;
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "AI Script" : name.trim();
    }

    private static Script.Mode parseMode(String mode, String content) {
        if (mode != null && !mode.isBlank()) {
            switch (mode.trim().toLowerCase()) {
                case "ir": return Script.Mode.IR;
                case "both": return Script.Mode.BOTH;
                case "ast": return Script.Mode.AST;
                default: break;
            }
        }
        return Script.parseModeFromContent(content);
    }
}
