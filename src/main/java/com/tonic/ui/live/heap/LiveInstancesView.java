package com.tonic.ui.live.heap;

import com.tonic.event.EventBus;
import com.tonic.event.events.ScanSeedEvent;
import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveField;
import com.tonic.live.protocol.LiveInstance;
import com.tonic.live.protocol.LiveProtocol;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.core.component.ThemedJTable;
import com.tonic.ui.editor.view.AbstractEditorView;
import com.tonic.ui.debug.JdiReachService;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A live view of the instances of one class. The left list shows instances found by walking the live heap;
 * selecting one shows its current field values on the right, read from the real object via a retained handle.
 * Non-final primitive/String fields edit in place (double-click; booleans use a true/false dropdown) and are
 * written straight to the live object; reference fields are click-to-navigate, with a back stack. Refresh
 * re-walks the heap.
 */
public final class LiveInstancesView extends AbstractEditorView {

    private final ClassEntryModel classEntry;

    private final JLabel countLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton refreshButton;
    private JButton backButton;
    private final JLabel detailHeader = new JLabel(" ");

    private static final int MAX_INSTANCES = 10_000;
    private static final int MAX_VISITED = 3_000_000;

    private final DefaultListModel<LiveInstance> listModel = new DefaultListModel<>();
    private final JList<LiveInstance> instanceList = new JList<>(listModel);
    private final FieldTableModel fieldModel = new FieldTableModel();
    private final ThemedJTable fieldTable = new ThemedJTable(fieldModel) {
        @Override
        public javax.swing.table.TableCellEditor getCellEditor(int row, int column) {
            if (convertColumnIndexToModel(column) == 2) {
                Object v = fieldModel.getValueAt(convertRowIndexToModel(row), 2);
                if (v instanceof LiveField && ((LiveField) v).isBoolean()) {
                    return new ValueEditor(new JComboBox<>(new String[]{"true", "false"}));
                }
                return new ValueEditor(new JTextField());
            }
            return super.getCellEditor(row, column);
        }
    };

    private final Deque<Long> backStack = new ArrayDeque<>();
    private long currentHandleId;

    public LiveInstancesView(ClassEntryModel classEntry) {
        super(new BorderLayout());
        this.classEntry = classEntry;

        ThemedJPanel topBar = new ThemedJPanel(BackgroundStyle.PRIMARY,
                new FlowLayout(FlowLayout.LEFT, 8, 4));
        refreshButton = new JButton("Refresh", Icons.getIcon("refresh"));
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Re-walk the live heap and re-list instances");
        refreshButton.addActionListener(e -> loadInstances());
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
                LiveInstance sel = instanceList.getSelectedValue();
                if (sel != null) {
                    backStack.clear();
                    showInstance(sel.getHandleId(), sel.getLabel());
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
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.setEnabled(false);
        backButton.addActionListener(e -> {
            if (!backStack.isEmpty()) {
                String n = classEntry.getClassName();
                int slash = n.lastIndexOf('/');
                showInstance(backStack.pop(), slash < 0 ? n : n.substring(slash + 1));
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
        MouseAdapter refNav = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                LiveField f = refAt(e.getPoint());
                if (f != null) {
                    backStack.push(currentHandleId);
                    showInstance(f.getRefHandleId(), f.getDisplay());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                fieldTable.setCursor(refAt(e.getPoint()) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        };
        fieldTable.addMouseListener(refNav);
        fieldTable.addMouseMotionListener(refNav);
        fieldTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeScanPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeScanPopup(e);
            }
        });
        detail.add(new ThemedJScrollPane(fieldTable), BorderLayout.CENTER);
        return detail;
    }

    /** Right-click "Scan for this value" on a primitive/String field value: seeds the live Value Scanner. */
    private void maybeScanPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = fieldTable.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        fieldTable.setRowSelectionInterval(row, row);
        Object value = fieldModel.getValueAt(row, 2);
        if (!(value instanceof LiveField)) {
            return;
        }
        LiveField f = (LiveField) value;
        int valueType = scanTypeOf(f);
        if (valueType < 0 || "null".equals(f.getDisplay())) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem scan = new JMenuItem("Scan for this value");
        scan.addActionListener(ev -> EventBus.getInstance().post(
                new ScanSeedEvent(this, valueType, f.getDisplay(), "")));
        menu.add(scan);
        menu.show(fieldTable, e.getX(), e.getY());
    }

    /** Maps an instance field (by JVM type descriptor) to a scanner {@code SCAN_*} type, or -1 if not scannable. */
    private static int scanTypeOf(LiveField f) {
        switch (f.getTypeDesc()) {
            case "I": return LiveProtocol.SCAN_INT;
            case "J": return LiveProtocol.SCAN_LONG;
            case "S": return LiveProtocol.SCAN_SHORT;
            case "B": return LiveProtocol.SCAN_BYTE;
            case "C": return LiveProtocol.SCAN_CHAR;
            case "F": return LiveProtocol.SCAN_FLOAT;
            case "D": return LiveProtocol.SCAN_DOUBLE;
            case "Z": return LiveProtocol.SCAN_BOOLEAN;
            case "Ljava/lang/String;": return LiveProtocol.SCAN_STRING;
            default: return -1;
        }
    }

    /** Called when the view becomes visible (EditorTab refresh). Lists instances using the current snapshot. */
    @Override
    public void refresh() {
        if (!loaded) {
            loaded = true;
            loadInstances();
        }
    }

    /** Walks the live heap for instances of this class (live handles, so fields can be read and written). */
    private void loadInstances() {
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
        countLabel.setText(JdiReachService.getInstance().isJdiBacked() ? "Enumerating (JDI)..." : "Walking heap...");
        final String internalName = classEntry.getClassName();

        SwingWorkers.run(
                () -> JdiReachService.getInstance().listInstances(session, internalName, MAX_INSTANCES, MAX_VISITED),
                this::showInstances,
                err -> {
                    endLoading();
                    listModel.clear();
                    clearDetail();
                    countLabel.setText("Instance walk failed: " + err.getMessage());
                });
    }

    private void showInstances(List<LiveInstance> instances) {
        endLoading();
        listModel.clear();
        for (LiveInstance i : instances) {
            listModel.addElement(i);
        }
        countLabel.setText(listModel.size() + " instance" + (listModel.size() == 1 ? "" : "s"));
        clearDetail();
    }

    private void endLoading() {
        refreshButton.setEnabled(true);
        progressBar.setVisible(false);
        instanceList.setEnabled(true);
    }

    private void showInstance(long handleId, String header) {
        currentHandleId = handleId;
        backButton.setEnabled(!backStack.isEmpty());
        detailHeader.setText(header);
        fieldModel.setRowCount(0);
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            return;
        }
        SwingWorkers.run(
                () -> session.instanceFields(handleId),
                fields -> {
                    fieldModel.setRowCount(0);
                    for (LiveField f : fields) {
                        fieldModel.addRow(new Object[]{f.getName(), prettyType(f.getTypeDesc()), f});
                    }
                },
                err -> detailHeader.setText(header + " - read failed: " + err.getMessage()));
    }

    /** Writes an edited primitive/String field to the live object, then refreshes the cell with the read-back value. */
    private void commitFieldEdit(int row, LiveField field, String newValue) {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            return;
        }
        SwingWorkers.run(
                () -> session.setInstanceField(currentHandleId, field.getName(), false, newValue),
                newDisplay -> fieldModel.setLiveField(row, new LiveField(field.getName(), field.getTypeDesc(),
                        newDisplay, field.getRefHandleId(), field.isEditable())),
                err -> countLabel.setText("Set failed: " + err.getMessage()));
    }

    /** Friendly type from a JVM descriptor: {@code I -> int}, {@code Ljava/lang/String; -> String}, {@code [I -> int[]}. */
    private static String prettyType(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "?";
        }
        switch (desc.charAt(0)) {
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case '[': return prettyType(desc.substring(1)) + "[]";
            case 'L':
                String n = desc.substring(1, desc.endsWith(";") ? desc.length() - 1 : desc.length());
                int slash = n.lastIndexOf('/');
                return slash < 0 ? n : n.substring(slash + 1);
            default: return desc;
        }
    }

    /** The reference {@code LiveField} under point {@code p} if it is a navigable ref in the Value column, else null. */
    private LiveField refAt(Point p) {
        int row = fieldTable.rowAtPoint(p);
        int col = fieldTable.columnAtPoint(p);
        if (row < 0 || col < 0 || fieldTable.convertColumnIndexToModel(col) != 2) {
            return null;
        }
        Object value = fieldModel.getValueAt(row, 2);
        return value instanceof LiveField && ((LiveField) value).isReference() ? (LiveField) value : null;
    }

    private void clearDetail() {
        detailHeader.setText(" ");
        fieldModel.setRowCount(0);
        backStack.clear();
        if (backButton != null) {
            backButton.setEnabled(false);
        }
    }

    /** Table model whose Value column (2) holds {@link LiveField}s; non-final primitive/String cells edit in place. */
    private final class FieldTableModel extends DefaultTableModel {
        FieldTableModel() {
            super(new Object[]{"Field", "Type", "Value"}, 0);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            if (column != 2) {
                return false;
            }
            Object v = getValueAt(row, 2);
            return v instanceof LiveField && ((LiveField) v).isEditable();
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            if (column == 2) {
                Object cur = getValueAt(row, 2);
                if (cur instanceof LiveField && ((LiveField) cur).isEditable()) {
                    commitFieldEdit(row, (LiveField) cur, String.valueOf(aValue));
                    return;
                }
            }
            super.setValueAt(aValue, row, column);
        }

        /** Programmatic update that bypasses the edit intercept (used after a successful write). */
        void setLiveField(int row, LiveField f) {
            super.setValueAt(f, row, 2);
        }
    }

    /** Value-column editor: a true/false combo for booleans, a text field otherwise; seeds from the field's display. */
    private static final class ValueEditor extends DefaultCellEditor {
        ValueEditor(JComboBox<String> combo) {
            super(combo);
        }

        ValueEditor(JTextField text) {
            super(text);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            String text = value instanceof LiveField ? ((LiveField) value).getDisplay() : String.valueOf(value);
            return super.getTableCellEditorComponent(table, text, isSelected, row, column);
        }
    }

    /** Renders a live instance by its agent-supplied label (Class@hex, or the text for a String). */
    private static final class InstanceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText(value instanceof LiveInstance ? ((LiveInstance) value).getLabel() : String.valueOf(value));
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

    /** Colours field values: references accent, strings/chars green, null muted, primitives plain. */
    private static final class ValueCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Color fg = JStudioTheme.getTextPrimary();
            String text = String.valueOf(value);
            if (value instanceof LiveField) {
                LiveField f = (LiveField) value;
                text = f.getDisplay();
                if (f.isReference()) {
                    fg = JStudioTheme.getAccent();
                } else if ("null".equals(f.getDisplay())) {
                    fg = JStudioTheme.getTextDisabled();
                } else if ("Ljava/lang/String;".equals(f.getTypeDesc()) || "C".equals(f.getTypeDesc())) {
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
