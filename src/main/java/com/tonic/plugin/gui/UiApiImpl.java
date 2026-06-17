package com.tonic.plugin.gui;

import com.tonic.plugin.api.ui.NavigatorAction;
import com.tonic.plugin.api.ui.NavigatorActionProvider;
import com.tonic.plugin.api.ui.Registration;
import com.tonic.plugin.api.ui.UiApi;
import com.tonic.ui.MainFrame;
import com.tonic.ui.bottom.BottomPanel;
import com.tonic.ui.core.component.ToolWindowPane;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.navigator.NavigatorPanel;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the plugin UI contribution surface against the live {@link MainFrame}. Every mutator records its
 * undo {@link Registration} into the plugin's contribution list (shared with its {@link LoadedPlugin}) so the
 * manager removes it on unload. All methods run on the EDT (the manager activates plugins on the EDT). Plugin
 * callbacks are invoked guarded, so a failing action shows a dialog rather than escaping as an uncaught exception.
 */
final class UiApiImpl implements UiApi {

    private final MainFrame frame;
    private final List<Registration> contributions;
    /** Top-level menus this plugin created (so they can be removed when they become empty again). */
    private final List<JMenu> createdMenus = new ArrayList<>();

    UiApiImpl(MainFrame frame, List<Registration> contributions) {
        this.frame = frame;
        this.contributions = contributions;
    }

    @Override
    public Registration addToolWindow(String name, JComponent component) {
        ToolWindowPane dock = frame.getRightToolWindow();
        String actual = uniqueToolName(dock, name);
        PluginThemer themer = PluginThemer.install(component);
        dock.addTool(actual, component);
        return record(() -> {
            dock.removeTool(actual);
            themer.uninstall();
        });
    }

    @Override
    public Registration openCenterView(String id, String title, Icon icon, JComponent view) {
        EditorPanel editor = frame.getEditorPanel();
        PluginThemer themer = PluginThemer.install(view);
        editor.openCustomView(id, title, icon, view);
        return record(() -> {
            editor.closeCustomView(id);
            themer.uninstall();
        });
    }

    @Override
    public Registration addBottomTab(String title, JComponent component) {
        BottomPanel bottom = frame.getSidePanel();
        PluginThemer themer = PluginThemer.install(component);
        bottom.addPluginTab(title, component);
        return record(() -> {
            bottom.removePluginTab(component);
            themer.uninstall();
        });
    }

    @Override
    public Registration addMenuItem(String menuName, String itemText, Runnable action) {
        JMenuBar bar = frame.getJMenuBar();
        JMenu menu = findMenu(bar, menuName);
        if (menu == null) {
            menu = new JMenu(menuName);
            bar.add(menu);
            bar.revalidate();
            bar.repaint();
            createdMenus.add(menu);
        }
        final JMenu target = menu;
        JMenuItem item = new JMenuItem(itemText);
        item.addActionListener(e -> runGuarded(action));
        target.add(item);
        return record(() -> {
            target.remove(item);
            if (createdMenus.contains(target) && target.getItemCount() == 0) {
                JMenuBar currentBar = frame.getJMenuBar();
                if (currentBar != null) {
                    currentBar.remove(target);
                    currentBar.revalidate();
                    currentBar.repaint();
                }
                createdMenus.remove(target);
            }
        });
    }

    @Override
    public Registration addToolbarButton(Icon icon, String tooltip, Runnable action) {
        JButton button = frame.getToolbarBuilder().addPluginButton(icon, tooltip, e -> runGuarded(action));
        return record(() -> frame.getToolbarBuilder().removePluginButton(button));
    }

    @Override
    public Registration addNavigatorAction(NavigatorActionProvider provider) {
        NavigatorPanel navigator = frame.getNavigatorPanel();
        NavigatorActionProvider guarded = context -> {
            List<NavigatorAction> raw = provider.actionsFor(context);
            if (raw == null) {
                return new ArrayList<>();
            }
            List<NavigatorAction> wrapped = new ArrayList<>(raw.size());
            for (NavigatorAction action : raw) {
                wrapped.add(new NavigatorAction(action.label(), () -> runGuarded(action.action())));
            }
            return wrapped;
        };
        navigator.addActionProvider(guarded);
        return record(() -> navigator.removeActionProvider(guarded));
    }

    @Override
    public void setStatus(String message) {
        frame.getStatusBar().setMessage(message);
    }

    private Registration record(Registration registration) {
        Registration once = new Registration() {
            private boolean removed;

            @Override
            public void remove() {
                if (removed) {
                    return;
                }
                removed = true;
                registration.remove();
            }
        };
        contributions.add(once);
        return once;
    }

    private void runGuarded(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(frame, "Plugin action failed:\n" + t,
                    "Plugin Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JMenu findMenu(JMenuBar bar, String name) {
        if (bar == null) {
            return null;
        }
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && name.equalsIgnoreCase(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    private static String uniqueToolName(ToolWindowPane dock, String name) {
        if (!dock.hasTool(name)) {
            return name;
        }
        int n = 2;
        while (dock.hasTool(name + " (" + n + ")")) {
            n++;
        }
        return name + " (" + n + ")";
    }
}
