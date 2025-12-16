package com.tonic.ui.util;

import com.tonic.ui.MainFrame;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Registers global keyboard shortcuts for JStudio.
 */
public class KeyboardShortcuts {

    private static final int MENU_SHORTCUT_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    /**
     * Register all keyboard shortcuts for the main frame.
     */
    public static void register(MainFrame mainFrame) {
        JRootPane rootPane = mainFrame.getRootPane();
        int condition = JComponent.WHEN_IN_FOCUSED_WINDOW;

        // File shortcuts
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_SHORTCUT_MASK),
                "open", e -> mainFrame.showOpenDialog());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_W, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
                "closeProject", e -> mainFrame.closeProject());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_Q, MENU_SHORTCUT_MASK),
                "exit", e -> mainFrame.exitApplication());

        // Navigation shortcuts
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_G, MENU_SHORTCUT_MASK),
                "goToClass", e -> mainFrame.showGoToClassDialog());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_L, MENU_SHORTCUT_MASK),
                "goToLine", e -> mainFrame.showGoToLineDialog());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK),
                "navigateBack", e -> mainFrame.navigateBack());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK),
                "navigateForward", e -> mainFrame.navigateForward());

        // View shortcuts
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0),
                "sourceView", e -> mainFrame.switchToSourceView());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0),
                "bytecodeView", e -> mainFrame.switchToBytecodeView());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0),
                "irView", e -> mainFrame.switchToIRView());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0),
                "hexView", e -> mainFrame.switchToHexView());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F5, MENU_SHORTCUT_MASK),
                "refresh", e -> mainFrame.refreshCurrentView());

        // Edit shortcuts
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_C, MENU_SHORTCUT_MASK),
                "copy", e -> mainFrame.copySelection());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F, MENU_SHORTCUT_MASK),
                "find", e -> mainFrame.showFindDialog());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
                "findInProject", e -> mainFrame.showFindInProjectDialog());

        // Analysis shortcuts
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0),
                "runAnalysis", e -> mainFrame.runAnalysis());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_G, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
                "showCallGraph", e -> mainFrame.showCallGraph());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_T, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
                "showTransforms", e -> mainFrame.showTransformDialog());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_U, MENU_SHORTCUT_MASK),
                "findUsages", e -> mainFrame.findUsages());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_B, MENU_SHORTCUT_MASK),
                "goToDefinition", e -> mainFrame.goToDefinition());

        // Panel toggles
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_1, MENU_SHORTCUT_MASK),
                "toggleNavigator", e -> mainFrame.toggleNavigatorPanel());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_2, MENU_SHORTCUT_MASK),
                "toggleProperties", e -> mainFrame.togglePropertiesPanel());

        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_3, MENU_SHORTCUT_MASK),
                "toggleConsole", e -> mainFrame.toggleConsolePanel());

        // Close tab
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_W, MENU_SHORTCUT_MASK),
                "closeTab", e -> {
                    // Close current tab if any
                    if (mainFrame.getEditorPanel().getCurrentTab() != null) {
                        mainFrame.getEditorPanel().closeTab(mainFrame.getEditorPanel().getCurrentTab());
                    }
                });

        // Help
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                "showShortcuts", e -> mainFrame.showKeyboardShortcuts());

        // Font size shortcuts
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, MENU_SHORTCUT_MASK),
                "increaseFontSize", e -> mainFrame.increaseFontSize());
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, MENU_SHORTCUT_MASK),
                "increaseFontSize2", e -> mainFrame.increaseFontSize());
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, MENU_SHORTCUT_MASK),
                "decreaseFontSize", e -> mainFrame.decreaseFontSize());
        registerAction(rootPane, condition, KeyStroke.getKeyStroke(KeyEvent.VK_0, MENU_SHORTCUT_MASK),
                "resetFontSize", e -> mainFrame.resetFontSize());
    }

    private static void registerAction(JRootPane rootPane, int condition, KeyStroke keyStroke,
                                        String actionName, java.util.function.Consumer<ActionEvent> action) {
        rootPane.getInputMap(condition).put(keyStroke, actionName);
        rootPane.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.accept(e);
            }
        });
    }
}
