package com.tonic.sample;

import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.annotations.JStudioPlugin;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.api.ui.JStudioHost;
import com.tonic.plugin.api.ui.NavigatorAction;
import com.tonic.plugin.api.ui.UiPlugin;
import com.tonic.service.ProjectService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.util.Collections;

/**
 * Reference plugin that exercises every UI extension point plus direct app/EventBus access. Dropped into
 * {@code ~/.jstudio/plugins/} (via the {@code copyToPluginsDir} Gradle task) to validate the plugin runtime.
 */
@JStudioPlugin(
        id = "sample-plugin",
        name = "Sample Plugin",
        version = "1.0",
        description = "Demonstrates tool windows, center views, bottom tabs, menus, toolbar, and navigator actions.",
        author = "JStudio"
)
public class SamplePlugin implements UiPlugin {

    private JLabel selectionLabel;

    @Override
    public PluginInfo getInfo() {
        return PluginInfo.builder()
                .id("sample-plugin")
                .name("Sample Plugin")
                .version("1.0")
                .description("Reference GUI plugin")
                .author("JStudio")
                .build();
    }

    @Override
    public void start(JStudioHost host) {
        // 1. A right-dock tool window with a live label and a couple of buttons.
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        selectionLabel = new JLabel("No class selected yet.");
        panel.add(selectionLabel);

        JButton countButton = new JButton("Show class count");
        countButton.addActionListener(e -> {
            ProjectModel project = host.currentProject();
            int count = project != null ? project.getAllClasses().size() : 0;
            host.ui().setStatus("Sample Plugin: project has " + count + " classes");
        });
        panel.add(countButton);

        JButton viewButton = new JButton("Open center view");
        viewButton.addActionListener(e -> openCenterView(host));
        panel.add(viewButton);

        JButton bottomButton = new JButton("Open bottom tab");
        bottomButton.addActionListener(e -> openBottomTab(host));
        panel.add(bottomButton);

        host.ui().addToolWindow("Sample", panel);

        // 2. A menu item (creates a "Sample" top-level menu) and a toolbar button.
        host.ui().addMenuItem("Sample", "Say Hello", () ->
                JOptionPane.showMessageDialog(host.frame(), "Hello from the Sample Plugin!"));
        host.ui().addToolbarButton(null, "Sample Plugin: open center view", () -> openCenterView(host));

        // 3. A navigator right-click action available when a class is selected.
        host.ui().addNavigatorAction(context -> context.selectedClass()
                .map(cls -> Collections.singletonList(new NavigatorAction(
                        "Sample: log class name",
                        () -> host.log().info("Selected class: " + cls.getClassName()))))
                .orElse(Collections.emptyList()));

        // 4. React to navigation (auto-unregistered on unload).
        host.onEvent(ClassSelectedEvent.class, event -> {
            if (event.getClassEntry() != null) {
                selectionLabel.setText("Selected: " + event.getClassEntry().getClassName());
            }
        });

        // 5. Direct singleton access works too (no host API needed).
        host.log().info("Sample Plugin started. Current project: "
                + (ProjectService.getInstance().getCurrentProject() != null ? "loaded" : "none"));

        // 6. Hand the host a cleanup the registrations can't express.
        host.track(() -> host.log().info("Sample Plugin cleanup ran."));
    }

    private void openCenterView(JStudioHost host) {
        JTextArea area = new JTextArea("This is a plugin-contributed center view.\n"
                + "Plugins can host arbitrary Swing content here.");
        area.setEditable(false);
        host.ui().openCenterView("sample-view", "Sample View", null, new JScrollPane(area));
    }

    private void openBottomTab(JStudioHost host) {
        JTextArea area = new JTextArea("Plugin-contributed bottom tab.");
        area.setEditable(false);
        host.ui().addBottomTab("Sample Output", new JScrollPane(area));
    }

    @Override
    public void dispose() {
        selectionLabel = null;
    }
}
