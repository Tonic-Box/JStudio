package com.tonic.ui.layout;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;

/** The single owner of where every view is; everything that opens, moves or closes a view goes through it. */
public interface ViewHost
{

    /**
     * Opens a view in its home stack, or brings it forward where it is already open.
     *
     * @param spec the view to open
     */
    void open(ViewSpec spec);

    /**
     * Takes a view off the window; closing a view that is not open does nothing.
     *
     * @param view the view to close
     */
    void close(ViewId view);

    /**
     * Brings an open view forward, wherever it is.
     *
     * @param view the view to select
     */
    void select(ViewId view);

    /**
     * Tells whether a view is open anywhere, torn-out windows included.
     *
     * @param view the view
     * @return true where it is open
     */
    boolean isOpen(ViewId view);

    /**
     * Redraws an open view's tab.
     *
     * @param view the view
     * @param look how its tab is now drawn
     */
    void relook(ViewId view, TabLook look);

    /**
     * Finds the component that draws a view.
     *
     * @param view the view
     * @return its component, or empty where it is not open
     */
    Optional<JComponent> bodyOf(ViewId view);

    /**
     * Finds the stack holding a view.
     *
     * @param view the view
     * @return its stack, or empty where it is not open
     */
    Optional<StackId> stackOf(ViewId view);

    /**
     * Finds the view in front of a stack.
     *
     * @param stack the stack
     * @return the view in front, or empty where the stack holds nothing
     */
    Optional<ViewId> frontOf(StackId stack);

    /**
     * Lists the views in a stack.
     *
     * @param stack the stack
     * @return its views in tab order
     */
    List<ViewId> viewsIn(StackId stack);

    /**
     * Finds the view a component draws.
     *
     * @param body the component
     * @return its view, or empty where it is not open
     */
    Optional<ViewId> viewOf(JComponent body);

    /**
     * Moves a view into a window of its own; a view already alone in one stays put.
     *
     * @param view the view
     * @param onScreen where to put the window, or null to let the platform choose
     */
    void tearOut(ViewId view, java.awt.Point onScreen);

    /**
     * Tells whether a stack is on the window.
     *
     * @param stack the stack
     * @return true where it is drawn
     */
    boolean showsStack(StackId stack);

    /**
     * Tells whether a stack is put away against its edge.
     *
     * @param stack the stack
     * @return true where it is put away
     */
    boolean isPutAway(StackId stack);

    /**
     * Puts a stack away against its edge, or lets it out again.
     *
     * @param stack the stack
     * @param away true to put it away, false to let it out
     */
    void putAway(StackId stack, boolean away);

    /**
     * Brings a view forward and opens its stack, or puts the stack away where the view is already in front of it.
     *
     * @param view the view
     */
    void reveal(ViewId view);

    /**
     * Moves an open view into a stack and brings it forward; a stack that will not take it leaves it where it is.
     *
     * @param view the view
     * @param onto the stack to move it into
     */
    void moveTo(ViewId view, StackId onto);

    /**
     * Registers a listener for what a reader does to any tab.
     *
     * @param listener the listener
     */
    void addListener(Listener listener);

    /** Hears what a reader does to every tab, and acts on the views it owns. */
    interface Listener
    {

        /**
         * Called when a view comes forward.
         *
         * @param view the view
         */
        default void viewSelected(ViewId view)
        {
        }

        /**
         * Called when a tab header is pressed with the left button, after the view comes forward.
         *
         * @param view the view
         * @param inFront whether it was already in front before the press
         */
        default void headerPressed(ViewId view, boolean inFront)
        {
        }

        /**
         * Called when a reader asks for a view's menu.
         *
         * @param view the view
         * @param event the mouse event that asked
         */
        default void menuRequested(ViewId view, MouseEvent event)
        {
        }
    }
}
