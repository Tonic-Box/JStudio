package com.tonic.ui.navigator;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;
import java.awt.Font;

/**
 * Custom tree cell renderer for the class navigator.
 */
public class ClassTreeCellRenderer extends DefaultTreeCellRenderer
{

    private final Font normalFont;
    private final Font italicFont;

    public ClassTreeCellRenderer()
    {
        this.normalFont = JStudioTheme.getUIFont(12);
        this.italicFont = normalFont.deriveFont(Font.ITALIC);

        // Set colors
        setTextSelectionColor(JStudioTheme.getTextPrimary());
        setTextNonSelectionColor(JStudioTheme.getTextPrimary());
        setBackgroundSelectionColor(JStudioTheme.getSelection());
        setBackgroundNonSelectionColor(JStudioTheme.getBgSecondary());
        setBorderSelectionColor(JStudioTheme.getSelection());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof NavigatorNode)
        {
            NavigatorNode node = (NavigatorNode) value;

            // Set display text
            setText(node.getDisplayText());

            // Set icon
            setIcon(node.getIcon());

            // Set tooltip
            setToolTipText(node.getTooltip());

            // Set font (italic for abstract classes/methods)
            if (node instanceof NavigatorNode.ClassNode)
            {
                NavigatorNode.ClassNode classNode = (NavigatorNode.ClassNode) node;
                if (classNode.getClassEntry().isAbstract() || classNode.getClassEntry().isInterface())
                {
                    setFont(italicFont);
                }
                else
                {
                    setFont(normalFont);
                }
            }
            else if (node instanceof NavigatorNode.MethodNode)
            {
                NavigatorNode.MethodNode methodNode = (NavigatorNode.MethodNode) node;
                if (methodNode.getMethodEntry().isAbstract())
                {
                    setFont(italicFont);
                }
                else
                {
                    setFont(normalFont);
                }
            }
            else
            {
                setFont(normalFont);
            }

            // Adjust colors
            if (selected)
            {
                setBackground(JStudioTheme.getSelection());
                setForeground(JStudioTheme.getTextPrimary());
            }
            else
            {
                setBackground(JStudioTheme.getBgSecondary());
                setForeground(JStudioTheme.getTextPrimary());
            }
        }

        setOpaque(true);
        return this;
    }
}
