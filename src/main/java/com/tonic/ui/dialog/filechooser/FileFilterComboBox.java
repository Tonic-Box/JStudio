package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.BorderFactory;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Combo box for file type filters.
 */
public class FileFilterComboBox extends JComboBox<ExtensionFileFilter> {

    /**
     * Listener for filter changes.
     */
    public interface FilterChangeListener {
        void onFilterChanged(ExtensionFileFilter filter);
    }

    private final DefaultComboBoxModel<ExtensionFileFilter> model;
    private FilterChangeListener listener;

    public FileFilterComboBox() {
        model = new DefaultComboBoxModel<>();
        setModel(model);

        setBackground(JStudioTheme.getBgTertiary());
        setForeground(JStudioTheme.getTextPrimary());
        setFont(JStudioTheme.getUIFont(12));
        setPreferredSize(new Dimension(180, 28));
        setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        setRenderer(new FilterRenderer());

        // Always include "All Files" option
        model.addElement(ExtensionFileFilter.allFiles());

        addActionListener(e -> {
            if (listener != null) {
                ExtensionFileFilter selected = (ExtensionFileFilter) getSelectedItem();
                listener.onFilterChanged(selected);
            }
        });
    }

    /**
     * Set the filter change listener.
     */
    public void setFilterChangeListener(FilterChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Set the available filters.
     */
    public void setFilters(ExtensionFileFilter... filters) {
        model.removeAllElements();

        // Add custom filters first
        if (filters != null) {
            for (ExtensionFileFilter filter : filters) {
                if (filter != null && !filter.isAllFiles()) {
                    model.addElement(filter);
                }
            }
        }

        // Always add "All Files" at the end
        model.addElement(ExtensionFileFilter.allFiles());

        // Select first filter
        if (model.getSize() > 0) {
            setSelectedIndex(0);
        }
    }

    /**
     * Get the currently selected filter.
     */
    public ExtensionFileFilter getSelectedFilter() {
        return (ExtensionFileFilter) getSelectedItem();
    }

    /**
     * Get all available filters.
     */
    public List<ExtensionFileFilter> getFilters() {
        List<ExtensionFileFilter> result = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            result.add(model.getElementAt(i));
        }
        return result;
    }

    /**
     * Custom renderer for filter items.
     */
    private static class FilterRenderer extends JLabel implements ListCellRenderer<ExtensionFileFilter> {

        FilterRenderer() {
            setOpaque(true);
            setFont(JStudioTheme.getUIFont(12));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ExtensionFileFilter> list,
                                                      ExtensionFileFilter value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (isSelected) {
                setBackground(JStudioTheme.getSelection());
                setForeground(JStudioTheme.getTextPrimary());
            } else {
                setBackground(JStudioTheme.getBgTertiary());
                setForeground(JStudioTheme.getTextPrimary());
            }

            if (value != null) {
                setText(value.getDescription());
            } else {
                setText("");
            }

            return this;
        }
    }
}
