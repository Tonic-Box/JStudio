package com.tonic.ui.live.statics;

import com.tonic.event.EventBus;
import com.tonic.event.events.ScanSeedEvent;
import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveProtocol;
import com.tonic.live.protocol.StaticField;
import com.tonic.live.protocol.StaticMethod;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.core.component.ThemedJTable;
import com.tonic.ui.editor.view.AbstractEditorView;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.util.DescriptorParser;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A per-class view (under the "Live" section, shown only while attached) of the class's live static state:
 * the top table lists static fields with their current values - primitives and Strings are editable inline,
 * reference fields can only be set to null, and {@code final} fields are read-only. The bottom list shows
 * static methods that can be invoked with primitive/String/null arguments.
 */
public final class LiveStaticsView extends AbstractEditorView {

    private final String className;

    private final JLabel statusLabel = new JLabel(" ");
    private final JButton refreshButton;
    private final JButton setNullButton;

    private boolean programmaticEdit;
    private final List<StaticField> fieldRows = new ArrayList<>();
    private final DefaultTableModel fieldModel = new DefaultTableModel(new Object[]{"Field", "Type", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            if (column != 2 || row < 0 || row >= fieldRows.size()) {
                return false;
            }
            int kind = fieldRows.get(row).getKind();
            return kind == LiveProtocol.STATIC_PRIMITIVE || kind == LiveProtocol.STATIC_STRING;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 2 && !programmaticEdit) {
                commitFieldEdit(row, String.valueOf(value));
            } else {
                super.setValueAt(value, row, column);
            }
        }
    };
    private final ThemedJTable fieldTable = new ThemedJTable(fieldModel);

    private final DefaultListModel<StaticMethod> methodModel = new DefaultListModel<>();
    private final JList<StaticMethod> methodList = new JList<>(methodModel);
    private JButton invokeButton;

    public LiveStaticsView(ClassEntryModel classEntry) {
        super(new BorderLayout());
        this.className = classEntry.getClassName();

        ThemedJPanel topBar = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        refreshButton = new JButton("Refresh", Icons.getIcon("refresh"));
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Re-read live static state");
        refreshButton.addActionListener(e -> reloadData());
        setNullButton = new JButton("Set null");
        setNullButton.setFocusable(false);
        setNullButton.setToolTipText("Set the selected reference/String field to null");
        setNullButton.setEnabled(false);
        setNullButton.addActionListener(e -> setSelectedNull());
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setFont(JStudioTheme.getUIFont(12));
        topBar.add(refreshButton);
        topBar.add(setNullButton);
        topBar.add(statusLabel);
        add(topBar, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildFieldsPanel(), buildMethodsPanel());
        split.setResizeWeight(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(JStudioTheme.getBgTertiary());
        add(split, BorderLayout.CENTER);
    }

    private JComponent buildFieldsPanel() {
        ThemedJPanel panel = new ThemedJPanel(BackgroundStyle.SECONDARY, new BorderLayout());
        JLabel header = sectionLabel("Static Fields");
        panel.add(header, BorderLayout.NORTH);

        fieldTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        fieldTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        fieldTable.getColumnModel().getColumn(2).setPreferredWidth(280);
        fieldTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = fieldTable.getSelectedRow();
                boolean nullable = row >= 0 && row < fieldRows.size()
                        && (fieldRows.get(row).getKind() == LiveProtocol.STATIC_STRING
                        || fieldRows.get(row).getKind() == LiveProtocol.STATIC_REFERENCE);
                setNullButton.setEnabled(nullable);
            }
        });
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
        panel.add(new ThemedJScrollPane(fieldTable), BorderLayout.CENTER);
        return panel;
    }

    /** Right-click "Scan for this value" on a primitive/String field: seeds the live Value Scanner. */
    private void maybeScanPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = fieldTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= fieldRows.size()) {
            return;
        }
        fieldTable.setRowSelectionInterval(row, row);
        StaticField field = fieldRows.get(row);
        int valueType = scanTypeOf(field.getTypeDesc());
        if (valueType < 0) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem scan = new JMenuItem("Scan for this value");
        scan.addActionListener(ev -> EventBus.getInstance().post(
                new ScanSeedEvent(this, valueType, field.getValue(), "")));
        menu.add(scan);
        menu.show(fieldTable, e.getX(), e.getY());
    }

    /** Maps a field descriptor to a scanner {@code SCAN_*} value type, or -1 for non-scannable types. */
    private static int scanTypeOf(String desc) {
        if (desc == null || desc.isEmpty()) {
            return -1;
        }
        switch (desc) {
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

    private JComponent buildMethodsPanel() {
        ThemedJPanel panel = new ThemedJPanel(BackgroundStyle.SECONDARY, new BorderLayout());
        panel.add(sectionLabel("Static Methods"), BorderLayout.NORTH);

        methodList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        methodList.setBackground(JStudioTheme.getBgSecondary());
        methodList.setForeground(JStudioTheme.getTextPrimary());
        methodList.setFont(JStudioTheme.getCodeFont(12));
        methodList.setCellRenderer(new MethodCellRenderer());
        methodList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                invokeButton.setEnabled(methodList.getSelectedValue() != null);
            }
        });
        panel.add(new ThemedJScrollPane(methodList), BorderLayout.CENTER);

        ThemedJPanel south = new ThemedJPanel(BackgroundStyle.SECONDARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        invokeButton = new JButton("Invoke", Icons.getIcon("run"));
        invokeButton.setFocusable(false);
        invokeButton.setEnabled(false);
        invokeButton.addActionListener(e -> invokeSelected());
        south.add(invokeButton);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(JStudioTheme.getUIFont(11));
        label.setForeground(JStudioTheme.getTextSecondary());
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));
        return label;
    }

    /** Called when the view becomes visible (EditorTab refresh). */
    @Override
    public void refresh() {
        if (!loaded) {
            loaded = true;
            reloadData();
        }
    }

    private void reloadData() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            fieldRows.clear();
            fieldModel.setRowCount(0);
            methodModel.clear();
            statusLabel.setText("Not attached.");
            return;
        }
        refreshButton.setEnabled(false);
        statusLabel.setText("Reading live state...");
        SwingWorkers.run(
                () -> new StaticsData(session.getStatics(className), session.listStaticMethods(className)),
                this::populate,
                err -> {
                    refreshButton.setEnabled(true);
                    statusLabel.setText("Failed: " + err.getMessage());
                });
    }

    private void populate(StaticsData data) {
        refreshButton.setEnabled(true);
        fieldRows.clear();
        fieldModel.setRowCount(0);
        for (StaticField f : data.fields) {
            fieldRows.add(f);
            fieldModel.addRow(new Object[]{f.getName(), DescriptorParser.formatFieldDescriptor(f.getTypeDesc()), f.getValue()});
        }
        methodModel.clear();
        for (StaticMethod m : data.methods) {
            methodModel.addElement(m);
        }
        statusLabel.setText(data.fields.size() + " static field" + (data.fields.size() == 1 ? "" : "s")
                + ", " + data.methods.size() + " method" + (data.methods.size() == 1 ? "" : "s"));
    }

    private void setDisplayValue(int row, String value) {
        programmaticEdit = true;
        fieldModel.setValueAt(value, row, 2);
        programmaticEdit = false;
    }

    private void commitFieldEdit(int row, String newValue) {
        if (row < 0 || row >= fieldRows.size()) {
            return;
        }
        applySet(row, false, newValue);
    }

    private void setSelectedNull() {
        int row = fieldTable.getSelectedRow();
        if (row >= 0 && row < fieldRows.size()) {
            applySet(row, true, "");
        }
    }

    private void applySet(int row, boolean setNull, String value) {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        final StaticField field = fieldRows.get(row);
        statusLabel.setText("Setting " + field.getName() + "...");
        SwingWorkers.run(
                () -> session.setStatic(className, field.getName(), setNull, value),
                newValue -> {
                    fieldRows.set(row, new StaticField(field.getName(), field.getTypeDesc(), newValue, field.getKind()));
                    setDisplayValue(row, newValue);
                    statusLabel.setText("Set " + field.getName() + " = " + newValue);
                },
                err -> {
                    setDisplayValue(row, field.getValue());
                    statusLabel.setText("Set failed: " + err.getMessage());
                });
    }

    private void invokeSelected() {
        StaticMethod method = methodList.getSelectedValue();
        if (method == null) {
            return;
        }
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        List<String> paramTypes = DescriptorParser.parseParameterTypes(method.getDesc());
        List<String> args = new ArrayList<>();
        if (!paramTypes.isEmpty()) {
            JPanel form = new JPanel(new GridLayout(paramTypes.size(), 2, 6, 4));
            List<JTextField> inputs = new ArrayList<>();
            for (int i = 0; i < paramTypes.size(); i++) {
                String type = paramTypes.get(i);
                form.add(new JLabel("arg" + i + " (" + type + "):"));
                JTextField tf = new JTextField(isReferenceType(type) ? "null" : "");
                inputs.add(tf);
                form.add(tf);
            }
            int ok = JOptionPane.showConfirmDialog(this, form, "Invoke " + method.getName(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            for (JTextField tf : inputs) {
                args.add(tf.getText());
            }
        }
        invokeButton.setEnabled(false);
        statusLabel.setText("Invoking " + method.getName() + "...");
        SwingWorkers.run(
                () -> session.invokeStatic(className, method.getName(), method.getDesc(), args),
                result -> {
                    invokeButton.setEnabled(true);
                    statusLabel.setText(method.getName() + " -> " + result);
                },
                err -> {
                    invokeButton.setEnabled(true);
                    statusLabel.setText(method.getName() + " " + err.getMessage());
                });
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** True for non-primitive readable type names (used to pre-fill "null" in the invoke dialog). */
    private static boolean isReferenceType(String readableType) {
        switch (readableType) {
            case "boolean":
            case "byte":
            case "char":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
                return false;
            default:
                return true;
        }
    }

    /** Carries the two lists fetched off the EDT so {@link #populate} can apply them together. */
    private static final class StaticsData {
        final List<StaticField> fields;
        final List<StaticMethod> methods;

        StaticsData(List<StaticField> fields, List<StaticMethod> methods) {
            this.fields = fields;
            this.methods = methods;
        }
    }

    private static final class MethodCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof StaticMethod) {
                StaticMethod m = (StaticMethod) value;
                setText(m.getName() + "(" + DescriptorParser.formatMethodParams(m.getDesc()) + "): "
                        + DescriptorParser.formatReturnType(m.getDesc()));
            }
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
}
