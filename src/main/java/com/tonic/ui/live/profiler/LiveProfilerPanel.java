package com.tonic.ui.live.profiler;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.MetricsSnapshot;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Right-dock tool (shown only while attached): live graphs of the target JVM's CPU, heap, metaspace, GC,
 * threads, and loaded classes, sampled once a second from its JMX MXBeans via the agent. Sampling pauses
 * while the tab is not visible, and at most one request is outstanding at a time (the connection is serial).
 */
public final class LiveProfilerPanel extends ThemedJPanel {

    private static final int INTERVAL_MS = 1000;

    private final JLabel status = new JLabel("Attached - sampling...");
    private final Timer timer = new Timer(INTERVAL_MS, e -> poll());

    private final MetricChart cpu = new MetricChart("CPU", 100.0);
    private final MetricChart heap = new MetricChart("Heap", null);
    private final MetricChart meta = new MetricChart("Metaspace", null);
    private final MetricChart gc = new MetricChart("GC (ms/s)", null);
    private final MetricChart threads = new MetricChart("Threads", null);
    private final MetricChart classes = new MetricChart("Loaded classes", null);

    private final int cpuProc;
    private final int cpuSys;
    private final int heapUsed;
    private final int metaUsed;
    private final int gcSeries;
    private final int threadSeries;
    private final int classSeries;

    private boolean inFlight;
    private long prevGcTimeTotal = -1;
    private long prevUptime = -1;

    public LiveProfilerPanel() {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        cpuProc = cpu.addSeries(new Color(90, 200, 130));
        cpuSys = cpu.addSeries(new Color(230, 170, 70));
        heapUsed = heap.addSeries(new Color(90, 160, 230));
        metaUsed = meta.addSeries(new Color(180, 130, 220));
        gcSeries = gc.addSeries(new Color(230, 110, 110));
        threadSeries = threads.addSeries(new Color(110, 200, 200));
        classSeries = classes.addSeries(new Color(200, 180, 100));

        ThemedJPanel topBar = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        status.setForeground(JStudioTheme.getTextSecondary());
        status.setFont(JStudioTheme.getUIFont(11));
        topBar.add(status);
        add(topBar, BorderLayout.NORTH);

        Box stack = Box.createVerticalBox();
        for (MetricChart chart : new MetricChart[]{cpu, heap, meta, gc, threads, classes}) {
            chart.setAlignmentX(Component.LEFT_ALIGNMENT);
            chart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
            stack.add(chart);
            stack.add(Box.createVerticalStrut(6));
        }
        ThemedJPanel holder = new ThemedJPanel(BackgroundStyle.SECONDARY, new BorderLayout());
        holder.add(stack, BorderLayout.NORTH);
        add(new ThemedJScrollPane(holder), BorderLayout.CENTER);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
        poll();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private void poll() {
        // Poll regardless of whether the side tab is currently visible, so sampling starts as soon as the panel is
        // added on attach (the timer stops on removeNotify/detach). Metrics are global JVM stats, not view-specific.
        if (inFlight) {
            return;
        }
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            status.setText("Not attached.");
            return;
        }
        inFlight = true;
        SwingWorkers.run(
                session::getMetrics,
                this::apply,
                err -> {
                    inFlight = false;
                    status.setText("Profiler error: " + err.getMessage());
                });
    }

    private void apply(MetricsSnapshot m) {
        inFlight = false;

        cpu.push(cpuProc, percentValue(m.processCpuLoad));
        cpu.push(cpuSys, percentValue(m.systemCpuLoad));
        cpu.setReadout(percent(m.processCpuLoad) + " / " + percent(m.systemCpuLoad));

        heap.push(heapUsed, m.heapUsed);
        heap.setReadout(bytes(m.heapUsed) + " / " + bytes(m.heapMax > 0 ? m.heapMax : m.heapCommitted));

        MetricsSnapshot.MemoryPool metaspace = findPool(m, "Metaspace");
        if (metaspace != null) {
            meta.push(metaUsed, metaspace.used);
            meta.setReadout(bytes(metaspace.used) + " / " + bytes(metaspace.committed));
        }

        long gcTimeTotal = 0;
        long gcCountTotal = 0;
        for (MetricsSnapshot.GcStat g : m.gcStats) {
            gcTimeTotal += g.collectionTimeMs;
            gcCountTotal += g.collectionCount;
        }
        double msPerSec = 0;
        if (prevGcTimeTotal >= 0 && prevUptime >= 0 && m.uptimeMs > prevUptime) {
            double intervalSec = (m.uptimeMs - prevUptime) / 1000.0;
            msPerSec = Math.max(0, (gcTimeTotal - prevGcTimeTotal) / intervalSec);
        }
        gc.push(gcSeries, msPerSec);
        gc.setReadout(gcCountTotal + " GCs - " + (long) msPerSec + " ms/s");
        prevGcTimeTotal = gcTimeTotal;
        prevUptime = m.uptimeMs;

        threads.push(threadSeries, m.threadCount);
        threads.setReadout(m.threadCount + " (" + m.daemonThreadCount + " daemon, peak " + m.peakThreadCount + ")");

        classes.push(classSeries, m.loadedClassCount);
        classes.setReadout(m.loadedClassCount + " loaded, " + m.unloadedClassCount + " unloaded");

        status.setText("Uptime " + uptime(m.uptimeMs) + "  -  " + m.availableProcessors + " CPUs");

        for (JComponent chart : new JComponent[]{cpu, heap, meta, gc, threads, classes}) {
            chart.repaint();
        }
    }

    private static MetricsSnapshot.MemoryPool findPool(MetricsSnapshot m, String nameContains) {
        for (MetricsSnapshot.MemoryPool p : m.memoryPools) {
            if (p.name != null && p.name.contains(nameContains)) {
                return p;
            }
        }
        return null;
    }

    private static double percentValue(double load) {
        return load < 0 ? 0 : load * 100.0;
    }

    private static String percent(double load) {
        return load < 0 ? "n/a" : Math.round(load * 100) + "%";
    }

    private static String bytes(long b) {
        if (b < 0) {
            return "?";
        }
        if (b < 1024) {
            return b + " B";
        }
        double kb = b / 1024.0;
        if (kb < 1024) {
            return Math.round(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.0f MB", mb);
        }
        return String.format("%.1f GB", mb / 1024.0);
    }

    private static String uptime(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
