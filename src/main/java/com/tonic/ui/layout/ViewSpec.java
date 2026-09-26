package com.tonic.ui.layout;

import javax.swing.JComponent;

/** Everything the layout needs to place, draw and close one view. */
public final class ViewSpec
{

    /** Where a view may be docked: a document only in the middle or its own window, a utility anywhere. */
    public enum Kind
    {
        DOCUMENT,
        UTILITY;

        /**
         * Tells whether a stack will take a view of this kind.
         *
         * @param stack the stack being dropped on
         * @return true where the view may be docked there
         */
        public boolean fits(StackId stack)
        {
            return this == UTILITY || Stacks.DOCUMENTS.equals(stack) || !Stacks.isShipped(stack);
        }
    }

    private final ViewId id;
    private final TabLook look;
    private final Kind kind;
    private final StackId home;
    private final JComponent body;
    private final Runnable onClose;

    /**
     * Describes a view.
     *
     * @param id the view's identity
     * @param look how its tab is drawn
     * @param kind where it may be docked
     * @param home the stack it opens in when nothing says otherwise
     * @param body the component that draws it
     * @param onClose what closing its tab means to its owner, or null
     * @throws IllegalArgumentException if any argument but onClose is null
     */
    public ViewSpec(ViewId id, TabLook look, Kind kind, StackId home, JComponent body, Runnable onClose)
    {
        if (id == null || look == null || kind == null || home == null || body == null)
        {
            throw new IllegalArgumentException("a view needs all of it");
        }
        this.id = id;
        this.look = look;
        this.kind = kind;
        this.home = home;
        this.body = body;
        this.onClose = onClose;
    }

    /** @return the view's identity */
    public ViewId id()
    {
        return id;
    }

    /** @return how its tab is drawn */
    public TabLook look()
    {
        return look;
    }

    /** @return where it may be docked */
    public Kind kind()
    {
        return kind;
    }

    /** @return the stack it opens in when nothing says otherwise */
    public StackId home()
    {
        return home;
    }

    /** @return the component that draws it */
    public JComponent body()
    {
        return body;
    }

    /** @return what closing its tab means to its owner, or null */
    public Runnable onClose()
    {
        return onClose;
    }

    /**
     * Describes a utility view, which may be docked anywhere.
     *
     * @param id the view's identity
     * @param look how its tab is drawn
     * @param home the stack it opens in
     * @param body the component that draws it
     * @return the description
     * @throws IllegalArgumentException if any argument is null
     */
    public static ViewSpec utility(ViewId id, TabLook look, StackId home, JComponent body)
    {
        return new ViewSpec(id, look, Kind.UTILITY, home, body, null);
    }

    /**
     * Describes a document, which docks only in the middle or in a window of its own.
     *
     * @param id the view's identity
     * @param look how its tab is drawn
     * @param body the component that draws it
     * @return the description
     * @throws IllegalArgumentException if any argument is null
     */
    public static ViewSpec document(ViewId id, TabLook look, JComponent body)
    {
        return new ViewSpec(id, look, Kind.DOCUMENT, Stacks.DOCUMENTS, body, null);
    }

    /**
     * Attaches what closing the view's tab means to its owner.
     *
     * @param closer run when the tab is closed
     * @return the same view with the close action
     */
    public ViewSpec closedBy(Runnable closer)
    {
        return new ViewSpec(id, look, kind, home, body, closer);
    }

    @Override
    public String toString()
    {
        return "ViewSpec[" + id + " " + kind + " home=" + home + "]";
    }
}
