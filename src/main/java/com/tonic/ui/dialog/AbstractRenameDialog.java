package com.tonic.ui.dialog;

import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.ThemeStyles;
import lombok.Getter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Base for the simple single-field rename dialogs (field, method): a "Current: …" label, a styled name input,
 * Cancel/Rename buttons, and Enter/Escape handling. Subclasses supply the title, the current-value label, the
 * initial name, and the entity word used in validation messages.
 */
public abstract class AbstractRenameDialog extends ThemedJDialog {

    private final JTextField nameField;
    @Getter
    private boolean confirmed;

    protected AbstractRenameDialog(Window owner, String title, String currentLabelText, String initialName) {
        super(owner, title, ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.setBackground(JStudioTheme.getBgPrimary());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel currentLabel = new JLabel(currentLabelText);
        currentLabel.setForeground(JStudioTheme.getTextSecondary());
        currentLabel.setFont(JStudioTheme.getCodeFont(11));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        content.add(currentLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        JLabel nameLabel = new JLabel("New Name:");
        nameLabel.setForeground(JStudioTheme.getTextPrimary());
        content.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JTextField(initialName, 25);
        ThemeStyles.styleTextField(nameField);
        content.add(nameField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton cancelButton = new JButton("Cancel");
        ThemeStyles.styleButton(cancelButton, false);
        cancelButton.addActionListener(e -> dispose());

        JButton renameButton = new JButton("Rename");
        ThemeStyles.styleButton(renameButton, true);
        renameButton.addActionListener(e -> confirmIfValid());

        buttonPanel.add(cancelButton);
        buttonPanel.add(renameButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(15, 5, 5, 5);
        content.add(buttonPanel, gbc);

        nameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    confirmIfValid();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);

        nameField.selectAll();
        nameField.requestFocusInWindow();
    }

    private void confirmIfValid() {
        if (validateName()) {
            confirmed = true;
            dispose();
        }
    }

    /** The entity word used in validation messages, e.g. {@code "field"} or {@code "method"}. */
    protected abstract String entityWord();

    /** Validates the entered name (empty / valid Java identifier), showing an error dialog on failure. */
    protected boolean validateName() {
        String name = getNewName();
        if (name.isEmpty()) {
            showError(capitalize(entityWord()) + " name cannot be empty");
            return false;
        }
        if (!isValidJavaIdentifier(name)) {
            showError("Invalid " + entityWord() + " name: " + name);
            return false;
        }
        return true;
    }

    protected void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
    }

    public String getNewName() {
        return nameField.getText().trim();
    }

    /** Whether {@code s} is a valid Java identifier (shared by the rename dialogs). */
    public static boolean isValidJavaIdentifier(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
