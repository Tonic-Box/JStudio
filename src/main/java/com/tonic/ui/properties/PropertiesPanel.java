package com.tonic.ui.properties;

import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.MainFrame;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.simulation.metrics.ComplexityMetrics;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class PropertiesPanel extends ThemedJPanel {

    private final JTabbedPane tabbedPane;
    private final JPanel classPanel;
    private final JPanel methodPanel;
    private final JPanel fieldPanel;
    private final JTextArea detailsArea;

    private ClassEntryModel currentClass;
    private MethodEntryModel currentMethod;
    private FieldEntryModel currentField;

    public PropertiesPanel(MainFrame mainFrame) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());
        tabbedPane.setBorder(null);

        // Class properties
        classPanel = createPropertiesGrid();
        tabbedPane.addTab("Class", classPanel);

        // Method properties
        methodPanel = createPropertiesGrid();
        tabbedPane.addTab("Method", methodPanel);

        // Field properties
        fieldPanel = createPropertiesGrid();
        tabbedPane.addTab("Field", fieldPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Details area at bottom
        detailsArea = new JTextArea(5, 30);
        detailsArea.setEditable(false);
        detailsArea.setBackground(JStudioTheme.getBgTertiary());
        detailsArea.setForeground(JStudioTheme.getTextPrimary());
        detailsArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM));

        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        add(detailsScroll, BorderLayout.SOUTH);

        showEmptyState();
    }

    @Override
    protected void applyChildThemes() {
        tabbedPane.setBackground(JStudioTheme.getBgSecondary());
        tabbedPane.setForeground(JStudioTheme.getTextPrimary());

        classPanel.setBackground(JStudioTheme.getBgSecondary());
        methodPanel.setBackground(JStudioTheme.getBgSecondary());
        fieldPanel.setBackground(JStudioTheme.getBgSecondary());

        detailsArea.setBackground(JStudioTheme.getBgTertiary());
        detailsArea.setForeground(JStudioTheme.getTextPrimary());
        detailsArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
    }

    private JPanel createPropertiesGrid() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private void showEmptyState() {
        detailsArea.setText("Select a class, method, or field to view properties.");
    }

    /**
     * Show class properties.
     */
    public void showClass(ClassEntryModel classEntry) {
        this.currentClass = classEntry;
        this.currentMethod = null;
        this.currentField = null;

        updateClassPanel(classEntry);
        tabbedPane.setSelectedComponent(classPanel);
    }

    /**
     * Show method properties.
     */
    public void showMethod(MethodEntryModel method) {
        this.currentMethod = method;
        this.currentField = null;

        updateMethodPanel(method);
        tabbedPane.setSelectedComponent(methodPanel);
    }

    /**
     * Show field properties.
     */
    public void showField(FieldEntryModel field) {
        this.currentField = field;
        this.currentMethod = null;

        updateFieldPanel(field);
        tabbedPane.setSelectedComponent(fieldPanel);
    }

    private void updateClassPanel(ClassEntryModel classEntry) {
        classPanel.removeAll();

        if (classEntry == null) {
            addProperty(classPanel, 0, "No class selected", "");
            return;
        }

        int row = 0;
        addProperty(classPanel, row++, "Name", classEntry.getClassName());
        addProperty(classPanel, row++, "Super", classEntry.getSuperClassName());
        addProperty(classPanel, row++, "Interfaces", String.join(", ", classEntry.getInterfaceNames()));
        addProperty(classPanel, row++, "Access", formatAccessFlags(classEntry.getClassFile().getAccess()));
        addProperty(classPanel, row++, "Version", classEntry.getClassFile().getMajorVersion() + "." +
                classEntry.getClassFile().getMinorVersion());
        addProperty(classPanel, row++, "Methods", String.valueOf(classEntry.getMethods().size()));
        addProperty(classPanel, row++, "Fields", String.valueOf(classEntry.getFields().size()));
        addProperty(classPanel, row++, "Const Pool", String.valueOf(classEntry.getClassFile().getConstPool().getItems().size()));

        // Add filler
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weighty = 1;
        classPanel.add(new JPanel() {{ setBackground(JStudioTheme.getBgSecondary()); }}, gbc);

        classPanel.revalidate();
        classPanel.repaint();

        // Update details
        String sb = "Full name: " + classEntry.getClassName().replace('/', '.') + "\n" +
                "Internal name: " + classEntry.getClassName() + "\n";
        detailsArea.setText(sb);
    }

    private void updateMethodPanel(MethodEntryModel method) {
        methodPanel.removeAll();

        if (method == null) {
            addProperty(methodPanel, 0, "No method selected", "");
            return;
        }

        MethodEntry entry = method.getMethodEntry();
        int row = 0;
        addProperty(methodPanel, row++, "Name", entry.getName());
        addProperty(methodPanel, row++, "Descriptor", entry.getDesc());
        addProperty(methodPanel, row++, "Access", formatAccessFlags(entry.getAccess()));

        CodeAttribute code = entry.getCodeAttribute();
        if (code != null) {
            addProperty(methodPanel, row++, "Max Stack", String.valueOf(code.getMaxStack()));
            addProperty(methodPanel, row++, "Max Locals", String.valueOf(code.getMaxLocals()));
            addProperty(methodPanel, row++, "Code Length", String.valueOf(code.getCode().length));
            addProperty(methodPanel, row++, "Exceptions", String.valueOf(code.getExceptionTable().size()));
        } else {
            addProperty(methodPanel, row++, "Code", "None (abstract/native)");
        }

        ComplexityMetrics metrics = method.getComplexityMetrics();
        if (metrics != null) {
            row = addSeparator(methodPanel, row, "Complexity");
            addProperty(methodPanel, row++, "Cyclomatic",
                    metrics.getCyclomaticComplexity() + " (" + metrics.getComplexityRating() + ")");
            addProperty(methodPanel, row++, "Blocks", String.valueOf(metrics.getBlockCount()));
            addProperty(methodPanel, row++, "Branches", String.valueOf(metrics.getBranchCount()));
            addProperty(methodPanel, row++, "Loops", String.valueOf(metrics.getLoopCount()));
            addProperty(methodPanel, row++, "Instructions", String.valueOf(metrics.getInstructionCount()));
        }

        // Add filler
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weighty = 1;
        methodPanel.add(new JPanel() {{ setBackground(JStudioTheme.getBgSecondary()); }}, gbc);

        methodPanel.revalidate();
        methodPanel.repaint();

        // Update details
        String sb = "Signature: " + entry.getName() + entry.getDesc() + "\n" +
                "Return: " + parseReturnType(entry.getDesc()) + "\n" +
                "Parameters: " + parseParameters(entry.getDesc()) + "\n";
        detailsArea.setText(sb);
    }

    private void updateFieldPanel(FieldEntryModel field) {
        fieldPanel.removeAll();

        if (field == null) {
            addProperty(fieldPanel, 0, "No field selected", "");
            return;
        }

        FieldEntry entry = field.getFieldEntry();
        int row = 0;
        addProperty(fieldPanel, row++, "Name", entry.getName());
        addProperty(fieldPanel, row++, "Descriptor", entry.getDesc());
        addProperty(fieldPanel, row++, "Type", parseFieldType(entry.getDesc()));
        addProperty(fieldPanel, row++, "Access", formatAccessFlags(entry.getAccess()));

        // Add filler
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weighty = 1;
        fieldPanel.add(new JPanel() {{ setBackground(JStudioTheme.getBgSecondary()); }}, gbc);

        fieldPanel.revalidate();
        fieldPanel.repaint();

        // Update details
        detailsArea.setText("Full signature: " + entry.getName() + " : " + entry.getDesc() + "\n");
    }

    private void addProperty(JPanel panel, int row, String label, String value) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel labelComp = new JLabel(label + ":");
        labelComp.setForeground(JStudioTheme.getTextSecondary());
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel valueComp = new JLabel(sanitize(value));
        valueComp.setForeground(JStudioTheme.getTextPrimary());
        panel.add(valueComp, gbc);
    }

    private int addSeparator(JPanel panel, int row, String title) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 4, 4, 4);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel sep = new JLabel("\u2500\u2500 " + title + " \u2500\u2500");
        sep.setForeground(JStudioTheme.getTextSecondary());
        panel.add(sep, gbc);
        return row + 1;
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String formatAccessFlags(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & 0x0001) != 0) sb.append("public ");
        if ((flags & 0x0002) != 0) sb.append("private ");
        if ((flags & 0x0004) != 0) sb.append("protected ");
        if ((flags & 0x0008) != 0) sb.append("static ");
        if ((flags & 0x0010) != 0) sb.append("final ");
        if ((flags & 0x0020) != 0) sb.append("synchronized ");
        if ((flags & 0x0040) != 0) sb.append("volatile ");
        if ((flags & 0x0080) != 0) sb.append("transient ");
        if ((flags & 0x0100) != 0) sb.append("native ");
        if ((flags & 0x0200) != 0) sb.append("interface ");
        if ((flags & 0x0400) != 0) sb.append("abstract ");
        if ((flags & 0x1000) != 0) sb.append("synthetic ");
        if ((flags & 0x2000) != 0) sb.append("annotation ");
        if ((flags & 0x4000) != 0) sb.append("enum ");
        return sb.toString().trim();
    }

    private String parseReturnType(String desc) {
        int parenClose = desc.indexOf(')');
        if (parenClose >= 0 && parenClose < desc.length() - 1) {
            return parseType(desc.substring(parenClose + 1));
        }
        return "?";
    }

    private String parseParameters(String desc) {
        int parenOpen = desc.indexOf('(');
        int parenClose = desc.indexOf(')');
        if (parenOpen >= 0 && parenClose > parenOpen) {
            String params = desc.substring(parenOpen + 1, parenClose);
            if (params.isEmpty()) return "none";
            return params;
        }
        return "?";
    }

    private String parseFieldType(String desc) {
        return parseType(desc);
    }

    private String parseType(String desc) {
        if (desc.isEmpty()) return "void";
        char c = desc.charAt(0);
        switch (c) {
            case 'V': return "void";
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case '[': return parseType(desc.substring(1)) + "[]";
            case 'L':
                int semi = desc.indexOf(';');
                if (semi > 1) {
                    String className = desc.substring(1, semi);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return desc;
            default: return desc;
        }
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        if (currentClass != null) {
            updateClassPanel(currentClass);
        }
        if (currentMethod != null) {
            updateMethodPanel(currentMethod);
        }
        if (currentField != null) {
            updateFieldPanel(currentField);
        }
    }
}
