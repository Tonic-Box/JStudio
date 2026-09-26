package com.tonic.ui.layout;

import com.tonic.ui.bottom.BottomToolbar;
import com.tonic.ui.layout.Arrangement.Divided;
import com.tonic.ui.layout.Arrangement.Node;
import com.tonic.ui.layout.Arrangement.Stack;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** The window's working area, drawn from an Arrangement; the single ViewHost every view is opened, moved and closed through. */
public final class LayoutController implements ViewHost
{

    private static final int STRIP = ViewStackPane.ACROSS;

    private static final int SMALLEST = 60;

    private final Map<ViewId, ViewSpec> specs = new LinkedHashMap<>();

    private final Map<StackId, ViewStackPane> strips = new LinkedHashMap<>();

    private final Map<StackId, FloatingStack> floating = new LinkedHashMap<>();

    private final List<ViewHost.Listener> listeners = new ArrayList<>();

    private final BottomToolbar bottomToolbar;

    private final JPanel host = new JPanel(new BorderLayout());

    private final Map<Divided, JSplitPane> made = new IdentityHashMap<>();

    private final Map<Node, JComponent> drawn = new IdentityHashMap<>();

    private Arrangement arrangement = Presets.shipped();

    private transient TabDrag carrying;

    private final transient Map<JRootPane, TabDragOverlay> overlays = new LinkedHashMap<>();

    private transient DragGhost ghost;

    private int stacksMade;

    /**
     * Creates the working area with the navigator open in its side stack.
     *
     * @param navigator the navigator's component
     * @param bottomToolbar the toolbar drawn under the whole working area
     */
    public LayoutController(JComponent navigator, BottomToolbar bottomToolbar)
    {
        this.bottomToolbar = bottomToolbar;
        open(ViewSpec.utility(Views.NAVIGATOR, TabLook.of("Navigator"), Stacks.NAVIGATOR, navigator));
    }

    /**
     * Draws the current arrangement into the component the frame puts in its content pane.
     *
     * @return the working area, which stays the same component across rebuilds
     */
    public JComponent buildCenter()
    {
        rebuild();
        return host;
    }

    /**
     * Reads the arrangement being drawn, with each divider where the reader left it.
     *
     * @return the current arrangement
     */
    public Arrangement arrangement()
    {
        return arrangement.withWeights(node -> node instanceof Divided ? measured((Divided) node) : null);
    }

    private static Arrangement madeFor(Arrangement into, ViewId view, StackId home)
    {
        return into.withShown(home, List.of(view)).withCollapsed(home, true);
    }

    private void keepTheDividers()
    {
        arrangement = arrangement();
    }

    /**
     * Draws a different arrangement; any open view it does not place goes back to its home stack.
     *
     * @param next the arrangement to draw, or null for the shipped one
     */
    public void show(Arrangement next)
    {
        Arrangement wanted = next == null ? Presets.shipped() : next.normalised();
        for (ViewSpec spec : List.copyOf(specs.values()))
        {
            if (wanted.showsView(spec.id()) || windowHolding(spec.id()) != null)
            {
                continue;
            }
            wanted = wanted.shows(spec.home())
                    ? wanted.withViewIn(spec.id(), spec.home(), Integer.MAX_VALUE)
                    : madeFor(wanted, spec.id(), spec.home());
        }
        this.arrangement = wanted;
        rebuild();
    }

    /**
     * Measures where each pane is.
     *
     * @param reference the component the bounds are relative to
     * @return every pane on screen, in layout order
     */
    public List<PaneOnScreen> panesIn(JComponent reference)
    {
        final List<PaneOnScreen> out = new ArrayList<>();
        arrangement.working().ifPresent(node -> collectPanes(node, reference, out));
        return List.copyOf(out);
    }

    /** One pane as drawn: its stack, what it holds, and its bounds. */
    public static final class PaneOnScreen
    {

        private final StackId stack;
        private final List<ViewId> views;
        private final ViewId visible;
        private final Rectangle bounds;

        /**
         * Describes a pane.
         *
         * @param stack the stack it draws
         * @param views the views it holds
         * @param visible the view in front, or null
         * @param bounds where it is
         */
        public PaneOnScreen(StackId stack, List<ViewId> views, ViewId visible, Rectangle bounds)
        {
            this.stack = stack;
            this.views = views;
            this.visible = visible;
            this.bounds = bounds;
        }

        /** @return the stack it draws */
        public StackId stack()
        {
            return stack;
        }

        /** @return the views it holds */
        public List<ViewId> views()
        {
            return views;
        }

        /** @return the view in front, or null */
        public ViewId visible()
        {
            return visible;
        }

        /** @return where it is */
        public Rectangle bounds()
        {
            return bounds;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof PaneOnScreen))
            {
                return false;
            }
            final PaneOnScreen that = (PaneOnScreen) other;
            return Objects.equals(stack, that.stack)
                    && Objects.equals(views, that.views)
                    && Objects.equals(visible, that.visible)
                    && Objects.equals(bounds, that.bounds);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(stack, views, visible, bounds);
        }

        @Override
        public String toString()
        {
            return "PaneOnScreen[" + stack + " " + bounds + "]";
        }
    }

    private void collectPanes(Node node, JComponent reference, List<PaneOnScreen> into)
    {
        if (node instanceof Divided)
        {
            final Divided divided = (Divided) node;
            collectPanes(divided.first(), reference, into);
            collectPanes(divided.second(), reference, into);
            return;
        }
        final JComponent component = drawn.get(node);
        if (component == null || !component.isShowing() || !reference.isShowing())
        {
            return;
        }
        final Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), reference);
        final List<ViewId> views = node.views();
        final ViewId visible = node instanceof Stack
                ? ((Stack) node).showing().orElse(null)
                : views.isEmpty() ? null : views.get(0);
        into.add(new PaneOnScreen(node.stacks().get(0), views, visible, bounds));
    }

    /**
     * Tells whether a stack is on the window.
     *
     * @param stack the stack
     * @return true where it is in the arrangement
     */
    public boolean shows(StackId stack)
    {
        return arrangement.shows(stack);
    }

    /**
     * Tells whether a stack is put away, which an empty stack always is.
     *
     * @param stack the stack
     * @return true where it is put away or empty
     */
    public boolean isCollapsed(StackId stack)
    {
        return arrangement.isCollapsed(stack) || arrangement.viewsIn(stack).isEmpty();
    }

    /**
     * Puts a stack away against its edge, or lets it out again; a stack not on the window is ignored.
     *
     * @param stack the stack
     * @param collapsed true to put it away, false to let it out
     */
    public void setCollapsed(StackId stack, boolean collapsed)
    {
        if (!arrangement.shows(stack) || isCollapsed(stack) == collapsed)
        {
            return;
        }
        keepTheDividers();
        show(arrangement.withCollapsed(stack, collapsed));
    }

    /**
     * Shows a stack that is off the window, or else flips it between put away and open.
     *
     * @param stack the stack
     */
    public void toggle(StackId stack)
    {
        if (!arrangement.shows(stack))
        {
            keepTheDividers();
            show(arrangement.withShown(stack, Presets.contentsOf(stack)).withCollapsed(stack, false));
        }
        else
        {
            setCollapsed(stack, !isCollapsed(stack));
        }
    }

    /**
     * Writes the arrangement, with the dividers where the reader left them, to a file.
     *
     * @param to the file to replace
     */
    public void save(Path to)
    {
        LayoutBook.save(to, arrangement());
    }

    /** Writes the arrangement to ~/.jstudio/layout.json. */
    public void saveLayout()
    {
        save(LayoutBook.defaultPath());
    }

    /**
     * Tells whether the dock is off the window or put away.
     *
     * @return true where the dock is not open
     */
    public boolean isBottomCollapsed()
    {
        return !arrangement.shows(Stacks.BOTTOM) || isCollapsed(Stacks.BOTTOM);
    }

    /** Puts the dock away. */
    public void collapseBottom()
    {
        setCollapsed(Stacks.BOTTOM, true);
    }

    /** Opens the dock, showing it first where it is off the window. */
    public void expandBottom()
    {
        if (!arrangement.shows(Stacks.BOTTOM))
        {
            keepTheDividers();
            show(arrangement.withShown(Stacks.BOTTOM, Presets.contentsOf(Stacks.BOTTOM)).withCollapsed(Stacks.BOTTOM, false));
            return;
        }
        setCollapsed(Stacks.BOTTOM, false);
    }

    /** Shows the navigator, or flips it between put away and open. */
    public void toggleNavigatorPanel()
    {
        toggle(Stacks.NAVIGATOR);
    }

    private void rebuild()
    {
        made.clear();
        drawn.clear();
        host.removeAll();
        arrangement.working().ifPresent(node ->
        {
            JComponent working = build(node, false, false);
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(working, BorderLayout.CENTER);
            wrapper.add(bottomToolbar, BorderLayout.SOUTH);
            host.add(wrapper, BorderLayout.CENTER);
        });
        host.revalidate();
        host.repaint();
    }

    private JComponent build(Node node, boolean beside, boolean atTheEnd)
    {
        if (node instanceof Stack)
        {
            final Stack stack = (Stack) node;
            final ViewStackPane strip = stripFor(stack.id());
            strip.setTabsOn(beside && Stacks.isSidePanel(stack.id()) ? (atTheEnd ? JTabbedPane.RIGHT : JTabbedPane.LEFT) : JTabbedPane.TOP);
            fill(strip, stack);
            strip.setVisible(true);
            strip.setMinimumSize(new Dimension(SMALLEST, SMALLEST));
            strip.setPreferredSize(new Dimension(0, 0));
            drawn.put(node, strip);
            return strip;
        }
        final Divided divided = (Divided) node;
        JSplitPane split = new JSplitPane(divided.horizontal() ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT, build(divided.first(), divided.horizontal(), false), build(divided.second(), divided.horizontal(), true));
        split.setDividerSize(4);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setResizeWeight(divided.weight());
        made.put(divided, split);
        drawn.put(divided, split);

        split.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                if (place(divided, split))
                {
                    split.removeComponentListener(this);
                }
            }
        });
        return split;
    }

    /**
     * Finds the tab strip that draws a stack, creating it the first time.
     *
     * @param stack the stack
     * @return its strip
     */
    public ViewStackPane stripFor(StackId stack)
    {
        final FloatingStack torn = floating.get(stack);
        if (torn != null)
        {
            return torn.strip();
        }
        return strips.computeIfAbsent(stack, id -> wire(new ViewStackPane(id)));
    }

    private ViewStackPane wire(ViewStackPane made)
    {
        made.setDragOut(new ViewStackPane.DragOut()
        {
            @Override
            public void began(ViewId view)
            {
                beginCarrying(view);
            }

            @Override
            public void movedTo(ViewId view, Point onScreen, boolean toItsOwnWindow)
            {
                carryTo(view, onScreen, toItsOwnWindow);
            }

            @Override
            public void ended(ViewId view, Point onScreen, boolean toItsOwnWindow)
            {
                drop(view, onScreen, toItsOwnWindow);
            }
        });
        made.addListener(new ViewStackPane.Listener()
        {
            @Override
            public void viewSelected(ViewId view)
            {
                retitle(made.id());
                for (ViewHost.Listener heard : List.copyOf(listeners))
                {
                    heard.viewSelected(view);
                }
            }

            @Override
            public void closeRequested(ViewId view)
            {
                if (sentHome(view))
                {
                    return;
                }
                final ViewSpec spec = specs.get(view);
                if (spec != null && spec.onClose() != null)
                {
                    spec.onClose().run();
                    return;
                }
                close(view);
            }

            @Override
            public void headerPressed(ViewId view, boolean inFront)
            {
                pressed(made.id(), view, inFront);
            }

            @Override
            public void menuRequested(ViewId view, MouseEvent event)
            {
                for (ViewHost.Listener heard : List.copyOf(listeners))
                {
                    heard.menuRequested(view, event);
                }
            }
        });
        return made;
    }

    private void fill(ViewStackPane strip, Stack stack)
    {
        final ViewId wasShowing = strip.selected().orElse(null);
        for (ViewId gone : strip.views())
        {
            if (!stack.views().contains(gone))
            {
                strip.remove(gone);
            }
        }
        int at = 0;
        for (ViewId view : stack.views())
        {
            final ViewSpec spec = specs.get(view);
            if (spec == null)
            {
                continue;
            }
            strip.insert(view, spec.look(), spec.body(), at);
            at++;
        }
        strip.order(stack.views());
        if (wasShowing != null && strip.holds(wasShowing))
        {
            strip.select(wasShowing);
        }
        else
        {
            stack.showing().ifPresent(strip::select);
        }
    }

    @Override
    public void open(ViewSpec spec)
    {
        specs.put(spec.id(), spec);
        if (isOpen(spec.id()))
        {
            relook(spec.id(), spec.look());
            select(spec.id());
            return;
        }
        if (arrangement.stackOf(spec.id()).isEmpty())
        {
            keepTheDividers();
            arrangement = arrangement.shows(spec.home())
                    ? arrangement.withViewIn(spec.id(), spec.home(), Integer.MAX_VALUE)
                    : madeFor(arrangement, spec.id(), spec.home());
        }
        rebuild();
        select(spec.id());
    }

    @Override
    public void close(ViewId view)
    {
        specs.remove(view);
        final FloatingStack torn = windowHolding(view);
        if (torn != null)
        {
            torn.strip().remove(view);
            closeEmptyWindows();
            return;
        }
        if (arrangement.stackOf(view).isEmpty())
        {
            return;
        }
        keepTheDividers();
        arrangement = arrangement.withoutView(view);
        rebuild();
    }

    @Override
    public void select(ViewId view)
    {
        stackOf(view).ifPresent(stack -> stripFor(stack).select(view));
    }

    @Override
    public boolean isOpen(ViewId view)
    {
        return stackOf(view).isPresent();
    }

    @Override
    public void relook(ViewId view, TabLook look)
    {
        final ViewSpec spec = specs.get(view);
        if (spec == null)
        {
            return;
        }
        specs.put(view, new ViewSpec(view, look, spec.kind(), spec.home(), spec.body(), spec.onClose()));
        stackOf(view).ifPresent(stack ->
        {
            stripFor(stack).relook(view, look);
            retitle(stack);
        });
    }

    @Override
    public Optional<JComponent> bodyOf(ViewId view)
    {
        return isOpen(view) ? Optional.ofNullable(specs.get(view)).map(ViewSpec::body)
                : Optional.empty();
    }

    @Override
    public Optional<StackId> stackOf(ViewId view)
    {
        final Optional<StackId> inTheWindow = arrangement.stackOf(view);
        if (inTheWindow.isPresent())
        {
            return inTheWindow;
        }
        final FloatingStack torn = windowHolding(view);
        return torn == null ? Optional.empty() : Optional.of(torn.id());
    }

    @Override
    public Optional<ViewId> frontOf(StackId stack)
    {
        final FloatingStack torn = floating.get(stack);
        if (torn != null)
        {
            return torn.strip().selected();
        }
        final ViewStackPane strip = strips.get(stack);
        return strip == null ? arrangement.stack(stack).flatMap(Stack::showing)
                : strip.selected();
    }

    @Override
    public List<ViewId> viewsIn(StackId stack)
    {
        final FloatingStack torn = floating.get(stack);
        return torn == null ? arrangement.viewsIn(stack) : torn.views();
    }

    @Override
    public Optional<ViewId> viewOf(JComponent body)
    {
        return specs.values().stream()
                .filter(spec -> spec.body() == body)
                .map(ViewSpec::id)
                .filter(this::isOpen)
                .findFirst();
    }

    /**
     * Moves a view into a window of its own, which is shown only while this window is showing.
     *
     * @param view the view
     * @param onScreen where to put the window, or null to let the platform choose
     */
    @Override
    public void tearOut(ViewId view, Point onScreen)
    {
        final FloatingStack window = windowFor(view);
        if (window != null && host.isShowing())
        {
            window.show(onScreen, specs.get(view).look().title());
        }
    }

    private FloatingStack windowFor(ViewId view)
    {
        final ViewSpec spec = specs.get(view);
        if (spec == null || windowHolding(view) != null)
        {
            return null;
        }
        final StackId made = StackId.generated(++stacksMade);
        final FloatingStack window = new FloatingStack(made, () -> putBack(made));
        wire(window.strip());
        floating.put(made, window);

        if (arrangement.stackOf(view).isPresent())
        {
            keepTheDividers();
            arrangement = arrangement.withoutView(view);
            rebuild();
        }
        window.strip().add(view, spec.look(), spec.body());
        retitle(made);
        return window;
    }

    /** Closes every torn-out window, sending its views home first. */
    public void closeTornOut()
    {
        for (FloatingStack window : List.copyOf(floating.values()))
        {
            putBack(window.id());
            window.dispose();
        }
        floating.clear();
        if (ghost != null)
        {
            ghost.dispose();
            ghost = null;
        }
    }

    private FloatingStack windowHolding(ViewId view)
    {
        return floating.values().stream()
                .filter(window -> window.strip().holds(view))
                .findFirst()
                .orElse(null);
    }

    private void putBack(StackId stack)
    {
        final FloatingStack window = floating.remove(stack);
        if (window == null)
        {
            return;
        }
        keepTheDividers();
        for (ViewId view : window.views())
        {
            final ViewSpec spec = specs.get(view);
            if (spec != null)
            {
                arrangement = arrangement.shows(spec.home())
                        ? arrangement.withViewIn(view, spec.home(), Integer.MAX_VALUE)
                        : madeFor(arrangement, view, spec.home());
            }
        }
        window.strip().clear();
        rebuild();
    }

    private void closeEmptyWindows()
    {
        for (FloatingStack window : List.copyOf(floating.values()))
        {
            if (window.views().isEmpty())
            {
                floating.remove(window.id());
                window.dispose();
            }
        }
    }

    private void retitle(StackId stack)
    {
        final FloatingStack window = floating.get(stack);
        if (window == null)
        {
            return;
        }
        final ViewId front = window.strip().selected().orElse(null);
        if (front == null)
        {
            return;
        }
        final ViewSpec spec = specs.get(front);
        if (spec != null)
        {
            window.retitle(spec.look().title());
        }
    }

    @Override
    public boolean showsStack(StackId stack)
    {
        return arrangement.shows(stack);
    }

    @Override
    public boolean isPutAway(StackId stack)
    {
        return isCollapsed(stack);
    }

    @Override
    public void putAway(StackId stack, boolean away)
    {
        setCollapsed(stack, away);
    }

    @Override
    public void reveal(ViewId view)
    {
        final StackId where = stackOf(view).orElse(null);
        if (where == null)
        {
            return;
        }
        if (!isCollapsed(where) && frontOf(where).filter(view::equals).isPresent())
        {
            setCollapsed(where, true);
            return;
        }
        setCollapsed(where, false);
        select(view);
    }

    @Override
    public void moveTo(ViewId view, StackId onto)
    {
        if (!isOpen(view) || !willTake(view, onto) || onto.equals(stackOf(view).orElse(null)))
        {
            return;
        }
        final FloatingStack window = floating.get(onto);
        keepTheDividers();
        if (window != null)
        {
            moveInto(view, window);
        }
        else
        {
            arrangement = arrangement.shows(onto)
                    ? arrangement.withViewIn(view, onto, Integer.MAX_VALUE)
                    : madeFor(arrangement, view, onto);
            rebuild();
        }
        closeEmptyWindows();
        setCollapsed(onto, false);
        select(view);
    }

    @Override
    public void addListener(ViewHost.Listener listener)
    {
        listeners.add(listener);
    }

    void pressed(StackId stack, ViewId view, boolean inFront)
    {
        openedOrShut(stack, inFront);
        for (ViewHost.Listener heard : List.copyOf(listeners))
        {
            heard.headerPressed(view, inFront);
        }
    }

    private void openedOrShut(StackId stack, boolean inFront)
    {
        if (!Stacks.isSidePanel(stack))
        {
            return;
        }
        if (isCollapsed(stack))
        {
            setCollapsed(stack, false);
        }
        else if (inFront)
        {
            setCollapsed(stack, true);
        }
    }

    private boolean sentHome(ViewId view)
    {
        final ViewSpec spec = specs.get(view);
        if (spec == null || !Stacks.isSidePanel(spec.home())
                || spec.home().equals(stackOf(view).orElse(null)))
        {
            return false;
        }
        keepTheDividers();
        arrangement = arrangement.shows(spec.home())
                ? arrangement.withViewIn(view, spec.home(), Integer.MAX_VALUE)
                : madeFor(arrangement, view, spec.home());
        rebuild();
        closeEmptyWindows();
        return true;
    }

    /**
     * Tells whether a stack will take a view dropped on it.
     *
     * @param dragged the view
     * @param stack the stack
     * @return true where the view may be docked there
     */
    public boolean willTake(ViewId dragged, StackId stack)
    {
        final ViewSpec spec = specs.get(dragged);
        return spec == null || spec.kind().fits(stack);
    }

    private static final class TabDrag
    {

        private final List<PaneOnScreen> panes;
        private final DragFeedback feedback;
        private final KeyEventDispatcher escape;

        TabDrag(List<PaneOnScreen> panes, DragFeedback feedback, KeyEventDispatcher escape)
        {
            this.panes = panes;
            this.feedback = feedback;
            this.escape = escape;
        }

        List<PaneOnScreen> panes()
        {
            return panes;
        }

        DragFeedback feedback()
        {
            return feedback;
        }

        KeyEventDispatcher escape()
        {
            return escape;
        }

        TabDrag aiming(DragFeedback found)
        {
            return new TabDrag(panes, found, escape);
        }
    }

    void beginCarrying(ViewId view)
    {
        beginCarrying(view, panesOnScreen());
        raiseOverlays();
        stackOf(view).map(this::stripFor)
                .flatMap(strip -> strip.ghostOf(view))
                .ifPresent(image -> ghost().carry(image, false));
    }

    void beginCarrying(ViewId view, List<PaneOnScreen> panes)
    {
        if (carrying != null)
        {
            stopCarrying();
        }
        final KeyEventDispatcher escape = event ->
        {
            if (event.getID() == KeyEvent.KEY_PRESSED
                    && event.getKeyCode() == KeyEvent.VK_ESCAPE && carrying != null)
            {
                stopCarrying();
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
        carrying = new TabDrag(panes, null, escape);
    }

    private List<PaneOnScreen> panesOnScreen()
    {
        final List<PaneOnScreen> out = new ArrayList<>();
        if (host.isShowing())
        {
            final Point origin = host.getLocationOnScreen();
            for (PaneOnScreen pane : panesIn(host))
            {
                final Rectangle bounds = new Rectangle(pane.bounds());
                bounds.translate(origin.x, origin.y);
                out.add(new PaneOnScreen(pane.stack(), pane.views(), pane.visible(), bounds));
            }
        }
        for (FloatingStack window : floating.values())
        {
            final Point at = window.locationOnScreen();
            if (at != null)
            {
                out.add(new PaneOnScreen(window.id(), window.views(), window.strip().selected().orElse(null), new Rectangle(at.x, at.y, window.size().width, window.size().height)));
            }
        }
        return List.copyOf(out);
    }

    private void carryTo(ViewId view, Point onScreen, boolean toItsOwnWindow)
    {
        aimAt(view, onScreen, toItsOwnWindow);
        if (carrying == null)
        {
            return;
        }
        showFeedback(carrying.feedback());
        stackOf(view).map(this::stripFor)
                .flatMap(strip -> strip.ghostOf(view))
                .ifPresent(image -> ghost().carry(image, carrying.feedback() == null));
        ghost().moveTo(onScreen);
    }

    void aimAt(ViewId view, Point onScreen, boolean toItsOwnWindow)
    {
        if (carrying == null)
        {
            return;
        }
        carrying = carrying.aiming(whatWouldHappen(view, onScreen, toItsOwnWindow));
    }

    DragFeedback aiming()
    {
        return carrying == null ? null : carrying.feedback();
    }

    private DragFeedback whatWouldHappen(ViewId view, Point onScreen, boolean toItsOwnWindow)
    {
        if (toItsOwnWindow || carrying == null)
        {
            return null;
        }
        final PaneOnScreen pane = carrying.panes().stream()
                .filter(found -> found.bounds().contains(onScreen))
                .filter(found -> willTake(view, found.stack()))
                .findFirst()
                .orElse(null);
        if (pane == null)
        {
            return null;
        }
        final ViewStackPane strip = stripFor(pane.stack());
        final Point inStrip = inStrip(strip, onScreen);
        if (inStrip != null && strip.inTheTabRow(inStrip))
        {
            final int caret = strip.dropIndexAt(inStrip);
            return new DragFeedback(pane.stack(), Edge.CENTRE, pane.bounds(), caret, strip.caretAt(caret).map(mark -> onScreen(strip, mark)).orElse(null));
        }
        final Edge edge = DropTarget.edgeIn(pane.bounds(), onScreen);
        return new DragFeedback(pane.stack(), edge, DropTarget.zoneIn(pane.bounds(), edge), -1, null);
    }

    private static Point inStrip(ViewStackPane strip, Point onScreen)
    {
        if (!strip.isShowing())
        {
            return null;
        }
        final Point inside = new Point(onScreen);
        SwingUtilities.convertPointFromScreen(inside, strip);
        return inside;
    }

    private static Rectangle onScreen(ViewStackPane strip, Rectangle inStrip)
    {
        final Point origin = strip.getLocationOnScreen();
        final Rectangle out = new Rectangle(inStrip);
        out.translate(origin.x, origin.y);
        return out;
    }

    void drop(ViewId view, Point onScreen, boolean toItsOwnWindow)
    {
        if (carrying == null)
        {
            return;
        }
        aimAt(view, onScreen, toItsOwnWindow);
        final DragFeedback where = carrying.feedback();
        stopCarrying();

        if (where == null)
        {
            tearOut(view, onScreen);
            return;
        }
        keepTheDividers();
        final FloatingStack onto = floating.get(where.onto());
        if (onto != null)
        {
            moveInto(view, onto);
        }
        else if (where.edge() == Edge.CENTRE)
        {
            arrangement = arrangement.withViewIn(view, where.onto(), landingIndex(view, where));
            rebuild();
        }
        else
        {
            arrangement = arrangement.withViewSplit(view, where.onto(), where.edge(), StackId.generated(++stacksMade));
            rebuild();
        }
        closeEmptyWindows();
        select(view);
    }

    int landingIndex(ViewId view, DragFeedback where)
    {
        final int was = viewsIn(where.onto()).indexOf(view);
        if (where.caret() < 0)
        {
            return was >= 0 ? was : Integer.MAX_VALUE;
        }
        return was >= 0 && was < where.caret() ? where.caret() - 1 : where.caret();
    }

    private void moveInto(ViewId view, FloatingStack onto)
    {
        if (arrangement.stackOf(view).isPresent())
        {
            keepTheDividers();
            arrangement = arrangement.withoutView(view);
            rebuild();
        }
        final ViewSpec spec = specs.get(view);
        if (spec != null)
        {
            onto.strip().add(view, spec.look(), spec.body());
        }
    }

    void stopCarrying()
    {
        if (carrying == null)
        {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(carrying.escape());
        carrying = null;
        showFeedback(null);
        lowerOverlays();
        if (ghost != null)
        {
            ghost.hide();
        }
    }

    private DragGhost ghost()
    {
        if (ghost == null)
        {
            ghost = new DragGhost();
        }
        return ghost;
    }

    private void raiseOverlays()
    {
        for (JRootPane root : rootPanes())
        {
            overlays.computeIfAbsent(root, TabDragOverlay::new).raise();
        }
    }

    private void lowerOverlays()
    {
        overlays.values().forEach(TabDragOverlay::lower);
    }

    private void showFeedback(DragFeedback where)
    {
        overlays.values().forEach(overlay -> overlay.show(where));
    }

    private List<JRootPane> rootPanes()
    {
        final List<JRootPane> out = new ArrayList<>();
        final JRootPane here = SwingUtilities.getRootPane(host);
        if (here != null)
        {
            out.add(here);
        }
        floating.values().stream()
                .map(FloatingStack::rootPane)
                .filter(Objects::nonNull)
                .forEach(out::add);
        return out;
    }

    private boolean place(Divided divided, JSplitPane split)
    {
        final int span = divided.horizontal() ? split.getWidth() : split.getHeight();
        if (span <= 0)
        {
            return false;
        }
        final int usable = Math.max(0, span - split.getDividerSize());
        if (isPutAway(divided.first()))
        {
            split.setDividerLocation(extentOf(divided.first(), divided.horizontal()));
            return false;
        }
        if (isPutAway(divided.second()))
        {
            split.setDividerLocation(Math.max(0, usable - extentOf(divided.second(), divided.horizontal())));
            return false;
        }
        split.setDividerLocation((int) Math.round(usable * divided.weight()));
        return true;
    }

    private boolean isPutAway(Node node)
    {
        final List<StackId> stacks = node.stacks();
        return !stacks.isEmpty() && stacks.stream().allMatch(this::isCollapsed);
    }

    private int extentOf(Node node, boolean horizontal)
    {
        int most = 0;
        for (StackId stack : node.stacks())
        {
            final ViewStackPane strip = strips.get(stack);
            most = Math.max(most, strip == null ? 0 : strip.stripExtent());
        }
        return most;
    }

    private Double measured(Divided divided)
    {
        final JSplitPane split = made.get(divided);
        if (split == null || isPutAway(divided.first()) || isPutAway(divided.second()))
        {
            return null;
        }
        final int span = divided.horizontal() ? split.getWidth() : split.getHeight();
        final int usable = span - split.getDividerSize();
        if (usable <= 0)
        {
            return null;
        }
        final double fraction = split.getDividerLocation() / (double) usable;
        return Math.min(0.9, Math.max(0.1, fraction));
    }
}
