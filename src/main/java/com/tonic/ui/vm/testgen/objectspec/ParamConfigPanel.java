package com.tonic.ui.vm.testgen.objectspec;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.*;

public class ParamConfigPanel extends JPanel {

    private final Window ownerWindow;
    private ParamSpec spec;

    private JLabel nameLabel;
    private JLabel typeLabel;
    private JComboBox<ValueMode> modeCombo;
    private JTextField valueField;
    private JButton configButton;
    private JLabel summaryLabel;

    public ParamConfigPanel(Window owner, ParamSpec spec) {
        this.ownerWindow = owner;
        this.spec = spec;
        initComponents();
        updateDisplay();
    }

    private void initComponents() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        setBackground(JStudioTheme.getBgPrimary());
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        nameLabel = createLabel(spec.getName() != null ? spec.getName() : "param");
        nameLabel.setPreferredSize(new Dimension(80, 20));
        add(nameLabel);

        typeLabel = createLabel("(" + spec.getSimpleTypeName() + ")");
        typeLabel.setForeground(JStudioTheme.getTextSecondary());
        typeLabel.setPreferredSize(new Dimension(80, 20));
        add(typeLabel);

        modeCombo = new JComboBox<>();
        populateModeCombo();
        modeCombo.setPreferredSize(new Dimension(120, 22));
        modeCombo.addActionListener(e -> onModeChanged());
        add(modeCombo);

        valueField = new JTextField(15);
        valueField.setVisible(false);
        add(valueField);

        configButton = new JButton("Configure...");
        configButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        configButton.setVisible(false);
        configButton.addActionListener(e -> openObjectBuilder());
        add(configButton);

        summaryLabel = createLabel("");
        summaryLabel.setForeground(new Color(156, 220, 254));
        add(summaryLabel);
    }

    private void populateModeCombo() {
        modeCombo.removeAllItems();

        if (spec.isPrimitive()) {
            modeCombo.addItem(ValueMode.FUZZ);
            modeCombo.addItem(ValueMode.FIXED);
        } else if (spec.isString()) {
            modeCombo.addItem(ValueMode.FUZZ);
            modeCombo.addItem(ValueMode.FIXED);
            modeCombo.addItem(ValueMode.NULL);
        } else {
            modeCombo.addItem(ValueMode.OBJECT_SPEC);
            modeCombo.addItem(ValueMode.FUZZ);
            modeCombo.addItem(ValueMode.NULL);
        }

        modeCombo.setSelectedItem(spec.getMode());
    }

    private void onModeChanged() {
        ValueMode mode = (ValueMode) modeCombo.getSelectedItem();
        if (mode == null) return;

        spec.setMode(mode);

        valueField.setVisible(mode == ValueMode.FIXED);
        configButton.setVisible(mode == ValueMode.OBJECT_SPEC);

        updateSummary();
        revalidate();
    }

    private void openObjectBuilder() {
        if (!spec.isObjectType()) return;

        String objectTypeName = spec.getTypeDescriptor();
        if (objectTypeName.startsWith("L") && objectTypeName.endsWith(";")) {
            objectTypeName = objectTypeName.substring(1, objectTypeName.length() - 1);
        }

        ObjectSpec existing = spec.getNestedObjectSpec();
        ObjectSpec result = ObjectBuilderDialog.showDialog(ownerWindow, objectTypeName, existing);

        if (result != null) {
            spec.setNestedObjectSpec(result);
            updateSummary();
        }
    }

    private void updateDisplay() {
        if (spec.getMode() != null) {
            modeCombo.setSelectedItem(spec.getMode());
        }

        if (spec.getMode() == ValueMode.FIXED && spec.getFixedValue() != null) {
            valueField.setText(String.valueOf(spec.getFixedValue()));
        }

        onModeChanged();
    }

    private void updateSummary() {
        ValueMode mode = (ValueMode) modeCombo.getSelectedItem();

        switch (mode) {
            case FUZZ:
                FuzzStrategy strategy = spec.getFuzzStrategy();
                summaryLabel.setText("🎲 " + (strategy != null ? strategy.getDescription() : "default"));
                break;
            case FIXED:
                summaryLabel.setText("");
                break;
            case OBJECT_SPEC:
                if (spec.getNestedObjectSpec() != null) {
                    summaryLabel.setText("→ " + spec.getNestedObjectSpec().getSummary());
                } else {
                    summaryLabel.setText("(not configured)");
                }
                break;
            case NULL:
                summaryLabel.setText("null");
                break;
        }
    }

    public ParamSpec getParamSpec() {
        ValueMode mode = (ValueMode) modeCombo.getSelectedItem();
        spec.setMode(mode);

        if (mode == ValueMode.FIXED) {
            String text = valueField.getText();
            spec.setFixedValue(parseValue(text, spec.getTypeDescriptor()));
        }

        return spec;
    }

    public void loadSpec(ParamSpec newSpec) {
        this.spec = newSpec.copy();
        updateDisplay();
    }

    private Object parseValue(String text, String typeDesc) {
        if (text == null || text.isEmpty()) return null;

        try {
            switch (typeDesc) {
                case "Z": return Boolean.parseBoolean(text);
                case "B": return Byte.parseByte(text);
                case "C": return text.isEmpty() ? ' ' : text.charAt(0);
                case "S": return Short.parseShort(text);
                case "I": return Integer.parseInt(text);
                case "J": return Long.parseLong(text);
                case "F": return Float.parseFloat(text);
                case "D": return Double.parseDouble(text);
                case "Ljava/lang/String;": return text;
                default: return text;
            }
        } catch (NumberFormatException e) {
            return text;
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextPrimary());
        return label;
    }
}
