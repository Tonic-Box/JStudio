package com.tonic.ui.layout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The window's layout as an immutable tree of view stacks; every operation returns a new arrangement. */
public final class Arrangement
{

    private final Optional<Node> working;
    private final List<Strip> above;
    private final List<Strip> below;
    private final Set<StackId> collapsed;

    /**
     * Creates an arrangement.
     *
     * @param working the tree of stacks, or empty where every stack is hidden
     * @param above the strips over the working area, from the top
     * @param below the strips under the working area, from the top
     * @param collapsed the stacks put away against their edge
     */
    public Arrangement(Optional<Node> working, List<Strip> above, List<Strip> below, Set<StackId> collapsed)
    {
        this.working = working;
        this.above = above;
        this.below = below;
        this.collapsed = collapsed;
    }

    /** @return the tree of stacks, or empty where every stack is hidden */
    public Optional<Node> working()
    {
        return working;
    }

    /** @return the strips over the working area, from the top */
    public List<Strip> above()
    {
        return above;
    }

    /** @return the strips under the working area, from the top */
    public List<Strip> below()
    {
        return below;
    }

    /** @return the stacks put away against their edge */
    public Set<StackId> collapsed()
    {
        return collapsed;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof Arrangement))
        {
            return false;
        }
        final Arrangement that = (Arrangement) other;
        return working.equals(that.working) && above.equals(that.above)
                && below.equals(that.below) && collapsed.equals(that.collapsed);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(working, above, below, collapsed);
    }

    /** A strip spanning the window over or under the working area; its key is written to layout.json and never changes. */
    public enum Strip
    {
        TOOLBAR("toolbar", "Toolbar"),
        BAND("band", "Image map band");

        private final String key;
        private final String title;

        Strip(String key, String title)
        {
            this.key = key;
            this.title = title;
        }

        /** @return the name written to the layout file */
        public String key()
        {
            return key;
        }

        /** @return what the strip is called while a reader moves it */
        public String title()
        {
            return title;
        }

        /**
         * Finds the strip a key names.
         *
         * @param key the key from the layout file
         * @return the strip, or empty where no strip has that key
         */
        public static Optional<Strip> named(String key)
        {
            for (Strip strip : values())
            {
                if (strip.key.equals(key))
                {
                    return Optional.of(strip);
                }
            }
            return Optional.empty();
        }
    }

    /** One node of the tree: a stack, or two nodes with a divider between them. */
    public interface Node
    {

        /**
         * Lists every stack under this node.
         *
         * @return the stacks in layout order
         */
        default List<StackId> stacks()
        {
            List<StackId> out = new ArrayList<>();
            collectStacks(out);
            return List.copyOf(out);
        }

        /**
         * Lists every view under this node.
         *
         * @return the views in layout order
         */
        default List<ViewId> views()
        {
            List<ViewId> out = new ArrayList<>();
            collectViews(out);
            return List.copyOf(out);
        }

        /**
         * Adds every stack under this node to a list, in layout order.
         *
         * @param into the list to add to
         */
        void collectStacks(List<StackId> into);

        /**
         * Adds every view under this node to a list, in layout order.
         *
         * @param into the list to add to
         */
        void collectViews(List<ViewId> into);
    }

    /** Several views in one place, one showing at a time. */
    public static final class Stack implements Node
    {

        private final StackId id;
        private final List<ViewId> views;
        private final int selected;

        /**
         * Creates a stack.
         *
         * @param id the stack's identity
         * @param views its views in tab order
         * @param selected the index of the view in front
         */
        public Stack(StackId id, List<ViewId> views, int selected)
        {
            this.id = id;
            this.views = List.copyOf(views);
            this.selected = selected;
        }

        /** @return the stack's identity */
        public StackId id()
        {
            return id;
        }

        /** @return its views in tab order */
        public List<ViewId> views()
        {
            return views;
        }

        /** @return the index of the view in front */
        public int selected()
        {
            return selected;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof Stack))
            {
                return false;
            }
            final Stack that = (Stack) other;
            return selected == that.selected && id.equals(that.id) && views.equals(that.views);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(id, views, selected);
        }

        @Override
        public String toString()
        {
            return "Stack[" + id + " " + views + " @" + selected + "]";
        }

        /**
         * Creates a stack holding one view.
         *
         * @param id the stack's identity
         * @param view the view it holds
         * @return the stack
         */
        public static Stack of(StackId id, ViewId view)
        {
            return new Stack(id, List.of(view), 0);
        }

        /**
         * Finds the view in front.
         *
         * @return the view in front, or empty where the stack holds nothing
         */
        public Optional<ViewId> showing()
        {
            if (views.isEmpty())
            {
                return Optional.empty();
            }
            return Optional.of(views.get(Math.max(0, Math.min(selected, views.size() - 1))));
        }

        @Override
        public void collectStacks(List<StackId> into)
        {
            into.add(id);
        }

        @Override
        public void collectViews(List<ViewId> into)
        {
            into.addAll(views);
        }
    }

    /** Two nodes with a divider between them, sized by a fraction so a layout survives a different screen. */
    public static final class Divided implements Node
    {

        private final boolean horizontal;
        private final double weight;
        private final Node first;
        private final Node second;

        /**
         * Creates a divided node.
         *
         * @param horizontal true where the divider separates left from right
         * @param weight the share of the space the first node takes, from 0 to 1
         * @param first the left or top node
         * @param second the right or bottom node
         */
        public Divided(boolean horizontal, double weight, Node first, Node second)
        {
            this.horizontal = horizontal;
            this.weight = weight;
            this.first = first;
            this.second = second;
        }

        /** @return true where the divider separates left from right */
        public boolean horizontal()
        {
            return horizontal;
        }

        /** @return the share of the space the first node takes, from 0 to 1 */
        public double weight()
        {
            return weight;
        }

        /** @return the left or top node */
        public Node first()
        {
            return first;
        }

        /** @return the right or bottom node */
        public Node second()
        {
            return second;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof Divided))
            {
                return false;
            }
            final Divided that = (Divided) other;
            return horizontal == that.horizontal
                    && Double.compare(weight, that.weight) == 0
                    && first.equals(that.first) && second.equals(that.second);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(horizontal, weight, first, second);
        }

        @Override
        public String toString()
        {
            return "Divided[" + (horizontal ? "h" : "v") + " " + weight + "]";
        }

        @Override
        public void collectStacks(List<StackId> into)
        {
            first.collectStacks(into);
            second.collectStacks(into);
        }

        @Override
        public void collectViews(List<ViewId> into)
        {
            first.collectViews(into);
            second.collectViews(into);
        }
    }

    /**
     * Creates an arrangement with nothing in it.
     *
     * @return the empty arrangement
     */
    public static Arrangement empty()
    {
        return new Arrangement(Optional.empty(), List.of(), List.of(), Set.of());
    }

    /**
     * Lists every stack on the window.
     *
     * @return the stacks in layout order
     */
    public List<StackId> shown()
    {
        return working.map(Node::stacks).orElse(List.of());
    }

    /**
     * Lists every view on the window.
     *
     * @return the views in layout order
     */
    public List<ViewId> views()
    {
        return working.map(Node::views).orElse(List.of());
    }

    /**
     * Lists the shipped stacks that are not on the window.
     *
     * @return the hidden shipped stacks
     */
    public Set<StackId> hidden()
    {
        Set<StackId> out = new LinkedHashSet<>(Stacks.shipped());
        shown().forEach(out::remove);
        return Set.copyOf(out);
    }

    /**
     * Tells whether a stack is on the window.
     *
     * @param stack the stack
     * @return true where it is in the tree
     */
    public boolean shows(StackId stack)
    {
        return shown().contains(stack);
    }

    /**
     * Tells whether a view is on the window.
     *
     * @param view the view
     * @return true where some stack holds it
     */
    public boolean showsView(ViewId view)
    {
        return views().contains(view);
    }

    /**
     * Tells whether a stack is put away against its edge.
     *
     * @param stack the stack
     * @return true where it is collapsed
     */
    public boolean isCollapsed(StackId stack)
    {
        return collapsed.contains(stack);
    }

    /**
     * Finds the stack holding a view.
     *
     * @param view the view
     * @return its stack, or empty where it is not on the window
     */
    public Optional<StackId> stackOf(ViewId view)
    {
        return leaves().stream()
                .filter(node -> node instanceof Stack && ((Stack) node).views().contains(view))
                .map(node -> ((Stack) node).id())
                .findFirst();
    }

    /**
     * Lists the views in a stack.
     *
     * @param stack the stack
     * @return its views in tab order, or an empty list where it is not on the window
     */
    public List<ViewId> viewsIn(StackId stack)
    {
        return leaves().stream()
                .filter(node -> node instanceof Stack && ((Stack) node).id().equals(stack))
                .map(node -> ((Stack) node).views())
                .findFirst()
                .orElse(List.of());
    }

    /**
     * Finds a stack anywhere in the tree.
     *
     * @param id the stack's identity
     * @return the stack, or empty where it is not on the window
     */
    public Optional<Stack> stack(StackId id)
    {
        return leaves().stream()
                .filter(node -> node instanceof Stack && ((Stack) node).id().equals(id))
                .map(Stack.class::cast)
                .findFirst();
    }

    /**
     * Lists the leaves of the tree.
     *
     * @return the leaves in layout order
     */
    public List<Node> leaves()
    {
        List<Node> out = new ArrayList<>();
        working.ifPresent(node -> collectLeaves(node, out));
        return List.copyOf(out);
    }

    /**
     * Moves a stack beside another, or into its tabs for CENTRE; dropping a stack on itself changes nothing.
     *
     * @param moved the stack being moved
     * @param onto the stack it is dropped on
     * @param edge which side of the target it lands on
     * @return the new arrangement, or this one where the drop means nothing
     */
    public Arrangement withDropped(StackId moved, StackId onto, Edge edge)
    {
        if (moved.equals(onto) || working.isEmpty())
        {
            return this;
        }
        if (edge == Edge.CENTRE)
        {
            return withStacked(moved, onto);
        }
        final Node taken = nodeOf(moved);
        final Arrangement without = stripped(moved);
        if (taken == null || without.working.isEmpty() || !without.shows(onto))
        {
            return this;
        }
        final Node inserted = replaceLeaf(without.working.get(), onto, target -> beside(taken, target, edge, weightFor(moved)));
        return new Arrangement(Optional.of(inserted), above, below, collapsed).normalised();
    }

    /**
     * Moves one stack's views to the end of another's tabs; the moved stack stops existing and the target keeps its identity.
     *
     * @param moved the stack being moved
     * @param onto the stack that takes its views
     * @return the new arrangement, or this one where the drop means nothing
     */
    public Arrangement withStacked(StackId moved, StackId onto)
    {
        if (moved.equals(onto) || working.isEmpty())
        {
            return this;
        }
        final Node taken = nodeOf(moved);
        final Stack from = taken instanceof Stack ? (Stack) taken : null;
        if (from == null)
        {
            return withDropped(moved, onto, Stacks.homeEdge(moved));
        }
        final Arrangement without = stripped(moved);
        if (without.working.isEmpty() || !without.shows(onto))
        {
            return this;
        }
        final Node merged = replaceLeaf(without.working.get(), onto, target ->
        {
            final Stack into = target instanceof Stack ? (Stack) target : null;
            if (into == null)
            {
                return target;
            }
            List<ViewId> views = new ArrayList<>(into.views());
            views.addAll(from.views());
            return new Stack(into.id(), List.copyOf(views), into.views().size());
        });
        return new Arrangement(Optional.of(merged), above, below, collapsed).normalised();
    }

    /**
     * Takes a stack off the window.
     *
     * @param stack the stack to remove
     * @return the new arrangement
     */
    public Arrangement without(StackId stack)
    {
        return stripped(stack).normalised();
    }

    /**
     * Puts a hidden stack back against the window edge it ships on, taking its views from wherever they are now.
     *
     * @param stack the stack to show
     * @param views the views it holds
     * @return the new arrangement, or this one where the stack is already shown
     */
    public Arrangement withShown(StackId stack, List<ViewId> views)
    {
        if (shows(stack))
        {
            return this;
        }
        Arrangement without = this;
        for (ViewId view : views)
        {
            without = without.withoutViewKeepingStacks(view);
        }
        without = without.normalised();

        if (views.isEmpty())
        {
            return without;
        }
        final Node node = new Stack(stack, views, 0);
        if (without.working.isEmpty())
        {
            return new Arrangement(Optional.of(node), above, below, without.collapsed);
        }
        return new Arrangement(Optional.of(beside(node, without.working.get(), Stacks.homeEdge(stack), Stacks.homeWeight(stack))), above, below, without.collapsed).normalised();
    }

    /**
     * Puts a stack away against its edge, or lets it out again.
     *
     * @param stack the stack
     * @param pinned true to put it away, false to let it out
     * @return the new arrangement, or this one where nothing changes
     */
    public Arrangement withCollapsed(StackId stack, boolean pinned)
    {
        if (pinned == collapsed.contains(stack))
        {
            return this;
        }
        Set<StackId> next = new LinkedHashSet<>(collapsed);
        if (pinned)
        {
            next.add(stack);
        }
        else
        {
            next.remove(stack);
        }
        return new Arrangement(working, above, below, Set.copyOf(next));
    }

    /**
     * Puts a view into a stack at a place among its tabs, taking it from wherever it was first.
     *
     * @param view the view to move
     * @param onto the stack that takes it
     * @param index where among the tabs, counted after the view is removed; anything past the end goes last
     * @return the new arrangement, or this one where the target is not on the window
     */
    public Arrangement withViewIn(ViewId view, StackId onto, int index)
    {
        if (working.isEmpty())
        {
            return this;
        }
        final Arrangement without = withoutViewKeepingStacks(view);
        if (without.working.isEmpty() || !without.shows(onto))
        {
            return this;
        }
        final Node placed = replaceLeaf(without.working.get(), onto, target ->
        {
            final Stack stack = target instanceof Stack ? (Stack) target : null;
            if (stack == null)
            {
                return target;
            }
            List<ViewId> views = new ArrayList<>(stack.views());
            final int at = Math.max(0, Math.min(index, views.size()));
            views.add(at, view);
            return new Stack(stack.id(), List.copyOf(views), at);
        });
        return new Arrangement(Optional.of(placed), above, below, collapsed).normalised();
    }

    /**
     * Drops a view against a stack's edge in a new stack of its own, splitting the target in half.
     *
     * @param view the view to move
     * @param onto the stack it is dropped against
     * @param edge which side of the target; CENTRE puts it among the target's tabs instead
     * @param made the identity of the new stack
     * @return the new arrangement, or this one where the target is not on the window
     */
    public Arrangement withViewSplit(ViewId view, StackId onto, Edge edge, StackId made)
    {
        if (working.isEmpty())
        {
            return this;
        }
        if (edge == Edge.CENTRE)
        {
            return withViewIn(view, onto, Integer.MAX_VALUE);
        }
        final Arrangement without = withoutViewKeepingStacks(view);
        if (without.working.isEmpty() || !without.shows(onto))
        {
            return this;
        }
        final Node inserted = replaceLeaf(without.working.get(), onto, target -> beside(Stack.of(made, view), target, edge, 0.5));
        return new Arrangement(Optional.of(inserted), above, below, collapsed).normalised();
    }

    /**
     * Takes a view off the window, along with any stack it leaves empty.
     *
     * @param view the view to remove
     * @return the new arrangement
     */
    public Arrangement withoutView(ViewId view)
    {
        return withoutViewKeepingStacks(view).normalised();
    }

    /**
     * Brings a different tab of a stack to the front.
     *
     * @param stack the stack
     * @param index the tab to bring forward, clamped to the tabs there are
     * @return the new arrangement, or this one where nothing changes
     */
    public Arrangement withSelected(StackId stack, int index)
    {
        if (working.isEmpty())
        {
            return this;
        }
        final Node changed = replaceLeaf(working.get(), stack, target ->
        {
            final Stack found = target instanceof Stack ? (Stack) target : null;
            if (found == null || found.views().isEmpty())
            {
                return target;
            }
            final int at = Math.max(0, Math.min(index, found.views().size() - 1));
            return at == found.selected() ? found
                    : new Stack(found.id(), found.views(), at);
        });
        return changed == working.get() ? this
                : new Arrangement(Optional.of(changed), above, below, collapsed);
    }

    /**
     * Replaces both rows of strips.
     *
     * @param newAbove the strips over the working area, from the top
     * @param newBelow the strips under the working area, from the top
     * @return the new arrangement
     */
    public Arrangement withStrips(List<Strip> newAbove, List<Strip> newBelow)
    {
        return new Arrangement(working, List.copyOf(newAbove), List.copyOf(newBelow), collapsed);
    }

    /**
     * Moves a strip to a place in one of the rows, taking it from wherever it was first.
     *
     * @param strip the strip to move
     * @param putAbove true for the row over the working area, false for the row under it
     * @param index where in the row, counted after the strip is removed; anything past the end goes last
     * @return the new arrangement
     */
    public Arrangement withStripAt(Strip strip, boolean putAbove, int index)
    {
        final List<Strip> newAbove = new ArrayList<>(above);
        final List<Strip> newBelow = new ArrayList<>(below);
        newAbove.remove(strip);
        newBelow.remove(strip);
        final List<Strip> into = putAbove ? newAbove : newBelow;
        into.add(Math.max(0, Math.min(index, into.size())), strip);
        return new Arrangement(working, List.copyOf(newAbove), List.copyOf(newBelow), collapsed);
    }

    /**
     * Lists every strip on the window.
     *
     * @return the strips above, then the strips below
     */
    public List<Strip> strips()
    {
        final List<Strip> out = new ArrayList<>(above);
        out.addAll(below);
        return List.copyOf(out);
    }

    /**
     * Replaces divider weights with measured ones, keeping a divider's weight where nothing was measured.
     *
     * @param measured the measured weight of each divided node, or null for none
     * @return the new arrangement
     */
    public Arrangement withWeights(Function<Node, Double> measured)
    {
        return working.map(node -> new Arrangement(Optional.of(reweigh(node, measured)), above, below, collapsed)).orElse(this);
    }

    /**
     * Removes dividers with one side and stacks with nothing to draw, keeping emptied side panels.
     *
     * @return the tidied arrangement
     */
    public Arrangement normalised()
    {
        final Node tidy = working.map(Arrangement::tidy).orElse(null);
        Set<StackId> stillThere = new LinkedHashSet<>();
        if (tidy != null)
        {
            tidy.stacks().stream().filter(collapsed::contains).forEach(stillThere::add);
        }
        return new Arrangement(Optional.ofNullable(tidy), above, below, Set.copyOf(stillThere));
    }

    /**
     * Tells whether the arrangement can be drawn: no stack and no view appears twice.
     *
     * @return true where it is sound
     */
    public boolean isSound()
    {
        final List<StackId> stacks = shown();
        final List<ViewId> views = views();
        return new LinkedHashSet<>(stacks).size() == stacks.size()
                && new LinkedHashSet<>(views).size() == views.size();
    }

    private Arrangement stripped(StackId stack)
    {
        final Node left = working.map(node -> removeStack(node, stack)).orElse(null);
        return new Arrangement(Optional.ofNullable(left), above, below, minus(collapsed, stack));
    }

    private Arrangement withoutViewKeepingStacks(ViewId view)
    {
        final Node left = working.map(node -> removeView(node, view)).orElse(null);
        return new Arrangement(Optional.ofNullable(left), above, below, collapsed);
    }

    private Node nodeOf(StackId stack)
    {
        return leaves().stream()
                .filter(node -> node.stacks().contains(stack))
                .findFirst()
                .orElse(null);
    }

    private static double weightFor(StackId stack)
    {
        return Stacks.homeWeight(stack);
    }

    private static Node beside(Node moved, Node target, Edge edge, double weight)
    {
        final double share = edge.first() ? weight : 1 - weight;
        return edge.first()
                ? new Divided(edge.horizontal(), share, moved, target)
                : new Divided(edge.horizontal(), share, target, moved);
    }

    private static void collectLeaves(Node node, List<Node> into)
    {
        if (node instanceof Divided)
        {
            final Divided divided = (Divided) node;
            collectLeaves(divided.first(), into);
            collectLeaves(divided.second(), into);
            return;
        }
        into.add(node);
    }

    private static Node replaceLeaf(Node node, StackId stack, UnaryOperator<Node> change)
    {
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return node.stacks().contains(stack) ? change.apply(node) : node;
        }
        final Node first = replaceLeaf(divided.first(), stack, change);
        final Node second = replaceLeaf(divided.second(), stack, change);
        return first == divided.first() && second == divided.second()
                ? divided
                : new Divided(divided.horizontal(), divided.weight(), first, second);
    }

    private static Node removeStack(Node node, StackId stack)
    {
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return node.stacks().contains(stack) ? null : node;
        }
        return withSides(divided, removeStack(divided.first(), stack), removeStack(divided.second(), stack));
    }

    private static Node removeView(Node node, ViewId view)
    {
        if (node instanceof Stack)
        {
            final Stack stack = (Stack) node;
            if (!stack.views().contains(view))
            {
                return stack;
            }
            final List<ViewId> left = new ArrayList<>(stack.views());
            final int was = left.indexOf(view);
            left.remove(view);
            if (left.isEmpty())
            {
                return new Stack(stack.id(), List.of(), 0);
            }
            return new Stack(stack.id(), List.copyOf(left), Math.max(0, Math.min(stack.selected(), was >= left.size() ? left.size() - 1 : was)));
        }
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return node;
        }
        return withSides(divided, removeView(divided.first(), view), removeView(divided.second(), view));
    }

    private static Node withSides(Divided divided, Node first, Node second)
    {
        if (first == null)
        {
            return second;
        }
        if (second == null)
        {
            return first;
        }
        return first == divided.first() && second == divided.second()
                ? divided
                : new Divided(divided.horizontal(), divided.weight(), first, second);
    }

    private static Node tidy(Node node)
    {
        if (node instanceof Stack)
        {
            final Stack stack = (Stack) node;
            if (stack.views().isEmpty())
            {
                return Stacks.isSidePanel(stack.id()) ? stack : null;
            }
            final int selected = Math.max(0, Math.min(stack.selected(), stack.views().size() - 1));
            return selected == stack.selected() ? stack
                    : new Stack(stack.id(), stack.views(), selected);
        }
        if (node instanceof Divided)
        {
            final Divided divided = (Divided) node;
            final Node first = tidy(divided.first());
            final Node second = tidy(divided.second());
            if (first == null)
            {
                return second;
            }
            if (second == null)
            {
                return first;
            }
            return first == divided.first() && second == divided.second()
                    ? divided
                    : new Divided(divided.horizontal(), divided.weight(), first, second);
        }
        return node;
    }

    private static Node reweigh(Node node, Function<Node, Double> measured)
    {
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return node;
        }
        final Node first = reweigh(divided.first(), measured);
        final Node second = reweigh(divided.second(), measured);
        final Double now = measured.apply(divided);
        final double weight = now == null ? divided.weight() : now;
        return new Divided(divided.horizontal(), weight, first, second);
    }

    private static Set<StackId> minus(Set<StackId> from, StackId stack)
    {
        Set<StackId> out = new LinkedHashSet<>(from);
        out.remove(stack);
        return Set.copyOf(out);
    }
}
