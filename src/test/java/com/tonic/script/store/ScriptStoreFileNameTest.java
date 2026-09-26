package com.tonic.script.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.script.engine.Script;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("scripts whose names differ only in punctuation are stored in separate files")
class ScriptStoreFileNameTest
{

    @TempDir
    Path dir;

    private List<Path> files() throws IOException
    {
        try (Stream<Path> listing = Files.list(dir))
        {
            return listing.sorted().collect(Collectors.toList());
        }
    }

    @Test
    @DisplayName("saving 'my script' and 'my.script' keeps both")
    void punctuationDoesNotCollide() throws IOException
    {
        ScriptStore.saveTo(dir, new Script("my script", Script.Mode.AST, "log('a');"));
        ScriptStore.saveTo(dir, new Script("my.script", Script.Mode.AST, "log('b');"));

        Set<String> names = new HashSet<>();
        for (Path file : files())
        {
            names.add(ScriptStore.loadScript(file.toFile()).getName());
        }
        assertEquals(Set.of("my script", "my.script"), names);
    }

    @Test
    @DisplayName("deleting one script leaves the other")
    void deleteRemovesOnlyItsOwnFile() throws IOException
    {
        Script first = new Script("my script", Script.Mode.AST, "log('a');");
        Script second = new Script("my_script", Script.Mode.AST, "log('b');");
        ScriptStore.saveTo(dir, first);
        ScriptStore.saveTo(dir, second);

        assertTrue(ScriptStore.deleteFrom(dir, first));

        List<Path> left = files();
        assertEquals(1, left.size());
        assertEquals("my_script", ScriptStore.loadScript(left.get(0).toFile()).getName());
    }

    @Test
    @DisplayName("a script saved under the old unhashed name moves to its new file on the next save")
    void legacyFileIsReplaced() throws IOException
    {
        Script script = new Script("legacy", Script.Mode.AST, "log('a');");
        ScriptStore.saveScript(script, dir.resolve("legacy.yabr-script").toFile());

        ScriptStore.saveTo(dir, script);

        List<Path> left = files();
        assertEquals(1, left.size());
        assertFalse(left.get(0).getFileName().toString().equals("legacy.yabr-script"));
        assertEquals("legacy", ScriptStore.loadScript(left.get(0).toFile()).getName());
    }

    @Test
    @DisplayName("an unrelated script that happens to own the old unhashed file name is not removed")
    void unrelatedLegacyFileIsKept() throws IOException
    {
        ScriptStore.saveScript(new Script("my.script", Script.Mode.AST, "log('a');"), dir.resolve("my_script.yabr-script").toFile());

        ScriptStore.saveTo(dir, new Script("my_script", Script.Mode.AST, "log('b');"));

        assertEquals(2, files().size());
    }
}
