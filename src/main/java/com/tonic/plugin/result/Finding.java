package com.tonic.plugin.result;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Finding {

    private final String id;
    private final Severity severity;
    private final String category;
    private final String title;
    private final String message;
    private final Location location;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final String pluginId;

    private Finding(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.severity = builder.severity != null ? builder.severity : Severity.INFO;
        this.category = builder.category != null ? builder.category : "general";
        this.title = builder.title;
        this.message = builder.message;
        this.location = builder.location;
        this.metadata = builder.metadata != null ?
            Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : Collections.emptyMap();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.pluginId = builder.pluginId;
    }

    public String getId() {
        return id;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Location getLocation() {
        return location;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getPluginId() {
        return pluginId;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Finding info(String message, Location location) {
        return builder().severity(Severity.INFO).message(message).location(location).build();
    }

    public static Finding warning(String message, Location location) {
        return builder().severity(Severity.MEDIUM).message(message).location(location).build();
    }

    public static Finding error(String message, Location location) {
        return builder().severity(Severity.HIGH).message(message).location(location).build();
    }

    public static Finding critical(String message, Location location) {
        return builder().severity(Severity.CRITICAL).message(message).location(location).build();
    }

    public static class Builder {
        private String id;
        private Severity severity;
        private String category;
        private String title;
        private String message;
        private Location location;
        private Map<String, Object> metadata;
        private Instant timestamp;
        private String pluginId;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        public Finding build() {
            if (message == null || message.isEmpty()) {
                throw new IllegalStateException("Finding message is required");
            }
            return new Finding(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity.getDisplayName()).append("] ");
        if (title != null) {
            sb.append(title).append(": ");
        }
        sb.append(message);
        if (location != null) {
            sb.append(" at ").append(location);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Finding finding = (Finding) o;
        return Objects.equals(id, finding.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
