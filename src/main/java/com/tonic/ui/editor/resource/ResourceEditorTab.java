package com.tonic.ui.editor.resource;

import com.tonic.model.ResourceEntryModel;
import com.tonic.model.ResourceType;
import lombok.Getter;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/** The editor tab for a non-class resource, showing it as an image, text or hex dump by its resource type. */
@Getter
public class ResourceEditorTab extends JPanel
{

    private final ResourceEntryModel resource;
    private final JPanel contentView;

    /**
     * Creates the tab with the view that fits the resource's type, falling back to hex.
     *
     * @param resource the resource to show
     */
    public ResourceEditorTab(ResourceEntryModel resource)
    {
        this.resource = resource;
        setLayout(new BorderLayout());

        this.contentView = createViewForResource(resource);
        add(contentView, BorderLayout.CENTER);
    }

    private JPanel createViewForResource(ResourceEntryModel resource)
    {
        ResourceType type = resource.getResourceType();
        switch (type)
        {
            case IMAGE:
                return new ImageResourceView(resource);
            case TEXT:
                return new TextResourceView(resource);
            case BINARY:
            default:
                return new HexResourceView(resource);
        }
    }

    /**
     * Returns the tab title.
     *
     * @return the resource's name
     */
    public String getTitle()
    {
        return resource.getName();
    }

    /**
     * Returns the tab tooltip.
     *
     * @return the resource's path
     */
    public String getTooltip()
    {
        return resource.getPath();
    }
}
