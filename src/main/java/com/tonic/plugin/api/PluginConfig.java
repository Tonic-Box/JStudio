package com.tonic.plugin.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PluginConfig {

    Optional<String> getString(String key);

    String getString(String key, String defaultValue);

    Optional<Integer> getInt(String key);

    int getInt(String key, int defaultValue);

    Optional<Boolean> getBoolean(String key);

    boolean getBoolean(String key, boolean defaultValue);

    Optional<Double> getDouble(String key);

    double getDouble(String key, double defaultValue);

    List<String> getStringList(String key);

    Map<String, String> getAll();

    boolean has(String key);
}
