package com.tonic.plugin.api;

import com.tonic.ui.JStudio;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/** A plugin's immutable metadata: id, name, version, description, author and free-form string metadata. */
@Getter
public class PluginInfo
{

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final Map<String, String> metadata;

    private PluginInfo(Builder builder)
    {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.author = builder.author;
        this.metadata = builder.metadata != null ?
                Collections.unmodifiableMap(builder.metadata) : Collections.emptyMap();
    }

    /**
     * Starts a builder with the JStudio version and empty description and author.
     *
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** The builder for PluginInfo; only the name is required, and the id is derived from it when not set. */
    public static class Builder
    {
        private String id;
        private String name;
        private String version = JStudio.APP_VERSION;
        private String description = "";
        private String author = "";
        private Map<String, String> metadata;

        /**
         * Sets the id the GUI uses to remember a plugin's enabled state and to name its directory.
         *
         * @param id the id; null or empty derives it from the name at build time
         * @return this builder
         */
        public Builder id(String id)
        {
            this.id = id;
            return this;
        }

        /**
         * Sets the display name.
         *
         * @param name the name, required and non-empty by build time
         * @return this builder
         */
        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        /**
         * Sets the version.
         *
         * @param version the version text
         * @return this builder
         */
        public Builder version(String version)
        {
            this.version = version;
            return this;
        }

        /**
         * Sets the description.
         *
         * @param description a short description
         * @return this builder
         */
        public Builder description(String description)
        {
            this.description = description;
            return this;
        }

        /**
         * Sets the author.
         *
         * @param author the author
         * @return this builder
         */
        public Builder author(String author)
        {
            this.author = author;
            return this;
        }

        /**
         * Sets free-form metadata; the map is wrapped read-only, not copied.
         *
         * @param metadata the entries, or null for none
         * @return this builder
         */
        public Builder metadata(Map<String, String> metadata)
        {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the info, deriving a missing id from the name by lowercasing it and replacing every character outside a to z and 0 to 9 with a dash.
         *
         * @return the info
         * @throws IllegalStateException if the name is null or empty
         */
        public PluginInfo build()
        {
            if (name == null || name.isEmpty())
            {
                throw new IllegalStateException("Plugin name is required");
            }
            if (id == null || id.isEmpty())
            {
                id = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
            }
            return new PluginInfo(this);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s v%s (%s)", name, version, id);
    }
}
