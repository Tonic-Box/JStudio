package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.event.EventBus;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.event.events.StatusMessageEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.XrefQueryService;
import com.tonic.util.Settings;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.SwingWorker;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Owns the editor's usage-count "lens" overlay: it recomputes per-member usage counts on a background worker (using
 * the same xref database Find Usages uses), supplies the painting for the host view's {@code paintComponent}, and
 * opens Find Usages when a lens is clicked. Recompute is skipped/cleared when lenses are off, annotations are
 * filtered (line numbers shift), the source is dirty, or no project/span data is available.
 */
final class UsageLensController {

    private final RSyntaxTextArea textArea;
    private final ClassEntryModel classEntry;
    private final BooleanSupplier omitAnnotations;
    private final BooleanSupplier dirty;
    private final Supplier<ProjectModel> projectModel;
    private final UsageLensOverlay lensOverlay = new UsageLensOverlay();
    private boolean enabled = Settings.getInstance().isUsageLensEnabled();
    private int generation;

    UsageLensController(RSyntaxTextArea textArea, ClassEntryModel classEntry, BooleanSupplier omitAnnotations,
                        BooleanSupplier dirty, Supplier<ProjectModel> projectModel) {
        this.textArea = textArea;
        this.classEntry = classEntry;
        this.omitAnnotations = omitAnnotations;
        this.dirty = dirty;
        this.projectModel = projectModel;
        installMouseHandling();
    }

    /** Paints the lens entries onto the editor's text area; call from the host view's {@code paintComponent}. */
    void paint(Graphics2D g) {
        lensOverlay.paint(g, textArea);
    }

    /** Clears the overlay entries (does not repaint). */
    void clear() {
        lensOverlay.clear();
    }

    /** Enables or disables the usage-count lenses, recomputing or clearing them immediately. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        scheduleUpdate();
    }

    void scheduleUpdate() {
        int gen = ++generation;
        Map<String, DecompileResult.MethodSpan> methodSpans = classEntry.getMethodSpans();
        Map<String, DecompileResult.MemberSpan> fieldSpans = classEntry.getFieldSpans();
        DecompileResult.MemberSpan classSpan = classEntry.getClassSpan();
        if (!enabled || omitAnnotations.getAsBoolean() || dirty.getAsBoolean()
                || projectModel.get() == null || methodSpans == null) {
            lensOverlay.clear();
            textArea.repaint();
            return;
        }
        String className = classEntry.getClassName();
        ProjectModel project = projectModel.get();
        List<MethodEntryModel> methods = classEntry.getMethods();
        List<FieldEntryModel> fields = classEntry.getFields();
        new SwingWorker<List<UsageLens.LensTarget>, Void>() {
            @Override
            protected List<UsageLens.LensTarget> doInBackground() {
                if (project.getXrefDatabase() == null || project.getXrefDatabase().isEmpty()) {
                    EventBus.getInstance().post(new StatusMessageEvent(this, "Building cross-reference database..."));
                    XrefQueryService.ensureDatabase(project);
                    EventBus.getInstance().post(new StatusMessageEvent(this, "Cross-reference database ready."));
                }
                List<UsageLens.LensTarget> targets = new ArrayList<>();
                for (MethodEntryModel method : methods) {
                    DecompileResult.MemberSpan span = methodSpans.get(method.getName() + method.getDescriptor());
                    if (span != null) {
                        int count = XrefQueryService.getUsages(project, FindUsagesEvent.TargetType.METHOD,
                                className, method.getName(), method.getDescriptor()).size();
                        targets.add(new UsageLens.LensTarget(FindUsagesEvent.TargetType.METHOD,
                                method.getName(), method.getDescriptor(), span, count));
                    }
                }
                if (fieldSpans != null) {
                    for (FieldEntryModel field : fields) {
                        DecompileResult.MemberSpan span = fieldSpans.get(field.getName() + field.getDescriptor());
                        if (span != null) {
                            int count = XrefQueryService.getUsages(project, FindUsagesEvent.TargetType.FIELD,
                                    className, field.getName(), field.getDescriptor()).size();
                            targets.add(new UsageLens.LensTarget(FindUsagesEvent.TargetType.FIELD,
                                    field.getName(), field.getDescriptor(), span, count));
                        }
                    }
                }
                if (classSpan != null) {
                    int count = XrefQueryService.getUsages(project, FindUsagesEvent.TargetType.CLASS,
                            className, null, null).size();
                    targets.add(new UsageLens.LensTarget(FindUsagesEvent.TargetType.CLASS,
                            className, null, classSpan, count));
                }
                return targets;
            }

            @Override
            protected void done() {
                if (gen != generation || dirty.getAsBoolean()) {
                    return;
                }
                try {
                    List<UsageLens.LensTarget> targets = get();
                    String[] lines = textArea.getText().split("\n", -1);
                    lensOverlay.setEntries(UsageLens.compute(lines, targets));
                    textArea.repaint();
                } catch (Exception e) {
                    // Leave existing lenses untouched
                }
            }
        }.execute();
    }

    /** Opens Find Usages for the member a lens belongs to - the same event the navigator posts. */
    private void postFindUsages(UsageLens.LensEntry lens) {
        String className = classEntry.getClassName();
        switch (lens.targetType) {
            case METHOD:
                EventBus.getInstance().post(FindUsagesEvent.forMethod(
                        this, className, lens.memberName, lens.memberDescriptor));
                break;
            case FIELD:
                EventBus.getInstance().post(FindUsagesEvent.forField(
                        this, className, lens.memberName, lens.memberDescriptor));
                break;
            case CLASS:
                EventBus.getInstance().post(FindUsagesEvent.forClass(this, className));
                break;
        }
    }

    private void installMouseHandling() {
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 1
                        || (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    return;
                }
                UsageLens.LensEntry lens = lensOverlay.hitTest(e.getPoint());
                if (lens != null) {
                    postFindUsages(lens);
                }
            }
        });
        textArea.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    return;
                }
                boolean overLens = lensOverlay.hitTest(e.getPoint()) != null;
                textArea.setCursor(Cursor.getPredefinedCursor(
                        overLens ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }
        });
    }
}
