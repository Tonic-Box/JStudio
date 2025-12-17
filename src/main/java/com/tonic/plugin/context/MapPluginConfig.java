package com.tonic.plugin.context;

import com.tonic.plugin.api.PluginConfig;

import java.util.*;

public class MapPluginConfig implements PluginConfig {

    private final Map<String, String> properties;

    public MapPluginConfig() {
        this.properties = new HashMap<>();
    }

    public MapPluginConfig(Map<String, String> properties) {
        this.properties = new HashMap<>(properties);
    }

    public void put(String key, String value) {
        properties.put(key, value);
    }

    public void putAll(Map<String, String> values) {
        properties.putAll(values);
    }

    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    @Override
    public String getString(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    @Override
    public Optional<Integer> getInt(String key) {
        String value = properties.get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        String value = properties.get(key);
        if (value == null) return Optional.empty();
        return Optional.of(Boolean.parseBoolean(value));
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key).orElse(defaultValue);
    }

    @Override
    public Optional<Double> getDouble(String key) {
        String value = properties.get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        return getDouble(key).orElse(defaultValue);
    }

    @Override
    public List<String> getStringList(String key) {
        String value = properties.get(key);
        if (value == null || value.isEmpty()) return Collections.emptyList();
        return Arrays.asList(value.split(","));
    }

    @Override
    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public boolean has(String key) {
        return properties.containsKey(key);
    }
}
