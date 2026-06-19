package com.tonic.plugin.context;

import com.tonic.event.EventBus;
import com.tonic.event.events.ProjectRenamedEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassPool;
import com.tonic.plugin.api.RefactorApi;
import com.tonic.renamer.Renamer;
import com.tonic.renamer.exception.RenameException;
import com.tonic.service.ProjectService;

import java.util.ArrayList;
import java.util.List;

/**
 * Host-side {@link RefactorApi}: applies class/method/field renames with YABR's {@link Renamer} (which rewrites every
 * reference across the {@link ClassPool}), updates the {@link ProjectModel}, invalidates the affected class's
 * decompile cache, and posts a {@link ProjectRenamedEvent} so {@code MainFrame} refreshes the UI. Resolves the
 * current project live on each call (like {@code ScriptApiImpl}); called off the EDT (the chat worker thread).
 */
public class RefactorApiImpl implements RefactorApi {

    @Override
    public RenameResult renameClass(String oldName, String newName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            return fail("No project is loaded.");
        }
        if (isBlank(newName)) {
            return fail("Provide a new class name.");
        }
        String oldInternal = normalize(oldName);
        String newInternal = normalize(newName);
        if (oldInternal.equals(newInternal)) {
            return fail("The new name is the same as the old name.");
        }
        if (project.getClass(oldInternal) == null) {
            return fail("Class not found: " + oldName + " (use the internal or dotted class name).");
        }
        if (project.getClass(newInternal) != null) {
            return fail("A class named " + dotted(newInternal) + " already exists.");
        }
        try {
            new Renamer(project.getClassPool()).mapClass(oldInternal, newInternal).apply();
        } catch (RenameException e) {
            return fail("Rename failed: " + e.getMessage());
        }
        project.notifyClassRenamed(oldInternal, newInternal);
        ClassEntryModel renamed = project.getClass(newInternal);
        if (renamed != null) {
            renamed.invalidateDecompilationCache();
        }
        EventBus.getInstance().post(
                new ProjectRenamedEvent(this, ProjectRenamedEvent.Kind.CLASS, oldInternal, newInternal, null));
        return ok("Renamed class " + dotted(oldInternal) + " -> " + dotted(newInternal)
                + " (all references updated).");
    }

    @Override
    public RenameResult renameMethod(String className, String name, String descriptor, String newName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            return fail("No project is loaded.");
        }
        if (isBlank(name) || isBlank(newName)) {
            return fail("Provide the method name and the new name.");
        }
        String internal = normalize(className);
        ClassEntryModel cls = project.getClass(internal);
        if (cls == null) {
            return fail("Class not found: " + className + " (use the internal or dotted class name).");
        }
        List<String> descs = new ArrayList<>();
        for (MethodEntryModel m : cls.getMethods()) {
            if (name.equals(m.getName())) {
                descs.add(m.getMethodEntry().getDesc());
            }
        }
        if (descs.isEmpty()) {
            return fail("Method not found: " + name + " in " + dotted(internal) + ".");
        }
        String desc = descriptor;
        if (isBlank(desc)) {
            if (descs.size() > 1) {
                return fail("Method '" + name + "' is overloaded in " + dotted(internal)
                        + "; pass a descriptor. Candidates: " + String.join(", ", descs));
            }
            desc = descs.get(0);
        } else if (!descs.contains(desc)) {
            return fail("No method " + name + desc + " in " + dotted(internal) + ".");
        }
        try {
            new Renamer(project.getClassPool()).mapMethod(internal, name, desc, newName).apply();
        } catch (RenameException e) {
            return fail("Rename failed: " + e.getMessage());
        }
        cls.invalidateDecompilationCache();
        EventBus.getInstance().post(
                new ProjectRenamedEvent(this, ProjectRenamedEvent.Kind.METHOD, internal, internal, name));
        return ok("Renamed method " + name + desc + " -> " + newName + " in " + dotted(internal)
                + " (all call sites updated).");
    }

    @Override
    public RenameResult renameField(String className, String name, String descriptor, String newName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            return fail("No project is loaded.");
        }
        if (isBlank(name) || isBlank(newName)) {
            return fail("Provide the field name and the new name.");
        }
        String internal = normalize(className);
        ClassEntryModel cls = project.getClass(internal);
        if (cls == null) {
            return fail("Class not found: " + className + " (use the internal or dotted class name).");
        }
        List<String> descs = new ArrayList<>();
        for (FieldEntryModel f : cls.getFields()) {
            if (name.equals(f.getName())) {
                descs.add(f.getFieldEntry().getDesc());
            }
        }
        if (descs.isEmpty()) {
            return fail("Field not found: " + name + " in " + dotted(internal) + ".");
        }
        String desc = descriptor;
        if (isBlank(desc)) {
            desc = descs.get(0);
        } else if (!descs.contains(desc)) {
            return fail("No field " + name + " " + desc + " in " + dotted(internal) + ".");
        }
        try {
            new Renamer(project.getClassPool()).mapField(internal, name, desc, newName).apply();
        } catch (RenameException e) {
            return fail("Rename failed: " + e.getMessage());
        }
        cls.invalidateDecompilationCache();
        EventBus.getInstance().post(
                new ProjectRenamedEvent(this, ProjectRenamedEvent.Kind.FIELD, internal, internal, name));
        return ok("Renamed field " + name + " -> " + newName + " in " + dotted(internal)
                + " (all accesses updated).");
    }

    private static RenameResult ok(String message) {
        return new RenameResult(true, message);
    }

    private static RenameResult fail(String message) {
        return new RenameResult(false, message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalize(String name) {
        return name == null ? null : name.trim().replace('.', '/');
    }

    private static String dotted(String internal) {
        return internal == null ? null : internal.replace('/', '.');
    }
}
