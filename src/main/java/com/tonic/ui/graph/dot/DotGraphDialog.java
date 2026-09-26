package com.tonic.ui.graph.dot;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JDialog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;

/** A modeless popup holding the interactive DOT diagram panel, opened from a thumbnail that has no other open handler. */
public final class DotGraphDialog extends JDialog
{

    /**
     * Builds the popup at three quarters of the owner's size, at least 640 by 480.
     *
     * @param owner the window it centers on; null gives a fixed 820 by 620 size
     * @param dotSource the DOT text to draw
     */
    public DotGraphDialog(Window owner, String dotSource)
    {
        super(owner, "Diagram", ModalityType.MODELESS);
        setLayout(new BorderLayout());
        add(new DotGraphPanel(dotSource), BorderLayout.CENTER);
        getContentPane().setBackground(JStudioTheme.getBgPrimary());
        setSize(sizeFor(owner));
        setLocationRelativeTo(owner);
    }

    private static Dimension sizeFor(Window owner)
    {
        if (owner != null)
        {
            return new Dimension(Math.max(640, owner.getWidth() * 3 / 4), Math.max(480, owner.getHeight() * 3 / 4));
        }
        return new Dimension(820, 620);
    }
}
