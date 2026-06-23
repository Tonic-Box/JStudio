package com.tonic.ui.editor;

import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.editor.resource.ResourceEditorTab;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.RunnableOverlayIcon;
import com.tonic.ui.theme.ThemeStyles;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiFunction;

/**
 * Builds the tab header components for the editor's tabbed pane. The Welcome header is non-closable and only
 * selects on click; the class, resource, and custom-view headers share one {@link #buildHeader} builder that adds
 * the close button, select/context-menu click listener, and drag-to-reorder handler.
 */
final class TabHeaderFactory {

    private final JTabbedPane pane;
    private final TabDragController dragController;
    private final BiFunction<JComponent, Component, MouseAdapter> headerListenerFactory;

    TabHeaderFactory(JTabbedPane pane, TabDragController dragController,
                     BiFunction<JComponent, Component, MouseAdapter> headerListenerFactory) {
        this.pane = pane;
        this.dragController = dragController;
        this.headerListenerFactory = headerListenerFactory;
    }

    /** The class icon with the runnable (play) overlay when the class has a {@code main} method - matches the tree. */
    static Icon classIcon(ClassEntryModel classEntry) {
        Icon icon = Icons.getIcon(classEntry.getIconKey());
        return classEntry.hasMainMethod() ? new RunnableOverlayIcon(icon) : icon;
    }

    /** The pinned Welcome header: icon + title, no close button; clicking anywhere selects the tab. */
    JPanel createWelcomeTabComponent() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
        panel.setOpaque(false);

        JLabel iconLabel = new JLabel(Icons.getIcon("home"));
        panel.add(iconLabel);

        JLabel titleLabel = new JLabel("Welcome");
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        panel.add(titleLabel);

        MouseAdapter tabSelector = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = pane.indexOfTabComponent(panel);
                if (index != -1) {
                    pane.setSelectedIndex(index);
                }
            }
        };
        panel.addMouseListener(tabSelector);
        iconLabel.addMouseListener(tabSelector);
        titleLabel.addMouseListener(tabSelector);

        return panel;
    }

    /** A class-editor tab header. */
    JPanel createTabComponent(EditorTab tab, Runnable onClose) {
        return buildHeader(classIcon(tab.getClassEntry()), true, tab.getTitle(), onClose, tab);
    }

    /** A resource-editor tab header. */
    JPanel createResourceTabComponent(ResourceEditorTab tab, Runnable onClose) {
        return buildHeader(Icons.getIcon(tab.getResource().getIconKey()), true, tab.getTitle(), onClose, tab);
    }

    /** A plugin-contributed custom-view tab header (icon may be null; the icon, if any, is not interactive). */
    JPanel createCustomTabComponent(String title, Icon icon, JComponent view, Runnable onClose) {
        return buildHeader(icon, false, title, onClose, view);
    }

    /**
     * The shared header builder for closable tabs: icon (optional) + title + close button, plus the
     * select/context-menu click listener and drag-to-reorder handler. When {@code iconInteractive} is true the icon
     * label also receives the click listener and participates in dragging.
     */
    private JPanel buildHeader(Icon icon, boolean iconInteractive, String title, Runnable onClose, Component tabBody) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 0));
        panel.setOpaque(false);

        JLabel iconLabel = icon != null ? new JLabel(icon) : null;
        if (iconLabel != null) {
            panel.add(iconLabel);
        }

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(JStudioTheme.getTextPrimary());
        titleLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        panel.add(titleLabel);

        JButton closeButton = new JButton(Icons.getIcon("close"));
        closeButton.setPreferredSize(new Dimension(UIConstants.ICON_SIZE_SMALL, UIConstants.ICON_SIZE_SMALL));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        ThemeStyles.addFillHoverEffect(closeButton);
        closeButton.addActionListener(e -> onClose.run());
        panel.add(closeButton);

        MouseAdapter tabClickListener = headerListenerFactory.apply(panel, tabBody);
        panel.addMouseListener(tabClickListener);
        titleLabel.addMouseListener(tabClickListener);

        if (iconInteractive && iconLabel != null) {
            iconLabel.addMouseListener(tabClickListener);
            dragController.install(panel, iconLabel, titleLabel);
        } else {
            dragController.install(panel, titleLabel);
        }
        return panel;
    }
}
