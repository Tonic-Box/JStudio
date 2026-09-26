package com.tonic.ui.layout;

import java.util.Objects;
import javax.swing.Icon;

/** How one tab is drawn and whether it can be closed or moved, kept apart from the view it stands for. */
public final class TabLook
{

    private final String title;
    private final Icon icon;
    private final String tooltip;
    private final boolean closable;
    private final boolean movable;

    /**
     * Describes a tab.
     *
     * @param title what the tab says
     * @param icon the icon beside the title, or null
     * @param tooltip what hovering the tab says, or null
     * @param closable whether it has a close button and answers a middle-click
     * @param movable whether it may be dragged
     */
    public TabLook(String title, Icon icon, String tooltip, boolean closable, boolean movable)
    {
        this.title = title;
        this.icon = icon;
        this.tooltip = tooltip;
        this.closable = closable;
        this.movable = movable;
    }

    /** @return what the tab says */
    public String title()
    {
        return title;
    }

    /** @return the icon beside the title, or null */
    public Icon icon()
    {
        return icon;
    }

    /** @return what hovering the tab says, or null */
    public String tooltip()
    {
        return tooltip;
    }

    /** @return whether it has a close button and answers a middle-click */
    public boolean closable()
    {
        return closable;
    }

    /** @return whether it may be dragged */
    public boolean movable()
    {
        return movable;
    }

    /**
     * Describes an ordinary closable, movable tab with no icon.
     *
     * @param title what the tab says
     * @return the look
     */
    public static TabLook of(String title)
    {
        return new TabLook(title, null, null, true, true);
    }

    /**
     * Describes an ordinary closable, movable tab with an icon.
     *
     * @param title what the tab says
     * @param icon the icon beside the title
     * @return the look
     */
    public static TabLook of(String title, Icon icon)
    {
        return new TabLook(title, icon, null, true, true);
    }

    /**
     * Pins the tab: no close button, and it cannot be dragged.
     *
     * @return the pinned look
     */
    public TabLook pinned()
    {
        return new TabLook(title, icon, tooltip, false, false);
    }

    /**
     * Changes the tab's tooltip.
     *
     * @param said the new tooltip, or null
     * @return the changed look
     */
    public TabLook withTooltip(String said)
    {
        return new TabLook(title, icon, said, closable, movable);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof TabLook))
        {
            return false;
        }
        final TabLook that = (TabLook) other;
        return closable == that.closable && movable == that.movable
                && Objects.equals(title, that.title)
                && Objects.equals(icon, that.icon)
                && Objects.equals(tooltip, that.tooltip);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(title, icon, tooltip, closable, movable);
    }

    @Override
    public String toString()
    {
        return "TabLook[" + title + "]";
    }
}
