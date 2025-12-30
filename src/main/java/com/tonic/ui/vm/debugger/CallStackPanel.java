package com.tonic.ui.vm.debugger;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.JdkClassFilter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CallStackPanel extends ThemedJPanel {

    private final JList<FrameEntry> frameList;
    private final DefaultListModel<FrameEntry> listModel;
    private Consumer<FrameEntry> onFrameSelected;

    public CallStackPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Call Stack",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));

        listModel = new DefaultListModel<>();
        frameList = new JList<>(listModel);
        frameList.setBackground(JStudioTheme.getBgSecondary());
        frameList.setForeground(JStudioTheme.getTextPrimary());
        frameList.setSelectionBackground(JStudioTheme.getAccent());
        frameList.setSelectionForeground(JStudioTheme.getTextPrimary());
        frameList.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        frameList.setFixedCellHeight(UIConstants.TABLE_ROW_HEIGHT);

        frameList.setCellRenderer(new FrameCellRenderer());

        frameList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onFrameSelected != null) {
                FrameEntry selected = frameList.getSelectedValue();
                if (selected != null) {
                    onFrameSelected.accept(selected);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(frameList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateCallStack(List<FrameEntry> frames) {
        listModel.clear();
        for (FrameEntry frame : frames) {
            listModel.addElement(frame);
        }
        if (!frames.isEmpty()) {
            for (int i = 0; i < frames.size(); i++) {
                if (frames.get(i).isCurrent()) {
                    frameList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    public void clear() {
        listModel.clear();
    }

    public void setOnFrameSelected(Consumer<FrameEntry> handler) {
        this.onFrameSelected = handler;
    }

    private static class FrameCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof FrameEntry) {
                FrameEntry frame = (FrameEntry) value;
                setText(frame.toString());

                boolean isJdkFrame = JdkClassFilter.isJdkClass(frame.getClassName());

                if (isSelected) {
                    setBackground(JStudioTheme.getAccent());
                    setForeground(JStudioTheme.getTextPrimary());
                } else if (frame.isCurrent()) {
                    setBackground(JStudioTheme.getAccentSecondary().darker());
                    setForeground(JStudioTheme.getTextPrimary());
                } else {
                    setBackground(JStudioTheme.getBgSecondary());
                    setForeground(isJdkFrame ? JStudioTheme.getTextDisabled() : JStudioTheme.getTextPrimary());
                }
            }

            return this;
        }
    }
}
