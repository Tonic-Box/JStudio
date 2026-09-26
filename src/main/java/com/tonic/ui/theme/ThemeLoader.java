package com.tonic.ui.theme;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tonic.service.ConsoleLogService;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the built-in themes from JSON resources under /themes/, caching each by name. */
public class ThemeLoader
{

    private static final String THEMES_PATH = "/themes/";
    private static final String[] BUILT_IN_THEMES =
            {
                    "darcula", "dracula", "monokai", "nord",
                    "github-light", "jstudio-dark", "solarized-dark", "vscode-dark"
            };

    private static final Map<String, Theme> themeCache = new HashMap<>();
    private static final Gson gson = new Gson();

    private ThemeLoader()
    {
    }

    /**
     * Loads every built-in theme, skipping any that fail to load.
     *
     * @return the themes, in built-in order
     */
    public static List<Theme> loadAllThemes()
    {
        List<Theme> themes = new ArrayList<>();
        for (String themeName : BUILT_IN_THEMES)
        {
            Theme theme = loadTheme(themeName);
            if (theme != null)
            {
                themes.add(theme);
            }
        }
        return themes;
    }

    /**
     * Loads a theme from its JSON resource, or returns the cached copy; failures are logged to the console.
     *
     * @param name the theme's key, which is also its resource file name
     * @return the theme, or null if the resource is missing or cannot be parsed
     */
    public static Theme loadTheme(String name)
    {
        if (themeCache.containsKey(name))
        {
            return themeCache.get(name);
        }

        String resourcePath = THEMES_PATH + name + ".json";
        try (InputStream is = ThemeLoader.class.getResourceAsStream(resourcePath))
        {
            if (is == null)
            {
                return null;
            }

            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            String displayName = json.has("displayName") ? json.get("displayName").getAsString() : name;
            Map<String, Color> colors = parseColors(json);

            Theme theme = new ConfigurableTheme(name, displayName, colors);
            themeCache.put(name, theme);
            return theme;
        }
        catch (Exception e)
        {
            ConsoleLogService.getInstance().error("Failed to load theme: " + name + " - " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Color> parseColors(JsonObject json)
    {
        Map<String, Color> colors = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet())
        {
            String key = entry.getKey();
            if ("name".equals(key) || "displayName".equals(key))
            {
                continue;
            }

            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
            {
                String colorStr = value.getAsString();
                Color color = parseColor(colorStr);
                if (color != null)
                {
                    colors.put(key, color);
                }
            }
        }

        return colors;
    }

    private static Color parseColor(String colorStr)
    {
        if (colorStr == null || colorStr.isEmpty())
        {
            return null;
        }

        try
        {
            if (colorStr.startsWith("#"))
            {
                colorStr = colorStr.substring(1);
            }
            else if (colorStr.startsWith("0x") || colorStr.startsWith("0X"))
            {
                colorStr = colorStr.substring(2);
            }

            int rgb = Integer.parseInt(colorStr, 16);
            return new Color(rgb);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Drops every cached theme so the next load rereads its resource. */
    public static void clearCache()
    {
        themeCache.clear();
    }
}
