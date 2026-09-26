package com.tonic.ui.layout;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.ThemeStyles;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The tab strip that draws one stack of views, the same everywhere a tab can be; it reports gestures and leaves decisions to its owner. */
public final class ViewStackPane extends ThemedJPanel
{

    private static final long serialVersionUID = 1L;

    private static final int DRAG_THRESHOLD = 5;

    private static final int UNMEASURED_STRIP = 28;

    static final int ACROSS = 26;

    /** Hears a tab being dragged off its strip, in screen coordinates. */
    public interface DragOut
    {

        /**
         * Called when a drag begins.
         *
         * @param view the view being dragged
         */
        void began(ViewId view);

        /**
         * Called as the pointer moves during a drag.
         *
         * @param view the view being dragged
         * @param onScreen the pointer
         * @param toItsOwnWindow whether the reader is asking for a new window rather than for what is under the pointer
         */
        void movedTo(ViewId view, Point onScreen, boolean toItsOwnWindow);

        /**
         * Called when the reader lets go.
         *
         * @param view the view being dragged
         * @param onScreen the pointer
         * @param toItsOwnWindow whether the reader is asking for a new window rather than for what is under the pointer
         */
        void ended(ViewId view, Point onScreen, boolean toItsOwnWindow);
    }

    /** Hears what a reader does to the strip's tabs; every method defaults to doing nothing. */
    public interface Listener
    {

        /**
         * Called when a different tab comes forward.
         *
         * @param view the view now in front
         */
        default void viewSelected(ViewId view)
        {
        }

        /**
         * Called when a reader asks to close a tab by its close button or a middle-click; the tab is not removed.
         *
         * @param view the view to close
         */
        default void closeRequested(ViewId view)
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
         * Called when a reader asks for a tab's menu.
         *
         * @param view the view
         * @param event the mouse event that asked
         */
        default void menuRequested(ViewId view, MouseEvent event)
        {
        }
    }

    private final StackId id;
    private final JTabbedPane tabs;
    private final Map<ViewId, JComponent> bodies = new LinkedHashMap<>();
    private final Map<Component, ViewId> owners = new LinkedHashMap<>();

    private final Map<ViewId, TabLook> looks = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    private ViewId inFront;
    private DragOut dragOut;
    private int placement = JTabbedPane.TOP;

    /**
     * Creates an empty strip with its tabs along the top.
     *
     * @param id the stack it draws
     */
    public ViewStackPane(StackId id)
    {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.id = id;

        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBorder(null);
        for (String taken : new String[]{"ctrl PAGE_UP", "ctrl PAGE_DOWN"})
        {
            tabs.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(taken), "none");
        }
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabs, BorderLayout.CENTER);

        tabs.addChangeListener(event -> announceSelection());
        styleTabbedPane();
    }

    /** @return the stack this strip draws */
    public StackId id()
    {
        return id;
    }

    /**
     * Puts the tabs along the top, or upright down the left or right side.
     *
     * @param side JTabbedPane.TOP, LEFT or RIGHT
     */
    public void setTabsOn(int side)
    {
        if (placement == side)
        {
            return;
        }
        placement = side;
        tabs.setTabPlacement(side);
        tabs.putClientProperty("JTabbedPane.tabInsets", upright() ? new Insets(6, 0, 6, 0) : null);
        tabs.putClientProperty("JTabbedPane.tabAreaInsets", upright() ? new Insets(0, 0, 0, 0) : null);
        tabs.putClientProperty("JTabbedPane.tabHeight", upright() ? ACROSS : null);
        tabs.putClientProperty("JTabbedPane.minimumTabWidth", upright() ? ACROSS : null);
        List.copyOf(bodies.keySet()).forEach(view -> relook(view, looks.get(view)));
    }

    /** @return the side the tabs are on: JTabbedPane.TOP, LEFT or RIGHT */
    public int tabsOn()
    {
        return placement;
    }

    private boolean upright()
    {
        return placement == JTabbedPane.LEFT || placement == JTabbedPane.RIGHT;
    }

    /**
     * Registers a listener for what a reader does to the tabs.
     *
     * @param listener the listener
     */
    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    /**
     * Sets what hears a tab being dragged off this strip.
     *
     * @param value the drag handler, or null for none
     */
    public void setDragOut(DragOut value)
    {
        this.dragOut = value;
    }

    /**
     * Puts a view at the end of the strip and brings it forward.
     *
     * @param view the view
     * @param look how its tab is drawn
     * @param body the component that draws it
     */
    public void add(ViewId view, TabLook look, JComponent body)
    {
        insert(view, look, body, count());
    }

    /**
     * Puts a view at a place in the strip and brings it forward; a view already here is redrawn and brought forward instead.
     *
     * @param view the view
     * @param look how its tab is drawn
     * @param body the component that draws it
     * @param index where among the tabs, clamped to the tabs there are
     */
    public void insert(ViewId view, TabLook look, JComponent body, int index)
    {
        if (holds(view))
        {
            relook(view, look);
            select(view);
            return;
        }
        final int at = Math.max(0, Math.min(index, count()));
        bodies.put(view, body);
        owners.put(body, view);
        looks.put(view, look);
        tabs.insertTab(look.title(), null, body, look.tooltip(), at);
        tabs.setTabComponentAt(at, header(view, look));
        select(view);
    }

    /**
     * Takes a view out of the strip; a view that is not here is ignored.
     *
     * @param view the view
     */
    public void remove(ViewId view)
    {
        final JComponent body = bodies.remove(view);
        if (body == null)
        {
            return;
        }
        owners.remove(body);
        looks.remove(view);
        final int at = tabs.indexOfComponent(body);
        if (at >= 0)
        {
            tabs.removeTabAt(at);
        }
    }

    /** Empties the strip without telling any listener. */
    public void clear()
    {
        bodies.clear();
        owners.clear();
        looks.clear();
        tabs.removeAll();
        inFront = null;
    }

    /**
     * Redraws a tab's header.
     *
     * @param view the view
     * @param look how its tab is now drawn
     */
    public void relook(ViewId view, TabLook look)
    {
        final JComponent body = bodies.get(view);
        if (body == null || look == null)
        {
            return;
        }
        looks.put(view, look);
        final int at = tabs.indexOfComponent(body);
        if (at < 0)
        {
            return;
        }
        tabs.setTitleAt(at, look.title());
        tabs.setToolTipTextAt(at, look.tooltip());
        tabs.setTabComponentAt(at, header(view, look));
    }

    /**
     * Finds what a view's tab says.
     *
     * @param view the view
     * @return its title, or empty where the view is not here
     */
    public Optional<String> titleOf(ViewId view)
    {
        final JComponent body = bodies.get(view);
        if (body == null)
        {
            return Optional.empty();
        }
        final int at = tabs.indexOfComponent(body);
        return at < 0 ? Optional.empty() : Optional.ofNullable(tabs.getTitleAt(at));
    }

    /**
     * Tells whether a view is in this strip, asking the tabs themselves since Swing moves a component given to another strip.
     *
     * @param view the view
     * @return true where the view is here
     */
    public boolean holds(ViewId view)
    {
        final JComponent body = bodies.get(view);
        return body != null && tabs.indexOfComponent(body) >= 0;
    }

    /**
     * Finds the component that draws a view.
     *
     * @param view the view
     * @return its component, or empty where the view is not here
     */
    public Optional<JComponent> bodyOf(ViewId view)
    {
        return holds(view) ? Optional.ofNullable(bodies.get(view)) : Optional.empty();
    }

    /**
     * Lists the views in the strip.
     *
     * @return the views in tab order
     */
    public List<ViewId> views()
    {
        final List<ViewId> order = new ArrayList<>(tabs.getTabCount());
        for (int at = 0; at < tabs.getTabCount(); at++)
        {
            final ViewId view = owners.get(tabs.getComponentAt(at));
            if (view != null)
            {
                order.add(view);
            }
        }
        return List.copyOf(order);
    }

    /**
     * Lists the components in the strip.
     *
     * @return the components in tab order
     */
    public List<JComponent> bodies()
    {
        final List<JComponent> order = new ArrayList<>(tabs.getTabCount());
        for (int at = 0; at < tabs.getTabCount(); at++)
        {
            final Component body = tabs.getComponentAt(at);
            if (body instanceof JComponent && owners.containsKey(body))
            {
                order.add((JComponent) body);
            }
        }
        return List.copyOf(order);
    }

    /**
     * Finds the view a component draws.
     *
     * @param body the component
     * @return its view, or empty where it is not here
     */
    public Optional<ViewId> viewOf(Component body)
    {
        return body == null ? Optional.empty() : Optional.ofNullable(owners.get(body));
    }

    /**
     * Counts the tabs.
     *
     * @return the number of tabs
     */
    public int count()
    {
        return tabs.getTabCount();
    }

    /**
     * Brings a view forward; a view that is not here is ignored.
     *
     * @param view the view
     */
    public void select(ViewId view)
    {
        final JComponent body = bodies.get(view);
        if (body != null && tabs.indexOfComponent(body) >= 0)
        {
            tabs.setSelectedComponent(body);
        }
    }

    /**
     * Finds the view in front.
     *
     * @return the view, or empty where the strip is empty
     */
    public Optional<ViewId> selected()
    {
        final Component showing = tabs.getSelectedComponent();
        return showing == null ? Optional.empty() : Optional.ofNullable(owners.get(showing));
    }

    /**
     * Measures the extent that shows only the tabs, which is what a put-away stack shrinks to.
     *
     * @return the height of a row of tabs, or the width of an upright column
     */
    public int stripExtent()
    {
        if (tabs.getTabCount() == 0)
        {
            return upright() ? ACROSS : 0;
        }
        final Rectangle bounds = tabs.getBoundsAt(0);
        if (upright())
        {
            final int wide = bounds != null && bounds.width > 0 ? bounds.width : ACROSS;
            return wide + getInsets().left + getInsets().right;
        }
        final int high = bounds != null && bounds.height > 0 ? bounds.height : UNMEASURED_STRIP;
        return high + getInsets().top + getInsets().bottom + UIConstants.SPACING_SMALL;
    }

    private void announceSelection()
    {
        final ViewId now = selected().orElse(null);
        if (now == null || now.equals(inFront))
        {
            inFront = now;
            return;
        }
        inFront = now;
        for (Listener listener : List.copyOf(listeners))
        {
            listener.viewSelected(now);
        }
    }

    void close(ViewId view)
    {
        requestClose(view);
    }

    private void requestClose(ViewId view)
    {
        for (Listener listener : List.copyOf(listeners))
        {
            listener.closeRequested(view);
        }
    }

    private JComponent header(ViewId view, TabLook look)
    {
        if (upright())
        {
            final Upright name = new Upright(look);
            final MouseAdapter gestures = tabGestures(view, look);
            name.addMouseListener(gestures);
            name.addMouseMotionListener(gestures);
            return name;
        }
        final Header panel = new Header(look.movable());

        final JLabel icon = look.icon() == null ? null : new JLabel(look.icon());
        if (icon != null)
        {
            panel.add(icon);
        }
        final JLabel title = new JLabel(look.title());
        panel.follow(title);
        panel.add(title);

        if (look.closable())
        {
            panel.add(closeButton(view, look));
        }

        final MouseAdapter gestures = tabGestures(view, look);
        final JComponent[] grabbable = icon == null
                ? new JComponent[]{panel, title}
                : new JComponent[]{panel, title, icon};
        for (JComponent target : grabbable)
        {
            target.addMouseListener(gestures);
            target.addMouseMotionListener(gestures);
        }
        return panel;
    }

    private JButton closeButton(ViewId view, TabLook look)
    {
        final JButton button = new JButton(Icons.getIcon("close", UIConstants.FONT_SIZE_SMALL));
        button.setToolTipText("Close " + look.title());
        button.setPreferredSize(new Dimension(UIConstants.ICON_SIZE_SMALL, UIConstants.ICON_SIZE_SMALL));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusable(false);
        ThemeStyles.addFillHoverEffect(button);
        button.addActionListener(event -> requestClose(view));
        return button;
    }

    private MouseAdapter tabGestures(ViewId view, TabLook look)
    {
        return new MouseAdapter()
        {
            private Point pressed;
            private boolean wasInFront;
            private boolean dragging;

            @Override
            public void mousePressed(MouseEvent event)
            {
                if (event.isPopupTrigger())
                {
                    menu(event);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(event))
                {
                    pressed = null;
                    return;
                }
                pressed = event.getPoint();
                dragging = false;
                wasInFront = selected().map(view::equals).orElse(false);
                select(view);
            }

            @Override
            public void mouseDragged(MouseEvent event)
            {
                if (pressed == null || dragOut == null || !look.movable())
                {
                    return;
                }
                if (!dragging)
                {
                    if (Math.abs(event.getX() - pressed.x) < DRAG_THRESHOLD
                            && Math.abs(event.getY() - pressed.y) < DRAG_THRESHOLD)
                    {
                        return;
                    }
                    dragging = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    dragOut.began(view);
                }
                dragOut.movedTo(view, new Point(event.getXOnScreen(), event.getYOnScreen()), event.isControlDown());
            }

            @Override
            public void mouseReleased(MouseEvent event)
            {
                if (event.isPopupTrigger())
                {
                    menu(event);
                    return;
                }
                final boolean wasPressed = pressed != null;
                pressed = null;
                if (dragging)
                {
                    dragging = false;
                    setCursor(Cursor.getDefaultCursor());
                    dragOut.ended(view, new Point(event.getXOnScreen(), event.getYOnScreen()), event.isControlDown());
                    return;
                }
                if (wasPressed)
                {
                    for (Listener listener : List.copyOf(listeners))
                    {
                        listener.headerPressed(view, wasInFront);
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent event)
            {
                if (look.closable() && SwingUtilities.isMiddleMouseButton(event))
                {
                    requestClose(view);
                }
            }

            private void menu(MouseEvent event)
            {
                for (Listener listener : List.copyOf(listeners))
                {
                    listener.menuRequested(view, event);
                }
            }
        };
    }

    int dropIndexAt(Point inStrip)
    {
        final Point inTabs = SwingUtilities.convertPoint(this, inStrip, tabs);
        int index = 0;
        for (int at = 0; at < tabs.getTabCount(); at++)
        {
            final Rectangle bounds = tabs.getBoundsAt(at);
            if (bounds == null)
            {
                continue;
            }
            final boolean past = upright()
                    ? inTabs.y >= bounds.y + bounds.height / 2
                    : inTabs.x >= bounds.x + bounds.width / 2;
            if (past)
            {
                index = at + 1;
            }
        }
        return Math.max(index, firstMovable());
    }

    Optional<Rectangle> caretAt(int index)
    {
        if (tabs.getTabCount() == 0)
        {
            return Optional.empty();
        }
        final int at = Math.max(0, Math.min(index, tabs.getTabCount()));
        final boolean past = at == tabs.getTabCount();
        final Rectangle bounds = tabs.getBoundsAt(past ? at - 1 : at);
        if (bounds == null)
        {
            return Optional.empty();
        }
        final Rectangle caret = upright()
                ? new Rectangle(bounds.x, (past ? bounds.y + bounds.height : bounds.y) - 1, bounds.width, 2)
                : new Rectangle((past ? bounds.x + bounds.width : bounds.x) - 1, bounds.y, 2, bounds.height);
        caret.translate(tabs.getX(), tabs.getY());
        return Optional.of(caret);
    }

    Optional<BufferedImage> ghostOf(ViewId view)
    {
        final JComponent body = bodies.get(view);
        final int at = body == null ? -1 : tabs.indexOfComponent(body);
        final Component header = at < 0 ? null : tabs.getTabComponentAt(at);
        if (header == null || header.getWidth() <= 0 || header.getHeight() <= 0)
        {
            return Optional.empty();
        }
        final BufferedImage image = new BufferedImage(header.getWidth(), header.getHeight(), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        header.paint(graphics);
        graphics.dispose();
        return Optional.of(image);
    }

    boolean inTheTabRow(Point inStrip)
    {
        if (tabs.getTabCount() == 0)
        {
            return false;
        }
        final Rectangle first = tabs.getBoundsAt(0);
        if (first == null)
        {
            return false;
        }
        final Point inTabs = SwingUtilities.convertPoint(this, inStrip, tabs);
        if (upright())
        {
            return inTabs.x >= first.x && inTabs.x < first.x + first.width
                    && inTabs.y >= 0 && inTabs.y < tabs.getHeight();
        }
        return inTabs.y >= 0 && inTabs.y < first.y + first.height
                && inTabs.x >= 0 && inTabs.x < tabs.getWidth();
    }

    void order(List<ViewId> wanted)
    {
        int at = 0;
        for (ViewId view : wanted)
        {
            final JComponent body = bodies.get(view);
            final int from = body == null ? -1 : tabs.indexOfComponent(body);
            if (from < 0)
            {
                continue;
            }
            if (from != at)
            {
                moveTab(from, at);
            }
            at++;
        }
    }

    private void moveTab(int from, int to)
    {
        final Component body = tabs.getComponentAt(from);
        final Component showing = tabs.getSelectedComponent();
        final String title = tabs.getTitleAt(from);
        final String tip = tabs.getToolTipTextAt(from);
        final Component header = tabs.getTabComponentAt(from);
        tabs.removeTabAt(from);
        tabs.insertTab(title, null, body, tip, to);
        tabs.setTabComponentAt(to, header);
        if (showing != null && tabs.indexOfComponent(showing) >= 0)
        {
            tabs.setSelectedComponent(showing);
        }
    }

    private int firstMovable()
    {
        int at = 0;
        while (at < tabs.getTabCount() && !movable(at))
        {
            at++;
        }
        return at;
    }

    private boolean movable(int at)
    {
        final Component header = tabs.getTabComponentAt(at);
        if (header instanceof Upright)
        {
            return ((Upright) header).movable;
        }
        return header instanceof Header && ((Header) header).movable;
    }

    @Override
    protected void applyChildThemes()
    {
        styleTabbedPane();
        for (int at = 0; at < tabs.getTabCount(); at++)
        {
            final Component header = tabs.getTabComponentAt(at);
            if (header instanceof Header)
            {
                ((Header) header).applyChildThemes();
            }
        }
    }

    private void styleTabbedPane()
    {
        tabs.setBackground(JStudioTheme.getBgSecondary());
        tabs.setForeground(JStudioTheme.getTextPrimary());
        tabs.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
    }

    private static final class Upright extends ThemedJPanel
    {

        private static final long serialVersionUID = 1L;

        private final boolean movable;
        private final String title;

        Upright(TabLook look)
        {
            super(BackgroundStyle.SECONDARY);
            this.movable = look.movable();
            this.title = look.title();
            setOpaque(false);
            setToolTipText(look.tooltip() == null ? look.title() : look.tooltip());
        }

        @Override
        public Dimension getPreferredSize()
        {
            final int length = getFontMetrics(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE))
                    .stringWidth(title) + 18;
            return new Dimension(ACROSS, length);
        }

        @Override
        public Dimension getMaximumSize()
        {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            final Graphics2D canvas = (Graphics2D) graphics.create();
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE).deriveFont(Font.PLAIN));
            canvas.setColor(JStudioTheme.getTextPrimary());

            final AffineTransform straight = canvas.getTransform();
            canvas.translate(getWidth() - 7, getHeight() - 9);
            canvas.rotate(-Math.PI / 2);
            canvas.drawString(title, 0, 0);
            canvas.setTransform(straight);
            canvas.dispose();
        }
    }

    private static final class Header extends ThemedJPanel
    {

        private static final long serialVersionUID = 1L;

        private final List<JLabel> following = new ArrayList<>(2);

        private final boolean movable;

        Header(boolean movable)
        {
            super(BackgroundStyle.SECONDARY, new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
            this.movable = movable;
            setOpaque(false);
        }

        void follow(JLabel label)
        {
            following.add(label);
            style(label);
        }

        @Override
        protected void applyChildThemes()
        {
            setOpaque(false);
            following.forEach(Header::style);
        }

        private static void style(JLabel label)
        {
            label.setForeground(JStudioTheme.getTextPrimary());
            label.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        }
    }
}
