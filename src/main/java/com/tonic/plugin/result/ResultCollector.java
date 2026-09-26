package com.tonic.plugin.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** A thread-safe collector of a plugin's findings and free-form result data, stamping each finding with the plugin's name and notifying listeners as findings arrive. */
public class ResultCollector
{

    private final List<Finding> findings = new CopyOnWriteArrayList<>();
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private final List<Consumer<Finding>> listeners = new CopyOnWriteArrayList<>();
    private final String pluginId;

    /** Creates a collector that leaves findings' plugin unset. */
    public ResultCollector()
    {
        this.pluginId = null;
    }

    /**
     * Creates a collector that stamps findings with a plugin identifier.
     *
     * @param pluginId stamped on findings that have none; the contexts pass the plugin's name
     */
    public ResultCollector(String pluginId)
    {
        this.pluginId = pluginId;
    }

    /**
     * Adds a finding, stamping it with this collector's plugin when it has none, and notifies the listeners on the calling thread.
     *
     * @param finding the finding to add
     */
    public void add(Finding finding)
    {
        if (pluginId != null && finding.getPluginId() == null)
        {
            finding = Finding.builder()
                    .id(finding.getId())
                    .severity(finding.getSeverity())
                    .category(finding.getCategory())
                    .title(finding.getTitle())
                    .message(finding.getMessage())
                    .location(finding.getLocation())
                    .metadata(finding.getMetadata())
                    .timestamp(finding.getTimestamp())
                    .pluginId(pluginId)
                    .build();
        }
        findings.add(finding);
        for (Consumer<Finding> listener : listeners)
        {
            listener.accept(finding);
        }
    }

    /**
     * Adds a finding built from a severity, message and location.
     *
     * @param severity how serious it is
     * @param message what was found, required and non-empty
     * @param location where, or null
     */
    public void add(Severity severity, String message, Location location)
    {
        add(Finding.builder().severity(severity).message(message).location(location).pluginId(pluginId).build());
    }

    /**
     * Adds an INFO finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     */
    public void info(String message, Location location)
    {
        add(Severity.INFO, message, location);
    }

    /**
     * Adds a LOW finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     */
    public void low(String message, Location location)
    {
        add(Severity.LOW, message, location);
    }

    /**
     * Adds a MEDIUM finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     */
    public void medium(String message, Location location)
    {
        add(Severity.MEDIUM, message, location);
    }

    /**
     * Adds a HIGH finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     */
    public void high(String message, Location location)
    {
        add(Severity.HIGH, message, location);
    }

    /**
     * Adds a CRITICAL finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     */
    public void critical(String message, Location location)
    {
        add(Severity.CRITICAL, message, location);
    }

    /**
     * Returns every finding in the order added.
     *
     * @return a read-only live view
     */
    public List<Finding> getFindings()
    {
        return Collections.unmodifiableList(findings);
    }

    /**
     * Returns the findings that pass a filter.
     *
     * @param filter which findings to keep
     * @return a new list in the order added
     */
    public List<Finding> getFindings(Predicate<Finding> filter)
    {
        return findings.stream().filter(filter).collect(Collectors.toList());
    }

    /**
     * Returns the findings of exactly one severity.
     *
     * @param severity the severity to match
     * @return a new list in the order added
     */
    public List<Finding> getBySeverity(Severity severity)
    {
        return getFindings(f -> f.getSeverity() == severity);
    }

    /**
     * Returns the findings in a category.
     *
     * @param category the category, matched exactly
     * @return a new list in the order added
     */
    public List<Finding> getByCategory(String category)
    {
        return getFindings(f -> category.equals(f.getCategory()));
    }

    /**
     * Returns the findings located in a class.
     *
     * @param className the class name, matched exactly against the location's
     * @return a new list in the order added
     */
    public List<Finding> getByClass(String className)
    {
        return getFindings(f -> f.getLocation() != null && className.equals(f.getLocation().getClassName()));
    }

    /**
     * Returns the findings at or above a severity; despite the name the threshold is included.
     *
     * @param threshold the lowest severity to keep
     * @return a new list in the order added
     */
    public List<Finding> getAboveSeverity(Severity threshold)
    {
        return getFindings(f -> f.getSeverity().isAtLeast(threshold));
    }

    /**
     * Counts the findings.
     *
     * @return the number of findings
     */
    public int count()
    {
        return findings.size();
    }

    /**
     * Counts the findings of exactly one severity.
     *
     * @param severity the severity to count
     * @return the number of matching findings
     */
    public int count(Severity severity)
    {
        return (int) findings.stream().filter(f -> f.getSeverity() == severity).count();
    }

    /**
     * Counts the findings per severity.
     *
     * @return a new map holding only the severities that occur
     */
    public Map<Severity, Long> countBySeverity()
    {
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));
    }

    /**
     * Counts the findings per category.
     *
     * @return a new map holding only the categories that occur
     */
    public Map<String, Long> countByCategory()
    {
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::getCategory, Collectors.counting()));
    }

    /**
     * Reports whether any finding was added.
     *
     * @return true when there is at least one finding
     */
    public boolean hasFindings()
    {
        return !findings.isEmpty();
    }

    /**
     * Reports whether any finding is CRITICAL.
     *
     * @return true when one is
     */
    public boolean hasCritical()
    {
        return findings.stream().anyMatch(f -> f.getSeverity() == Severity.CRITICAL);
    }

    /**
     * Reports whether any finding is HIGH or CRITICAL.
     *
     * @return true when one is
     */
    public boolean hasHigh()
    {
        return findings.stream().anyMatch(f -> f.getSeverity().isAtLeast(Severity.HIGH));
    }

    /** Removes every finding; result data is kept. */
    public void clear()
    {
        findings.clear();
    }

    /**
     * Stores a free-form result value, replacing any earlier one.
     *
     * @param key the value's name, not null
     * @param value the value, not null
     */
    public void setData(String key, Object value)
    {
        data.put(key, value);
    }

    /**
     * Reads a result value of an expected type.
     *
     * @param <T> the expected type
     * @param key the value's name
     * @param type the type the value must have
     * @return the value, or null when it is absent or of another type
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type)
    {
        Object value = data.get(key);
        if (type.isInstance(value))
        {
            return (T) value;
        }
        return null;
    }

    /**
     * Returns every result value.
     *
     * @return a read-only live view
     */
    public Map<String, Object> getAllData()
    {
        return Collections.unmodifiableMap(data);
    }

    /**
     * Registers a listener called with each finding added from now on, on the adding thread.
     *
     * @param listener called once per finding
     */
    public void addListener(Consumer<Finding> listener)
    {
        listeners.add(listener);
    }

    /**
     * Unregisters a listener; an unknown one is ignored.
     *
     * @param listener the listener to remove
     */
    public void removeListener(Consumer<Finding> listener)
    {
        listeners.remove(listener);
    }

    /**
     * Copies another collector's findings and data into this one, without restamping the findings or notifying listeners.
     *
     * @param other the collector to copy from
     */
    public void merge(ResultCollector other)
    {
        findings.addAll(other.findings);
        data.putAll(other.data);
    }

    /**
     * Counts the findings in total and per severity.
     *
     * @return a new summary
     */
    public ResultSummary getSummary()
    {
        Map<Severity, Long> severityCounts = countBySeverity();
        return new ResultSummary(findings.size(), severityCounts.getOrDefault(Severity.CRITICAL, 0L).intValue(), severityCounts.getOrDefault(Severity.HIGH, 0L).intValue(), severityCounts.getOrDefault(Severity.MEDIUM, 0L).intValue(), severityCounts.getOrDefault(Severity.LOW, 0L).intValue(), severityCounts.getOrDefault(Severity.INFO, 0L).intValue());
    }

    /** The finding counts of a collector, in total and per severity. */
    @Getter
    @AllArgsConstructor
    public static final class ResultSummary
    {
        private final int total;
        private final int critical;
        private final int high;
        private final int medium;
        private final int low;
        private final int info;

        @Override
        public String toString()
        {
            return String.format("Total: %d (Critical: %d, High: %d, Medium: %d, Low: %d, Info: %d)", total, critical, high, medium, low, info);
        }
    }
}
