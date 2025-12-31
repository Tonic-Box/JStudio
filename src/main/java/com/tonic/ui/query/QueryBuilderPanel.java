package com.tonic.ui.query;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class QueryBuilderPanel extends ThemedJPanel {

    private JComboBox<String> targetCombo;
    private JComboBox<String> scopeTypeCombo;
    private JTextField scopePatternField;
    private JPanel predicatesPanel;
    private final List<PredicateRow> predicateRows = new ArrayList<>();
    private JSpinner limitSpinner;
    private JCheckBox useLimitCheck;

    private Consumer<String> queryChangeListener;

    public QueryBuilderPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM));
        setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM));

        add(createTopSection(), BorderLayout.NORTH);
        add(createPredicatesSection(), BorderLayout.CENTER);
        add(createBottomSection(), BorderLayout.SOUTH);
    }

    public void setQueryChangeListener(Consumer<String> listener) {
        this.queryChangeListener = listener;
    }

    private JPanel createTopSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Find:"), gbc);

        gbc.gridx = 1;
        targetCombo = new JComboBox<>(new String[]{"methods", "classes", "events", "strings", "objects"});
        targetCombo.setPreferredSize(new Dimension(120, 26));
        targetCombo.addActionListener(e -> fireQueryChanged());
        panel.add(targetCombo, gbc);

        gbc.gridx = 2;
        panel.add(Box.createHorizontalStrut(20), gbc);

        gbc.gridx = 3;
        panel.add(new JLabel("Scope:"), gbc);

        gbc.gridx = 4;
        scopeTypeCombo = new JComboBox<>(new String[]{"all", "class", "method", "during <clinit>"});
        scopeTypeCombo.setPreferredSize(new Dimension(120, 26));
        scopeTypeCombo.addActionListener(e -> {
            updateScopePatternVisibility();
            fireQueryChanged();
        });
        panel.add(scopeTypeCombo, gbc);

        gbc.gridx = 5;
        scopePatternField = new JTextField(20);
        scopePatternField.setPreferredSize(new Dimension(200, 26));
        scopePatternField.setToolTipText("Pattern (e.g., com/example/.*)");
        scopePatternField.getDocument().addDocumentListener(new SimpleDocumentListener(this::fireQueryChanged));
        panel.add(scopePatternField, gbc);

        gbc.gridx = 6; gbc.weightx = 1.0;
        panel.add(Box.createHorizontalGlue(), gbc);

        updateScopePatternVisibility();
        return panel;
    }

    private void updateScopePatternVisibility() {
        String scopeType = (String) scopeTypeCombo.getSelectedItem();
        boolean showPattern = "class".equals(scopeType) || "method".equals(scopeType);
        scopePatternField.setVisible(showPattern);
        scopePatternField.setEnabled(showPattern);
    }

    private JPanel createPredicatesSection() {
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBorder(BorderFactory.createTitledBorder("WHERE Conditions"));

        predicatesPanel = new JPanel();
        predicatesPanel.setLayout(new BoxLayout(predicatesPanel, BoxLayout.Y_AXIS));

        addPredicateRow();

        JScrollPane scrollPane = new JScrollPane(predicatesPanel);
        scrollPane.setPreferredSize(new Dimension(800, 200));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        outerPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("+ Add Condition");
        addButton.addActionListener(e -> addPredicateRow());
        buttonPanel.add(addButton);
        outerPanel.add(buttonPanel, BorderLayout.SOUTH);

        return outerPanel;
    }

    private JPanel createBottomSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        useLimitCheck = new JCheckBox("LIMIT");
        useLimitCheck.addActionListener(e -> {
            limitSpinner.setEnabled(useLimitCheck.isSelected());
            fireQueryChanged();
        });
        panel.add(useLimitCheck);

        limitSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 1000, 10));
        limitSpinner.setEnabled(false);
        limitSpinner.setPreferredSize(new Dimension(80, 26));
        limitSpinner.addChangeListener(e -> fireQueryChanged());
        panel.add(limitSpinner);

        panel.add(Box.createHorizontalStrut(30));

        JLabel previewLabel = new JLabel("Generated query shown in DSL tab");
        previewLabel.setFont(previewLabel.getFont().deriveFont(Font.ITALIC));
        panel.add(previewLabel);

        return panel;
    }

    private void addPredicateRow() {
        PredicateRow row = new PredicateRow(predicateRows.size(), this::removePredicateRow, this::fireQueryChanged);
        predicateRows.add(row);
        predicatesPanel.add(row);
        predicatesPanel.revalidate();
        predicatesPanel.repaint();
        fireQueryChanged();
    }

    private void removePredicateRow(PredicateRow row) {
        if (predicateRows.size() <= 1) return;
        predicateRows.remove(row);
        predicatesPanel.remove(row);
        for (int i = 0; i < predicateRows.size(); i++) {
            predicateRows.get(i).updateIndex(i);
        }
        predicatesPanel.revalidate();
        predicatesPanel.repaint();
        fireQueryChanged();
    }

    private void fireQueryChanged() {
        if (queryChangeListener != null) {
            queryChangeListener.accept(buildQuery());
        }
    }

    public String buildQuery() {
        StringBuilder query = new StringBuilder();

        query.append("FIND ").append(targetCombo.getSelectedItem());

        String scopeType = (String) scopeTypeCombo.getSelectedItem();
        if (!"all".equals(scopeType)) {
            if ("during <clinit>".equals(scopeType)) {
                query.append(" DURING <clinit>");
            } else {
                String pattern = scopePatternField.getText().trim();
                if (!pattern.isEmpty()) {
                    query.append(" IN ").append(scopeType).append(" \"").append(pattern).append("\"");
                }
            }
        }

        List<String> predicates = new ArrayList<>();
        for (PredicateRow row : predicateRows) {
            String pred = row.buildPredicate();
            if (pred != null && !pred.isEmpty()) {
                predicates.add(pred);
            }
        }

        if (!predicates.isEmpty()) {
            query.append(" WHERE ");
            for (int i = 0; i < predicates.size(); i++) {
                if (i > 0) {
                    String connector = predicateRows.get(i).getConnector();
                    query.append(" ").append(connector).append(" ");
                }
                query.append(predicates.get(i));
            }
        }

        if (useLimitCheck.isSelected()) {
            query.append(" LIMIT ").append(limitSpinner.getValue());
        }

        return query.toString();
    }

    public void applyTheme() {
        Color bgPrimary = JStudioTheme.getBgPrimary();
        Color textPrimary = JStudioTheme.getTextPrimary();
        Color border = JStudioTheme.getBorder();

        setBackground(bgPrimary);
        applyThemeRecursive(this, bgPrimary, textPrimary, border);
    }

    private void applyThemeRecursive(Container container, Color bg, Color fg, Color border) {
        container.setBackground(bg);
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel) {
                JPanel panel = (JPanel) c;
                panel.setBackground(bg);
                if (panel.getBorder() instanceof TitledBorder) {
                    TitledBorder tb = (TitledBorder) panel.getBorder();
                    tb.setTitleColor(fg);
                }
                applyThemeRecursive(panel, bg, fg, border);
            } else if (c instanceof JLabel) {
                c.setForeground(fg);
            } else if (c instanceof JComboBox) {
                JComboBox<?> combo = (JComboBox<?>) c;
                combo.setBackground(JStudioTheme.getBgSurface());
                combo.setForeground(fg);
            } else if (c instanceof JTextField) {
                JTextField field = (JTextField) c;
                field.setBackground(JStudioTheme.getBgSurface());
                field.setForeground(fg);
                field.setCaretColor(fg);
            } else if (c instanceof JCheckBox) {
                JCheckBox check = (JCheckBox) c;
                check.setBackground(bg);
                check.setForeground(fg);
            } else if (c instanceof JButton) {
                JButton button = (JButton) c;
                button.setBackground(JStudioTheme.getBgSecondary());
                button.setForeground(fg);
            } else if (c instanceof JSpinner) {
                JSpinner spinner = (JSpinner) c;
                spinner.setBackground(JStudioTheme.getBgSurface());
                spinner.setForeground(fg);
            } else if (c instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) c;
                sp.setBackground(bg);
                sp.getViewport().setBackground(JStudioTheme.getBgSurface());
                if (sp.getViewport().getView() instanceof Container) {
                    applyThemeRecursive((Container) sp.getViewport().getView(), JStudioTheme.getBgSurface(), fg, border);
                }
            } else if (c instanceof Container) {
                applyThemeRecursive((Container) c, bg, fg, border);
            }
        }
    }

    private static class PredicateRow extends JPanel {
        private final JComboBox<String> connectorCombo;
        private final JComboBox<String> predicateTypeCombo;
        private final JPanel argumentsPanel;
        private final CardLayout argumentsLayout;

        private JTextField callsTargetField;
        private JTextField allocTypeField;
        private JComboBox<String> allocOpCombo;
        private JSpinner allocThresholdSpinner;
        private JTextField stringPatternField;
        private JCheckBox stringRegexCheck;
        private JTextField writesFieldField;
        private JTextField readsFieldField;
        private JTextField throwsTypeField;
        private JComboBox<String> instructionOpCombo;
        private JSpinner instructionThresholdSpinner;

        private final Runnable changeCallback;

        PredicateRow(int index, Consumer<PredicateRow> removeCallback, Runnable changeCallback) {
            this.changeCallback = changeCallback;

            setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            connectorCombo = new JComboBox<>(new String[]{"AND", "OR"});
            connectorCombo.setPreferredSize(new Dimension(70, 26));
            connectorCombo.setVisible(index > 0);
            connectorCombo.addActionListener(e -> changeCallback.run());
            add(connectorCombo);

            predicateTypeCombo = new JComboBox<>(new String[]{
                "calls", "allocCount", "containsString", "writesField", "readsField", "throws", "instructionCount"
            });
            predicateTypeCombo.setPreferredSize(new Dimension(130, 26));
            predicateTypeCombo.addActionListener(e -> {
                showArgumentsFor((String) predicateTypeCombo.getSelectedItem());
                changeCallback.run();
            });
            add(predicateTypeCombo);

            argumentsLayout = new CardLayout();
            argumentsPanel = new JPanel(argumentsLayout);
            argumentsPanel.setPreferredSize(new Dimension(400, 30));

            argumentsPanel.add(createCallsPanel(), "calls");
            argumentsPanel.add(createAllocCountPanel(), "allocCount");
            argumentsPanel.add(createContainsStringPanel(), "containsString");
            argumentsPanel.add(createWritesFieldPanel(), "writesField");
            argumentsPanel.add(createReadsFieldPanel(), "readsField");
            argumentsPanel.add(createThrowsPanel(), "throws");
            argumentsPanel.add(createInstructionCountPanel(), "instructionCount");

            add(argumentsPanel);

            JButton removeBtn = new JButton("X");
            removeBtn.setPreferredSize(new Dimension(30, 26));
            removeBtn.setMargin(new Insets(0, 0, 0, 0));
            removeBtn.addActionListener(e -> removeCallback.accept(this));
            add(removeBtn);

            showArgumentsFor("calls");
        }

        void updateIndex(int newIndex) {
            connectorCombo.setVisible(newIndex > 0);
        }

        String getConnector() {
            return (String) connectorCombo.getSelectedItem();
        }

        private JPanel createCallsPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.add(new JLabel("("));
            callsTargetField = new JTextField(25);
            callsTargetField.setToolTipText("owner/class.methodName or just methodName");
            callsTargetField.getDocument().addDocumentListener(new SimpleDocumentListener(changeCallback));
            panel.add(callsTargetField);
            panel.add(new JLabel(")"));
            return panel;
        }

        private JPanel createAllocCountPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.add(new JLabel("("));
            allocTypeField = new JTextField(15);
            allocTypeField.setToolTipText("Type name (e.g., java/lang/StringBuilder)");
            allocTypeField.getDocument().addDocumentListener(new SimpleDocumentListener(changeCallback));
            panel.add(allocTypeField);
            panel.add(new JLabel(")"));

            allocOpCombo = new JComboBox<>(new String[]{">", ">=", "=", "<", "<="});
            allocOpCombo.addActionListener(e -> changeCallback.run());
            panel.add(allocOpCombo);

            allocThresholdSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10000, 1));
            allocThresholdSpinner.setPreferredSize(new Dimension(70, 26));
            allocThresholdSpinner.addChangeListener(e -> changeCallback.run());
            panel.add(allocThresholdSpinner);
            return panel;
        }

        private JPanel createContainsStringPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.add(new JLabel("("));
            stringPatternField = new JTextField(20);
            stringPatternField.setToolTipText("String pattern to search for");
            stringPatternField.getDocument().addDocumentListener(new SimpleDocumentListener(changeCallback));
            panel.add(stringPatternField);
            panel.add(new JLabel(")"));

            stringRegexCheck = new JCheckBox("regex");
            stringRegexCheck.addActionListener(e -> changeCallback.run());
            panel.add(stringRegexCheck);
            return panel;
        }

        private JPanel createWritesFieldPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.add(new JLabel("("));
            writesFieldField = new JTextField(25);
            writesFieldField.setToolTipText("owner/class.fieldName");
            writesFieldField.getDocument().addDocumentListener(new SimpleDocumentListener(changeCallback));
            panel.add(writesFieldField);
            panel.add(new JLabel(")"));
            return panel;
        }

        private JPanel createReadsFieldPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.add(new JLabel("("));
            readsFieldField = new JTextField(25);
            readsFieldField.setToolTipText("owner/class.fieldName (use * for any)");
            readsFieldField.getDocument().addDocumentListener(new SimpleDocumentListener(changeCallback));
            panel.add(readsFieldField);
            panel.add(new JLabel(")"));
            return panel;
        }

        private JPanel createThrowsPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.add(new JLabel("("));
            throwsTypeField = new JTextField(25);
            throwsTypeField.setToolTipText("Exception type (e.g., java/lang/NullPointerException)");
            throwsTypeField.getDocument().addDocumentListener(new SimpleDocumentListener(changeCallback));
            panel.add(throwsTypeField);
            panel.add(new JLabel(")"));
            return panel;
        }

        private JPanel createInstructionCountPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

            instructionOpCombo = new JComboBox<>(new String[]{">", ">=", "=", "<", "<="});
            instructionOpCombo.addActionListener(e -> changeCallback.run());
            panel.add(instructionOpCombo);

            instructionThresholdSpinner = new JSpinner(new SpinnerNumberModel(1000, 0, 1000000, 100));
            instructionThresholdSpinner.setPreferredSize(new Dimension(100, 26));
            instructionThresholdSpinner.addChangeListener(e -> changeCallback.run());
            panel.add(instructionThresholdSpinner);
            return panel;
        }

        private void showArgumentsFor(String predicateType) {
            argumentsLayout.show(argumentsPanel, predicateType);
        }

        String buildPredicate() {
            String type = (String) predicateTypeCombo.getSelectedItem();

            switch (type) {
                case "calls": {
                    String target = callsTargetField.getText().trim();
                    if (target.isEmpty()) return null;
                    return "calls(\"" + target + "\")";
                }
                case "allocCount": {
                    String allocType = allocTypeField.getText().trim();
                    if (allocType.isEmpty()) return null;
                    String op = (String) allocOpCombo.getSelectedItem();
                    int threshold = (Integer) allocThresholdSpinner.getValue();
                    return "allocCount(\"" + allocType + "\") " + op + " " + threshold;
                }
                case "containsString": {
                    String pattern = stringPatternField.getText().trim();
                    if (pattern.isEmpty()) return null;
                    if (stringRegexCheck.isSelected()) {
                        return "containsString(/" + pattern + "/)";
                    }
                    return "containsString(\"" + pattern + "\")";
                }
                case "writesField": {
                    String field = writesFieldField.getText().trim();
                    if (field.isEmpty()) return null;
                    return "writesField(\"" + field + "\")";
                }
                case "readsField": {
                    String field = readsFieldField.getText().trim();
                    if (field.isEmpty()) return null;
                    return "readsField(\"" + field + "\")";
                }
                case "throws": {
                    String exType = throwsTypeField.getText().trim();
                    if (exType.isEmpty()) return null;
                    return "throws(\"" + exType + "\")";
                }
                case "instructionCount": {
                    String op = (String) instructionOpCombo.getSelectedItem();
                    int threshold = (Integer) instructionThresholdSpinner.getValue();
                    return "instructionCount " + op + " " + threshold;
                }
                default:
                    return null;
            }
        }
    }

    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable callback;

        SimpleDocumentListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
    }
}
