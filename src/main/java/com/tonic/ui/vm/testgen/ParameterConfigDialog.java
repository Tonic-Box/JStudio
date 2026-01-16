package com.tonic.ui.vm.testgen;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.testgen.objectspec.ObjectBuilderDialog;
import com.tonic.ui.vm.testgen.objectspec.ObjectSpec;
import com.tonic.ui.vm.testgen.objectspec.ParamSpec;
import com.tonic.ui.vm.testgen.objectspec.ValueMode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ParameterConfigDialog extends JDialog {

    private final List<ParamSpec> originalSpecs;
    private List<ParamSpec> resultSpecs;
    private final List<ParamRow> paramRows = new ArrayList<>();

    public ParameterConfigDialog(Window owner, List<ParamSpec> specs) {
        super(owner, "Configure Parameters", ModalityType.APPLICATION_MODAL);
        this.originalSpecs = specs;
        initComponents();
        pack();
        setMinimumSize(new Dimension(550, 400));
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        JLabel infoLabel = new JLabel("Configure how each parameter should be generated:");
        infoLabel.setForeground(JStudioTheme.getTextPrimary());
        infoLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        add(infoLabel, BorderLayout.NORTH);

        JPanel paramsPanel = new JPanel();
        paramsPanel.setLayout(new BoxLayout(paramsPanel, BoxLayout.Y_AXIS));
        paramsPanel.setBackground(JStudioTheme.getBgPrimary());

        for (int i = 0; i < originalSpecs.size(); i++) {
            ParamSpec spec = originalSpecs.get(i).copy();
            ParamRow row = new ParamRow(i, spec);
            paramRows.add(row);
            paramsPanel.add(row);
            paramsPanel.add(Box.createVerticalStrut(8));
        }

        JScrollPane scroll = new JScrollPane(paramsPanel);
        scroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        scroll.setBorder(createTitledBorder());
        add(scroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton resetButton = new JButton("Reset to Defaults");
        resetButton.addActionListener(e -> resetToDefaults());
        buttonPanel.add(resetButton);

        buttonPanel.add(Box.createHorizontalStrut(20));

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> onOk());
        buttonPanel.add(okButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            resultSpecs = null;
            dispose();
        });
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void resetToDefaults() {
        for (int i = 0; i < paramRows.size(); i++) {
            ParamRow row = paramRows.get(i);
            ParamSpec defaultSpec = new ParamSpec(originalSpecs.get(i).getName(),
                                                   originalSpecs.get(i).getTypeDescriptor());
            defaultSpec.setMode(ValueMode.FUZZ);
            row.loadSpec(defaultSpec);
        }
    }

    private void onOk() {
        resultSpecs = new ArrayList<>();
        for (ParamRow row : paramRows) {
            resultSpecs.add(row.getSpec());
        }
        dispose();
    }

    public List<ParamSpec> getResult() {
        return resultSpecs;
    }

    private TitledBorder createTitledBorder() {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Parameters",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 11),
            JStudioTheme.getTextPrimary()
        );
    }

    private class ParamRow extends JPanel {
        private ParamSpec spec;
        private final JComboBox<ValueMode> modeCombo;
        private final JTextField valueField;
        private final JButton configButton;
        private final JLabel summaryLabel;

        ParamRow(int index, ParamSpec spec) {
            this.spec = spec;

            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 3));
            setBackground(JStudioTheme.getBgSecondary());
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

            JLabel nameLabel = createLabel(spec.getName() != null ? spec.getName() : "param" + index);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            nameLabel.setPreferredSize(new Dimension(70, 20));
            add(nameLabel);

            JLabel typeLabel = createLabel("(" + spec.getSimpleTypeName() + ")");
            typeLabel.setForeground(JStudioTheme.getTextSecondary());
            typeLabel.setPreferredSize(new Dimension(80, 20));
            add(typeLabel);

            modeCombo = new JComboBox<>();
            populateModeCombo();
            modeCombo.setPreferredSize(new Dimension(130, 22));
            modeCombo.addActionListener(e -> onModeChanged());
            add(modeCombo);

            valueField = new JTextField(12);
            valueField.setVisible(false);
            add(valueField);

            configButton = new JButton("Configure...");
            configButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            configButton.setVisible(false);
            configButton.addActionListener(e -> openObjectConfig());
            add(configButton);

            summaryLabel = createLabel("");
            summaryLabel.setForeground(JStudioTheme.getInfo());
            add(summaryLabel);

            modeCombo.setSelectedItem(spec.getMode());
            updateDisplay();
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
                modeCombo.addItem(ValueMode.FUZZ);
                modeCombo.addItem(ValueMode.OBJECT_SPEC);
                modeCombo.addItem(ValueMode.NULL);
            }
        }

        private void onModeChanged() {
            ValueMode mode = (ValueMode) modeCombo.getSelectedItem();
            if (mode == null) return;

            spec.setMode(mode);
            updateDisplay();
        }

        private void updateDisplay() {
            ValueMode mode = spec.getMode();

            valueField.setVisible(mode == ValueMode.FIXED);
            configButton.setVisible(mode == ValueMode.OBJECT_SPEC);

            switch (mode) {
                case FUZZ:
                    summaryLabel.setText("🎲 auto-generate");
                    break;
                case FIXED:
                    if (spec.getFixedValue() != null) {
                        valueField.setText(String.valueOf(spec.getFixedValue()));
                    }
                    summaryLabel.setText("");
                    break;
                case OBJECT_SPEC:
                    if (spec.getNestedObjectSpec() != null) {
                        summaryLabel.setText("-> " + spec.getNestedObjectSpec().getSummary());
                    } else {
                        summaryLabel.setText("(click Configure)");
                    }
                    break;
                case NULL:
                    summaryLabel.setText("null");
                    break;
            }

            revalidate();
        }

        private void openObjectConfig() {
            String typeName = spec.getTypeDescriptor();
            if (typeName.startsWith("L") && typeName.endsWith(";")) {
                typeName = typeName.substring(1, typeName.length() - 1);
            }

            ObjectSpec existing = spec.getNestedObjectSpec();
            ObjectSpec result = ObjectBuilderDialog.showDialog(
                ParameterConfigDialog.this, typeName, existing);

            if (result != null) {
                spec.setNestedObjectSpec(result);
                updateDisplay();
            }
        }

        void loadSpec(ParamSpec newSpec) {
            this.spec = newSpec;
            modeCombo.setSelectedItem(newSpec.getMode());
            updateDisplay();
        }

        ParamSpec getSpec() {
            ValueMode mode = (ValueMode) modeCombo.getSelectedItem();
            spec.setMode(mode);

            if (mode == ValueMode.FIXED) {
                String text = valueField.getText();
                spec.setFixedValue(parseValue(text));
            }

            return spec;
        }

        private Object parseValue(String text) {
            if (text == null || text.isEmpty()) return null;
            String typeDesc = spec.getTypeDescriptor();

            try {
                switch (typeDesc) {
                    case "Z": return Boolean.parseBoolean(text);
                    case "B": return Byte.parseByte(text);
                    case "C": return text.charAt(0);
                    case "S": return Short.parseShort(text);
                    case "I": return Integer.parseInt(text);
                    case "J": return Long.parseLong(text);
                    case "F": return Float.parseFloat(text);
                    case "D": return Double.parseDouble(text);
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
}
