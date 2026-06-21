package com.tonic.ui.graph.dot;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JDialog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;

/**
 * The expanded, interactive view of a DOT diagram in a popup window. A thin wrapper over {@link DotGraphPanel} (the
 * actual pan/zoom graph + toolbar); opened from {@link DotGraphView} when no in-app open handler is supplied. The
 * AI chat instead embeds {@link DotGraphPanel} directly as an editor tab.
 */
public final class DotGraphDialog extends JDialog {

    public DotGraphDialog(Window owner, String dotSource) {
        super(owner, "Diagram", ModalityType.MODELESS);
        setLayout(new BorderLayout());
        add(new DotGraphPanel(dotSource), BorderLayout.CENTER);
        getContentPane().setBackground(JStudioTheme.getBgPrimary());
        setSize(sizeFor(owner));
        setLocationRelativeTo(owner);
    }

    private static Dimension sizeFor(Window owner) {
        if (owner != null) {
            return new Dimension(Math.max(640, owner.getWidth() * 3 / 4),
                    Math.max(480, owner.getHeight() * 3 / 4));
        }
        return new Dimension(820, 620);
    }
}
