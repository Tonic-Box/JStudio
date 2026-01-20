package com.tonic.ui.dialog;

import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.service.ClassCreationService.ClassCreationParams;
import com.tonic.ui.service.ClassCreationService.ClassType;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class NewClassDialog extends ThemedJDialog {

    private final JTextField packageField;
    private final JTextField nameField;
    private final JComboBox<ClassType> typeCombo;
    private final JComboBox<String> accessCombo;
    private final JCheckBox abstractCheck;
    private final JCheckBox finalCheck;
    private final JTextField superClassField;
    private final JTextField interfacesField;
    private final JLabel superClassLabel;
    private final JLabel errorLabel;

    @Getter
    private boolean confirmed = false;

    public NewClassDialog(Window owner, String defaultPackage) {
        super(owner, "New Class", ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.setBackground(JStudioTheme.getBgPrimary());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        gbc.gridx = 0;
        gbc.gridy = row;
        JLabel pkgLabel = new JLabel("Package:");
        pkgLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(pkgLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String displayPackage = defaultPackage != null ? defaultPackage.replace('/', '.') : "";
        packageField = new JTextField(displayPackage, 25);
        styleTextField(packageField);
        content.add(packageField, gbc);

        JLabel packageHint = new JLabel("(e.g. com.example.myapp, leave empty for default package)");
        packageHint.setForeground(JStudioTheme.getTextSecondary());
        packageHint.setFont(JStudioTheme.getUIFont(10));
        row++;
        gbc.gridy = row;
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        content.add(packageHint, gbc);

        row++;
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JTextField(25);
        styleTextField(nameField);
        content.add(nameField, gbc);

        row++;
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel typeLabel = new JLabel("Type:");
        typeLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(typeLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        typeCombo = new JComboBox<>(ClassType.values());
        styleComboBox(typeCombo);
        typeCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateFieldsForType();
            }
        });
        content.add(typeCombo, gbc);

        row++;
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel accessLabel = new JLabel("Access:");
        accessLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(accessLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        accessCombo = new JComboBox<>(new String[]{"public", "package-private"});
        styleComboBox(accessCombo);
        content.add(accessCombo, gbc);

        row++;
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel modifiersLabel = new JLabel("Modifiers:");
        modifiersLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(modifiersLabel, gbc);

        JPanel modifiersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modifiersPanel.setBackground(JStudioTheme.getBgPrimary());

        abstractCheck = new JCheckBox("abstract");
        abstractCheck.setBackground(JStudioTheme.getBgPrimary());
        abstractCheck.setForeground(JStudioTheme.getTextPrimary());
        modifiersPanel.add(abstractCheck);

        finalCheck = new JCheckBox("final");
        finalCheck.setBackground(JStudioTheme.getBgPrimary());
        finalCheck.setForeground(JStudioTheme.getTextPrimary());
        modifiersPanel.add(finalCheck);

        abstractCheck.addItemListener(e -> {
            if (abstractCheck.isSelected() && finalCheck.isSelected()) {
                finalCheck.setSelected(false);
            }
        });
        finalCheck.addItemListener(e -> {
            if (finalCheck.isSelected() && abstractCheck.isSelected()) {
                abstractCheck.setSelected(false);
            }
        });

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        content.add(modifiersPanel, gbc);

        row++;
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        superClassLabel = new JLabel("Superclass:");
        superClassLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(superClassLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        superClassField = new JTextField("java.lang.Object", 25);
        styleTextField(superClassField);
        content.add(superClassField, gbc);

        row++;
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel interfacesLabel = new JLabel("Interfaces:");
        interfacesLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(interfacesLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        interfacesField = new JTextField(25);
        styleTextField(interfacesField);
        content.add(interfacesField, gbc);

        JLabel interfacesHint = new JLabel("(comma-separated, e.g. java.io.Serializable, java.lang.Comparable)");
        interfacesHint.setForeground(JStudioTheme.getTextSecondary());
        interfacesHint.setFont(JStudioTheme.getUIFont(10));
        row++;
        gbc.gridy = row;
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        content.add(interfacesHint, gbc);

        row++;
        errorLabel = new JLabel(" ");
        errorLabel.setForeground(JStudioTheme.getError());
        errorLabel.setFont(JStudioTheme.getUIFont(11));
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        content.add(errorLabel, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton cancelButton = new JButton("Cancel");
        styleButton(cancelButton, false);
        cancelButton.addActionListener(e -> dispose());

        JButton createButton = new JButton("Create");
        styleButton(createButton, true);
        createButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);

        row++;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
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
        superClassField.addKeyListener(enterListener);
        interfacesField.addKeyListener(enterListener);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);

        nameField.requestFocusInWindow();
    }

    private void updateFieldsForType() {
        ClassType type = (ClassType) typeCombo.getSelectedItem();
        if (type == null) return;

        switch (type) {
            case INTERFACE:
                abstractCheck.setSelected(false);
                abstractCheck.setEnabled(false);
                finalCheck.setSelected(false);
                finalCheck.setEnabled(false);
                superClassField.setText("java.lang.Object");
                superClassField.setEnabled(false);
                superClassLabel.setForeground(JStudioTheme.getTextSecondary());
                break;
            case ENUM:
                abstractCheck.setSelected(false);
                abstractCheck.setEnabled(false);
                finalCheck.setSelected(false);
                finalCheck.setEnabled(false);
                superClassField.setText("java.lang.Enum");
                superClassField.setEnabled(false);
                superClassLabel.setForeground(JStudioTheme.getTextSecondary());
                break;
            case ANNOTATION:
                abstractCheck.setSelected(false);
                abstractCheck.setEnabled(false);
                finalCheck.setSelected(false);
                finalCheck.setEnabled(false);
                superClassField.setText("java.lang.Object");
                superClassField.setEnabled(false);
                superClassLabel.setForeground(JStudioTheme.getTextSecondary());
                break;
            case CLASS:
            default:
                abstractCheck.setEnabled(true);
                finalCheck.setEnabled(true);
                superClassField.setEnabled(true);
                superClassLabel.setForeground(JStudioTheme.getTextPrimary());
                if ("java.lang.Enum".equals(superClassField.getText())) {
                    superClassField.setText("java.lang.Object");
                }
                break;
        }
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

    private void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(JStudioTheme.getBgSecondary());
        combo.setForeground(JStudioTheme.getTextPrimary());
        combo.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
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
        errorLabel.setText(" ");

        String pkg = packageField.getText().trim();
        if (!pkg.isEmpty()) {
            String[] pkgParts = pkg.split("\\.");
            for (String part : pkgParts) {
                if (!isValidJavaIdentifier(part)) {
                    errorLabel.setText("Invalid package name: each segment must be a valid Java identifier");
                    packageField.requestFocusInWindow();
                    return false;
                }
            }
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText("Class name cannot be empty");
            nameField.requestFocusInWindow();
            return false;
        }

        if (!isValidJavaIdentifier(name)) {
            errorLabel.setText("Invalid class name: must be a valid Java identifier");
            nameField.requestFocusInWindow();
            return false;
        }

        ClassType type = (ClassType) typeCombo.getSelectedItem();
        if (type == ClassType.CLASS) {
            if (abstractCheck.isSelected() && finalCheck.isSelected()) {
                errorLabel.setText("Cannot be both abstract and final");
                return false;
            }
        }

        String superClass = superClassField.getText().trim();
        if (!superClass.isEmpty() && !superClass.equals("java.lang.Object")) {
            String[] parts = superClass.split("\\.");
            for (String part : parts) {
                if (!isValidJavaIdentifier(part)) {
                    errorLabel.setText("Invalid superclass name");
                    superClassField.requestFocusInWindow();
                    return false;
                }
            }
        }

        String interfaces = interfacesField.getText().trim();
        if (!interfaces.isEmpty()) {
            String[] ifaceList = interfaces.split(",");
            for (String iface : ifaceList) {
                String trimmed = iface.trim();
                if (!trimmed.isEmpty()) {
                    String[] parts = trimmed.split("\\.");
                    for (String part : parts) {
                        if (!isValidJavaIdentifier(part)) {
                            errorLabel.setText("Invalid interface name: " + trimmed);
                            interfacesField.requestFocusInWindow();
                            return false;
                        }
                    }
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

    public ClassCreationParams getCreationParams() {
        String name = nameField.getText().trim();
        String pkg = packageField.getText().trim().replace('.', '/');
        String fullClassName;
        if (!pkg.isEmpty()) {
            fullClassName = pkg + "/" + name;
        } else {
            fullClassName = name;
        }

        ClassType type = (ClassType) typeCombo.getSelectedItem();
        boolean isPublic = "public".equals(accessCombo.getSelectedItem());

        String superClass = superClassField.getText().trim().replace('.', '/');
        if (superClass.isEmpty()) {
            superClass = "java/lang/Object";
        }

        List<String> interfaces = parseInterfaces();

        return ClassCreationParams.builder(fullClassName)
                .classType(type)
                .publicAccess(isPublic)
                .isAbstract(abstractCheck.isSelected())
                .isFinal(finalCheck.isSelected())
                .superClass(superClass)
                .interfaces(interfaces)
                .build();
    }

    private List<String> parseInterfaces() {
        List<String> result = new ArrayList<>();
        String text = interfacesField.getText().trim();
        if (text.isEmpty()) {
            return result;
        }

        String[] parts = text.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.replace('.', '/'));
            }
        }
        return result;
    }
}
