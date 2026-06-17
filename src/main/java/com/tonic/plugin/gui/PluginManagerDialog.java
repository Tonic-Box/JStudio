package com.tonic.plugin.gui;

import com.tonic.plugin.api.PluginInfo;
import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Frame;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Lists the GUI plugins discovered from {@code ~/.jstudio/plugins/} and lets the user enable, disable, or reload
 * them and inspect load errors. Reachable from the Plugins menu.
 */
public final class PluginManagerDialog extends ThemedJDialog {

    private final DefaultListModel<LoadedPlugin> model = new DefaultListModel<>();
    private final JList<LoadedPlugin> list = new JList<>(model);
    private final JTextArea details = new JTextArea();
    private final JButton enableButton = new JButton("Enable");
    private final JButton disableButton = new JButton("Disable");
    private final JButton reloadButton = new JButton("Reload");

    public PluginManagerDialog(Frame owner) {
        super(owner, "Plugins", true);
        buildUi();
        refresh();
        setSize(720, 440);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.setBackground(JStudioTheme.getBgPrimary());

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new PluginCellRenderer());
        list.setBackground(JStudioTheme.getBgSecondary());
        list.setForeground(JStudioTheme.getTextPrimary());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(JStudioTheme.getCodeFont(12));
        details.setBackground(JStudioTheme.getBgTertiary());
        details.setForeground(JStudioTheme.getTextPrimary());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list), new JScrollPane(details));
        split.setResizeWeight(0.45);
        split.setBorder(null);
        content.add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        enableButton.addActionListener(e -> withSelection(GuiPluginManager.getInstance()::enable));
        disableButton.addActionListener(e -> withSelection(GuiPluginManager.getInstance()::disable));
        reloadButton.addActionListener(e -> withSelection(GuiPluginManager.getInstance()::reload));
        left.add(enableButton);
        left.add(disableButton);
        left.add(reloadButton);

        JPanel right = new JPanel();
        right.setOpaque(false);
        JButton reloadAll = new JButton("Reload All");
        reloadAll.addActionListener(e -> {
            GuiPluginManager.getInstance().reloadAll();
            refresh();
        });
        JButton openFolder = new JButton("Open Folder");
        openFolder.addActionListener(e -> openPluginsFolder());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        right.add(reloadAll);
        right.add(openFolder);
        right.add(Box.createHorizontalStrut(12));
        right.add(close);

        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void withSelection(java.util.function.Consumer<LoadedPlugin> action) {
        LoadedPlugin selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        action.accept(selected);
        refresh();
    }

    private void refresh() {
        LoadedPlugin previouslySelected = list.getSelectedValue();
        model.clear();
        for (LoadedPlugin lp : GuiPluginManager.getInstance().getPlugins()) {
            model.addElement(lp);
        }
        if (previouslySelected != null) {
            int index = indexOfId(previouslySelected.info.getId());
            if (index >= 0) {
                list.setSelectedIndex(index);
            }
        } else if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        onSelectionChanged();
    }

    private int indexOfId(String id) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).info.getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void onSelectionChanged() {
        LoadedPlugin selected = list.getSelectedValue();
        if (selected == null) {
            details.setText("");
            enableButton.setEnabled(false);
            disableButton.setEnabled(false);
            reloadButton.setEnabled(false);
            return;
        }
        details.setText(describe(selected));
        details.setCaretPosition(0);
        enableButton.setEnabled(selected.isUi() && selected.state != LoadedPlugin.State.ENABLED);
        disableButton.setEnabled(selected.state == LoadedPlugin.State.ENABLED);
        reloadButton.setEnabled(selected.plugin != null);
    }

    private static String describe(LoadedPlugin lp) {
        PluginInfo info = lp.info;
        StringBuilder sb = new StringBuilder();
        sb.append("Name:    ").append(info.getName()).append('\n');
        sb.append("Id:      ").append(info.getId()).append('\n');
        sb.append("Version: ").append(info.getVersion()).append('\n');
        if (info.getAuthor() != null && !info.getAuthor().isEmpty()) {
            sb.append("Author:  ").append(info.getAuthor()).append('\n');
        }
        sb.append("State:   ").append(lp.state).append('\n');
        sb.append("Jar:     ").append(lp.jar.getName()).append('\n');
        if (info.getDescription() != null && !info.getDescription().isEmpty()) {
            sb.append('\n').append(info.getDescription()).append('\n');
        }
        if (lp.error != null) {
            sb.append("\n--- Error ---\n").append(stackTrace(lp.error));
        }
        return sb.toString();
    }

    private static String stackTrace(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private void openPluginsFolder() {
        try {
            Desktop.getDesktop().open(GuiPluginManager.getInstance().pluginsDir());
        } catch (Exception ignored) {
        }
    }

    /** Renders a plugin row as "name vVersion  [STATE]", colored by state. */
    private static final class PluginCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            LoadedPlugin lp = (LoadedPlugin) value;
            setText(lp.info.getName() + "  v" + lp.info.getVersion() + "   [" + lp.state + "]");
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            if (!isSelected) {
                setForeground(stateColor(lp.state));
                setBackground(JStudioTheme.getBgSecondary());
            }
            return this;
        }

        private static java.awt.Color stateColor(LoadedPlugin.State state) {
            switch (state) {
                case ENABLED:
                    return JStudioTheme.getSuccess();
                case ERROR:
                    return JStudioTheme.getError();
                case NOT_UI:
                    return JStudioTheme.getTextDisabled();
                default:
                    return JStudioTheme.getTextSecondary();
            }
        }
    }
}
