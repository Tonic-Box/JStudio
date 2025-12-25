package com.tonic.ui.vm.testgen.objectspec;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.VMExecutionService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ObjectBuilderDialog extends JDialog {

    private final String typeName;
    private ObjectSpec resultSpec;

    private JComboBox<ConstructionMode> modeCombo;
    private JComboBox<ConstructorInfo> constructorCombo;
    private JPanel constructorArgsPanel;
    private JPanel fieldOverridesPanel;
    private JTextArea expressionArea;
    private JComboBox<String> templateCombo;
    private JTextField templateNameField;

    private final List<ParamConfigPanel> constructorArgPanels = new ArrayList<>();
    private final List<FieldConfigPanel> fieldPanels = new ArrayList<>();

    private CardLayout cardLayout;
    private JPanel cardPanel;

    public ObjectBuilderDialog(Window owner, String typeName) {
        super(owner, "Configure Object: " + getSimpleName(typeName), ModalityType.APPLICATION_MODAL);
        this.typeName = typeName;
        initComponents();
        loadTypeInfo();
        pack();
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(owner);
    }

    private static String getSimpleName(String typeName) {
        if (typeName == null) return "?";
        String name = typeName.replace('/', '.');
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBackground(JStudioTheme.getBgPrimary());
        modePanel.add(createLabel("Construction Mode:"));

        modeCombo = new JComboBox<>(new ConstructionMode[]{
            ConstructionMode.CONSTRUCTOR,
            ConstructionMode.FIELD_INJECTION,
            ConstructionMode.EXPRESSION,
            ConstructionMode.TEMPLATE,
            ConstructionMode.NULL
        });
        modeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ConstructionMode) {
                    setText(((ConstructionMode) value).getDisplayName());
                }
                return this;
            }
        });
        modeCombo.addActionListener(e -> onModeChanged());
        modePanel.add(modeCombo);

        add(modePanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(JStudioTheme.getBgPrimary());

        cardPanel.add(createConstructorPanel(), "CONSTRUCTOR");
        cardPanel.add(createFieldInjectionPanel(), "FIELD_INJECTION");
        cardPanel.add(createExpressionPanel(), "EXPRESSION");
        cardPanel.add(createTemplatePanel(), "TEMPLATE");
        cardPanel.add(createNullPanel(), "NULL");

        add(cardPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton saveTemplateBtn = new JButton("Save as Template...");
        saveTemplateBtn.addActionListener(e -> saveAsTemplate());
        buttonPanel.add(saveTemplateBtn);

        buttonPanel.add(Box.createHorizontalStrut(20));

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> onOk());
        buttonPanel.add(okButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            resultSpec = null;
            dispose();
        });
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createConstructorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(JStudioTheme.getBgPrimary());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(JStudioTheme.getBgPrimary());
        topPanel.add(createLabel("Constructor:"));

        constructorCombo = new JComboBox<>();
        constructorCombo.setPreferredSize(new Dimension(400, 25));
        constructorCombo.addActionListener(e -> onConstructorSelected());
        topPanel.add(constructorCombo);

        panel.add(topPanel, BorderLayout.NORTH);

        constructorArgsPanel = new JPanel();
        constructorArgsPanel.setLayout(new BoxLayout(constructorArgsPanel, BoxLayout.Y_AXIS));
        constructorArgsPanel.setBackground(JStudioTheme.getBgPrimary());
        constructorArgsPanel.setBorder(createTitledBorder("Constructor Arguments"));

        JScrollPane scroll = new JScrollPane(constructorArgsPanel);
        scroll.setPreferredSize(new Dimension(550, 300));
        scroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createFieldInjectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(JStudioTheme.getBgPrimary());

        JLabel infoLabel = createLabel("Configure field values directly (bypasses constructor):");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        panel.add(infoLabel, BorderLayout.NORTH);

        fieldOverridesPanel = new JPanel();
        fieldOverridesPanel.setLayout(new BoxLayout(fieldOverridesPanel, BoxLayout.Y_AXIS));
        fieldOverridesPanel.setBackground(JStudioTheme.getBgPrimary());
        fieldOverridesPanel.setBorder(createTitledBorder("Fields"));

        JScrollPane scroll = new JScrollPane(fieldOverridesPanel);
        scroll.setPreferredSize(new Dimension(550, 300));
        scroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createExpressionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(JStudioTheme.getBgPrimary());

        JLabel infoLabel = createLabel("Enter a Java expression to construct the object:");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        panel.add(infoLabel, BorderLayout.NORTH);

        expressionArea = new JTextArea();
        expressionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        expressionArea.setRows(8);
        expressionArea.setBackground(JStudioTheme.getBgSecondary());
        expressionArea.setForeground(JStudioTheme.getTextPrimary());
        expressionArea.setCaretColor(JStudioTheme.getTextPrimary());
        expressionArea.setText("new " + getSimpleName(typeName) + "()");

        JScrollPane scroll = new JScrollPane(expressionArea);
        scroll.setBorder(createTitledBorder("Expression"));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        helpPanel.setBackground(JStudioTheme.getBgPrimary());
        helpPanel.add(createLabel("Tip: Use $fuzz.int(), $fuzz.string() for dynamic values"));
        panel.add(helpPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createTemplatePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(JStudioTheme.getBgPrimary());

        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectPanel.setBackground(JStudioTheme.getBgPrimary());
        selectPanel.add(createLabel("Select Template:"));

        templateCombo = new JComboBox<>();
        templateCombo.setPreferredSize(new Dimension(300, 25));
        refreshTemplateCombo();
        selectPanel.add(templateCombo);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshTemplateCombo());
        selectPanel.add(refreshBtn);

        panel.add(selectPanel, BorderLayout.NORTH);

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBackground(JStudioTheme.getBgPrimary());
        previewPanel.setBorder(createTitledBorder("Template Preview"));

        JTextArea previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        previewArea.setBackground(JStudioTheme.getBgSecondary());
        previewArea.setForeground(JStudioTheme.getTextSecondary());

        templateCombo.addActionListener(e -> {
            String selected = (String) templateCombo.getSelectedItem();
            if (selected != null) {
                ObjectTemplate template = ObjectTemplateManager.getInstance().getTemplate(selected);
                if (template != null && template.getSpec() != null) {
                    previewArea.setText(template.getSpec().getSummary());
                }
            }
        });

        previewPanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);
        panel.add(previewPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createNullPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());

        JLabel label = createLabel("This parameter will be null.");
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 14f));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    private void onModeChanged() {
        ConstructionMode mode = (ConstructionMode) modeCombo.getSelectedItem();
        if (mode != null) {
            cardLayout.show(cardPanel, mode.name());
        }
    }

    private void onConstructorSelected() {
        constructorArgsPanel.removeAll();
        constructorArgPanels.clear();

        ConstructorInfo info = (ConstructorInfo) constructorCombo.getSelectedItem();
        if (info != null && info.paramTypes != null) {
            for (int i = 0; i < info.paramTypes.size(); i++) {
                String paramType = info.paramTypes.get(i);
                String paramName = "arg" + i;
                ParamSpec spec = new ParamSpec(paramName, paramType);

                ParamConfigPanel configPanel = new ParamConfigPanel(this, spec);
                constructorArgPanels.add(configPanel);
                constructorArgsPanel.add(configPanel);
                constructorArgsPanel.add(Box.createVerticalStrut(5));
            }
        }

        if (constructorArgPanels.isEmpty()) {
            JLabel emptyLabel = createLabel("No parameters (default constructor)");
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));
            constructorArgsPanel.add(emptyLabel);
        }

        constructorArgsPanel.revalidate();
        constructorArgsPanel.repaint();
    }

    private void loadTypeInfo() {
        VMExecutionService service = VMExecutionService.getInstance();
        if (!service.isInitialized()) {
            addDefaultConstructor();
            return;
        }

        ClassFile classFile = service.getClassPool().get(typeName);
        if (classFile == null) {
            addDefaultConstructor();
            return;
        }

        for (MethodEntry method : classFile.getMethods()) {
            if ("<init>".equals(method.getName())) {
                ConstructorInfo info = new ConstructorInfo(method.getDesc());
                constructorCombo.addItem(info);
            }
        }

        if (constructorCombo.getItemCount() == 0) {
            addDefaultConstructor();
        }

        fieldPanels.clear();
        fieldOverridesPanel.removeAll();
        for (FieldEntry field : classFile.getFields()) {
            int access = field.getAccess();
            if ((access & 0x0008) != 0) continue;

            ParamSpec spec = new ParamSpec(field.getName(), field.getDesc());
            FieldConfigPanel panel = new FieldConfigPanel(field.getName(), spec);
            fieldPanels.add(panel);
            fieldOverridesPanel.add(panel);
            fieldOverridesPanel.add(Box.createVerticalStrut(3));
        }

        if (fieldPanels.isEmpty()) {
            JLabel emptyLabel = createLabel("No instance fields found");
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));
            fieldOverridesPanel.add(emptyLabel);
        }

        if (constructorCombo.getItemCount() > 0) {
            constructorCombo.setSelectedIndex(0);
        }
    }

    private void addDefaultConstructor() {
        constructorCombo.addItem(new ConstructorInfo("()V"));
    }

    private void refreshTemplateCombo() {
        templateCombo.removeAllItems();
        List<String> names = ObjectTemplateManager.getInstance().getTemplateNamesForType(typeName);
        for (String name : names) {
            templateCombo.addItem(name);
        }
        if (names.isEmpty()) {
            List<String> allNames = ObjectTemplateManager.getInstance().getTemplateNames();
            for (String name : allNames) {
                templateCombo.addItem(name);
            }
        }
    }

    private void onOk() {
        resultSpec = buildSpec();
        dispose();
    }

    private ObjectSpec buildSpec() {
        ConstructionMode mode = (ConstructionMode) modeCombo.getSelectedItem();
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.setMode(mode);

        switch (mode) {
            case CONSTRUCTOR:
                ConstructorInfo info = (ConstructorInfo) constructorCombo.getSelectedItem();
                if (info != null) {
                    spec.setConstructorDescriptor(info.descriptor);
                    for (ParamConfigPanel panel : constructorArgPanels) {
                        spec.addConstructorArg(panel.getParamSpec());
                    }
                }
                break;

            case FIELD_INJECTION:
                for (FieldConfigPanel panel : fieldPanels) {
                    if (panel.isIncluded()) {
                        spec.setFieldOverride(panel.getFieldName(), panel.getParamSpec());
                    }
                }
                break;

            case EXPRESSION:
                spec.setExpression(expressionArea.getText());
                break;

            case TEMPLATE:
                spec.setTemplateName((String) templateCombo.getSelectedItem());
                break;

            case NULL:
                break;
        }

        return spec;
    }

    private void saveAsTemplate() {
        String name = JOptionPane.showInputDialog(this, "Template name:", "Save Template",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        ObjectSpec spec = buildSpec();
        ObjectTemplate template = new ObjectTemplate(name.trim(), typeName, spec);
        ObjectTemplateManager.getInstance().saveTemplate(template);
        refreshTemplateCombo();

        JOptionPane.showMessageDialog(this, "Template saved: " + name,
                "Template Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    public ObjectSpec getResult() {
        return resultSpec;
    }

    public static ObjectSpec showDialog(Window owner, String typeName) {
        ObjectBuilderDialog dialog = new ObjectBuilderDialog(owner, typeName);
        dialog.setVisible(true);
        return dialog.getResult();
    }

    public static ObjectSpec showDialog(Window owner, String typeName, ObjectSpec existing) {
        ObjectBuilderDialog dialog = new ObjectBuilderDialog(owner, typeName);
        if (existing != null) {
            dialog.loadExistingSpec(existing);
        }
        dialog.setVisible(true);
        return dialog.getResult();
    }

    private void loadExistingSpec(ObjectSpec spec) {
        modeCombo.setSelectedItem(spec.getMode());
        onModeChanged();

        switch (spec.getMode()) {
            case CONSTRUCTOR:
                for (int i = 0; i < constructorCombo.getItemCount(); i++) {
                    ConstructorInfo info = constructorCombo.getItemAt(i);
                    if (info.descriptor.equals(spec.getConstructorDescriptor())) {
                        constructorCombo.setSelectedIndex(i);
                        break;
                    }
                }
                for (int i = 0; i < spec.getConstructorArgs().size() && i < constructorArgPanels.size(); i++) {
                    constructorArgPanels.get(i).loadSpec(spec.getConstructorArgs().get(i));
                }
                break;

            case EXPRESSION:
                expressionArea.setText(spec.getExpression());
                break;

            case TEMPLATE:
                templateCombo.setSelectedItem(spec.getTemplateName());
                break;
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextPrimary());
        return label;
    }

    private TitledBorder createTitledBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 11),
            JStudioTheme.getTextPrimary()
        );
    }

    private static class ConstructorInfo {
        final String descriptor;
        final List<String> paramTypes;

        ConstructorInfo(String descriptor) {
            this.descriptor = descriptor;
            this.paramTypes = parseParams(descriptor);
        }

        private List<String> parseParams(String desc) {
            List<String> types = new ArrayList<>();
            int i = desc.indexOf('(');
            if (i < 0) return types;
            i++;

            while (i < desc.length() && desc.charAt(i) != ')') {
                char c = desc.charAt(i);
                if (c == 'L') {
                    int end = desc.indexOf(';', i);
                    if (end < 0) break;
                    types.add(desc.substring(i, end + 1));
                    i = end + 1;
                } else if (c == '[') {
                    int start = i;
                    i++;
                    while (i < desc.length() && desc.charAt(i) == '[') i++;
                    if (i < desc.length()) {
                        char elem = desc.charAt(i);
                        if (elem == 'L') {
                            int end = desc.indexOf(';', i);
                            if (end < 0) break;
                            types.add(desc.substring(start, end + 1));
                            i = end + 1;
                        } else {
                            types.add(desc.substring(start, i + 1));
                            i++;
                        }
                    }
                } else {
                    types.add(String.valueOf(c));
                    i++;
                }
            }
            return types;
        }

        @Override
        public String toString() {
            if (paramTypes.isEmpty()) {
                return "No-arg constructor";
            }
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < paramTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(getSimpleTypeName(paramTypes.get(i)));
            }
            sb.append(")");
            return sb.toString();
        }

        private String getSimpleTypeName(String typeDesc) {
            ParamSpec temp = new ParamSpec(null, typeDesc);
            return temp.getSimpleTypeName();
        }
    }

    private class FieldConfigPanel extends JPanel {
        private final String fieldName;
        private final JCheckBox includeCheck;
        private final ParamConfigPanel configPanel;

        FieldConfigPanel(String fieldName, ParamSpec spec) {
            this.fieldName = fieldName;
            setLayout(new BorderLayout(5, 0));
            setBackground(JStudioTheme.getBgPrimary());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

            includeCheck = new JCheckBox();
            includeCheck.setBackground(JStudioTheme.getBgPrimary());
            add(includeCheck, BorderLayout.WEST);

            configPanel = new ParamConfigPanel(ObjectBuilderDialog.this, spec);
            add(configPanel, BorderLayout.CENTER);
        }

        boolean isIncluded() {
            return includeCheck.isSelected();
        }

        String getFieldName() {
            return fieldName;
        }

        ParamSpec getParamSpec() {
            return configPanel.getParamSpec();
        }
    }
}
