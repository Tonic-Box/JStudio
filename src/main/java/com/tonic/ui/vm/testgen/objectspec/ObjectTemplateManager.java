package com.tonic.ui.vm.testgen.objectspec;

import java.util.*;
import java.util.stream.Collectors;

public class ObjectTemplateManager {

    private static final ObjectTemplateManager INSTANCE = new ObjectTemplateManager();

    private final Map<String, ObjectTemplate> templates = new LinkedHashMap<>();
    private final List<TemplateChangeListener> listeners = new ArrayList<>();

    private ObjectTemplateManager() {
    }

    public static ObjectTemplateManager getInstance() {
        return INSTANCE;
    }

    public void saveTemplate(ObjectTemplate template) {
        if (template.getName() == null || template.getName().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        templates.put(template.getName(), template);
        notifyListeners();
    }

    public ObjectTemplate getTemplate(String name) {
        return templates.get(name);
    }

    public void deleteTemplate(String name) {
        templates.remove(name);
        notifyListeners();
    }

    public boolean hasTemplate(String name) {
        return templates.containsKey(name);
    }

    public List<ObjectTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    public List<ObjectTemplate> getTemplatesForType(String typeName) {
        return templates.values().stream()
            .filter(t -> typeName.equals(t.getTypeName()))
            .collect(Collectors.toList());
    }

    public List<String> getTemplateNames() {
        return new ArrayList<>(templates.keySet());
    }

    public List<String> getTemplateNamesForType(String typeName) {
        return templates.values().stream()
            .filter(t -> typeName.equals(t.getTypeName()))
            .map(ObjectTemplate::getName)
            .collect(Collectors.toList());
    }

    public int getTemplateCount() {
        return templates.size();
    }

    public void clear() {
        templates.clear();
        notifyListeners();
    }

    public void addListener(TemplateChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TemplateChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (TemplateChangeListener listener : listeners) {
            listener.onTemplatesChanged();
        }
    }

    public ObjectSpec resolveSpec(ObjectSpec spec) {
        if (spec == null) return null;

        if (spec.getMode() == ConstructionMode.TEMPLATE) {
            ObjectTemplate template = getTemplate(spec.getTemplateName());
            if (template != null && template.getSpec() != null) {
                return template.getSpec().copy();
            }
        }
        return spec;
    }

    public interface TemplateChangeListener {
        void onTemplatesChanged();
    }
}
