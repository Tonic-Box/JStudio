package com.tonic.ui.vm.dialog;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.CommandParser;
import com.tonic.ui.vm.MethodSelectorPanel;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.dialog.result.ExecutionResultPanel;
import com.tonic.ui.vm.model.ExecutionResult;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExecuteMethodDialog extends JDialog {

    private MethodEntryModel methodModel;
    private MethodEntry method;
    private final List<JTextField> parameterFields;
    private final JCheckBox traceCheckbox;
    private final ExecutionResultPanel resultPanel;
    private final JButton executeButton;
    private final JButton clearButton;
    private final JButton closeButton;
    private final CommandParser parser;

    private final MethodSelectorPanel methodSelector;
    private final JPanel signaturePanel;
    private final JPanel parametersPanel;
    private final JLabel signatureLabel;
    private JPanel parametersContent;

    public ExecuteMethodDialog(Frame parent) {
        this(parent, null);
    }

    public ExecuteMethodDialog(Frame parent, MethodEntryModel methodModel) {
        super(parent, "Execute Method", true);
        this.methodModel = methodModel;
        this.method = methodModel != null ? methodModel.getMethodEntry() : null;
        this.parameterFields = new ArrayList<>();
        this.parser = new CommandParser();

        this.traceCheckbox = new JCheckBox("Trace method calls");
        this.resultPanel = new ExecutionResultPanel();
        this.executeButton = new JButton("Execute");
        this.clearButton = new JButton("Clear");
        this.closeButton = new JButton("Close");

        this.methodSelector = new MethodSelectorPanel("Select Method");
        this.signaturePanel = new JPanel(new BorderLayout());
        this.parametersPanel = new JPanel(new BorderLayout());
        this.signatureLabel = new JLabel("No method selected");

        initializeComponents();

        if (methodModel != null) {
            onMethodSelected(methodModel);
        }

        setSize(900, 800);
        setMinimumSize(new Dimension(700, 600));
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBackground(JStudioTheme.getBgPrimary());

        methodSelector.setPreferredSize(new Dimension(0, 200));
        methodSelector.setOnMethodSelected(this::onMethodSelected);
        topPanel.add(methodSelector, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(JStudioTheme.getBgPrimary());

        setupSignaturePanel();
        centerPanel.add(signaturePanel, BorderLayout.NORTH);

        setupParametersPanel();
        centerPanel.add(parametersPanel, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(JStudioTheme.getBgPrimary());
        bottomPanel.add(createOptionsPanel(), BorderLayout.NORTH);
        bottomPanel.add(createResultPanel(), BorderLayout.CENTER);
        bottomPanel.add(createButtonPanel(), BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        updateUIState();
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

    private void onMethodSelected(MethodEntryModel selectedMethod) {
        this.methodModel = selectedMethod;
        this.method = selectedMethod != null ? selectedMethod.getMethodEntry() : null;

        if (method != null) {
            signatureLabel.setText("<html>" + formatMethodSignature() + "</html>");
            signatureLabel.setForeground(JStudioTheme.getTextPrimary());
            rebuildParametersPanel();
        } else {
            signatureLabel.setText("No method selected");
            signatureLabel.setForeground(JStudioTheme.getTextSecondary());
            clearParametersPanel();
        }

        updateUIState();
    }

    private void rebuildParametersPanel() {
        parametersPanel.removeAll();
        parameterFields.clear();

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

                gbc.gridx = 0;
                gbc.gridy = i;
                gbc.weightx = 0;
                JLabel label = new JLabel("arg" + i + " (" + formatType(paramType) + "):");
                label.setForeground(JStudioTheme.getTextPrimary());
                fieldsPanel.add(label, gbc);

                gbc.gridx = 1;
                gbc.weightx = 1;
                JTextField field = new JTextField(20);
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
            }

            JScrollPane scrollPane = new JScrollPane(fieldsPanel);
            scrollPane.setBorder(null);
            scrollPane.getViewport().setBackground(JStudioTheme.getBgPrimary());
            parametersPanel.add(scrollPane, BorderLayout.CENTER);
        }

        parametersPanel.revalidate();
        parametersPanel.repaint();
    }

    private void clearParametersPanel() {
        parametersPanel.removeAll();
        parameterFields.clear();

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

    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(JStudioTheme.getBgPrimary());

        traceCheckbox.setBackground(JStudioTheme.getBgPrimary());
        traceCheckbox.setForeground(JStudioTheme.getTextPrimary());
        panel.add(traceCheckbox);

        return panel;
    }

    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Result",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));
        panel.setPreferredSize(new Dimension(0, 350));
        panel.add(resultPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBackground(JStudioTheme.getBgPrimary());

        executeButton.setBackground(JStudioTheme.getBgSecondary());
        executeButton.setForeground(JStudioTheme.getTextPrimary());
        executeButton.addActionListener(e -> executeMethod());
        executeButton.setEnabled(false);

        clearButton.setBackground(JStudioTheme.getBgSecondary());
        clearButton.setForeground(JStudioTheme.getTextPrimary());
        clearButton.addActionListener(e -> resultPanel.clear());

        closeButton.setBackground(JStudioTheme.getBgSecondary());
        closeButton.setForeground(JStudioTheme.getTextPrimary());
        closeButton.addActionListener(e -> dispose());

        panel.add(executeButton);
        panel.add(clearButton);
        panel.add(closeButton);

        return panel;
    }

    private void executeMethod() {
        if (method == null) {
            return;
        }

        String className = method.getOwnerName();
        String methodName = method.getName();
        String descriptor = method.getDesc();

        try {
            Object[] args = collectArguments();

            if (!VMExecutionService.getInstance().isInitialized()) {
                VMExecutionService.getInstance().initialize();
            }

            executeButton.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            resultPanel.showExecuting();

            SwingWorker<ExecutionResult, Void> worker = new SwingWorker<>() {
                @Override
                protected ExecutionResult doInBackground() {
                    if (traceCheckbox.isSelected()) {
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
                        displayResult(result);
                    } catch (Exception e) {
                        displayError(e.getMessage());
                    }
                }
            };

            worker.execute();

        } catch (Exception e) {
            displayError("Failed to parse arguments: " + e.getMessage());
        }
    }

    private Object[] collectArguments() throws Exception {
        Object[] args = new Object[parameterFields.size()];
        String desc = method.getDesc();
        List<String> paramTypes = parseParameterTypes(desc);

        for (int i = 0; i < parameterFields.size(); i++) {
            String value = parameterFields.get(i).getText().trim();
            String type = paramTypes.get(i);
            args[i] = parseArgumentValue(value, type);
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
