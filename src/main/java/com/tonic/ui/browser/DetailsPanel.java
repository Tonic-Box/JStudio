package com.tonic.ui.browser;

import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.constpool.Item;
import com.tonic.ui.browser.details.AttributeDetailRegistry;
import com.tonic.ui.browser.details.DetailContext;
import com.tonic.ui.browser.details.ItemDetailRegistry;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class DetailsPanel extends ThemedJPanel {

    private final JTextArea detailsArea;
    private final JToggleButton hexToggle;
    private boolean showHex = false;

    private Item<?> currentItem;
    private int currentIndex;
    private Attribute currentAttribute;
    private ConstPool constPool;

    public DetailsPanel() {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 2));
        toolbar.setBackground(JStudioTheme.getBgSecondary());

        hexToggle = new JToggleButton("Hex");
        hexToggle.setFont(JStudioTheme.getUIFont(10));
        hexToggle.setFocusable(false);
        hexToggle.addActionListener(e -> {
            showHex = hexToggle.isSelected();
            refresh();
        });
        toolbar.add(hexToggle);

        add(toolbar, BorderLayout.NORTH);

        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        detailsArea.setBackground(JStudioTheme.getBgTertiary());
        detailsArea.setForeground(JStudioTheme.getTextPrimary());
        detailsArea.setCaretColor(JStudioTheme.getTextPrimary());
        detailsArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM));

        add(detailsArea, BorderLayout.CENTER);
    }

    public void clear() {
        currentItem = null;
        currentAttribute = null;
        detailsArea.setText("");
    }

    public void showItem(Item<?> item, int index, ConstPool constPool) {
        this.currentItem = item;
        this.currentIndex = index;
        this.currentAttribute = null;
        this.constPool = constPool;
        refresh();
    }

    public void showAttribute(Attribute attribute, String context, ConstPool constPool) {
        this.currentAttribute = attribute;
        this.currentItem = null;
        this.constPool = constPool;
        refreshAttribute(context);
    }

    private void refresh() {
        if (currentItem == null) {
            detailsArea.setText("");
            return;
        }

        DetailContext ctx = new DetailContext(constPool, showHex);
        StringBuilder sb = new StringBuilder();

        sb.append("=== Constant Pool Entry #").append(currentIndex).append(" ===\n\n");
        sb.append("Type: ").append(ItemDetailRegistry.getTypeName(currentItem)).append("\n");
        sb.append("Tag:  ").append(currentItem.getType() & 0xFF).append("\n\n");

        sb.append("--- Value ---\n");
        ItemDetailRegistry.format(currentItem, sb, ctx);

        if (showHex) {
            sb.append("\n--- Raw Bytes ---\n");
            sb.append("(Hex dump not available for this item type)\n");
        }

        detailsArea.setText(sb.toString());
        detailsArea.setCaretPosition(0);
    }

    private void refreshAttribute(String context) {
        if (currentAttribute == null) {
            detailsArea.setText("");
            return;
        }

        DetailContext ctx = new DetailContext(constPool, showHex);
        StringBuilder sb = new StringBuilder();

        sb.append("=== Attribute ===\n\n");
        sb.append("Name: ").append(AttributeDetailRegistry.getTypeName(currentAttribute)).append("\n");
        sb.append("Context: ").append(context).append("\n\n");

        sb.append("--- Details ---\n");
        AttributeDetailRegistry.format(currentAttribute, sb, ctx);

        detailsArea.setText(sb.toString());
        detailsArea.setCaretPosition(0);
    }
}
