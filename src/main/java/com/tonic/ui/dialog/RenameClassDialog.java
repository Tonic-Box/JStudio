package com.tonic.ui.dialog;

import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.ThemeStyles;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class RenameClassDialog extends ThemedJDialog {

    private final JTextField packageField;
    private final JTextField nameField;
    @Getter
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
        ThemeStyles.styleTextField(packageField);
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
        ThemeStyles.styleTextField(nameField);
        content.add(nameField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton cancelButton = new JButton("Cancel");
        ThemeStyles.styleButton(cancelButton, false);
        cancelButton.addActionListener(e -> dispose());

        JButton renameButton = new JButton("Rename");
        ThemeStyles.styleButton(renameButton, true);
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

    private boolean validateInput() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("Class name cannot be empty");
            return false;
        }
        if (!AbstractRenameDialog.isValidJavaIdentifier(name)) {
            showError("Invalid class name: " + name);
            return false;
        }
        String pkg = packageField.getText().trim();
        if (!pkg.isEmpty()) {
            for (String part : pkg.split("\\.")) {
                if (!AbstractRenameDialog.isValidJavaIdentifier(part)) {
                    showError("Invalid package name: " + pkg);
                    return false;
                }
            }
        }
        return true;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
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
