package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Path bar with breadcrumb navigation and editable path input.
 * Click path segments to navigate, click empty area to edit path directly.
 */
public class PathBar extends JPanel {

    /**
     * Listener for navigation events.
     */
    public interface NavigationListener {
        void onNavigate(File directory);
    }

    private final NavigationListener listener;

    // Navigation history
    private final List<File> history = new ArrayList<>();
    private int historyIndex = -1;

    // Components
    private final JButton backButton;
    private final JButton forwardButton;
    private final JButton upButton;
    private final JPanel pathContainer;
    private final CardLayout pathCardLayout;
    private final JPanel breadcrumbPanel;
    private final JTextField pathTextField;

    /**
     * -- GETTER --
     *  Get the current directory.
     */
    @Getter
    private File currentDirectory;
    private boolean inEditMode = false;

    public PathBar(NavigationListener listener) {
        this.listener = listener;

        setLayout(new BorderLayout(4, 0));
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        setPreferredSize(new Dimension(0, 36));

        // Navigation buttons
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navButtons.setOpaque(false);

        backButton = createNavButton("<", "Go back");
        forwardButton = createNavButton(">", "Go forward");
        upButton = createNavButton("^", "Go up");

        backButton.addActionListener(e -> goBack());
        forwardButton.addActionListener(e -> goForward());
        upButton.addActionListener(e -> goUp());

        navButtons.add(backButton);
        navButtons.add(forwardButton);
        navButtons.add(upButton);

        add(navButtons, BorderLayout.WEST);

        // Path container (switches between breadcrumb and text field)
        pathCardLayout = new CardLayout();
        pathContainer = new JPanel(pathCardLayout);
        pathContainer.setOpaque(false);

        // Breadcrumb panel
        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        breadcrumbPanel.setOpaque(false);
        breadcrumbPanel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        breadcrumbPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Click on empty area enters edit mode
                enterEditMode();
            }
        });

        // Path text field (edit mode)
        pathTextField = new JTextField();
        pathTextField.setFont(JStudioTheme.getUIFont(12));
        pathTextField.setBackground(JStudioTheme.getBgTertiary());
        pathTextField.setForeground(JStudioTheme.getTextPrimary());
        pathTextField.setCaretColor(JStudioTheme.getTextPrimary());
        pathTextField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getAccent()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        pathTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    applyEditedPath();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    exitEditMode();
                }
            }
        });

        pathTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                exitEditMode();
            }
        });

        pathContainer.add(breadcrumbPanel, "breadcrumb");
        pathContainer.add(pathTextField, "edit");

        add(pathContainer, BorderLayout.CENTER);

        updateNavigationButtons();
    }

    private JButton createNavButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(JStudioTheme.getUIFont(12));
        button.setPreferredSize(new Dimension(28, 24));
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(JStudioTheme.getHover());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(JStudioTheme.getBgTertiary());
            }
        });

        return button;
    }

    /**
     * Set the current directory and update breadcrumbs.
     */
    public void setCurrentDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        this.currentDirectory = directory;

        // Add to history
        if (historyIndex < 0 || !directory.equals(history.get(historyIndex))) {
            // Remove forward history
            while (history.size() > historyIndex + 1) {
                history.remove(history.size() - 1);
            }
            history.add(directory);
            historyIndex = history.size() - 1;
        }

        updateBreadcrumbs();
        updateNavigationButtons();
    }

    /**
     * Update the breadcrumb display.
     */
    private void updateBreadcrumbs() {
        breadcrumbPanel.removeAll();

        if (currentDirectory == null) {
            breadcrumbPanel.revalidate();
            breadcrumbPanel.repaint();
            return;
        }

        // Build path segments
        List<File> segments = new ArrayList<>();
        File current = currentDirectory;
        while (current != null) {
            segments.add(0, current);
            current = current.getParentFile();
        }

        // Create breadcrumb buttons
        for (int i = 0; i < segments.size(); i++) {
            File segment = segments.get(i);

            if (i > 0) {
                JButton separator = createSeparator();
                breadcrumbPanel.add(separator);
            }

            JButton crumb = createBreadcrumb(segment);
            breadcrumbPanel.add(crumb);
        }

        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }

    private JButton createBreadcrumb(File segment) {
        String name = FileSystemWorker.getDisplayName(segment);
        if (name.isEmpty()) {
            name = segment.getAbsolutePath();
        }

        JButton button = new JButton(name);
        button.setFont(JStudioTheme.getUIFont(12));
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(JStudioTheme.getAccent());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(JStudioTheme.getTextPrimary());
            }
        });

        button.addActionListener(e -> {
            if (listener != null) {
                listener.onNavigate(segment);
            }
        });

        return button;
    }

    private JButton createSeparator() {
        JButton sep = new JButton(">");
        sep.setFont(JStudioTheme.getUIFont(10));
        sep.setForeground(JStudioTheme.getTextSecondary());
        sep.setBackground(JStudioTheme.getBgSecondary());
        sep.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        sep.setFocusPainted(false);
        sep.setContentAreaFilled(false);
        sep.setEnabled(false);
        return sep;
    }

    /**
     * Enter edit mode to type/paste a path.
     */
    public void enterEditMode() {
        if (inEditMode) {
            return;
        }

        inEditMode = true;
        pathTextField.setText(currentDirectory != null ?
                currentDirectory.getAbsolutePath() : "");
        pathCardLayout.show(pathContainer, "edit");
        pathTextField.requestFocusInWindow();
        pathTextField.selectAll();
    }

    /**
     * Exit edit mode without applying changes.
     */
    private void exitEditMode() {
        if (!inEditMode) {
            return;
        }

        inEditMode = false;
        pathCardLayout.show(pathContainer, "breadcrumb");
    }

    /**
     * Apply the edited path and navigate.
     */
    private void applyEditedPath() {
        String path = pathTextField.getText().trim();
        exitEditMode();

        if (path.isEmpty()) {
            return;
        }

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            if (listener != null) {
                listener.onNavigate(dir);
            }
        } else if (dir.exists() && dir.isFile()) {
            // If user typed a file, navigate to its parent
            File parent = dir.getParentFile();
            if (parent != null && listener != null) {
                listener.onNavigate(parent);
            }
        } else {
            // Invalid path - flash red briefly
            Color original = pathTextField.getBackground();
            Color errorBg = new Color(JStudioTheme.getError().getRed() / 3, JStudioTheme.getError().getGreen() / 6, JStudioTheme.getError().getBlue() / 6);
            pathTextField.setBackground(errorBg);
            Timer timer = new Timer(500, e -> pathTextField.setBackground(original));
            timer.setRepeats(false);
            timer.start();
            enterEditMode();
        }
    }

    /**
     * Update navigation button enabled states.
     */
    private void updateNavigationButtons() {
        backButton.setEnabled(historyIndex > 0);
        forwardButton.setEnabled(historyIndex < history.size() - 1);
        upButton.setEnabled(currentDirectory != null &&
                currentDirectory.getParentFile() != null);
    }

    /**
     * Navigate back in history.
     */
    public void goBack() {
        if (historyIndex > 0) {
            historyIndex--;
            File dir = history.get(historyIndex);
            currentDirectory = dir;
            updateBreadcrumbs();
            updateNavigationButtons();
            if (listener != null) {
                listener.onNavigate(dir);
            }
        }
    }

    /**
     * Navigate forward in history.
     */
    public void goForward() {
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            File dir = history.get(historyIndex);
            currentDirectory = dir;
            updateBreadcrumbs();
            updateNavigationButtons();
            if (listener != null) {
                listener.onNavigate(dir);
            }
        }
    }

    /**
     * Navigate to parent directory.
     */
    public void goUp() {
        if (currentDirectory != null) {
            File parent = currentDirectory.getParentFile();
            if (parent != null && listener != null) {
                listener.onNavigate(parent);
            }
        }
    }

    /**
     * Focus the path bar for editing (Ctrl+L shortcut).
     */
    public void focusPathBar() {
        enterEditMode();
    }
}
