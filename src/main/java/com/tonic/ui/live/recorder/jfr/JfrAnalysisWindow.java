package com.tonic.ui.live.recorder.jfr;

import com.tonic.ui.MainFrame;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJPanel.BackgroundStyle;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.core.component.ThemedJTable;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A resizable, reused window that visualizes one captured {@code .jfr}: a dashboard Overview plus per-category
 * tabs (CPU, Allocations, Locks, Exceptions), each combining a {@link FlameGraphPanel} (with a zoom breadcrumb)
 * and a ranked, data-bar table. Tabs appear only for categories that have data. Double-clicking a flame-graph
 * frame or a hot-method row opens that method's decompiled source via {@link MainFrame#openLiveFrame}.
 */
public final class JfrAnalysisWindow extends JFrame {

    private final MainFrame mainFrame;
    private final JLabel header = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();
    private String fileName = "";

    public JfrAnalysisWindow(MainFrame mainFrame) {
        super("JFR Analysis");
        this.mainFrame = mainFrame;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setIconImages(mainFrame.getIconImages());
        setSize(1150, 780);
        setLocationRelativeTo(mainFrame);

        tabs.putClientProperty("JTabbedPane.tabType", "underlined");
        tabs.putClientProperty("JTabbedPane.showTabSeparators", false);
        tabs.setBackground(JStudioTheme.getBgPrimary());
        tabs.setForeground(JStudioTheme.getTextPrimary());

        ThemedJPanel content = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        ThemedJPanel top = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        header.setForeground(JStudioTheme.getTextPrimary());
        header.setFont(JStudioTheme.getUIFont(14).deriveFont(Font.BOLD));
        top.add(header, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        setContentPane(content);
    }

    /** Loads and analyzes {@code jfr} (parsing off the EDT), rebuilding the tabs. */
    public void load(File jfr) {
        fileName = jfr.getName();
        setTitle("JFR Analysis - " + fileName);
        header.setText("Parsing " + fileName + "...");
        tabs.removeAll();
        SwingWorkers.run(
                () -> JfrRecording.parse(jfr),
                this::buildTabs,
                err -> header.setText("Failed to parse " + fileName + ": " + err.getMessage()));
    }

    private void buildTabs(JfrRecording r) {
        tabs.removeAll();
        header.setText(String.format("%s     -     %s     -     %s events",
                fileName, JfrFormat.durationSec(r.getDuration()), JfrFormat.count(r.getTotalEvents())));

        tabs.addTab("Overview", overviewTab(r));
        if (r.hasCpu()) {
            tabs.addTab("CPU", flameTab(
                    new FlameGraphPanel(r.getCpuTree(), JfrFormat::samples, this::navigate),
                    String.format("CPU - %s across %,d methods", JfrFormat.samples(r.getCpuSamples()), r.getHotMethods().size()),
                    hotMethodsTable(r.getHotMethods())));
        }
        if (r.hasAllocations()) {
            tabs.addTab("Allocations", flameTab(
                    new FlameGraphPanel(r.getAllocTree(), JfrFormat::bytes, this::navigate),
                    String.format("Allocations - %s across %,d types", JfrFormat.bytes(r.getAllocBytes()), r.getAllocByType().size()),
                    table(new TypeTableModel(r.getAllocByType()), null,
                            new BarTableCellRenderer.Kind[]{BarTableCellRenderer.Kind.TEXT,
                                    BarTableCellRenderer.Kind.COUNT, BarTableCellRenderer.Kind.BYTES})));
        }
        if (r.hasLocks()) {
            tabs.addTab("Locks", flameTab(
                    new FlameGraphPanel(r.getLockTree(), JfrFormat::millisFromNanos, this::navigate),
                    String.format("Lock contention - %s across %,d monitors", JfrFormat.millisFromNanos(r.getLockNanos()), r.getLockContention().size()),
                    table(new LockTableModel(r.getLockContention()), null,
                            new BarTableCellRenderer.Kind[]{BarTableCellRenderer.Kind.TEXT,
                                    BarTableCellRenderer.Kind.COUNT, BarTableCellRenderer.Kind.MILLIS})));
        }
        if (r.hasExceptions()) {
            ThemedJPanel wrap = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
            wrap.add(summaryStrip(String.format("Exceptions - %s thrown across %,d types",
                    JfrFormat.count(r.getExceptionCount()), r.getExceptions().size())), BorderLayout.NORTH);
            wrap.add(table(new ExceptionTableModel(r.getExceptions()), null,
                    new BarTableCellRenderer.Kind[]{BarTableCellRenderer.Kind.TEXT, BarTableCellRenderer.Kind.COUNT}),
                    BorderLayout.CENTER);
            tabs.addTab("Exceptions", wrap);
        }
    }

    // ---- overview dashboard -------------------------------------------------------------------------

    private Component overviewTab(JfrRecording r) {
        ThemedJPanel overview = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        overview.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ThemedJPanel tiles = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 8));
        tiles.add(tile("Duration", JfrFormat.durationSec(r.getDuration())));
        tiles.add(tile("Events", JfrFormat.count(r.getTotalEvents())));
        if (r.hasCpu()) {
            tiles.add(tile("CPU samples", JfrFormat.count(r.getCpuSamples())));
        }
        if (r.hasAllocations()) {
            tiles.add(tile("Allocated", JfrFormat.bytes(r.getAllocBytes())));
        }
        if (r.hasLocks()) {
            tiles.add(tile("Lock time", JfrFormat.millisFromNanos(r.getLockNanos())));
        }
        if (r.hasExceptions()) {
            tiles.add(tile("Exceptions", JfrFormat.count(r.getExceptionCount())));
        }

        ThemedJPanel highlights = new ThemedJPanel(BackgroundStyle.PRIMARY, new GridLayout(0, 1, 0, 3));
        highlights.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        if (!r.getHotMethods().isEmpty()) {
            JfrRecording.MethodStat m = r.getHotMethods().get(0);
            highlights.add(highlight("Top hot method:  " + m.getFrame().displayLabel()
                    + "  (" + JfrFormat.samples(m.getSelf()) + ")"));
        }
        if (!r.getAllocByType().isEmpty()) {
            JfrRecording.TypeStat t = r.getAllocByType().get(0);
            highlights.add(highlight("Top allocated type:  " + t.getClassName()
                    + "  (" + JfrFormat.bytes(t.getBytes()) + ")"));
        }

        ThemedJPanel north = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        north.add(tiles, BorderLayout.NORTH);
        north.add(highlights, BorderLayout.CENTER);

        ThemedJPanel tableWrap = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        tableWrap.add(sectionLabel("Event types"), BorderLayout.NORTH);
        tableWrap.add(table(new EventCountTableModel(r.getEventCounts()), null,
                new BarTableCellRenderer.Kind[]{BarTableCellRenderer.Kind.TEXT, BarTableCellRenderer.Kind.COUNT}),
                BorderLayout.CENTER);

        overview.add(north, BorderLayout.NORTH);
        overview.add(tableWrap, BorderLayout.CENTER);
        return overview;
    }

    private ThemedJPanel tile(String caption, String value) {
        ThemedJPanel tile = new ThemedJPanel(BackgroundStyle.SECONDARY, new BorderLayout());
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        tile.setPreferredSize(new Dimension(160, 64));

        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(JStudioTheme.getTextPrimary());
        valueLabel.setFont(JStudioTheme.getUIFont(20).deriveFont(Font.BOLD));
        JLabel captionLabel = new JLabel(caption.toUpperCase());
        captionLabel.setForeground(JStudioTheme.getTextSecondary());
        captionLabel.setFont(JStudioTheme.getUIFont(10));

        tile.add(valueLabel, BorderLayout.CENTER);
        tile.add(captionLabel, BorderLayout.SOUTH);
        return tile;
    }

    private JLabel highlight(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setFont(JStudioTheme.getUIFont(12));
        return label;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextSecondary());
        label.setFont(JStudioTheme.getUIFont(11).deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        return label;
    }

    // ---- flame + table tab --------------------------------------------------------------------------

    /** A category tab: a zoom-breadcrumb/summary strip, a flame graph, and a ranked table below. */
    private Component flameTab(FlameGraphPanel flame, String summary, Component table) {
        JButton reset = new JButton("Reset zoom");
        reset.setFocusable(false);
        reset.setEnabled(false);
        reset.addActionListener(e -> flame.reset());

        JLabel info = new JLabel(summary);
        info.setForeground(JStudioTheme.getTextSecondary());
        info.setFont(JStudioTheme.getUIFont(12));
        flame.setOnZoomChanged(() -> {
            info.setText(flame.isZoomed() ? flame.pathLabel() : summary);
            reset.setEnabled(flame.isZoomed());
        });

        ThemedJPanel strip = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        strip.add(reset);
        strip.add(info);

        JScrollPane flameScroll = new ThemedJScrollPane(flame);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, flameScroll, table);
        split.setResizeWeight(0.55);
        split.setBorder(null);

        ThemedJPanel wrapper = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        wrapper.add(strip, BorderLayout.NORTH);
        wrapper.add(split, BorderLayout.CENTER);
        return wrapper;
    }

    private Component summaryStrip(String summary) {
        JLabel info = new JLabel(summary);
        info.setForeground(JStudioTheme.getTextSecondary());
        info.setFont(JStudioTheme.getUIFont(12));
        ThemedJPanel strip = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        strip.add(Box.createHorizontalStrut(2));
        strip.add(info);
        return strip;
    }

    // ---- tables -------------------------------------------------------------------------------------

    private Component hotMethodsTable(List<JfrRecording.MethodStat> methods) {
        HotMethodsTableModel model = new HotMethodsTableModel(methods);
        return table(model, model, new BarTableCellRenderer.Kind[]{BarTableCellRenderer.Kind.TEXT,
                BarTableCellRenderer.Kind.COUNT, BarTableCellRenderer.Kind.COUNT});
    }

    /** Themed, sortable, data-bar table; if {@code nav} is non-null, double-clicking a row navigates to source. */
    private Component table(AbstractTableModel model, FrameRowSource nav, BarTableCellRenderer.Kind[] kinds) {
        ThemedJTable table = new ThemedJTable(model);
        table.setAutoCreateRowSorter(true);
        BarTableCellRenderer.install(table, kinds);
        if (nav != null) {
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                        navigate(nav.frameAt(table.convertRowIndexToModel(table.getSelectedRow())));
                    }
                }
            });
        }
        return new ThemedJScrollPane(table);
    }

    private void navigate(FrameKey frame) {
        if (frame != null) {
            mainFrame.openLiveFrame(frame.getClassInternal(), frame.getMethod());
        }
    }

    /** A table whose model rows can yield a navigable frame. */
    private interface FrameRowSource {
        FrameKey frameAt(int modelRow);
    }

    // ---- table models -------------------------------------------------------------------------------

    private static final class HotMethodsTableModel extends AbstractTableModel implements FrameRowSource {
        private static final String[] COLS = {"Method", "Self", "Total"};
        private final List<JfrRecording.MethodStat> rows;

        HotMethodsTableModel(List<JfrRecording.MethodStat> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : Long.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            JfrRecording.MethodStat m = rows.get(row);
            switch (col) {
                case 0:
                    return m.getFrame().getClassInternal().replace('/', '.') + "." + m.getFrame().getMethod();
                case 1:
                    return m.getSelf();
                default:
                    return m.getTotal();
            }
        }

        @Override
        public FrameKey frameAt(int modelRow) {
            return rows.get(modelRow).getFrame();
        }
    }

    private static final class TypeTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Type", "Count", "Bytes"};
        private final List<JfrRecording.TypeStat> rows;

        TypeTableModel(List<JfrRecording.TypeStat> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : Long.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            JfrRecording.TypeStat t = rows.get(row);
            switch (col) {
                case 0:
                    return t.getClassName();
                case 1:
                    return t.getCount();
                default:
                    return t.getBytes();
            }
        }
    }

    private static final class LockTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Monitor", "Count", "Blocked"};
        private final List<JfrRecording.LockStat> rows;

        LockTableModel(List<JfrRecording.LockStat> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : (c == 1 ? Long.class : Double.class);
        }

        @Override
        public Object getValueAt(int row, int col) {
            JfrRecording.LockStat l = rows.get(row);
            switch (col) {
                case 0:
                    return l.getClassName();
                case 1:
                    return l.getCount();
                default:
                    return l.getNanos() / 1_000_000.0;
            }
        }
    }

    private static final class ExceptionTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Exception", "Count"};
        private final List<JfrRecording.ExceptionStat> rows;

        ExceptionTableModel(List<JfrRecording.ExceptionStat> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : Long.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            JfrRecording.ExceptionStat e = rows.get(row);
            return col == 0 ? e.getClassName() : e.getCount();
        }
    }

    private static final class EventCountTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Event type", "Count"};
        private final List<Map.Entry<String, Long>> rows;

        EventCountTableModel(Map<String, Long> counts) {
            this.rows = new ArrayList<>(counts.entrySet());
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : Long.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            Map.Entry<String, Long> e = rows.get(row);
            return col == 0 ? e.getKey() : e.getValue();
        }
    }
}
