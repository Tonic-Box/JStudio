package com.tonic.ui.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

public class TransformPanel extends ThemedJPanel {

    /**
     * Callback interface for transform completion.
     */
    public interface TransformCallback {
        void onTransformComplete();
    }

    private final ProjectModel project;
    private final List<TransformCheckBox> transformCheckBoxes;
    private JTextArea beforeArea;
    private JTextArea afterArea;
    private JLabel statusLabel;

    // Target selection UI
    private JLabel classLabel;
    private JComboBox<MethodEntryModel> methodComboBox;

    // Selected targets
    private ClassEntryModel selectedClass;
    private MethodEntryModel selectedMethod;

    // Callback for notifying when transforms are applied
    private TransformCallback transformCallback;

    public TransformPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;
        this.transformCheckBoxes = new ArrayList<>();

        // Top panel: target selection (class and method)
        JPanel targetPanel = createTargetPanel();
        add(targetPanel, BorderLayout.NORTH);

        // Left panel: transform list
        JPanel transformListPanel = createTransformListPanel();

        // Right panel: before/after preview
        JSplitPane previewSplit = createPreviewPanel();

        // Main split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                transformListPanel, previewSplit);
        mainSplit.setDividerLocation(250);
        mainSplit.setBorder(null);
        add(mainSplit, BorderLayout.CENTER);

        // Bottom: status and apply buttons
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTargetPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel classLabelTitle = new JLabel("Class:");
        classLabelTitle.setForeground(JStudioTheme.getTextSecondary());
        panel.add(classLabelTitle);
        panel.add(Box.createHorizontalStrut(8));

        classLabel = new JLabel("(none selected)");
        classLabel.setForeground(JStudioTheme.getAccent());
        panel.add(classLabel);
        panel.add(Box.createHorizontalStrut(16));

        JLabel methodLabelTitle = new JLabel("Method:");
        methodLabelTitle.setForeground(JStudioTheme.getTextSecondary());
        panel.add(methodLabelTitle);
        panel.add(Box.createHorizontalStrut(8));

        methodComboBox = new JComboBox<>();
        methodComboBox.setBackground(JStudioTheme.getBgTertiary());
        methodComboBox.setForeground(JStudioTheme.getTextPrimary());
        methodComboBox.setRenderer(new MethodComboRenderer());
        methodComboBox.addActionListener(e -> {
            if (methodComboBox.getSelectedItem() instanceof MethodEntryModel) {
                selectedMethod = (MethodEntryModel) methodComboBox.getSelectedItem();
                updateStatusLabel();
                beforeArea.setText("");
                afterArea.setText("");
            }
        });
        panel.add(methodComboBox);
        panel.add(Box.createHorizontalGlue());

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
        bottomPanel.setBackground(JStudioTheme.getBgSecondary());
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JButton previewButton = new JButton("Preview");
        previewButton.setBackground(JStudioTheme.getBgTertiary());
        previewButton.setForeground(JStudioTheme.getTextPrimary());
        previewButton.addActionListener(e -> preview());
        bottomPanel.add(previewButton);
        bottomPanel.add(Box.createHorizontalStrut(8));

        JButton applyButton = new JButton("Apply to Method");
        applyButton.setBackground(JStudioTheme.getBgTertiary());
        applyButton.setForeground(JStudioTheme.getTextPrimary());
        applyButton.addActionListener(e -> applyToMethod());
        bottomPanel.add(applyButton);
        bottomPanel.add(Box.createHorizontalStrut(8));

        JButton applyClassButton = new JButton("Apply to Class");
        applyClassButton.setBackground(JStudioTheme.getBgTertiary());
        applyClassButton.setForeground(JStudioTheme.getTextPrimary());
        applyClassButton.addActionListener(e -> applyToClass());
        bottomPanel.add(applyClassButton);
        bottomPanel.add(Box.createHorizontalStrut(8));

        JButton applyAllButton = new JButton("Apply to All Classes");
        applyAllButton.setBackground(JStudioTheme.getBgTertiary());
        applyAllButton.setForeground(JStudioTheme.getTextPrimary());
        applyAllButton.addActionListener(e -> applyToAllClasses());
        bottomPanel.add(applyAllButton);
        bottomPanel.add(Box.createHorizontalStrut(16));

        statusLabel = new JLabel("Select a class to transform.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        bottomPanel.add(statusLabel);
        bottomPanel.add(Box.createHorizontalGlue());

        return bottomPanel;
    }

    private JPanel createTransformListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel header = new JLabel("SSA Transforms");
        header.setForeground(JStudioTheme.getTextPrimary());
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        panel.add(header, BorderLayout.NORTH);

        JPanel checkboxPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        checkboxPanel.setBackground(JStudioTheme.getBgSecondary());

        // Add all transforms with descriptions
        addTransform(checkboxPanel, "Constant Folding", "Evaluate constant expressions at compile time", true);
        addTransform(checkboxPanel, "Copy Propagation", "Replace copies with their source values", true);
        addTransform(checkboxPanel, "Dead Code Elimination", "Remove unreachable and unused code", true);
        addTransform(checkboxPanel, "Algebraic Simplification", "Apply mathematical identities (x+0=x)", false);
        addTransform(checkboxPanel, "Strength Reduction", "Replace expensive ops with cheaper ones", false);
        addTransform(checkboxPanel, "Reassociation", "Reorder ops to enable folding", false);
        addTransform(checkboxPanel, "Phi Constant Propagation", "Simplify phi nodes with identical inputs", false);
        addTransform(checkboxPanel, "Peephole Optimizations", "Pattern-based local optimizations", false);
        addTransform(checkboxPanel, "Common Subexpression Elimination", "Reuse identical expressions", false);
        addTransform(checkboxPanel, "Null Check Elimination", "Remove redundant null checks", false);
        addTransform(checkboxPanel, "Conditional Constant Propagation", "Propagate constants through branches", false);
        addTransform(checkboxPanel, "Loop Invariant Code Motion", "Move loop-invariant code out", false);
        addTransform(checkboxPanel, "Loop Predication", "Convert loop guards to predicates", false);
        addTransform(checkboxPanel, "Induction Variable Simplification", "Simplify loop counters", false);
        addTransform(checkboxPanel, "Jump Threading", "Eliminate redundant jump chains", false);
        addTransform(checkboxPanel, "Block Merging", "Merge adjacent blocks", false);
        addTransform(checkboxPanel, "Control Flow Reducibility", "Convert irreducible to reducible CFG", false);
        addTransform(checkboxPanel, "Duplicate Block Merging", "Merge duplicated blocks", false);
        addTransform(checkboxPanel, "Redundant Copy Elimination", "Remove identity copies", false);
        addTransform(checkboxPanel, "Bit-Tracking DCE", "Eliminate unused bit operations", false);
        addTransform(checkboxPanel, "Correlated Value Propagation", "Use branch info for optimization", false);

        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getVerticalScrollBar().setUnitIncrement(15);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Buttons for select all/none
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setBackground(JStudioTheme.getBgSecondary());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        JButton selectAll = new JButton("All");
        selectAll.setBackground(JStudioTheme.getBgTertiary());
        selectAll.setForeground(JStudioTheme.getTextPrimary());
        selectAll.addActionListener(e -> setAllSelected(true));
        buttonPanel.add(selectAll);
        buttonPanel.add(Box.createHorizontalStrut(4));

        JButton selectNone = new JButton("None");
        selectNone.setBackground(JStudioTheme.getBgTertiary());
        selectNone.setForeground(JStudioTheme.getTextPrimary());
        selectNone.addActionListener(e -> setAllSelected(false));
        buttonPanel.add(selectNone);
        buttonPanel.add(Box.createHorizontalStrut(4));

        JButton selectStandard = new JButton("Standard");
        selectStandard.setBackground(JStudioTheme.getBgTertiary());
        selectStandard.setForeground(JStudioTheme.getTextPrimary());
        selectStandard.addActionListener(e -> selectStandard());
        buttonPanel.add(selectStandard);
        buttonPanel.add(Box.createHorizontalGlue());

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void addTransform(JPanel panel, String name, String description, boolean defaultSelected) {
        TransformCheckBox cb = new TransformCheckBox(name, description, defaultSelected);
        transformCheckBoxes.add(cb);
        panel.add(cb);
    }

    private JSplitPane createPreviewPanel() {
        // Before panel
        JPanel beforePanel = new JPanel(new BorderLayout());
        beforePanel.setBackground(JStudioTheme.getBgTertiary());

        JLabel beforeLabel = new JLabel("Before");
        beforeLabel.setForeground(JStudioTheme.getTextSecondary());
        beforeLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        beforePanel.add(beforeLabel, BorderLayout.NORTH);

        beforeArea = new JTextArea();
        beforeArea.setEditable(false);
        beforeArea.setBackground(JStudioTheme.getBgTertiary());
        beforeArea.setForeground(JStudioTheme.getTextPrimary());
        beforeArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        JScrollPane beforeScroll = new JScrollPane(beforeArea);
        beforeScroll.setBorder(null);
        beforePanel.add(beforeScroll, BorderLayout.CENTER);

        // After panel
        JPanel afterPanel = new JPanel(new BorderLayout());
        afterPanel.setBackground(JStudioTheme.getBgTertiary());

        JLabel afterLabel = new JLabel("After");
        afterLabel.setForeground(JStudioTheme.getTextSecondary());
        afterLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        afterPanel.add(afterLabel, BorderLayout.NORTH);

        afterArea = new JTextArea();
        afterArea.setEditable(false);
        afterArea.setBackground(JStudioTheme.getBgTertiary());
        afterArea.setForeground(JStudioTheme.getTextPrimary());
        afterArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        JScrollPane afterScroll = new JScrollPane(afterArea);
        afterScroll.setBorder(null);
        afterPanel.add(afterScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, beforePanel, afterPanel);
        split.setDividerLocation(300);
        split.setBorder(null);
        return split;
    }

    private void setAllSelected(boolean selected) {
        for (TransformCheckBox cb : transformCheckBoxes) {
            cb.setSelected(selected);
        }
    }

    private void selectStandard() {
        for (TransformCheckBox cb : transformCheckBoxes) {
            String name = cb.getTransformName();
            cb.setSelected(name.equals("Constant Folding") ||
                    name.equals("Copy Propagation") ||
                    name.equals("Dead Code Elimination"));
        }
    }

    /**
     * Set the class to transform (and populate method dropdown).
     */
    public void setClass(ClassEntryModel classEntry) {
        this.selectedClass = classEntry;
        this.selectedMethod = null;

        // Update class label
        if (classEntry != null) {
            classLabel.setText(classEntry.getSimpleName());
        } else {
            classLabel.setText("(none selected)");
        }

        // Populate method dropdown
        methodComboBox.removeAllItems();
        if (classEntry != null) {
            for (MethodEntryModel method : classEntry.getMethods()) {
                // Only add methods with code (skip abstract/native)
                if (method.getMethodEntry().getCodeAttribute() != null) {
                    methodComboBox.addItem(method);
                }
            }
            // Select first method if available
            if (methodComboBox.getItemCount() > 0) {
                methodComboBox.setSelectedIndex(0);
                selectedMethod = (MethodEntryModel) methodComboBox.getSelectedItem();
            }
        }

        // Clear preview
        beforeArea.setText("");
        afterArea.setText("");
        updateStatusLabel();
    }

    /**
     * Set the method to transform (legacy method for compatibility).
     */
    public void setMethod(MethodEntryModel method) {
        this.selectedMethod = method;
        if (method != null) {
            // Try to select this method in the combo box
            for (int i = 0; i < methodComboBox.getItemCount(); i++) {
                if (methodComboBox.getItemAt(i) == method) {
                    methodComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateStatusLabel();
        beforeArea.setText("");
        afterArea.setText("");
    }

    /**
     * Set the callback to be invoked when transforms are applied.
     */
    public void setTransformCallback(TransformCallback callback) {
        this.transformCallback = callback;
    }

    /**
     * Notify callback that transforms were applied.
     */
    private void notifyTransformComplete() {
        if (transformCallback != null) {
            transformCallback.onTransformComplete();
        }
    }

    private void updateStatusLabel() {
        if (selectedClass == null) {
            statusLabel.setText("No class selected. Open a class first.");
        } else if (selectedMethod == null) {
            statusLabel.setText("Class: " + selectedClass.getSimpleName() + " - Select a method");
        } else {
            statusLabel.setText("Ready: " + selectedClass.getSimpleName() + "." + selectedMethod.getMethodEntry().getName() + "()");
        }
    }

    /**
     * Preview the transform effect.
     */
    public void preview() {
        if (selectedMethod == null) {
            statusLabel.setText("No method selected.");
            return;
        }

        MethodEntry method = selectedMethod.getMethodEntry();
        if (method.getCodeAttribute() == null) {
            statusLabel.setText("Method has no code (abstract or native).");
            return;
        }

        statusLabel.setText("Generating preview...");

        SwingWorker<String[], Void> worker = new SwingWorker<>() {
            @Override
            protected String[] doInBackground() {
                SSA ssa = createConfiguredSSA(method);

                // Before: just lift
                IRMethod before = new SSA(method.getClassFile().getConstPool()).lift(method);
                String beforeText = IRPrinter.format(before);

                // After: lift and transform
                IRMethod after = ssa.lift(method);
                ssa.runTransforms(after);
                String afterText = IRPrinter.format(after);

                return new String[]{beforeText, afterText};
            }

            @Override
            protected void done() {
                try {
                    String[] result = get();
                    beforeArea.setText(result[0]);
                    afterArea.setText(result[1]);
                    beforeArea.setCaretPosition(0);
                    afterArea.setCaretPosition(0);
                    statusLabel.setText("Preview generated.");
                } catch (Exception e) {
                    statusLabel.setText("Preview failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Apply transforms to the selected method.
     */
    public void applyToMethod() {
        if (selectedMethod == null) {
            statusLabel.setText("No method selected.");
            return;
        }

        MethodEntry method = selectedMethod.getMethodEntry();
        if (method.getCodeAttribute() == null) {
            statusLabel.setText("Method has no code (abstract or native).");
            return;
        }

        statusLabel.setText("Applying transforms...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                SSA ssa = createConfiguredSSA(method);
                ssa.transform(method);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    // Clear caches
                    selectedMethod.setIrCache(null);
                    if (selectedClass != null) {
                        selectedClass.setDecompilationCache(null);
                    }
                    statusLabel.setText("Transforms applied to " + method.getName());
                    // Notify callback to refresh the view
                    notifyTransformComplete();
                } catch (Exception e) {
                    statusLabel.setText("Transform failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Apply transforms to all methods in the selected class.
     */
    public void applyToClass() {
        if (selectedClass == null) {
            statusLabel.setText("No class selected.");
            return;
        }

        statusLabel.setText("Applying transforms to class...");

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                int count = 0;
                for (MethodEntryModel methodModel : selectedClass.getMethods()) {
                    MethodEntry method = methodModel.getMethodEntry();
                    if (method.getCodeAttribute() == null) continue;
                    if (method.getName().startsWith("<")) continue;

                    try {
                        SSA ssa = createConfiguredSSA(method);
                        ssa.transform(method);
                        methodModel.setIrCache(null);
                        count++;
                    } catch (Exception ex) {
                        // Log but continue with other methods
                        System.err.println("Failed to transform " + selectedClass.getClassName() + "." + method.getName() + ": " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
                return count;
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    selectedClass.setDecompilationCache(null);
                    statusLabel.setText("Transforms applied to " + count + " methods in " + selectedClass.getSimpleName());
                    // Notify callback to refresh the view
                    notifyTransformComplete();
                } catch (Exception e) {
                    statusLabel.setText("Transform failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Apply transforms to all methods in all loaded classes.
     */
    public void applyToAllClasses() {
        statusLabel.setText("Applying transforms to all classes...");

        SwingWorker<int[], String> worker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() {
                int totalMethods = 0;
                int totalClasses = 0;
                for (ClassEntryModel classEntry : project.getUserClasses()) {
                    boolean classModified = false;
                    for (MethodEntryModel methodModel : classEntry.getMethods()) {
                        MethodEntry method = methodModel.getMethodEntry();
                        if (method.getCodeAttribute() == null) continue;
                        if (method.getName().startsWith("<")) continue;

                        try {
                            SSA ssa = createConfiguredSSA(method);
                            ssa.transform(method);
                            methodModel.setIrCache(null);
                            totalMethods++;
                            classModified = true;
                        } catch (Exception ex) {
                            // Log but continue with other methods
                            System.err.println("Failed to transform " + classEntry.getClassName() + "." + method.getName() + ": " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    if (classModified) {
                        classEntry.setDecompilationCache(null);
                        totalClasses++;
                        publish(classEntry.getSimpleName());
                    }
                }
                return new int[]{totalMethods, totalClasses};
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText("Processing: " + chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    int[] result = get();
                    statusLabel.setText("Transforms applied to " + result[0] + " methods across " + result[1] + " classes");
                    notifyTransformComplete();
                } catch (Exception e) {
                    e.printStackTrace();
                    Throwable cause = e.getCause();
                    String msg = cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : e.getMessage();
                    statusLabel.setText("Transform failed: " + msg);
                }
            }
        };

        worker.execute();
    }

    private SSA createConfiguredSSA(MethodEntry method) {
        SSA ssa = new SSA(method.getClassFile().getConstPool());

        for (TransformCheckBox cb : transformCheckBoxes) {
            if (!cb.isSelected()) continue;

            String name = cb.getTransformName();
            switch (name) {
                case "Constant Folding": ssa.withConstantFolding(); break;
                case "Copy Propagation": ssa.withCopyPropagation(); break;
                case "Dead Code Elimination": ssa.withDeadCodeElimination(); break;
                case "Algebraic Simplification": ssa.withAlgebraicSimplification(); break;
                case "Strength Reduction": ssa.withStrengthReduction(); break;
                case "Reassociation": ssa.withReassociate(); break;
                case "Phi Constant Propagation": ssa.withPhiConstantPropagation(); break;
                case "Peephole Optimizations": ssa.withPeepholeOptimizations(); break;
                case "Common Subexpression Elimination": ssa.withCommonSubexpressionElimination(); break;
                case "Null Check Elimination": ssa.withNullCheckElimination(); break;
                case "Conditional Constant Propagation": ssa.withConditionalConstantPropagation(); break;
                case "Loop Invariant Code Motion": ssa.withLoopInvariantCodeMotion(); break;
                case "Loop Predication": ssa.withLoopPredication(); break;
                case "Induction Variable Simplification": ssa.withInductionVariableSimplification(); break;
                case "Jump Threading": ssa.withJumpThreading(); break;
                case "Block Merging": ssa.withBlockMerging(); break;
                case "Control Flow Reducibility": ssa.withControlFlowReducibility(); break;
                case "Duplicate Block Merging": ssa.withDuplicateBlockMerging(); break;
                case "Redundant Copy Elimination": ssa.withRedundantCopyElimination(); break;
                case "Bit-Tracking DCE": ssa.withBitTrackingDCE(); break;
                case "Correlated Value Propagation": ssa.withCorrelatedValuePropagation(); break;
            }
        }

        return ssa;
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        // Nothing specific to refresh
    }

    /**
     * Inner class for transform checkbox with tooltip.
     */
    private static class TransformCheckBox extends JCheckBox {
        private final String transformName;

        TransformCheckBox(String name, String description, boolean selected) {
            super(name, selected);
            this.transformName = name;
            setToolTipText(description);
            setBackground(JStudioTheme.getBgSecondary());
            setForeground(JStudioTheme.getTextPrimary());
        }

        String getTransformName() {
            return transformName;
        }
    }

    /**
     * Custom renderer for method combo box.
     */
    private static class MethodComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof MethodEntryModel) {
                MethodEntryModel method = (MethodEntryModel) value;
                MethodEntry entry = method.getMethodEntry();
                setText(entry.getName() + entry.getDesc());
            }

            setBackground(isSelected ? JStudioTheme.getSelection() : JStudioTheme.getBgTertiary());
            setForeground(JStudioTheme.getTextPrimary());

            return this;
        }
    }
}
