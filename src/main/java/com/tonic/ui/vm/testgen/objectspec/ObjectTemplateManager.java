package com.tonic.ui.vm.testgen.objectspec;

import java.util.*;
import java.util.stream.Collectors;

/** The in-memory registry of object templates, keyed by name in insertion order, notifying listeners on every change. */
public class ObjectTemplateManager
{

    private static final ObjectTemplateManager INSTANCE = new ObjectTemplateManager();

    private final Map<String, ObjectTemplate> templates = new LinkedHashMap<>();
    private final List<TemplateChangeListener> listeners = new ArrayList<>();

    private ObjectTemplateManager()
    {
    }

    /** @return the shared manager */
    public static ObjectTemplateManager getInstance()
    {
        return INSTANCE;
    }

    /**
     * Saves a template, replacing any with the same name, and notifies listeners.
     *
     * @param template the template to save
     * @throws IllegalArgumentException if the template's name is null or empty
     */
    public void saveTemplate(ObjectTemplate template)
    {
        if (template.getName() == null || template.getName().isEmpty())
        {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        templates.put(template.getName(), template);
        notifyListeners();
    }

    /**
     * Looks up a template.
     *
     * @param name the template's name
     * @return the template, or null if there is none
     */
    public ObjectTemplate getTemplate(String name)
    {
        return templates.get(name);
    }

    /**
     * Deletes a template, if present, and notifies listeners.
     *
     * @param name the template's name
     */
    public void deleteTemplate(String name)
    {
        templates.remove(name);
        notifyListeners();
    }

    /**
     * Checks whether a template exists.
     *
     * @param name the template's name
     * @return true if one is saved under the name
     */
    public boolean hasTemplate(String name)
    {
        return templates.containsKey(name);
    }

    /**
     * Lists all templates.
     *
     * @return a copy of all templates, in insertion order
     */
    public List<ObjectTemplate> getAllTemplates()
    {
        return new ArrayList<>(templates.values());
    }

    /**
     * Lists the templates for one type.
     *
     * @param typeName the class's internal name, with slashes
     * @return the matching templates, in insertion order
     */
    public List<ObjectTemplate> getTemplatesForType(String typeName)
    {
        return templates.values().stream()
                .filter(t -> typeName.equals(t.getTypeName()))
                .collect(Collectors.toList());
    }

    /**
     * Lists all template names.
     *
     * @return a copy of all template names, in insertion order
     */
    public List<String> getTemplateNames()
    {
        return new ArrayList<>(templates.keySet());
    }

    /**
     * Lists the template names for one type.
     *
     * @param typeName the class's internal name, with slashes
     * @return the matching names, in insertion order
     */
    public List<String> getTemplateNamesForType(String typeName)
    {
        return templates.values().stream()
                .filter(t -> typeName.equals(t.getTypeName()))
                .map(ObjectTemplate::getName)
                .collect(Collectors.toList());
    }

    /**
     * Counts the saved templates.
     *
     * @return the number of saved templates
     */
    public int getTemplateCount()
    {
        return templates.size();
    }

    /** Deletes all templates and notifies listeners. */
    public void clear()
    {
        templates.clear();
        notifyListeners();
    }

    /**
     * Registers a listener for template changes.
     *
     * @param listener the listener to add
     */
    public void addListener(TemplateChangeListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Unregisters a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(TemplateChangeListener listener)
    {
        listeners.remove(listener);
    }

    private void notifyListeners()
    {
        for (TemplateChangeListener listener : listeners)
        {
            listener.onTemplatesChanged();
        }
    }

    /**
     * Replaces a template spec with a copy of the template's saved spec.
     *
     * @param spec the spec to resolve, or null
     * @return a copy of the template's spec when spec uses a template that exists, otherwise spec itself, or null if spec is null
     */
    public ObjectSpec resolveSpec(ObjectSpec spec)
    {
        if (spec == null) return null;

        if (spec.getMode() == ConstructionMode.TEMPLATE)
        {
            ObjectTemplate template = getTemplate(spec.getTemplateName());
            if (template != null && template.getSpec() != null)
            {
                return template.getSpec().copy();
            }
        }
        return spec;
    }

    /** Notified when templates are saved, deleted or cleared. */
    public interface TemplateChangeListener
    {
        /** Called after the set of templates changes. */
        void onTemplatesChanged();
    }
}
