package com.tonic.ui.dialog;

import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class RenameClassDialog extends ThemedJDialog {

    private final JTextField packageField;
    private final JTextField nameField;
    private boolean confirmed = false;

    public RenameClassDialog(Window owner, String currentClassName) {
        super(owner, "Rename Class", ModalityType.APPLICATION_MODAL);

        String currentPackage = "";
        String currentSimpleName = currentClassName;
        int lastSlash = currentClassName.lastIndexOf('/');
        if (lastSlash >= 0) {
            currentPackage = currentClassName.substring(0, lastSlash);
            currentSimpleName = currentClassName.substring(lastSlash + 1);
        }

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.setBackground(JStudioTheme.getBgPrimary());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel currentLabel = new JLabel("Current: " + currentClassName.replace('/', '.'));
        currentLabel.setForeground(JStudioTheme.getTextSecondary());
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        content.add(currentLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        JLabel packageLabel = new JLabel("Package:");
        packageLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(packageLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        packageField = new JTextField(currentPackage.replace('/', '.'), 30);
        styleTextField(packageField);
        content.add(packageField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel nameLabel = new JLabel("Class Name:");
        nameLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JTextField(currentSimpleName, 30);
        styleTextField(nameField);
        content.add(nameField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton cancelButton = new JButton("Cancel");
        styleButton(cancelButton, false);
        cancelButton.addActionListener(e -> dispose());

        JButton renameButton = new JButton("Rename");
        styleButton(renameButton, true);
        renameButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(renameButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(15, 5, 5, 5);
        content.add(buttonPanel, gbc);

        KeyAdapter enterListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (validateInput()) {
                        confirmed = true;
                        dispose();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        };
        packageField.addKeyListener(enterListener);
        nameField.addKeyListener(enterListener);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);

        nameField.selectAll();
        nameField.requestFocusInWindow();
    }

    private void styleTextField(JTextField field) {
        field.setBackground(JStudioTheme.getBgSecondary());
        field.setForeground(JStudioTheme.getTextPrimary());
        field.setCaretColor(JStudioTheme.getTextPrimary());
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        field.setFont(JStudioTheme.getCodeFont(12));
    }

    private void styleButton(JButton button, boolean primary) {
        if (primary) {
            button.setBackground(JStudioTheme.getAccent());
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(JStudioTheme.getBgSecondary());
            button.setForeground(JStudioTheme.getTextPrimary());
        }
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
    }

    private boolean validateInput() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Class name cannot be empty",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!isValidJavaIdentifier(name)) {
            JOptionPane.showMessageDialog(this, "Invalid class name: " + name,
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        String pkg = packageField.getText().trim();
        if (!pkg.isEmpty()) {
            for (String part : pkg.split("\\.")) {
                if (!isValidJavaIdentifier(part)) {
                    JOptionPane.showMessageDialog(this, "Invalid package name: " + pkg,
                            "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isValidJavaIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getNewClassName() {
        String pkg = packageField.getText().trim().replace('.', '/');
        String name = nameField.getText().trim();
        if (pkg.isEmpty()) {
            return name;
        }
        return pkg + "/" + name;
    }
}
