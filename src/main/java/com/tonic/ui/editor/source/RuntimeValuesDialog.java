package com.tonic.ui.editor.source;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Modal viewer for all of a paused frame's runtime values, each {@code name = value} on its own line in a
 * selectable, scrollable text area. Opened by clicking any inline hint; the full (untruncated) value is shown
 * here even though the inline annotation is truncated, so long strings remain readable and copyable.
 */
final class RuntimeValuesDialog {

    private RuntimeValuesDialog() {
    }

    static void show(Component owner, List<RuntimeHint.HintEntry> entries) {
        List<RuntimeHint.HintEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt((RuntimeHint.HintEntry e) -> e.line)
                .thenComparing(e -> e.fullText));

        StringBuilder sb = new StringBuilder();
        for (RuntimeHint.HintEntry e : sorted) {
            sb.append(e.fullText).append('\n');
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(JStudioTheme.getCodeFont(13));
        area.setBackground(JStudioTheme.getBgSecondary());
        area.setForeground(JStudioTheme.getTextPrimary());
        area.setCaretColor(JStudioTheme.getTextPrimary());
        area.setSelectionColor(JStudioTheme.getAccent());
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        scroll.setBorder(null);

        Window win = owner != null ? SwingUtilities.getWindowAncestor(owner) : null;
        JDialog dialog = new JDialog(win, "Runtime values", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.getContentPane().setBackground(JStudioTheme.getBgSecondary());
        dialog.setLayout(new BorderLayout());

        JLabel header = new JLabel("  " + sorted.size() + " value" + (sorted.size() == 1 ? "" : "s"));
        header.setForeground(JStudioTheme.getTextSecondary());
        header.setFont(JStudioTheme.getUIFont(11));
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        dialog.add(header, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.setSize(520, 480);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
