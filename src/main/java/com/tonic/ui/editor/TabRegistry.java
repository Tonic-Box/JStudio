package com.tonic.ui.editor;

import com.tonic.ui.editor.resource.ResourceEditorTab;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single owner of the editor's open-tab bookkeeping: the class, resource, and custom-view maps (plus per-id custom
 * close hooks) and the pinned Welcome tab. Provides the index lookups over the tabbed pane and the
 * {@link #classify(Component)} dispatch used to route a tab body to its close path.
 */
final class TabRegistry {

    /** What kind of tab body a component is (used to route closes). */
    enum Kind { CLASS, RESOURCE, CUSTOM, WELCOME, NONE }

    private final JTabbedPane tabbedPane;
    private final Map<String, EditorTab> openTabs = new HashMap<>();
    private final Map<String, ResourceEditorTab> openResourceTabs = new HashMap<>();
    private final Map<String, JComponent> customViews = new HashMap<>();
    private final Map<String, Runnable> customViewCloseHooks = new HashMap<>();
    private Component welcomeTab;

    TabRegistry(JTabbedPane tabbedPane) {
        this.tabbedPane = tabbedPane;
    }

    void setWelcomeTab(Component welcomeTab) {
        this.welcomeTab = welcomeTab;
    }

    EditorTab getClassTab(String className) {
        return openTabs.get(className);
    }

    void putClassTab(String className, EditorTab tab) {
        openTabs.put(className, tab);
    }

    void removeClassTab(String className) {
        openTabs.remove(className);
    }

    Iterable<EditorTab> classTabs() {
        return openTabs.values();
    }

    ResourceEditorTab getResourceTab(String path) {
        return openResourceTabs.get(path);
    }

    void putResourceTab(String path, ResourceEditorTab tab) {
        openResourceTabs.put(path, tab);
    }

    void removeResourceTab(String path) {
        openResourceTabs.remove(path);
    }

    JComponent getCustomView(String id) {
        return customViews.get(id);
    }

    void putCustomView(String id, JComponent view, Runnable onClose) {
        customViews.put(id, view);
        if (onClose != null) {
            customViewCloseHooks.put(id, onClose);
        }
    }

    /** Removes the custom view {@code id} from the maps and returns its close hook (or null). */
    Runnable removeCustomView(String id) {
        customViews.remove(id);
        return customViewCloseHooks.remove(id);
    }

    /** True when only the Welcome tab remains (no class, resource, or custom tabs are open). */
    boolean isEmpty() {
        return openTabs.isEmpty() && openResourceTabs.isEmpty() && customViews.isEmpty();
    }

    boolean noClassOrResourceTabs() {
        return openTabs.isEmpty() && openResourceTabs.isEmpty();
    }

    boolean noClassTabs() {
        return openTabs.isEmpty();
    }

    /**
     * Clears all maps (class, resource, custom) and returns the custom-view close hooks that were registered, so the
     * caller can run them after the tabs have been removed.
     */
    List<Runnable> clearAll() {
        openTabs.clear();
        openResourceTabs.clear();
        List<Runnable> hooks = new ArrayList<>(customViewCloseHooks.values());
        customViewCloseHooks.clear();
        customViews.clear();
        return hooks;
    }

    int findTabIndex(EditorTab tab) {
        return findComponentIndex(tab);
    }

    int findResourceTabIndex(ResourceEditorTab tab) {
        return findComponentIndex(tab);
    }

    int findComponentIndex(Component component) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) == component) {
                return i;
            }
        }
        return -1;
    }

    boolean isWelcome(Component component) {
        return component == welcomeTab;
    }

    /** The custom-view id whose tab body is {@code content}, or null if it isn't an open custom view. */
    String customViewId(Component content) {
        for (Map.Entry<String, JComponent> entry : customViews.entrySet()) {
            if (entry.getValue() == content) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Classifies a tab body so the caller can route it to the matching close path. */
    Kind classify(Component content) {
        if (content == null || content == welcomeTab) {
            return content == welcomeTab ? Kind.WELCOME : Kind.NONE;
        }
        if (content instanceof EditorTab) {
            return Kind.CLASS;
        }
        if (content instanceof ResourceEditorTab) {
            return Kind.RESOURCE;
        }
        return customViewId(content) != null ? Kind.CUSTOM : Kind.NONE;
    }
}
