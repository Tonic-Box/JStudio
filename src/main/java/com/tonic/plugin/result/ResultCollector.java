package com.tonic.plugin.result;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ResultCollector {

    private final List<Finding> findings = new CopyOnWriteArrayList<>();
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private final List<Consumer<Finding>> listeners = new CopyOnWriteArrayList<>();
    private final String pluginId;

    public ResultCollector() {
        this.pluginId = null;
    }

    public ResultCollector(String pluginId) {
        this.pluginId = pluginId;
    }

    public void add(Finding finding) {
        if (pluginId != null && finding.getPluginId() == null) {
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
        for (Consumer<Finding> listener : listeners) {
            listener.accept(finding);
        }
    }

    public void add(Severity severity, String message, Location location) {
        add(Finding.builder()
            .severity(severity)
            .message(message)
            .location(location)
            .pluginId(pluginId)
            .build());
    }

    public void info(String message, Location location) {
        add(Severity.INFO, message, location);
    }

    public void low(String message, Location location) {
        add(Severity.LOW, message, location);
    }

    public void medium(String message, Location location) {
        add(Severity.MEDIUM, message, location);
    }

    public void high(String message, Location location) {
        add(Severity.HIGH, message, location);
    }

    public void critical(String message, Location location) {
        add(Severity.CRITICAL, message, location);
    }

    public List<Finding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    public List<Finding> getFindings(Predicate<Finding> filter) {
        return findings.stream().filter(filter).collect(Collectors.toList());
    }

    public List<Finding> getBySeverity(Severity severity) {
        return getFindings(f -> f.getSeverity() == severity);
    }

    public List<Finding> getByCategory(String category) {
        return getFindings(f -> category.equals(f.getCategory()));
    }

    public List<Finding> getByClass(String className) {
        return getFindings(f -> f.getLocation() != null &&
            className.equals(f.getLocation().getClassName()));
    }

    public List<Finding> getAboveSeverity(Severity threshold) {
        return getFindings(f -> f.getSeverity().isAtLeast(threshold));
    }

    public int count() {
        return findings.size();
    }

    public int count(Severity severity) {
        return (int) findings.stream().filter(f -> f.getSeverity() == severity).count();
    }

    public Map<Severity, Long> countBySeverity() {
        return findings.stream()
            .collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));
    }

    public Map<String, Long> countByCategory() {
        return findings.stream()
            .collect(Collectors.groupingBy(Finding::getCategory, Collectors.counting()));
    }

    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    public boolean hasCritical() {
        return findings.stream().anyMatch(f -> f.getSeverity() == Severity.CRITICAL);
    }

    public boolean hasHigh() {
        return findings.stream().anyMatch(f -> f.getSeverity().isAtLeast(Severity.HIGH));
    }

    public void clear() {
        findings.clear();
    }

    public void setData(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public Map<String, Object> getAllData() {
        return Collections.unmodifiableMap(data);
    }

    public void addListener(Consumer<Finding> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Finding> listener) {
        listeners.remove(listener);
    }

    public void merge(ResultCollector other) {
        findings.addAll(other.findings);
        data.putAll(other.data);
    }

    public ResultSummary getSummary() {
        Map<Severity, Long> severityCounts = countBySeverity();
        return new ResultSummary(
            findings.size(),
            severityCounts.getOrDefault(Severity.CRITICAL, 0L).intValue(),
            severityCounts.getOrDefault(Severity.HIGH, 0L).intValue(),
            severityCounts.getOrDefault(Severity.MEDIUM, 0L).intValue(),
            severityCounts.getOrDefault(Severity.LOW, 0L).intValue(),
            severityCounts.getOrDefault(Severity.INFO, 0L).intValue()
        );
    }

    public static final class ResultSummary {
        private final int total;
        private final int critical;
        private final int high;
        private final int medium;
        private final int low;
        private final int info;

        public ResultSummary(int total, int critical, int high, int medium, int low, int info) {
            this.total = total;
            this.critical = critical;
            this.high = high;
            this.medium = medium;
            this.low = low;
            this.info = info;
        }

        public int getTotal() { return total; }
        public int getCritical() { return critical; }
        public int getHigh() { return high; }
        public int getMedium() { return medium; }
        public int getLow() { return low; }
        public int getInfo() { return info; }

        @Override
        public String toString() {
            return String.format(
                "Total: %d (Critical: %d, High: %d, Medium: %d, Low: %d, Info: %d)",
                total, critical, high, medium, low, info
            );
        }
    }
}
