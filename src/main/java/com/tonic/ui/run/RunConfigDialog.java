package com.tonic.ui.run;

import com.tonic.util.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A small modal dialog gathering a run configuration (program arguments, VM options, working directory) before
 * launching a {@code main} class. Last-used values persist via {@link Settings}.
 */
public final class RunConfigDialog extends JDialog {

    /** A resolved run configuration. */
    public static final class RunConfig {
        public final List<String> programArgs;
        public final List<String> vmOptions;
        public final File workingDir;

        RunConfig(List<String> programArgs, List<String> vmOptions, File workingDir) {
            this.programArgs = programArgs;
            this.vmOptions = vmOptions;
            this.workingDir = workingDir;
        }
    }

    private final JTextField argsField = new JTextField(28);
    private final JTextField vmField = new JTextField(28);
    private final JTextField dirField = new JTextField(28);
    private RunConfig result;

    private RunConfigDialog(Frame owner, String className, File defaultWorkingDir) {
        super(owner, "Run " + className, true);

        Settings settings = Settings.getInstance();
        argsField.setText(settings.getRunProgramArgs());
        vmField.setText(settings.getRunVmOptions());
        String savedDir = settings.getRunWorkingDir();
        dirField.setText(!savedDir.isEmpty() ? savedDir
                : defaultWorkingDir != null ? defaultWorkingDir.getAbsolutePath() : "");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        addRow(form, 0, "Program arguments:", argsField, null);
        addRow(form, 1, "VM options:", vmField, null);
        JButton browse = new JButton("...");
        browse.setFocusable(false);
        browse.addActionListener(e -> chooseDir());
        addRow(form, 2, "Working directory:", dirField, browse);

        JButton run = new JButton("Run");
        JButton cancel = new JButton("Cancel");
        run.addActionListener(e -> onRun());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel();
        buttons.add(run);
        buttons.add(cancel);
        getRootPane().setDefaultButton(run);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the chosen config, or null if cancelled. */
    public static RunConfig show(Frame owner, String className, File defaultWorkingDir) {
        RunConfigDialog dialog = new RunConfigDialog(owner, className, defaultWorkingDir);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void addRow(JPanel form, int row, String label, JTextField field, JButton trailing) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), c);
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

    private void onRun() {
        Settings settings = Settings.getInstance();
        settings.setRunProgramArgs(argsField.getText());
        settings.setRunVmOptions(vmField.getText());
        settings.setRunWorkingDir(dirField.getText());
        File dir = dirField.getText().trim().isEmpty() ? null : new File(dirField.getText().trim());
        result = new RunConfig(tokenize(argsField.getText()), tokenize(vmField.getText()), dir);
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
        return new Dimension(Math.max(d.width, 480), d.height);
    }
}
