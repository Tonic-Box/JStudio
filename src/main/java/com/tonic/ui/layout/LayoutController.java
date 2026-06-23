package com.tonic.ui.layout;

import com.tonic.ui.bottom.BottomPanel;
import com.tonic.ui.bottom.BottomToolbar;
import com.tonic.ui.core.component.ToolWindowPane;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.navigator.NavigatorPanel;
import com.tonic.util.Settings;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Builds and drives the main window's three nested split panes (navigator | (editor-over-bottom-dock | right
 * tool window)) and owns the collapse/expand divider math for the bottom dock and right tool window, plus the
 * navigator/properties show-hide toggles. Constructed with the child components it arranges; the assembled center
 * component is returned from {@link #buildCenter()} for the frame to drop into its content pane.
 */
public final class LayoutController {

    private final EditorPanel editorPanel;
    private final BottomPanel sidePanel;
    private final NavigatorPanel navigatorPanel;
    private final ToolWindowPane rightToolWindow;
    private final BottomToolbar bottomToolbar;

    private JSplitPane editorBottomSplit;
    private JSplitPane mainHorizontalSplit;
    private JSplitPane leftRightSplit;

    private boolean bottomPanelCollapsed = true;
    private int expandedBottomDivider = -1;

    private int savedNavigatorDivider = 250;
    private int savedPropertiesDivider = -1;

    public LayoutController(EditorPanel editorPanel, BottomPanel sidePanel, NavigatorPanel navigatorPanel,
                            ToolWindowPane rightToolWindow, BottomToolbar bottomToolbar) {
        this.editorPanel = editorPanel;
        this.sidePanel = sidePanel;
        this.navigatorPanel = navigatorPanel;
        this.rightToolWindow = rightToolWindow;
        this.bottomToolbar = bottomToolbar;
    }

    /**
     * Builds the nested split panes and returns the top-level component (navigator + center + right) to be
     * placed in the frame's content pane.
     */
    public JComponent buildCenter() {
        editorBottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, sidePanel);
        editorBottomSplit.setResizeWeight(1.0);
        editorBottomSplit.setDividerSize(4);
        editorBottomSplit.setBorder(null);
        editorBottomSplit.setContinuousLayout(true);
        editorBottomSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (bottomPanelCollapsed && editorBottomSplit.getHeight() > 0) {
                    editorBottomSplit.setDividerLocation(collapsedBottomDivider());
                }
            }
        });

        JPanel editorAreaWrapper = new JPanel(new BorderLayout());
        editorAreaWrapper.add(editorBottomSplit, BorderLayout.CENTER);
        editorAreaWrapper.add(bottomToolbar, BorderLayout.SOUTH);

        leftRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                editorAreaWrapper, rightToolWindow);
        leftRightSplit.setResizeWeight(0.75);
        leftRightSplit.setDividerSize(4);
        leftRightSplit.setBorder(null);
        leftRightSplit.setContinuousLayout(true);

        rightToolWindow.setCollapseListener(this::applyRightPanelCollapsed);
        leftRightSplit.addComponentListener(new ComponentAdapter() {
            private boolean applied = false;

            @Override
            public void componentResized(ComponentEvent e) {
                if (leftRightSplit.getWidth() <= 0) {
                    return;
                }
                if (!applied) {
                    applied = true;
                    applyRightPanelCollapsed(rightToolWindow.isCollapsed());
                } else if (rightToolWindow.isCollapsed()) {
                    int stripe = rightToolWindow.getStripeWidth() + leftRightSplit.getDividerSize();
                    leftRightSplit.setDividerLocation(Math.max(0, leftRightSplit.getWidth() - stripe));
                }
            }
        });

        mainHorizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                navigatorPanel, leftRightSplit);
        mainHorizontalSplit.setDividerLocation(250);
        mainHorizontalSplit.setDividerSize(4);
        mainHorizontalSplit.setBorder(null);
        mainHorizontalSplit.setContinuousLayout(true);

        return mainHorizontalSplit;
    }

    /** Whether the bottom dock is currently collapsed (used by the bottom panel's collapse host). */
    public boolean isBottomCollapsed() {
        return bottomPanelCollapsed;
    }

    public void toggleNavigatorPanel() {
        if (navigatorPanel.isVisible()) {
            savedNavigatorDivider = mainHorizontalSplit.getDividerLocation();
            navigatorPanel.setVisible(false);
            mainHorizontalSplit.setDividerLocation(0);
        } else {
            navigatorPanel.setVisible(true);
            mainHorizontalSplit.setDividerLocation(savedNavigatorDivider > 0 ? savedNavigatorDivider : 250);
        }
        mainHorizontalSplit.revalidate();
    }

    public void togglePropertiesPanel() {
        rightToolWindow.setCollapsed(!rightToolWindow.isCollapsed());
        leftRightSplit.revalidate();
    }

    /**
     * Reacts to the right tool window collapsing/expanding: when collapsed, the right column shrinks to just
     * the stripe; when expanded, the saved layout is restored.
     */
    private void applyRightPanelCollapsed(boolean collapsed) {
        if (collapsed) {
            int loc = leftRightSplit.getDividerLocation();
            if (loc > 0 && loc < leftRightSplit.getWidth()) {
                savedPropertiesDivider = loc;
            }
            int stripe = rightToolWindow.getStripeWidth() + leftRightSplit.getDividerSize();
            leftRightSplit.setDividerLocation(Math.max(0, leftRightSplit.getWidth() - stripe));
        } else {
            if (savedPropertiesDivider > 0) {
                leftRightSplit.setDividerLocation(savedPropertiesDivider);
            } else {
                leftRightSplit.setDividerLocation((int) (leftRightSplit.getWidth() * 0.75));
            }
        }
        leftRightSplit.revalidate();
    }

    /** The divider location that leaves only the bottom tab strip visible (or fully hides it when there are no tabs). */
    private int collapsedBottomDivider() {
        int total = editorBottomSplit.getHeight();
        int strip = sidePanel.hasTabs() ? sidePanel.collapsedHeight() : 0;
        return Math.max(0, total - strip - editorBottomSplit.getDividerSize());
    }

    /** Collapses the bottom dock to just its tab strip (or hides it when empty), remembering the expanded height. */
    public void collapseBottom() {
        if (!bottomPanelCollapsed) {
            expandedBottomDivider = editorBottomSplit.getDividerLocation();
        }
        bottomPanelCollapsed = true;
        editorBottomSplit.setDividerLocation(collapsedBottomDivider());
    }

    /** Expands the bottom dock back to its remembered height (or a default); no-op when already expanded. */
    public void expandBottom() {
        if (!bottomPanelCollapsed) {
            return;
        }
        bottomPanelCollapsed = false;
        int total = editorBottomSplit.getHeight();
        int location = expandedBottomDivider > 0 && expandedBottomDivider < total - 50
                ? expandedBottomDivider : Math.max(0, total - 200);
        editorBottomSplit.setDividerLocation(location);
    }

    /** Persists the navigator and right-tool-window divider positions (console height is preserved as-is). */
    public void saveDividers(Settings settings) {
        settings.saveDividerPositions(
                mainHorizontalSplit.getDividerLocation(),
                leftRightSplit.getWidth() - leftRightSplit.getDividerLocation(),
                settings.getConsoleHeight()
        );
    }
}
