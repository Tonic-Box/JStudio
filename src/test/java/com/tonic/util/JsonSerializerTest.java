package com.tonic.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonic.model.Bookmark;
import com.tonic.model.Comment;
import com.tonic.model.ProjectDatabase;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("project databases survive a save and load without losing text or failing on older files")
class JsonSerializerTest
{

    @TempDir
    Path dir;

    @Test
    @DisplayName("non-Latin text and control characters round-trip as UTF-8")
    void textRoundTrips() throws IOException
    {
        ProjectDatabase db = new ProjectDatabase();
        db.getComments().addComment(new Comment("com/foo/Bar", 3, "注释 comment\u0001with a control character"));
        File file = dir.resolve("p.jstudio").toFile();

        JsonSerializer.save(db, file);
        ProjectDatabase read = JsonSerializer.load(file);

        assertEquals("注释 comment\u0001with a control character", read.getComments().getAllComments().get(0).getText());
        assertEquals(-1, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).indexOf('\u0001'));
    }

    @Test
    @DisplayName("missing numbers take their defaults and entries without a class are dropped")
    void olderFilesLoad() throws IOException
    {
        String json = "{\"version\": \"1.0\", \"comments\": [{\"class\": \"a/B\", \"text\": \"kept\"}, {\"text\": \"no class\"}],"
                + " \"bookmarks\": [{\"class\": \"a/B\", \"name\": \"mark\"}, {\"name\": \"no class\"}]}";
        File file = dir.resolve("old.jstudio").toFile();
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));

        ProjectDatabase read = JsonSerializer.load(file);

        assertEquals(1, read.getComments().getAllComments().size());
        assertEquals(-1, read.getComments().getAllComments().get(0).getLineNumber());
        assertEquals(1, read.getBookmarks().getAll().size());
        assertEquals(Bookmark.NO_SLOT, read.getBookmarks().getAll().get(0).getSlot());
    }
}
