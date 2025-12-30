package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.QuickAccessManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.Map;

public class QuickAccessPanel extends ThemedJPanel implements QuickAccessManager.QuickAccessListener {

    public interface LocationListener {
        void onLocationSelected(File location);
    }

    private final LocationListener listener;
    private final QuickAccessManager manager;

    private final DefaultListModel<QuickAccessItem> pinnedModel;
    private final DefaultListModel<QuickAccessItem> quickAccessModel;
    private final DefaultListModel<QuickAccessItem> recentModel;
    private final DefaultListModel<QuickAccessItem> drivesModel;

    private JList<QuickAccessItem> pinnedList;
    private JList<QuickAccessItem> quickAccessList;
    private JList<QuickAccessItem> recentList;
    private JList<QuickAccessItem> drivesList;

    private JLabel pinnedHeader;
    private JLabel recentHeader;

    public QuickAccessPanel(LocationListener listener) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.listener = listener;
        this.manager = QuickAccessManager.getInstance();

        setPreferredSize(new Dimension(170, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, JStudioTheme.getBorder()));

        pinnedModel = new DefaultListModel<>();
        quickAccessModel = new DefaultListModel<>();
        recentModel = new DefaultListModel<>();
        drivesModel = new DefaultListModel<>();

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(JStudioTheme.getBgSecondary());

        pinnedHeader = createSectionHeader("Pinned");
        contentPanel.add(pinnedHeader);
        pinnedList = createList(pinnedModel, QuickAccessItemType.PINNED);
        contentPanel.add(pinnedList);

        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionHeader("Quick Access"));
        quickAccessList = createList(quickAccessModel, QuickAccessItemType.FOLDER);
        contentPanel.add(quickAccessList);

        contentPanel.add(Box.createVerticalStrut(12));

        recentHeader = createSectionHeader("Recent");
        contentPanel.add(recentHeader);
        recentList = createList(recentModel, QuickAccessItemType.RECENT);
        contentPanel.add(recentList);

        contentPanel.add(Box.createVerticalStrut(12));

        contentPanel.add(createSectionHeader("This PC"));
        drivesList = createList(drivesModel, QuickAccessItemType.DRIVE);
        contentPanel.add(drivesList);

        contentPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
        add(scrollPane, BorderLayout.CENTER);

        populatePinned();
        populateQuickAccess();
        populateRecent();
        populateDrives();
        updateSectionVisibility();

        manager.addListener(this);
    }

    @Override
    public void onPinnedChanged(List<File> pinned) {
        SwingUtilities.invokeLater(() -> {
            populatePinned();
            updateSectionVisibility();
        });
    }

    @Override
    public void onRecentChanged(List<File> recent) {
        SwingUtilities.invokeLater(() -> {
            populateRecent();
            updateSectionVisibility();
        });
    }

    private void updateSectionVisibility() {
        boolean hasPinned = pinnedModel.getSize() > 0;
        pinnedHeader.setVisible(hasPinned);
        pinnedList.setVisible(hasPinned);

        boolean hasRecent = recentModel.getSize() > 0;
        recentHeader.setVisible(hasRecent);
        recentList.setVisible(hasRecent);
    }

    private JLabel createSectionHeader(String title) {
        JLabel header = new JLabel(title);
        header.setForeground(JStudioTheme.getTextSecondary());
        header.setFont(JStudioTheme.getUIFont(11).deriveFont(Font.BOLD));
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 8));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        return header;
    }

    private JList<QuickAccessItem> createList(DefaultListModel<QuickAccessItem> model,
                                               QuickAccessItemType defaultType) {
        JList<QuickAccessItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(JStudioTheme.getBgSecondary());
        list.setForeground(JStudioTheme.getTextPrimary());
        list.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        list.setFixedCellHeight(UIConstants.TABLE_ROW_HEIGHT + 6);
        list.setCellRenderer(new QuickAccessRenderer());
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        QuickAccessItem item = model.getElementAt(index);
                        if (item != null && item.file != null && listener != null) {
                            listener.onLocationSelected(item.file);
                            clearAllSelections();
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e, list, model);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e, list, model);
                }
            }
        });

        return list;
    }

    private void showContextMenu(MouseEvent e, JList<QuickAccessItem> list,
                                  DefaultListModel<QuickAccessItem> model) {
        int index = list.locationToIndex(e.getPoint());
        if (index < 0) return;

        list.setSelectedIndex(index);
        QuickAccessItem item = model.getElementAt(index);
        if (item == null || item.file == null) return;

        JPopupMenu menu = new JPopupMenu();
        styleMenu(menu);

        if (item.type == QuickAccessItemType.PINNED) {
            addMenuItem(menu, "Unpin", () -> manager.removePinned(item.file));
            menu.addSeparator();
            addMenuItem(menu, "Move Up", () -> manager.movePinnedUp(item.file));
            addMenuItem(menu, "Move Down", () -> manager.movePinnedDown(item.file));
        } else if (item.type == QuickAccessItemType.RECENT) {
            if (!manager.isPinned(item.file)) {
                addMenuItem(menu, "Pin to Quick Access", () -> manager.addPinned(item.file));
            }
            addMenuItem(menu, "Remove", () -> manager.removeRecent(item.file));
            menu.addSeparator();
            addMenuItem(menu, "Clear All Recent", () -> manager.clearRecent());
        } else {
            if (!manager.isPinned(item.file)) {
                addMenuItem(menu, "Pin to Quick Access", () -> manager.addPinned(item.file));
            } else {
                addMenuItem(menu, "Unpin", () -> manager.removePinned(item.file));
            }
        }

        if (menu.getComponentCount() > 0) {
            menu.show(list, e.getX(), e.getY());
        }
    }

    private void styleMenu(JPopupMenu menu) {
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
    }

    private void addMenuItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void populatePinned() {
        pinnedModel.clear();
        for (File dir : manager.getPinnedDirectories()) {
            pinnedModel.addElement(new QuickAccessItem(
                    dir.getName(),
                    dir,
                    QuickAccessItemType.PINNED
            ));
        }
    }

    private void populateQuickAccess() {
        quickAccessModel.clear();

        Map<String, File> folders = FileSystemWorker.getSpecialFolders();

        addIfExists(folders, "Desktop", quickAccessModel, QuickAccessItemType.DESKTOP);
        addIfExists(folders, "Documents", quickAccessModel, QuickAccessItemType.DOCUMENTS);
        addIfExists(folders, "Downloads", quickAccessModel, QuickAccessItemType.DOWNLOADS);
        addIfExists(folders, "Home", quickAccessModel, QuickAccessItemType.HOME);
    }

    private void addIfExists(Map<String, File> folders, String key,
                             DefaultListModel<QuickAccessItem> model,
                             QuickAccessItemType type) {
        File file = folders.get(key);
        if (file != null && file.exists()) {
            model.addElement(new QuickAccessItem(key, file, type));
        }
    }

    private void populateRecent() {
        recentModel.clear();
        for (File dir : manager.getRecentDirectories()) {
            recentModel.addElement(new QuickAccessItem(
                    dir.getName(),
                    dir,
                    QuickAccessItemType.RECENT
            ));
        }
    }

    private void populateDrives() {
        drivesModel.clear();

        for (File root : FileSystemWorker.getRoots()) {
            String name = FileSystemWorker.getDisplayName(root);
            if (name == null || name.isEmpty()) {
                name = root.getAbsolutePath();
            }
            drivesModel.addElement(new QuickAccessItem(name, root, QuickAccessItemType.DRIVE));
        }
    }

    public void addRecentLocation(File location) {
        if (location == null || !location.isDirectory()) {
            return;
        }
        manager.addRecent(location);
    }

    public void clearSelection() {
        clearAllSelections();
    }

    private void clearAllSelections() {
        pinnedList.clearSelection();
        quickAccessList.clearSelection();
        recentList.clearSelection();
        drivesList.clearSelection();
    }

    private enum QuickAccessItemType {
        PINNED, DESKTOP, DOCUMENTS, DOWNLOADS, HOME, FOLDER, RECENT, DRIVE
    }

    private static class QuickAccessItem {
        final String name;
        final File file;
        final QuickAccessItemType type;

        QuickAccessItem(String name, File file, QuickAccessItemType type) {
            this.name = name;
            this.file = file;
            this.type = type;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class QuickAccessRenderer implements ListCellRenderer<QuickAccessItem> {
        private final JPanel panel;
        private final JLabel iconLabel;
        private final JLabel textLabel;
        private final JLabel pinIndicator;

        QuickAccessRenderer() {
            panel = new JPanel(new BorderLayout(6, 0));
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 8));

            iconLabel = new JLabel();
            textLabel = new JLabel();
            textLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));

            pinIndicator = new JLabel();
            pinIndicator.setFont(JStudioTheme.getUIFont(10));

            JPanel leftPanel = new JPanel(new BorderLayout(4, 0));
            leftPanel.setOpaque(false);
            leftPanel.add(iconLabel, BorderLayout.WEST);
            leftPanel.add(textLabel, BorderLayout.CENTER);

            panel.add(leftPanel, BorderLayout.CENTER);
            panel.add(pinIndicator, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends QuickAccessItem> list,
                                                      QuickAccessItem value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (isSelected) {
                panel.setBackground(JStudioTheme.getSelection());
                textLabel.setForeground(JStudioTheme.getTextPrimary());
                pinIndicator.setForeground(JStudioTheme.getTextSecondary());
            } else {
                panel.setBackground(JStudioTheme.getBgSecondary());
                textLabel.setForeground(JStudioTheme.getTextPrimary());
                pinIndicator.setForeground(JStudioTheme.getTextSecondary());
            }

            textLabel.setText(value.name);

            if (value.file != null) {
                javax.swing.Icon icon = FileSystemWorker.getSystemIcon(value.file);
                iconLabel.setIcon(icon);
            } else {
                iconLabel.setIcon(null);
            }

            if (value.type == QuickAccessItemType.PINNED) {
                pinIndicator.setText("\u2302");
            } else if (value.type == QuickAccessItemType.RECENT) {
                pinIndicator.setText("");
            } else {
                pinIndicator.setText("");
            }

            return panel;
        }
    }
}
