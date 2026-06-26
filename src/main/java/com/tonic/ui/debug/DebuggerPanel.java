package com.tonic.ui.debug;

import com.tonic.event.EventBus;
import com.tonic.event.events.DebugFrameSelectedEvent;
import com.tonic.event.events.DebugPausedEvent;
import com.tonic.event.events.DebugResumedEvent;
import com.tonic.event.events.DebugSessionEvent;
import com.tonic.live.debug.DebugFrame;
import com.tonic.live.debug.DebugVariable;
import com.tonic.ui.MainFrame;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.ThemeStyles;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

/**
 * The Debugger tool window (right dock): a step toolbar (Resume / Stop), the paused thread's call stack, and a
 * read-only Variables table for the selected frame. Driven entirely by EventBus debug events; navigation back
 * into the editor is delegated to {@link MainFrame}. Variable editing and stepping come in later phases.
 */
public final class DebuggerPanel extends JPanel {

    private final MainFrame mainFrame;

    private final DefaultListModel<DebugFrame> stackModel = new DefaultListModel<>();
    private final JList<DebugFrame> stackList = new JList<>(stackModel);
    private final DefaultTableModel varsModel = new DefaultTableModel(new Object[]{"Name", "Type", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable varsTable = new JTable(varsModel);
    private final JButton resumeButton = new JButton("Resume");
    private final JLabel status = new JLabel("Running.");

    public DebuggerPanel(MainFrame mainFrame) {
        super(new BorderLayout());
        this.mainFrame = mainFrame;
        setBackground(JStudioTheme.getBgSecondary());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        registerEvents();
        updateButtons(false);
    }

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBackground(JStudioTheme.getBgSecondary());
        resumeButton.setToolTipText("Resume the target");
        resumeButton.addActionListener(e -> DebugManager.getInstance().resume());
        ThemeStyles.styleButton(resumeButton, false);
        status.setForeground(JStudioTheme.getTextSecondary());
        status.setFont(JStudioTheme.getUIFont(11));
        bar.add(resumeButton);
        bar.addSeparator();
        bar.add(status);
        return bar;
    }

    private JComponent buildBody() {
        stackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stackList.setBackground(JStudioTheme.getBgSecondary());
        stackList.setForeground(JStudioTheme.getTextPrimary());
        stackList.setSelectionBackground(JStudioTheme.getSelection());
        stackList.setSelectionForeground(JStudioTheme.getTextPrimary());
        stackList.setFont(JStudioTheme.getCodeFont(12));
        stackList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof DebugFrame) {
                    setText(((DebugFrame) value).getDisplay());
                }
                return this;
            }
        });
        stackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onFrameSelected();
            }
        });
        varsTable.setFillsViewportHeight(true);
        ThemeStyles.styleTable(varsTable);
        ThemeStyles.styleTableHeader(varsTable);
        JScrollPane stackScroll = new JScrollPane(stackList);
        JScrollPane varsScroll = new JScrollPane(varsTable);
        stackScroll.setBorder(null);
        varsScroll.setBorder(null);
        stackScroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        varsScroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stackScroll, varsScroll);
        split.setResizeWeight(0.4);
        split.setBorder(null);
        return split;
    }

    private void registerEvents() {
        EventBus.getInstance().register(DebugPausedEvent.class,
                e -> SwingUtilities.invokeLater(() -> onPaused(e.getFrames())));
        EventBus.getInstance().register(DebugResumedEvent.class,
                e -> SwingUtilities.invokeLater(this::onResumed));
        EventBus.getInstance().register(DebugSessionEvent.class, e -> SwingUtilities.invokeLater(() -> {
            if (!e.isConnected()) {
                onResumed();
            }
        }));
    }

    private void onPaused(List<DebugFrame> frames) {
        stackModel.clear();
        for (DebugFrame f : frames) {
            stackModel.addElement(f);
        }
        status.setText("Paused.");
        updateButtons(true);
        if (!stackModel.isEmpty()) {
            stackList.setSelectedIndex(0);
        } else {
            varsModel.setRowCount(0);
        }
    }

    private void onResumed() {
        status.setText("Running.");
        updateButtons(false);
        stackModel.clear();
        varsModel.setRowCount(0);
    }

    private void onFrameSelected() {
        DebugFrame frame = stackList.getSelectedValue();
        if (frame == null) {
            return;
        }
        mainFrame.navigateToDebugLocation(frame.getLocation());
        EventBus.getInstance().post(new DebugFrameSelectedEvent(this, frame));
        varsModel.setRowCount(0);
        for (DebugVariable v : DebugManager.getInstance().variables(frame.getIndex())) {
            varsModel.addRow(new Object[]{v.getName(), prettyType(v.getTypeDescriptor()), v.getDisplay()});
        }
    }

    private void updateButtons(boolean paused) {
        resumeButton.setEnabled(paused);
    }

    /** Best-effort readable type from a JVM descriptor (e.g. {@code Ljava/lang/String;} -> {@code String}). */
    private static String prettyType(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "";
        }
        int arrays = 0;
        int i = 0;
        while (i < desc.length() && desc.charAt(i) == '[') {
            arrays++;
            i++;
        }
        String base;
        switch (desc.charAt(i)) {
            case 'L':
                String cn = desc.substring(i + 1, desc.endsWith(";") ? desc.length() - 1 : desc.length());
                base = cn.substring(cn.lastIndexOf('/') + 1);
                break;
            case 'Z': base = "boolean"; break;
            case 'B': base = "byte"; break;
            case 'C': base = "char"; break;
            case 'S': base = "short"; break;
            case 'I': base = "int"; break;
            case 'J': base = "long"; break;
            case 'F': base = "float"; break;
            case 'D': base = "double"; break;
            default: base = desc;
        }
        return base + "[]".repeat(Math.max(0, arrays));
    }
}
