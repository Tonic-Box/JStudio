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
import java.util.function.Consumer;

/** The breadcrumb bar above the editor showing package, class and method, each clickable; hidden while no class is set. */
public class BreadcrumbBar extends JPanel
{

    private ClassEntryModel currentClass;
    private String currentMethod;

    private Consumer<String> onPackageClick;
    private Consumer<ClassEntryModel> onClassClick;
    private Consumer<String> onMethodClick;

    /** Creates an empty, hidden bar. */
    public BreadcrumbBar()
    {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        setVisible(false);
    }

    /**
     * Shows a class, clearing any method.
     *
     * @param classEntry the class, or null to hide the bar
     */
    public void setClass(ClassEntryModel classEntry)
    {
        this.currentClass = classEntry;
        this.currentMethod = null;
        rebuild();
    }

    /**
     * Shows a method within the current class.
     *
     * @param methodName the method's name, or null to show only the class
     */
    public void setMethod(String methodName)
    {
        this.currentMethod = methodName;
        rebuild();
    }

    /** Clears the class and method and hides the bar. */
    public void clear()
    {
        this.currentClass = null;
        this.currentMethod = null;
        rebuild();
    }

    /**
     * Sets what runs when the package crumb is clicked.
     *
     * @param callback receives the package name
     */
    public void setOnPackageClick(Consumer<String> callback)
    {
        this.onPackageClick = callback;
    }

    /**
     * Sets what runs when the class crumb is clicked.
     *
     * @param callback receives the current class
     */
    public void setOnClassClick(Consumer<ClassEntryModel> callback)
    {
        this.onClassClick = callback;
    }

    /**
     * Sets what runs when the method crumb is clicked.
     *
     * @param callback receives the method name
     */
    public void setOnMethodClick(Consumer<String> callback)
    {
        this.onMethodClick = callback;
    }

    private void rebuild()
    {
        removeAll();

        if (currentClass == null)
        {
            setVisible(false);
            return;
        }

        setVisible(true);

        String className = currentClass.getClassName();
        String[] parts = className.replace('/', '.').split("\\.");

        StringBuilder packagePath = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++)
        {
            if (i > 0)
            {
                packagePath.append(".");
            }
            packagePath.append(parts[i]);

            String pkg = parts[i];
            final String fullPackage = packagePath.toString();
            BreadcrumbItem item = new BreadcrumbItem(pkg, false, () ->
            {
                if (onPackageClick != null)
                {
                    onPackageClick.accept(fullPackage);
                }
            });
            addItem(item);
            addSeparator();
        }

        String simpleClassName = parts[parts.length - 1];
        BreadcrumbItem classItem = new BreadcrumbItem(simpleClassName, currentMethod == null, () ->
        {
            if (onClassClick != null)
            {
                onClassClick.accept(currentClass);
            }
        });
        addItem(classItem);

        if (currentMethod != null)
        {
            addSeparator();
            BreadcrumbItem methodItem = new BreadcrumbItem(currentMethod, true, () ->
            {
                if (onMethodClick != null)
                {
                    onMethodClick.accept(currentMethod);
                }
            });
            addItem(methodItem);
        }

        revalidate();
        repaint();
    }

    private void addItem(BreadcrumbItem item)
    {
        add(item);
    }

    private void addSeparator()
    {
        JLabel sep = new JLabel(" > ");
        sep.setForeground(JStudioTheme.getTextSecondary());
        sep.setFont(JStudioTheme.getUIFont(11));
        add(sep);
    }

    private static String sanitize(String text)
    {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static class BreadcrumbItem extends JLabel
    {

        BreadcrumbItem(String text, boolean isCurrent, Runnable onClick)
        {
            super(sanitize(text));
            setFont(JStudioTheme.getUIFont(11));

            if (isCurrent)
            {
                setForeground(JStudioTheme.getAccent());
            }
            else
            {
                setForeground(JStudioTheme.getTextPrimary());
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseEntered(MouseEvent e)
                    {
                        setForeground(JStudioTheme.getAccent());
                    }

                    @Override
                    public void mouseExited(MouseEvent e)
                    {
                        setForeground(JStudioTheme.getTextPrimary());
                    }

                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        if (onClick != null)
                        {
                            onClick.run();
                        }
                    }
                });
            }
        }
    }
}
