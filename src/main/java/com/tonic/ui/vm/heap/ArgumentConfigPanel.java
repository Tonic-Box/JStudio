package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.VmValueConverter;
import com.tonic.ui.vm.testgen.MethodFuzzer;
import com.tonic.util.DescriptorParser;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The argument editor for a VM run: typed manual fields or generated fuzz combinations, plus the receiver's constructor for instance methods. */
public class ArgumentConfigPanel extends ThemedJPanel
{

    /** How arguments are supplied: typed by hand or generated. */
    public enum Mode
    {MANUAL, FUZZ}

    @Getter
    private Mode currentMode = Mode.MANUAL;
    private MethodEntry method;
    private List<String> paramTypes = new ArrayList<>();
    private final JPanel contentPanel;
    private final CardLayout cardLayout;
    private final JPanel manualPanel;
    private final List<JTextField> paramFields = new ArrayList<>();
    private final Map<Integer, Object[]> arrayValues = new HashMap<>();

    private JCheckBox edgeCasesCheck;
    private JCheckBox randomCheck;
    private JCheckBox nullsCheck;
    private JSpinner iterationsSpinner;
    private JLabel comboInfoLabel;

    private List<Object[]> fuzzCombinations;
    private int currentComboIndex = 0;

    private JPanel receiverSection;
    private JPanel receiverContent;
    private JComboBox<MethodEntry> constructorCombo;
    private final List<JTextField> ctorParamFields = new ArrayList<>();
    private List<String> ctorParamTypes = new ArrayList<>();
    private final Map<Integer, Object[]> ctorArrayValues = new HashMap<>();
    private boolean receiverExpanded = false;

    /** Builds the mode toggle and the manual and fuzz cards, starting in manual mode with no method. */
    public ArgumentConfigPanel()
    {
        super(BackgroundStyle.SECONDARY, new BorderLayout(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()), "Arguments", TitledBorder.LEFT, TitledBorder.TOP, null, JStudioTheme.getTextPrimary()));

        JPanel modeButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, 2));
        modeButtonPanel.setBackground(JStudioTheme.getBgSecondary());

        ButtonGroup modeGroup = new ButtonGroup();
        JToggleButton manualBtn = new JToggleButton("Manual");
        manualBtn.setSelected(true);
        manualBtn.addActionListener(e -> switchMode(Mode.MANUAL));
        modeGroup.add(manualBtn);

        JToggleButton fuzzBtn = new JToggleButton("Fuzz");
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

        JPanel fuzzPanel = createFuzzPanel();

        contentPanel.add(manualScroll, "MANUAL");
        contentPanel.add(fuzzPanel, "FUZZ");

        add(contentPanel, BorderLayout.CENTER);

        updateNoMethodState();
    }

    private JPanel createFuzzPanel()
    {
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

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(edgeCasesCheck, gbc);
        gbc.gridx = 1;
        panel.add(randomCheck, gbc);
        gbc.gridx = 2;
        panel.add(nullsCheck, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel iterLabel = new JLabel("Iterations:");
        iterLabel.setForeground(JStudioTheme.getTextPrimary());
        panel.add(iterLabel, gbc);

        gbc.gridx = 1;
        iterationsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        iterationsSpinner.setPreferredSize(new Dimension(60, 25));
        iterationsSpinner.addChangeListener(e -> regenerateCombinations());
        panel.add(iterationsSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        comboInfoLabel = new JLabel("No method selected");
        comboInfoLabel.setForeground(JStudioTheme.getTextSecondary());
        panel.add(comboInfoLabel, gbc);

        return panel;
    }

    private void switchMode(Mode mode)
    {
        currentMode = mode;
        cardLayout.show(contentPanel, mode == Mode.MANUAL ? "MANUAL" : "FUZZ");
        if (mode == Mode.FUZZ && method != null)
        {
            regenerateCombinations();
        }
    }

    /**
     * Shows the fields for a method's parameters and discards previous values and fuzz combinations.
     *
     * @param method the method to configure, or null for none
     */
    public void setMethod(MethodEntry method)
    {
        this.method = method;
        this.paramTypes = method != null ? DescriptorParser.parameterDescriptors(method.getDesc()) : new ArrayList<>();
        this.currentComboIndex = 0;
        this.fuzzCombinations = null;
        this.arrayValues.clear();

        rebuildManualFields();
        if (currentMode == Mode.FUZZ)
        {
            regenerateCombinations();
        }
        updateFuzzInfo();
    }

    private void buildReceiverSection()
    {
        if (receiverSection != null)
        {
            manualPanel.remove(receiverSection);
        }

        if (method == null || (method.getAccess() & 0x0008) != 0)
        {
            receiverSection = null;
            return;
        }

        receiverSection = new JPanel();
        receiverSection.setLayout(new BoxLayout(receiverSection, BoxLayout.Y_AXIS));
        receiverSection.setBackground(JStudioTheme.getBgSecondary());
        receiverSection.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel receiverHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        receiverHeader.setBackground(JStudioTheme.getBgTertiary());
        receiverHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        receiverHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        receiverHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel toggleLabel = new JLabel(receiverExpanded ? "\u25BC" : "\u25B6");
        toggleLabel.setForeground(JStudioTheme.getTextPrimary());
        JLabel titleLabel = new JLabel("Receiver (this)");
        titleLabel.setForeground(JStudioTheme.getTextPrimary());

        receiverHeader.add(toggleLabel);
        receiverHeader.add(titleLabel);

        receiverHeader.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                receiverExpanded = !receiverExpanded;
                toggleLabel.setText(receiverExpanded ? "\u25BC" : "\u25B6");
                receiverContent.setVisible(receiverExpanded);
                manualPanel.revalidate();
                manualPanel.repaint();
            }
        });

        receiverContent = new JPanel();
        receiverContent.setLayout(new BoxLayout(receiverContent, BoxLayout.Y_AXIS));
        receiverContent.setBackground(JStudioTheme.getBgSecondary());
        receiverContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        receiverContent.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 5));
        receiverContent.setVisible(receiverExpanded);

        buildConstructorDropdown();
        buildConstructorParamFields();

        receiverSection.add(receiverHeader);
        receiverSection.add(receiverContent);
    }

    private void buildConstructorDropdown()
    {
        if (receiverContent == null) return;

        JPanel dropdownRow = new JPanel(new BorderLayout(5, 0));
        dropdownRow.setBackground(JStudioTheme.getBgSecondary());
        dropdownRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropdownRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel label = new JLabel("Constructor:");
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setPreferredSize(new Dimension(80, 24));

        constructorCombo = new JComboBox<>();
        constructorCombo.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof MethodEntry)
                {
                    MethodEntry m = (MethodEntry) value;
                    setText(formatConstructorDesc(m.getDesc()));
                }
                return this;
            }
        });

        populateConstructors();

        constructorCombo.addActionListener(e ->
        {
            if (constructorCombo.getSelectedItem() != null)
            {
                updateConstructorParams();
            }
        });

        dropdownRow.add(label, BorderLayout.WEST);
        dropdownRow.add(constructorCombo, BorderLayout.CENTER);
        dropdownRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        receiverContent.add(dropdownRow);
    }

    private void populateConstructors()
    {
        if (constructorCombo == null || method == null)
        {
            return;
        }

        constructorCombo.removeAllItems();
        ClassFile classFile = method.getClassFile();
        if (classFile != null)
        {
            for (MethodEntry m : classFile.getMethods())
            {
                if ("<init>".equals(m.getName()))
                {
                    constructorCombo.addItem(m);
                }
            }
        }

        if (constructorCombo.getItemCount() == 0)
        {
            JLabel noCtors = new JLabel("(no constructors found)");
            noCtors.setForeground(JStudioTheme.getTextSecondary());
            noCtors.setAlignmentX(Component.LEFT_ALIGNMENT);
            receiverContent.add(noCtors);
        }
    }

    private String formatConstructorDesc(String desc)
    {
        List<String> types = DescriptorParser.parameterDescriptors(desc);
        if (types.isEmpty())
        {
            return "<init>()";
        }
        StringBuilder sb = new StringBuilder("<init>(");
        for (int i = 0; i < types.size(); i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(formatType(types.get(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    private void buildConstructorParamFields()
    {
        updateConstructorParams();
    }

    private void updateConstructorParams()
    {
        if (receiverContent == null) return;

        while (receiverContent.getComponentCount() > 1)
        {
            receiverContent.remove(receiverContent.getComponentCount() - 1);
        }

        ctorParamFields.clear();
        ctorParamTypes.clear();
        ctorArrayValues.clear();

        MethodEntry ctor = (MethodEntry) constructorCombo.getSelectedItem();
        if (ctor == null)
        {
            JLabel noParams = new JLabel("(no constructor selected)");
            noParams.setForeground(JStudioTheme.getTextSecondary());
            noParams.setAlignmentX(Component.LEFT_ALIGNMENT);
            receiverContent.add(noParams);
            receiverContent.revalidate();
            receiverContent.repaint();
            return;
        }

        ctorParamTypes = new ArrayList<>(DescriptorParser.parameterDescriptors(ctor.getDesc()));

        if (ctorParamTypes.isEmpty())
        {
            JLabel noParams = new JLabel("(no parameters)");
            noParams.setForeground(JStudioTheme.getTextSecondary());
            noParams.setAlignmentX(Component.LEFT_ALIGNMENT);
            receiverContent.add(noParams);
        }
        else
        {
            for (int i = 0; i < ctorParamTypes.size(); i++)
            {
                String type = ctorParamTypes.get(i);
                JPanel row = new JPanel(new BorderLayout(5, 0));
                row.setBackground(JStudioTheme.getBgSecondary());
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

                JLabel label = new JLabel(formatType(type) + " arg" + i + ":");
                label.setForeground(JStudioTheme.getTextPrimary());
                label.setPreferredSize(new Dimension(100, 24));

                JTextField field = new JTextField(getDefaultValue(type));
                ctorParamFields.add(field);

                if (type.startsWith("["))
                {
                    field.setEditable(false);
                    field.setBackground(JStudioTheme.getBgTertiary());
                    field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    field.setToolTipText("Click to edit array");

                    final int paramIndex = i;
                    final String componentType = getArrayComponentType(type);
                    field.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            openCtorArrayEditor(paramIndex, componentType, field);
                        }
                    });

                    ctorArrayValues.put(i, new Object[0]);
                    field.setText("[]");
                }

                row.add(label, BorderLayout.WEST);
                row.add(field, BorderLayout.CENTER);
                row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
                receiverContent.add(row);
            }
        }

        receiverContent.revalidate();
        receiverContent.repaint();
    }

    private void openCtorArrayEditor(int paramIndex, String componentType, JTextField displayField)
    {
        Object[] currentValues = ctorArrayValues.getOrDefault(paramIndex, new Object[0]);
        Window owner = SwingUtilities.getWindowAncestor(this);
        ArrayEditorDialog dialog = new ArrayEditorDialog(owner, componentType, currentValues);
        dialog.setVisible(true);

        if (dialog.isConfirmed())
        {
            Object[] newValues = dialog.getElements();
            ctorArrayValues.put(paramIndex, newValues);
            displayField.setText(ArrayEditorDialog.formatArrayDisplay(newValues));
        }
    }

    private void rebuildManualFields()
    {
        manualPanel.removeAll();
        paramFields.clear();

        buildReceiverSection();
        if (receiverSection != null)
        {
            manualPanel.add(receiverSection);
            manualPanel.add(Box.createVerticalStrut(10));
        }

        if (paramTypes.isEmpty())
        {
            JLabel noParams = new JLabel("(no parameters)");
            noParams.setForeground(JStudioTheme.getTextSecondary());
            noParams.setAlignmentX(Component.LEFT_ALIGNMENT);
            manualPanel.add(noParams);
        }
        else
        {
            for (int i = 0; i < paramTypes.size(); i++)
            {
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

                if (type.startsWith("["))
                {
                    field.setEditable(false);
                    field.setBackground(JStudioTheme.getBgTertiary());
                    field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    field.setToolTipText("Click to edit array");

                    final int paramIndex = i;
                    final String componentType = getArrayComponentType(type);
                    field.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            openArrayEditor(paramIndex, componentType, field);
                        }
                    });

                    arrayValues.put(i, new Object[0]);
                    field.setText("[]");
                }
                else
                {
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

    private void openArrayEditor(int paramIndex, String componentType, JTextField displayField)
    {
        Object[] currentValues = arrayValues.getOrDefault(paramIndex, new Object[0]);

        Window owner = SwingUtilities.getWindowAncestor(this);
        ArrayEditorDialog dialog = new ArrayEditorDialog(owner, componentType, currentValues);
        dialog.setVisible(true);

        if (dialog.isConfirmed())
        {
            Object[] newValues = dialog.getElements();
            arrayValues.put(paramIndex, newValues);
            displayField.setText(ArrayEditorDialog.formatArrayDisplay(newValues));
        }
    }

    private String getArrayComponentType(String arrayType)
    {
        if (arrayType.startsWith("["))
        {
            return arrayType.substring(1);
        }
        return arrayType;
    }

    private void regenerateCombinations()
    {
        if (method == null || paramTypes.isEmpty())
        {
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

        MethodFuzzer fuzzer = new MethodFuzzer(method.getOwnerName(), method.getName(), method.getDesc(), (method.getAccess() & 0x0008) != 0, config);

        fuzzCombinations = fuzzer.generateInputSets();
        currentComboIndex = 0;
        updateFuzzInfo();
    }

    private void updateFuzzInfo()
    {
        if (method == null)
        {
            comboInfoLabel.setText("No method selected");
        }
        else if (paramTypes.isEmpty())
        {
            comboInfoLabel.setText("Method has no parameters");
        }
        else if (fuzzCombinations != null)
        {
            comboInfoLabel.setText("Combination " + (currentComboIndex + 1) + " of " + fuzzCombinations.size());
        }
    }

    private void updateNoMethodState()
    {
        JLabel noMethod = new JLabel("Select a method to configure arguments");
        noMethod.setForeground(JStudioTheme.getTextSecondary());
        manualPanel.add(noMethod);
    }

    private Object[] collectConstructorArguments()
    {
        Object[] args = new Object[ctorParamFields.size()];
        for (int i = 0; i < ctorParamFields.size(); i++)
        {
            String type = ctorParamTypes.get(i);
            if (type.startsWith("["))
            {
                args[i] = ctorArrayValues.getOrDefault(i, new Object[0]);
            }
            else
            {
                String value = ctorParamFields.get(i).getText().trim();
                args[i] = parseArgumentValue(value, type);
            }
        }
        return args;
    }

    /**
     * Captures the arguments from the manual fields or the current fuzz combination, and for an instance method the selected constructor and its arguments; call on the event dispatch thread.
     *
     * @return the captured arguments, or null when no method is set
     */
    public Arguments captureArguments()
    {
        if (method == null)
        {
            return null;
        }
        Object[] values;
        if (paramTypes.isEmpty())
        {
            values = new Object[0];
        }
        else if (currentMode == Mode.MANUAL)
        {
            values = collectManualArguments();
        }
        else
        {
            values = getCurrentFuzzArguments().clone();
        }
        boolean isStatic = (method.getAccess() & 0x0008) != 0;
        MethodEntry constructor = isStatic || constructorCombo == null ? null : (MethodEntry) constructorCombo.getSelectedItem();
        Object[] constructorArgs = constructor != null ? collectConstructorArguments() : new Object[0];
        return new Arguments(method, values, constructor, constructorArgs);
    }

    /** Argument values captured from the panel, converted to VM values off the event dispatch thread. */
    public static final class Arguments
    {
        private final MethodEntry method;
        private final Object[] values;
        private final MethodEntry constructor;
        private final Object[] constructorArgs;

        Arguments(MethodEntry method, Object[] values, MethodEntry constructor, Object[] constructorArgs)
        {
            this.method = method;
            this.values = values;
            this.constructor = constructor;
            this.constructorArgs = constructorArgs;
        }

        /**
         * Converts the captured values to VM values of the declared parameter types; for an instance method a receiver is allocated and its selected constructor run first.
         *
         * @param heap the heap values are allocated in
         * @param resolver the resolver the constructor is found and run with
         * @param maxCallDepth the call depth limit for the constructor run
         * @param maxInstructions the instruction limit for the constructor run
         * @return the values in call order, the receiver first for an instance method
         * @throws IllegalArgumentException if a value cannot be passed as its type, or an instance method's class has no constructor to build the receiver with
         * @throws IllegalStateException if the constructor throws
         */
        public ConcreteValue[] toConcrete(SimpleHeapManager heap, ClassResolver resolver, int maxCallDepth, int maxInstructions)
        {
            VmValueConverter converter = new VmValueConverter(heap, resolver, maxCallDepth, maxInstructions);
            ConcreteValue[] params = converter.toConcreteAll(values, DescriptorParser.parameterDescriptors(method.getDesc()));
            if ((method.getAccess() & 0x0008) != 0)
            {
                return params;
            }
            if (constructor == null)
            {
                throw new IllegalArgumentException(method.getOwnerName() + " has no constructor to build the receiver with");
            }
            ObjectInstance receiver = converter.construct(method.getOwnerName(), constructor.getDesc(), constructorArgs);
            ConcreteValue[] all = new ConcreteValue[params.length + 1];
            all[0] = ConcreteValue.reference(receiver);
            System.arraycopy(params, 0, all, 1, params.length);
            return all;
        }
    }

    private Object[] collectManualArguments()
    {
        Object[] args = new Object[paramFields.size()];
        for (int i = 0; i < paramFields.size(); i++)
        {
            String type = paramTypes.get(i);
            if (type.startsWith("["))
            {
                args[i] = arrayValues.getOrDefault(i, new Object[0]);
            }
            else
            {
                String value = paramFields.get(i).getText().trim();
                args[i] = parseArgumentValue(value, type);
            }
        }
        return args;
    }

    private Object[] getCurrentFuzzArguments()
    {
        if (fuzzCombinations == null || fuzzCombinations.isEmpty())
        {
            return new Object[0];
        }
        return fuzzCombinations.get(currentComboIndex);
    }

    /**
     * Tells whether another fuzz combination follows the current one.
     *
     * @return true when a later combination exists
     */
    public boolean hasNextCombination()
    {
        return fuzzCombinations != null && currentComboIndex < fuzzCombinations.size() - 1;
    }

    /** Moves to the next fuzz combination, if there is one. */
    public void nextCombination()
    {
        if (hasNextCombination())
        {
            currentComboIndex++;
            updateFuzzInfo();
        }
    }

    /** Returns to the first fuzz combination. */
    public void resetCombinations()
    {
        currentComboIndex = 0;
        updateFuzzInfo();
    }

    /**
     * Counts the generated fuzz combinations.
     *
     * @return the number of combinations, 0 when none are generated
     */
    public int getTotalCombinations()
    {
        return fuzzCombinations != null ? fuzzCombinations.size() : 0;
    }

    /** @return the index of the current fuzz combination */
    public int getCurrentCombinationIndex()
    {
        return currentComboIndex;
    }

    /**
     * Formats the current arguments for display.
     *
     * @return the arguments in parentheses, strings and chars quoted
     */
    public String getCurrentArgsDescription()
    {
        Object[] args = currentMode == Mode.MANUAL ? collectManualArguments() : getCurrentFuzzArguments();
        if (args.length == 0) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(formatArgValue(args[i]));
        }
        sb.append(")");
        return sb.toString();
    }

    private String formatArgValue(Object val)
    {
        if (val == null) return "null";
        if (val instanceof String) return "\"" + val + "\"";
        if (val instanceof Character) return "'" + val + "'";
        return String.valueOf(val);
    }

    private Object parseArgumentValue(String value, String type)
    {
        boolean isEmpty = value == null || value.isEmpty() || value.equalsIgnoreCase("null");

        try
        {
            switch (type)
            {
                case "I":
                    return isEmpty ? 0 : Integer.parseInt(value);
                case "B":
                    return isEmpty ? (byte) 0 : (byte) Integer.parseInt(value);
                case "S":
                    return isEmpty ? (short) 0 : (short) Integer.parseInt(value);
                case "C":
                    if (isEmpty) return 'a';
                    if (value.length() == 1) return value.charAt(0);
                    if (value.startsWith("'") && value.endsWith("'") && value.length() == 3)
                    {
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
                    return !isEmpty && Boolean.parseBoolean(value);
                default:
                    if (isEmpty) return null;
                    if (type.equals("Ljava/lang/String;"))
                    {
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                        {
                            return value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                    return null;
            }
        }
        catch (NumberFormatException e)
        {
            return getDefaultForType(type);
        }
    }

    private Object getDefaultForType(String type)
    {
        switch (type)
        {
            case "I":
                return 0;
            case "B":
                return (byte) 0;
            case "S":
                return (short) 0;
            case "C":
                return 'a';
            case "J":
                return 0L;
            case "F":
                return 0.0f;
            case "D":
                return 0.0;
            case "Z":
                return false;
            default:
                return null;
        }
    }

    private String formatType(String type)
    {
        if (type.startsWith("["))
        {
            return formatType(type.substring(1)) + "[]";
        }
        switch (type)
        {
            case "I":
                return "int";
            case "J":
                return "long";
            case "F":
                return "float";
            case "D":
                return "double";
            case "Z":
                return "boolean";
            case "B":
                return "byte";
            case "S":
                return "short";
            case "C":
                return "char";
            case "V":
                return "void";
            default:
                if (type.startsWith("L") && type.endsWith(";"))
                {
                    String className = type.substring(1, type.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return type;
        }
    }

    private String getDefaultValue(String type)
    {
        switch (type)
        {
            case "I":
            case "B":
            case "S":
                return "0";
            case "J":
                return "0L";
            case "F":
                return "0.0f";
            case "D":
                return "0.0";
            case "Z":
                return "false";
            case "C":
                return "'a'";
            default:
                if (type.equals("Ljava/lang/String;")) return "\"\"";
                return "null";
        }
    }
}
