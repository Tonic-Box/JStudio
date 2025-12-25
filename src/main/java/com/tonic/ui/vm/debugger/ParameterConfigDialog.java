package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.DescriptorParser;
import com.tonic.ui.vm.testgen.objectspec.ObjectBuilderDialog;
import com.tonic.ui.vm.testgen.objectspec.ObjectSpec;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterConfigDialog extends JDialog {

    private static final Map<String, Object[]> parameterCache = new HashMap<>();

    private final MethodEntry method;
    private final String methodKey;
    private final List<ParameterField> parameterFields;
    private Object[] result;
    private boolean confirmed;
    private final boolean isStatic;

    public ParameterConfigDialog(Component parent, MethodEntry method) {
        super(SwingUtilities.getWindowAncestor(parent), "Configure Parameters", ModalityType.APPLICATION_MODAL);
        this.method = method;
        this.methodKey = method.getOwnerName() + "." + method.getName() + method.getDesc();
        this.parameterFields = new ArrayList<>();
        this.confirmed = false;
        this.isStatic = (method.getAccess() & 0x0008) != 0;

        initUI();
        restoreCachedValues();
        pack();
        setMinimumSize(new Dimension(400, 200));
        setLocationRelativeTo(parent);
    }

    private void restoreCachedValues() {
        Object[] cached = parameterCache.get(methodKey);
        if (cached != null && cached.length == parameterFields.size()) {
            for (int i = 0; i < cached.length; i++) {
                parameterFields.get(i).setValue(cached[i]);
            }
        }
    }

    private void cacheValues() {
        if (result != null) {
            parameterCache.put(methodKey, result.clone());
        }
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createParametersPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        StringBuilder sig = new StringBuilder();
        int access = method.getAccess();
        if ((access & 0x0001) != 0) sig.append("public ");
        if ((access & 0x0002) != 0) sig.append("private ");
        if ((access & 0x0004) != 0) sig.append("protected ");
        if ((access & 0x0008) != 0) sig.append("static ");
        if ((access & 0x0010) != 0) sig.append("final ");

        String returnType = DescriptorParser.formatReturnType(method.getDesc());
        sig.append(returnType).append(" ");
        sig.append(method.getName());
        sig.append(DescriptorParser.formatMethodParams(method.getDesc()));

        JLabel sigLabel = new JLabel(sig.toString());
        sigLabel.setFont(JStudioTheme.getCodeFont(12));
        sigLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(sigLabel, BorderLayout.CENTER);

        return panel;
    }

    private JScrollPane createParametersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Parameters",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextSecondary()
        ));

        List<String> paramTypes = parseParameterTypes(method.getDesc());
        Map<Integer, String> paramNames = getParameterNames();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        int slotOffset = isStatic ? 0 : 1;

        if (paramTypes.isEmpty()) {
            gbc.gridx = 0;
            JLabel noParams = new JLabel("(No parameters)");
            noParams.setForeground(JStudioTheme.getTextSecondary());
            noParams.setFont(noParams.getFont().deriveFont(Font.ITALIC));
            panel.add(noParams, gbc);
        } else {
            int slot = slotOffset;
            for (int i = 0; i < paramTypes.size(); i++) {
                String typeDesc = paramTypes.get(i);
                String name = paramNames.getOrDefault(slot, "arg" + i);
                String formattedType = formatType(typeDesc);

                ParameterField field = createParameterField(i, name, typeDesc, formattedType);
                parameterFields.add(field);

                gbc.gridx = 0;
                gbc.weightx = 0;
                gbc.fill = GridBagConstraints.NONE;
                JLabel label = new JLabel(name + " (" + formattedType + "):");
                label.setForeground(JStudioTheme.getTextPrimary());
                panel.add(label, gbc);

                gbc.gridx = 1;
                gbc.weightx = 1;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                panel.add(field.getComponent(), gbc);

                gbc.gridy++;

                slot += getSlotSize(typeDesc);
            }
        }

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), gbc);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
        return scrollPane;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        JButton resetBtn = new JButton("Reset Defaults");
        resetBtn.setBackground(JStudioTheme.getBgTertiary());
        resetBtn.setForeground(JStudioTheme.getTextPrimary());
        resetBtn.addActionListener(e -> resetDefaults());

        JButton okBtn = new JButton("OK");
        okBtn.setBackground(JStudioTheme.getBgTertiary());
        okBtn.setForeground(JStudioTheme.getTextPrimary());
        okBtn.addActionListener(e -> {
            if (collectValues()) {
                cacheValues();
                confirmed = true;
                dispose();
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(JStudioTheme.getBgTertiary());
        cancelBtn.setForeground(JStudioTheme.getTextPrimary());
        cancelBtn.addActionListener(e -> dispose());

        panel.add(resetBtn);
        panel.add(okBtn);
        panel.add(cancelBtn);

        getRootPane().setDefaultButton(okBtn);

        return panel;
    }

    private ParameterField createParameterField(int index, String name, String typeDesc, String formattedType) {
        char first = typeDesc.charAt(0);

        switch (first) {
            case 'Z':
                return new BooleanParameterField(index, name, typeDesc);
            case 'B':
            case 'C':
            case 'S':
            case 'I':
            case 'J':
            case 'F':
            case 'D':
                return new PrimitiveParameterField(index, name, typeDesc);
            case '[':
                return new ArrayParameterField(index, name, typeDesc);
            case 'L':
                if (typeDesc.equals("Ljava/lang/String;")) {
                    return new StringParameterField(index, name, typeDesc);
                } else if (isBoxedPrimitive(typeDesc)) {
                    return new BoxedPrimitiveParameterField(index, name, typeDesc);
                } else {
                    return new ObjectParameterField(index, name, typeDesc);
                }
            default:
                return new ObjectParameterField(index, name, typeDesc);
        }
    }

    private boolean isBoxedPrimitive(String typeDesc) {
        return typeDesc.equals("Ljava/lang/Integer;") ||
               typeDesc.equals("Ljava/lang/Long;") ||
               typeDesc.equals("Ljava/lang/Float;") ||
               typeDesc.equals("Ljava/lang/Double;") ||
               typeDesc.equals("Ljava/lang/Boolean;") ||
               typeDesc.equals("Ljava/lang/Byte;") ||
               typeDesc.equals("Ljava/lang/Short;") ||
               typeDesc.equals("Ljava/lang/Character;");
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

    private Map<Integer, String> getParameterNames() {
        Map<Integer, String> names = new HashMap<>();
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) return names;

        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof LocalVariableTableAttribute) {
                LocalVariableTableAttribute lvt = (LocalVariableTableAttribute) attr;
                for (LocalVariableTableEntry entry : lvt.getLocalVariableTable()) {
                    if (entry.getStartPc() == 0) {
                        try {
                            Utf8Item utf8 = (Utf8Item) method.getClassFile().getConstPool().getItem(entry.getNameIndex());
                            names.put(entry.getIndex(), utf8.getValue());
                        } catch (Exception ignored) {
                        }
                    }
                }
                break;
            }
        }
        return names;
    }

    private String formatType(String typeDesc) {
        return DescriptorParser.formatFieldDescriptor(typeDesc);
    }

    private int getSlotSize(String typeDesc) {
        char first = typeDesc.charAt(0);
        return (first == 'J' || first == 'D') ? 2 : 1;
    }

    private void resetDefaults() {
        for (ParameterField field : parameterFields) {
            field.resetToDefault();
        }
    }

    private boolean collectValues() {
        result = new Object[parameterFields.size()];
        for (int i = 0; i < parameterFields.size(); i++) {
            try {
                result[i] = parameterFields.get(i).getValue();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid value for parameter " + parameterFields.get(i).getName() + ": " + e.getMessage(),
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        return true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public Object[] getResult() {
        return result;
    }

    private abstract static class ParameterField {
        protected final int index;
        protected final String name;
        protected final String typeDesc;

        ParameterField(int index, String name, String typeDesc) {
            this.index = index;
            this.name = name;
            this.typeDesc = typeDesc;
        }

        String getName() { return name; }
        abstract JComponent getComponent();
        abstract Object getValue() throws Exception;
        abstract void resetToDefault();
        abstract void setValue(Object value);
    }

    private static class PrimitiveParameterField extends ParameterField {
        private final JTextField textField;

        PrimitiveParameterField(int index, String name, String typeDesc) {
            super(index, name, typeDesc);
            textField = new JTextField(15);
            textField.setBackground(JStudioTheme.getBgTertiary());
            textField.setForeground(JStudioTheme.getTextPrimary());
            textField.setCaretColor(JStudioTheme.getTextPrimary());
            resetToDefault();
        }

        @Override
        JComponent getComponent() { return textField; }

        @Override
        Object getValue() throws Exception {
            String text = textField.getText().trim();
            switch (typeDesc.charAt(0)) {
                case 'B': return Byte.parseByte(text);
                case 'C': return text.isEmpty() ? '\0' : text.charAt(0);
                case 'S': return Short.parseShort(text);
                case 'I': return Integer.parseInt(text);
                case 'J': return Long.parseLong(text.replace("L", "").replace("l", ""));
                case 'F': return Float.parseFloat(text);
                case 'D': return Double.parseDouble(text);
                default: throw new IllegalStateException("Unknown primitive: " + typeDesc);
            }
        }

        @Override
        void resetToDefault() {
            switch (typeDesc.charAt(0)) {
                case 'B': case 'C': case 'S': case 'I': textField.setText("0"); break;
                case 'J': textField.setText("0"); break;
                case 'F': textField.setText("0.0"); break;
                case 'D': textField.setText("0.0"); break;
            }
        }

        @Override
        void setValue(Object value) {
            if (value != null) {
                textField.setText(String.valueOf(value));
            }
        }
    }

    private static class BooleanParameterField extends ParameterField {
        private final JCheckBox checkBox;

        BooleanParameterField(int index, String name, String typeDesc) {
            super(index, name, typeDesc);
            checkBox = new JCheckBox();
            checkBox.setBackground(JStudioTheme.getBgSecondary());
            checkBox.setSelected(false);
        }

        @Override
        JComponent getComponent() { return checkBox; }

        @Override
        Object getValue() { return checkBox.isSelected(); }

        @Override
        void resetToDefault() { checkBox.setSelected(false); }

        @Override
        void setValue(Object value) {
            if (value instanceof Boolean) {
                checkBox.setSelected((Boolean) value);
            }
        }
    }

    private static class StringParameterField extends ParameterField {
        private final JPanel panel;
        private final JTextField textField;
        private final JCheckBox nullCheckBox;

        StringParameterField(int index, String name, String typeDesc) {
            super(index, name, typeDesc);
            panel = new JPanel(new BorderLayout(5, 0));
            panel.setOpaque(false);

            textField = new JTextField(15);
            textField.setBackground(JStudioTheme.getBgTertiary());
            textField.setForeground(JStudioTheme.getTextPrimary());
            textField.setCaretColor(JStudioTheme.getTextPrimary());

            nullCheckBox = new JCheckBox("null");
            nullCheckBox.setBackground(JStudioTheme.getBgSecondary());
            nullCheckBox.setForeground(JStudioTheme.getTextSecondary());
            nullCheckBox.addActionListener(e -> textField.setEnabled(!nullCheckBox.isSelected()));

            panel.add(textField, BorderLayout.CENTER);
            panel.add(nullCheckBox, BorderLayout.EAST);

            resetToDefault();
        }

        @Override
        JComponent getComponent() { return panel; }

        @Override
        Object getValue() {
            return nullCheckBox.isSelected() ? null : textField.getText();
        }

        @Override
        void resetToDefault() {
            textField.setText("");
            textField.setEnabled(true);
            nullCheckBox.setSelected(false);
        }

        @Override
        void setValue(Object value) {
            if (value == null) {
                nullCheckBox.setSelected(true);
                textField.setEnabled(false);
                textField.setText("");
            } else {
                nullCheckBox.setSelected(false);
                textField.setEnabled(true);
                textField.setText(String.valueOf(value));
            }
        }
    }

    private static class BoxedPrimitiveParameterField extends ParameterField {
        private final JPanel panel;
        private final JTextField textField;
        private final JCheckBox nullCheckBox;

        BoxedPrimitiveParameterField(int index, String name, String typeDesc) {
            super(index, name, typeDesc);
            panel = new JPanel(new BorderLayout(5, 0));
            panel.setOpaque(false);

            textField = new JTextField(15);
            textField.setBackground(JStudioTheme.getBgTertiary());
            textField.setForeground(JStudioTheme.getTextPrimary());
            textField.setCaretColor(JStudioTheme.getTextPrimary());

            nullCheckBox = new JCheckBox("null");
            nullCheckBox.setBackground(JStudioTheme.getBgSecondary());
            nullCheckBox.setForeground(JStudioTheme.getTextSecondary());
            nullCheckBox.addActionListener(e -> textField.setEnabled(!nullCheckBox.isSelected()));

            panel.add(textField, BorderLayout.CENTER);
            panel.add(nullCheckBox, BorderLayout.EAST);

            resetToDefault();
        }

        @Override
        JComponent getComponent() { return panel; }

        @Override
        Object getValue() throws Exception {
            if (nullCheckBox.isSelected()) return null;
            String text = textField.getText().trim();
            switch (typeDesc) {
                case "Ljava/lang/Integer;": return Integer.parseInt(text);
                case "Ljava/lang/Long;": return Long.parseLong(text.replace("L", "").replace("l", ""));
                case "Ljava/lang/Float;": return Float.parseFloat(text);
                case "Ljava/lang/Double;": return Double.parseDouble(text);
                case "Ljava/lang/Boolean;": return Boolean.parseBoolean(text);
                case "Ljava/lang/Byte;": return Byte.parseByte(text);
                case "Ljava/lang/Short;": return Short.parseShort(text);
                case "Ljava/lang/Character;": return text.isEmpty() ? '\0' : text.charAt(0);
                default: throw new IllegalStateException("Unknown boxed type: " + typeDesc);
            }
        }

        @Override
        void resetToDefault() {
            nullCheckBox.setSelected(false);
            textField.setEnabled(true);
            switch (typeDesc) {
                case "Ljava/lang/Integer;": case "Ljava/lang/Byte;":
                case "Ljava/lang/Short;": case "Ljava/lang/Character;":
                    textField.setText("0"); break;
                case "Ljava/lang/Long;": textField.setText("0"); break;
                case "Ljava/lang/Float;": case "Ljava/lang/Double;":
                    textField.setText("0.0"); break;
                case "Ljava/lang/Boolean;": textField.setText("false"); break;
                default: textField.setText("0");
            }
        }

        @Override
        void setValue(Object value) {
            if (value == null) {
                nullCheckBox.setSelected(true);
                textField.setEnabled(false);
                textField.setText("");
            } else {
                nullCheckBox.setSelected(false);
                textField.setEnabled(true);
                textField.setText(String.valueOf(value));
            }
        }
    }

    private static class ArrayParameterField extends ParameterField {
        private final JTextField textField;
        private final String elementType;

        ArrayParameterField(int index, String name, String typeDesc) {
            super(index, name, typeDesc);
            textField = new JTextField(15);
            textField.setBackground(JStudioTheme.getBgTertiary());
            textField.setForeground(JStudioTheme.getTextPrimary());
            textField.setCaretColor(JStudioTheme.getTextPrimary());
            textField.setToolTipText("Comma-separated values (empty for empty array)");

            int dims = 0;
            int i = 0;
            while (typeDesc.charAt(i) == '[') { dims++; i++; }
            elementType = typeDesc.substring(i);

            resetToDefault();
        }

        @Override
        JComponent getComponent() { return textField; }

        @Override
        Object getValue() throws Exception {
            String text = textField.getText().trim();
            if (text.isEmpty()) {
                return createEmptyArray();
            }

            String[] parts = text.split(",");
            return parseArray(parts);
        }

        private Object createEmptyArray() {
            switch (elementType.charAt(0)) {
                case 'I': return new int[0];
                case 'J': return new long[0];
                case 'F': return new float[0];
                case 'D': return new double[0];
                case 'Z': return new boolean[0];
                case 'B': return new byte[0];
                case 'S': return new short[0];
                case 'C': return new char[0];
                default: return new Object[0];
            }
        }

        private Object parseArray(String[] parts) throws Exception {
            switch (elementType.charAt(0)) {
                case 'I': {
                    int[] arr = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Integer.parseInt(parts[i].trim());
                    }
                    return arr;
                }
                case 'J': {
                    long[] arr = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Long.parseLong(parts[i].trim().replace("L", "").replace("l", ""));
                    }
                    return arr;
                }
                case 'F': {
                    float[] arr = new float[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Float.parseFloat(parts[i].trim());
                    }
                    return arr;
                }
                case 'D': {
                    double[] arr = new double[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Double.parseDouble(parts[i].trim());
                    }
                    return arr;
                }
                case 'Z': {
                    boolean[] arr = new boolean[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Boolean.parseBoolean(parts[i].trim());
                    }
                    return arr;
                }
                case 'B': {
                    byte[] arr = new byte[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Byte.parseByte(parts[i].trim());
                    }
                    return arr;
                }
                case 'S': {
                    short[] arr = new short[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        arr[i] = Short.parseShort(parts[i].trim());
                    }
                    return arr;
                }
                case 'C': {
                    char[] arr = new char[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        String s = parts[i].trim();
                        arr[i] = s.isEmpty() ? '\0' : s.charAt(0);
                    }
                    return arr;
                }
                default: {
                    if (elementType.equals("Ljava/lang/String;")) {
                        return parts;
                    }
                    return new Object[parts.length];
                }
            }
        }

        @Override
        void resetToDefault() {
            textField.setText("");
        }

        @Override
        void setValue(Object value) {
            if (value == null) {
                textField.setText("");
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (value instanceof int[]) {
                int[] arr = (int[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof long[]) {
                long[] arr = (long[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof float[]) {
                float[] arr = (float[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof double[]) {
                double[] arr = (double[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof boolean[]) {
                boolean[] arr = (boolean[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof byte[]) {
                byte[] arr = (byte[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof short[]) {
                short[] arr = (short[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof char[]) {
                char[] arr = (char[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            } else if (value instanceof Object[]) {
                Object[] arr = (Object[]) value;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr[i]);
                }
            }
            textField.setText(sb.toString());
        }
    }

    private class ObjectParameterField extends ParameterField {
        private final JPanel panel;
        private final JButton configureButton;
        private final JCheckBox nullCheckBox;
        private final JLabel statusLabel;
        private ObjectSpec objectSpec;

        ObjectParameterField(int index, String name, String typeDesc) {
            super(index, name, typeDesc);
            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.setOpaque(false);

            String simpleName = typeDesc;
            if (typeDesc.startsWith("L") && typeDesc.endsWith(";")) {
                simpleName = typeDesc.substring(1, typeDesc.length() - 1);
                int lastSlash = simpleName.lastIndexOf('/');
                if (lastSlash >= 0) {
                    simpleName = simpleName.substring(lastSlash + 1);
                }
            }

            configureButton = new JButton("Configure...");
            configureButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            configureButton.setToolTipText("Configure how to construct " + simpleName);
            configureButton.addActionListener(e -> openObjectConfig());

            statusLabel = new JLabel("");
            statusLabel.setForeground(new Color(156, 220, 254));
            statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

            nullCheckBox = new JCheckBox("null");
            nullCheckBox.setBackground(JStudioTheme.getBgSecondary());
            nullCheckBox.setForeground(JStudioTheme.getTextSecondary());
            nullCheckBox.setSelected(true);
            nullCheckBox.addActionListener(e -> {
                configureButton.setEnabled(!nullCheckBox.isSelected());
                if (nullCheckBox.isSelected()) {
                    objectSpec = null;
                    statusLabel.setText("");
                }
            });

            panel.add(configureButton);
            panel.add(nullCheckBox);
            panel.add(statusLabel);

            configureButton.setEnabled(false);
        }

        private void openObjectConfig() {
            String typeName = typeDesc;
            if (typeName.startsWith("L") && typeName.endsWith(";")) {
                typeName = typeName.substring(1, typeName.length() - 1);
            }

            ObjectSpec result = ObjectBuilderDialog.showDialog(
                ParameterConfigDialog.this, typeName, objectSpec);

            if (result != null) {
                objectSpec = result;
                configureButton.setText("✓ Configured");
                statusLabel.setText(result.getSummary());
            }
        }

        @Override
        JComponent getComponent() { return panel; }

        @Override
        Object getValue() {
            if (nullCheckBox.isSelected() || objectSpec == null) {
                return null;
            }
            return objectSpec;
        }

        @Override
        void resetToDefault() {
            nullCheckBox.setSelected(true);
            configureButton.setEnabled(false);
            configureButton.setText("Configure...");
            objectSpec = null;
            statusLabel.setText("");
        }

        @Override
        void setValue(Object value) {
            if (value == null) {
                nullCheckBox.setSelected(true);
                configureButton.setEnabled(false);
                configureButton.setText("Configure...");
                objectSpec = null;
                statusLabel.setText("");
            } else if (value instanceof ObjectSpec) {
                objectSpec = (ObjectSpec) value;
                nullCheckBox.setSelected(false);
                configureButton.setEnabled(true);
                configureButton.setText("✓ Configured");
                statusLabel.setText(objectSpec.getSummary());
            }
        }
    }
}
