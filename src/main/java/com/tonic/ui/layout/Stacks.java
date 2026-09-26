package com.tonic.ui.layout;

import java.util.List;
import java.util.Optional;

/** The four stacks the window ships with, and the edge and share each returns to when shown again. */
public final class Stacks
{

    /** The documents in the middle, the one shipped stack a document may be docked in. */
    public static final StackId DOCUMENTS = new StackId("editor");

    /** The navigator down the left side. */
    public static final StackId NAVIGATOR = new StackId("navigator");

    /** The tool windows down the right side. */
    public static final StackId TOOLS = new StackId("tools");

    /** The dock of output tabs along the bottom. */
    public static final StackId BOTTOM = new StackId("dock");

    private Stacks()
    {
    }

    /**
     * Lists the shipped stacks.
     *
     * @return the four shipped stacks, in no particular order
     */
    public static List<StackId> shipped()
    {
        return List.of(DOCUMENTS, NAVIGATOR, TOOLS, BOTTOM);
    }

    /**
     * Tells whether a stack shipped rather than being made by a reader.
     *
     * @param stack the stack
     * @return true for the four shipped stacks
     */
    public static boolean isShipped(StackId stack)
    {
        return shipped().contains(stack);
    }

    /**
     * Finds the shipped stack a key names.
     *
     * @param key the key from the layout file
     * @return the shipped stack, or empty where a reader made it
     */
    public static Optional<StackId> named(String key)
    {
        return shipped().stream().filter(stack -> stack.key().equals(key)).findFirst();
    }

    /**
     * Finds what a stack is called on its grab bar.
     *
     * @param stack the stack
     * @return its name, or Panel for one a reader made
     */
    public static String title(StackId stack)
    {
        if (DOCUMENTS.equals(stack))
        {
            return "Editor";
        }
        if (NAVIGATOR.equals(stack))
        {
            return "Navigator";
        }
        if (TOOLS.equals(stack))
        {
            return "Tools";
        }
        if (BOTTOM.equals(stack))
        {
            return "Dock";
        }
        return "Panel";
    }

    /**
     * Tells whether a stack lives down a side, which gives it upright tabs and keeps it on the window when emptied.
     *
     * @param stack the stack
     * @return true for the navigator and the tools
     */
    public static boolean isSidePanel(StackId stack)
    {
        return NAVIGATOR.equals(stack) || TOOLS.equals(stack);
    }

    /**
     * Finds the side of the window a stack returns to when shown again.
     *
     * @param stack the stack
     * @return its home edge
     */
    public static Edge homeEdge(StackId stack)
    {
        if (NAVIGATOR.equals(stack))
        {
            return Edge.LEFT;
        }
        if (TOOLS.equals(stack))
        {
            return Edge.RIGHT;
        }
        if (BOTTOM.equals(stack))
        {
            return Edge.BOTTOM;
        }
        return Edge.LEFT;
    }

    /**
     * Finds the share of the window a stack takes when it returns, which is its own share and not the divider position.
     *
     * @param stack the stack
     * @return its share, between 0 and 1
     */
    public static double homeWeight(StackId stack)
    {
        if (NAVIGATOR.equals(stack))
        {
            return 0.2;
        }
        if (TOOLS.equals(stack))
        {
            return 0.22;
        }
        if (BOTTOM.equals(stack))
        {
            return 0.28;
        }
        return 0.28;
    }
}
