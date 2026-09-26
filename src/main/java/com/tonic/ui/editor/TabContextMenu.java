package com.tonic.ui.editor;

import com.tonic.ui.editor.resource.ResourceEditorTab;
import com.tonic.ui.layout.StackId;
import com.tonic.ui.layout.Stacks;
import com.tonic.ui.layout.ViewHost;
import com.tonic.ui.layout.ViewId;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class TabContextMenu
{

    private final TabRegistry registry;
    private final Consumer<EditorTab> closeClass;
    private final Consumer<ResourceEditorTab> closeResource;
    private final Consumer<String> closeCustom;

    private ViewHost host;

    private StackId about = Stacks.DOCUMENTS;

    TabContextMenu(TabRegistry registry, Consumer<EditorTab> closeClass, Consumer<ResourceEditorTab> closeResource, Consumer<String> closeCustom)
    {
        this.registry = registry;
        this.closeClass = closeClass;
        this.closeResource = closeResource;
        this.closeCustom = closeCustom;
    }

    void setHost(ViewHost value)
    {
        this.host = value;
    }

    void showFor(ViewId view, MouseEvent event)
    {
        if (host == null)
        {
            return;
        }
        final Component body = host.bodyOf(view).orElse(null);
        if (body == null || registry.classify(body) == TabRegistry.Kind.NONE)
        {
            return;
        }
        about = host.stackOf(view).orElse(Stacks.DOCUMENTS);
        showMenu(body, event);
    }

    private void showMenu(Component body, MouseEvent event)
    {
        final JPopupMenu menu = new JPopupMenu();
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        final int closableLeft = closableBeside(body, true);
        final int closableRight = closableBeside(body, false);

        menu.add(item("Close", () -> closeTabComponent(body)));

        final JMenuItem others = item("Close Others", () -> closeOtherTabs(body));
        others.setEnabled(closableLeft + closableRight > 0);
        menu.add(others);

        menu.add(item("Close All", this::closeAllTabs));
        menu.addSeparator();

        final JMenuItem toLeft = item("Close Tabs to the Left", () -> closeTabsToLeft(body));
        toLeft.setEnabled(closableLeft > 0);
        menu.add(toLeft);

        final JMenuItem toRight = item("Close Tabs to the Right", () -> closeTabsToRight(body));
        toRight.setEnabled(closableRight > 0);
        menu.add(toRight);

        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private JMenuItem item(String text, Runnable action)
    {
        final JMenuItem made = new JMenuItem(text);
        made.setBackground(JStudioTheme.getBgSecondary());
        made.setForeground(JStudioTheme.getTextPrimary());
        made.addActionListener(ignored -> action.run());
        return made;
    }

    void closeAllTabs()
    {
        for (Component body : registry.bodies())
        {
            closeTabComponent(body);
        }
        showWelcome();
    }

    void closeOtherTabs(Component keepTab)
    {
        for (Component body : besides(keepTab, null))
        {
            closeTabComponent(body);
        }
        select(keepTab);
    }

    void closeTabsToLeft(Component referenceTab)
    {
        for (Component body : besides(referenceTab, Boolean.TRUE))
        {
            closeTabComponent(body);
        }
        select(referenceTab);
    }

    void closeTabsToRight(Component referenceTab)
    {
        for (Component body : besides(referenceTab, Boolean.FALSE))
        {
            closeTabComponent(body);
        }
        select(referenceTab);
    }

    private List<Component> besides(Component anchor, Boolean toTheLeft)
    {
        if (host == null)
        {
            return Collections.emptyList();
        }
        final StackId stack = viewOf(anchor).flatMap(host::stackOf).orElse(about);
        final List<ViewId> open = host.viewsIn(stack);
        final int at = indexOf(open, anchor);
        if (at < 0)
        {
            return Collections.emptyList();
        }
        final List<Component> out = new ArrayList<>();
        for (int index = 0; index < open.size(); index++)
        {
            if (index == at)
            {
                continue;
            }
            if (toTheLeft != null && toTheLeft != (index < at))
            {
                continue;
            }
            host.bodyOf(open.get(index))
                    .filter(body -> !registry.isWelcome(body))
                    .ifPresent(out::add);
        }
        return out;
    }

    private int closableBeside(Component anchor, boolean toTheLeft)
    {
        return besides(anchor, toTheLeft).size();
    }

    private int indexOf(List<ViewId> open, Component body)
    {
        for (int index = 0; index < open.size(); index++)
        {
            if (host.bodyOf(open.get(index)).orElse(null) == body)
            {
                return index;
            }
        }
        return -1;
    }

    private java.util.Optional<ViewId> viewOf(Component body)
    {
        if (host == null || !(body instanceof JComponent))
        {
            return java.util.Optional.empty();
        }
        return host.viewOf((JComponent) body);
    }

    private void select(Component body)
    {
        viewOf(body).ifPresent(host::select);
    }

    private void showWelcome()
    {
        if (host != null)
        {
            host.select(com.tonic.ui.layout.Views.WELCOME);
        }
    }

    private void closeTabComponent(Component content)
    {
        switch (registry.classify(content))
        {
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
}
