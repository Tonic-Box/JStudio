package com.tonic.ui.editor;

import com.tonic.ui.editor.resource.ResourceEditorTab;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the per-tab right-click menu (Close / Close Others / Close All / Close to the Left|Right) and the close-set
 * algorithms behind it, plus the header mouse listener that selects on left-click and pops the menu on right-click.
 * Single-tab closes are dispatched back to the host via the injected closers; {@link #closeAllTabs()} clears the
 * pane and the registry directly.
 */
final class TabContextMenu {

    private final JTabbedPane tabbedPane;
    private final TabRegistry registry;
    private final Consumer<EditorTab> closeClass;
    private final Consumer<ResourceEditorTab> closeResource;
    private final Consumer<String> closeCustom;

    TabContextMenu(JTabbedPane tabbedPane, TabRegistry registry, Consumer<EditorTab> closeClass,
                   Consumer<ResourceEditorTab> closeResource, Consumer<String> closeCustom) {
        this.tabbedPane = tabbedPane;
        this.registry = registry;
        this.closeClass = closeClass;
        this.closeResource = closeResource;
        this.closeCustom = closeCustom;
    }

    /** A header mouse listener: left-click selects the tab; right-click shows its close context menu (bound to {@code tabBody}). */
    MouseAdapter headerListener(JComponent header, Component tabBody) {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabContextMenu(tabBody, e);
                } else {
                    int index = tabbedPane.indexOfTabComponent(header);
                    if (index != -1) {
                        tabbedPane.setSelectedIndex(index);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabContextMenu(tabBody, e);
                }
            }
        };
    }

    private void showTabContextMenu(Component tabBody, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        // Count the closable tabs (every kind except Welcome) on each side of the clicked tab.
        int tabIndex = registry.findComponentIndex(tabBody);
        int closableLeft = 0;
        int closableRight = 0;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (registry.isWelcome(comp) || i == tabIndex) {
                continue;
            }
            if (i < tabIndex) {
                closableLeft++;
            } else {
                closableRight++;
            }
        }

        // Close
        JMenuItem closeItem = createMenuItem("Close", () -> closeTabComponent(tabBody));
        menu.add(closeItem);

        // Close Others
        JMenuItem closeOthersItem = createMenuItem("Close Others", () -> closeOtherTabs(tabBody));
        closeOthersItem.setEnabled(closableLeft + closableRight > 0);
        menu.add(closeOthersItem);

        // Close All
        JMenuItem closeAllItem = createMenuItem("Close All", this::closeAllTabs);
        menu.add(closeAllItem);

        menu.addSeparator();

        // Close Tabs to the Left
        JMenuItem closeLeftItem = createMenuItem("Close Tabs to the Left", () -> closeTabsToLeft(tabBody));
        closeLeftItem.setEnabled(closableLeft > 0);
        menu.add(closeLeftItem);

        // Close Tabs to the Right
        JMenuItem closeRightItem = createMenuItem("Close Tabs to the Right", () -> closeTabsToRight(tabBody));
        closeRightItem.setEnabled(closableRight > 0);
        menu.add(closeRightItem);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem createMenuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        item.addActionListener(e -> action.run());
        return item;
    }

    /** Close all tabs (except the Welcome tab). */
    void closeAllTabs() {
        // Remove all tabs except the Welcome tab (index 0)
        while (tabbedPane.getTabCount() > 1) {
            tabbedPane.removeTabAt(1);
        }
        List<Runnable> hooks = registry.clearAll();
        tabbedPane.setSelectedIndex(0); // Switch to Welcome tab
        for (Runnable hook : hooks) {
            hook.run();
        }
    }

    /**
     * Closes whatever kind of tab {@code content} is the body of - class editor, resource editor, or custom view
     * (running its close hook) - dispatching to the matching close path. The Welcome tab is never closed.
     */
    private void closeTabComponent(Component content) {
        switch (registry.classify(content)) {
            case CLASS:
                closeClass.accept((EditorTab) content);
                break;
            case RESOURCE:
                closeResource.accept((ResourceEditorTab) content);
                break;
            case CUSTOM:
                closeCustom.accept(registry.customViewId(content));
                break;
            default:
                break;
        }
    }

    /**
     * Close every closable tab except the specified one (all tab kinds, not just class editors; the Welcome tab is
     * always kept).
     */
    void closeOtherTabs(Component keepTab) {
        // Collect by component reference first so removals don't shift the indices we still need to visit.
        List<Component> toClose = new ArrayList<>();
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (!registry.isWelcome(comp) && comp != keepTab) {
                toClose.add(comp);
            }
        }
        for (Component comp : toClose) {
            closeTabComponent(comp);
        }
        tabbedPane.setSelectedComponent(keepTab);
    }

    /**
     * Close every closable tab to the left of the specified tab (all tab kinds; the Welcome tab is always kept).
     */
    void closeTabsToLeft(Component referenceTab) {
        int refIndex = registry.findComponentIndex(referenceTab);
        if (refIndex <= 0) {
            return;
        }
        List<Component> toClose = new ArrayList<>();
        for (int i = 0; i < refIndex; i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (!registry.isWelcome(comp)) {
                toClose.add(comp);
            }
        }
        for (Component comp : toClose) {
            closeTabComponent(comp);
        }
        tabbedPane.setSelectedComponent(referenceTab);
    }

    /**
     * Close every closable tab to the right of the specified tab (all tab kinds; the Welcome tab is always kept).
     */
    void closeTabsToRight(Component referenceTab) {
        int refIndex = registry.findComponentIndex(referenceTab);
        if (refIndex < 0 || refIndex >= tabbedPane.getTabCount() - 1) {
            return;
        }
        List<Component> toClose = new ArrayList<>();
        for (int i = refIndex + 1; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (!registry.isWelcome(comp)) {
                toClose.add(comp);
            }
        }
        for (Component comp : toClose) {
            closeTabComponent(comp);
        }
        tabbedPane.setSelectedComponent(referenceTab);
    }
}
