package com.tonic.plugin.api;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public class PluginInfo {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final Map<String, String> metadata;

    private PluginInfo(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.author = builder.author;
        this.metadata = builder.metadata != null ?
            Collections.unmodifiableMap(builder.metadata) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String version = "1.0.0";
        private String description = "";
        private String author = "";
        private Map<String, String> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public PluginInfo build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Plugin name is required");
            }
            if (id == null || id.isEmpty()) {
                id = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
            }
            return new PluginInfo(this);
        }
    }

    @Override
    public String toString() {
        return String.format("%s v%s (%s)", name, version, id);
    }
}
