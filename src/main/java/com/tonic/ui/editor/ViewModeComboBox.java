package com.tonic.ui.editor;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ViewModeComboBox extends JComboBox<Object> {

    public static final String HEADER_PREFIX = "# ";

    private static final Map<ViewMode, String> SHORTCUTS = new LinkedHashMap<>();
    static {
        SHORTCUTS.put(ViewMode.SOURCE, "F5");
        SHORTCUTS.put(ViewMode.BYTECODE, "F6");
        SHORTCUTS.put(ViewMode.IR, "F7");
        SHORTCUTS.put(ViewMode.AST, "F8");
        SHORTCUTS.put(ViewMode.HEX, "");
    }

    public ViewModeComboBox() {
        setFont(JStudioTheme.getCodeFont(11));
        setMaximumSize(new Dimension(180, 25));
        setPreferredSize(new Dimension(150, 25));

        addItem(HEADER_PREFIX + "Code");
        addItem(ViewMode.SOURCE);
        addItem(ViewMode.BYTECODE);

        addItem(HEADER_PREFIX + "IR");
        addItem(ViewMode.IR);
        addItem(ViewMode.AST);

        addItem(HEADER_PREFIX + "Graphing");
        addItem(ViewMode.CFG);
        addItem(ViewMode.PDG);
        addItem(ViewMode.SDG);
        addItem(ViewMode.CPG);
        addItem(ViewMode.CALLGRAPH);

        addItem(HEADER_PREFIX + "Other");
        addItem(ViewMode.CONSTPOOL);
        addItem(ViewMode.ATTRIBUTES);
        addItem(ViewMode.STATISTICS);
        addItem(ViewMode.HEX);

        setRenderer(new ViewModeListCellRenderer());
        setSelectedItem(ViewMode.SOURCE);

        addActionListener(e -> {
            Object selected = getSelectedItem();
            if (selected instanceof String && ((String) selected).startsWith(HEADER_PREFIX)) {
                setSelectedItem(ViewMode.SOURCE);
            }
        });
    }

    public ViewMode getSelectedViewMode() {
        Object selected = getSelectedItem();
        if (selected instanceof ViewMode) {
            return (ViewMode) selected;
        }
        return ViewMode.SOURCE;
    }

    public void setSelectedViewMode(ViewMode mode) {
        setSelectedItem(mode);
    }

    @Override
    public void setSelectedItem(Object item) {
        if (!(item instanceof String) || !((String) item).startsWith(HEADER_PREFIX)) {
            super.setSelectedItem(item);
        }
    }

    private static class ViewModeListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                list, value, index, false, false);

            if (value instanceof String && ((String) value).startsWith(HEADER_PREFIX)) {
                String headerText = ((String) value).substring(HEADER_PREFIX.length());
                label.setText(headerText);
                label.setFont(JStudioTheme.getCodeFont(10).deriveFont(Font.BOLD));
                label.setForeground(JStudioTheme.getTextSecondary());
                label.setBackground(JStudioTheme.getBgPrimary());
                label.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
                label.setEnabled(false);
                return label;
            }

            if (value instanceof ViewMode) {
                ViewMode mode = (ViewMode) value;
                String shortcut = SHORTCUTS.get(mode);

                if (shortcut != null && !shortcut.isEmpty()) {
                    label.setText(String.format("<html><b>%s</b> <span style='color:gray'>(%s)</span></html>",
                        mode.getDisplayName(), shortcut));
                } else {
                    label.setText(mode.getDisplayName());
                }

                label.setToolTipText(mode.getDescription());
            }

            label.setFont(JStudioTheme.getCodeFont(11));
            label.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 6));

            if (isSelected) {
                label.setBackground(JStudioTheme.getAccent());
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(JStudioTheme.getBgSecondary());
                label.setForeground(JStudioTheme.getTextPrimary());
            }

            return label;
        }
    }
}
