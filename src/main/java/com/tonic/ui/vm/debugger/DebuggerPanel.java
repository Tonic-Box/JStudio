package com.tonic.ui.vm.debugger;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import com.tonic.parser.constpool.*;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.MethodSelectorPanel;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.heap.ArgumentConfigPanel;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.utill.Opcode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.filechooser.FileNameExtensionFilter;

public class DebuggerPanel extends JPanel implements VMDebugSession.DebugListener {

    private final VMDebugSession session;
    private final JTable bytecodeTable;
    private final BytecodeTableModel bytecodeModel;
    private final StackPanel stackPanel;
    private final LocalsPanel localsPanel;
    private final CallStackPanel callStackPanel;
    private final JLabel statusLabel;
    private final JTextArea outputArea;
    private final MethodSelectorPanel methodSelector;

    private JButton startBtn;
    private JButton stepIntoBtn;
    private JButton stepOverBtn;
    private JButton stepOutBtn;
    private JButton resumeBtn;
    private JButton stopBtn;
    private JComboBox<String> speedSelector;
    private JCheckBox recursiveCheckbox;

    private MethodEntry currentMethod;
    private MethodEntry displayedMethod;
    private JScrollPane bytecodeScroll;
    private ArgumentConfigPanel argumentConfigPanel;
    private boolean recursiveExecution = false;
    private List<InstructionEntry> instructions;
    private Map<Integer, Integer> pcToRowMap;
    private Consumer<String> onStatusMessage;

    private JToggleButton recordBtn;
    private JButton exportTraceBtn;
    private JButton clearTraceBtn;
    private boolean recording = false;
    private ExecutionTrace currentTrace;
    private List<String> lastStackState = new ArrayList<>();

    private JTabbedPane bottomTabbedPane;
    private static final int TAB_ARGUMENTS = 0;
    private static final int TAB_OUTPUT = 1;

    public DebuggerPanel() {
        this.session = new VMDebugSession();
        this.session.addListener(this);
        this.instructions = new ArrayList<>();
        this.pcToRowMap = new HashMap<>();

        setLayout(new BorderLayout(5, 5));
        setBackground(JStudioTheme.getBgPrimary());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbarPanel = createToolbar();
        add(toolbarPanel, BorderLayout.NORTH);

        methodSelector = new MethodSelectorPanel("Method Browser");
        methodSelector.setPreferredSize(new Dimension(250, 0));
        methodSelector.setOnMethodSelected(this::onMethodSelected);

        bytecodeModel = new BytecodeTableModel();
        bytecodeTable = new JTable(bytecodeModel);
        setupBytecodeTable();

        bytecodeScroll = new JScrollPane(bytecodeTable);
        updateBytecodeTitle(null);
        bytecodeScroll.getViewport().setBackground(JStudioTheme.getBgSecondary());

        stackPanel = new StackPanel();
        localsPanel = new LocalsPanel();
        callStackPanel = new CallStackPanel();

        localsPanel.setOnValueEdit((slot, value) -> {
            if (session.setLocalValue(slot, value)) {
                appendOutput("Local at slot " + slot + " updated to: " + formatValue(value));
            }
        });

        stackPanel.setOnValueEdit((index, value) -> {
            if (session.setStackValue(index, value)) {
                appendOutput("Stack at index " + index + " updated to: " + formatValue(value));
            }
        });

        localsPanel.setOnObjectFieldEdit((obj, owner, name, desc, value) -> {
            if (session.setObjectFieldValue(obj, owner, name, desc, value)) {
                appendOutput("Field " + name + " updated on object @" + Integer.toHexString(obj.getId()));
            }
        });

        stackPanel.setOnObjectFieldEdit((obj, owner, name, desc, value) -> {
            if (session.setObjectFieldValue(obj, owner, name, desc, value)) {
                appendOutput("Field " + name + " updated on object @" + Integer.toHexString(obj.getId()));
            }
        });

        callStackPanel.setOnFrameSelected(frame -> {
            if (frame != null) {
                navigateToFrame(frame);
            }
        });

        JSplitPane rightTopSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stackPanel, localsPanel);
        rightTopSplit.setDividerLocation(180);
        rightTopSplit.setResizeWeight(0.5);
        rightTopSplit.setBackground(JStudioTheme.getBgPrimary());

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightTopSplit, callStackPanel);
        rightSplit.setDividerLocation(360);
        rightSplit.setResizeWeight(0.7);
        rightSplit.setBackground(JStudioTheme.getBgPrimary());
        rightSplit.setPreferredSize(new Dimension(280, 0));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bytecodeScroll, rightSplit);
        centerSplit.setDividerLocation(450);
        centerSplit.setResizeWeight(0.6);
        centerSplit.setBackground(JStudioTheme.getBgPrimary());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, methodSelector, centerSplit);
        mainSplit.setDividerLocation(250);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setBackground(JStudioTheme.getBgPrimary());

        argumentConfigPanel = new ArgumentConfigPanel();
        argumentConfigPanel.setPreferredSize(new Dimension(0, 140));
        argumentConfigPanel.setMinimumSize(new Dimension(200, 100));

        outputArea = new JTextArea(4, 50);
        outputArea.setEditable(false);
        outputArea.setBackground(JStudioTheme.getBgSecondary());
        outputArea.setForeground(JStudioTheme.getTextPrimary());
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane outputScroll = new JScrollPane(outputArea);

        bottomTabbedPane = new JTabbedPane();
        bottomTabbedPane.setBackground(JStudioTheme.getBgPrimary());
        bottomTabbedPane.setForeground(JStudioTheme.getTextPrimary());
        bottomTabbedPane.addTab("Arguments", argumentConfigPanel);
        bottomTabbedPane.addTab("Output", outputScroll);
        bottomTabbedPane.setSelectedIndex(TAB_ARGUMENTS);
        bottomTabbedPane.setPreferredSize(new Dimension(0, 160));

        statusLabel = new JLabel("Select a method to debug");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(JStudioTheme.getBgPrimary());
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(bottomTabbedPane, BorderLayout.CENTER);

        add(mainSplit, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void onMethodSelected(MethodEntryModel methodModel) {
        if (methodModel != null) {
            setMethod(methodModel.getMethodEntry());
        }
    }

    public void setMethod(MethodEntry method) {
        this.currentMethod = method;
        loadMethod(method);
        argumentConfigPanel.setMethod(method);
        statusLabel.setText("Loaded: " + method.getOwnerName() + "." + method.getName() + method.getDesc());
        updateButtonStates();
    }

    private void updateBytecodeTitle(MethodEntry method) {
        String title;
        if (method == null) {
            title = "Bytecode";
        } else {
            String className = method.getOwnerName();
            int lastSlash = className.lastIndexOf('/');
            String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
            title = String.format("Bytecode - %s.%s%s", simpleName, method.getName(), method.getDesc());
        }
        bytecodeScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));
    }

    private void loadMethod(MethodEntry method) {
        this.displayedMethod = method;
        updateBytecodeTitle(method);

        instructions.clear();
        pcToRowMap.clear();

        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            bytecodeModel.setInstructions(instructions);
            return;
        }

        byte[] bytecode = code.getCode();
        Map<Integer, Integer> lineNumbers = buildLineNumberMap(code);
        ConstPool constPool = method.getClassFile().getConstPool();

        int pc = 0;
        int index = 0;
        while (pc < bytecode.length) {
            int opcodeByte = bytecode[pc] & 0xFF;
            Opcode opcode = Opcode.fromCode(opcodeByte);
            String mnemonic = opcode != null ? opcode.getMnemonic() : String.format("0x%02X", opcodeByte);

            String operandStr = formatOperandsEnhanced(bytecode, pc, opcode, constPool);
            InstructionCategory category = categorizeOpcode(opcode);

            Integer line = lineNumbers.get(pc);
            int lineNum = line != null ? line : -1;
            instructions.add(new InstructionEntry(index, pc, mnemonic, operandStr, lineNum, category, false));
            pcToRowMap.put(pc, index);

            int instrLength = calculateInstructionLength(bytecode, pc, opcode);
            pc += instrLength;
            index++;
        }

        bytecodeModel.setInstructions(instructions);
    }

    private InstructionCategory categorizeOpcode(Opcode opcode) {
        if (opcode == null) return InstructionCategory.OTHER;

        String name = opcode.getMnemonic();

        if (name.endsWith("load") || name.endsWith("store") || name.startsWith("iload") ||
            name.startsWith("lload") || name.startsWith("fload") || name.startsWith("dload") ||
            name.startsWith("aload") || name.startsWith("istore") || name.startsWith("lstore") ||
            name.startsWith("fstore") || name.startsWith("dstore") || name.startsWith("astore")) {
            return InstructionCategory.LOAD_STORE;
        }

        if (name.startsWith("invoke") || name.equals("invokedynamic")) {
            return InstructionCategory.INVOKE;
        }

        if (name.startsWith("get") || name.startsWith("put")) {
            return InstructionCategory.FIELD_ACCESS;
        }

        if (name.startsWith("if") || name.equals("goto") || name.equals("goto_w") ||
            name.equals("jsr") || name.equals("jsr_w") || name.equals("ret") ||
            name.startsWith("return") || name.equals("ireturn") || name.equals("lreturn") ||
            name.equals("freturn") || name.equals("dreturn") || name.equals("areturn") ||
            name.equals("tableswitch") || name.equals("lookupswitch") || name.equals("athrow")) {
            return InstructionCategory.CONTROL_FLOW;
        }

        if (name.equals("new") || name.equals("newarray") || name.equals("anewarray") ||
            name.equals("multianewarray") || name.equals("arraylength") ||
            name.equals("checkcast") || name.equals("instanceof") || name.equals("monitorenter") ||
            name.equals("monitorexit")) {
            return InstructionCategory.OBJECT;
        }

        if (name.equals("dup") || name.equals("dup_x1") || name.equals("dup_x2") ||
            name.equals("dup2") || name.equals("dup2_x1") || name.equals("dup2_x2") ||
            name.equals("pop") || name.equals("pop2") || name.equals("swap") || name.equals("nop")) {
            return InstructionCategory.STACK;
        }

        if (name.startsWith("iconst") || name.startsWith("lconst") || name.startsWith("fconst") ||
            name.startsWith("dconst") || name.equals("aconst_null") || name.equals("bipush") ||
            name.equals("sipush") || name.startsWith("ldc")) {
            return InstructionCategory.CONSTANT;
        }

        if (name.startsWith("i") || name.startsWith("l") || name.startsWith("f") || name.startsWith("d") ||
            name.contains("add") || name.contains("sub") || name.contains("mul") || name.contains("div") ||
            name.contains("rem") || name.contains("neg") || name.contains("shl") || name.contains("shr") ||
            name.contains("and") || name.contains("or") || name.contains("xor") || name.contains("2")) {
            return InstructionCategory.ARITHMETIC;
        }

        return InstructionCategory.OTHER;
    }

    private String formatOperandsEnhanced(byte[] bytecode, int pc, Opcode opcode, ConstPool constPool) {
        if (opcode == null) return "";
        int operandSize = opcode.getOperandCount();
        if (operandSize == 0) return "";

        StringBuilder sb = new StringBuilder();
        int idx = pc + 1;

        try {
            switch (opcode) {
                case BIPUSH:
                    if (idx < bytecode.length) {
                        sb.append(bytecode[idx]);
                    }
                    break;
                case SIPUSH:
                    if (idx + 1 < bytecode.length) {
                        short value = (short)(((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF));
                        sb.append(value);
                    }
                    break;
                case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE:
                case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE:
                case IF_ICMPGT: case IF_ICMPLE: case IF_ACMPEQ: case IF_ACMPNE:
                case GOTO: case JSR: case IFNULL: case IFNONNULL:
                    if (idx + 1 < bytecode.length) {
                        short offset = (short)(((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF));
                        int target = pc + offset;
                        sb.append("-> ").append(target);
                    }
                    break;
                case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
                case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
                case RET:
                    if (idx < bytecode.length) {
                        sb.append("local[").append(Byte.toUnsignedInt(bytecode[idx])).append("]");
                    }
                    break;
                case LDC:
                    if (idx < bytecode.length) {
                        int cpIdx = Byte.toUnsignedInt(bytecode[idx]);
                        sb.append(resolveConstant(constPool, cpIdx));
                    }
                    break;
                case LDC_W: case LDC2_W:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveConstant(constPool, cpIdx));
                    }
                    break;
                case GETSTATIC: case PUTSTATIC: case GETFIELD: case PUTFIELD:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveFieldRef(constPool, cpIdx));
                    }
                    break;
                case INVOKEVIRTUAL: case INVOKESPECIAL: case INVOKESTATIC:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveMethodRef(constPool, cpIdx));
                    }
                    break;
                case INVOKEINTERFACE:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveInterfaceMethodRef(constPool, cpIdx));
                    }
                    break;
                case INVOKEDYNAMIC:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveInvokeDynamic(constPool, cpIdx));
                    }
                    break;
                case NEW: case ANEWARRAY: case CHECKCAST: case INSTANCEOF:
                    if (idx + 1 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        sb.append(resolveClassRef(constPool, cpIdx));
                    }
                    break;
                case IINC:
                    if (idx + 1 < bytecode.length) {
                        int varIdx = Byte.toUnsignedInt(bytecode[idx]);
                        int constVal = bytecode[idx + 1];
                        sb.append("local[").append(varIdx).append("] += ").append(constVal);
                    }
                    break;
                case NEWARRAY:
                    if (idx < bytecode.length) {
                        int atype = Byte.toUnsignedInt(bytecode[idx]);
                        sb.append(getArrayTypeName(atype)).append("[]");
                    }
                    break;
                case MULTIANEWARRAY:
                    if (idx + 2 < bytecode.length) {
                        int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                        int dims = Byte.toUnsignedInt(bytecode[idx + 2]);
                        sb.append(resolveClassRef(constPool, cpIdx)).append(" dim=").append(dims);
                    }
                    break;
                default:
                    for (int i = 0; i < operandSize && idx + i < bytecode.length; i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(String.format("%02X", bytecode[idx + i]));
                    }
            }
        } catch (Exception e) {
            sb.append("(error)");
        }

        return sb.toString();
    }

    private String resolveConstant(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof StringRefItem) {
                StringRefItem strRef = (StringRefItem) item;
                int utf8Index = strRef.getValue();
                Item<?> utf8Item = constPool.getItem(utf8Index);
                if (utf8Item instanceof Utf8Item) {
                    String value = ((Utf8Item) utf8Item).getValue();
                    if (value.length() > 30) {
                        value = value.substring(0, 27) + "...";
                    }
                    return "\"" + escapeString(value) + "\"";
                }
            } else if (item instanceof IntegerItem) {
                return String.valueOf(((IntegerItem) item).getValue());
            } else if (item instanceof LongItem) {
                return ((LongItem) item).getValue() + "L";
            } else if (item instanceof FloatItem) {
                return ((FloatItem) item).getValue() + "f";
            } else if (item instanceof DoubleItem) {
                return ((DoubleItem) item).getValue() + "d";
            } else if (item instanceof ClassRefItem) {
                return resolveClassRef(constPool, index);
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveClassRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                String name = classRef.getClassName();
                if (name != null) {
                    int lastSlash = name.lastIndexOf('/');
                    return lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
                }
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveFieldRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) item;
                String owner = fieldRef.getOwner();
                String name = fieldRef.getName();
                int lastSlash = owner.lastIndexOf('/');
                String simpleOwner = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
                return simpleOwner + "." + name;
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveMethodRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) item;
                String owner = methodRef.getOwner();
                String name = methodRef.getName();
                int lastSlash = owner.lastIndexOf('/');
                String simpleOwner = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
                return simpleOwner + "." + name + "()";
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveInterfaceMethodRef(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) item;
                String owner = ifaceRef.getOwner();
                String name = ifaceRef.getName();
                int lastSlash = owner.lastIndexOf('/');
                String simpleOwner = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
                return simpleOwner + "." + name + "()";
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String resolveInvokeDynamic(ConstPool constPool, int index) {
        try {
            Item<?> item = constPool.getItem(index);
            if (item instanceof InvokeDynamicItem) {
                InvokeDynamicItem invdyn = (InvokeDynamicItem) item;
                return "invokedynamic#" + index;
            }
            return "#" + index;
        } catch (Exception e) {
            return "#" + index;
        }
    }

    private String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    private Map<Integer, Integer> buildLineNumberMap(CodeAttribute code) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof LineNumberTableAttribute) {
                LineNumberTableAttribute lnt = (LineNumberTableAttribute) attr;
                for (LineNumberTableEntry entry : lnt.getLineNumberTable()) {
                    map.put(entry.getStartPc(), entry.getLineNumber());
                }
            }
        }
        return map;
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel.setBackground(JStudioTheme.getBgPrimary());

        startBtn = createToolButton("Start", null, e -> startDebugging());
        stepIntoBtn = createToolButton("Step Into (F7)", "F7", e -> {
            ensureSessionStarted();
            session.stepInto();
        });
        stepOverBtn = createToolButton("Step Over (F8)", "F8", e -> {
            ensureSessionStarted();
            session.stepOver();
        });
        stepOutBtn = createToolButton("Step Out (Shift+F8)", "shift F8", e -> {
            ensureSessionStarted();
            session.stepOut();
        });
        resumeBtn = createToolButton("Run (F9)", "F9", e -> {
            if (session.isAnimating()) {
                session.stopAnimation();
                updateButtonStates();
            } else {
                ensureSessionStarted();
                session.resumeAnimated();
            }
        });
        stopBtn = createToolButton("Stop", null, e -> stopDebugging());

        speedSelector = new JComboBox<>(new String[]{"5ms", "10ms", "20ms", "50ms", "100ms", "300ms"});
        speedSelector.setSelectedIndex(3);
        speedSelector.setBackground(JStudioTheme.getBgTertiary());
        speedSelector.setForeground(JStudioTheme.getTextPrimary());
        speedSelector.setMaximumSize(new Dimension(80, 28));
        speedSelector.setToolTipText("Animation speed for Run mode");
        speedSelector.addActionListener(e -> {
            int[] delays = {5,10,20, 50, 100, 300};
            session.setAnimationDelay(delays[speedSelector.getSelectedIndex()]);
        });

        recursiveCheckbox = new JCheckBox("Recursive Calls");
        recursiveCheckbox.setSelected(false);
        recursiveCheckbox.setBackground(JStudioTheme.getBgPrimary());
        recursiveCheckbox.setForeground(JStudioTheme.getTextPrimary());
        recursiveCheckbox.setToolTipText("Execute called methods recursively (vs stub with defaults)");
        recursiveCheckbox.addActionListener(e -> {
            recursiveExecution = recursiveCheckbox.isSelected();
        });

        panel.add(startBtn);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(stepIntoBtn);
        panel.add(stepOverBtn);
        panel.add(stepOutBtn);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(resumeBtn);
        panel.add(stopBtn);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(speedSelector);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(recursiveCheckbox);
        panel.add(Box.createHorizontalStrut(20));

        recordBtn = new JToggleButton("Record");
        recordBtn.setBackground(JStudioTheme.getBgSecondary());
        recordBtn.setForeground(JStudioTheme.getTextPrimary());
        recordBtn.setFocusPainted(false);
        recordBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        recordBtn.setToolTipText("Record execution trace");
        recordBtn.addActionListener(e -> toggleRecording());

        exportTraceBtn = createToolButton("Export Trace", null, e -> exportTrace());
        exportTraceBtn.setEnabled(false);
        exportTraceBtn.setToolTipText("Export recorded execution trace");

        clearTraceBtn = createToolButton("Clear Trace", null, e -> clearTrace());
        clearTraceBtn.setEnabled(false);
        clearTraceBtn.setToolTipText("Clear recorded execution trace");

        panel.add(recordBtn);
        panel.add(exportTraceBtn);
        panel.add(clearTraceBtn);

        return panel;
    }

    private JButton createToolButton(String text, String shortcut, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        button.addActionListener(action);

        if (shortcut != null) {
            KeyStroke keyStroke = KeyStroke.getKeyStroke(shortcut);
            if (keyStroke != null) {
                button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(keyStroke, shortcut);
                button.getActionMap().put(shortcut, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (button.isEnabled()) {
                            action.actionPerformed(e);
                        }
                    }
                });
            }
        }

        return button;
    }

    private void setupBytecodeTable() {
        bytecodeTable.setBackground(JStudioTheme.getBgSecondary());
        bytecodeTable.setForeground(JStudioTheme.getTextPrimary());
        bytecodeTable.setGridColor(JStudioTheme.getBorder());
        bytecodeTable.setSelectionBackground(JStudioTheme.getAccent());
        bytecodeTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        bytecodeTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bytecodeTable.getTableHeader().setBackground(JStudioTheme.getBgPrimary());
        bytecodeTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        bytecodeTable.setRowHeight(20);

        bytecodeTable.setDefaultRenderer(Object.class, new BytecodeCellRenderer());

        bytecodeTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        bytecodeTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        bytecodeTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        bytecodeTable.getColumnModel().getColumn(3).setPreferredWidth(200);
    }

    public void loadMethod(MethodEntryModel methodModel) {
        if (methodModel == null) return;

        this.currentMethod = methodModel.getMethodEntry();
        loadBytecode(currentMethod);
        argumentConfigPanel.setMethod(currentMethod);

        statusLabel.setText("Method loaded: " + currentMethod.getOwnerName() + "." + currentMethod.getName());
        appendOutput("Loaded method: " + currentMethod.getName() + currentMethod.getDesc());
        updateButtonStates();
    }

    private void loadBytecode(MethodEntry method) {
        this.displayedMethod = method;
        updateBytecodeTitle(method);

        instructions.clear();
        pcToRowMap.clear();

        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            appendOutput("Method has no code (abstract or native)");
            bytecodeModel.setInstructions(instructions);
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            appendOutput("Method has empty bytecode");
            bytecodeModel.setInstructions(instructions);
            return;
        }

        Map<Integer, Integer> lineNumberTable = buildLineNumberTable(code);
        ConstPool constPool = method.getClassFile().getConstPool();

        int pc = 0;
        int index = 0;
        int currentLine = -1;

        while (pc < bytecode.length) {
            int opcodeValue = Byte.toUnsignedInt(bytecode[pc]);
            Opcode opcode = Opcode.fromCode(opcodeValue);
            String mnemonic = opcode.getMnemonic();

            if (lineNumberTable.containsKey(pc)) {
                currentLine = lineNumberTable.get(pc);
            }

            int instrLength = calculateInstructionLength(bytecode, pc, opcode);
            String operands = formatOperandsEnhanced(bytecode, pc, opcode, constPool);
            InstructionCategory category = categorizeOpcode(opcode);

            pcToRowMap.put(pc, index);
            instructions.add(new InstructionEntry(
                index,
                pc,
                mnemonic,
                operands,
                currentLine,
                category,
                false
            ));

            pc += instrLength;
            index++;
        }

        bytecodeModel.setInstructions(instructions);
    }

    private Map<Integer, Integer> buildLineNumberTable(CodeAttribute code) {
        Map<Integer, Integer> table = new HashMap<>();
        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof LineNumberTableAttribute) {
                LineNumberTableAttribute lnt = (LineNumberTableAttribute) attr;
                for (LineNumberTableEntry entry : lnt.getLineNumberTable()) {
                    table.put(entry.getStartPc(), entry.getLineNumber());
                }
            }
        }
        return table;
    }

    private int calculateInstructionLength(byte[] code, int pc, Opcode opcode) {
        int baseLength = 1 + opcode.getOperandCount();

        switch (opcode) {
            case TABLESWITCH: {
                int padding = (4 - ((pc + 1) % 4)) % 4;
                int idx = pc + 1 + padding;
                if (idx + 12 > code.length) return baseLength;
                int low = readInt(code, idx + 4);
                int high = readInt(code, idx + 8);
                int count = high - low + 1;
                return 1 + padding + 12 + (count * 4);
            }
            case LOOKUPSWITCH: {
                int padding = (4 - ((pc + 1) % 4)) % 4;
                int idx = pc + 1 + padding;
                if (idx + 8 > code.length) return baseLength;
                int npairs = readInt(code, idx + 4);
                return 1 + padding + 8 + (npairs * 8);
            }
            case WIDE: {
                if (pc + 1 >= code.length) return 1;
                int subOpcode = Byte.toUnsignedInt(code[pc + 1]);
                if (subOpcode == 0x84) {
                    return 6;
                } else {
                    return 4;
                }
            }
            default:
                return baseLength;
        }
    }

    private int readInt(byte[] code, int pos) {
        if (pos + 3 >= code.length) return 0;
        return ((code[pos] & 0xFF) << 24) |
               ((code[pos + 1] & 0xFF) << 16) |
               ((code[pos + 2] & 0xFF) << 8) |
               (code[pos + 3] & 0xFF);
    }

    private String formatOperands(byte[] bytecode, int pc, Opcode opcode, CodeAttribute code) {
        int operandSize = opcode.getOperandCount();
        if (operandSize == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int idx = pc + 1;

        switch (opcode) {
            case BIPUSH:
                if (idx < bytecode.length) {
                    sb.append(bytecode[idx]);
                }
                break;
            case SIPUSH:
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case GOTO:
            case JSR:
            case IFNULL:
            case IFNONNULL:
                if (idx + 1 < bytecode.length) {
                    int value = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                    sb.append(value);
                }
                break;
            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD:
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
            case RET:
                if (idx < bytecode.length) {
                    sb.append(Byte.toUnsignedInt(bytecode[idx]));
                }
                break;
            case LDC:
                if (idx < bytecode.length) {
                    int cpIdx = Byte.toUnsignedInt(bytecode[idx]);
                    sb.append("#").append(cpIdx);
                }
                break;
            case LDC_W:
            case LDC2_W:
            case GETSTATIC:
            case PUTSTATIC:
            case GETFIELD:
            case PUTFIELD:
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case NEW:
            case ANEWARRAY:
            case CHECKCAST:
            case INSTANCEOF:
                if (idx + 1 < bytecode.length) {
                    int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                    sb.append("#").append(cpIdx);
                }
                break;
            case IINC:
                if (idx + 1 < bytecode.length) {
                    int varIdx = Byte.toUnsignedInt(bytecode[idx]);
                    int constVal = bytecode[idx + 1];
                    sb.append(varIdx).append(", ").append(constVal);
                }
                break;
            case INVOKEINTERFACE:
            case INVOKEDYNAMIC:
                if (idx + 1 < bytecode.length) {
                    int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                    sb.append("#").append(cpIdx);
                }
                break;
            case NEWARRAY:
                if (idx < bytecode.length) {
                    int atype = Byte.toUnsignedInt(bytecode[idx]);
                    sb.append(getArrayTypeName(atype));
                }
                break;
            case MULTIANEWARRAY:
                if (idx + 2 < bytecode.length) {
                    int cpIdx = ((bytecode[idx] & 0xFF) << 8) | (bytecode[idx + 1] & 0xFF);
                    int dims = Byte.toUnsignedInt(bytecode[idx + 2]);
                    sb.append("#").append(cpIdx).append(", ").append(dims);
                }
                break;
            default:
                for (int i = 0; i < operandSize && idx + i < bytecode.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(String.format("%02X", bytecode[idx + i]));
                }
        }

        return sb.toString();
    }

    private String getArrayTypeName(int atype) {
        switch (atype) {
            case 4: return "boolean";
            case 5: return "char";
            case 6: return "float";
            case 7: return "double";
            case 8: return "byte";
            case 9: return "short";
            case 10: return "int";
            case 11: return "long";
            default: return "type" + atype;
        }
    }

    public void startDebugging(Object... args) {
        if (currentMethod == null) {
            JOptionPane.showMessageDialog(this,
                "No method loaded. Select a method first.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            VMExecutionService vmService = VMExecutionService.getInstance();
            if (!vmService.isInitialized()) {
                vmService.initialize();
            }

            argumentConfigPanel.setHeapManager(vmService.getHeapManager());
            argumentConfigPanel.setClassResolver(vmService.getClassResolver());
            localsPanel.setClassResolver(vmService.getClassResolver());
            stackPanel.setClassResolver(vmService.getClassResolver());

            ConcreteValue[] vmArgs = argumentConfigPanel.getArguments();
            session.start(currentMethod, recursiveExecution, (Object[]) vmArgs);

            updateButtonStates();
            String modeStr = recursiveExecution ? " (recursive mode)" : " (stub mode)";
            appendOutput("Started debugging: " + currentMethod.getName() + modeStr);
        } catch (Exception e) {
            appendOutput("Failed to start: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to start debugging: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ensureSessionStarted() {
        if (!session.isStarted() && currentMethod != null) {
            try {
                VMExecutionService vmService = VMExecutionService.getInstance();
                if (!vmService.isInitialized()) {
                    vmService.initialize();
                }

                argumentConfigPanel.setHeapManager(vmService.getHeapManager());
                argumentConfigPanel.setClassResolver(vmService.getClassResolver());
                localsPanel.setClassResolver(vmService.getClassResolver());
                stackPanel.setClassResolver(vmService.getClassResolver());

                ConcreteValue[] vmArgs = argumentConfigPanel.getArguments();
                session.start(currentMethod, recursiveExecution, (Object[]) vmArgs);

                updateButtonStates();
                String modeStr = recursiveExecution ? " (recursive mode)" : " (stub mode)";
                appendOutput("Started debugging: " + currentMethod.getName() + modeStr);
            } catch (Exception e) {
                appendOutput("Failed to start: " + e.getMessage());
            }
        }
    }

    public void stopDebugging() {
        session.stop();
        updateButtonStates();
        clearHighlight();
        appendOutput("Debugging stopped");

        if (recording && currentTrace != null) {
            currentTrace.complete("Session stopped by user", false);
            exportTraceBtn.setEnabled(true);
            clearTraceBtn.setEnabled(true);
        }
    }

    private void toggleRecording() {
        recording = recordBtn.isSelected();
        if (recording) {
            if (currentMethod != null) {
                currentTrace = new ExecutionTrace(
                    currentMethod.getOwnerName(),
                    currentMethod.getName(),
                    currentMethod.getDesc()
                );
                lastStackState.clear();
                appendOutput("Recording started - execution trace will be captured");
            } else {
                appendOutput("Recording enabled - will start capturing when debugging begins");
                currentTrace = null;
            }
            recordBtn.setBackground(new Color(180, 80, 80));
            recordBtn.setForeground(Color.WHITE);
            exportTraceBtn.setEnabled(false);
            clearTraceBtn.setEnabled(false);
        } else {
            if (currentTrace != null && !currentTrace.getSteps().isEmpty()) {
                appendOutput("Recording stopped - " + currentTrace.getSteps().size() + " steps captured");
                exportTraceBtn.setEnabled(true);
                clearTraceBtn.setEnabled(true);
            } else {
                appendOutput("Recording stopped - no steps captured");
            }
            recordBtn.setBackground(JStudioTheme.getBgSecondary());
            recordBtn.setForeground(JStudioTheme.getTextPrimary());
        }
    }

    private void captureStep(DebugStateModel state) {
        if (!recording || currentTrace == null) return;

        ExecutionStep step = new ExecutionStep(
            state.getClassName(),
            state.getMethodName(),
            state.getDescriptor(),
            state.getInstructionIndex(),
            state.getLineNumber(),
            getInstructionAtPC(state.getInstructionIndex()),
            state.getCallStack().size()
        );

        step.setStackBefore(new ArrayList<>(lastStackState));

        List<String> currentStack = new ArrayList<>();
        for (StackEntry entry : state.getOperandStack()) {
            currentStack.add(entry.toString());
        }
        step.setStackAfter(currentStack);
        lastStackState = new ArrayList<>(currentStack);

        List<String> locals = new ArrayList<>();
        for (LocalEntry entry : state.getLocalVariables()) {
            locals.add("local" + entry.getSlot() + ": " + entry.toString());
        }
        step.setLocals(locals);

        currentTrace.addStep(step);
    }

    private String getInstructionAtPC(int pc) {
        Integer row = pcToRowMap.get(pc);
        if (row != null && row < instructions.size()) {
            InstructionEntry entry = instructions.get(row);
            if (entry.operands != null && !entry.operands.isEmpty()) {
                return entry.mnemonic + " " + entry.operands;
            }
            return entry.mnemonic;
        }
        return "PC=" + pc;
    }

    private void exportTrace() {
        if (currentTrace == null || currentTrace.getSteps().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No execution trace to export",
                "Export Trace",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Execution Trace");
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown files (*.md)", "md"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));

        String defaultName = currentTrace.getMethodName() + "_trace";
        chooser.setSelectedFile(new File(defaultName + ".md"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String path = file.getAbsolutePath();

            boolean isMarkdown = path.endsWith(".md") ||
                (chooser.getFileFilter() instanceof FileNameExtensionFilter &&
                 ((FileNameExtensionFilter)chooser.getFileFilter()).getExtensions()[0].equals("md"));

            if (!path.endsWith(".md") && !path.endsWith(".txt")) {
                path += isMarkdown ? ".md" : ".txt";
                file = new File(path);
            }

            try (FileWriter writer = new FileWriter(file)) {
                if (path.endsWith(".md")) {
                    writer.write(currentTrace.toMarkdown());
                } else {
                    writer.write(currentTrace.toCompactText());
                }
                appendOutput("Trace exported to: " + file.getName());
                JOptionPane.showMessageDialog(this,
                    "Trace exported successfully!\n" + currentTrace.getSteps().size() + " steps saved.",
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                appendOutput("Failed to export trace: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Failed to export trace: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearTrace() {
        if (currentTrace == null || currentTrace.getSteps().isEmpty()) {
            appendOutput("No trace to clear");
            return;
        }

        int stepCount = currentTrace.getSteps().size();
        currentTrace = null;
        lastStackState.clear();
        exportTraceBtn.setEnabled(false);
        clearTraceBtn.setEnabled(false);
        appendOutput("Trace cleared (" + stepCount + " steps removed)");
    }

    public boolean isDebugging() {
        return session.isStarted();
    }

    private void updateButtonStates() {
        boolean hasMethod = currentMethod != null;
        boolean canStep = session.isPaused();
        boolean isRunning = session.isStarted() && !session.isStopped();
        boolean isAnimating = session.isAnimating();
        boolean canStartStepping = hasMethod && (!session.isStarted() || canStep) && !isAnimating;

        startBtn.setEnabled(hasMethod && !isRunning);
        stepIntoBtn.setEnabled(canStartStepping);
        stepOverBtn.setEnabled(canStartStepping);
        stepOutBtn.setEnabled(canStartStepping);
        resumeBtn.setEnabled(canStartStepping || isAnimating);
        resumeBtn.setText(isAnimating ? "Pause (F9)" : "Run (F9)");
        stopBtn.setEnabled(isRunning || isAnimating);
    }

    private void highlightInstruction(int pc) {
        Integer rowIndex = pcToRowMap.get(pc);
        if (rowIndex == null) {
            for (Map.Entry<Integer, Integer> entry : pcToRowMap.entrySet()) {
                if (entry.getKey() <= pc) {
                    rowIndex = entry.getValue();
                }
            }
        }

        for (InstructionEntry entry : instructions) {
            entry.current = false;
        }

        if (rowIndex != null && rowIndex < instructions.size()) {
            instructions.get(rowIndex).current = true;
            final int row = rowIndex;
            SwingUtilities.invokeLater(() -> {
                int tableRowCount = bytecodeTable.getRowCount();
                if (row >= 0 && row < tableRowCount) {
                    bytecodeTable.setRowSelectionInterval(row, row);
                    bytecodeTable.scrollRectToVisible(bytecodeTable.getCellRect(row, 0, true));
                }
            });
        }

        bytecodeModel.fireTableDataChanged();
    }

    private void clearHighlight() {
        for (InstructionEntry entry : instructions) {
            entry.current = false;
        }
        bytecodeModel.fireTableDataChanged();
    }

    private void navigateToFrame(FrameEntry frame) {
        if (currentMethod != null &&
            frame.getClassName().equals(currentMethod.getOwnerName()) &&
            frame.getMethodName().equals(currentMethod.getName())) {
            highlightInstruction(frame.getInstructionIndex());
        } else {
            ClassFile classFile = ProjectService.getInstance().getCurrentProject()
                .getClassPool().get(frame.getClassName());
            if (classFile != null) {
                for (MethodEntry m : classFile.getMethods()) {
                    if (m.getName().equals(frame.getMethodName()) &&
                        m.getDesc().equals(frame.getDescriptor())) {
                        currentMethod = m;
                        loadBytecode(m);
                        highlightInstruction(frame.getInstructionIndex());
                        break;
                    }
                }
            }
        }
    }

    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private String formatValue(ConcreteValue value) {
        if (value == null) {
            return "null";
        }
        switch (value.getTag()) {
            case INT:
                return String.valueOf(value.asInt());
            case LONG:
                return value.asLong() + "L";
            case FLOAT:
                return value.asFloat() + "f";
            case DOUBLE:
                return String.valueOf(value.asDouble());
            case NULL:
                return "null";
            case REFERENCE:
                var ref = value.asReference();
                return ref != null ? ref.toString() : "null";
            default:
                return value.toString();
        }
    }

    public void setOnStatusMessage(Consumer<String> handler) {
        this.onStatusMessage = handler;
    }

    @Override
    public void onStateChanged(DebugStateModel state) {
        SwingUtilities.invokeLater(() -> {
            boolean methodChanged = displayedMethod == null ||
                !state.getClassName().equals(displayedMethod.getOwnerName()) ||
                !state.getMethodName().equals(displayedMethod.getName()) ||
                !state.getDescriptor().equals(displayedMethod.getDesc());

            if (methodChanged) {
                ClassFile classFile = ProjectService.getInstance().getCurrentProject()
                    .getClassPool().get(state.getClassName());
                if (classFile != null) {
                    for (MethodEntry m : classFile.getMethods()) {
                        if (m.getName().equals(state.getMethodName()) &&
                            m.getDesc().equals(state.getDescriptor())) {
                            loadBytecode(m);
                            break;
                        }
                    }
                }
            }

            if (recording) {
                captureStep(state);
            }

            highlightInstruction(state.getInstructionIndex());
            stackPanel.updateStack(state.getOperandStack());
            localsPanel.updateLocals(state.getLocalVariables());
            callStackPanel.updateCallStack(state.getCallStack());

            statusLabel.setText(String.format("Paused at %s.%s @ PC=%d (Line %d)",
                state.getSimpleClassName(),
                state.getMethodName(),
                state.getInstructionIndex(),
                state.getLineNumber()));

            updateButtonStates();
        });
    }

    @Override
    public void onSessionStarted() {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Debug session started");
            updateButtonStates();
            bottomTabbedPane.setSelectedIndex(TAB_OUTPUT);

            if (recording && currentTrace == null && currentMethod != null) {
                currentTrace = new ExecutionTrace(
                    currentMethod.getOwnerName(),
                    currentMethod.getName(),
                    currentMethod.getDesc()
                );
                lastStackState.clear();
                appendOutput("Recording execution trace...");
            }
        });
    }

    @Override
    public void onSessionStopped(String reason) {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Debug session stopped: " + reason);
            statusLabel.setText("Stopped: " + reason);
            clearHighlight();
            stackPanel.clear();
            localsPanel.clear();
            callStackPanel.clear();
            updateButtonStates();
            bottomTabbedPane.setSelectedIndex(TAB_ARGUMENTS);

            if (recording && currentTrace != null) {
                boolean normal = reason.toLowerCase().contains("complete") ||
                                 reason.toLowerCase().contains("return");
                currentTrace.complete(reason, normal);
                appendOutput("Trace recording complete - " + currentTrace.getSteps().size() + " steps captured");
                exportTraceBtn.setEnabled(true);
                clearTraceBtn.setEnabled(true);
            }
        });
    }

    @Override
    public void onBreakpointHit(String location) {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Breakpoint hit: " + location);
        });
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Error: " + message);
            statusLabel.setText("Error: " + message);
        });
    }

    private enum InstructionCategory {
        LOAD_STORE,
        ARITHMETIC,
        CONTROL_FLOW,
        INVOKE,
        FIELD_ACCESS,
        OBJECT,
        STACK,
        CONSTANT,
        OTHER
    }

    private static class InstructionEntry {
        final int index;
        final int offset;
        final String mnemonic;
        final String operands;
        final int lineNumber;
        final InstructionCategory category;
        boolean current;

        InstructionEntry(int index, int offset, String mnemonic, String operands, int lineNumber, InstructionCategory category, boolean current) {
            this.index = index;
            this.offset = offset;
            this.mnemonic = mnemonic;
            this.operands = operands;
            this.lineNumber = lineNumber;
            this.category = category;
            this.current = current;
        }

        InstructionEntry(int index, int offset, String mnemonic, String operands, int lineNumber, boolean current) {
            this(index, offset, mnemonic, operands, lineNumber, InstructionCategory.OTHER, current);
        }
    }

    private class BytecodeTableModel extends AbstractTableModel {
        private final String[] columnNames = {"#", "Offset", "Opcode", "Operands"};
        private List<InstructionEntry> instructions = new ArrayList<>();

        public void setInstructions(List<InstructionEntry> instructions) {
            this.instructions = new ArrayList<>(instructions);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return instructions.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InstructionEntry entry = instructions.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.index;
                case 1: return entry.offset;
                case 2: return entry.mnemonic;
                case 3: return entry.operands;
                default: return "";
            }
        }

        public InstructionEntry getEntryAt(int row) {
            if (row >= 0 && row < instructions.size()) {
                return instructions.get(row);
            }
            return null;
        }
    }

    private class BytecodeCellRenderer extends DefaultTableCellRenderer {
        private final Color colorLoadStore = new Color(86, 156, 214);
        private final Color colorArithmetic = new Color(181, 206, 168);
        private final Color colorControlFlow = new Color(197, 134, 192);
        private final Color colorInvoke = new Color(220, 220, 170);
        private final Color colorFieldAccess = new Color(156, 220, 254);
        private final Color colorObject = new Color(78, 201, 176);
        private final Color colorStack = new Color(128, 128, 128);
        private final Color colorConstant = new Color(206, 145, 120);
        private final Color colorString = new Color(206, 145, 120);
        private final Color colorOther = JStudioTheme.getTextPrimary();
        private final Color highlightedTextColor = new Color(255, 255, 255);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            InstructionEntry entry = bytecodeModel.getEntryAt(row);
            boolean isHighlighted = isSelected || (entry != null && entry.current);

            if (isSelected) {
                setBackground(JStudioTheme.getAccent());
            } else if (entry != null && entry.current) {
                setBackground(JStudioTheme.getSuccess().darker());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }

            if (isHighlighted) {
                setForeground(highlightedTextColor);
            } else if (entry != null) {
                Color textColor = getCategoryColor(entry.category);
                if (column == 2) {
                    setForeground(textColor);
                } else if (column == 3) {
                    String operands = entry.operands;
                    if (operands.startsWith("\"")) {
                        setForeground(colorString);
                    } else if (operands.contains(".") && !operands.matches(".*\\d+\\.\\d+.*")) {
                        setForeground(colorInvoke);
                    } else if (operands.startsWith("-> ")) {
                        setForeground(colorControlFlow);
                    } else if (operands.startsWith("local[")) {
                        setForeground(colorLoadStore);
                    } else {
                        setForeground(JStudioTheme.getTextSecondary());
                    }
                } else {
                    setForeground(JStudioTheme.getTextSecondary());
                }
            } else {
                setForeground(JStudioTheme.getTextPrimary());
            }

            return this;
        }

        private Color getCategoryColor(InstructionCategory category) {
            if (category == null) return colorOther;
            switch (category) {
                case LOAD_STORE: return colorLoadStore;
                case ARITHMETIC: return colorArithmetic;
                case CONTROL_FLOW: return colorControlFlow;
                case INVOKE: return colorInvoke;
                case FIELD_ACCESS: return colorFieldAccess;
                case OBJECT: return colorObject;
                case STACK: return colorStack;
                case CONSTANT: return colorConstant;
                default: return colorOther;
            }
        }
    }
}
