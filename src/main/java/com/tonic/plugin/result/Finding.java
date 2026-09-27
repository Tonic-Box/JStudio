package com.tonic.plugin.result;

import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One immutable plugin finding: severity, category, title, message, location, metadata, time and the reporting plugin; findings are equal when their ids are. */
@Getter
public class Finding
{

    private final String id;
    private final Severity severity;
    private final String category;
    private final String title;
    private final String message;
    private final Location location;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final String pluginId;

    private Finding(Builder builder)
    {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.severity = builder.severity != null ? builder.severity : Severity.INFO;
        this.category = builder.category != null ? builder.category : "general";
        this.title = builder.title;
        this.message = builder.message;
        this.location = builder.location;
        this.metadata = builder.metadata != null ?
                Map.copyOf(builder.metadata) : Collections.emptyMap();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.pluginId = builder.pluginId;
    }

    /**
     * Reads a metadata value of an expected type.
     *
     * @param <T> the expected type
     * @param key the metadata key
     * @param type the type the value must have
     * @return the value, or null when it is absent or of another type
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type)
    {
        Object value = metadata.get(key);
        if (type.isInstance(value))
        {
            return (T) value;
        }
        return null;
    }

    /**
     * Starts a builder; unset parts default to a random id, INFO, the "general" category, the build time and no metadata.
     *
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Creates an INFO finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     * @return the finding
     */
    public static Finding info(String message, Location location)
    {
        return builder().severity(Severity.INFO).message(message).location(location).build();
    }

    /**
     * Creates a MEDIUM finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     * @return the finding
     */
    public static Finding warning(String message, Location location)
    {
        return builder().severity(Severity.MEDIUM).message(message).location(location).build();
    }

    /**
     * Creates a HIGH finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     * @return the finding
     */
    public static Finding error(String message, Location location)
    {
        return builder().severity(Severity.HIGH).message(message).location(location).build();
    }

    /**
     * Creates a CRITICAL finding.
     *
     * @param message what was found, required and non-empty
     * @param location where, or null
     * @return the finding
     */
    public static Finding critical(String message, Location location)
    {
        return builder().severity(Severity.CRITICAL).message(message).location(location).build();
    }

    /** The builder for Finding; only the message is required. */
    public static class Builder
    {
        private String id;
        private Severity severity;
        private String category;
        private String title;
        private String message;
        private Location location;
        private Map<String, Object> metadata;
        private Instant timestamp;
        private String pluginId;

        /**
         * Sets the id.
         *
         * @param id the id, or null for a random one
         * @return this builder
         */
        public Builder id(String id)
        {
            this.id = id;
            return this;
        }

        /**
         * Sets the severity.
         *
         * @param severity the severity, or null for INFO
         * @return this builder
         */
        public Builder severity(Severity severity)
        {
            this.severity = severity;
            return this;
        }

        /**
         * Sets the category.
         *
         * @param category the category, or null for "general"
         * @return this builder
         */
        public Builder category(String category)
        {
            this.category = category;
            return this;
        }

        /**
         * Sets a short title.
         *
         * @param title the title, or null for none
         * @return this builder
         */
        public Builder title(String title)
        {
            this.title = title;
            return this;
        }

        /**
         * Sets the message.
         *
         * @param message what was found, required and non-empty by build time
         * @return this builder
         */
        public Builder message(String message)
        {
            this.message = message;
            return this;
        }

        /**
         * Sets where the finding is.
         *
         * @param location the location, or null
         * @return this builder
         */
        public Builder location(Location location)
        {
            this.location = location;
            return this;
        }

        /**
         * Replaces the metadata with a copy of the given entries; entries with null values are dropped.
         *
         * @param metadata the entries, or null for none
         * @return this builder
         * @throws NullPointerException if a key is null
         */
        public Builder metadata(Map<String, Object> metadata)
        {
            this.metadata = null;
            if (metadata != null)
            {
                for (Map.Entry<String, Object> entry : metadata.entrySet())
                {
                    addMetadata(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /**
         * Adds one metadata entry, or removes the key when the value is null.
         *
         * @param key the entry's key
         * @param value the entry's value, or null to leave the key out
         * @return this builder
         * @throws NullPointerException if the key is null
         */
        public Builder addMetadata(String key, Object value)
        {
            Objects.requireNonNull(key, "metadata key");
            if (value == null)
            {
                if (this.metadata != null)
                {
                    this.metadata.remove(key);
                }
                return this;
            }
            if (this.metadata == null)
            {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets when the finding was made.
         *
         * @param timestamp the time, or null for the build time
         * @return this builder
         */
        public Builder timestamp(Instant timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the reporting plugin.
         *
         * @param pluginId the plugin's identifier, or null to let a ResultCollector fill it in
         * @return this builder
         */
        public Builder pluginId(String pluginId)
        {
            this.pluginId = pluginId;
            return this;
        }

        /**
         * Builds the finding, copying the metadata.
         *
         * @return the finding
         * @throws IllegalStateException if the message is null or empty
         */
        public Finding build()
        {
            if (message == null || message.isEmpty())
            {
                throw new IllegalStateException("Finding message is required");
            }
            return new Finding(this);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity.getDisplayName()).append("] ");
        if (title != null)
        {
            sb.append(title).append(": ");
        }
        sb.append(message);
        if (location != null)
        {
            sb.append(" at ").append(location);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Finding finding = (Finding) o;
        return Objects.equals(id, finding.id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }
}
