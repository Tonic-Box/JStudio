package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.core.util.JvmDescriptorFormatter;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The debugger's bytecode disassembly view: the {@link JTable} plus its model and category-colouring renderer, the
 * scroll pane with its titled border, and the execution-highlight / breakpoint-dot bookkeeping. Owns the displayed
 * instruction rows and the PC-to-row map. Context-menu actions are delegated through injected callbacks.
 */
final class BytecodeTableView {

    private final JTable table;
    private final BytecodeTableModel model;
    private final JScrollPane scrollPane;
    private final List<InstructionEntry> instructions = new ArrayList<>();
    private final Map<Integer, Integer> pcToRowMap = new HashMap<>();
    private final Supplier<Set<Integer>> breakpoints;

    BytecodeTableView(Supplier<Set<Integer>> breakpoints,
                      IntConsumer onToggleBreakpoint,
                      IntConsumer onRunToCursor) {
        this.breakpoints = breakpoints;
        this.model = new BytecodeTableModel();
        this.table = new JTable(model);

        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setSelectionBackground(JStudioTheme.getAccent());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getTableHeader().setBackground(JStudioTheme.getBgPrimary());
        table.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        table.setRowHeight(20);

        table.setDefaultRenderer(Object.class, new BytecodeCellRenderer());

        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);

        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem toggleBreakpoint = new JMenuItem("Toggle Breakpoint");
        toggleBreakpoint.addActionListener(e -> withSelectedEntry(entry -> onToggleBreakpoint.accept(entry.offset)));
        contextMenu.add(toggleBreakpoint);

        JMenuItem runToCursor = new JMenuItem("Run to Cursor");
        runToCursor.addActionListener(e -> withSelectedEntry(entry -> onRunToCursor.accept(entry.offset)));
        contextMenu.add(runToCursor);

        table.setComponentPopupMenu(contextMenu);

        scrollPane = new JScrollPane(table);
        setTitle(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
    }

    JScrollPane getScrollPane() {
        return scrollPane;
    }

    /** Applies the titled border to the scroll pane reflecting the displayed method (or "Bytecode" when null). */
    void setTitle(MethodEntry method) {
        String title;
        if (method == null) {
            title = "Bytecode";
        } else {
            String simpleName = JvmDescriptorFormatter.getSimpleClassName(method.getOwnerName());
            title = String.format("Bytecode - %s.%s%s", simpleName, method.getName(), method.getDesc());
        }
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));
    }

    /** Replaces the displayed rows and PC map from a fresh disassembly result. */
    void setInstructions(List<InstructionEntry> newInstructions, Map<Integer, Integer> newPcToRow) {
        instructions.clear();
        pcToRowMap.clear();
        instructions.addAll(newInstructions);
        pcToRowMap.putAll(newPcToRow);
        model.setInstructions(instructions);
    }

    /** Clears all rows and the PC map, leaving an empty table. */
    void clearInstructions() {
        instructions.clear();
        pcToRowMap.clear();
        model.setInstructions(instructions);
    }

    /** Returns the mnemonic (and operands) for the instruction at the given PC, or "PC=n" if unknown. */
    String getInstructionAtPC(int pc) {
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

    void highlightInstruction(int pc) {
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
                int tableRowCount = table.getRowCount();
                if (row < tableRowCount) {
                    table.setRowSelectionInterval(row, row);
                    table.scrollRectToVisible(table.getCellRect(row, 0, true));
                }
            });
        }

        model.fireTableDataChanged();
    }

    /** Clears the table execution highlight and repaints; the source-view half is handled by the caller. */
    void clearHighlight() {
        for (InstructionEntry entry : instructions) {
            entry.current = false;
        }
        model.fireTableDataChanged();
    }

    /** Repaints the table, e.g. after a breakpoint set/cleared elsewhere. */
    void refresh() {
        model.fireTableDataChanged();
    }

    private void withSelectedEntry(java.util.function.Consumer<InstructionEntry> action) {
        int row = table.getSelectedRow();
        if (row < 0) return;
        InstructionEntry entry = model.getEntryAt(row);
        if (entry == null) return;
        action.accept(entry);
    }

    private static class BytecodeTableModel extends AbstractTableModel {
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
        private Color colorLoadStore() { return JStudioTheme.getBcLoad(); }
        private Color colorArithmetic() { return JStudioTheme.getBcArithmetic(); }
        private Color colorControlFlow() { return JStudioTheme.getBcBranch(); }
        private Color colorInvoke() { return JStudioTheme.getBcInvoke(); }
        private Color colorFieldAccess() { return JStudioTheme.getBcField(); }
        private Color colorObject() { return JStudioTheme.getBcNew(); }
        private Color colorStack() { return JStudioTheme.getBcStack(); }
        private Color colorConstant() { return JStudioTheme.getBcConst(); }
        private Color colorString() { return JStudioTheme.getBcConst(); }
        private Color colorOther() { return JStudioTheme.getTextPrimary(); }
        private Color highlightedTextColor() { return JStudioTheme.getTextPrimary(); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            InstructionEntry entry = model.getEntryAt(row);
            boolean isHighlighted = isSelected || (entry != null && entry.current);
            boolean hasBreakpoint = entry != null && breakpoints.get().contains(entry.offset);

            if (isSelected) {
                setBackground(JStudioTheme.getAccent());
            } else if (entry != null && entry.current) {
                setBackground(JStudioTheme.getSuccess().darker());
            } else if (hasBreakpoint) {
                setBackground(JStudioTheme.getError().darker().darker());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }

            if (column == 0 && hasBreakpoint) {
                setText("\u25CF " + value);
            }

            if (isHighlighted) {
                setForeground(highlightedTextColor());
            } else if (entry != null) {
                Color textColor = getCategoryColor(entry.category);
                if (column == 2) {
                    setForeground(textColor);
                } else if (column == 3) {
                    String operands = entry.operands;
                    if (operands.startsWith("\"")) {
                        setForeground(colorString());
                    } else if (operands.contains(".") && !operands.matches(".*\\d+\\.\\d+.*")) {
                        setForeground(colorInvoke());
                    } else if (operands.startsWith("-> ")) {
                        setForeground(colorControlFlow());
                    } else if (operands.startsWith("local[")) {
                        setForeground(colorLoadStore());
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
            if (category == null) return colorOther();
            switch (category) {
                case LOAD_STORE: return colorLoadStore();
                case ARITHMETIC: return colorArithmetic();
                case CONTROL_FLOW: return colorControlFlow();
                case INVOKE: return colorInvoke();
                case FIELD_ACCESS: return colorFieldAccess();
                case OBJECT: return colorObject();
                case STACK: return colorStack();
                case CONSTANT: return colorConstant();
                default: return colorOther();
            }
        }
    }
}
