package com.tonic.ui.script;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.editor.ASTEditor;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.bridge.AnnotationBridge;
import com.tonic.ui.script.bridge.ASTBridge;
import com.tonic.ui.script.bridge.CommonAPI;
import com.tonic.ui.script.bridge.IRBridge;
import com.tonic.ui.script.engine.*;
import com.tonic.ui.script.store.ScriptStore;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class ScriptEditorPanel extends ThemedJPanel implements ThemeManager.ThemeChangeListener {

    private final MainFrame mainFrame;

    // UI Components
    private RSyntaxTextArea codeEditor;
    private RTextScrollPane editorScrollPane;
    private JTextArea consoleOutput;
    private JComboBox<Script.Mode> modeComboBox;
    private JComboBox<String> targetComboBox;
    private JComboBox<ClassEntryModel> classComboBox;
    private JComboBox<MethodEntryModel> methodComboBox;
    private JLabel statusLabel;
    private JList<Script> scriptList;
    private DefaultListModel<Script> scriptListModel;

    // State
    private ProjectModel projectModel;
    private Script currentScript;
    private Runnable onTransformComplete;

    public ScriptEditorPanel(MainFrame mainFrame) {
        super(BackgroundStyle.TERTIARY, new BorderLayout());
        this.mainFrame = mainFrame;
        this.currentScript = new Script("Untitled", Script.Mode.AST, "");

        // Create main content
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(200);
        mainSplit.setBorder(null);

        // Left: Script library
        mainSplit.setLeftComponent(createLibraryPanel());

        // Right: Editor and console
        JSplitPane editorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        editorSplit.setDividerLocation(400);
        editorSplit.setBorder(null);

        editorSplit.setTopComponent(createEditorPanel());
        editorSplit.setBottomComponent(createConsolePanel());

        mainSplit.setRightComponent(editorSplit);

        add(createToolbar(), BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        loadBuiltInScripts();
        applySyntaxColors();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        applySyntaxColors();
        updateEditorColors();
    }

    private void applySyntaxColors() {
        if (codeEditor == null) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        SyntaxScheme scheme = codeEditor.getSyntaxScheme();

        scheme.getStyle(Token.RESERVED_WORD).foreground = theme.getJavaKeyword();
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = theme.getJavaKeyword();
        scheme.getStyle(Token.DATA_TYPE).foreground = theme.getJavaType();
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = theme.getJavaString();
        scheme.getStyle(Token.LITERAL_CHAR).foreground = theme.getJavaString();
        scheme.getStyle(Token.LITERAL_BACKQUOTE).foreground = theme.getJavaString();
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = theme.getJavaNumber();
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = theme.getJavaNumber();
        scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL).foreground = theme.getJavaNumber();
        scheme.getStyle(Token.LITERAL_BOOLEAN).foreground = theme.getJavaConstant();
        scheme.getStyle(Token.COMMENT_EOL).foreground = theme.getJavaComment();
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = theme.getJavaComment();
        scheme.getStyle(Token.COMMENT_DOCUMENTATION).foreground = theme.getJavaComment();
        scheme.getStyle(Token.FUNCTION).foreground = theme.getJavaMethod();
        scheme.getStyle(Token.VARIABLE).foreground = theme.getJavaLocalVar();
        scheme.getStyle(Token.OPERATOR).foreground = theme.getJavaOperator();
        scheme.getStyle(Token.SEPARATOR).foreground = theme.getTextPrimary();
        scheme.getStyle(Token.IDENTIFIER).foreground = theme.getTextPrimary();
        scheme.getStyle(Token.ANNOTATION).foreground = theme.getJavaAnnotation();

        codeEditor.revalidate();
        codeEditor.repaint();
    }

    private void updateEditorColors() {
        if (codeEditor == null) return;

        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        codeEditor.setBackground(theme.getBgPrimary());
        codeEditor.setForeground(theme.getTextPrimary());
        codeEditor.setCaretColor(theme.getTextPrimary());
        codeEditor.setCurrentLineHighlightColor(theme.getBgSecondary());
        codeEditor.setSelectionColor(theme.getSelection());

        if (editorScrollPane != null) {
            editorScrollPane.getGutter().setBackground(theme.getBgSecondary());
            editorScrollPane.getGutter().setLineNumberColor(theme.getTextSecondary());
            editorScrollPane.getGutter().setBorderColor(theme.getBorder());
        }

        codeEditor.revalidate();
        codeEditor.repaint();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        // Top row: selectors
        JPanel selectorsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectorsRow.setBackground(JStudioTheme.getBgSecondary());

        selectorsRow.add(new JLabel("Mode:"));
        modeComboBox = new JComboBox<>(Script.Mode.values());
        modeComboBox.setSelectedItem(Script.Mode.AST);
        modeComboBox.addActionListener(e -> currentScript.setMode((Script.Mode) modeComboBox.getSelectedItem()));
        styleComboBox(modeComboBox);
        selectorsRow.add(modeComboBox);

        selectorsRow.add(Box.createHorizontalStrut(16));

        selectorsRow.add(new JLabel("Target:"));
        targetComboBox = new JComboBox<>(new String[]{"Current Method", "Current Class", "All Classes"});
        styleComboBox(targetComboBox);
        selectorsRow.add(targetComboBox);

        selectorsRow.add(Box.createHorizontalStrut(16));

        selectorsRow.add(new JLabel("Class:"));
        classComboBox = new JComboBox<>();
        classComboBox.setRenderer(new ClassComboRenderer());
        classComboBox.addActionListener(e -> updateMethodComboBox());
        styleComboBox(classComboBox);
        selectorsRow.add(classComboBox);

        selectorsRow.add(new JLabel("Method:"));
        methodComboBox = new JComboBox<>();
        methodComboBox.setRenderer(new MethodComboRenderer());
        styleComboBox(methodComboBox);
        selectorsRow.add(methodComboBox);

        // Bottom row: action buttons
        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonsRow.setBackground(JStudioTheme.getBgSecondary());

        JButton runButton = createToolbarButton("Run", Icons.getIcon("play"), e -> runScript());
        buttonsRow.add(runButton);

        JButton saveButton = createToolbarButton("Save", Icons.getIcon("save"), e -> saveScript());
        buttonsRow.add(saveButton);

        JButton loadButton = createToolbarButton("Load", Icons.getIcon("folder"), e -> loadScript());
        buttonsRow.add(loadButton);

        buttonsRow.add(Box.createHorizontalStrut(16));

        JButton helpButton = createToolbarButton("Help", Icons.getIcon("info"), e -> showDocumentation());
        buttonsRow.add(helpButton);

        // Combine rows
        JPanel rows = new JPanel(new GridLayout(2, 1));
        rows.setBackground(JStudioTheme.getBgSecondary());
        rows.add(selectorsRow);
        rows.add(buttonsRow);

        toolbar.add(rows, BorderLayout.CENTER);

        return toolbar;
    }

    private JPanel createLibraryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, JStudioTheme.getBorder()));

        JLabel header = new JLabel("Scripts");
        header.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL).deriveFont(Font.BOLD));
        header.setForeground(JStudioTheme.getTextPrimary());
        header.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(header, BorderLayout.NORTH);

        scriptListModel = new DefaultListModel<>();
        scriptList = new JList<>(scriptListModel);
        scriptList.setBackground(JStudioTheme.getBgSecondary());
        scriptList.setForeground(JStudioTheme.getTextPrimary());
        scriptList.setSelectionBackground(JStudioTheme.getSelection());
        scriptList.setSelectionForeground(JStudioTheme.getTextPrimary());
        scriptList.setCellRenderer(new ScriptListRenderer());
        scriptList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Script selected = scriptList.getSelectedValue();
                if (selected != null) {
                    loadScriptToEditor(selected);
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(scriptList);
        listScroll.setBorder(null);
        panel.add(listScroll, BorderLayout.CENTER);

        // New script button
        JButton newButton = new JButton("+ New Script");
        newButton.setBackground(JStudioTheme.getBgTertiary());
        newButton.setForeground(JStudioTheme.getTextPrimary());
        newButton.addActionListener(e -> createNewScript());
        panel.add(newButton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgTertiary());

        codeEditor = new RSyntaxTextArea();
        codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        codeEditor.setCodeFoldingEnabled(true);
        codeEditor.setAntiAliasingEnabled(true);
        codeEditor.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL + 1));
        codeEditor.setBackground(JStudioTheme.getBgPrimary());
        codeEditor.setForeground(JStudioTheme.getTextPrimary());
        codeEditor.setCaretColor(JStudioTheme.getTextPrimary());
        codeEditor.setCurrentLineHighlightColor(JStudioTheme.getBgSecondary());
        codeEditor.setSelectionColor(JStudioTheme.getSelection());

        // Set default content
        codeEditor.setText(getDefaultScriptContent());

        editorScrollPane = new RTextScrollPane(codeEditor);
        editorScrollPane.setLineNumbersEnabled(true);
        editorScrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        editorScrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        editorScrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());
        editorScrollPane.setBorder(null);

        panel.add(editorScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgTertiary());

        JLabel header = new JLabel("Console Output");
        header.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE).deriveFont(Font.BOLD));
        header.setForeground(JStudioTheme.getTextSecondary());
        header.setBorder(new EmptyBorder(4, 8, 4, 8));
        header.setBackground(JStudioTheme.getBgSecondary());
        header.setOpaque(true);
        panel.add(header, BorderLayout.NORTH);

        consoleOutput = new JTextArea();
        consoleOutput.setEditable(false);
        consoleOutput.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        consoleOutput.setBackground(JStudioTheme.getBgPrimary());
        consoleOutput.setForeground(JStudioTheme.getTextPrimary());
        consoleOutput.setCaretColor(JStudioTheme.getTextPrimary());

        JScrollPane scrollPane = new JScrollPane(consoleOutput);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Clear button
        JButton clearButton = new JButton("Clear");
        clearButton.setBackground(JStudioTheme.getBgSecondary());
        clearButton.setForeground(JStudioTheme.getTextSecondary());
        clearButton.addActionListener(e -> consoleOutput.setText(""));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgSecondary());
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        panel.add(statusLabel);

        return panel;
    }

    private String getDefaultScriptContent() {
        return "// @mode: ast\n" +
               "// @name: My Transform\n" +
               "\n" +
               "// Example: Log all method calls\n" +
               "ast.onMethodCall((call) => {\n" +
               "    log(\"Found method call: \" + call.name);\n" +
               "    // Return null to remove, or return modified node\n" +
               "    // return null;\n" +
               "});\n";
    }

    // ==================== Script Execution ====================

    private void runScript() {
        String code = codeEditor.getText();
        Script.Mode mode = (Script.Mode) modeComboBox.getSelectedItem();
        String target = (String) targetComboBox.getSelectedItem();

        consoleOutput.setText("");
        appendToConsole("Running script in " + mode + " mode...\n");
        statusLabel.setText("Running...");

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                try {
                    int count = 0;

                    if ("All Classes".equals(target)) {
                        if (projectModel == null) {
                            publish("ERROR: No project loaded");
                            return 0;
                        }
                        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
                            // Run annotation handlers (once per class)
                            count += runAnnotationsOnClass(code, classEntry);
                            // Run method-level handlers
                            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                                count += runOnMethod(code, mode, classEntry, methodModel);
                            }
                            // Clear decompilation cache
                            classEntry.setDecompilationCache(null);
                        }
                    } else if ("Current Class".equals(target)) {
                        ClassEntryModel classEntry = (ClassEntryModel) classComboBox.getSelectedItem();
                        if (classEntry == null) {
                            publish("ERROR: No class selected");
                            return 0;
                        }
                        // Run annotation handlers (once per class)
                        count += runAnnotationsOnClass(code, classEntry);
                        // Run method-level handlers
                        for (MethodEntryModel methodModel : classEntry.getMethods()) {
                            count += runOnMethod(code, mode, classEntry, methodModel);
                        }
                        classEntry.setDecompilationCache(null);
                    } else {
                        ClassEntryModel classEntry = (ClassEntryModel) classComboBox.getSelectedItem();
                        MethodEntryModel methodModel = (MethodEntryModel) methodComboBox.getSelectedItem();
                        if (classEntry == null || methodModel == null) {
                            publish("ERROR: No method selected");
                            return 0;
                        }
                        // Run annotation handlers (once per class)
                        count += runAnnotationsOnClass(code, classEntry);
                        count += runOnMethod(code, mode, classEntry, methodModel);
                        classEntry.setDecompilationCache(null);
                    }

                    return count;
                } catch (Exception e) {
                    publish("ERROR: " + e.getMessage());
                    e.printStackTrace();
                    return 0;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendToConsole(msg + "\n");
                }
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    statusLabel.setText("Completed: " + count + " modifications");
                    appendToConsole("\nTransform completed with " + count + " modifications.\n");

                    // Notify that transforms are complete
                    if (onTransformComplete != null) {
                        onTransformComplete.run();
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    appendToConsole("ERROR: " + e.getMessage() + "\n");
                }
            }
        };

        worker.execute();
    }

    private int runOnMethod(String code, Script.Mode mode, ClassEntryModel classEntry, MethodEntryModel methodModel) {
        MethodEntry method = methodModel.getMethodEntry();
        if (method.getCodeAttribute() == null) return 0;
        if (method.getName().startsWith("<")) return 0;

        int count = 0;

        // Create interpreter
        ScriptInterpreter interpreter = new ScriptInterpreter();

        // Setup common API
        CommonAPI commonAPI = new CommonAPI();
        commonAPI.setContext(
            classEntry.getClassName(),
            method.getName(),
            method.getDesc()
        );
        commonAPI.setCallbacks(
            msg -> SwingUtilities.invokeLater(() -> appendToConsole(msg + "\n")),
            msg -> SwingUtilities.invokeLater(() -> appendToConsole("WARN: " + msg + "\n")),
            msg -> SwingUtilities.invokeLater(() -> appendToConsole("ERROR: " + msg + "\n"))
        );
        commonAPI.registerIn(interpreter);

        // Parse script
        ScriptLexer lexer = new ScriptLexer(code);
        List<ScriptToken> tokens = lexer.tokenize();
        if (!lexer.getErrors().isEmpty()) {
            for (String err : lexer.getErrors()) {
                appendToConsole("Lexer error: " + err + "\n");
            }
            return 0;
        }

        ScriptParser parser = new ScriptParser(tokens);
        List<ScriptAST> statements = parser.parse();
        if (!parser.getErrors().isEmpty()) {
            for (String err : parser.getErrors()) {
                appendToConsole("Parser error: " + err + "\n");
            }
            return 0;
        }

        // Run based on mode
        if (mode == Script.Mode.AST || mode == Script.Mode.BOTH) {
            count += runASTMode(interpreter, statements, classEntry, method);
        }

        if (mode == Script.Mode.IR || mode == Script.Mode.BOTH) {
            count += runIRMode(interpreter, statements, method, methodModel, classEntry);
        }

        return count;
    }

    private int runASTMode(ScriptInterpreter interpreter, List<ScriptAST> statements,
                           ClassEntryModel classEntry, MethodEntry method) {
        try {
            // Lift method to IR then recover AST
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(method);
            if (irMethod == null || irMethod.getEntryBlock() == null) return 0;

            BlockStmt methodBody = MethodRecoverer.recoverMethod(irMethod, method);
            if (methodBody == null) return 0;

            // Create AST bridge
            ASTBridge astBridge = new ASTBridge(interpreter);
            astBridge.setLogCallback(msg -> SwingUtilities.invokeLater(() -> appendToConsole(msg + "\n")));

            // Register ast object
            interpreter.getGlobalContext().defineConstant("ast", astBridge.createAstObject());

            // Execute script to register handlers
            interpreter.execute(statements);

            // Apply handlers to AST
            ASTEditor editor = new ASTEditor(methodBody, method.getName(), method.getDesc(),
                classEntry.getClassName());
            return astBridge.applyTo(editor);

        } catch (Exception e) {
            appendToConsole("AST mode error: " + e.getMessage() + "\n");
            return 0;
        }
    }

    private int runIRMode(ScriptInterpreter interpreter, List<ScriptAST> statements,
                          MethodEntry method, MethodEntryModel methodModel, ClassEntryModel classEntry) {
        try {
            // Build SSA
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(method);
            if (irMethod == null || irMethod.getEntryBlock() == null) return 0;

            // Create IR bridge
            IRBridge irBridge = new IRBridge(interpreter);
            irBridge.setLogCallback(msg -> SwingUtilities.invokeLater(() -> appendToConsole(msg + "\n")));

            // Register ir object
            interpreter.getGlobalContext().defineConstant("ir", irBridge.createIRObject());

            // Execute script to register handlers
            interpreter.execute(statements);

            // Apply handlers to IR
            int count = irBridge.applyTo(irMethod);

            // Clear cached IR
            methodModel.setIrCache(null);

            return count;

        } catch (Exception e) {
            appendToConsole("IR mode error: " + e.getMessage() + "\n");
            return 0;
        }
    }

    private int runAnnotationsOnClass(String code, ClassEntryModel classEntry) {
        try {
            // Create interpreter
            ScriptInterpreter interpreter = new ScriptInterpreter();

            // Setup common API with class context
            CommonAPI commonAPI = new CommonAPI();
            commonAPI.setContext(classEntry.getClassName(), "", "");
            commonAPI.setCallbacks(
                msg -> SwingUtilities.invokeLater(() -> appendToConsole(msg + "\n")),
                msg -> SwingUtilities.invokeLater(() -> appendToConsole("WARN: " + msg + "\n")),
                msg -> SwingUtilities.invokeLater(() -> appendToConsole("ERROR: " + msg + "\n"))
            );
            commonAPI.registerIn(interpreter);

            // Create annotation bridge
            AnnotationBridge annotationBridge = new AnnotationBridge(interpreter);
            annotationBridge.setLogCallback(msg -> SwingUtilities.invokeLater(() -> appendToConsole(msg + "\n")));

            // Register annotations object
            interpreter.getGlobalContext().defineConstant("annotations", annotationBridge.createAnnotationObject());

            // Parse and execute script to register handlers
            ScriptLexer lexer = new ScriptLexer(code);
            List<ScriptToken> tokens = lexer.tokenize();
            if (!lexer.getErrors().isEmpty()) {
                return 0;
            }

            ScriptParser parser = new ScriptParser(tokens);
            List<ScriptAST> statements = parser.parse();
            if (!parser.getErrors().isEmpty()) {
                return 0;
            }

            interpreter.execute(statements);

            // Apply annotation handlers if any were registered
            if (annotationBridge.hasHandlers()) {
                return annotationBridge.applyToClass(classEntry);
            }

            return 0;

        } catch (Exception e) {
            appendToConsole("Annotation processing error: " + e.getMessage() + "\n");
            return 0;
        }
    }

    // ==================== Script Management ====================

    private void loadBuiltInScripts() {
        // Add built-in example scripts
        Script example1 = new Script("Remove Debug Prints", Script.Mode.AST,
            "// @mode: ast\n// @name: Remove Debug Prints\n\n" +
            "ast.onMethodCall((call) => {\n" +
            "    if (call.receiver?.type == \"java.io.PrintStream\") {\n" +
            "        if (call.name == \"println\" || call.name == \"print\") {\n" +
            "            log(\"Removing: \" + call.name);\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "});\n");
        example1.setBuiltIn(true);

        Script example2 = new Script("Log Method Calls", Script.Mode.AST,
            "// @mode: ast\n// @name: Log Method Calls\n\n" +
            "let count = 0;\n" +
            "ast.onMethodCall((call) => {\n" +
            "    log(\"Method: \" + call.name + \" on \" + (call.owner || \"unknown\"));\n" +
            "    count++;\n" +
            "});\n" +
            "log(\"Total: \" + count + \" method calls\");\n");
        example2.setBuiltIn(true);

        Script example3 = new Script("Constant Folding (IR)", Script.Mode.IR,
            "// @mode: ir\n// @name: Fold Constants\n\n" +
            "ir.onBinaryOp((instr) => {\n" +
            "    if (instr.left.isConstant && instr.right.isConstant) {\n" +
            "        let leftVal = instr.left.value;\n" +
            "        let rightVal = instr.right.value;\n" +
            "        log(\"Folding: \" + leftVal + \" \" + instr.op + \" \" + rightVal);\n" +
            "        // Return constant with computed value\n" +
            "        // return ir.intConstant(leftVal + rightVal);\n" +
            "    }\n" +
            "});\n");
        example3.setBuiltIn(true);

        Script example4 = new Script("Strip @Named Annotations", Script.Mode.AST,
            "// @name: Strip @Named Annotations\n\n" +
            "// Remove @Named from classes\n" +
            "annotations.onClassAnnotation((anno) => {\n" +
            "    if (anno.simpleName == \"Named\") {\n" +
            "        log(\"Removing @Named from class\");\n" +
            "        return null;\n" +
            "    }\n" +
            "    return anno;\n" +
            "});\n\n" +
            "// Remove @Named from methods\n" +
            "annotations.onMethodAnnotation((anno) => {\n" +
            "    if (anno.simpleName == \"Named\") {\n" +
            "        log(\"Removing @Named from \" + anno.target);\n" +
            "        return null;\n" +
            "    }\n" +
            "    return anno;\n" +
            "});\n\n" +
            "// Remove @Named from fields\n" +
            "annotations.onFieldAnnotation((anno) => {\n" +
            "    if (anno.simpleName == \"Named\") {\n" +
            "        log(\"Removing @Named from \" + anno.target);\n" +
            "        return null;\n" +
            "    }\n" +
            "    return anno;\n" +
            "});\n");
        example4.setBuiltIn(true);

        scriptListModel.addElement(example1);
        scriptListModel.addElement(example2);
        scriptListModel.addElement(example3);
        scriptListModel.addElement(example4);

        // Load user scripts
        ScriptStore.loadUserScripts().forEach(scriptListModel::addElement);
    }

    private void loadScriptToEditor(Script script) {
        currentScript = script;
        codeEditor.setText(script.getContent());
        modeComboBox.setSelectedItem(script.getMode());
        statusLabel.setText("Loaded: " + script.getName());
    }

    private void createNewScript() {
        Script newScript = new Script("New Script", Script.Mode.AST, getDefaultScriptContent());
        scriptListModel.addElement(newScript);
        scriptList.setSelectedValue(newScript, true);
        loadScriptToEditor(newScript);
    }

    private void saveScript() {
        currentScript.setContent(codeEditor.getText());
        currentScript.setMode((Script.Mode) modeComboBox.getSelectedItem());
        currentScript.setName(Script.parseNameFromContent(codeEditor.getText()));

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Script");
        chooser.setSelectedFile(new File(currentScript.getName() + ".jstudio-script"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ScriptStore.saveScript(currentScript, chooser.getSelectedFile());
                statusLabel.setText("Saved: " + chooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Save failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadScript() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Script");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JStudio Scripts", "jstudio-script", "js"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Script loaded = ScriptStore.loadScript(chooser.getSelectedFile());
                scriptListModel.addElement(loaded);
                scriptList.setSelectedValue(loaded, true);
                loadScriptToEditor(loaded);
                statusLabel.setText("Loaded: " + chooser.getSelectedFile().getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Load failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ==================== UI Helpers ====================

    private void showDocumentation() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        ScriptDocumentationDialog dialog = new ScriptDocumentationDialog(owner);
        dialog.setVisible(true);
    }

    private void appendToConsole(String text) {
        consoleOutput.append(text);
        consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
    }

    private JButton createToolbarButton(String text, Icon icon, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text, icon);
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.addActionListener(listener);
        return button;
    }

    private void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(JStudioTheme.getBgTertiary());
        combo.setForeground(JStudioTheme.getTextPrimary());
        combo.setMaximumSize(new Dimension(200, 25));
    }

    private void updateMethodComboBox() {
        methodComboBox.removeAllItems();
        ClassEntryModel selected = (ClassEntryModel) classComboBox.getSelectedItem();
        if (selected != null) {
            for (MethodEntryModel method : selected.getMethods()) {
                if (method.getMethodEntry().getCodeAttribute() != null) {
                    methodComboBox.addItem(method);
                }
            }
        }
    }

    // ==================== Public API ====================

    public void setProjectModel(ProjectModel model) {
        this.projectModel = model;

        classComboBox.removeAllItems();
        if (model != null) {
            for (ClassEntryModel classEntry : model.getAllClasses()) {
                classComboBox.addItem(classEntry);
            }
        }
    }

    public void setClass(ClassEntryModel classEntry) {
        if (classEntry != null) {
            classComboBox.setSelectedItem(classEntry);
        }
    }

    public void setOnTransformComplete(Runnable callback) {
        this.onTransformComplete = callback;
    }

    // ==================== Renderers ====================

    private static class ScriptListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Script) {
                Script script = (Script) value;
                setText((script.isBuiltIn() ? "[Built-in] " : "") + script.getName());
                setForeground(isSelected ? JStudioTheme.getTextPrimary() :
                    (script.isBuiltIn() ? JStudioTheme.getTextSecondary() : JStudioTheme.getTextPrimary()));
            }
            return this;
        }
    }

    private static class ClassComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ClassEntryModel) {
                setText(((ClassEntryModel) value).getSimpleName());
            }
            return this;
        }
    }

    private static class MethodComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MethodEntryModel) {
                setText(((MethodEntryModel) value).getDisplaySignature());
            }
            return this;
        }
    }
}
