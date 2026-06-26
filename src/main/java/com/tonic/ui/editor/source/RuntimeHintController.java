package com.tonic.ui.editor.source;

import com.tonic.event.EventBus;
import com.tonic.event.events.DebugFrameSelectedEvent;
import com.tonic.event.events.DebugResumedEvent;
import com.tonic.event.events.DebugSessionEvent;
import com.tonic.live.debug.DebugFrame;
import com.tonic.live.debug.DebugLocation;
import com.tonic.live.debug.DebugVariable;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.debug.DebugManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Paints live runtime values inline over the decompiled source when the debugger pauses on a frame whose class
 * matches this view. Mirrors {@link UsageLensController}: listens to debug events, computes values+placement off
 * the EDT, and repaints. Clicking any hint opens a {@link RuntimeValuesDialog} listing every value in full.
 */
final class RuntimeHintController {

    /** Longest inline value before it's ellipsized; the full value stays available in the values dialog. */
    private static final int INLINE_VALUE_MAX = 12;

    private final RSyntaxTextArea textArea;
    private final ClassEntryModel classEntry;
    private final RuntimeHintOverlay overlay = new RuntimeHintOverlay();

    private long lastTooltipHandle;
    private String lastTooltip;

    private final EventBus.EventHandler<DebugFrameSelectedEvent> frameHandler = e -> onFrame(e.getFrame());
    private final EventBus.EventHandler<DebugResumedEvent> resumedHandler = e -> clear();
    private final EventBus.EventHandler<DebugSessionEvent> sessionHandler = e -> {
        if (!e.isConnected()) {
            clear();
        }
    };

    RuntimeHintController(RSyntaxTextArea textArea, ClassEntryModel classEntry) {
        this.textArea = textArea;
        this.classEntry = classEntry;
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || overlay.isEmpty()) {
                    return;
                }
                if (overlay.hitTestAny(e.getPoint()) != null) {
                    RuntimeValuesDialog.show(textArea, overlay.entries());
                }
            }
        });
    }

    void paint(Graphics2D g) {
        overlay.paint(g, textArea);
    }

    /** Pixel width needed to fully reveal the rightmost hint, so the editor can widen for horizontal scrolling. */
    int requiredWidth() {
        return overlay.requiredWidth(textArea);
    }

    /** HTML preview (first 10 elements) when hovering a non-char array hint, else null. Cached per handle. */
    String tooltipAt(java.awt.Point p) {
        RuntimeHint.HintEntry hit = overlay.hitTest(p);
        if (hit == null || !hit.array || hit.refHandle <= 0) {
            return null;
        }
        if (hit.refHandle == lastTooltipHandle && lastTooltip != null) {
            return lastTooltip;
        }
        List<DebugVariable> els = DebugManager.getInstance().arrayElements(hit.refHandle, 10);
        StringBuilder sb = new StringBuilder("<html><body style='padding:2px'>");
        for (DebugVariable e : els) {
            sb.append(escape(e.getName())).append(" = ").append(escape(e.getDisplay())).append("<br>");
        }
        if (hit.arrayLength > els.size()) {
            sb.append("<i>... +").append(hit.arrayLength - els.size()).append(" more</i><br>");
        }
        sb.append("</body></html>");
        lastTooltipHandle = hit.refHandle;
        lastTooltip = sb.toString();
        return lastTooltip;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Truncates a value for the inline annotation; the full value stays available in the values dialog. */
    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= INLINE_VALUE_MAX ? s : s.substring(0, INLINE_VALUE_MAX) + "…";
    }

    void attach() {
        EventBus.getInstance().register(DebugFrameSelectedEvent.class, frameHandler);
        EventBus.getInstance().register(DebugResumedEvent.class, resumedHandler);
        EventBus.getInstance().register(DebugSessionEvent.class, sessionHandler);
    }

    void detach() {
        EventBus.getInstance().unregister(DebugFrameSelectedEvent.class, frameHandler);
        EventBus.getInstance().unregister(DebugResumedEvent.class, resumedHandler);
        EventBus.getInstance().unregister(DebugSessionEvent.class, sessionHandler);
        clear();
    }

    private void clear() {
        lastTooltipHandle = 0;
        lastTooltip = null;
        SwingUtilities.invokeLater(() -> {
            overlay.clear();
            textArea.revalidate();
            textArea.repaint();
        });
    }

    private void onFrame(DebugFrame frame) {
        if (frame == null || frame.getLocation() == null) {
            clear();
            return;
        }
        DebugLocation loc = frame.getLocation();
        if (!classEntry.getClassName().replace('/', '.').equals(loc.getClassName())) {
            clear();
            return;
        }
        final String methodKey = loc.getMethodName() + loc.getMethodDescriptor();
        final int pc = (int) loc.getCodeIndex();
        final int frameIndex = frame.getIndex();
        new SwingWorker<List<RuntimeHint.HintEntry>, Void>() {
            @Override
            protected List<RuntimeHint.HintEntry> doInBackground() {
                List<DebugVariable> vars = DebugManager.getInstance().variables(frameIndex);
                List<String> names = new ArrayList<>();
                for (DebugVariable v : vars) {
                    names.add(v.getName());
                }
                Map<String, Integer> lines = RuntimeHintPlacement.place(classEntry, methodKey, pc, names);
                List<RuntimeHint.HintEntry> out = new ArrayList<>();
                for (DebugVariable v : vars) {
                    Integer line = lines.get(v.getName());
                    if (line == null || line <= 0) {
                        continue;
                    }
                    String display = v.getDisplay();
                    String inline = v.getName() + " = " + (v.isArray() ? display : truncate(display));
                    String full = v.getName() + " = " + display;
                    out.add(new RuntimeHint.HintEntry(line, inline, full,
                            v.isReference() ? v.getRefHandle() : 0, v.isArray(), v.getArrayLength()));
                }
                return out;
            }

            @Override
            protected void done() {
                try {
                    overlay.setEntries(get());
                    textArea.revalidate();
                    textArea.repaint();
                } catch (Exception e) {
                    overlay.clear();
                }
            }
        }.execute();
    }
}
