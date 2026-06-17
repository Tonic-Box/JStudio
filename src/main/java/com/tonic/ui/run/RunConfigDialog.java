package com.tonic.ui.run;

import com.tonic.service.run.Jdk;
import com.tonic.service.run.JdkDetector;
import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.util.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A small modal dialog gathering a run configuration (JDK, program arguments, VM options, working directory)
 * before launching a {@code main} class. Theme-aware; last-used values persist via {@link Settings}.
 */
public final class RunConfigDialog extends ThemedJDialog {

    /** A resolved run configuration. */
    public static final class RunConfig {
        public final List<String> programArgs;
        public final List<String> vmOptions;
        public final File workingDir;
        public final File javaHome;
        public final int javaFeature;

        RunConfig(List<String> programArgs, List<String> vmOptions, File workingDir, File javaHome, int javaFeature) {
            this.programArgs = programArgs;
            this.vmOptions = vmOptions;
            this.workingDir = workingDir;
            this.javaHome = javaHome;
            this.javaFeature = javaFeature;
        }
    }

    private final JComboBox<Jdk> jdkCombo = new JComboBox<>();
    private final JTextField argsField = new JTextField(28);
    private final JTextField vmField = new JTextField(28);
    private final JTextField dirField = new JTextField(28);
    private RunConfig result;

    private RunConfigDialog(Frame owner, String className, File defaultWorkingDir) {
        super(owner, "Run " + className, true);

        Settings settings = Settings.getInstance();
        populateJdks(settings.getRunJdkHome());
        argsField.setText(settings.getRunProgramArgs());
        vmField.setText(settings.getRunVmOptions());
        String savedDir = settings.getRunWorkingDir();
        dirField.setText(!savedDir.isEmpty() ? savedDir
                : defaultWorkingDir != null ? defaultWorkingDir.getAbsolutePath() : "");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        JButton browseJdk = trailingButton("...", e -> chooseJdk());
        addRow(form, 0, "JRE / JDK:", jdkCombo, browseJdk);
        addRow(form, 1, "Program arguments:", argsField, null);
        addRow(form, 2, "VM options:", vmField, null);
        addRow(form, 3, "Working directory:", dirField, trailingButton("...", e -> chooseDir()));

        JButton run = new JButton("Run");
        JButton cancel = new JButton("Cancel");
        run.addActionListener(e -> onRun());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(run);
        buttons.add(cancel);
        getRootPane().setDefaultButton(run);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(JStudioTheme.getBgPrimary());
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the chosen config, or null if cancelled. */
    public static RunConfig show(Frame owner, String className, File defaultWorkingDir) {
        RunConfigDialog dialog = new RunConfigDialog(owner, className, defaultWorkingDir);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void populateJdks(String savedHome) {
        List<Jdk> jdks = JdkDetector.detect();
        Jdk selected = null;
        if (savedHome != null && !savedHome.isEmpty()) {
            for (Jdk jdk : jdks) {
                if (jdk.getHome().getAbsolutePath().equals(savedHome)) {
                    selected = jdk;
                    break;
                }
            }
            if (selected == null) {
                Jdk custom = JdkDetector.fromHome(new File(savedHome));
                if (custom != null) {
                    jdks.add(custom);
                    selected = custom;
                }
            }
        }
        for (Jdk jdk : jdks) {
            jdkCombo.addItem(jdk);
        }
        jdkCombo.setSelectedItem(selected != null ? selected : (jdks.isEmpty() ? null : jdks.get(0)));
    }

    private void chooseJdk() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select a JDK/JRE home directory");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Jdk jdk = JdkDetector.fromHome(chooser.getSelectedFile());
        if (jdk == null) {
            JOptionPane.showMessageDialog(this, "No java executable under that directory's bin folder.",
                    "Not a JDK/JRE", JOptionPane.WARNING_MESSAGE);
            return;
        }
        jdkCombo.addItem(jdk);
        jdkCombo.setSelectedItem(jdk);
    }

    private void chooseDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!dirField.getText().isEmpty()) {
            chooser.setCurrentDirectory(new File(dirField.getText()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JButton trailingButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.addActionListener(action);
        return button;
    }

    private void addRow(JPanel form, int row, String label, JComponent field, JButton trailing) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(JStudioTheme.getTextSecondary());
        form.add(labelComponent, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        if (trailing != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(trailing, c);
        }
    }

    private void onRun() {
        Jdk jdk = (Jdk) jdkCombo.getSelectedItem();
        Settings settings = Settings.getInstance();
        settings.setRunProgramArgs(argsField.getText());
        settings.setRunVmOptions(vmField.getText());
        settings.setRunWorkingDir(dirField.getText());
        if (jdk != null) {
            settings.setRunJdkHome(jdk.getHome().getAbsolutePath());
        }
        File dir = dirField.getText().trim().isEmpty() ? null : new File(dirField.getText().trim());
        result = new RunConfig(tokenize(argsField.getText()), tokenize(vmField.getText()), dir,
                jdk != null ? jdk.getHome() : null, jdk != null ? jdk.getFeature() : 0);
        dispose();
    }

    /** Splits a command-line string into tokens, honoring double quotes (so paths with spaces stay intact). */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean has = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                has = true;
            } else if (Character.isWhitespace(ch) && !inQuotes) {
                if (has) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    has = false;
                }
            } else {
                current.append(ch);
                has = true;
            }
        }
        if (has) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 520), d.height);
    }
}
