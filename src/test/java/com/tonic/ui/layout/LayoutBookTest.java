package com.tonic.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tonic.ui.layout.Arrangement.Divided;
import com.tonic.ui.layout.Arrangement.Strip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LayoutBookTest
{

    private static Arrangement roundTrip(Arrangement arrangement)
    {
        return LayoutBook.read(LayoutBook.write(arrangement));
    }

    @Test
    @DisplayName("an arrangement written and read back is the same one")
    void roundTrips()
    {
        Arrangement arranged = Presets.shipped()
                .withDropped(Stacks.NAVIGATOR, Stacks.BOTTOM, Edge.BOTTOM)
                .withCollapsed(Stacks.NAVIGATOR, true)
                .withStrips(List.of(Strip.BAND), List.of(Strip.TOOLBAR));

        Arrangement back = roundTrip(arranged);
        assertEquals(arranged, back, "what came back is not what went in");
    }

    @Test
    @DisplayName("every preset survives being written down")
    void everyPresetRoundTrips()
    {
        assertEquals(Presets.shipped(), roundTrip(Presets.shipped()));
    }

    @Test
    @DisplayName("a file that will not parse costs the arrangement and nothing else")
    void unreadableFile(@TempDir Path directory) throws IOException
    {
        Path file = directory.resolve("layout.json");
        Files.writeString(file, "{ this is not json");
        assertEquals(Presets.shipped(), LayoutBook.load(file));

        Files.writeString(file, "");
        assertEquals(Presets.shipped(), LayoutBook.load(file));
    }

    @Test
    @DisplayName("no file at all is the arrangement that ships")
    void noFile(@TempDir Path directory)
    {
        assertEquals(Presets.shipped(), LayoutBook.load(directory.resolve("nothing.json")));
    }

    @Test
    @DisplayName("it writes what it can be read back from")
    void savesAndLoads(@TempDir Path directory)
    {
        Path file = directory.resolve("layout.json");
        Arrangement arranged = Presets.shipped().withCollapsed(Stacks.NAVIGATOR, true);

        LayoutBook.save(file, arranged);
        assertTrue(Files.exists(file));
        assertEquals(arranged, LayoutBook.load(file));
    }

    @Test
    @DisplayName("a file from the build before stacks held views reads as stacks")
    void theOlderShape()
    {
        String text = "{\"version\":1,"
                + "\"working\":{\"horizontal\":true,\"weight\":0.25,"
                + "\"first\":{\"pane\":\"navigator\"},"
                + "\"second\":{\"horizontal\":false,\"weight\":0.7,"
                + "\"first\":{\"pane\":\"editor\"},\"second\":{\"pane\":\"dock\"}}},"
                + "\"above\":[\"toolbar\",\"band\"],\"below\":[],\"collapsed\":[\"dock\"]}";
        Arrangement read = LayoutBook.read(JsonParser.parseString(text).getAsJsonObject());

        assertTrue(read.isSound());
        assertEquals(List.of(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Stacks.BOTTOM), read.shown());
        assertEquals(List.of(Views.WELCOME), read.viewsIn(Stacks.DOCUMENTS));
        assertTrue(read.isCollapsed(Stacks.BOTTOM), "and what was put away still is");
        assertEquals(List.of(Strip.TOOLBAR, Strip.BAND), read.above());
    }

    @Test
    @DisplayName("a file naming the tools leaves the place they fill, not a pane")
    void theToolsAreNotWrittenDown()
    {
        String text = "{\"version\":1,"
                + "\"working\":{\"horizontal\":true,\"weight\":0.8,"
                + "\"first\":{\"pane\":\"editor\"},\"second\":{\"pane\":\"tools\"}},"
                + "\"above\":[],\"below\":[],\"collapsed\":[]}";
        Arrangement read = LayoutBook.read(JsonParser.parseString(text).getAsJsonObject());

        assertEquals(List.of(Stacks.DOCUMENTS, Stacks.TOOLS), read.shown());
        assertEquals(List.of(), read.viewsIn(Stacks.TOOLS), "a file cannot say what is on it");
        assertTrue(read.isSound());
    }

    @Test
    @DisplayName("a stack this build no longer has costs that stack, not the layout")
    void anUnknownStack()
    {
        JsonObject written = LayoutBook.write(Presets.shipped());
        String text = written.toString().replace("\"navigator\"", "\"loupe\"");

        Arrangement read = LayoutBook.read(JsonParser.parseString(text).getAsJsonObject());
        assertFalse(read.shows(Stacks.NAVIGATOR), "the unknown one is gone");
        assertEquals(3, read.shown().size(), "the others are still arranged");
        assertTrue(read.isSound());
    }

    @Test
    @DisplayName("a file naming a stack twice does not draw it twice")
    void aStackNamedTwice()
    {
        String text = "{\"version\":2,"
                + "\"working\":{\"horizontal\":true,\"weight\":0.3,"
                + "\"first\":{\"stack\":\"navigator\"},"
                + "\"second\":{\"horizontal\":true,\"weight\":0.5,"
                + "\"first\":{\"stack\":\"navigator\"},\"second\":{\"stack\":\"editor\"}}},"
                + "\"above\":[\"toolbar\",\"toolbar\"],\"below\":[],\"collapsed\":[]}";
        Arrangement read = LayoutBook.read(JsonParser.parseString(text).getAsJsonObject());

        assertEquals(1, read.above().size(), "one toolbar, however many the file named");
        assertTrue(read.isSound(), "a stack was drawn twice");
        assertEquals(1, read.shown().stream().filter(Stacks.NAVIGATOR::equals).count());
        assertTrue(read.shows(Stacks.DOCUMENTS), "and the rest of the file still stands");
    }

    @Test
    @DisplayName("a tree with nothing left in it falls back to what ships")
    void everythingUnknown(@TempDir Path directory) throws IOException
    {
        Path file = directory.resolve("layout.json");
        Files.writeString(file, "{\"version\":2,\"working\":{\"stack\":\"loupe\"},\"above\":[],\"below\":[],\"collapsed\":[]}");

        assertTrue(LayoutBook.read(JsonParser.parseString(Files.readString(file)).getAsJsonObject()).shown().isEmpty(), "nothing in the file was understood");
        assertEquals(Presets.shipped(), LayoutBook.load(file), "so what ships is used instead");
    }

    @Test
    @DisplayName("a divider dragged off the edge is brought back on")
    void weightsAreKeptInsideThePane()
    {
        String text = "{\"version\":2,"
                + "\"working\":{\"horizontal\":true,\"weight\":0.0,"
                + "\"first\":{\"stack\":\"navigator\"},\"second\":{\"stack\":\"editor\"}},"
                + "\"above\":[],\"below\":[],\"collapsed\":[]}";
        Arrangement read = LayoutBook.read(JsonParser.parseString(text).getAsJsonObject());
        Divided divided = (Divided) read.working().orElseThrow();
        assertTrue(divided.weight() >= 0.1 && divided.weight() <= 0.9, "the divider is off the edge at " + divided.weight());
    }

    @Test
    @DisplayName("a stack said to be put away that is not there is ignored")
    void collapsedButAbsent()
    {
        String text = "{\"version\":2,\"working\":{\"stack\":\"editor\"},"
                + "\"above\":[],\"below\":[],\"collapsed\":[\"navigator\",\"editor\"]}";
        Arrangement read = LayoutBook.read(JsonParser.parseString(text).getAsJsonObject());
        assertTrue(read.isCollapsed(Stacks.DOCUMENTS));
        assertFalse(read.isCollapsed(Stacks.NAVIGATOR));
    }
}
