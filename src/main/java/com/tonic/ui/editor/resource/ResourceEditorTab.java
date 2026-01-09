package com.tonic.ui.editor.resource;

import com.tonic.ui.model.ResourceEntryModel;
import com.tonic.ui.model.ResourceType;
import lombok.Getter;

import javax.swing.JPanel;
import java.awt.BorderLayout;

@Getter
public class ResourceEditorTab extends JPanel {

    private final ResourceEntryModel resource;
    private final JPanel contentView;

    public ResourceEditorTab(ResourceEntryModel resource) {
        this.resource = resource;
        setLayout(new BorderLayout());

        this.contentView = createViewForResource(resource);
        add(contentView, BorderLayout.CENTER);
    }

    private JPanel createViewForResource(ResourceEntryModel resource) {
        ResourceType type = resource.getResourceType();
        switch (type) {
            case IMAGE:
                return new ImageResourceView(resource);
            case TEXT:
                return new TextResourceView(resource);
            case BINARY:
            default:
                return new HexResourceView(resource);
        }
    }

    public String getTitle() {
        return resource.getName();
    }

    public String getTooltip() {
        return resource.getPath();
    }
}
