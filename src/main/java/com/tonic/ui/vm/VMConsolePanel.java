package com.tonic.ui.vm;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.JdkClassFilter;
import com.tonic.ui.vm.model.ExecutionResult;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class VMConsolePanel extends ThemedJPanel {

    private JTextPane outputPane;
    private JTextField inputField;
    private JButton runButton;
    private JButton stopButton;
    private JButton clearButton;

    private final CommandParser parser;
    private final List<String> commandHistory;
    private int historyIndex;
    private boolean isExecuting;

    private Style defaultStyle;
    private Style errorStyle;
    private Style successStyle;
    private Style infoStyle;
    private Style promptStyle;

    public VMConsolePanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout());
        this.parser = new CommandParser();
        this.commandHistory = new ArrayList<>();
        this.historyIndex = -1;
        this.isExecuting = false;

        initializeComponents();
        initializeStyles();
        printWelcome();
    }

    private void initializeComponents() {
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setBackground(JStudioTheme.getBgSecondary());
        outputPane.setForeground(JStudioTheme.getTextPrimary());
        outputPane.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        outputPane.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        JScrollPane scrollPane = new JScrollPane(outputPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel inputPanel = new JPanel(new BorderLayout(UIConstants.SPACING_SMALL, 0));
        inputPanel.setBackground(JStudioTheme.getBgPrimary());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        JLabel promptLabel = new JLabel(">>> ");
        promptLabel.setForeground(JStudioTheme.getAccent());
        promptLabel.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL).deriveFont(Font.BOLD));

        inputField = new JTextField();
        inputField.setBackground(JStudioTheme.getBgSecondary());
        inputField.setForeground(JStudioTheme.getTextPrimary());
        inputField.setCaretColor(JStudioTheme.getTextPrimary());
        inputField.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(UIConstants.SPACING_TINY, UIConstants.SPACING_SMALL, UIConstants.SPACING_TINY, UIConstants.SPACING_SMALL)
        ));

        inputField.addActionListener(e -> executeCommand());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    navigateHistory(-1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistory(1);
                    e.consume();
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        runButton = createButton("Run", e -> executeCommand());
        stopButton = createButton("Stop", e -> stopExecution());
        clearButton = createButton("Clear", e -> clearOutput());

        stopButton.setEnabled(false);

        buttonPanel.add(runButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(clearButton);

        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private JButton createButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        button.addActionListener(action);
        return button;
    }

    private void initializeStyles() {
        StyledDocument doc = outputPane.getStyledDocument();

        defaultStyle = doc.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, JStudioTheme.getTextPrimary());

        errorStyle = doc.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, JStudioTheme.getError());

        successStyle = doc.addStyle("success", null);
        StyleConstants.setForeground(successStyle, JStudioTheme.getSuccess());

        infoStyle = doc.addStyle("info", null);
        StyleConstants.setForeground(infoStyle, JStudioTheme.getAccentSecondary());

        promptStyle = doc.addStyle("prompt", null);
        StyleConstants.setForeground(promptStyle, JStudioTheme.getAccent());
        StyleConstants.setBold(promptStyle, true);
    }

    private void printWelcome() {
        appendText("VM Console - Interactive Method Execution\n", infoStyle);
        appendText("Type /help for available commands\n", infoStyle);
        appendText("Execute methods: ClassName.methodName(args)\n\n", infoStyle);
    }

    private void executeCommand() {
        String input = inputField.getText().trim();
        if (input.isEmpty()) {
            return;
        }

        commandHistory.add(input);
        historyIndex = commandHistory.size();

        appendText(">>> " + input + "\n", promptStyle);
        inputField.setText("");

        CommandParser.ParseResult result = parser.parse(input);

        switch (result.getType()) {
            case COMMAND:
                handleCommand(result.getCommand(), result.getCommandArgs());
                break;
            case METHOD_CALL:
                handleMethodCall(result);
                break;
            case ERROR:
                appendText("Error: " + result.getErrorMessage() + "\n", errorStyle);
                break;
            case EMPTY:
                break;
        }
    }

    private void handleCommand(String command, String args) {
        switch (command) {
            case "help":
                printHelp();
                break;
            case "init":
                initializeVM();
                break;
            case "reset":
                resetVM();
                break;
            case "status":
                showStatus();
                break;
            case "classes":
                listClasses(args);
                break;
            case "methods":
                listMethods(args);
                break;
            case "clear":
                clearOutput();
                break;
            default:
                appendText("Unknown command: /" + command + "\n", errorStyle);
                appendText("Type /help for available commands\n", infoStyle);
        }
    }

    private void printHelp() {
        appendText("\nAvailable Commands:\n", infoStyle);
        appendText("  /help              Show this help message\n", defaultStyle);
        appendText("  /init              Initialize the VM\n", defaultStyle);
        appendText("  /reset             Reset the VM\n", defaultStyle);
        appendText("  /status            Show VM status\n", defaultStyle);
        appendText("  /classes [filter]  List loaded classes\n", defaultStyle);
        appendText("  /methods <class>   List methods in a class\n", defaultStyle);
        appendText("  /clear             Clear console output\n", defaultStyle);
        appendText("\nMethod Execution:\n", infoStyle);
        appendText("  ClassName.methodName(args)\n", defaultStyle);
        appendText("  Examples:\n", defaultStyle);
        appendText("    com.example.Utils.decode(\"abc\")\n", defaultStyle);
        appendText("    MyClass.calculate(42, 3.14)\n", defaultStyle);
        appendText("\nArgument Types:\n", infoStyle);
        appendText("  Strings: \"hello\"\n", defaultStyle);
        appendText("  Integers: 42, 0xFF\n", defaultStyle);
        appendText("  Longs: 42L\n", defaultStyle);
        appendText("  Floats: 3.14f\n", defaultStyle);
        appendText("  Doubles: 3.14, 3.14d\n", defaultStyle);
        appendText("  Booleans: true, false\n", defaultStyle);
        appendText("  Null: null\n\n", defaultStyle);
    }

    private void initializeVM() {
        try {
            if (!ProjectService.getInstance().hasProject()) {
                appendText("Error: No project loaded\n", errorStyle);
                return;
            }

            VMExecutionService.getInstance().initialize();
            appendText("VM initialized successfully\n", successStyle);
        } catch (Exception e) {
            appendText("Failed to initialize VM: " + e.getMessage() + "\n", errorStyle);
        }
    }

    private void resetVM() {
        try {
            VMExecutionService.getInstance().reset();
            appendText("VM reset successfully\n", successStyle);
        } catch (Exception e) {
            appendText("Failed to reset VM: " + e.getMessage() + "\n", errorStyle);
        }
    }

    private void showStatus() {
        String status = VMExecutionService.getInstance().getVMStatus();
        appendText(status + "\n", infoStyle);
    }

    private void listClasses(String filter) {
        if (!ProjectService.getInstance().hasProject()) {
            appendText("Error: No project loaded\n", errorStyle);
            return;
        }

        var project = ProjectService.getInstance().getCurrentProject();
        var classes = project.getUserClasses();

        int count = 0;
        int max = 50;

        appendText("Loaded classes:\n", infoStyle);
        for (var classEntry : classes) {
            String name = classEntry.getClassName().replace('/', '.');
            if (filter == null || filter.isEmpty() || name.contains(filter)) {
                appendText("  " + name + "\n", defaultStyle);
                count++;
                if (count >= max) {
                    appendText("  ... and " + (classes.size() - count) + " more\n", infoStyle);
                    break;
                }
            }
        }
        appendText("Total: " + classes.size() + " user classes\n\n", infoStyle);
    }

    private void listMethods(String className) {
        if (className == null || className.isEmpty()) {
            appendText("Usage: /methods <className>\n", errorStyle);
            return;
        }

        if (!ProjectService.getInstance().hasProject()) {
            appendText("Error: No project loaded\n", errorStyle);
            return;
        }

        String internalName = className.replace('.', '/');

        if (JdkClassFilter.isJdkClass(internalName)) {
            appendText("Cannot list JDK class methods: " + className + "\n", errorStyle);
            return;
        }

        ClassFile classFile = ProjectService.getInstance().getCurrentProject().getClassPool().get(internalName);

        if (classFile == null) {
            appendText("Class not found: " + className + "\n", errorStyle);
            return;
        }

        appendText("Methods in " + className + ":\n", infoStyle);
        for (MethodEntry method : classFile.getMethods()) {
            String flags = "";
            int access = method.getAccess();
            if ((access & 0x0008) != 0) flags += "static ";
            if ((access & 0x0001) != 0) flags += "public ";
            else if ((access & 0x0002) != 0) flags += "private ";
            else if ((access & 0x0004) != 0) flags += "protected ";

            appendText("  " + flags + method.getName() + method.getDesc() + "\n", defaultStyle);
        }
        appendText("\n", defaultStyle);
    }

    private void handleMethodCall(CommandParser.ParseResult result) {
        if (!VMExecutionService.getInstance().isInitialized()) {
            appendText("VM not initialized. Initializing...\n", infoStyle);
            try {
                VMExecutionService.getInstance().initialize();
                appendText("VM initialized successfully\n", successStyle);
            } catch (Exception e) {
                appendText("Failed to initialize VM: " + e.getMessage() + "\n", errorStyle);
                return;
            }
        }

        String className = result.getClassName();
        String methodName = result.getMethodName();
        Object[] args = result.getMethodArgs();

        appendText("Executing: " + className.replace('/', '.') + "." + methodName + "...\n", infoStyle);

        setExecuting(true);

        SwingWorker<ExecutionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ExecutionResult doInBackground() {
                return VMExecutionService.getInstance().executeStaticMethod(className, methodName, null, args);
            }

            @Override
            protected void done() {
                setExecuting(false);
                try {
                    ExecutionResult execResult = get();
                    displayResult(execResult);
                } catch (Exception e) {
                    appendText("Execution failed: " + e.getMessage() + "\n", errorStyle);
                }
            }
        };

        worker.execute();
    }

    private void displayResult(ExecutionResult result) {
        if (result.isSuccess()) {
            appendText("Result: " + result.getFormattedReturnValue() + "\n", successStyle);
        } else {
            appendText("Execution failed\n", errorStyle);
            if (result.getException() != null) {
                appendText("  " + result.getException().getMessage() + "\n", errorStyle);
            }
        }
        appendText(result.getFormattedStatistics() + "\n\n", infoStyle);
    }

    private void stopExecution() {
        VMExecutionService.getInstance().interrupt();
        appendText("Execution interrupted\n", errorStyle);
        setExecuting(false);
    }

    private void clearOutput() {
        outputPane.setText("");
        printWelcome();
    }

    private void setExecuting(boolean executing) {
        this.isExecuting = executing;
        runButton.setEnabled(!executing);
        stopButton.setEnabled(executing);
        inputField.setEnabled(!executing);
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) {
            return;
        }

        historyIndex += direction;
        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            inputField.setText("");
            return;
        }

        inputField.setText(commandHistory.get(historyIndex));
        inputField.setCaretPosition(inputField.getText().length());
    }

    private void appendText(String text, Style style) {
        StyledDocument doc = outputPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
            outputPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public void focusInput() {
        inputField.requestFocusInWindow();
    }
}
