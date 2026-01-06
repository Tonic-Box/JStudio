package com.tonic.ui;

import com.tonic.ui.editor.ViewMode;
import com.tonic.ui.editor.ViewModeComboBox;
import com.tonic.ui.theme.*;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Builds the main toolbar for JStudio.
 */
public class ToolbarBuilder implements ThemeChangeListener {

    private final MainFrame mainFrame;
    private JToolBar toolbar;
    private JTextField searchField;
    private ViewModeComboBox viewModeCombo;
    private JToggleButton omitAnnotationsButton;

    public ToolbarBuilder(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        if (toolbar != null) {
            toolbar.setBackground(JStudioTheme.getBgPrimary());
            toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        }
        if (searchField != null) {
            searchField.setBackground(JStudioTheme.getBgTertiary());
            searchField.setForeground(JStudioTheme.getTextPrimary());
            searchField.setCaretColor(JStudioTheme.getTextPrimary());
            searchField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)
            ));
        }
    }

    public JToolBar build() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        toolbar.setBackground(JStudioTheme.getBgPrimary());

        // File operations
        toolbar.add(createButton(Icons.getIcon("open"), "Open JAR/Class (Ctrl+O)", e -> mainFrame.showOpenDialog()));
        toolbar.add(createButton(Icons.getIcon("save"), "Export Class (Ctrl+Shift+E)", e -> mainFrame.exportCurrentClass()));
        toolbar.addSeparator();

        // Navigation
        toolbar.add(createButton(Icons.getIcon("back"), "Navigate Back (Alt+Left)", e -> mainFrame.navigateBack()));
        toolbar.add(createButton(Icons.getIcon("forward"), "Navigate Forward (Alt+Right)", e -> mainFrame.navigateForward()));
        toolbar.addSeparator();

        // View mode dropdown
        viewModeCombo = new ViewModeComboBox();
        viewModeCombo.addActionListener(e -> {
            ViewMode mode = viewModeCombo.getSelectedViewMode();
            mainFrame.switchToView(mode);
        });
        toolbar.add(viewModeCombo);
        toolbar.addSeparator();

        omitAnnotationsButton = new JToggleButton(Icons.getIcon("annotation"));
        omitAnnotationsButton.setToolTipText("Hide Annotations");
        omitAnnotationsButton.setFocusable(false);
        omitAnnotationsButton.setBorderPainted(false);
        omitAnnotationsButton.setPreferredSize(new Dimension(32, 32));
        omitAnnotationsButton.addActionListener(e ->
                mainFrame.setOmitAnnotations(omitAnnotationsButton.isSelected()));
        toolbar.add(omitAnnotationsButton);

        toolbar.addSeparator();

        // Bookmarks & Comments
        toolbar.add(createButton(Icons.getIcon("bookmark"), "Add Bookmark (Ctrl+B)", e -> mainFrame.addBookmarkAtCurrentLocation()));
        toolbar.add(createButton(Icons.getIcon("comment"), "Add Comment (Ctrl+;)", e -> mainFrame.addCommentAtCurrentLocation()));
        toolbar.addSeparator();

        // Analysis
        toolbar.add(createButton(Icons.getIcon("analyze"), "Run Analysis (F9)", e -> mainFrame.runAnalysis()));
        toolbar.add(createButton(Icons.getIcon("callgraph"), "Show Call Graph (Ctrl+Shift+G)", e -> mainFrame.showCallGraph()));
        toolbar.add(createButton(Icons.getIcon("transform"), "Apply Transforms (Ctrl+Shift+T)", e -> mainFrame.showTransformDialog()));
        toolbar.addSeparator();

        // Refresh
        toolbar.add(createButton(Icons.getIcon("refresh"), "Refresh (Ctrl+F5)", e -> mainFrame.refreshCurrentView()));

        // Spacer to push search to the right
        toolbar.add(Box.createHorizontalGlue());

        // Search field
        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(200, 28));
        searchField.setPreferredSize(new Dimension(200, 28));
        searchField.putClientProperty("JTextField.placeholderText", "Search classes...");
        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    mainFrame.searchClasses(searchField.getText());
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchField.setText("");
                    mainFrame.requestFocus();
                }
            }
        });

        toolbar.add(searchField);
        toolbar.add(Box.createHorizontalStrut(8));

        return toolbar;
    }

    private JButton createButton(javax.swing.Icon icon, String tooltip, ActionListener action) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(32, 32));
        button.addActionListener(action);

        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });

        return button;
    }

    public void setViewMode(ViewMode mode) {
        viewModeCombo.setSelectedViewMode(mode);
    }
}
