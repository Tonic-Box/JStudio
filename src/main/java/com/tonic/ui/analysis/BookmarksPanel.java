package com.tonic.ui.analysis;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.Bookmark;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectDatabaseService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class BookmarksPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final JList<Bookmark> bookmarkList;
    private final DefaultListModel<Bookmark> listModel;
    private final JLabel statusLabel;

    public BookmarksPanel(ProjectModel project) {
        super(BackgroundStyle.PRIMARY, new BorderLayout());
        this.project = project;

        JToolBar toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        bookmarkList = new JList<>(listModel);
        bookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookmarkList.setCellRenderer(new BookmarkCellRenderer());
        bookmarkList.setBackground(JStudioTheme.getBgPrimary());
        bookmarkList.setForeground(JStudioTheme.getTextPrimary());

        bookmarkList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(bookmarkList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgPrimary());
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("No bookmarks");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        ProjectDatabaseService.getInstance().addListener((db, dirty) -> {
            SwingUtilities.invokeLater(this::refresh);
        });
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JButton addBtn = createToolButton("Add Bookmark", "bookmark", e -> addBookmark());
        JButton removeBtn = createToolButton("Remove", "delete", e -> removeSelected());
        JButton refreshBtn = createToolButton("Refresh", "refresh", e -> refresh());

        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        toolbar.addSeparator();
        toolbar.add(refreshBtn);

        return toolbar;
    }

    private JButton createToolButton(String tooltip, String iconName, java.awt.event.ActionListener action) {
        JButton button = new JButton();
        button.setIcon(Icons.getIcon(iconName));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(28, 28));
        button.addActionListener(action);
        return button;
    }

    public void refresh() {
        listModel.clear();
        List<Bookmark> bookmarks = ProjectDatabaseService.getInstance().getAllBookmarks();
        for (Bookmark b : bookmarks) {
            listModel.addElement(b);
        }
        updateStatus();
    }

    private void updateStatus() {
        int count = listModel.size();
        if (count == 0) {
            statusLabel.setText("No bookmarks");
        } else {
            statusLabel.setText(count + " bookmark" + (count == 1 ? "" : "s"));
        }
    }

    private void navigateToSelected() {
        Bookmark selected = bookmarkList.getSelectedValue();
        if (selected == null) return;

        ClassEntryModel classEntry = findClass(selected.getClassName());
        if (classEntry != null) {
            EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
        }
    }

    private ClassEntryModel findClass(String className) {
        if (project == null) {
            return null;
        }
        return project.getClass(className);
    }

    public void addBookmark() {
        if (project == null || project.getClassCount() == 0) {
            JOptionPane.showMessageDialog(this, "No classes loaded.", "Add Bookmark", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String className = JOptionPane.showInputDialog(this, "Class name (internal format, e.g., com/example/Main):",
                "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (className == null || className.trim().isEmpty()) {
            return;
        }

        ClassEntryModel classEntry = findClass(className.trim());
        if (classEntry == null) {
            JOptionPane.showMessageDialog(this, "Class not found: " + className, "Add Bookmark", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        Bookmark bookmark = new Bookmark(classEntry.getClassName(), name.trim());
        ProjectDatabaseService.getInstance().addBookmark(bookmark);
        refresh();
    }

    private void removeSelected() {
        Bookmark selected = bookmarkList.getSelectedValue();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(this,
                "Remove bookmark \"" + selected.getDisplayName() + "\"?",
                "Remove Bookmark",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            ProjectDatabaseService.getInstance().removeBookmark(selected.getId());
            refresh();
        }
    }

    private void showContextMenu(MouseEvent e) {
        int index = bookmarkList.locationToIndex(e.getPoint());
        if (index >= 0) {
            bookmarkList.setSelectedIndex(index);
        }

        Bookmark selected = bookmarkList.getSelectedValue();
        if (selected == null) return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem goTo = new JMenuItem("Go to Location");
        goTo.addActionListener(ev -> navigateToSelected());
        menu.add(goTo);

        menu.addSeparator();

        JMenuItem rename = new JMenuItem("Rename...");
        rename.addActionListener(ev -> renameSelected());
        menu.add(rename);

        JMenuItem setSlot = new JMenuItem("Set Quick Slot...");
        setSlot.addActionListener(ev -> setQuickSlot(selected));
        menu.add(setSlot);

        if (selected.hasSlot()) {
            JMenuItem clearSlot = new JMenuItem("Clear Quick Slot");
            clearSlot.addActionListener(ev -> {
                ProjectDatabaseService.getInstance().getDatabase().getBookmarks().clearQuickSlot(selected.getSlot());
                ProjectDatabaseService.getInstance().markDirty();
                refresh();
            });
            menu.add(clearSlot);
        }

        menu.addSeparator();

        JMenuItem remove = new JMenuItem("Remove");
        remove.addActionListener(ev -> removeSelected());
        menu.add(remove);

        menu.show(bookmarkList, e.getX(), e.getY());
    }

    private void renameSelected() {
        Bookmark selected = bookmarkList.getSelectedValue();
        if (selected == null) return;

        String newName = JOptionPane.showInputDialog(this, "New name:", selected.getName());
        if (newName != null && !newName.trim().isEmpty()) {
            selected.setName(newName.trim());
            ProjectDatabaseService.getInstance().markDirty();
            refresh();
        }
    }

    private void setQuickSlot(Bookmark bookmark) {
        String[] options = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        String choice = (String) JOptionPane.showInputDialog(this,
                "Select quick slot (Ctrl+number to jump):",
                "Set Quick Slot",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                bookmark.hasSlot() ? String.valueOf(bookmark.getSlot()) : "1");

        if (choice != null) {
            int slot = Integer.parseInt(choice);
            ProjectDatabaseService.getInstance().setQuickSlot(slot, bookmark);
            refresh();
        }
    }

    private static class BookmarkCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof Bookmark) {
                Bookmark b = (Bookmark) value;
                String text = b.getDisplayName();
                if (b.hasSlot()) {
                    text = "[" + b.getSlot() + "] " + text;
                }
                setText(text);
                setToolTipText(b.getClassName());
                setIcon(Icons.getIcon("bookmark"));

                if (!isSelected) {
                    setBackground(JStudioTheme.getBgPrimary());
                    setForeground(JStudioTheme.getTextPrimary());
                }
            }

            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setFont(getFont().deriveFont(Font.PLAIN, 12f));

            return this;
        }
    }
}
