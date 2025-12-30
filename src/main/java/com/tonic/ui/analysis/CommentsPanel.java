package com.tonic.ui.analysis;

import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.Comment;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectDatabaseService;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
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
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommentsPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final JList<Comment> commentList;
    private final DefaultListModel<Comment> listModel;
    private final JTextArea previewArea;
    private final JLabel statusLabel;

    public CommentsPanel(ProjectModel project) {
        super(BackgroundStyle.PRIMARY, new BorderLayout());
        this.project = project;

        JToolBar toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        commentList = new JList<>(listModel);
        commentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commentList.setCellRenderer(new CommentCellRenderer());
        commentList.setBackground(JStudioTheme.getBgPrimary());
        commentList.setForeground(JStudioTheme.getTextPrimary());

        commentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showPreview();
            }
        });

        commentList.addMouseListener(new MouseAdapter() {
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

        JScrollPane listScroll = new JScrollPane(commentList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        listScroll.setPreferredSize(new Dimension(0, 200));

        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setBackground(JStudioTheme.getBgSecondary());
        previewArea.setForeground(JStudioTheme.getTextPrimary());
        previewArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        previewArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM));

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Preview"));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(listScroll, BorderLayout.CENTER);
        centerPanel.add(previewScroll, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("No comments");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM));
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

        JButton addBtn = createToolButton("Add Comment", "comment", e -> addCommentAtCurrentLocation());
        JButton editBtn = createToolButton("Edit", "edit", e -> editSelected());
        JButton removeBtn = createToolButton("Remove", "delete", e -> removeSelected());
        JButton refreshBtn = createToolButton("Refresh", "refresh", e -> refresh());

        toolbar.add(addBtn);
        toolbar.add(editBtn);
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
        if (ProjectDatabaseService.getInstance().hasDatabase()) {
            List<Comment> comments = ProjectDatabaseService.getInstance().getDatabase().getComments().getAllComments();
            comments.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            for (Comment c : comments) {
                listModel.addElement(c);
            }
        }
        updateStatus();
        previewArea.setText("");
    }

    private void updateStatus() {
        int count = listModel.size();
        if (count == 0) {
            statusLabel.setText("No comments");
        } else {
            statusLabel.setText(count + " comment" + (count == 1 ? "" : "s"));
        }
    }

    private void showPreview() {
        Comment selected = commentList.getSelectedValue();
        if (selected == null) {
            previewArea.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Location: ").append(selected.getLocationKey()).append("\n");
        sb.append("Type: ").append(selected.getType()).append("\n");
        sb.append("Created: ").append(formatDate(selected.getTimestamp())).append("\n");
        sb.append("\n").append(selected.getText());
        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    private void navigateToSelected() {
        Comment selected = commentList.getSelectedValue();
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

    public void addCommentAtCurrentLocation() {
        if (project == null || project.getClassCount() == 0) {
            JOptionPane.showMessageDialog(this, "No classes loaded.", "Add Comment", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String className = JOptionPane.showInputDialog(this, "Class name (internal format, e.g., com/example/Main):",
                "Add Comment", JOptionPane.PLAIN_MESSAGE);
        if (className == null || className.trim().isEmpty()) {
            return;
        }

        ClassEntryModel classEntry = findClass(className.trim());
        if (classEntry == null) {
            JOptionPane.showMessageDialog(this, "Class not found: " + className, "Add Comment", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextArea textArea = new JTextArea(5, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Enter Comment for " + classEntry.getSimpleName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !textArea.getText().trim().isEmpty()) {
            Comment comment = new Comment(classEntry.getClassName(), -1, textArea.getText().trim());
            comment.setType(Comment.Type.CLASS);
            ProjectDatabaseService.getInstance().addComment(comment);
            refresh();
        }
    }

    private void editSelected() {
        Comment selected = commentList.getSelectedValue();
        if (selected == null) return;

        JTextArea textArea = new JTextArea(5, 30);
        textArea.setText(selected.getText());
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Edit Comment",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !textArea.getText().trim().isEmpty()) {
            ProjectDatabaseService.getInstance().updateComment(selected.getId(), textArea.getText().trim());
            refresh();
        }
    }

    private void removeSelected() {
        Comment selected = commentList.getSelectedValue();
        if (selected == null) return;

        String preview = selected.getText();
        if (preview.length() > 50) {
            preview = preview.substring(0, 47) + "...";
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Remove comment: \"" + preview + "\"?",
                "Remove Comment",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            ProjectDatabaseService.getInstance().removeComment(selected.getId());
            refresh();
        }
    }

    private void showContextMenu(MouseEvent e) {
        int index = commentList.locationToIndex(e.getPoint());
        if (index >= 0) {
            commentList.setSelectedIndex(index);
        }

        Comment selected = commentList.getSelectedValue();
        if (selected == null) return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem goTo = new JMenuItem("Go to Location");
        goTo.addActionListener(ev -> navigateToSelected());
        menu.add(goTo);

        menu.addSeparator();

        JMenuItem edit = new JMenuItem("Edit...");
        edit.addActionListener(ev -> editSelected());
        menu.add(edit);

        JMenuItem remove = new JMenuItem("Remove");
        remove.addActionListener(ev -> removeSelected());
        menu.add(remove);

        menu.show(commentList, e.getX(), e.getY());
    }

    private static class CommentCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof Comment) {
                Comment c = (Comment) value;
                String preview = c.getText();
                if (preview.length() > 40) {
                    preview = preview.substring(0, 37) + "...";
                }
                String className = c.getClassName();
                int lastSlash = className.lastIndexOf('/');
                if (lastSlash >= 0) {
                    className = className.substring(lastSlash + 1);
                }
                setText(className + ": " + preview);
                setToolTipText("<html>" + c.getLocationKey() + "<br>" + c.getText().replace("\n", "<br>") + "</html>");
                setIcon(Icons.getIcon("comment"));

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
