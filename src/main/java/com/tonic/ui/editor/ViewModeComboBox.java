package com.tonic.ui.editor;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ViewModeComboBox extends JComboBox<Object> {

    public static final String SEPARATOR = "---";

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

        addItem(ViewMode.SOURCE);
        addItem(SEPARATOR);
        addItem(ViewMode.BYTECODE);
        addItem(ViewMode.CONSTPOOL);
        addItem(ViewMode.IR);
        addItem(SEPARATOR);
        addItem(ViewMode.AST);
        addItem(ViewMode.PDG);
        addItem(ViewMode.SDG);
        addItem(ViewMode.CPG);
        addItem(SEPARATOR);
        addItem(ViewMode.HEX);

        setRenderer(new ViewModeListCellRenderer());

        addActionListener(e -> {
            Object selected = getSelectedItem();
            if (SEPARATOR.equals(selected)) {
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
        if (!SEPARATOR.equals(item)) {
            super.setSelectedItem(item);
        }
    }

    private static class ViewModeListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            if (SEPARATOR.equals(value)) {
                JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
                sep.setPreferredSize(new Dimension(0, 5));
                return sep;
            }

            JLabel label = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);

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
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

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
