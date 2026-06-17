package com.tonic.ui.live.heap;

import com.tonic.live.LiveSession;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.core.component.ThemedJTable;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.live.LiveHeapService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A heap-snapshot view of the live instances of one class. The left list shows every instance of the tab's
 * class found in the current heap dump; selecting one shows its field values on the right. References are
 * navigable (double-click), with a back stack. The Refresh button takes a fresh dump; merely switching to
 * another class's tab re-filters the existing snapshot instantly (no new dump).
 */
public final class LiveInstancesView extends ThemedJPanel {

    private final ClassEntryModel classEntry;

    private final JLabel countLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton refreshButton;
    private JButton backButton;
    private final JLabel detailHeader = new JLabel(" ");

    private final DefaultListModel<Long> listModel = new DefaultListModel<>();
    private final JList<Long> instanceList = new JList<>(listModel);
    private final DefaultTableModel fieldModel = new DefaultTableModel(new Object[]{"Field", "Type", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final ThemedJTable fieldTable = new ThemedJTable(fieldModel);

    private final Deque<Long> backStack = new ArrayDeque<>();
    private long currentObjId;
    private boolean loaded;

    public LiveInstancesView(ClassEntryModel classEntry) {
        super(BackgroundStyle.TERTIARY, new BorderLayout());
        this.classEntry = classEntry;

        ThemedJPanel topBar = new ThemedJPanel(BackgroundStyle.PRIMARY,
                new FlowLayout(FlowLayout.LEFT, 8, 4));
        refreshButton = new JButton("Refresh", Icons.getIcon("refresh"));
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Take a fresh heap dump and re-list instances");
        refreshButton.addActionListener(e -> reload(true));
        countLabel.setForeground(JStudioTheme.getTextSecondary());
        countLabel.setFont(JStudioTheme.getUIFont(12));
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(90, 14));
        progressBar.setVisible(false);
        topBar.add(refreshButton);
        topBar.add(progressBar);
        topBar.add(countLabel);
        add(topBar, BorderLayout.NORTH);

        instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instanceList.setBackground(JStudioTheme.getBgSecondary());
        instanceList.setForeground(JStudioTheme.getTextPrimary());
        instanceList.setFont(JStudioTheme.getCodeFont(12));
        instanceList.setCellRenderer(new InstanceCellRenderer());
        instanceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Long sel = instanceList.getSelectedValue();
                if (sel != null) {
                    backStack.clear();
                    showInstance(sel);
                }
            }
        });
        ThemedJScrollPane leftScroll = new ThemedJScrollPane(instanceList);
        leftScroll.setPreferredSize(new Dimension(260, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, buildDetailPanel());
        split.setDividerLocation(280);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(JStudioTheme.getBgTertiary());
        add(split, BorderLayout.CENTER);
    }

    private JComponent buildDetailPanel() {
        ThemedJPanel detail = new ThemedJPanel(BackgroundStyle.SECONDARY, new BorderLayout());

        ThemedJPanel header = new ThemedJPanel(BackgroundStyle.SECONDARY,
                new FlowLayout(FlowLayout.LEFT, 8, 4));
        backButton = new JButton(Icons.getIcon("back"));
        backButton.setToolTipText("Back to previous object");
        backButton.setFocusable(false);
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setPreferredSize(new Dimension(28, 28));
        backButton.setEnabled(false);
        backButton.addActionListener(e -> {
            if (!backStack.isEmpty()) {
                showInstance(backStack.pop());
            }
        });
        detailHeader.setForeground(JStudioTheme.getAccent());
        detailHeader.setFont(JStudioTheme.getCodeFont(13));
        header.add(backButton);
        header.add(detailHeader);
        detail.add(header, BorderLayout.NORTH);

        fieldTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        fieldTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        fieldTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        fieldTable.getColumnModel().getColumn(2).setCellRenderer(new ValueCellRenderer());
        fieldTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateSelectedRef();
                }
            }
        });
        detail.add(new ThemedJScrollPane(fieldTable), BorderLayout.CENTER);
        return detail;
    }

    /** Called when the view becomes visible (EditorTab refresh). Lists instances using the current snapshot. */
    public void refresh() {
        if (!loaded) {
            loaded = true;
            reload(false);
        }
    }

    /**
     * Lists the instances of this class. With {@code forceDump} a brand-new heap dump is taken; otherwise the
     * existing snapshot is reused (only dumping if none exists yet). Runs the dump/parse off the EDT.
     */
    private void reload(boolean forceDump) {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            listModel.clear();
            clearDetail();
            countLabel.setText("Not attached.");
            return;
        }
        refreshButton.setEnabled(false);
        progressBar.setVisible(true);
        instanceList.setEnabled(false);
        listModel.clear();
        clearDetail();
        countLabel.setText(forceDump ? "Taking heap dump..." : "Loading snapshot...");
        final String internalName = classEntry.getClassName();

        SwingWorkers.run(
                () -> {
                    LiveHeapService svc = LiveHeapService.get();
                    HprofSnapshot snap = forceDump ? svc.snapshot(session) : svc.ensureSnapshot(session);
                    return snap.instancesOf(internalName);
                },
                this::showInstances,
                err -> {
                    endLoading();
                    listModel.clear();
                    clearDetail();
                    countLabel.setText("Heap dump failed: " + err.getMessage());
                });
    }

    private void showInstances(List<Long> ids) {
        endLoading();
        listModel.clear();
        for (Long id : ids) {
            listModel.addElement(id);
        }
        countLabel.setText(listModel.size() + " instance" + (listModel.size() == 1 ? "" : "s"));
        clearDetail();
    }

    private void endLoading() {
        refreshButton.setEnabled(true);
        progressBar.setVisible(false);
        instanceList.setEnabled(true);
    }

    private void showInstance(long objId) {
        currentObjId = objId;
        backButton.setEnabled(!backStack.isEmpty());
        HprofSnapshot snap = LiveHeapService.get().getSnapshot();
        if (snap == null) {
            clearDetail();
            return;
        }
        detailHeader.setText(snap.labelFor(objId));
        fieldModel.setRowCount(0);
        try {
            HprofSnapshot.InstanceData data = snap.decode(objId);
            for (HprofSnapshot.FieldValue f : data.fields) {
                fieldModel.addRow(new Object[]{f.name, f.type, f});
            }
        } catch (Exception ex) {
            detailHeader.setText("Failed to decode @" + Long.toHexString(objId) + ": " + ex.getMessage());
        }
    }

    private void navigateSelectedRef() {
        int row = fieldTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Object value = fieldModel.getValueAt(row, 2);
        if (value instanceof HprofSnapshot.FieldValue) {
            HprofSnapshot.FieldValue f = (HprofSnapshot.FieldValue) value;
            if (f.refId != 0) {
                backStack.push(currentObjId);
                showInstance(f.refId);
            }
        }
    }

    private void clearDetail() {
        detailHeader.setText(" ");
        fieldModel.setRowCount(0);
        backStack.clear();
        if (backButton != null) {
            backButton.setEnabled(false);
        }
    }

    /** Renders an instance id as its snapshot label (Class@hex, or the text for a String). */
    private static final class InstanceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            HprofSnapshot snap = LiveHeapService.get().getSnapshot();
            setText(snap != null && value instanceof Long ? snap.labelFor((Long) value) : String.valueOf(value));
            setFont(JStudioTheme.getCodeFont(12));
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            if (isSelected) {
                setBackground(JStudioTheme.getSelection());
                setForeground(JStudioTheme.getTextPrimary());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
                setForeground(JStudioTheme.getTextPrimary());
            }
            return this;
        }
    }

    /** Colours field values: references accent + underlined, strings green, null muted, primitives plain. */
    private static final class ValueCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Color fg = JStudioTheme.getTextPrimary();
            String text = String.valueOf(value);
            if (value instanceof HprofSnapshot.FieldValue) {
                HprofSnapshot.FieldValue f = (HprofSnapshot.FieldValue) value;
                text = f.display;
                if (f.refId != 0) {
                    fg = JStudioTheme.getAccent();
                } else if ("null".equals(f.display)) {
                    fg = JStudioTheme.getTextDisabled();
                } else if (f.display.startsWith("\"") || "char".equals(f.type)) {
                    fg = JStudioTheme.getSuccess();
                }
            }
            setText(text);
            setForeground(isSelected ? JStudioTheme.getTextPrimary() : fg);
            setBackground(isSelected ? JStudioTheme.getSelection() : JStudioTheme.getBgSecondary());
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }
}
