package com.tonic.ui.vm.dialog;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.CommandParser;
import com.tonic.ui.vm.MethodSelectorPanel;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.dialog.result.ExecutionResultPanel;
import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.testgen.objectspec.ObjectBuilderDialog;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory;
import com.tonic.ui.vm.testgen.objectspec.ObjectSpec;
import com.tonic.ui.vm.testgen.objectspec.ParamSpec;
import com.tonic.ui.vm.testgen.objectspec.ValueMode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecuteMethodDialog extends JDialog {

    private MethodEntryModel methodModel;
    private MethodEntry method;
    private final List<JTextField> parameterFields;
    private final List<JButton> configureButtons;
    private final Map<Integer, ObjectSpec> configuredObjects;
    private final JCheckBox stubInvokesCheckbox;
    private final ExecutionResultPanel resultPanel;
    private final JButton executeButton;
    private final JButton clearButton;
    private final JButton closeButton;
    private final CommandParser parser;

    private final MethodSelectorPanel methodSelector;
    private final JPanel configPanel;
    private final JPanel signaturePanel;
    private final JPanel parametersPanel;
    private final JLabel signatureLabel;
    private final JLabel statusLabel;

    public ExecuteMethodDialog(Frame parent) {
        this(parent, null);
    }

    public ExecuteMethodDialog(Frame parent, MethodEntryModel methodModel) {
        super(parent, "Execute Method", true);
        this.methodModel = methodModel;
        this.method = methodModel != null ? methodModel.getMethodEntry() : null;
        this.parameterFields = new ArrayList<>();
        this.configureButtons = new ArrayList<>();
        this.configuredObjects = new HashMap<>();
        this.parser = new CommandParser();

        this.stubInvokesCheckbox = new JCheckBox("Stub invokes");
        this.stubInvokesCheckbox.setSelected(false);
        this.resultPanel = new ExecutionResultPanel();
        this.executeButton = new JButton("Execute");
        this.clearButton = new JButton("Clear");
        this.closeButton = new JButton("Close");

        this.methodSelector = new MethodSelectorPanel("Method Browser");
        this.configPanel = new JPanel(new BorderLayout());
        this.signaturePanel = new JPanel(new BorderLayout());
        this.parametersPanel = new JPanel(new BorderLayout());
        this.signatureLabel = new JLabel("No method selected");
        this.statusLabel = new JLabel("Select a method to execute");

        initializeComponents();

        if (methodModel != null) {
            onMethodSelected(methodModel);
        }

        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(createToolbar(), BorderLayout.NORTH);

        JSplitPane mainSplit = createMainSplitPane();
        add(mainSplit, BorderLayout.CENTER);

        add(createStatusBar(), BorderLayout.SOUTH);

        updateUIState();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.setOpaque(false);

        executeButton.setBackground(JStudioTheme.getAccent());
        executeButton.setForeground(Color.WHITE);
        executeButton.setFocusPainted(false);
        executeButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getAccent().darker()),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        executeButton.addActionListener(e -> executeMethod());
        executeButton.setEnabled(false);

        clearButton.setBackground(JStudioTheme.getBgTertiary());
        clearButton.setForeground(JStudioTheme.getTextPrimary());
        clearButton.setFocusPainted(false);
        clearButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        clearButton.addActionListener(e -> resultPanel.clear());

        closeButton.setBackground(JStudioTheme.getBgTertiary());
        closeButton.setForeground(JStudioTheme.getTextPrimary());
        closeButton.setFocusPainted(false);
        closeButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        closeButton.addActionListener(e -> dispose());

        leftButtons.add(executeButton);
        leftButtons.add(clearButton);
        leftButtons.add(closeButton);

        JPanel rightOptions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightOptions.setOpaque(false);

        stubInvokesCheckbox.setOpaque(false);
        stubInvokesCheckbox.setForeground(JStudioTheme.getTextPrimary());
        stubInvokesCheckbox.setToolTipText("When checked, stub method invocations with defaults. When unchecked, execute recursively.");
        rightOptions.add(stubInvokesCheckbox);

        toolbar.add(leftButtons, BorderLayout.WEST);
        toolbar.add(rightOptions, BorderLayout.EAST);

        return toolbar;
    }

    private JSplitPane createMainSplitPane() {
        JSplitPane leftSplit = createLeftSplitPane();
        JPanel rightPanel = createRightPanel();

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightPanel);
        mainSplit.setDividerLocation(400);
        mainSplit.setResizeWeight(0.35);
        mainSplit.setBackground(JStudioTheme.getBgPrimary());
        mainSplit.setBorder(null);

        return mainSplit;
    }

    private JSplitPane createLeftSplitPane() {
        methodSelector.setOnMethodSelected(this::onMethodSelected);

        setupConfigPanel();

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, methodSelector, configPanel);
        leftSplit.setDividerLocation(250);
        leftSplit.setResizeWeight(0.4);
        leftSplit.setBackground(JStudioTheme.getBgPrimary());
        leftSplit.setBorder(null);

        return leftSplit;
    }

    private void setupConfigPanel() {
        configPanel.setBackground(JStudioTheme.getBgPrimary());

        setupSignaturePanel();
        setupParametersPanel();

        configPanel.add(signaturePanel, BorderLayout.NORTH);
        configPanel.add(parametersPanel, BorderLayout.CENTER);
    }

    private void setupSignaturePanel() {
        signaturePanel.setBackground(JStudioTheme.getBgPrimary());
        signaturePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Method Signature",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));

        signatureLabel.setForeground(JStudioTheme.getTextSecondary());
        signatureLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        signatureLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        signaturePanel.add(signatureLabel, BorderLayout.CENTER);
    }

    private void setupParametersPanel() {
        parametersPanel.setBackground(JStudioTheme.getBgPrimary());
        parametersPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Parameters",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));

        JLabel noMethodLabel = new JLabel("Select a method to configure parameters");
        noMethodLabel.setForeground(JStudioTheme.getTextSecondary());
        noMethodLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        parametersPanel.add(noMethodLabel, BorderLayout.CENTER);
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Execution Result",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));
        panel.add(resultPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(JStudioTheme.getBgSecondary());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBar.add(statusLabel, BorderLayout.WEST);

        return statusBar;
    }

    private void onMethodSelected(MethodEntryModel selectedMethod) {
        this.methodModel = selectedMethod;
        this.method = selectedMethod != null ? selectedMethod.getMethodEntry() : null;

        if (method != null) {
            signatureLabel.setText("<html>" + formatMethodSignature() + "</html>");
            signatureLabel.setForeground(JStudioTheme.getTextPrimary());
            rebuildParametersPanel();
            statusLabel.setText("Ready to execute: " + method.getOwnerName() + "." + method.getName());
            resultPanel.setMethodContext(method.getOwnerName(), method.getName(), method.getDesc());
        } else {
            signatureLabel.setText("No method selected");
            signatureLabel.setForeground(JStudioTheme.getTextSecondary());
            clearParametersPanel();
            statusLabel.setText("Select a method to execute");
            resultPanel.clearAll();
        }

        updateUIState();
    }

    private void rebuildParametersPanel() {
        parametersPanel.removeAll();
        parameterFields.clear();
        configureButtons.clear();
        configuredObjects.clear();

        String desc = method.getDesc();
        List<String> paramTypes = parseParameterTypes(desc);

        if (paramTypes.isEmpty()) {
            JLabel noParams = new JLabel("This method has no parameters");
            noParams.setForeground(JStudioTheme.getTextSecondary());
            noParams.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            parametersPanel.add(noParams, BorderLayout.CENTER);
        } else {
            JPanel fieldsPanel = new JPanel(new GridBagLayout());
            fieldsPanel.setBackground(JStudioTheme.getBgPrimary());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            for (int i = 0; i < paramTypes.size(); i++) {
                String paramType = paramTypes.get(i);
                boolean isObjectType = isObjectType(paramType);

                gbc.gridx = 0;
                gbc.gridy = i;
                gbc.weightx = 0;
                JLabel label = new JLabel("arg" + i + " (" + formatType(paramType) + "):");
                label.setForeground(JStudioTheme.getTextPrimary());
                fieldsPanel.add(label, gbc);

                gbc.gridx = 1;
                gbc.weightx = 1;
                JTextField field = new JTextField(15);
                field.setBackground(JStudioTheme.getBgSecondary());
                field.setForeground(JStudioTheme.getTextPrimary());
                field.setCaretColor(JStudioTheme.getTextPrimary());
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                    BorderFactory.createEmptyBorder(3, 5, 3, 5)
                ));
                field.setToolTipText(getInputHint(paramType));
                parameterFields.add(field);
                fieldsPanel.add(field, gbc);

                gbc.gridx = 2;
                gbc.weightx = 0;
                if (isObjectType && !paramType.equals("Ljava/lang/String;")) {
                    final int paramIndex = i;
                    final String objType = paramType;
                    JButton configBtn = new JButton("Configure...");
                    configBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                    configBtn.setToolTipText("Configure object construction");
                    configBtn.addActionListener(e -> openObjectConfig(paramIndex, objType));
                    configureButtons.add(configBtn);
                    fieldsPanel.add(configBtn, gbc);
                } else {
                    configureButtons.add(null);
                    fieldsPanel.add(Box.createHorizontalStrut(1), gbc);
                }
            }

            gbc.gridx = 0;
            gbc.gridy = paramTypes.size();
            gbc.weighty = 1;
            gbc.gridwidth = 3;
            fieldsPanel.add(Box.createVerticalGlue(), gbc);

            JScrollPane scrollPane = new JScrollPane(fieldsPanel);
            scrollPane.setBorder(null);
            scrollPane.getViewport().setBackground(JStudioTheme.getBgPrimary());
            parametersPanel.add(scrollPane, BorderLayout.CENTER);
        }

        parametersPanel.revalidate();
        parametersPanel.repaint();
    }

    private boolean isObjectType(String typeDesc) {
        return typeDesc != null &&
               (typeDesc.startsWith("L") || typeDesc.startsWith("["));
    }

    private void openObjectConfig(int paramIndex, String typeDesc) {
        String typeName = typeDesc;
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
            typeName = typeName.substring(1, typeName.length() - 1);
        }

        ObjectSpec existing = configuredObjects.get(paramIndex);
        ObjectSpec result = ObjectBuilderDialog.showDialog(this, typeName, existing);

        if (result != null) {
            configuredObjects.put(paramIndex, result);
            JButton btn = configureButtons.get(paramIndex);
            if (btn != null) {
                btn.setText("✓ Configured");
                btn.setForeground(new Color(78, 201, 176));
            }
            JTextField field = parameterFields.get(paramIndex);
            field.setText("[" + result.getSummary() + "]");
            field.setEditable(false);
        }
    }

    private void clearParametersPanel() {
        parametersPanel.removeAll();
        parameterFields.clear();
        configureButtons.clear();
        configuredObjects.clear();

        JLabel noMethodLabel = new JLabel("Select a method to configure parameters");
        noMethodLabel.setForeground(JStudioTheme.getTextSecondary());
        noMethodLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        parametersPanel.add(noMethodLabel, BorderLayout.CENTER);

        parametersPanel.revalidate();
        parametersPanel.repaint();
    }

    private void updateUIState() {
        boolean hasMethod = method != null;
        executeButton.setEnabled(hasMethod);
    }

    private void executeMethod() {
        if (method == null) {
            return;
        }

        String className = method.getOwnerName();
        String methodName = method.getName();
        String descriptor = method.getDesc();
        boolean useRecursive = !stubInvokesCheckbox.isSelected();

        try {
            Object[] args = collectArguments();

            if (!VMExecutionService.getInstance().isInitialized()) {
                VMExecutionService.getInstance().initialize();
            }

            executeButton.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            resultPanel.showExecuting();
            statusLabel.setText("Executing " + methodName + "...");

            SwingWorker<ExecutionResult, Void> worker = new SwingWorker<>() {
                @Override
                protected ExecutionResult doInBackground() {
                    if (useRecursive) {
                        return VMExecutionService.getInstance().traceStaticMethod(
                            className, methodName, descriptor, args);
                    } else {
                        return VMExecutionService.getInstance().executeStaticMethod(
                            className, methodName, descriptor, args);
                    }
                }

                @Override
                protected void done() {
                    executeButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    try {
                        ExecutionResult result = get();
                        resultPanel.setExecutionContext(className, methodName, descriptor, args);
                        displayResult(result);
                        if (result.isSuccess()) {
                            statusLabel.setText("Execution complete: " + result.getFormattedReturnValue());
                        } else {
                            statusLabel.setText("Execution failed: " + (result.getException() != null ? result.getException().getMessage() : "Unknown error"));
                            if (result.getException() != null) {
                                System.out.println("[ExecuteMethodDialog] Execution failed:");
                                result.getException().printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("[ExecuteMethodDialog] Error in done():");
                        e.printStackTrace();
                        displayError(e.getMessage());
                        statusLabel.setText("Execution failed: " + e.getMessage());
                    }
                }
            };

            worker.execute();

        } catch (Exception e) {
            System.out.println("[ExecuteMethodDialog] Failed to parse arguments:");
            e.printStackTrace();
            displayError("Failed to parse arguments: " + e.getMessage());
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private Object[] collectArguments() throws Exception {
        Object[] args = new Object[parameterFields.size()];
        String desc = method.getDesc();
        List<String> paramTypes = parseParameterTypes(desc);

        for (int i = 0; i < parameterFields.size(); i++) {
            if (configuredObjects.containsKey(i)) {
                ObjectSpec objSpec = configuredObjects.get(i);
                List<Object> values = ObjectFactory.getInstance().generateObjectValues(objSpec, 1);
                args[i] = values.isEmpty() ? null : values.get(0);
            } else {
                String value = parameterFields.get(i).getText().trim();
                String type = paramTypes.get(i);
                args[i] = parseArgumentValue(value, type);
            }
        }

        return args;
    }

    private Object parseArgumentValue(String value, String type) throws Exception {
        if (value.isEmpty() || value.equals("null")) {
            return null;
        }

        switch (type) {
            case "I":
            case "B":
            case "S":
                return Integer.parseInt(value);
            case "C":
                if (value.length() == 1) {
                    return value.charAt(0);
                }
                return Integer.parseInt(value);
            case "J":
                return Long.parseLong(value.replace("L", "").replace("l", ""));
            case "F":
                return Float.parseFloat(value.replace("f", "").replace("F", ""));
            case "D":
                return Double.parseDouble(value.replace("d", "").replace("D", ""));
            case "Z":
                return Boolean.parseBoolean(value);
            default:
                if (type.equals("Ljava/lang/String;")) {
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        return value.substring(1, value.length() - 1);
                    }
                    return value;
                }
                return parser.parseValue(value);
        }
    }

    private void displayResult(ExecutionResult result) {
        resultPanel.displayResult(result);
    }

    private void displayError(String message) {
        ExecutionResult errorResult = ExecutionResult.builder()
            .success(false)
            .exception(new RuntimeException(message))
            .build();
        resultPanel.displayResult(errorResult);
    }

    private String formatMethodSignature() {
        StringBuilder sb = new StringBuilder();

        int access = method.getAccess();
        if ((access & 0x0001) != 0) sb.append("public ");
        if ((access & 0x0002) != 0) sb.append("private ");
        if ((access & 0x0004) != 0) sb.append("protected ");
        if ((access & 0x0008) != 0) sb.append("static ");
        if ((access & 0x0010) != 0) sb.append("final ");

        String returnType = getReturnType(method.getDesc());
        sb.append(formatType(returnType)).append(" ");
        sb.append(method.getName()).append("(");

        List<String> paramTypes = parseParameterTypes(method.getDesc());
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatType(paramTypes.get(i))).append(" arg").append(i);
        }

        sb.append(")");
        return sb.toString();
    }

    private List<String> parseParameterTypes(String descriptor) {
        List<String> types = new ArrayList<>();
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            int start = i;
            while (descriptor.charAt(i) == '[') i++;

            if (descriptor.charAt(i) == 'L') {
                int end = descriptor.indexOf(';', i);
                i = end + 1;
            } else {
                i++;
            }
            types.add(descriptor.substring(start, i));
        }
        return types;
    }

    private String getReturnType(String descriptor) {
        int returnStart = descriptor.indexOf(')') + 1;
        return descriptor.substring(returnStart);
    }

    private String formatType(String type) {
        if (type.startsWith("[")) {
            return formatType(type.substring(1)) + "[]";
        }
        switch (type) {
            case "V": return "void";
            case "Z": return "boolean";
            case "B": return "byte";
            case "C": return "char";
            case "S": return "short";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            default:
                if (type.startsWith("L") && type.endsWith(";")) {
                    String className = type.substring(1, type.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return type;
        }
    }

    private String getInputHint(String type) {
        switch (type) {
            case "I": return "Enter an integer (e.g., 42)";
            case "J": return "Enter a long (e.g., 42L)";
            case "F": return "Enter a float (e.g., 3.14f)";
            case "D": return "Enter a double (e.g., 3.14)";
            case "Z": return "Enter true or false";
            case "B": return "Enter a byte (e.g., 127)";
            case "C": return "Enter a character (e.g., a)";
            case "S": return "Enter a short (e.g., 100)";
            case "Ljava/lang/String;": return "Enter a string (e.g., \"hello\")";
            default: return "Enter value (or null)";
        }
    }
}
