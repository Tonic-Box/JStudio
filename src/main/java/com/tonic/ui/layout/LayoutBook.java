package com.tonic.ui.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tonic.util.ConfigFile;
import com.tonic.util.JStudioPaths;
import com.tonic.ui.layout.Arrangement.Divided;
import com.tonic.ui.layout.Arrangement.Node;
import com.tonic.ui.layout.Arrangement.Stack;
import com.tonic.ui.layout.Arrangement.Strip;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The layout.json reader and writer, keeping shape only and dropping whatever it cannot understand. */
public final class LayoutBook
{

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int VERSION = 2;

    private LayoutBook()
    {
    }

    /**
     * Locates the layout file.
     *
     * @return the path of ~/.jstudio/layout.json
     */
    public static Path defaultPath()
    {
        return JStudioPaths.userFile("layout.json");
    }

    /**
     * Reads an arrangement from a file.
     *
     * @param from the file to read
     * @return the arrangement it holds, or the shipped one where the file is missing, unreadable or empty of stacks
     */
    public static Arrangement load(Path from)
    {
        final String text = ConfigFile.read(from);
        if (text == null || text.isBlank())
        {
            return Presets.shipped();
        }
        try
        {
            final Arrangement read = read(JsonParser.parseString(text).getAsJsonObject());
            return read.shown().isEmpty() || !read.isSound() ? Presets.shipped() : read;
        }
        catch (RuntimeException unreadable)
        {
            return Presets.shipped();
        }
    }

    /**
     * Writes an arrangement to a file, ignoring any failure.
     *
     * @param to the file to replace
     * @param arrangement the arrangement to store
     */
    public static void save(Path to, Arrangement arrangement)
    {
        ConfigFile.write(to, GSON.toJson(write(arrangement)));
    }

    /**
     * Converts an arrangement to JSON.
     *
     * @param arrangement the arrangement
     * @return its JSON form
     */
    public static JsonObject write(Arrangement arrangement)
    {
        final JsonObject out = new JsonObject();
        out.addProperty("version", VERSION);
        arrangement.working().ifPresent(node -> out.add("working", writeNode(node)));
        out.add("above", keys(arrangement.above()));
        out.add("below", keys(arrangement.below()));
        final JsonArray collapsed = new JsonArray();
        for (StackId stack : arrangement.shown())
        {
            if (arrangement.isCollapsed(stack))
            {
                collapsed.add(stack.key());
            }
        }
        out.add("collapsed", collapsed);
        return out;
    }

    /**
     * Converts JSON to an arrangement, dropping whatever it cannot understand.
     *
     * @param from the JSON form
     * @return the arrangement, normalised
     */
    public static Arrangement read(JsonObject from)
    {
        final Set<StackId> seen = new LinkedHashSet<>();
        final Node working = from.has("working")
                ? readNode(from.getAsJsonObject("working"), seen) : null;
        final Set<StackId> collapsed = new LinkedHashSet<>();
        if (from.has("collapsed"))
        {
            for (var element : from.getAsJsonArray("collapsed"))
            {
                final StackId stack = new StackId(element.getAsString());
                if (seen.contains(stack))
                {
                    collapsed.add(stack);
                }
            }
        }
        return new Arrangement(Optional.ofNullable(working), strips(from, "above"), strips(from, "below"), Set.copyOf(collapsed)).normalised();
    }

    private static JsonArray keys(List<Strip> strips)
    {
        final JsonArray out = new JsonArray();
        strips.forEach(strip -> out.add(strip.key()));
        return out;
    }

    private static List<Strip> strips(JsonObject from, String name)
    {
        if (!from.has(name))
        {
            return List.of();
        }
        final List<Strip> out = new ArrayList<>();
        for (var element : from.getAsJsonArray(name))
        {
            Strip.named(element.getAsString())
                    .filter(strip -> !out.contains(strip))
                    .ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private static JsonObject writeNode(Node node)
    {
        final JsonObject out = new JsonObject();
        if (node instanceof Stack)
        {
            final Stack stack = (Stack) node;
            out.addProperty("stack", stack.id().key());
            return out;
        }
        final Divided divided = (Divided) node;
        out.addProperty("horizontal", divided.horizontal());
        out.addProperty("weight", divided.weight());
        out.add("first", writeNode(divided.first()));
        out.add("second", writeNode(divided.second()));
        return out;
    }

    private static Node readNode(JsonObject from, Set<StackId> seen)
    {
        if (from.has("stripe"))
        {
            return leaf(new StackId(from.get("stripe").getAsString()), seen);
        }
        if (from.has("stack"))
        {
            return leaf(new StackId(from.get("stack").getAsString()), seen);
        }
        if (from.has("pane"))
        {
            return leaf(new StackId(from.get("pane").getAsString()), seen);
        }
        if (from.has("tabs"))
        {
            for (var element : from.getAsJsonArray("tabs"))
            {
                final Node node = leaf(new StackId(element.getAsString()), seen);
                if (node != null)
                {
                    return node;
                }
            }
            return null;
        }
        final Node first = from.has("first")
                ? readNode(from.getAsJsonObject("first"), seen) : null;
        final Node second = from.has("second")
                ? readNode(from.getAsJsonObject("second"), seen) : null;
        if (first == null)
        {
            return second;
        }
        if (second == null)
        {
            return first;
        }
        final boolean horizontal = !from.has("horizontal") || from.get("horizontal").getAsBoolean();
        final double weight = from.has("weight") ? from.get("weight").getAsDouble() : 0.5;
        return new Divided(horizontal, withinReach(weight), first, second);
    }

    private static double withinReach(double weight)
    {
        return Math.min(0.9, Math.max(0.1, weight));
    }

    private static Node leaf(StackId id, Set<StackId> seen)
    {
        if (!seen.add(id))
        {
            return null;
        }
        final List<ViewId> views = Presets.contentsOf(id);
        if (!views.isEmpty())
        {
            return new Stack(id, views, 0);
        }
        return Stacks.isSidePanel(id) ? new Stack(id, List.of(), 0) : null;
    }
}
