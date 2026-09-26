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

/** A themed combo box of file filters that always ends with All Files. */
public class FileFilterComboBox extends JComboBox<ExtensionFileFilter>
{

    /** A callback for filter selection changes. */
    public interface FilterChangeListener
    {
        /**
         * Called when the selected filter changes.
         *
         * @param filter the newly selected filter, or null if the list was emptied
         */
        void onFilterChanged(ExtensionFileFilter filter);
    }

    private final DefaultComboBoxModel<ExtensionFileFilter> model;
    private FilterChangeListener listener;

    /** Creates a combo box holding only All Files. */
    public FileFilterComboBox()
    {
        model = new DefaultComboBoxModel<>();
        setModel(model);

        setBackground(JStudioTheme.getBgTertiary());
        setForeground(JStudioTheme.getTextPrimary());
        setFont(JStudioTheme.getUIFont(12));
        setPreferredSize(new Dimension(180, 28));
        setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        setRenderer(new FilterRenderer());

        model.addElement(ExtensionFileFilter.allFiles());

        addActionListener(e ->
        {
            if (listener != null)
            {
                ExtensionFileFilter selected = (ExtensionFileFilter) getSelectedItem();
                listener.onFilterChanged(selected);
            }
        });
    }

    /**
     * Sets the listener told about selection changes, replacing any previous one.
     *
     * @param listener the listener, or null for none
     */
    public void setFilterChangeListener(FilterChangeListener listener)
    {
        this.listener = listener;
    }

    /**
     * Replaces the filters, drops nulls and All Files filters, appends All Files and selects the first.
     *
     * @param filters the filters, or null for All Files alone
     */
    public void setFilters(ExtensionFileFilter... filters)
    {
        model.removeAllElements();

        if (filters != null)
        {
            for (ExtensionFileFilter filter : filters)
            {
                if (filter != null && !filter.isAllFiles())
                {
                    model.addElement(filter);
                }
            }
        }

        model.addElement(ExtensionFileFilter.allFiles());

        if (model.getSize() > 0)
        {
            setSelectedIndex(0);
        }
    }

    /**
     * The selected filter.
     *
     * @return the filter, or null if none is selected
     */
    public ExtensionFileFilter getSelectedFilter()
    {
        return (ExtensionFileFilter) getSelectedItem();
    }

    /**
     * Lists the filters in display order.
     *
     * @return a new list, ending with All Files
     */
    public List<ExtensionFileFilter> getFilters()
    {
        List<ExtensionFileFilter> result = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++)
        {
            result.add(model.getElementAt(i));
        }
        return result;
    }

    private static class FilterRenderer extends JLabel implements ListCellRenderer<ExtensionFileFilter>
    {

        FilterRenderer()
        {
            setOpaque(true);
            setFont(JStudioTheme.getUIFont(12));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ExtensionFileFilter> list, ExtensionFileFilter value, int index, boolean isSelected, boolean cellHasFocus)
        {
            if (isSelected)
            {
                setBackground(JStudioTheme.getSelection());
                setForeground(JStudioTheme.getTextPrimary());
            }
            else
            {
                setBackground(JStudioTheme.getBgTertiary());
                setForeground(JStudioTheme.getTextPrimary());
            }

            if (value != null)
            {
                setText(value.getDescription());
            }
            else
            {
                setText("");
            }

            return this;
        }
    }
}
