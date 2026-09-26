package com.tonic.script.store;

import com.tonic.script.engine.Script;
import com.tonic.service.ConsoleLogService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saves and loads scripts: JSON .yabr-script files and plain .js files, with a per-user scripts directory under .yabr in the home directory. */
public class ScriptStore
{

    private static final String USER_SCRIPTS_DIR = System.getProperty("user.home") +
            File.separator + ".yabr" + File.separator + "scripts";

    /**
     * Gets the user scripts directory, creating it if missing; a failure to create it is logged, not thrown.
     *
     * @return the directory path, which may not exist if creation failed
     */
    public static Path getUserScriptsDirectory()
    {
        Path dir = Paths.get(USER_SCRIPTS_DIR);
        if (!Files.exists(dir))
        {
            try
            {
                Files.createDirectories(dir);
            }
            catch (IOException e)
            {
                ConsoleLogService.getInstance().error("Failed to create scripts directory: " + e.getMessage());
            }
        }
        return dir;
    }

    /**
     * Writes a script as JSON, replacing the file.
     *
     * @param script the script to save
     * @param file the file to write
     * @throws IOException if the file cannot be written
     */
    public static void saveScript(Script script, File file) throws IOException
    {
        String json = "{\n" +
                "  \"name\": " + escapeJson(script.getName()) + ",\n" +
                "  \"description\": " + escapeJson(script.getDescription()) + ",\n" +
                "  \"mode\": " + escapeJson(script.getMode().name().toLowerCase()) + ",\n" +
                "  \"version\": " + escapeJson(script.getVersion()) + ",\n" +
                "  \"author\": " + escapeJson(script.getAuthor()) + ",\n" +
                "  \"script\": " + escapeJson(script.getContent()) + "\n" +
                "}\n";

        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads a JSON script file; missing fields take defaults and the name defaults to the file name.
     *
     * @param file the .yabr-script file
     * @return the script, marked not built in
     * @throws IOException if the file cannot be read
     */
    public static Script loadScript(File file) throws IOException
    {
        String json = Files.readString(file.toPath());

        Script script = new Script();
        script.setName(extractJsonString(json, "name", file.getName()));
        script.setDescription(extractJsonString(json, "description", ""));
        script.setMode(parseMode(extractJsonString(json, "mode", "ast")));
        script.setVersion(extractJsonString(json, "version", "1.0"));
        script.setAuthor(extractJsonString(json, "author", ""));
        script.setContent(extractJsonString(json, "script", ""));
        script.setBuiltIn(false);

        return script;
    }

    /**
     * Reads a plain .js script, taking its name and mode from header comments in the content.
     *
     * @param file the script file
     * @return the script, marked not built in
     * @throws IOException if the file cannot be read
     */
    public static Script loadPlainScript(File file) throws IOException
    {
        String content = Files.readString(file.toPath());

        Script script = new Script();
        script.setName(Script.parseNameFromContent(content));
        script.setMode(Script.parseModeFromContent(content));
        script.setContent(content);
        script.setBuiltIn(false);

        return script;
    }

    /**
     * Loads every .yabr-script and .js file in the user scripts directory, logging and skipping files that fail.
     *
     * @return the loaded scripts; empty when there are none
     */
    public static List<Script> loadUserScripts()
    {
        List<Script> scripts = new ArrayList<>();
        Path dir = getUserScriptsDirectory();

        if (!Files.exists(dir))
        {
            return scripts;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.yabr-script"))
        {
            for (Path path : stream)
            {
                try
                {
                    Script script = loadScript(path.toFile());
                    scripts.add(script);
                }
                catch (IOException e)
                {
                    ConsoleLogService.getInstance().error("Failed to load script: " + path + " - " + e.getMessage());
                }
            }
        }
        catch (IOException e)
        {
            ConsoleLogService.getInstance().error("Failed to list scripts directory: " + e.getMessage());
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.js"))
        {
            for (Path path : stream)
            {
                try
                {
                    Script script = loadPlainScript(path.toFile());
                    scripts.add(script);
                }
                catch (IOException e)
                {
                    ConsoleLogService.getInstance().error("Failed to load script: " + path + " - " + e.getMessage());
                }
            }
        }
        catch (IOException e)
        {
        }

        return scripts;
    }

    /**
     * Saves a script into the user scripts directory under a file named after the script plus a hash of its exact name, so names differing only in punctuation get separate files; an older file of the same script under the unhashed name is removed.
     *
     * @param script the script to save
     * @throws IOException if the file cannot be written
     */
    public static void saveToUserDirectory(Script script) throws IOException
    {
        saveTo(getUserScriptsDirectory(), script);
    }

    static void saveTo(Path dir, Script script) throws IOException
    {
        saveScript(script, fileFor(dir, script.getName()).toFile());
        Path legacy = legacyFileFor(dir, script.getName());
        if (holds(legacy, script.getName()))
        {
            Files.delete(legacy);
        }
    }

    /**
     * Deletes a script's file from the user scripts directory, including an older file of the same script under the unhashed name; a failure is logged.
     *
     * @param script the script whose file to delete
     * @return true if a file was deleted, false if none existed or deletion failed
     */
    public static boolean deleteFromUserDirectory(Script script)
    {
        return deleteFrom(getUserScriptsDirectory(), script);
    }

    static boolean deleteFrom(Path dir, Script script)
    {
        try
        {
            boolean deleted = Files.deleteIfExists(fileFor(dir, script.getName()));
            Path legacy = legacyFileFor(dir, script.getName());
            if (holds(legacy, script.getName()))
            {
                Files.delete(legacy);
                deleted = true;
            }
            return deleted;
        }
        catch (IOException e)
        {
            ConsoleLogService.getInstance().error("Failed to delete script: " + e.getMessage());
            return false;
        }
    }

    private static Path fileFor(Path dir, String name)
    {
        return dir.resolve(safeName(name) + "-" + String.format("%08x", name.hashCode()) + ".yabr-script");
    }

    private static Path legacyFileFor(Path dir, String name)
    {
        return dir.resolve(safeName(name) + ".yabr-script");
    }

    private static String safeName(String name)
    {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static boolean holds(Path file, String name)
    {
        if (!Files.isRegularFile(file))
        {
            return false;
        }
        try
        {
            return name.equals(loadScript(file.toFile()).getName());
        }
        catch (IOException unreadable)
        {
            return false;
        }
    }

    private static Script.Mode parseMode(String mode)
    {
        if (mode == null) return Script.Mode.AST;
        switch (mode.toLowerCase())
        {
            case "ir":
                return Script.Mode.IR;
            case "both":
                return Script.Mode.BOTH;
            default:
                return Script.Mode.AST;
        }
    }

    private static String escapeJson(String s)
    {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray())
        {
            switch (c)
            {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32)
                    {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String extractJsonString(String json, String key, String defaultValue)
    {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find())
        {
            return unescapeJson(matcher.group(1));
        }
        return defaultValue;
    }

    private static String unescapeJson(String s)
    {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length())
        {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length())
            {
                char next = s.charAt(i + 1);
                switch (next)
                {
                    case '"':
                        sb.append('"');
                        i += 2;
                        continue;
                    case '\\':
                        sb.append('\\');
                        i += 2;
                        continue;
                    case 'n':
                        sb.append('\n');
                        i += 2;
                        continue;
                    case 'r':
                        sb.append('\r');
                        i += 2;
                        continue;
                    case 't':
                        sb.append('\t');
                        i += 2;
                        continue;
                    case 'u':
                        if (i + 5 < s.length())
                        {
                            String hex = s.substring(i + 2, i + 6);
                            try
                            {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                                continue;
                            }
                            catch (NumberFormatException e)
                            {
                            }
                        }
                        break;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
