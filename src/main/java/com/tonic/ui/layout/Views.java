package com.tonic.ui.layout;

/** The views the window ships knowing by name, plus the naming of dock tabs. */
public final class Views
{

    private static final String DOCK_PREFIX = "dock:";

    /** The page a window with nothing open shows, which is pinned and never closed. */
    public static final ViewId WELCOME = new ViewId("welcome");

    /** The tree of what is in the archive. */
    public static final ViewId NAVIGATOR = new ViewId("navigator");

    /** What the application writes as it works. */
    public static final ViewId CONSOLE = inDock("Console");

    /** The places a reader marked. */
    public static final ViewId BOOKMARKS = inDock("Bookmarks");

    /** The notes a reader left on classes and members. */
    public static final ViewId COMMENTS = inDock("Comments");

    /** What was edited, and when. */
    public static final ViewId LOCAL_HISTORY = inDock("Local History");

    private Views()
    {
    }

    /**
     * Names a view contributed to the dock.
     *
     * @param title the tab's title
     * @return the view's identity
     */
    public static ViewId inDock(String title)
    {
        return new ViewId(DOCK_PREFIX + title);
    }

    /**
     * Finds what a view is called on its tab.
     *
     * @param view the view to name
     * @return its title
     */
    public static String title(ViewId view)
    {
        if (NAVIGATOR.equals(view))
        {
            return "Navigator";
        }
        if (WELCOME.equals(view))
        {
            return "Welcome";
        }
        if (view.key().startsWith(DOCK_PREFIX))
        {
            return view.key().substring(DOCK_PREFIX.length());
        }
        return view.key();
    }
}
