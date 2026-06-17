package com.tonic.plugin.gui;

import com.tonic.event.Event;
import com.tonic.event.EventBus;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.PluginContext;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.api.PluginLogger;
import com.tonic.plugin.api.ui.JStudioHost;
import com.tonic.plugin.api.ui.Registration;
import com.tonic.plugin.api.ui.UiApi;
import com.tonic.service.ProjectService;
import com.tonic.ui.MainFrame;

import javax.swing.JFrame;
import java.util.List;
import java.util.function.Consumer;

/**
 * The {@link JStudioHost} handed to one plugin. Backed by the main window and the plugin's own
 * LiveGuiPluginContext; every event subscription and tracked cleanup is appended to the plugin's
 * contribution list so the manager can undo them on unload.
 */
final class JStudioHostImpl implements JStudioHost {

    private final MainFrame frame;
    private final PluginInfo info;
    private final PluginContext context;
    private final List<Registration> contributions;
    private final UiApiImpl ui;

    JStudioHostImpl(MainFrame frame, PluginInfo info, PluginContext context, List<Registration> contributions) {
        this.frame = frame;
        this.info = info;
        this.context = context;
        this.contributions = contributions;
        this.ui = new UiApiImpl(frame, contributions);
    }

    @Override
    public PluginContext context() {
        return context;
    }

    @Override
    public UiApi ui() {
        return ui;
    }

    @Override
    public EventBus events() {
        return EventBus.getInstance();
    }

    @Override
    public JFrame frame() {
        return frame;
    }

    @Override
    public ProjectModel currentProject() {
        return ProjectService.getInstance().getCurrentProject();
    }

    @Override
    public PluginInfo info() {
        return info;
    }

    @Override
    public PluginLogger log() {
        return context.getLogger();
    }

    @Override
    public <T extends Event> void onEvent(Class<T> type, Consumer<T> handler) {
        EventBus.EventHandler<T> wrapper = handler::accept;
        EventBus.getInstance().register(type, wrapper);
        contributions.add(() -> EventBus.getInstance().unregister(type, wrapper));
    }

    @Override
    public void track(Registration cleanup) {
        contributions.add(cleanup);
    }
}
