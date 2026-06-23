package com.tonic.ui.navigator;

import com.tonic.ui.MainFrame;
import com.tonic.ui.core.util.JvmDescriptorFormatter;
import com.tonic.ui.editor.ViewMode;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.event.events.ResourceSelectedEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.plugin.api.ui.NavigatorAction;
import com.tonic.plugin.api.ui.NavigatorActionProvider;
import com.tonic.plugin.api.ui.NavigatorContext;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the navigator tree's right-click popup. {@link #buildFor(Object)} returns a styled menu populated with the
 * per-node-type built-in items plus any plugin-contributed entries (consulting the shared provider list each time).
 * Mutating actions are delegated to {@link NavigatorActions}; navigation/run/rename items are issued over the
 * {@link EventBus} or routed to {@link MainFrame}, with the supplied event source preserved on posted events.
 */
final class NavigatorContextMenuFactory {

    private final Component eventSource;
    private final MainFrame mainFrame;
    private final NavigatorActions actions;
    private final List<NavigatorActionProvider> actionProviders;

    NavigatorContextMenuFactory(Component eventSource, MainFrame mainFrame, NavigatorActions actions,
                                List<NavigatorActionProvider> actionProviders) {
        this.eventSource = eventSource;
        this.mainFrame = mainFrame;
        this.actions = actions;
        this.actionProviders = actionProviders;
    }

    /** Builds the styled popup for the given right-clicked tree node, including any plugin-contributed entries. */
    JPopupMenu buildFor(Object node) {
        JPopupMenu menu = new JPopupMenu();
        styleMenu(menu);

        if (node instanceof NavigatorNode.MethodNode) {
            buildMethodMenu(menu, (NavigatorNode.MethodNode) node);
        } else if (node instanceof NavigatorNode.FieldNode) {
            buildFieldMenu(menu, (NavigatorNode.FieldNode) node);
        } else if (node instanceof NavigatorNode.ClassNode) {
            buildClassMenu(menu, (NavigatorNode.ClassNode) node);
        } else if (node instanceof NavigatorNode.PackageNode) {
            buildPackageMenu(menu, (NavigatorNode.PackageNode) node);
        } else if (node instanceof NavigatorNode.ProjectNode) {
            buildProjectMenu(menu);
        } else if (node instanceof NavigatorNode.ResourceFolderNode) {
            buildResourceFolderMenu(menu, (NavigatorNode.ResourceFolderNode) node);
        } else if (node instanceof NavigatorNode.ResourcesRootNode) {
            buildResourcesRootMenu(menu);
        } else if (node instanceof NavigatorNode.ResourceNode) {
            buildResourceMenu(menu, (NavigatorNode.ResourceNode) node);
        }

        appendPluginActions(menu, node);

        return menu;
    }

    /** Appends plugin-contributed entries (if any) for the right-clicked node, after the built-in items. */
    private void appendPluginActions(JPopupMenu menu, Object node) {
        if (actionProviders.isEmpty()) {
            return;
        }
        NavigatorContext context = buildNavigatorContext(node);
        List<NavigatorAction> actions = new ArrayList<>();
        for (NavigatorActionProvider provider : actionProviders) {
            try {
                List<NavigatorAction> contributed = provider.actionsFor(context);
                if (contributed != null) {
                    actions.addAll(contributed);
                }
            } catch (Exception ex) {
                // A misbehaving provider must not break the native context menu.
            }
        }
        if (actions.isEmpty()) {
            return;
        }
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        for (NavigatorAction action : actions) {
            addMenuItem(menu, action.label(), action.action());
        }
    }

    private NavigatorContext buildNavigatorContext(Object node) {
        final ClassEntryModel cls = node instanceof NavigatorNode.ClassNode
                ? ((NavigatorNode.ClassNode) node).getClassEntry() : null;
        final MethodEntryModel method = node instanceof NavigatorNode.MethodNode
                ? ((NavigatorNode.MethodNode) node).getMethodEntry() : null;
        final FieldEntryModel field = node instanceof NavigatorNode.FieldNode
                ? ((NavigatorNode.FieldNode) node).getFieldEntry() : null;
        final ResourceEntryModel resource = node instanceof NavigatorNode.ResourceNode
                ? ((NavigatorNode.ResourceNode) node).getResource() : null;
        return new NavigatorContext() {
            @Override
            public Optional<ClassEntryModel> selectedClass() {
                return Optional.ofNullable(cls);
            }

            @Override
            public Optional<MethodEntryModel> selectedMethod() {
                return Optional.ofNullable(method);
            }

            @Override
            public Optional<FieldEntryModel> selectedField() {
                return Optional.ofNullable(field);
            }

            @Override
            public Optional<ResourceEntryModel> selectedResource() {
                return Optional.ofNullable(resource);
            }
        };
    }

    private void buildMethodMenu(JPopupMenu menu, NavigatorNode.MethodNode node) {
        MethodEntryModel method = node.getMethodEntry();

        addMenuItem(menu, "Find Usages", () -> EventBus.getInstance().post(
            FindUsagesEvent.forMethod(eventSource, method.getOwner().getClassName(),
                method.getName(), method.getDescriptor())));

        menu.addSeparator();

        addMenuItem(menu, "Execute Method...", () -> mainFrame.openExecuteMethodDialog(method));

        addMenuItem(menu, "Fuzz & Generate Tests...", () -> actions.openFuzzTestDialog(method));

        menu.addSeparator();

        addMenuItem(menu, "Rename Method...", () -> mainFrame.showRenameMethodDialog(method.getOwner(), method));

        menu.addSeparator();

        addMenuItem(menu, "Copy Signature", () -> {
            String ownerSimple = JvmDescriptorFormatter.getSimpleClassName(method.getOwner().getClassName());
            String sig = ownerSimple + "." + method.getName()
                + JvmDescriptorFormatter.formatDescriptorParams(method.getDescriptor());
            copyToClipboard(sig);
        });

        addMenuItem(menu, "Copy Descriptor", () -> copyToClipboard(method.getDescriptor()));

        addMenuItem(menu, "Copy Full Reference", () -> {
            String fullRef = method.getOwner().getClassName() + "." + method.getName() + method.getDescriptor();
            copyToClipboard(fullRef);
        });
    }

    private void buildFieldMenu(JPopupMenu menu, NavigatorNode.FieldNode node) {
        FieldEntryModel field = node.getFieldEntry();

        addMenuItem(menu, "Go to Field", () -> {
            EventBus.getInstance().post(new ClassSelectedEvent(eventSource, field.getOwner()));
            SwingUtilities.invokeLater(() -> {
                mainFrame.getEditorPanel().setViewMode(ViewMode.SOURCE);
                mainFrame.getEditorPanel().scrollToField(field);
            });
        });

        addMenuItem(menu, "Find Usages", () -> EventBus.getInstance().post(
            FindUsagesEvent.forField(eventSource, field.getOwner().getClassName(),
                field.getName(), field.getDescriptor())));

        menu.addSeparator();

        addMenuItem(menu, "Rename Field...", () -> mainFrame.showRenameFieldDialog(field.getOwner(), field));

        menu.addSeparator();

        addMenuItem(menu, "Copy Name", () -> copyToClipboard(field.getName()));

        addMenuItem(menu, "Copy Full Name", () -> {
            String ownerSimple = JvmDescriptorFormatter.getSimpleClassName(field.getOwner().getClassName());
            copyToClipboard(ownerSimple + "." + field.getName());
        });

        addMenuItem(menu, "Copy Descriptor", () -> copyToClipboard(field.getDescriptor()));
    }

    private void buildClassMenu(JPopupMenu menu, NavigatorNode.ClassNode node) {
        ClassEntryModel classEntry = node.getClassEntry();

        addMenuItem(menu, "Open in Editor", () -> EventBus.getInstance().post(new ClassSelectedEvent(eventSource, classEntry)));

        if (classEntry.hasMainMethod() && !LiveAttachService.getInstance().isAttached()) {
            addMenuItem(menu, "Run " + classEntry.getSimpleName() + ".main()", () -> mainFrame.runMainClass(classEntry));
        }

        addMenuItem(menu, "Find Usages", () -> EventBus.getInstance().post(
            FindUsagesEvent.forClass(eventSource, classEntry.getClassName())));

        menu.addSeparator();

        addMenuItem(menu, "Rename Class...", () -> actions.renameClass(classEntry));

        addMenuItem(menu, "Delete", () -> actions.deleteClass(classEntry));

        menu.addSeparator();

        addMenuItem(menu, "Export Class...", () -> mainFrame.exportClass(classEntry));

        menu.addSeparator();

        addMenuItem(menu, "Copy Class Name", () -> copyToClipboard(classEntry.getClassName().replace('/', '.')));

        addMenuItem(menu, "Copy Internal Name", () -> copyToClipboard(classEntry.getClassName()));

        addMenuItem(menu, "Copy Simple Name", () -> copyToClipboard(classEntry.getSimpleName()));
    }

    private void buildPackageMenu(JPopupMenu menu, NavigatorNode.PackageNode node) {
        addMenuItem(menu, "Add New Class...", () -> actions.showNewClassDialog(node.getPackageName()));
    }

    private void buildProjectMenu(JPopupMenu menu) {
        // Works whether or not a project is loaded: with no project, creating a class spins up a
        // new (Untitled) project in the root package and opens the class in it.
        addMenuItem(menu, "Add New Class...", () -> actions.showNewClassDialog(""));
    }

    private void buildResourceFolderMenu(JPopupMenu menu, NavigatorNode.ResourceFolderNode node) {
        addMenuItem(menu, "New Empty File...", () -> actions.showNewResourceFileDialog(node.getFolderPath()));
        addMenuItem(menu, "Import File...", () -> actions.showImportResourceDialog(node.getFolderPath()));
    }

    private void buildResourcesRootMenu(JPopupMenu menu) {
        addMenuItem(menu, "New Empty File...", () -> actions.showNewResourceFileDialog(""));
        addMenuItem(menu, "Import File...", () -> actions.showImportResourceDialog(""));
    }

    private void buildResourceMenu(JPopupMenu menu, NavigatorNode.ResourceNode node) {
        ResourceEntryModel resource = node.getResource();
        addMenuItem(menu, "Open", () -> EventBus.getInstance().post(new ResourceSelectedEvent(eventSource, resource)));
        menu.addSeparator();
        addMenuItem(menu, "Delete", () -> actions.deleteResource(resource));
    }

    private void styleMenu(JPopupMenu menu) {
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
    }

    private void addMenuItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }
}
