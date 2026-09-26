package com.tonic.plugin.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A plugin's string key-value configuration with typed reads; values that are absent or do not parse read as empty or the default. */
public interface PluginConfig
{

    /**
     * Reads a value as a string.
     *
     * @param key the configuration key
     * @return the value, or empty when the key is absent
     */
    Optional<String> getString(String key);

    /**
     * Reads a value as a string, falling back to a default.
     *
     * @param key the configuration key
     * @param defaultValue what to return when the key is absent
     * @return the value, or defaultValue when the key is absent
     */
    String getString(String key, String defaultValue);

    /**
     * Reads a value as an integer.
     *
     * @param key the configuration key
     * @return the parsed value, or empty when the key is absent or not an integer
     */
    Optional<Integer> getInt(String key);

    /**
     * Reads a value as an integer, falling back to a default.
     *
     * @param key the configuration key
     * @param defaultValue what to return when the key is absent or not an integer
     * @return the parsed value, or defaultValue
     */
    int getInt(String key, int defaultValue);

    /**
     * Reads a value as a boolean; any text other than "true", ignoring case, reads as false.
     *
     * @param key the configuration key
     * @return the parsed value, or empty when the key is absent
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * Reads a value as a boolean, falling back to a default only when the key is absent.
     *
     * @param key the configuration key
     * @param defaultValue what to return when the key is absent
     * @return the parsed value, or defaultValue
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Reads a value as a double.
     *
     * @param key the configuration key
     * @return the parsed value, or empty when the key is absent or not a number
     */
    Optional<Double> getDouble(String key);

    /**
     * Reads a value as a double, falling back to a default.
     *
     * @param key the configuration key
     * @param defaultValue what to return when the key is absent or not a number
     * @return the parsed value, or defaultValue
     */
    double getDouble(String key, double defaultValue);

    /**
     * Reads a comma-separated value as a list; items are not trimmed.
     *
     * @param key the configuration key
     * @return the items, or an empty list when the key is absent or its value is empty
     */
    List<String> getStringList(String key);

    /**
     * Returns every configured key and value.
     *
     * @return a read-only view of the configuration
     */
    Map<String, String> getAll();

    /**
     * Reports whether a key is set.
     *
     * @param key the configuration key
     * @return true when the key is present, whatever its value
     */
    boolean has(String key);
}
