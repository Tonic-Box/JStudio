package com.tonic.ui.core.component;

import com.tonic.ui.layout.Stacks;
import com.tonic.ui.layout.TabLook;
import com.tonic.ui.layout.ViewHost;
import com.tonic.ui.layout.ViewId;
import com.tonic.ui.layout.ViewSpec;

import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** The registry of tool windows; each tool is a view homed in the tools stack and can be dragged anywhere like any other. */
public class ToolWindowPane
{

    private static final String PREFIX = "tool:";

    /** Where a tool can be sent from its menu. */
    public enum MoveTarget
    {
        TAB,
        WINDOW
    }

    private final Map<String, JComponent> tools = new LinkedHashMap<>();

    private ViewHost host;
    private BiConsumer<String, MoveTarget> moveListener = (title, target) ->
    {
    };

    /**
     * Names the view of a tool.
     *
     * @param title the tool's title
     * @return the view's identity
     */
    public static ViewId view(String title)
    {
        return new ViewId(PREFIX + title);
    }

    private Optional<String> titleOf(ViewId id)
    {
        final String key = id.key();
        if (!key.startsWith(PREFIX))
        {
            return Optional.empty();
        }
        final String title = key.substring(PREFIX.length());
        return tools.containsKey(title) ? Optional.of(title) : Optional.empty();
    }

    /**
     * Attaches the layout and opens every tool registered before it existed.
     *
     * @param value the layout the tools live in
     */
    public void setHost(ViewHost value)
    {
        this.host = value;
        value.addListener(new ViewHost.Listener()
        {
            @Override
            public void menuRequested(ViewId id, MouseEvent event)
            {
                titleOf(id).ifPresent(title -> showMenuOn(event, title));
            }
        });
        List.copyOf(tools.keySet()).forEach(this::place);
    }

    /**
     * Registers a tool, replacing any tool with the same title.
     *
     * @param name the tool's title
     * @param component the tool's panel
     */
    public void addTool(String name, JComponent component)
    {
        removeTool(name);
        tools.put(name, component);
        place(name);
    }

    private void place(String title)
    {
        final JComponent tool = tools.get(title);
        if (tool != null && host != null)
        {
            host.open(ViewSpec.utility(view(title), TabLook.of(title), Stacks.TOOLS, tool));
        }
    }

    /**
     * Tells whether a tool is registered.
     *
     * @param name the tool's title
     * @return true where it is registered
     */
    public boolean hasTool(String name)
    {
        return tools.containsKey(name);
    }

    /**
     * Unregisters a tool and closes its view; an unknown title is ignored.
     *
     * @param name the tool's title
     */
    public void removeTool(String name)
    {
        if (tools.remove(name) != null && host != null)
        {
            host.close(view(name));
        }
    }

    /**
     * Brings a tool forward, or puts the tools away where it is already in front or the name is null.
     *
     * @param name the tool's title, or null to put the tools away
     */
    public void select(String name)
    {
        if (host == null)
        {
            return;
        }
        if (name == null)
        {
            host.putAway(Stacks.TOOLS, true);
            return;
        }
        if (!tools.containsKey(name))
        {
            return;
        }
        if (!host.isOpen(view(name)))
        {
            place(name);
        }
        host.reveal(view(name));
    }

    /**
     * Puts the tools away, or lets them out again where any are registered.
     *
     * @param value true to put them away
     */
    public void setCollapsed(boolean value)
    {
        if (host == null)
        {
            return;
        }
        if (value)
        {
            host.putAway(Stacks.TOOLS, true);
        }
        else if (!tools.isEmpty())
        {
            host.putAway(Stacks.TOOLS, false);
        }
    }

    /**
     * Sets what handles a request, from a tool's menu, to move it.
     *
     * @param listener the handler, or null for none
     */
    public void setMoveListener(BiConsumer<String, MoveTarget> listener)
    {
        this.moveListener = listener == null ? (title, target) ->
        {
        } : listener;
    }

    /**
     * Tells whether the tools are put away or off the window.
     *
     * @return true where the tools are not open
     */
    public boolean isCollapsed()
    {
        return host == null || !host.showsStack(Stacks.TOOLS)
                || host.isPutAway(Stacks.TOOLS);
    }

    private void showMenuOn(MouseEvent event, String title)
    {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem toTab = new JMenuItem("Move to Tab");
        toTab.addActionListener(ignored -> moveListener.accept(title, MoveTarget.TAB));
        final JMenuItem toWindow = new JMenuItem("Move to Window");
        toWindow.addActionListener(ignored -> moveListener.accept(title, MoveTarget.WINDOW));
        menu.add(toTab);
        menu.add(toWindow);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }
}
