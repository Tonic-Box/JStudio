package com.tonic.ui.editor.source;

import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class FloatingCompileToolbar extends JPanel implements ThemeChangeListener {

    private final JButton compileButton;
    private final JButton discardButton;
    private final JLabel statusLabel;
    private final JButton toggleButton;

    private final JPanel errorListPanel;
    private final JList<CompilationError> errorList;
    private final DefaultListModel<CompilationError> errorListModel;
    private boolean errorListExpanded = false;
    private Consumer<Integer> lineNavigator;

    public FloatingCompileToolbar(Runnable onCompile, Runnable onDiscard) {

        setLayout(new BorderLayout());
        setOpaque(true);

        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        mainRow.setOpaque(false);

        statusLabel = new JLabel("Source modified");
        statusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!errorListModel.isEmpty()) {
                    toggleErrorList();
                }
            }
        });
        mainRow.add(statusLabel);

        toggleButton = new JButton("▼");
        toggleButton.setFont(JStudioTheme.getUIFont(10));
        toggleButton.setFocusPainted(false);
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setPreferredSize(new Dimension(24, 20));
        toggleButton.addActionListener(e -> toggleErrorList());
        toggleButton.setVisible(false);
        mainRow.add(toggleButton);

        compileButton = createButton("Recompile", "compile");
        compileButton.addActionListener(e -> {
            if (onCompile != null) {
                onCompile.run();
            }
        });
        mainRow.add(compileButton);

        discardButton = createButton("Discard", "undo");
        discardButton.addActionListener(e -> {
            if (onDiscard != null) {
                onDiscard.run();
            }
        });
        mainRow.add(discardButton);

        add(mainRow, BorderLayout.NORTH);

        errorListModel = new DefaultListModel<>();
        errorList = new JList<>(errorListModel);
        errorList.setCellRenderer(new ErrorCellRenderer());
        errorList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && lineNavigator != null) {
                    CompilationError selected = errorList.getSelectedValue();
                    if (selected != null && selected.getLine() > 0) {
                        lineNavigator.accept(selected.getLine());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(errorList);
        scrollPane.setPreferredSize(new Dimension(0, 120));
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        errorListPanel = new JPanel(new BorderLayout());
        errorListPanel.setOpaque(false);
        errorListPanel.add(scrollPane, BorderLayout.CENTER);
        errorListPanel.setVisible(false);

        add(errorListPanel, BorderLayout.CENTER);

        applyTheme();
        ThemeManager.getInstance().addThemeChangeListener(this);

        setVisible(false);
    }

    private void toggleErrorList() {
        errorListExpanded = !errorListExpanded;
        errorListPanel.setVisible(errorListExpanded);
        toggleButton.setText(errorListExpanded ? "▲" : "▼");
        revalidate();
        repaint();
    }

    public void setLineNavigator(Consumer<Integer> navigator) {
        this.lineNavigator = navigator;
    }

    private JButton createButton(String text, String iconName) {
        JButton button = new JButton(text, Icons.getIcon(iconName, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(JStudioTheme.getUIFont(11));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });

        return button;
    }

    public void showModified() {
        statusLabel.setText("Source modified");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        compileButton.setEnabled(true);
        errorListModel.clear();
        toggleButton.setVisible(false);
        errorListPanel.setVisible(false);
        errorListExpanded = false;
        setVisible(true);
        revalidate();
        repaint();
    }

    public void showWithErrors(int errorCount, int warningCount) {
        showWithErrors(errorCount, warningCount, Collections.emptyList());
    }

    public void showWithErrors(int errorCount, int warningCount, List<CompilationError> errors) {
        errorListModel.clear();
        for (CompilationError error : errors) {
            errorListModel.addElement(error);
        }

        if (errorCount > 0) {
            statusLabel.setText(errorCount + " error" + (errorCount == 1 ? "" : "s") +
                    (warningCount > 0 ? ", " + warningCount + " warning" + (warningCount == 1 ? "" : "s") : ""));
            statusLabel.setForeground(JStudioTheme.getError());
            compileButton.setEnabled(false);
        } else if (warningCount > 0) {
            statusLabel.setText(warningCount + " warning" + (warningCount == 1 ? "" : "s"));
            statusLabel.setForeground(JStudioTheme.getWarning());
            compileButton.setEnabled(true);
        } else {
            statusLabel.setText("Ready to compile");
            statusLabel.setForeground(JStudioTheme.getSuccess());
            compileButton.setEnabled(true);
        }

        toggleButton.setVisible(!errors.isEmpty());
        toggleButton.setText("▼");

        if (!errors.isEmpty() && !errorListExpanded) {
            errorListExpanded = true;
            errorListPanel.setVisible(true);
            toggleButton.setText("▲");
        }

        setVisible(true);
        revalidate();
        repaint();
    }

    public void showCompiling() {
        statusLabel.setText("Compiling...");
        statusLabel.setForeground(JStudioTheme.getInfo());
        compileButton.setEnabled(false);
        discardButton.setEnabled(false);
        revalidate();
        repaint();
    }

    public void showSuccess(long timeMs) {
        statusLabel.setText("Compiled successfully (" + timeMs + "ms)");
        statusLabel.setForeground(JStudioTheme.getSuccess());
        compileButton.setEnabled(true);
        discardButton.setEnabled(true);
        errorListModel.clear();
        toggleButton.setVisible(false);
        errorListPanel.setVisible(false);
        errorListExpanded = false;
        revalidate();
        repaint();
    }

    public void hideToolbar() {
        setVisible(false);
        errorListModel.clear();
        errorListPanel.setVisible(false);
        errorListExpanded = false;
        revalidate();
        repaint();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        applyTheme();
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        statusLabel.setFont(JStudioTheme.getUIFont(11));
        compileButton.setFont(JStudioTheme.getUIFont(11));
        compileButton.setForeground(JStudioTheme.getTextPrimary());
        compileButton.setBackground(JStudioTheme.getHover());
        discardButton.setFont(JStudioTheme.getUIFont(11));
        discardButton.setForeground(JStudioTheme.getTextPrimary());
        discardButton.setBackground(JStudioTheme.getHover());
        toggleButton.setForeground(JStudioTheme.getTextSecondary());

        errorList.setBackground(JStudioTheme.getBgTertiary());
        errorList.setForeground(JStudioTheme.getTextPrimary());
        errorList.setFont(JStudioTheme.getCodeFont(12));
    }

    public void dispose() {
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }

    private static class ErrorCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof CompilationError) {
                CompilationError error = (CompilationError) value;
                String prefix = error.isError() ? "[ERROR]" : "[WARN]";
                String lineInfo = error.getLine() > 0 ? " Line " + error.getLine() + ":" : "";
                setText(prefix + lineInfo + " " + error.getMessage());

                if (!isSelected) {
                    setForeground(error.isError() ? JStudioTheme.getError() : JStudioTheme.getWarning());
                    setBackground(JStudioTheme.getBgTertiary());
                }
            }

            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            return this;
        }
    }
}
