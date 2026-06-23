package com.tonic.ui.editor.source;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.Comment;
import com.tonic.service.ProjectDatabaseService;
import com.tonic.ui.theme.Icons;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the editor's line-comment gutter: the per-line comment icons, the add/view dialogs, and the project-database
 * listener that refreshes the icons when comments change. {@link #attach()} subscribes and {@link #detach()}
 * unsubscribes, so a closed editor leaves no listener behind in the long-lived service.
 */
final class CommentGutterController {

    private final Component dialogParent;
    private final RTextScrollPane scrollPane;
    private final ClassEntryModel classEntry;
    private final List<GutterIconInfo> commentIcons = new ArrayList<>();
    private final ProjectDatabaseService.DatabaseChangeListener dbListener =
            (db, dirty) -> SwingUtilities.invokeLater(this::updateIcons);

    CommentGutterController(Component dialogParent, RTextScrollPane scrollPane, ClassEntryModel classEntry) {
        this.dialogParent = dialogParent;
        this.scrollPane = scrollPane;
        this.classEntry = classEntry;
    }

    void attach() {
        ProjectDatabaseService.getInstance().addListener(dbListener);
    }

    void detach() {
        ProjectDatabaseService.getInstance().removeListener(dbListener);
    }

    void addCommentAtLine(int lineNumber) {
        JTextArea commentArea = new JTextArea(5, 40);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        JScrollPane commentScroll = new JScrollPane(commentArea);

        int result = JOptionPane.showConfirmDialog(
                dialogParent,
                commentScroll,
                "Add Comment at Line " + lineNumber + " in " + classEntry.getSimpleName(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !commentArea.getText().trim().isEmpty()) {
            Comment comment = new Comment(classEntry.getClassName(), lineNumber, commentArea.getText().trim());
            comment.setType(Comment.Type.LINE);
            ProjectDatabaseService.getInstance().addComment(comment);
        }
    }

    int countCommentsAtLine(int lineNumber) {
        if (!ProjectDatabaseService.getInstance().hasDatabase()) {
            return 0;
        }
        List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());
        int count = 0;
        for (Comment c : comments) {
            if (c.getLineNumber() == lineNumber) {
                count++;
            }
        }
        return count;
    }

    void viewCommentsAtLine(int lineNumber) {
        if (!ProjectDatabaseService.getInstance().hasDatabase()) {
            return;
        }
        List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());
        StringBuilder sb = new StringBuilder();
        for (Comment c : comments) {
            if (c.getLineNumber() == lineNumber) {
                if (sb.length() > 0) {
                    sb.append("\n---\n");
                }
                sb.append(c.getText());
            }
        }
        if (sb.length() > 0) {
            JTextArea viewArea = new JTextArea(sb.toString());
            viewArea.setEditable(false);
            viewArea.setLineWrap(true);
            viewArea.setWrapStyleWord(true);
            viewArea.setRows(Math.min(10, sb.toString().split("\n").length + 2));
            viewArea.setColumns(50);
            JScrollPane viewScroll = new JScrollPane(viewArea);
            JOptionPane.showMessageDialog(dialogParent, viewScroll,
                    "Comments at Line " + lineNumber, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    void updateIcons() {
        Gutter gutter = scrollPane.getGutter();

        for (GutterIconInfo iconInfo : commentIcons) {
            gutter.removeTrackingIcon(iconInfo);
        }
        commentIcons.clear();

        if (!ProjectDatabaseService.getInstance().hasDatabase()) {
            return;
        }

        List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());

        if (comments.isEmpty()) {
            return;
        }

        Map<Integer, List<Comment>> commentsByLine = new HashMap<>();
        for (Comment c : comments) {
            int line = c.getLineNumber();
            if (line > 0) {
                commentsByLine.computeIfAbsent(line, k -> new ArrayList<>()).add(c);
            }
        }

        Icon commentIcon = Icons.getIcon("comment");
        for (Map.Entry<Integer, List<Comment>> entry : commentsByLine.entrySet()) {
            int lineNumber = entry.getKey();
            List<Comment> lineComments = entry.getValue();

            StringBuilder tooltip = new StringBuilder("<html>");
            for (int i = 0; i < lineComments.size(); i++) {
                if (i > 0) {
                    tooltip.append("<hr>");
                }
                String text = lineComments.get(i).getText();
                if (text.length() > 100) {
                    text = text.substring(0, 97) + "...";
                }
                tooltip.append(escapeHtml(text).replace("\n", "<br>"));
            }
            tooltip.append("</html>");

            try {
                GutterIconInfo iconInfo = gutter.addLineTrackingIcon(lineNumber - 1, commentIcon, tooltip.toString());
                commentIcons.add(iconInfo);
            } catch (BadLocationException e) {
                // Line doesn't exist, skip
            }
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
