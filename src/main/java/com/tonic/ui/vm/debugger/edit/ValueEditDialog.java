package com.tonic.ui.vm.debugger.edit;

import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ValueEditDialog extends JDialog {

    private final JTextField valueField;
    private final JLabel errorLabel;
    private final ValueTag valueTag;
    @Getter
    private ConcreteValue result;
    @Getter
    private boolean confirmed;

    public ValueEditDialog(Window owner, String title, String currentValue, ValueTag tag) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.valueTag = tag;
        this.confirmed = false;
        this.result = null;

        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel typeLabel = new JLabel("Type: " + ValueParser.formatTypeName(tag));
        typeLabel.setForeground(JStudioTheme.getTextSecondary());
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        centerPanel.add(typeLabel, gbc);

        JLabel inputLabel = new JLabel("Value:");
        inputLabel.setForeground(JStudioTheme.getTextPrimary());
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        centerPanel.add(inputLabel, gbc);

        valueField = new JTextField(currentValue, 25);
        valueField.setBackground(JStudioTheme.getBgSecondary());
        valueField.setForeground(JStudioTheme.getTextPrimary());
        valueField.setCaretColor(JStudioTheme.getTextPrimary());
        valueField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        valueField.selectAll();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(valueField, gbc);

        JLabel hintLabel = createHintLabel(tag);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        centerPanel.add(hintLabel, gbc);

        errorLabel = new JLabel(" ");
        errorLabel.setForeground(JStudioTheme.getError());
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        centerPanel.add(errorLabel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);

        JButton okButton = createButton("OK");
        JButton cancelButton = createButton("Cancel");

        okButton.addActionListener(e -> tryConfirm());
        cancelButton.addActionListener(e -> cancel());

        valueField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    tryConfirm();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                errorLabel.setText(" ");
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(400, 180));
        setLocationRelativeTo(owner);
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        return button;
    }

    private JLabel createHintLabel(ValueTag tag) {
        String hint;
        switch (tag) {
            case INT:
                hint = "Accepts: decimal, 0x hex, 0b binary, 'c' char, true/false";
                break;
            case LONG:
                hint = "Accepts: decimal, 0x hex, 0b binary (optional L suffix)";
                break;
            case FLOAT:
            case DOUBLE:
                hint = "Accepts: decimal, scientific (1.5e10), NaN, Infinity";
                break;
            case REFERENCE:
            case NULL:
                hint = "Only 'null' is allowed for references";
                break;
            default:
                hint = "";
        }
        JLabel label = new JLabel(hint);
        label.setForeground(JStudioTheme.getTextSecondary());
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 10f));
        return label;
    }

    private void tryConfirm() {
        try {
            result = ValueParser.parse(valueField.getText(), valueTag);
            confirmed = true;
            dispose();
        } catch (ValueParseException e) {
            errorLabel.setText(e.getMessage());
            valueField.selectAll();
            valueField.requestFocus();
        }
    }

    private void cancel() {
        confirmed = false;
        result = null;
        dispose();
    }

    public static ConcreteValue showDialog(Component parent, String title,
            String currentValue, ValueTag tag) {
        Window window = SwingUtilities.getWindowAncestor(parent);
        ValueEditDialog dialog = new ValueEditDialog(window, title, currentValue, tag);
        dialog.setVisible(true);
        return dialog.isConfirmed() ? dialog.getResult() : null;
    }
}
