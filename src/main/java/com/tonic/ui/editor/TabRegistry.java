package com.tonic.ui.editor;

import com.tonic.ui.editor.resource.ResourceEditorTab;

import javax.swing.JComponent;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TabRegistry
{

    enum Kind
    {CLASS, RESOURCE, CUSTOM, WELCOME, NONE}

    private final Map<String, EditorTab> openTabs = new HashMap<>();
    private final Map<String, ResourceEditorTab> openResourceTabs = new HashMap<>();
    private final Map<String, JComponent> customViews = new HashMap<>();
    private final Map<String, Runnable> customViewCloseHooks = new HashMap<>();
    private Component welcomeTab;

    void setWelcomeTab(Component welcomeTab)
    {
        this.welcomeTab = welcomeTab;
    }

    EditorTab getClassTab(String className)
    {
        return openTabs.get(className);
    }

    void putClassTab(String className, EditorTab tab)
    {
        openTabs.put(className, tab);
    }

    void removeClassTab(String className)
    {
        openTabs.remove(className);
    }

    Iterable<EditorTab> classTabs()
    {
        return openTabs.values();
    }

    ResourceEditorTab getResourceTab(String path)
    {
        return openResourceTabs.get(path);
    }

    void putResourceTab(String path, ResourceEditorTab tab)
    {
        openResourceTabs.put(path, tab);
    }

    void removeResourceTab(String path)
    {
        openResourceTabs.remove(path);
    }

    JComponent getCustomView(String id)
    {
        return customViews.get(id);
    }

    void putCustomView(String id, JComponent view, Runnable onClose)
    {
        customViews.put(id, view);
        if (onClose != null)
        {
            customViewCloseHooks.put(id, onClose);
        }
    }

    Runnable removeCustomView(String id)
    {
        customViews.remove(id);
        return customViewCloseHooks.remove(id);
    }

    boolean isEmpty()
    {
        return openTabs.isEmpty() && openResourceTabs.isEmpty() && customViews.isEmpty();
    }

    boolean noClassOrResourceTabs()
    {
        return openTabs.isEmpty() && openResourceTabs.isEmpty();
    }

    boolean noClassTabs()
    {
        return openTabs.isEmpty();
    }

    List<Runnable> clearAll()
    {
        openTabs.clear();
        openResourceTabs.clear();
        List<Runnable> hooks = new ArrayList<>(customViewCloseHooks.values());
        customViewCloseHooks.clear();
        customViews.clear();
        return hooks;
    }

    List<Component> bodies()
    {
        List<Component> out = new ArrayList<>();
        out.addAll(openTabs.values());
        out.addAll(openResourceTabs.values());
        out.addAll(customViews.values());
        return out;
    }

    boolean isWelcome(Component component)
    {
        return component == welcomeTab;
    }

    String customViewId(Component content)
    {
        for (Map.Entry<String, JComponent> entry : customViews.entrySet())
        {
            if (entry.getValue() == content)
            {
                return entry.getKey();
            }
        }
        return null;
    }

    Kind classify(Component content)
    {
        if (content == null || content == welcomeTab)
        {
            return content == welcomeTab ? Kind.WELCOME : Kind.NONE;
        }
        if (content instanceof EditorTab)
        {
            return Kind.CLASS;
        }
        if (content instanceof ResourceEditorTab)
        {
            return Kind.RESOURCE;
        }
        return customViewId(content) != null ? Kind.CUSTOM : Kind.NONE;
    }
}
