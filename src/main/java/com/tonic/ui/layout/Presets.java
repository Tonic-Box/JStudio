package com.tonic.ui.layout;

import com.tonic.ui.layout.Arrangement.Divided;
import com.tonic.ui.layout.Arrangement.Node;
import com.tonic.ui.layout.Arrangement.Stack;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The arrangement the window ships with and returns to on Reset Layout. */
public final class Presets
{

    private Presets()
    {
    }

    /**
     * Builds the shipped arrangement: navigator, then documents over the dock, then tools.
     *
     * @return the shipped arrangement
     */
    public static Arrangement shipped()
    {
        final Node documentsOverDock = new Divided(false, 0.72, shippedStack(Stacks.DOCUMENTS), shippedStack(Stacks.BOTTOM));
        final Node middle = new Divided(true, 1 - Stacks.homeWeight(Stacks.TOOLS), documentsOverDock, shippedStack(Stacks.TOOLS));
        final Node all = new Divided(true, 0.2, shippedStack(Stacks.NAVIGATOR), middle);
        return new Arrangement(Optional.of(all), List.of(), List.of(), Set.of(Stacks.BOTTOM, Stacks.TOOLS));
    }

    /**
     * Prepares a saved arrangement for launch by putting the dock and the tools away.
     *
     * @param saved the arrangement read from the layout file
     * @return the arrangement to open the window with
     */
    public static Arrangement asLaunched(Arrangement saved)
    {
        return saved
                .withCollapsed(Stacks.BOTTOM, true)
                .withCollapsed(Stacks.TOOLS, true);
    }

    private static Stack shippedStack(StackId stack)
    {
        return new Stack(stack, contentsOf(stack), 0);
    }

    /**
     * Lists what a stack ships holding, which is what it holds when shown again after being removed.
     *
     * @param stack the stack
     * @return its shipped views, empty for the tools and for any stack a reader made
     */
    public static List<ViewId> contentsOf(StackId stack)
    {
        if (Stacks.DOCUMENTS.equals(stack))
        {
            return List.of(Views.WELCOME);
        }
        if (Stacks.BOTTOM.equals(stack))
        {
            return List.of(Views.CONSOLE, Views.BOOKMARKS, Views.COMMENTS, Views.LOCAL_HISTORY);
        }
        if (Stacks.NAVIGATOR.equals(stack))
        {
            return List.of(Views.NAVIGATOR);
        }
        return List.of();
    }
}
