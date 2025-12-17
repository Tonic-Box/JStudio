package com.tonic.plugin.api;

public interface Plugin {

    PluginInfo getInfo();

    default void init(PluginContext context) {
    }

    void execute();

    default void dispose() {
    }
}
