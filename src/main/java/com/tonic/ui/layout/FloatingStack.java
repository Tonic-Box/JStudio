package com.tonic.ui.layout;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.theme.JStudioTheme;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JRootPane;

/** A tab stack torn out into a window of its own; closing the window sends its views home. */
public final class FloatingStack
{

    private static final Dimension SIZE = new Dimension(560, 620);

    private final StackId id;
    private final ViewStackPane strip;
    private final JDialog window;

    FloatingStack(StackId id, Runnable onClosed)
    {
        this.id = id;
        this.strip = new ViewStackPane(id);

        window = new JDialog((Window) null);
        window.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        final ThemedJPanel holder = new ThemedJPanel(ThemedJPanel.BackgroundStyle.PRIMARY, new BorderLayout());
        holder.setBackground(JStudioTheme.getBgPrimary());
        holder.add(strip, BorderLayout.CENTER);
        window.setContentPane(holder);
        window.setSize(SIZE);
        window.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent event)
            {
                onClosed.run();
            }
        });
    }

    /** @return the stack this window holds */
    public StackId id()
    {
        return id;
    }

    /** @return the tab strip in the window */
    public ViewStackPane strip()
    {
        return strip;
    }

    /**
     * Lists the views in the window.
     *
     * @return its views in tab order
     */
    public List<ViewId> views()
    {
        return strip.views();
    }

    void show(Point at, String title)
    {
        window.setTitle(title);
        if (at == null)
        {
            window.setLocationByPlatform(true);
        }
        else
        {
            window.setLocation(at.x - SIZE.width / 2, Math.max(0, at.y - 20));
        }
        window.setVisible(true);
    }

    void retitle(String title)
    {
        window.setTitle(title);
    }

    boolean isShowing()
    {
        return window.isShowing();
    }

    JRootPane rootPane()
    {
        return window.getRootPane();
    }

    Point locationOnScreen()
    {
        return strip.isShowing() ? strip.getLocationOnScreen() : null;
    }

    Dimension size()
    {
        return strip.getSize();
    }

    void dispose()
    {
        window.dispose();
    }
}
