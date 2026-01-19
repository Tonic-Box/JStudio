package com.tonic.ui.core.component;

import lombok.Getter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FilterableComboBox<T> extends JComboBox<T> {

    private List<T> allItems = new ArrayList<>();
    private final Function<T, String> textExtractor;
    @Getter
    private boolean filtering = false;
    private boolean selecting = false;
    private boolean editorFocused = false;
    private T lastSelectedItem = null;

    public FilterableComboBox(Function<T, String> textExtractor) {
        super(new DefaultComboBoxModel<>());
        this.textExtractor = textExtractor;
        setEditable(true);
        setupFilter();
    }

    public void setAllItems(List<T> items) {
        filtering = true;
        try {
            allItems = new ArrayList<>(items);
            refreshModel("");
            if (!allItems.isEmpty()) {
                T first = allItems.get(0);
                setSelectedItem(first);
                lastSelectedItem = first;
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                editor.setText(textExtractor.apply(first));
            } else {
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                editor.setText("");
            }
        } finally {
            filtering = false;
        }
    }

    public void addAllItem(T item) {
        allItems.add(item);
    }

    public void clearAllItems() {
        allItems.clear();
        ((DefaultComboBoxModel<T>) getModel()).removeAllElements();
    }

    private void setupFilter() {
        JTextField editor = (JTextField) getEditor().getEditorComponent();

        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                editorFocused = true;
            }

            @Override
            public void focusLost(FocusEvent e) {
                editorFocused = false;
                restoreFullList();
            }
        });

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });

        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    editor.setText("");
                    hidePopup();
                    if (lastSelectedItem != null) {
                        setSelectedItem(lastSelectedItem);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    Object selected = getSelectedItem();
                    if (selected != null && allItems.contains(selected)) {
                        lastSelectedItem = (T) selected;
                        hidePopup();
                        editor.setText(textExtractor.apply(lastSelectedItem));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!isPopupVisible() && getModel().getSize() > 0) {
                        showPopup();
                    }
                }
            }
        });
    }

    private void restoreFullList() {
        filtering = true;
        try {
            refreshModel("");
            if (lastSelectedItem != null) {
                setSelectedItem(lastSelectedItem);
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                editor.setText(textExtractor.apply(lastSelectedItem));
            }
        } finally {
            filtering = false;
        }
    }

    private void filter() {
        if (filtering || selecting || !editorFocused) return;

        SwingUtilities.invokeLater(() -> {
            if (selecting) return;
            filtering = true;
            try {
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                String text = editor.getText();
                int caretPosition = editor.getCaretPosition();

                refreshModel(text);

                editor.setText(text);
                editor.setCaretPosition(Math.min(caretPosition, text.length()));

                if (!text.isEmpty() && getModel().getSize() > 0 && isShowing()) {
                    showPopup();
                }
            } finally {
                filtering = false;
            }
        });
    }

    private void refreshModel(String filterText) {
        DefaultComboBoxModel<T> model = (DefaultComboBoxModel<T>) getModel();
        model.removeAllElements();

        String lower = filterText.toLowerCase();
        for (T item : allItems) {
            String itemText = textExtractor.apply(item);
            if (itemText.toLowerCase().contains(lower)) {
                model.addElement(item);
            }
        }
    }

    @Override
    public void setSelectedItem(Object item) {
        selecting = true;
        try {
            super.setSelectedItem(item);
            if (item != null && allItems.contains(item)) {
                lastSelectedItem = (T) item;
                JTextField editor = (JTextField) getEditor().getEditorComponent();
                editor.setText(textExtractor.apply(lastSelectedItem));
            }
        } finally {
            selecting = false;
        }
    }
}
