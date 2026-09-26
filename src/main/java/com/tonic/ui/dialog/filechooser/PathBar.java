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

/** The file chooser's navigation bar: back, forward and up buttons plus clickable breadcrumbs that switch to an editable path field. */
public class PathBar extends JPanel
{

    /** A callback for navigation requests. */
    public interface NavigationListener
    {
        /**
         * Called when the user asks to go to a directory.
         *
         * @param directory the directory
         */
        void onNavigate(File directory);
    }

    private final NavigationListener listener;

    private final List<File> history = new ArrayList<>();
    private int historyIndex = -1;

    private final JButton backButton;
    private final JButton forwardButton;
    private final JButton upButton;
    private final JPanel pathContainer;
    private final CardLayout pathCardLayout;
    private final JPanel breadcrumbPanel;
    private final JTextField pathTextField;

    /** The directory shown, or null before one is set. */
    @Getter
    private File currentDirectory;
    private boolean inEditMode = false;

    /**
     * Creates the bar with no directory shown.
     *
     * @param listener told about every navigation request
     */
    public PathBar(NavigationListener listener)
    {
        this.listener = listener;

        setLayout(new BorderLayout(4, 0));
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        setPreferredSize(new Dimension(0, 36));

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

        pathCardLayout = new CardLayout();
        pathContainer = new JPanel(pathCardLayout);
        pathContainer.setOpaque(false);

        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        breadcrumbPanel.setOpaque(false);
        breadcrumbPanel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        breadcrumbPanel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                enterEditMode();
            }
        });

        pathTextField = new JTextField();
        pathTextField.setFont(JStudioTheme.getUIFont(12));
        pathTextField.setBackground(JStudioTheme.getBgTertiary());
        pathTextField.setForeground(JStudioTheme.getTextPrimary());
        pathTextField.setCaretColor(JStudioTheme.getTextPrimary());
        pathTextField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JStudioTheme.getAccent()), BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        pathTextField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    applyEditedPath();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    exitEditMode();
                }
            }
        });

        pathTextField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                exitEditMode();
            }
        });

        pathContainer.add(breadcrumbPanel, "breadcrumb");
        pathContainer.add(pathTextField, "edit");

        add(pathContainer, BorderLayout.CENTER);

        updateNavigationButtons();
    }

    private JButton createNavButton(String text, String tooltip)
    {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(JStudioTheme.getUIFont(12));
        button.setPreferredSize(new Dimension(28, 24));
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (button.isEnabled())
                {
                    button.setBackground(JStudioTheme.getHover());
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(JStudioTheme.getBgTertiary());
            }
        });

        return button;
    }

    /**
     * Shows a directory, adding it to the history after the current point unless it is already the current entry; ignores null and missing paths.
     *
     * @param directory the directory
     */
    public void setCurrentDirectory(File directory)
    {
        if (directory == null || !directory.exists())
        {
            return;
        }

        this.currentDirectory = directory;

        if (historyIndex < 0 || !directory.equals(history.get(historyIndex)))
        {
            while (history.size() > historyIndex + 1)
            {
                history.remove(history.size() - 1);
            }
            history.add(directory);
            historyIndex = history.size() - 1;
        }

        updateBreadcrumbs();
        updateNavigationButtons();
    }

    private void updateBreadcrumbs()
    {
        breadcrumbPanel.removeAll();

        if (currentDirectory == null)
        {
            breadcrumbPanel.revalidate();
            breadcrumbPanel.repaint();
            return;
        }

        List<File> segments = new ArrayList<>();
        File current = currentDirectory;
        while (current != null)
        {
            segments.add(0, current);
            current = current.getParentFile();
        }

        for (int i = 0; i < segments.size(); i++)
        {
            File segment = segments.get(i);

            if (i > 0)
            {
                JButton separator = createSeparator();
                breadcrumbPanel.add(separator);
            }

            JButton crumb = createBreadcrumb(segment);
            breadcrumbPanel.add(crumb);
        }

        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }

    private JButton createBreadcrumb(File segment)
    {
        String name = FileSystemWorker.getDisplayName(segment);
        if (name.isEmpty())
        {
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

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setForeground(JStudioTheme.getAccent());
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setForeground(JStudioTheme.getTextPrimary());
            }
        });

        button.addActionListener(e ->
        {
            if (listener != null)
            {
                listener.onNavigate(segment);
            }
        });

        return button;
    }

    private JButton createSeparator()
    {
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

    /** Swaps the breadcrumbs for a path field holding the current path, focused and selected. */
    public void enterEditMode()
    {
        if (inEditMode)
        {
            return;
        }

        inEditMode = true;
        pathTextField.setText(currentDirectory != null ? currentDirectory.getAbsolutePath() : "");
        pathCardLayout.show(pathContainer, "edit");
        pathTextField.requestFocusInWindow();
        pathTextField.selectAll();
    }

    private void exitEditMode()
    {
        if (!inEditMode)
        {
            return;
        }

        inEditMode = false;
        pathCardLayout.show(pathContainer, "breadcrumb");
    }

    private void applyEditedPath()
    {
        String path = pathTextField.getText().trim();
        exitEditMode();

        if (path.isEmpty())
        {
            return;
        }

        File dir = new File(path);
        if (dir.exists() && dir.isDirectory())
        {
            if (listener != null)
            {
                listener.onNavigate(dir);
            }
        }
        else if (dir.exists() && dir.isFile())
        {
            File parent = dir.getParentFile();
            if (parent != null && listener != null)
            {
                listener.onNavigate(parent);
            }
        }
        else
        {
            Color original = pathTextField.getBackground();
            Color errorBg = new Color(JStudioTheme.getError().getRed() / 3, JStudioTheme.getError().getGreen() / 6, JStudioTheme.getError().getBlue() / 6);
            pathTextField.setBackground(errorBg);
            Timer timer = new Timer(500, e -> pathTextField.setBackground(original));
            timer.setRepeats(false);
            timer.start();
            enterEditMode();
        }
    }

    private void updateNavigationButtons()
    {
        backButton.setEnabled(historyIndex > 0);
        forwardButton.setEnabled(historyIndex < history.size() - 1);
        upButton.setEnabled(currentDirectory != null && currentDirectory.getParentFile() != null);
    }

    /** Moves back one step in the history and asks the listener to go there; does nothing at the start. */
    public void goBack()
    {
        if (historyIndex > 0)
        {
            historyIndex--;
            File dir = history.get(historyIndex);
            currentDirectory = dir;
            updateBreadcrumbs();
            updateNavigationButtons();
            if (listener != null)
            {
                listener.onNavigate(dir);
            }
        }
    }

    /** Moves forward one step in the history and asks the listener to go there; does nothing at the end. */
    public void goForward()
    {
        if (historyIndex < history.size() - 1)
        {
            historyIndex++;
            File dir = history.get(historyIndex);
            currentDirectory = dir;
            updateBreadcrumbs();
            updateNavigationButtons();
            if (listener != null)
            {
                listener.onNavigate(dir);
            }
        }
    }

    /** Asks the listener to go to the parent of the current directory; does nothing at a root. */
    public void goUp()
    {
        if (currentDirectory != null)
        {
            File parent = currentDirectory.getParentFile();
            if (parent != null && listener != null)
            {
                listener.onNavigate(parent);
            }
        }
    }

    /** Switches to the editable path field, as the Ctrl+L shortcut does. */
    public void focusPathBar()
    {
        enterEditMode();
    }
}
