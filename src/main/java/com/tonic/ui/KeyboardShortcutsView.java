package com.tonic.ui;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * The keyboard-shortcuts reference shown as a center editor tab: each category is a small block (a bold header over
 * shortcut/action rows), and the blocks are greedily packed into balanced columns so the page reads as a few adjacent
 * tables rather than one very tall column.
 */
public final class KeyboardShortcutsView extends ThemedJPanel {

    private static final int COLUMNS = 3;

    public KeyboardShortcutsView() {
        super(ThemedJPanel.BackgroundStyle.PRIMARY, new BorderLayout());
        String mod = System.getProperty("os.name").toLowerCase().contains("mac") ? "Cmd" : "Ctrl";

        JPanel grid = new JPanel(new GridLayout(1, COLUMNS, 18, 0));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JPanel[] columns = new JPanel[COLUMNS];
        int[] weights = new int[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            columns[i] = new JPanel();
            columns[i].setLayout(new BoxLayout(columns[i], BoxLayout.Y_AXIS));
            columns[i].setOpaque(false);
            grid.add(columns[i]);
        }

        for (Category category : categories(mod)) {
            int target = 0;
            for (int i = 1; i < COLUMNS; i++) {
                if (weights[i] < weights[target]) {
                    target = i;
                }
            }
            columns[target].add(categoryBlock(category));
            columns[target].add(Box.createVerticalStrut(16));
            weights[target] += category.rows.length + 2;
        }
        for (JPanel column : columns) {
            column.add(Box.createVerticalGlue());
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    private JComponent categoryBlock(Category category) {
        JPanel block = new JPanel(new GridBagLayout());
        block.setOpaque(false);
        block.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 5, 0);
        JLabel header = new JLabel(category.name);
        header.setForeground(JStudioTheme.getAccent());
        header.setFont(JStudioTheme.getUIFont(13).deriveFont(Font.BOLD));
        block.add(header, c);

        int row = 1;
        for (String[] entry : category.rows) {
            c.gridx = 0;
            c.gridy = row;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(1, 0, 1, 16);
            JLabel key = new JLabel(entry[0]);
            key.setForeground(JStudioTheme.getInfo());
            key.setFont(JStudioTheme.getCodeFont(12));
            block.add(key, c);

            c.gridx = 1;
            c.weightx = 1;
            c.insets = new Insets(1, 0, 1, 0);
            JLabel desc = new JLabel(entry[1]);
            desc.setForeground(JStudioTheme.getTextPrimary());
            desc.setFont(JStudioTheme.getUIFont(12));
            block.add(desc, c);
            row++;
        }

        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
        return block;
    }

    private static List<Category> categories(String mod) {
        List<Category> list = new ArrayList<>();
        list.add(new Category("File", new String[][]{
                {mod + "+O", "Open JAR/Class"},
                {mod + "+Shift+O", "Open Recent"},
                {mod + "+S", "Save Project"},
                {mod + "+Shift+S", "Save Project As"},
                {mod + "+Alt+E", "Export Class"},
                {mod + "+Shift+J", "Export as JAR"},
                {mod + "+W", "Close Tab"},
                {mod + "+Shift+W", "Close Project"},
                {mod + "+Q", "Exit"},
        }));
        list.add(new Category("Navigation", new String[][]{
                {mod + "+G", "Go to Class"},
                {mod + "+L", "Go to Line"},
                {"Alt+Left", "Navigate Back"},
                {"Alt+Right", "Navigate Forward"},
        }));
        list.add(new Category("Edit", new String[][]{
                {mod + "+C", "Copy"},
                {mod + "+F", "Find in File"},
                {mod + "+Shift+F", "Find in Project"},
                {mod + "+B", "Add Bookmark"},
                {mod + "+Shift+B", "View Bookmarks"},
                {mod + "+;", "Add Comment"},
                {mod + "+,", "Preferences"},
        }));
        list.add(new Category("Views", new String[][]{
                {"F5", "Source View"},
                {"F6", "Bytecode View"},
                {"F7", "IR View"},
                {"F8", "Hex View"},
                {mod + "+F5", "Refresh"},
                {"Alt+Z", "Word Wrap"},
        }));
        list.add(new Category("Panels", new String[][]{
                {mod + "+1", "Toggle Navigator"},
                {mod + "+2", "Toggle Properties"},
                {mod + "+3", "Toggle Console"},
        }));
        list.add(new Category("Font", new String[][]{
                {mod + "+=", "Increase Font"},
                {mod + "+-", "Decrease Font"},
                {mod + "+0", "Reset Font"},
        }));
        list.add(new Category("Analysis", new String[][]{
                {"F9", "Run Analysis"},
                {"F10", "Simulation Analysis"},
                {mod + "+Shift+G", "Call Graph"},
        }));
        list.add(new Category("Transform", new String[][]{
                {mod + "+Shift+T", "Apply Transforms"},
                {mod + "+Alt+S", "Script Editor"},
                {mod + "+Shift+D", "String Deobfuscation"},
        }));
        list.add(new Category("VM", new String[][]{
                {"F11", "Bytecode Debugger"},
                {mod + "+Shift+C", "VM Console"},
                {mod + "+Shift+E", "Execute Method"},
                {mod + "+Shift+H", "Heap Forensics"},
        }));
        return list;
    }

    private static final class Category {
        final String name;
        final String[][] rows;

        Category(String name, String[][] rows) {
            this.name = name;
            this.rows = rows;
        }
    }
}
