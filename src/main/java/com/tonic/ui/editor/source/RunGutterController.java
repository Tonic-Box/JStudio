package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.event.EventBus;
import com.tonic.event.events.LiveSessionEvent;
import com.tonic.event.events.RunStateEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.service.run.RunStateService;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.Icons;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Owns the editor's "Run main()" gutter badge: it tracks the run/stop icon and its line, wires the click/cursor
 * handler onto the gutter, and (via {@link #attach()}/{@link #detach()}) subscribes to run-state and live-session
 * events so the badge reflects the current state. The actual launch is delegated to a supplied {@code runMain}.
 */
final class RunGutterController {

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final ClassEntryModel classEntry;
    private final BooleanSupplier omitAnnotations;

    private final List<GutterIconInfo> runIcons = new ArrayList<>();
    private final Set<Integer> runLines = new HashSet<>();
    private final Set<Component> wiredGutter = new HashSet<>();
    private final MouseAdapter runGutterMouse;
    private final EventBus.EventHandler<RunStateEvent> runStateHandler = e -> refresh();
    private final EventBus.EventHandler<LiveSessionEvent> liveSessionHandler = e -> refresh();

    RunGutterController(RSyntaxTextArea textArea, RTextScrollPane scrollPane, ClassEntryModel classEntry,
                        BooleanSupplier omitAnnotations, Runnable runMain) {
        this.textArea = textArea;
        this.scrollPane = scrollPane;
        this.classEntry = classEntry;
        this.omitAnnotations = omitAnnotations;

        // Make the Run gutter icon clickable + show a hand cursor over it. The icon lives on the gutter's
        // IconRowHeader child, so the listener must be on the children (AWT dispatches to the deepest
        // component, not the parent gutter); convertPoint maps the click into the text area (corrects scroll).
        this.runGutterMouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (lineAtRunIcon(e) > 0) {
                    if (RunStateService.getInstance().isRunning()) {
                        RunStateService.getInstance().terminate();
                    } else {
                        runMain.run();
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Component src = (Component) e.getSource();
                src.setCursor(Cursor.getPredefinedCursor(
                        lineAtRunIcon(e) > 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ((Component) e.getSource()).setCursor(Cursor.getDefaultCursor());
            }
        };
        wireGutterMouse();
    }

    /** Subscribes to run/live events and shows the initial badge; call from the host view's {@code addNotify}. */
    void attach() {
        EventBus.getInstance().register(RunStateEvent.class, runStateHandler);
        EventBus.getInstance().register(LiveSessionEvent.class, liveSessionHandler);
        updateIcons();
    }

    /** Unsubscribes from run/live events; call from the host view's {@code removeNotify}. */
    void detach() {
        EventBus.getInstance().unregister(RunStateEvent.class, runStateHandler);
        EventBus.getInstance().unregister(LiveSessionEvent.class, liveSessionHandler);
    }

    /** Refreshes the gutter run/stop badge when run or live-attach state changes (marshals to the EDT). */
    private void refresh() {
        SwingUtilities.invokeLater(this::updateIcons);
    }

    void updateIcons() {
        wireGutterMouse();
        Gutter gutter = scrollPane.getGutter();
        for (GutterIconInfo info : runIcons) {
            gutter.removeTrackingIcon(info);
        }
        runIcons.clear();
        runLines.clear();

        // While a run is active it auto-attaches a live (run) session, so allow the badge through when running;
        // only a manual (non-run) attachment hides it. A running badge becomes a stop/terminate affordance.
        boolean running = RunStateService.getInstance().isRunning();
        if (omitAnnotations.getAsBoolean() || classEntry == null || classEntry.getMethodSpans() == null
                || !classEntry.hasMainMethod()
                || (LiveAttachService.getInstance().isAttached() && !running)) {
            return;
        }
        DecompileResult.MethodSpan span = classEntry.getMethodSpans().get("main([Ljava/lang/String;)V");
        if (span == null) {
            return;
        }
        int line = span.getStartLine();
        try {
            runIcons.add(gutter.addLineTrackingIcon(line - 1,
                    Icons.getIcon(running ? "stop" : "run"), running ? "Terminate" : "Run main()"));
            runLines.add(line);
        } catch (BadLocationException ignored) {
        }
    }

    /** Attaches the run click/cursor adapter to the gutter and any child components not yet wired (idempotent). */
    private void wireGutterMouse() {
        Gutter gutter = scrollPane.getGutter();
        if (wiredGutter.add(gutter)) {
            gutter.addMouseListener(runGutterMouse);
            gutter.addMouseMotionListener(runGutterMouse);
        }
        for (Component child : gutter.getComponents()) {
            if (wiredGutter.add(child)) {
                child.addMouseListener(runGutterMouse);
                child.addMouseMotionListener(runGutterMouse);
            }
        }
    }

    /** Resolves the run-method source line under a gutter mouse event, or -1 if the cursor isn't on a run icon. */
    private int lineAtRunIcon(MouseEvent e) {
        if (runLines.isEmpty()) {
            return -1;
        }
        Point inText = SwingUtilities.convertPoint(
                (Component) e.getSource(), e.getPoint(), textArea);
        int offset = textArea.viewToModel2D(inText);
        if (offset < 0) {
            return -1;
        }
        try {
            int line = textArea.getLineOfOffset(offset) + 1;
            return runLines.contains(line) ? line : -1;
        } catch (BadLocationException ex) {
            return -1;
        }
    }
}
