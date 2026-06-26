package com.tonic.ui.live.scanner;

import com.tonic.event.EventBus;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveProtocol;
import com.tonic.live.protocol.ScanLocation;
import com.tonic.live.protocol.ScanPage;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.core.component.ThemedJTable;
import com.tonic.ui.editor.ViewMode;
import com.tonic.ui.debug.JdiReachService;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Right-dock tool (shown only while attached): a Cheat-Engine-style value scanner over the target JVM. A
 * first scan walks the app roots retaining matching field locations; successive next-scans narrow that set
 * by a comparator (changed/increased/...). Matches can be written, frozen, or pinned to a watch list, and
 * each result's owning field links back into the static tools (usages/rename/decompile/call graph).
 *
 * <p>All agent calls go through {@link SwingWorkers} off the EDT (the connection is serial); a ~700ms timer
 * re-reads the active set and the pinned set in place while no scan is in flight.
 */
public final class LiveValueScannerPanel extends ThemedJPanel {

    private static final int REFRESH_MS = 700;
    private static final int MAX_VISITED = 2_000_000;
    private static final int MAX_MATCHES = 50_000;
    private static final int PAGE_LIMIT = 2_000;

    private static final int[] VALUE_TYPES = {
            LiveProtocol.SCAN_INT, LiveProtocol.SCAN_LONG, LiveProtocol.SCAN_SHORT, LiveProtocol.SCAN_BYTE,
            LiveProtocol.SCAN_CHAR, LiveProtocol.SCAN_FLOAT, LiveProtocol.SCAN_DOUBLE, LiveProtocol.SCAN_NUMBER,
            LiveProtocol.SCAN_BOOLEAN, LiveProtocol.SCAN_STRING};
    private static final String[] VALUE_TYPE_NAMES = {
            "int", "long", "short", "byte", "char", "float", "double", "(number)", "boolean", "String"};

    private static final int[] SCAN_KINDS = {
            LiveProtocol.SCANKIND_EXACT, LiveProtocol.SCANKIND_GREATER, LiveProtocol.SCANKIND_LESS,
            LiveProtocol.SCANKIND_BETWEEN, LiveProtocol.SCANKIND_UNKNOWN};
    private static final String[] SCAN_KIND_NAMES = {"Exact", "Greater", "Less", "Between", "Unknown"};

    private static final int[] COMPARATORS = {
            LiveProtocol.CMP_EXACT, LiveProtocol.CMP_CHANGED, LiveProtocol.CMP_UNCHANGED, LiveProtocol.CMP_INCREASED,
            LiveProtocol.CMP_DECREASED, LiveProtocol.CMP_INCREASED_BY, LiveProtocol.CMP_DECREASED_BY,
            LiveProtocol.CMP_GREATER, LiveProtocol.CMP_LESS, LiveProtocol.CMP_BETWEEN};
    private static final String[] COMPARATOR_NAMES = {
            "Exact", "Changed", "Unchanged", "Increased", "Decreased", "Increased by", "Decreased by",
            "Greater", "Less", "Between"};

    private final MainFrame mainFrame;

    private final JComboBox<String> valueTypeCombo = new JComboBox<>(VALUE_TYPE_NAMES);
    private final JComboBox<String> scanKindCombo = new JComboBox<>(SCAN_KIND_NAMES);
    private final JComboBox<String> comparatorCombo = new JComboBox<>(COMPARATOR_NAMES);
    private final JTextField valueField = new JTextField(8);
    private final JTextField value2Field = new JTextField(8);
    private final JTextField pkgFilterField = new JTextField(8);
    private final JTextField scopeClassField = new JTextField(8);
    private final JCheckBox includeJdkCheckbox = new JCheckBox("Include Java internals");
    private final JButton firstScanButton = new JButton("First Scan");
    private final JButton nextScanButton = new JButton("Next Scan");
    private final JButton newScanButton = new JButton("New Scan");
    private final JLabel statusLabel = new JLabel("Not attached.");

    private final List<ScanLocation> resultRows = new ArrayList<>();
    private final DefaultTableModel resultModel = new DefaultTableModel(new Object[]{"Location", "Value", "Type"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final ThemedJTable resultTable = new ThemedJTable(resultModel);

    private final List<ScanLocation> watchRows = new ArrayList<>();
    private final DefaultTableModel watchModel = new DefaultTableModel(new Object[]{"Location", "Value", "Frozen?"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final ThemedJTable watchTable = new ThemedJTable(watchModel);
    private final JTextField watchValueField = new JTextField(8);
    private final JButton freezeButton = new JButton("Freeze");

    private final Timer timer = new Timer(REFRESH_MS, e -> refreshValues());

    private boolean scanned;
    private boolean scanInFlight;
    private boolean refreshInFlight;

    public LiveValueScannerPanel(MainFrame mainFrame) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.mainFrame = mainFrame;

        add(buildScanBar(), BorderLayout.NORTH);
        add(buildResults(), BorderLayout.CENTER);
        add(buildWatch(), BorderLayout.SOUTH);

        updateScanKindState();
        updateButtons();
    }

    private ThemedJPanel buildScanBar() {
        valueTypeCombo.setFocusable(false);
        valueTypeCombo.setToolTipText("The kind of field to search for. \"(number)\" matches any numeric field type.");
        scanKindCombo.setFocusable(false);
        scanKindCombo.setToolTipText("How to match: an exact value, a range, or Unknown (record everything, then narrow).");
        scanKindCombo.addActionListener(e -> updateScanKindState());
        comparatorCombo.setFocusable(false);
        comparatorCombo.setToolTipText("How the next scan compares each kept field to its previous value.");
        valueField.setToolTipText("The value to search for.");
        value2Field.setToolTipText("Upper bound - used only by the Between scan.");
        value2Field.setEnabled(false);
        pkgFilterField.setToolTipText("Limit the search to a package (internal form, e.g. com/foo); blank searches all app classes.");
        scopeClassField.setToolTipText("Requires the JDI debugger. When set, scans ONLY this class's complete "
                + "instance set (e.g. com.foo.Bar); blank uses the normal heap walk, augmented with stack roots under JDI.");
        firstScanButton.setFocusable(false);
        firstScanButton.addActionListener(e -> firstScan());
        firstScanButton.setToolTipText("Walk the live heap and keep every field matching the value.");
        nextScanButton.setFocusable(false);
        nextScanButton.addActionListener(e -> nextScan());
        nextScanButton.setToolTipText("Re-read the kept fields and narrow them by the comparator.");
        newScanButton.setFocusable(false);
        newScanButton.addActionListener(e -> newScan());
        newScanButton.setToolTipText("Clear all results and start over.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setFont(JStudioTheme.getUIFont(11));

        ThemedJPanel bar = new ThemedJPanel(BackgroundStyle.PRIMARY, new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 4);

        c.gridy = 0;
        c.gridx = 0; c.anchor = GridBagConstraints.EAST; bar.add(label("Value type:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST; bar.add(valueTypeCombo, c);
        c.gridx = 2; c.anchor = GridBagConstraints.EAST; bar.add(label("Scan:"), c);
        c.gridx = 3; c.anchor = GridBagConstraints.WEST; bar.add(scanKindCombo, c);

        c.gridy = 1;
        c.gridx = 0; c.anchor = GridBagConstraints.EAST; bar.add(label("Value:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST; bar.add(valueField, c);
        c.gridx = 2; c.anchor = GridBagConstraints.EAST; bar.add(label("to"), c);
        c.gridx = 3; c.anchor = GridBagConstraints.WEST; bar.add(value2Field, c);

        c.gridy = 2;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST; bar.add(label("Package:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST; bar.add(pkgFilterField, c);
        c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;

        c.gridy = 3;
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST; bar.add(label("Class scope:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST; bar.add(scopeClassField, c);
        c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;

        ThemedJPanel buttons = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(firstScanButton);
        buttons.add(newScanButton);
        buttons.add(Box.createHorizontalStrut(10));
        buttons.add(label("Next:"));
        buttons.add(comparatorCombo);
        buttons.add(nextScanButton);
        buttons.add(Box.createHorizontalStrut(10));
        includeJdkCheckbox.setFocusable(false);
        includeJdkCheckbox.setToolTipText("Off: only values reachable through your app's classes. "
                + "On: also report values held directly in JDK/library internals (slower, noisier).");
        buttons.add(includeJdkCheckbox);
        c.gridy = 4; c.gridx = 0; c.gridwidth = 4; c.anchor = GridBagConstraints.WEST;
        bar.add(buttons, c);

        c.gridy = 5; c.gridx = 0; c.gridwidth = 4; c.anchor = GridBagConstraints.WEST;
        bar.add(statusLabel, c);
        return bar;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(JStudioTheme.getTextSecondary());
        return l;
    }

    private ThemedJScrollPane buildResults() {
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(280);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ScanLocation loc = selectedResult();
                    if (loc != null) {
                        decompileOwner(loc);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }
        });
        return new ThemedJScrollPane(resultTable);
    }

    private ThemedJPanel buildWatch() {
        ThemedJPanel panel = new ThemedJPanel(BackgroundStyle.SECONDARY, new BorderLayout());

        JLabel header = new JLabel("Watch / Freeze");
        header.setFont(JStudioTheme.getUIFont(11));
        header.setForeground(JStudioTheme.getTextSecondary());
        panel.add(header, BorderLayout.NORTH);

        watchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        watchTable.getColumnModel().getColumn(0).setPreferredWidth(280);
        watchTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        watchTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        ThemedJScrollPane scroll = new ThemedJScrollPane(watchTable);
        scroll.setPreferredSize(new java.awt.Dimension(0, 140));
        panel.add(scroll, BorderLayout.CENTER);

        ThemedJPanel south = new ThemedJPanel(BackgroundStyle.SECONDARY, new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton setValueButton = new JButton("Set value");
        setValueButton.setFocusable(false);
        setValueButton.addActionListener(e -> setWatchValue());
        freezeButton.setFocusable(false);
        freezeButton.addActionListener(e -> toggleWatchFreeze());
        south.add(new JLabel("Value:"));
        south.add(watchValueField);
        south.add(setValueButton);
        south.add(freezeButton);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    // ---- lifecycle ----------------------------------------------------------------------------------

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /** Pre-fills the scan bar for the seeding entry points (heap/statics "Scan for this value"). */
    public void seed(int valueType, String value, String pkgFilter) {
        for (int i = 0; i < VALUE_TYPES.length; i++) {
            if (VALUE_TYPES[i] == valueType) {
                valueTypeCombo.setSelectedIndex(i);
                break;
            }
        }
        scanKindCombo.setSelectedIndex(0);
        valueField.setText(value != null ? value : "");
        pkgFilterField.setText(pkgFilter != null ? pkgFilter : "");
        updateScanKindState();
    }

    // ---- scanning -----------------------------------------------------------------------------------

    private void firstScan() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        int valueType = VALUE_TYPES[valueTypeCombo.getSelectedIndex()];
        int scanKind = SCAN_KINDS[scanKindCombo.getSelectedIndex()];
        String value = valueField.getText();
        String value2 = value2Field.getText();
        String pkgFilter = pkgFilterField.getText().trim();
        String scopeClass = scopeClassField.getText().trim();
        boolean userClassesOnly = !includeJdkCheckbox.isSelected();

        scanInFlight = true;
        statusLabel.setText("Scanning...");
        updateButtons();
        SwingWorkers.run(
                () -> JdiReachService.getInstance().scanFirst(session, valueType, scanKind, value, value2, pkgFilter,
                        userClassesOnly, MAX_VISITED, MAX_MATCHES, PAGE_LIMIT, scopeClass),
                page -> {
                    scanInFlight = false;
                    scanned = true;
                    applyResults(page);
                    updateButtons();
                },
                err -> {
                    scanInFlight = false;
                    statusLabel.setText("Scan failed: " + err.getMessage());
                    updateButtons();
                });
    }

    private void nextScan() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        int comparator = COMPARATORS[comparatorCombo.getSelectedIndex()];
        String value = valueField.getText();
        String value2 = value2Field.getText();

        scanInFlight = true;
        statusLabel.setText("Scanning...");
        updateButtons();
        SwingWorkers.run(
                () -> session.scanNext(comparator, value, value2, 0, PAGE_LIMIT),
                page -> {
                    scanInFlight = false;
                    applyResults(page);
                    updateButtons();
                },
                err -> {
                    scanInFlight = false;
                    statusLabel.setText("Scan failed: " + err.getMessage());
                    updateButtons();
                });
    }

    private void newScan() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        scanInFlight = true;
        updateButtons();
        SwingWorkers.run(
                () -> {
                    session.scanClear();
                    return null;
                },
                ignored -> {
                    scanInFlight = false;
                    scanned = false;
                    resultRows.clear();
                    resultModel.setRowCount(0);
                    watchRows.clear();
                    watchModel.setRowCount(0);
                    statusLabel.setText("Cleared. Ready for a new scan.");
                    updateButtons();
                },
                err -> {
                    scanInFlight = false;
                    statusLabel.setText("Clear failed: " + err.getMessage());
                    updateButtons();
                });
    }

    private void applyResults(ScanPage page) {
        resultRows.clear();
        resultModel.setRowCount(0);
        for (ScanLocation loc : page.getLocations()) {
            resultRows.add(loc);
            resultModel.addRow(new Object[]{loc.getDisplayPath(), loc.getValue(), loc.getType()});
        }
        StringBuilder text = new StringBuilder();
        text.append(page.getTotal()).append(" match").append(page.getTotal() == 1 ? "" : "es");
        if (page.getLocations().size() < page.getTotal()) {
            text.append(" (showing ").append(page.getLocations().size()).append(")");
        }
        if (page.isTruncated()) {
            text.append(" (partial - capped)");
        }
        statusLabel.setText(text.toString());
    }

    // ---- live refresh -------------------------------------------------------------------------------

    private void refreshValues() {
        if (scanInFlight || refreshInFlight) {
            return;
        }
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        refreshInFlight = true;
        SwingWorkers.run(
                () -> new RefreshData(
                        scanned ? session.scanRead(false, 0, PAGE_LIMIT) : null,
                        session.scanRead(true, 0, PAGE_LIMIT)),
                data -> {
                    refreshInFlight = false;
                    if (data.active != null) {
                        applyValueColumn(resultRows, resultModel, data.active, false);
                    }
                    applyWatch(data.pinned);
                },
                err -> refreshInFlight = false);
    }

    private void applyValueColumn(List<ScanLocation> rows, DefaultTableModel model, ScanPage page, boolean frozenColumn) {
        List<ScanLocation> locations = page.getLocations();
        int n = Math.min(rows.size(), Math.min(model.getRowCount(), locations.size()));
        for (int i = 0; i < n; i++) {
            ScanLocation loc = locations.get(i);
            if (loc.getId() == rows.get(i).getId()) {
                rows.set(i, loc);
                model.setValueAt(loc.getValue(), i, 1);
                if (frozenColumn) {
                    model.setValueAt(loc.isFrozen() ? "yes" : "", i, 2);
                }
            }
        }
    }

    private void applyWatch(ScanPage page) {
        List<ScanLocation> locations = page.getLocations();
        if (locations.size() == watchRows.size()) {
            applyValueColumn(watchRows, watchModel, page, true);
            return;
        }
        int selectedRow = watchTable.getSelectedRow();
        watchRows.clear();
        watchModel.setRowCount(0);
        for (ScanLocation loc : locations) {
            watchRows.add(loc);
            watchModel.addRow(new Object[]{loc.getDisplayPath(), loc.getValue(), loc.isFrozen() ? "yes" : ""});
        }
        if (selectedRow >= 0 && selectedRow < watchRows.size()) {
            watchTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }

    // ---- watch / freeze -----------------------------------------------------------------------------

    private void setWatchValue() {
        ScanLocation loc = selectedWatch();
        if (loc != null) {
            writeValue(loc, watchValueField.getText());
        }
    }

    private void toggleWatchFreeze() {
        ScanLocation loc = selectedWatch();
        if (loc != null) {
            freezeLocation(loc, !loc.isFrozen(), watchValueField.getText());
        }
    }

    private void writeValue(ScanLocation loc, String text) {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        SwingWorkers.run(
                () -> session.scanWrite(loc.getId(), false, text),
                newValue -> statusLabel.setText("Set " + loc.getDisplayPath() + " = " + newValue),
                err -> statusLabel.setText("Write failed: " + err.getMessage()));
    }

    private void freezeLocation(ScanLocation loc, boolean on, String text) {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        SwingWorkers.run(
                () -> {
                    session.scanFreeze(loc.getId(), on, text);
                    return null;
                },
                ignored -> statusLabel.setText((on ? "Froze " : "Unfroze ") + loc.getDisplayPath()),
                err -> statusLabel.setText("Freeze failed: " + err.getMessage()));
    }

    private void pinLocation(ScanLocation loc) {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        SwingWorkers.run(
                () -> {
                    session.scanPin(loc.getId(), true);
                    return null;
                },
                ignored -> statusLabel.setText("Added " + loc.getDisplayPath() + " to watch"),
                err -> statusLabel.setText("Pin failed: " + err.getMessage()));
    }

    // ---- launchpad ----------------------------------------------------------------------------------

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = resultTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= resultRows.size()) {
            return;
        }
        resultTable.setRowSelectionInterval(row, row);
        ScanLocation loc = resultRows.get(row);
        buildLaunchpad(loc).show(resultTable, e.getX(), e.getY());
    }

    private JPopupMenu buildLaunchpad(ScanLocation loc) {
        JPopupMenu menu = new JPopupMenu();

        ClassEntryModel owner = ownerOf(loc);

        JMenuItem findUsages = new JMenuItem("Find usages");
        findUsages.setEnabled(owner != null);
        findUsages.addActionListener(e -> EventBus.getInstance().post(FindUsagesEvent.forField(this,
                loc.getDeclaringClass(), loc.getFieldName(), loc.getFieldDesc())));
        menu.add(findUsages);

        JMenuItem decompile = new JMenuItem("Decompile owner");
        decompile.setEnabled(owner != null);
        decompile.addActionListener(e -> decompileOwner(loc));
        menu.add(decompile);

        JMenuItem rename = new JMenuItem("Rename field...");
        rename.setEnabled(owner != null && owner.getField(loc.getFieldName(), loc.getFieldDesc()) != null);
        rename.addActionListener(e -> {
            ClassEntryModel cls = ownerOf(loc);
            if (cls != null) {
                FieldEntryModel field = cls.getField(loc.getFieldName(), loc.getFieldDesc());
                if (field != null) {
                    mainFrame.showRenameFieldDialog(cls, field);
                }
            }
        });
        menu.add(rename);

        JMenuItem callGraph = new JMenuItem("Call graph");
        callGraph.setEnabled(owner != null);
        callGraph.addActionListener(e -> {
            ClassEntryModel cls = ownerOf(loc);
            if (cls != null) {
                mainFrame.getEditorPanel().openClass(cls, ViewMode.CALLGRAPH);
            }
        });
        menu.add(callGraph);

        menu.addSeparator();

        JMenuItem setValue = new JMenuItem("Set value...");
        setValue.addActionListener(e -> {
            String text = javax.swing.JOptionPane.showInputDialog(this,
                    "New value for " + loc.getDisplayPath() + ":", loc.getValue());
            if (text != null) {
                writeValue(loc, text);
            }
        });
        menu.add(setValue);

        JMenuItem freeze = new JMenuItem(loc.isFrozen() ? "Unfreeze" : "Freeze");
        freeze.addActionListener(e -> freezeLocation(loc, !loc.isFrozen(), loc.getValue()));
        menu.add(freeze);

        JMenuItem watch = new JMenuItem("Add to watch");
        watch.addActionListener(e -> pinLocation(loc));
        menu.add(watch);

        return menu;
    }

    private void decompileOwner(ScanLocation loc) {
        ClassEntryModel owner = ownerOf(loc);
        if (owner == null) {
            statusLabel.setText("Class not in project: " + loc.getDeclaringClass());
            return;
        }
        mainFrame.getEditorPanel().openClass(owner, ViewMode.SOURCE);
        FieldEntryModel field = owner.getField(loc.getFieldName(), loc.getFieldDesc());
        if (field != null) {
            mainFrame.getEditorPanel().scrollToField(field);
        }
    }

    private ClassEntryModel ownerOf(ScanLocation loc) {
        if (!loc.hasField()) {
            return null;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        return project != null ? project.getClass(loc.getDeclaringClass()) : null;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private ScanLocation selectedResult() {
        int row = resultTable.getSelectedRow();
        return row >= 0 && row < resultRows.size() ? resultRows.get(row) : null;
    }

    private ScanLocation selectedWatch() {
        int row = watchTable.getSelectedRow();
        return row >= 0 && row < watchRows.size() ? watchRows.get(row) : null;
    }

    private void updateScanKindState() {
        boolean between = SCAN_KINDS[scanKindCombo.getSelectedIndex()] == LiveProtocol.SCANKIND_BETWEEN;
        boolean unknown = SCAN_KINDS[scanKindCombo.getSelectedIndex()] == LiveProtocol.SCANKIND_UNKNOWN;
        value2Field.setEnabled(between);
        valueField.setEnabled(!unknown);
    }

    private void updateButtons() {
        firstScanButton.setEnabled(!scanInFlight);
        newScanButton.setEnabled(!scanInFlight);
        nextScanButton.setEnabled(scanned && !scanInFlight);
        comparatorCombo.setEnabled(scanned && !scanInFlight);
    }

    /** Carries both refresh reads off the EDT so the success callback can apply them together. */
    private static final class RefreshData {
        final ScanPage active;
        final ScanPage pinned;

        RefreshData(ScanPage active, ScanPage pinned) {
            this.active = active;
            this.pinned = pinned;
        }
    }
}
