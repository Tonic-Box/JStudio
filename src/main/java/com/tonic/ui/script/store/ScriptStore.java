package com.tonic.ui.script.store;

import com.tonic.ui.script.engine.Script;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles persistence of JStudio scripts.
 * Scripts are stored as JSON files with .yabr-script extension.
 */
public class ScriptStore {

    private static final String USER_SCRIPTS_DIR = System.getProperty("user.home") +
        File.separator + ".yabr" + File.separator + "scripts";

    /**
     * Gets the user scripts directory, creating it if necessary.
     */
    public static Path getUserScriptsDirectory() {
        Path dir = Paths.get(USER_SCRIPTS_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                System.err.println("Failed to create scripts directory: " + e.getMessage());
            }
        }
        return dir;
    }

    /**
     * Saves a script to a file.
     */
    public static void saveScript(Script script, File file) throws IOException {
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
     * Loads a script from a file.
     */
    public static Script loadScript(File file) throws IOException {
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

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
     * Loads a script from plain text (for .js files).
     */
    public static Script loadPlainScript(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        Script script = new Script();
        script.setName(Script.parseNameFromContent(content));
        script.setMode(Script.parseModeFromContent(content));
        script.setContent(content);
        script.setBuiltIn(false);

        return script;
    }

    /**
     * Loads all user scripts from the scripts directory.
     */
    public static List<Script> loadUserScripts() {
        List<Script> scripts = new ArrayList<>();
        Path dir = getUserScriptsDirectory();

        if (!Files.exists(dir)) {
            return scripts;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.yabr-script")) {
            for (Path path : stream) {
                try {
                    Script script = loadScript(path.toFile());
                    scripts.add(script);
                } catch (IOException e) {
                    System.err.println("Failed to load script: " + path + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to list scripts directory: " + e.getMessage());
        }

        // Also load .js files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.js")) {
            for (Path path : stream) {
                try {
                    Script script = loadPlainScript(path.toFile());
                    scripts.add(script);
                } catch (IOException e) {
                    System.err.println("Failed to load script: " + path + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        return scripts;
    }

    /**
     * Saves a script to the user scripts directory.
     */
    public static void saveToUserDirectory(Script script) throws IOException {
        Path dir = getUserScriptsDirectory();
        String safeName = script.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        Path file = dir.resolve(safeName + ".yabr-script");
        saveScript(script, file.toFile());
    }

    /**
     * Deletes a script from the user scripts directory.
     */
    public static boolean deleteFromUserDirectory(Script script) {
        Path dir = getUserScriptsDirectory();
        String safeName = script.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        Path file = dir.resolve(safeName + ".yabr-script");

        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("Failed to delete script: " + e.getMessage());
            return false;
        }
    }

    private static Script.Mode parseMode(String mode) {
        if (mode == null) return Script.Mode.AST;
        switch (mode.toLowerCase()) {
            case "ir": return Script.Mode.IR;
            case "both": return Script.Mode.BOTH;
            default: return Script.Mode.AST;
        }
    }

    /**
     * Escapes a string for JSON output.
     */
    private static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Extracts a JSON string value by key.
     */
    private static String extractJsonString(String json, String key, String defaultValue) {
        // Pattern: "key": "value" (handling escaped quotes)
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return defaultValue;
    }

    /**
     * Unescapes a JSON string value.
     */
    private static String unescapeJson(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case 'n': sb.append('\n'); i += 2; continue;
                    case 'r': sb.append('\r'); i += 2; continue;
                    case 't': sb.append('\t'); i += 2; continue;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                                continue;
                            } catch (NumberFormatException e) {
                                // Fall through
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
