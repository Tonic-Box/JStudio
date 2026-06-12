package com.tonic.ui.editor;

import com.tonic.model.ClassEntryModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Breadcrumb navigation bar showing current location: Package > Class > Method
 */
public class BreadcrumbBar extends JPanel {

    private ClassEntryModel currentClass;
    private String currentMethod;
    private final List<BreadcrumbItem> items = new ArrayList<>();

    private Consumer<String> onPackageClick;
    private Consumer<ClassEntryModel> onClassClick;
    private Consumer<String> onMethodClick;

    public BreadcrumbBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        setVisible(false); // Hidden until a class is loaded
    }

    /**
     * Update the breadcrumb to show the given class.
     */
    public void setClass(ClassEntryModel classEntry) {
        this.currentClass = classEntry;
        this.currentMethod = null;
        rebuild();
    }

    /**
     * Update the breadcrumb to show a method within the current class.
     */
    public void setMethod(String methodName) {
        this.currentMethod = methodName;
        rebuild();
    }

    /**
     * Clear the breadcrumb.
     */
    public void clear() {
        this.currentClass = null;
        this.currentMethod = null;
        rebuild();
    }

    /**
     * Set callback for package click.
     */
    public void setOnPackageClick(Consumer<String> callback) {
        this.onPackageClick = callback;
    }

    /**
     * Set callback for class click.
     */
    public void setOnClassClick(Consumer<ClassEntryModel> callback) {
        this.onClassClick = callback;
    }

    /**
     * Set callback for method click.
     */
    public void setOnMethodClick(Consumer<String> callback) {
        this.onMethodClick = callback;
    }

    private void rebuild() {
        removeAll();
        items.clear();

        if (currentClass == null) {
            setVisible(false);
            return;
        }

        setVisible(true);

        String className = currentClass.getClassName();
        String[] parts = className.replace('/', '.').split("\\.");

        // Build package path
        StringBuilder packagePath = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                packagePath.append(".");
            }
            packagePath.append(parts[i]);

            // Add package segment
            String pkg = parts[i];
            final String fullPackage = packagePath.toString();
            BreadcrumbItem item = new BreadcrumbItem(pkg, false, () -> {
                if (onPackageClick != null) {
                    onPackageClick.accept(fullPackage);
                }
            });
            addItem(item);
            addSeparator();
        }

        // Add class name
        String simpleClassName = parts[parts.length - 1];
        BreadcrumbItem classItem = new BreadcrumbItem(simpleClassName, currentMethod == null, () -> {
            if (onClassClick != null) {
                onClassClick.accept(currentClass);
            }
        });
        addItem(classItem);

        // Add method if present
        if (currentMethod != null) {
            addSeparator();
            BreadcrumbItem methodItem = new BreadcrumbItem(currentMethod, true, () -> {
                if (onMethodClick != null) {
                    onMethodClick.accept(currentMethod);
                }
            });
            addItem(methodItem);
        }

        revalidate();
        repaint();
    }

    private void addItem(BreadcrumbItem item) {
        items.add(item);
        add(item);
    }

    private void addSeparator() {
        JLabel sep = new JLabel(" > ");
        sep.setForeground(JStudioTheme.getTextSecondary());
        sep.setFont(JStudioTheme.getUIFont(11));
        add(sep);
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * A single breadcrumb segment.
     */
    private static class BreadcrumbItem extends JLabel {

        BreadcrumbItem(String text, boolean isCurrent, Runnable onClick) {
            super(sanitize(text));
            setFont(JStudioTheme.getUIFont(11));

            if (isCurrent) {
                setForeground(JStudioTheme.getAccent());
            } else {
                setForeground(JStudioTheme.getTextPrimary());
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        setForeground(JStudioTheme.getAccent());
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        setForeground(JStudioTheme.getTextPrimary());
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onClick != null) {
                            onClick.run();
                        }
                    }
                });
            }
        }
    }
}
