package com.tonic.ui.theme;

import com.tonic.ui.util.Settings;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThemeManager {

    private static ThemeManager instance;

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private Theme currentTheme;
    private final List<ThemeChangeListener> listeners = new ArrayList<>();

    private ThemeManager() {
        registerBuiltInThemes();
        String savedTheme = Settings.getInstance().getTheme();
        currentTheme = themes.getOrDefault(savedTheme, themes.get("jstudio-dark"));
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    private void registerBuiltInThemes() {
        for (Theme theme : ThemeLoader.loadAllThemes()) {
            registerTheme(theme);
        }
    }

    public void registerTheme(Theme theme) {
        themes.put(theme.getName(), theme);
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setTheme(String themeName) {
        Theme newTheme = themes.get(themeName);
        if (newTheme != null && newTheme != currentTheme) {
            currentTheme = newTheme;
            Settings.getInstance().setTheme(themeName);
            applyTheme();
            notifyListeners();
        }
    }

    public List<Theme> getAvailableThemes() {
        return new ArrayList<>(themes.values());
    }

    public void applyTheme() {
        Theme theme = currentTheme;

        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 4);

        UIManager.put("Panel.background", theme.getBgPrimary());
        UIManager.put("SplitPane.background", theme.getBgPrimary());
        UIManager.put("TabbedPane.background", theme.getBgSecondary());

        UIManager.put("TextField.background", theme.getBgTertiary());
        UIManager.put("TextArea.background", theme.getBgTertiary());
        UIManager.put("EditorPane.background", theme.getBgTertiary());
        UIManager.put("TextPane.background", theme.getBgTertiary());
        UIManager.put("FormattedTextField.background", theme.getBgTertiary());
        UIManager.put("PasswordField.background", theme.getBgTertiary());

        UIManager.put("TextField.selectionBackground", theme.getSelection());
        UIManager.put("TextArea.selectionBackground", theme.getSelection());
        UIManager.put("List.selectionBackground", theme.getSelection());
        UIManager.put("Tree.selectionBackground", theme.getSelection());
        UIManager.put("Table.selectionBackground", theme.getSelection());
        UIManager.put("ComboBox.selectionBackground", theme.getSelection());

        UIManager.put("Label.foreground", theme.getTextPrimary());
        UIManager.put("TextField.foreground", theme.getTextPrimary());
        UIManager.put("TextArea.foreground", theme.getTextPrimary());
        UIManager.put("Tree.foreground", theme.getTextPrimary());
        UIManager.put("List.foreground", theme.getTextPrimary());
        UIManager.put("Table.foreground", theme.getTextPrimary());
        UIManager.put("ComboBox.foreground", theme.getTextPrimary());
        UIManager.put("TabbedPane.foreground", theme.getTextPrimary());

        UIManager.put("TextField.caretForeground", theme.getTextPrimary());
        UIManager.put("TextArea.caretForeground", theme.getTextPrimary());
        UIManager.put("EditorPane.caretForeground", theme.getTextPrimary());
        UIManager.put("TextPane.caretForeground", theme.getTextPrimary());

        UIManager.put("Component.borderColor", theme.getBorder());
        UIManager.put("Component.focusedBorderColor", theme.getBorderFocus());
        UIManager.put("TitledBorder.titleColor", theme.getTextSecondary());

        UIManager.put("Button.background", theme.getBgSecondary());
        UIManager.put("Button.foreground", theme.getTextPrimary());
        UIManager.put("Button.hoverBackground", theme.getHover());
        UIManager.put("Button.pressedBackground", theme.getSelection());
        UIManager.put("Button.focusedBorderColor", theme.getAccent());

        UIManager.put("Tree.background", theme.getBgSecondary());
        UIManager.put("Tree.selectionBackground", theme.getSelection());
        UIManager.put("Tree.selectionForeground", theme.getTextPrimary());
        UIManager.put("Tree.hash", theme.getBorder());

        UIManager.put("List.background", theme.getBgSecondary());
        UIManager.put("List.selectionBackground", theme.getSelection());
        UIManager.put("List.selectionForeground", theme.getTextPrimary());

        UIManager.put("Table.background", theme.getBgSecondary());
        UIManager.put("Table.alternateRowColor", theme.getBgTertiary());
        UIManager.put("TableHeader.background", theme.getBgPrimary());
        UIManager.put("TableHeader.foreground", theme.getTextSecondary());

        UIManager.put("TabbedPane.selectedBackground", theme.getBgPrimary());
        UIManager.put("TabbedPane.hoverColor", theme.getHover());
        UIManager.put("TabbedPane.underlineColor", theme.getAccent());
        UIManager.put("TabbedPane.inactiveUnderlineColor", theme.getBorder());
        UIManager.put("TabbedPane.contentAreaColor", theme.getBgPrimary());

        UIManager.put("ScrollBar.track", theme.getBgSecondary());
        UIManager.put("ScrollBar.thumb", darker(theme.getBgSurface(), 0.8f));
        UIManager.put("ScrollBar.thumbInactiveColor", darker(theme.getBgSurface(), 0.9f));
        UIManager.put("ScrollBar.width", 12);

        UIManager.put("Menu.background", theme.getBgSecondary());
        UIManager.put("Menu.foreground", theme.getTextPrimary());
        UIManager.put("MenuItem.background", theme.getBgSecondary());
        UIManager.put("MenuItem.foreground", theme.getTextPrimary());
        UIManager.put("MenuItem.selectionBackground", theme.getSelection());
        UIManager.put("MenuBar.background", theme.getBgPrimary());
        UIManager.put("PopupMenu.background", theme.getBgSecondary());
        UIManager.put("PopupMenu.borderColor", theme.getBorder());

        UIManager.put("Separator.foreground", theme.getBorder());

        UIManager.put("ToolTip.background", theme.getBgSurface());
        UIManager.put("ToolTip.foreground", theme.getTextPrimary());
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(theme.getBorder()));

        UIManager.put("ProgressBar.background", theme.getBgTertiary());
        UIManager.put("ProgressBar.foreground", theme.getAccent());
        UIManager.put("ProgressBar.selectionBackground", theme.getTextPrimary());
        UIManager.put("ProgressBar.selectionForeground", theme.getBgTertiary());

        UIManager.put("ComboBox.background", theme.getBgTertiary());
        UIManager.put("Spinner.background", theme.getBgTertiary());

        UIManager.put("SplitPane.dividerColor", theme.getBgPrimary());
        UIManager.put("SplitPaneDivider.draggingColor", theme.getAccent());

        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.repaint();
        }
    }

    private Color darker(Color color, float factor) {
        return new Color(
                Math.max((int) (color.getRed() * factor), 0),
                Math.max((int) (color.getGreen() * factor), 0),
                Math.max((int) (color.getBlue() * factor), 0),
                color.getAlpha()
        );
    }

    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ThemeChangeListener listener : listeners) {
            listener.onThemeChanged(currentTheme);
        }
    }

    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }
}
