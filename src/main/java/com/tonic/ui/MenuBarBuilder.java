package com.tonic.ui;

import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.RecentFilesManager;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;

/**
 * Builds the main menu bar for JStudio.
 */
public class MenuBarBuilder {

    private static final int MENU_SHORTCUT_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final MainFrame mainFrame;
    private JMenu recentFilesMenu;
    private JCheckBoxMenuItem wordWrapItem;

    public MenuBarBuilder(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    public JMenuBar build() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(JStudioTheme.getBgPrimary());

        menuBar.add(buildFileMenu());
        menuBar.add(buildEditMenu());
        menuBar.add(buildViewMenu());
        menuBar.add(buildAnalysisMenu());
        menuBar.add(buildTransformMenu());
        menuBar.add(buildVMMenu());
        menuBar.add(buildHelpMenu());

        // Listen for recent files changes
        RecentFilesManager.getInstance().addListener(this::updateRecentFilesMenu);

        return menuBar;
    }

    private JMenu buildFileMenu() {
        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);

        menu.add(createMenuItem("Open JAR/Class...", KeyEvent.VK_O, MENU_SHORTCUT_MASK,
                Icons.getIcon("open"), e -> mainFrame.showOpenDialog()));

        menu.add(createMenuItem("Open Project...", 0, 0,
                null, e -> mainFrame.openProjectFile()));

        // Recent Files submenu
        recentFilesMenu = new JMenu("Open Recent");
        recentFilesMenu.setMnemonic(KeyEvent.VK_R);
        updateRecentFilesMenu(RecentFilesManager.getInstance().getRecentFiles());
        menu.add(recentFilesMenu);

        menu.addSeparator();

        menu.add(createMenuItem("Save Project", KeyEvent.VK_S, MENU_SHORTCUT_MASK,
                Icons.getIcon("save"), e -> mainFrame.saveProject()));

        menu.add(createMenuItem("Save Project As...", KeyEvent.VK_S, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                null, e -> mainFrame.saveProjectAs()));

        menu.addSeparator();

        menu.add(createMenuItem("Export Class...", KeyEvent.VK_E, MENU_SHORTCUT_MASK | InputEvent.ALT_DOWN_MASK,
                null, e -> mainFrame.exportCurrentClass()));

        menu.add(createMenuItem("Export All Classes...", 0, 0,
                null, e -> mainFrame.exportAllClasses()));

        menu.addSeparator();

        menu.add(createMenuItem("Close Project", KeyEvent.VK_W, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("close"), e -> mainFrame.closeProject()));

        menu.addSeparator();

        menu.add(createMenuItem("Exit", KeyEvent.VK_Q, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.exitApplication()));

        return menu;
    }

    private void updateRecentFilesMenu(List<File> recentFiles) {
        if (recentFilesMenu == null) return;

        recentFilesMenu.removeAll();

        if (recentFiles.isEmpty()) {
            JMenuItem emptyItem = new JMenuItem("(No recent files)");
            emptyItem.setEnabled(false);
            recentFilesMenu.add(emptyItem);
        } else {
            int index = 1;
            for (File file : recentFiles) {
                String label = index + ". " + file.getName();
                JMenuItem item = new JMenuItem(label);
                item.setToolTipText(file.getAbsolutePath());

                // Add accelerator for first item (Ctrl+Shift+O)
                if (index == 1) {
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
                            MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK));
                }

                final File fileToOpen = file;
                item.addActionListener(e -> mainFrame.openFile(fileToOpen.getAbsolutePath()));
                recentFilesMenu.add(item);
                index++;
            }

            recentFilesMenu.addSeparator();

            JMenuItem clearItem = new JMenuItem("Clear Recent Files");
            clearItem.addActionListener(e -> RecentFilesManager.getInstance().clear());
            recentFilesMenu.add(clearItem);
        }
    }

    private JMenu buildEditMenu() {
        JMenu menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);

        menu.add(createMenuItem("Copy", KeyEvent.VK_C, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.copySelection()));

        menu.addSeparator();

        menu.add(createMenuItem("Find...", KeyEvent.VK_F, MENU_SHORTCUT_MASK,
                Icons.getIcon("search"), e -> mainFrame.showFindDialog()));

        menu.add(createMenuItem("Find in Project...", KeyEvent.VK_F, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                null, e -> mainFrame.showFindInProjectDialog()));

        menu.addSeparator();

        menu.add(createMenuItem("Go to Class...", KeyEvent.VK_G, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.showGoToClassDialog()));

        menu.add(createMenuItem("Go to Line...", KeyEvent.VK_L, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.showGoToLineDialog()));

        menu.addSeparator();

        menu.add(createMenuItem("Add Bookmark...", KeyEvent.VK_B, MENU_SHORTCUT_MASK,
                Icons.getIcon("bookmark"), e -> mainFrame.addBookmarkAtCurrentLocation()));

        menu.add(createMenuItem("Add Comment...", KeyEvent.VK_SEMICOLON, MENU_SHORTCUT_MASK,
                Icons.getIcon("comment"), e -> mainFrame.addCommentAtCurrentLocation()));

        menu.add(createMenuItem("View Bookmarks", KeyEvent.VK_B, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                null, e -> mainFrame.showBookmarksPanel()));

        menu.add(createMenuItem("View Comments", 0, 0,
                null, e -> mainFrame.showCommentsPanel()));

        menu.addSeparator();

        menu.add(createMenuItem("Preferences...", KeyEvent.VK_COMMA, MENU_SHORTCUT_MASK,
                Icons.getIcon("settings"), e -> mainFrame.showPreferencesDialog()));

        return menu;
    }

    private JMenu buildViewMenu() {
        JMenu menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);

        menu.add(createMenuItem("Source View", KeyEvent.VK_F5, 0,
                Icons.getIcon("source"), e -> mainFrame.switchToSourceView()));

        menu.add(createMenuItem("Bytecode View", KeyEvent.VK_F6, 0,
                Icons.getIcon("bytecode"), e -> mainFrame.switchToBytecodeView()));

        menu.add(createMenuItem("IR View", KeyEvent.VK_F7, 0,
                Icons.getIcon("ir"), e -> mainFrame.switchToIRView()));

        menu.addSeparator();

        // Word wrap toggle
        wordWrapItem = new JCheckBoxMenuItem("Word Wrap");
        wordWrapItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.ALT_DOWN_MASK));
        wordWrapItem.addActionListener(e -> mainFrame.toggleWordWrap(wordWrapItem.isSelected()));
        menu.add(wordWrapItem);

        menu.addSeparator();

        menu.add(createMenuItem("Navigator Panel", KeyEvent.VK_1, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.toggleNavigatorPanel()));

        menu.add(createMenuItem("Properties Panel", KeyEvent.VK_2, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.togglePropertiesPanel()));

        menu.add(createMenuItem("Console Panel", KeyEvent.VK_3, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.toggleConsolePanel()));

        menu.addSeparator();

        // Font size controls
        menu.add(createMenuItem("Increase Font Size", KeyEvent.VK_EQUALS, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.increaseFontSize()));
        menu.add(createMenuItem("Decrease Font Size", KeyEvent.VK_MINUS, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.decreaseFontSize()));
        menu.add(createMenuItem("Reset Font Size", KeyEvent.VK_0, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.resetFontSize()));

        menu.addSeparator();

        menu.add(createMenuItem("Refresh", KeyEvent.VK_F5, MENU_SHORTCUT_MASK,
                Icons.getIcon("refresh"), e -> mainFrame.refreshCurrentView()));

        menu.addSeparator();

        menu.add(createMenuItem("Class Browser...", KeyEvent.VK_B, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("browser"), e -> mainFrame.showClassBrowser()));

        return menu;
    }

    private JMenu buildAnalysisMenu() {
        JMenu menu = new JMenu("Analysis");
        menu.setMnemonic(KeyEvent.VK_A);

        menu.add(createMenuItem("Run Analysis", KeyEvent.VK_F9, 0,
                Icons.getIcon("analyze"), e -> mainFrame.runAnalysis()));

        menu.addSeparator();

        menu.add(createMenuItem("Query Explorer...", KeyEvent.VK_Q, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("search"), e -> mainFrame.showQueryExplorer()));

        menu.addSeparator();

        menu.add(createMenuItem("Show Call Graph", KeyEvent.VK_G, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("callgraph"), e -> mainFrame.showCallGraph()));

        menu.add(createMenuItem("Show Dependencies", 0, 0,
                Icons.getIcon("dependency"), e -> mainFrame.showDependencies()));

        menu.addSeparator();

        menu.add(createMenuItem("Simulation Analysis", KeyEvent.VK_F10, 0,
                Icons.getIcon("analyze"), e -> mainFrame.runSimulationAnalysis()));

        menu.addSeparator();

        menu.add(createMenuItem("Find Usages", KeyEvent.VK_U, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.findUsages()));

        menu.add(createMenuItem("Go to Definition", KeyEvent.VK_B, MENU_SHORTCUT_MASK,
                null, e -> mainFrame.goToDefinition()));

        return menu;
    }

    private JMenu buildTransformMenu() {
        JMenu menu = new JMenu("Transform");
        menu.setMnemonic(KeyEvent.VK_T);

        menu.add(createMenuItem("Apply Transforms...", KeyEvent.VK_T, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("transform"), e -> mainFrame.showTransformDialog()));

        menu.add(createMenuItem("Script Editor...", KeyEvent.VK_S, MENU_SHORTCUT_MASK | InputEvent.ALT_DOWN_MASK,
                Icons.getIcon("source"), e -> mainFrame.showScriptEditor()));

        menu.addSeparator();

        menu.add(createMenuItem("String Deobfuscation...", KeyEvent.VK_D, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                null, e -> mainFrame.showDeobfuscationPanel()));

        menu.add(createMenuItem("Deobfuscate Names...", 0, 0,
                null, e -> mainFrame.showDeobfuscateNamesDialog()));

        menu.addSeparator();

        JMenu optimizeMenu = new JMenu("Optimize");
        optimizeMenu.add(createMenuItem("Constant Folding", 0, 0, null, e -> mainFrame.applyTransform("ConstantFolding")));
        optimizeMenu.add(createMenuItem("Copy Propagation", 0, 0, null, e -> mainFrame.applyTransform("CopyPropagation")));
        optimizeMenu.add(createMenuItem("Dead Code Elimination", 0, 0, null, e -> mainFrame.applyTransform("DeadCodeElimination")));
        optimizeMenu.add(createMenuItem("Strength Reduction", 0, 0, null, e -> mainFrame.applyTransform("StrengthReduction")));
        menu.add(optimizeMenu);

        menu.addSeparator();

        menu.add(createMenuItem("Recompute Stack Frames", 0, 0,
                null, e -> mainFrame.recomputeStackFrames()));

        return menu;
    }

    private JMenu buildVMMenu() {
        JMenu menu = new JMenu("VM");
        menu.setMnemonic(KeyEvent.VK_M);

        menu.add(createMenuItem("VM Console...", KeyEvent.VK_C, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("console"), e -> mainFrame.showVMConsole()));

        menu.add(createMenuItem("Bytecode Debugger...", KeyEvent.VK_F11, 0,
                Icons.getIcon("debug"), e -> mainFrame.showBytecodeDebugger()));

        menu.add(createMenuItem("Execute Method...", KeyEvent.VK_E, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("run"), e -> mainFrame.showExecuteMethodDialog()));

        menu.addSeparator();

        menu.add(createMenuItem("Heap Forensics...", KeyEvent.VK_H, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
                Icons.getIcon("heap"), e -> mainFrame.showHeapForensics()));

        menu.addSeparator();

        menu.add(createMenuItem("Initialize VM", 0, 0,
                null, e -> mainFrame.initializeVM()));

        menu.add(createMenuItem("Reset VM", 0, 0,
                null, e -> mainFrame.resetVM()));

        menu.addSeparator();

        menu.add(createMenuItem("VM Status", 0, 0,
                Icons.getIcon("info"), e -> mainFrame.showVMStatus()));

        return menu;
    }

    private JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);

        menu.add(createMenuItem("Keyboard Shortcuts", KeyEvent.VK_F1, 0,
                null, e -> mainFrame.showKeyboardShortcuts()));

        menu.addSeparator();

        menu.add(createMenuItem("About JStudio", 0, 0,
                Icons.getIcon("info"), e -> mainFrame.showAboutDialog()));

        return menu;
    }

    private JMenuItem createMenuItem(String text, int keyCode, int modifiers,
                                     javax.swing.Icon icon, ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) {
            item.setIcon(icon);
        }
        if (keyCode != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifiers));
        }
        if (action != null) {
            item.addActionListener(action);
        } else {
            item.setEnabled(false);
        }
        return item;
    }
}
