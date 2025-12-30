package com.tonic.ui.dialog;

import com.tonic.ui.theme.AbstractTheme;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;
import com.tonic.ui.util.Settings;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

public class PreferencesDialog extends JDialog implements ThemeManager.ThemeChangeListener {

    private JComboBox<String> fontComboBox;
    private JSpinner fontSizeSpinner;
    private JTextPane previewPane;
    private JScrollPane previewScrollPane;
    private JComboBox<Theme> themeComboBox;
    private JCheckBox loadJdkClassesBox;

    private final JPanel mainPanel;
    private final JPanel editorPanel;
    private final JPanel appearancePanel;
    private final JPanel executionPanel;
    private final JPanel previewPanel;
    private final JPanel buttonPanel;

    private Runnable onApply;

    public PreferencesDialog(Frame owner) {
        super(owner, "Preferences", true);

        setSize(500, 580);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        mainPanel.setBackground(JStudioTheme.getBgSecondary());

        editorPanel = createEditorSection();
        appearancePanel = createAppearanceSection();
        executionPanel = createExecutionSection();
        previewPanel = createPreviewSection();
        buttonPanel = createButtonPanel();

        mainPanel.add(editorPanel);
        mainPanel.add(Box.createVerticalStrut(16));
        mainPanel.add(appearancePanel);
        mainPanel.add(Box.createVerticalStrut(16));
        mainPanel.add(executionPanel);
        mainPanel.add(Box.createVerticalStrut(16));
        mainPanel.add(previewPanel);
        mainPanel.add(Box.createVerticalGlue());

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        ThemeManager.getInstance().addThemeChangeListener(this);

        loadSettings();
        updatePreview();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        mainPanel.setBackground(JStudioTheme.getBgSecondary());

        updatePanelTheme(editorPanel, "Editor Font");
        updatePanelTheme(appearancePanel, "Appearance");
        updatePanelTheme(executionPanel, "Execution");
        updatePanelTheme(previewPanel, "Preview");

        buttonPanel.setBackground(JStudioTheme.getBgSecondary());
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        if (loadJdkClassesBox != null) {
            loadJdkClassesBox.setBackground(JStudioTheme.getBgSecondary());
            loadJdkClassesBox.setForeground(JStudioTheme.getTextPrimary());
        }

        if (fontComboBox != null) {
            fontComboBox.setBackground(JStudioTheme.getBgTertiary());
            fontComboBox.setForeground(JStudioTheme.getTextPrimary());
        }

        if (themeComboBox != null) {
            themeComboBox.setBackground(JStudioTheme.getBgTertiary());
            themeComboBox.setForeground(JStudioTheme.getTextPrimary());
        }

        applyThemeToLabels(mainPanel);
        revalidate();
        repaint();
    }

    private void updatePanelTheme(JPanel panel, String title) {
        if (panel != null) {
            panel.setBackground(JStudioTheme.getBgSecondary());
            panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                title,
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                JStudioTheme.getUIFont(12),
                JStudioTheme.getTextPrimary()
            ));
        }
    }

    private void applyThemeToLabels(Component component) {
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            Color fg = label.getForeground();
            if (fg != null && !fg.equals(JStudioTheme.getAccent())) {
                if (isSecondaryLabel(label)) {
                    label.setForeground(JStudioTheme.getTextSecondary());
                } else {
                    label.setForeground(JStudioTheme.getTextPrimary());
                }
            }
        } else if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                applyThemeToLabels(child);
            }
        }
    }

    private boolean isSecondaryLabel(JLabel label) {
        String text = label.getText();
        return text != null && (text.contains("take effect") || text.startsWith("Changes"));
    }

    @Override
    public void dispose() {
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.dispose();
    }

    private JPanel createEditorSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Editor Font",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            JStudioTheme.getUIFont(12),
            JStudioTheme.getTextPrimary()
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel fontLabel = new JLabel("Font Family:");
        fontLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(fontLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        fontComboBox = new JComboBox<>();
        fontComboBox.setBackground(JStudioTheme.getBgTertiary());
        fontComboBox.setForeground(JStudioTheme.getTextPrimary());

        fontComboBox.addItem("(Default)");
        List<String> monoFonts = AbstractTheme.getAvailableMonospaceFonts();
        for (String font : monoFonts) {
            fontComboBox.addItem(font);
        }

        fontComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null && !value.equals("(Default)")) {
                    setFont(new Font((String) value, Font.PLAIN, 12));
                }
                return this;
            }
        });

        fontComboBox.addActionListener(e -> updatePreview());
        panel.add(fontComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel sizeLabel = new JLabel("Font Size:");
        sizeLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(sizeLabel, gbc);

        gbc.gridx = 1;
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(13, 8, 32, 1));
        fontSizeSpinner.setPreferredSize(new Dimension(80, 25));
        fontSizeSpinner.addChangeListener(e -> updatePreview());
        panel.add(fontSizeSpinner, gbc);

        return panel;
    }

    private JPanel createAppearanceSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Appearance",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            JStudioTheme.getUIFont(12),
            JStudioTheme.getTextPrimary()
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel themeLabel = new JLabel("Theme:");
        themeLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(themeLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        themeComboBox = new JComboBox<>();
        themeComboBox.setBackground(JStudioTheme.getBgTertiary());
        themeComboBox.setForeground(JStudioTheme.getTextPrimary());

        for (Theme theme : ThemeManager.getInstance().getAvailableThemes()) {
            themeComboBox.addItem(theme);
        }

        themeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Theme) {
                    setText(((Theme) value).getDisplayName());
                }
                return this;
            }
        });

        themeComboBox.addActionListener(e -> updatePreview());
        panel.add(themeComboBox, gbc);

        return panel;
    }

    private JPanel createExecutionSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Execution",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            JStudioTheme.getUIFont(12),
            JStudioTheme.getTextPrimary()
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        loadJdkClassesBox = new JCheckBox("Load JDK classes (enables stepping into standard library)");
        loadJdkClassesBox.setBackground(JStudioTheme.getBgSecondary());
        loadJdkClassesBox.setForeground(JStudioTheme.getTextPrimary());
        panel.add(loadJdkClassesBox, gbc);

        gbc.gridy = 1;
        JLabel noteLabel = new JLabel("Changes take effect on next project load");
        noteLabel.setForeground(JStudioTheme.getTextSecondary());
        noteLabel.setFont(noteLabel.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(noteLabel, gbc);

        return panel;
    }

    private JPanel createPreviewSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Preview",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            JStudioTheme.getUIFont(12),
            JStudioTheme.getTextPrimary()
        ));

        previewPane = new JTextPane();
        previewPane.setEditable(false);
        previewPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        previewScrollPane = new JScrollPane(previewPane);
        previewScrollPane.setPreferredSize(new Dimension(0, 140));
        previewScrollPane.setBorder(null);
        panel.add(previewScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        panel.add(cancelButton);

        JButton applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySettings());
        panel.add(applyButton);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            applySettings();
            dispose();
        });
        panel.add(okButton);

        return panel;
    }

    private void loadSettings() {
        Settings settings = Settings.getInstance();

        String fontFamily = settings.getFontFamily();
        if (fontFamily == null || fontFamily.isEmpty()) {
            fontComboBox.setSelectedIndex(0);
        } else {
            fontComboBox.setSelectedItem(fontFamily);
        }

        fontSizeSpinner.setValue(settings.getFontSize());

        Theme currentTheme = ThemeManager.getInstance().getCurrentTheme();
        themeComboBox.setSelectedItem(currentTheme);

        loadJdkClassesBox.setSelected(settings.isLoadJdkClassesEnabled());
    }

    private void updatePreview() {
        if (previewPane == null || fontComboBox == null || fontSizeSpinner == null || themeComboBox == null) {
            return;
        }

        Theme selectedTheme = (Theme) themeComboBox.getSelectedItem();
        if (selectedTheme == null) {
            selectedTheme = ThemeManager.getInstance().getCurrentTheme();
        }

        String selectedFont = (String) fontComboBox.getSelectedItem();
        int size = (Integer) fontSizeSpinner.getValue();

        Font previewFont;
        if (selectedFont == null || selectedFont.equals("(Default)")) {
            previewFont = selectedTheme.getCodeFont(size);
        } else {
            previewFont = new Font(selectedFont, Font.PLAIN, size);
        }

        previewPane.setBackground(selectedTheme.getBgTertiary());
        previewPane.setCaretColor(selectedTheme.getTextPrimary());
        previewScrollPane.getViewport().setBackground(selectedTheme.getBgTertiary());

        StyledDocument doc = previewPane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            // ignore
        }

        appendStyledCode(doc, selectedTheme, previewFont);
    }

    private void appendStyledCode(StyledDocument doc, Theme theme, Font font) {
        try {
            appendText(doc, "public", theme.getJavaKeyword(), font);
            appendText(doc, " ", theme.getTextPrimary(), font);
            appendText(doc, "class", theme.getJavaKeyword(), font);
            appendText(doc, " ", theme.getTextPrimary(), font);
            appendText(doc, "HelloWorld", theme.getJavaType(), font);
            appendText(doc, " {\n", theme.getTextPrimary(), font);

            appendText(doc, "    ", theme.getTextPrimary(), font);
            appendText(doc, "public", theme.getJavaKeyword(), font);
            appendText(doc, " ", theme.getTextPrimary(), font);
            appendText(doc, "static", theme.getJavaKeyword(), font);
            appendText(doc, " ", theme.getTextPrimary(), font);
            appendText(doc, "void", theme.getJavaKeyword(), font);
            appendText(doc, " ", theme.getTextPrimary(), font);
            appendText(doc, "main", theme.getJavaMethod(), font);
            appendText(doc, "(", theme.getTextPrimary(), font);
            appendText(doc, "String", theme.getJavaType(), font);
            appendText(doc, "[] args) {\n", theme.getTextPrimary(), font);

            appendText(doc, "        ", theme.getTextPrimary(), font);
            appendText(doc, "// Print greeting\n", theme.getJavaComment(), font);

            appendText(doc, "        System.out.", theme.getTextPrimary(), font);
            appendText(doc, "println", theme.getJavaMethod(), font);
            appendText(doc, "(", theme.getTextPrimary(), font);
            appendText(doc, "\"Hello, World!\"", theme.getJavaString(), font);
            appendText(doc, ");\n", theme.getTextPrimary(), font);

            appendText(doc, "        ", theme.getTextPrimary(), font);
            appendText(doc, "int", theme.getJavaKeyword(), font);
            appendText(doc, " count = ", theme.getTextPrimary(), font);
            appendText(doc, "42", theme.getJavaNumber(), font);
            appendText(doc, ";\n", theme.getTextPrimary(), font);

            appendText(doc, "    }\n", theme.getTextPrimary(), font);
            appendText(doc, "}", theme.getTextPrimary(), font);
        } catch (BadLocationException e) {
            // ignore
        }
    }

    private void appendText(StyledDocument doc, String text, Color color, Font font) throws BadLocationException {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, font.getFamily());
        StyleConstants.setFontSize(style, font.getSize());
        doc.insertString(doc.getLength(), text, style);
    }

    private void applySettings() {
        Settings settings = Settings.getInstance();

        String selectedFont = (String) fontComboBox.getSelectedItem();
        if (selectedFont != null && selectedFont.equals("(Default)")) {
            settings.setFontFamily("");
        } else {
            settings.setFontFamily(selectedFont);
        }

        settings.setFontSize((Integer) fontSizeSpinner.getValue());

        Theme selectedTheme = (Theme) themeComboBox.getSelectedItem();
        if (selectedTheme != null) {
            ThemeManager.getInstance().setTheme(selectedTheme.getName());
        }

        settings.setLoadJdkClassesEnabled(loadJdkClassesBox.isSelected());

        if (onApply != null) {
            SwingUtilities.invokeLater(onApply);
        }
    }

    public void setOnApply(Runnable onApply) {
        this.onApply = onApply;
    }
}
