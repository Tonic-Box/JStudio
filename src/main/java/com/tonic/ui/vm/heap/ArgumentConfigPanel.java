package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.testgen.MethodFuzzer;

import com.tonic.analysis.execution.heap.ArrayInstance;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgumentConfigPanel extends JPanel {

    public enum Mode { MANUAL, FUZZ }

    private Mode currentMode = Mode.MANUAL;
    private MethodEntry method;
    private List<String> paramTypes = new ArrayList<>();
    private SimpleHeapManager heapManager;

    private JPanel modeButtonPanel;
    private JToggleButton manualBtn;
    private JToggleButton fuzzBtn;

    private JPanel contentPanel;
    private CardLayout cardLayout;

    private JPanel manualPanel;
    private List<JTextField> paramFields = new ArrayList<>();
    private Map<Integer, Object[]> arrayValues = new HashMap<>();

    private JPanel fuzzPanel;
    private JCheckBox edgeCasesCheck;
    private JCheckBox randomCheck;
    private JCheckBox nullsCheck;
    private JSpinner iterationsSpinner;
    private JLabel comboInfoLabel;

    private List<Object[]> fuzzCombinations;
    private int currentComboIndex = 0;

    public ArgumentConfigPanel() {
        setLayout(new BorderLayout(5, 5));
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Arguments",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));

        modeButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        modeButtonPanel.setBackground(JStudioTheme.getBgSecondary());

        ButtonGroup modeGroup = new ButtonGroup();
        manualBtn = new JToggleButton("Manual");
        manualBtn.setSelected(true);
        manualBtn.addActionListener(e -> switchMode(Mode.MANUAL));
        modeGroup.add(manualBtn);

        fuzzBtn = new JToggleButton("Fuzz");
        fuzzBtn.addActionListener(e -> switchMode(Mode.FUZZ));
        modeGroup.add(fuzzBtn);

        modeButtonPanel.add(manualBtn);
        modeButtonPanel.add(fuzzBtn);
        add(modeButtonPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(JStudioTheme.getBgSecondary());

        manualPanel = new JPanel();
        manualPanel.setLayout(new BoxLayout(manualPanel, BoxLayout.Y_AXIS));
        manualPanel.setBackground(JStudioTheme.getBgSecondary());

        JScrollPane manualScroll = new JScrollPane(manualPanel);
        manualScroll.setBorder(null);
        manualScroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        manualScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        fuzzPanel = createFuzzPanel();

        contentPanel.add(manualScroll, "MANUAL");
        contentPanel.add(fuzzPanel, "FUZZ");

        add(contentPanel, BorderLayout.CENTER);

        updateNoMethodState();
    }

    private JPanel createFuzzPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        edgeCasesCheck = new JCheckBox("Edge cases", true);
        edgeCasesCheck.setBackground(JStudioTheme.getBgSecondary());
        edgeCasesCheck.setForeground(JStudioTheme.getTextPrimary());
        edgeCasesCheck.addActionListener(e -> regenerateCombinations());

        randomCheck = new JCheckBox("Random", true);
        randomCheck.setBackground(JStudioTheme.getBgSecondary());
        randomCheck.setForeground(JStudioTheme.getTextPrimary());
        randomCheck.addActionListener(e -> regenerateCombinations());

        nullsCheck = new JCheckBox("Include nulls", true);
        nullsCheck.setBackground(JStudioTheme.getBgSecondary());
        nullsCheck.setForeground(JStudioTheme.getTextPrimary());
        nullsCheck.addActionListener(e -> regenerateCombinations());

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(edgeCasesCheck, gbc);
        gbc.gridx = 1;
        panel.add(randomCheck, gbc);
        gbc.gridx = 2;
        panel.add(nullsCheck, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel iterLabel = new JLabel("Iterations:");
        iterLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(iterLabel, gbc);

        gbc.gridx = 1;
        iterationsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        iterationsSpinner.setPreferredSize(new Dimension(60, 25));
        iterationsSpinner.addChangeListener(e -> regenerateCombinations());
        panel.add(iterationsSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        comboInfoLabel = new JLabel("No method selected");
        comboInfoLabel.setForeground(JStudioTheme.getTextSecondary());
        panel.add(comboInfoLabel, gbc);

        return panel;
    }

    private void switchMode(Mode mode) {
        currentMode = mode;
        cardLayout.show(contentPanel, mode == Mode.MANUAL ? "MANUAL" : "FUZZ");
        if (mode == Mode.FUZZ && method != null) {
            regenerateCombinations();
        }
    }

    public void setMethod(MethodEntry method) {
        this.method = method;
        this.paramTypes = parseParameterTypes(method != null ? method.getDesc() : "()V");
        this.currentComboIndex = 0;
        this.fuzzCombinations = null;
        this.arrayValues.clear();

        rebuildManualFields();
        if (currentMode == Mode.FUZZ) {
            regenerateCombinations();
        }
        updateFuzzInfo();
    }

    public void setHeapManager(SimpleHeapManager heapManager) {
        this.heapManager = heapManager;
    }

    private void rebuildManualFields() {
        manualPanel.removeAll();
        paramFields.clear();

        if (paramTypes.isEmpty()) {
            JLabel noParams = new JLabel("(no parameters)");
            noParams.setForeground(JStudioTheme.getTextSecondary());
            noParams.setAlignmentX(Component.LEFT_ALIGNMENT);
            manualPanel.add(noParams);
        } else {
            for (int i = 0; i < paramTypes.size(); i++) {
                String type = paramTypes.get(i);
                JPanel row = new JPanel(new BorderLayout(5, 0));
                row.setBackground(JStudioTheme.getBgSecondary());
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

                JLabel label = new JLabel(formatType(type) + " arg" + i + ":");
                label.setForeground(JStudioTheme.getTextPrimary());
                label.setPreferredSize(new Dimension(100, 24));

                JTextField field = new JTextField(getDefaultValue(type));
                paramFields.add(field);

                if (type.startsWith("[")) {
                    field.setEditable(false);
                    field.setBackground(JStudioTheme.getBgTertiary());
                    field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    field.setToolTipText("Click to edit array");

                    final int paramIndex = i;
                    final String componentType = getArrayComponentType(type);
                    field.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            openArrayEditor(paramIndex, componentType, field);
                        }
                    });

                    arrayValues.put(i, new Object[0]);
                    field.setText("[]");
                } else {
                    field.setEditable(true);
                    field.setEnabled(true);
                }

                row.add(label, BorderLayout.WEST);
                row.add(field, BorderLayout.CENTER);
                row.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                manualPanel.add(row);
            }
        }

        manualPanel.revalidate();
        manualPanel.repaint();
    }

    private void openArrayEditor(int paramIndex, String componentType, JTextField displayField) {
        Object[] currentValues = arrayValues.getOrDefault(paramIndex, new Object[0]);

        Window owner = SwingUtilities.getWindowAncestor(this);
        ArrayEditorDialog dialog = new ArrayEditorDialog(owner, componentType, currentValues);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            Object[] newValues = dialog.getElements();
            arrayValues.put(paramIndex, newValues);
            displayField.setText(ArrayEditorDialog.formatArrayDisplay(newValues, componentType));
        }
    }

    private String getArrayComponentType(String arrayType) {
        if (arrayType.startsWith("[")) {
            return arrayType.substring(1);
        }
        return arrayType;
    }

    private void regenerateCombinations() {
        if (method == null || paramTypes.isEmpty()) {
            fuzzCombinations = new ArrayList<>();
            fuzzCombinations.add(new Object[0]);
            currentComboIndex = 0;
            updateFuzzInfo();
            return;
        }

        MethodFuzzer.FuzzConfig config = new MethodFuzzer.FuzzConfig();
        config.setIncludeEdgeCases(edgeCasesCheck.isSelected());
        config.setIncludeRandom(randomCheck.isSelected());
        config.setIncludeNulls(nullsCheck.isSelected());
        config.setIterationsPerType((Integer) iterationsSpinner.getValue());

        MethodFuzzer fuzzer = new MethodFuzzer(
            method.getOwnerName(),
            method.getName(),
            method.getDesc(),
            config
        );

        fuzzCombinations = fuzzer.generateInputSets();
        currentComboIndex = 0;
        updateFuzzInfo();
    }

    private void updateFuzzInfo() {
        if (method == null) {
            comboInfoLabel.setText("No method selected");
        } else if (paramTypes.isEmpty()) {
            comboInfoLabel.setText("Method has no parameters");
        } else if (fuzzCombinations != null) {
            comboInfoLabel.setText("Combination " + (currentComboIndex + 1) + " of " + fuzzCombinations.size());
        }
    }

    private void updateNoMethodState() {
        JLabel noMethod = new JLabel("Select a method to configure arguments");
        noMethod.setForeground(JStudioTheme.getTextSecondary());
        manualPanel.add(noMethod);
    }

    public ConcreteValue[] getArguments() {
        if (method == null) {
            return new ConcreteValue[0];
        }

        boolean isStatic = (method.getAccess() & 0x0008) != 0;

        Object[] rawArgs;
        if (paramTypes.isEmpty()) {
            rawArgs = new Object[0];
        } else if (currentMode == Mode.MANUAL) {
            rawArgs = collectManualArguments();
        } else {
            rawArgs = getCurrentFuzzArguments();
        }

        ConcreteValue[] paramValues = convertToConcreteValues(rawArgs);

        if (isStatic) {
            return paramValues;
        }

        ConcreteValue[] result = new ConcreteValue[paramValues.length + 1];
        String ownerClass = method.getOwnerName();
        ObjectInstance receiver = heapManager.newObject(ownerClass);
        result[0] = ConcreteValue.reference(receiver);
        System.arraycopy(paramValues, 0, result, 1, paramValues.length);
        return result;
    }

    private Object[] collectManualArguments() {
        Object[] args = new Object[paramFields.size()];
        for (int i = 0; i < paramFields.size(); i++) {
            String type = paramTypes.get(i);
            if (type.startsWith("[")) {
                args[i] = arrayValues.getOrDefault(i, new Object[0]);
            } else {
                String value = paramFields.get(i).getText().trim();
                args[i] = parseArgumentValue(value, type);
            }
        }
        return args;
    }

    private Object[] getCurrentFuzzArguments() {
        if (fuzzCombinations == null || fuzzCombinations.isEmpty()) {
            return new Object[0];
        }
        return fuzzCombinations.get(currentComboIndex);
    }

    public boolean hasNextCombination() {
        return fuzzCombinations != null && currentComboIndex < fuzzCombinations.size() - 1;
    }

    public void nextCombination() {
        if (hasNextCombination()) {
            currentComboIndex++;
            updateFuzzInfo();
        }
    }

    public void resetCombinations() {
        currentComboIndex = 0;
        updateFuzzInfo();
    }

    public int getTotalCombinations() {
        return fuzzCombinations != null ? fuzzCombinations.size() : 0;
    }

    public int getCurrentCombinationIndex() {
        return currentComboIndex;
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public String getCurrentArgsDescription() {
        Object[] args = currentMode == Mode.MANUAL ? collectManualArguments() : getCurrentFuzzArguments();
        if (args.length == 0) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatArgValue(args[i]));
        }
        sb.append(")");
        return sb.toString();
    }

    private String formatArgValue(Object val) {
        if (val == null) return "null";
        if (val instanceof String) return "\"" + val + "\"";
        if (val instanceof Character) return "'" + val + "'";
        return String.valueOf(val);
    }

    private Object parseArgumentValue(String value, String type) {
        boolean isEmpty = value == null || value.isEmpty() || value.equalsIgnoreCase("null");

        try {
            switch (type) {
                case "I":
                    return isEmpty ? 0 : Integer.parseInt(value);
                case "B":
                    return isEmpty ? (byte) 0 : (byte) Integer.parseInt(value);
                case "S":
                    return isEmpty ? (short) 0 : (short) Integer.parseInt(value);
                case "C":
                    if (isEmpty) return 'a';
                    if (value.length() == 1) return value.charAt(0);
                    if (value.startsWith("'") && value.endsWith("'") && value.length() == 3) {
                        return value.charAt(1);
                    }
                    return (char) Integer.parseInt(value);
                case "J":
                    return isEmpty ? 0L : Long.parseLong(value.replace("L", "").replace("l", ""));
                case "F":
                    return isEmpty ? 0.0f : Float.parseFloat(value.replace("f", "").replace("F", ""));
                case "D":
                    return isEmpty ? 0.0 : Double.parseDouble(value.replace("d", "").replace("D", ""));
                case "Z":
                    return isEmpty ? false : Boolean.parseBoolean(value);
                default:
                    if (isEmpty) return null;
                    if (type.equals("Ljava/lang/String;")) {
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                            return value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                    return null;
            }
        } catch (NumberFormatException e) {
            return getDefaultForType(type);
        }
    }

    private Object getDefaultForType(String type) {
        switch (type) {
            case "I": return 0;
            case "B": return (byte) 0;
            case "S": return (short) 0;
            case "C": return 'a';
            case "J": return 0L;
            case "F": return 0.0f;
            case "D": return 0.0;
            case "Z": return false;
            default: return null;
        }
    }

    private ConcreteValue[] convertToConcreteValues(Object[] args) {
        if (args == null || args.length == 0) {
            return new ConcreteValue[0];
        }

        ConcreteValue[] result = new ConcreteValue[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = convertToConcreteValue(args[i], paramTypes.get(i));
        }
        return result;
    }

    private ConcreteValue convertToConcreteValue(Object value, String type) {
        if (value == null) {
            if (isPrimitiveType(type)) {
                value = getDefaultForType(type);
            } else {
                return ConcreteValue.nullRef();
            }
        }

        if (type.startsWith("[") && value instanceof Object[]) {
            return convertArrayToConcreteValue((Object[]) value, type);
        }

        if (value instanceof Integer) {
            return ConcreteValue.intValue((Integer) value);
        } else if (value instanceof Long) {
            return ConcreteValue.longValue((Long) value);
        } else if (value instanceof Float) {
            return ConcreteValue.floatValue((Float) value);
        } else if (value instanceof Double) {
            return ConcreteValue.doubleValue((Double) value);
        } else if (value instanceof Boolean) {
            return ConcreteValue.intValue((Boolean) value ? 1 : 0);
        } else if (value instanceof Byte) {
            return ConcreteValue.intValue((Byte) value);
        } else if (value instanceof Short) {
            return ConcreteValue.intValue((Short) value);
        } else if (value instanceof Character) {
            return ConcreteValue.intValue((Character) value);
        } else if (value instanceof String) {
            if (heapManager != null) {
                return ConcreteValue.reference(heapManager.internString((String) value));
            }
            return ConcreteValue.nullRef();
        }
        return ConcreteValue.nullRef();
    }

    private ConcreteValue convertArrayToConcreteValue(Object[] elements, String arrayType) {
        if (heapManager == null) {
            return ConcreteValue.nullRef();
        }

        String componentType = getArrayComponentType(arrayType);
        int length = elements.length;

        ArrayInstance array = heapManager.newArray(componentType, length);
        if (array == null) {
            return ConcreteValue.nullRef();
        }

        for (int i = 0; i < length; i++) {
            Object elem = elements[i];
            setArrayElement(array, componentType, i, elem);
        }

        return ConcreteValue.reference(array);
    }

    private void setArrayElement(ArrayInstance array, String componentType, int index, Object value) {
        if (value == null) {
            if (!isPrimitiveType(componentType)) {
                array.set(index, null);
            }
            return;
        }

        try {
            switch (componentType) {
                case "I":
                    array.setInt(index, ((Number) value).intValue());
                    break;
                case "J":
                    array.setLong(index, ((Number) value).longValue());
                    break;
                case "F":
                    array.setFloat(index, ((Number) value).floatValue());
                    break;
                case "D":
                    array.setDouble(index, ((Number) value).doubleValue());
                    break;
                case "B":
                    array.setByte(index, ((Number) value).byteValue());
                    break;
                case "S":
                    array.setShort(index, ((Number) value).shortValue());
                    break;
                case "Z":
                    array.setBoolean(index, (Boolean) value);
                    break;
                case "C":
                    if (value instanceof Character) {
                        array.setChar(index, (Character) value);
                    } else if (value instanceof Number) {
                        array.setChar(index, (char) ((Number) value).intValue());
                    }
                    break;
                case "Ljava/lang/String;":
                    if (value instanceof String && heapManager != null) {
                        array.set(index, heapManager.internString((String) value));
                    }
                    break;
                default:
                    if (!isPrimitiveType(componentType)) {
                        array.set(index, null);
                    }
            }
        } catch (Exception e) {
            // Ignore element set errors
        }
    }

    private boolean isPrimitiveType(String type) {
        if (type == null || type.isEmpty()) return false;
        char c = type.charAt(0);
        return c == 'B' || c == 'C' || c == 'D' || c == 'F' ||
               c == 'I' || c == 'J' || c == 'S' || c == 'Z';
    }

    private ConcreteValue convertElementToConcreteValue(Object value, String componentType) {
        if (value == null) {
            return ConcreteValue.nullRef();
        }

        switch (componentType) {
            case "I":
                if (value instanceof Number) return ConcreteValue.intValue(((Number) value).intValue());
                break;
            case "J":
                if (value instanceof Number) return ConcreteValue.longValue(((Number) value).longValue());
                break;
            case "F":
                if (value instanceof Number) return ConcreteValue.floatValue(((Number) value).floatValue());
                break;
            case "D":
                if (value instanceof Number) return ConcreteValue.doubleValue(((Number) value).doubleValue());
                break;
            case "B":
                if (value instanceof Number) return ConcreteValue.intValue(((Number) value).byteValue());
                break;
            case "S":
                if (value instanceof Number) return ConcreteValue.intValue(((Number) value).shortValue());
                break;
            case "Z":
                if (value instanceof Boolean) return ConcreteValue.intValue((Boolean) value ? 1 : 0);
                break;
            case "C":
                if (value instanceof Character) return ConcreteValue.intValue((Character) value);
                if (value instanceof Number) return ConcreteValue.intValue(((Number) value).intValue());
                break;
            case "Ljava/lang/String;":
                if (value instanceof String && heapManager != null) {
                    return ConcreteValue.reference(heapManager.internString((String) value));
                }
                break;
        }

        if (componentType.startsWith("Ljava/lang/Integer")) {
            if (value instanceof Number) return ConcreteValue.intValue(((Number) value).intValue());
        } else if (componentType.startsWith("Ljava/lang/Long")) {
            if (value instanceof Number) return ConcreteValue.longValue(((Number) value).longValue());
        }

        return ConcreteValue.nullRef();
    }

    private List<String> parseParameterTypes(String descriptor) {
        List<String> types = new ArrayList<>();
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            int start = i;
            while (i < descriptor.length() && descriptor.charAt(i) == '[') i++;

            if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) break;
                i = end + 1;
            } else if (i < descriptor.length()) {
                i++;
            }
            types.add(descriptor.substring(start, i));
        }
        return types;
    }

    private String formatType(String type) {
        if (type.startsWith("[")) {
            return formatType(type.substring(1)) + "[]";
        }
        switch (type) {
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            case "Z": return "boolean";
            case "B": return "byte";
            case "S": return "short";
            case "C": return "char";
            case "V": return "void";
            default:
                if (type.startsWith("L") && type.endsWith(";")) {
                    String className = type.substring(1, type.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return type;
        }
    }

    private String getDefaultValue(String type) {
        switch (type) {
            case "I": case "B": case "S": return "0";
            case "J": return "0L";
            case "F": return "0.0f";
            case "D": return "0.0";
            case "Z": return "false";
            case "C": return "'a'";
            default:
                if (type.equals("Ljava/lang/String;")) return "\"\"";
                return "null";
        }
    }
}
