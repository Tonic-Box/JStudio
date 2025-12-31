package com.tonic.ui.editor;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.MainFrame;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.*;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.SwingUtilities;

/**
 * Welcome tab showing project info and quick links to main() methods.
 */
public class WelcomeTab extends JPanel implements ThemeChangeListener {

    private final MainFrame mainFrame;
    private ProjectModel projectModel;

    private final JPanel contentPanel;
    private JPanel mainMethodsPanel;
    private JPanel statsPanel;
    private JPanel versionStatsPanel;
    private JLabel projectNameLabel;
    private JLabel classCountLabel;
    private JLabel methodCountLabel;
    private JLabel fieldCountLabel;
    private JLabel packageCountLabel;
    private JLabel interfaceCountLabel;
    private final JScrollPane scrollPane;

    public WelcomeTab(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        // Create scrollable content
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(JStudioTheme.getBgTertiary());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        // Header section
        contentPanel.add(createHeaderSection());
        contentPanel.add(Box.createVerticalStrut(30));

        // Stats section
        contentPanel.add(createStatsSection());
        contentPanel.add(Box.createVerticalStrut(30));

        // Main methods section
        contentPanel.add(createMainMethodsSection());
        contentPanel.add(Box.createVerticalStrut(30));

        // Quick actions section
        contentPanel.add(createQuickActionsSection());

        // Add glue to push content to top
        contentPanel.add(Box.createVerticalGlue());

        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        contentPanel.setBackground(JStudioTheme.getBgTertiary());
        mainMethodsPanel.setBackground(JStudioTheme.getBgTertiary());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        if (statsPanel != null) {
            statsPanel.setBackground(JStudioTheme.getBgSecondary());
            statsPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JStudioTheme.getBorder(), 1),
                    BorderFactory.createEmptyBorder(16, 20, 16, 20)
            ));
        }

        if (versionStatsPanel != null) {
            versionStatsPanel.setBackground(JStudioTheme.getBgTertiary());
        }

        applyThemeRecursively(contentPanel);
        revalidate();
        repaint();
    }

    private void applyThemeRecursively(Component component) {
        if (component instanceof JPanel) {
            JPanel panel = (JPanel) component;
            Color bg = panel.getBackground();
            if (bg != null) {
                if (panel == statsPanel) {
                    panel.setBackground(JStudioTheme.getBgSecondary());
                } else if (isVersionChip(panel)) {
                    panel.setBackground(JStudioTheme.getBgSecondary());
                    panel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                            BorderFactory.createEmptyBorder(2, 8, 2, 8)
                    ));
                } else {
                    panel.setBackground(JStudioTheme.getBgTertiary());
                }
            }
            for (Component child : panel.getComponents()) {
                applyThemeRecursively(child);
            }
        } else if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            Color fg = label.getForeground();
            if (fg != null && !fg.equals(JStudioTheme.getAccent())) {
                if (isSecondaryText(label)) {
                    label.setForeground(JStudioTheme.getTextSecondary());
                } else {
                    label.setForeground(JStudioTheme.getTextPrimary());
                }
            }
        } else if (component instanceof JButton) {
            JButton button = (JButton) component;
            button.setBackground(JStudioTheme.getBgSecondary());
            button.setForeground(JStudioTheme.getTextPrimary());
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                    BorderFactory.createEmptyBorder(8, 16, 8, 16)
            ));
        }
    }

    private boolean isVersionChip(JPanel panel) {
        return panel.getComponentCount() == 2
            && panel.getComponent(0) instanceof JLabel
            && panel.getComponent(1) instanceof JLabel
            && panel.getParent() == versionStatsPanel;
    }

    private boolean isSecondaryText(JLabel label) {
        String text = label.getText();
        return text != null && (text.endsWith(":") || text.startsWith("(") || text.equals("No project loaded")
            || text.equals("No classes loaded") || text.equals("No main() methods found")
            || text.startsWith(".main("));
    }

    private JPanel createHeaderSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(JStudioTheme.getBgTertiary());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Logo/Title
        JLabel titleLabel = new JLabel("JStudio");
        titleLabel.setFont(JStudioTheme.getUIFont(28).deriveFont(Font.BOLD));
        titleLabel.setForeground(JStudioTheme.getAccent());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);

        panel.add(Box.createVerticalStrut(8));

        // Subtitle
        JLabel subtitleLabel = new JLabel("Java Reverse Engineering Suite");
        subtitleLabel.setFont(JStudioTheme.getUIFont(14));
        subtitleLabel.setForeground(JStudioTheme.getTextSecondary());
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(subtitleLabel);

        return panel;
    }

    private JPanel createStatsSection() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(JStudioTheme.getBgTertiary());
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel headerLabel = new JLabel("Project Statistics");
        headerLabel.setFont(JStudioTheme.getUIFont(14).deriveFont(Font.BOLD));
        headerLabel.setForeground(JStudioTheme.getTextPrimary());
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(headerLabel);
        container.add(Box.createVerticalStrut(12));

        statsPanel = new JPanel(new GridBagLayout());
        statsPanel.setBackground(JStudioTheme.getBgSecondary());
        statsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder(), 1),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPanel.setMaximumSize(new Dimension(700, 200));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 8, 3, 24);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        statsPanel.add(createStatLabel("Project:"), gbc);
        gbc.gridx = 1;
        projectNameLabel = new JLabel("No project loaded");
        projectNameLabel.setForeground(JStudioTheme.getTextPrimary());
        projectNameLabel.setFont(JStudioTheme.getUIFont(12).deriveFont(Font.BOLD));
        statsPanel.add(projectNameLabel, gbc);

        gbc.gridx = 2;
        statsPanel.add(createStatLabel("Packages:"), gbc);
        gbc.gridx = 3;
        packageCountLabel = new JLabel("0");
        packageCountLabel.setForeground(JStudioTheme.getTextPrimary());
        statsPanel.add(packageCountLabel, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        statsPanel.add(createStatLabel("Classes:"), gbc);
        gbc.gridx = 1;
        classCountLabel = new JLabel("0");
        classCountLabel.setForeground(JStudioTheme.getTextPrimary());
        statsPanel.add(classCountLabel, gbc);

        gbc.gridx = 2;
        statsPanel.add(createStatLabel("Interfaces:"), gbc);
        gbc.gridx = 3;
        interfaceCountLabel = new JLabel("0");
        interfaceCountLabel.setForeground(JStudioTheme.getTextPrimary());
        statsPanel.add(interfaceCountLabel, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        statsPanel.add(createStatLabel("Methods:"), gbc);
        gbc.gridx = 1;
        methodCountLabel = new JLabel("0");
        methodCountLabel.setForeground(JStudioTheme.getTextPrimary());
        statsPanel.add(methodCountLabel, gbc);

        gbc.gridx = 2;
        statsPanel.add(createStatLabel("Fields:"), gbc);
        gbc.gridx = 3;
        fieldCountLabel = new JLabel("0");
        fieldCountLabel.setForeground(JStudioTheme.getTextPrimary());
        statsPanel.add(fieldCountLabel, gbc);

        container.add(statsPanel);
        container.add(Box.createVerticalStrut(16));

        JLabel versionHeader = new JLabel("Class File Versions");
        versionHeader.setFont(JStudioTheme.getUIFont(12).deriveFont(Font.BOLD));
        versionHeader.setForeground(JStudioTheme.getTextSecondary());
        versionHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(versionHeader);
        container.add(Box.createVerticalStrut(8));

        versionStatsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        versionStatsPanel.setBackground(JStudioTheme.getBgTertiary());
        versionStatsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel noVersionLabel = new JLabel("No classes loaded");
        noVersionLabel.setForeground(JStudioTheme.getTextSecondary());
        versionStatsPanel.add(noVersionLabel);
        container.add(versionStatsPanel);

        return container;
    }

    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextSecondary());
        return label;
    }

    private String majorVersionToJavaVersion(int majorVersion) {
        switch (majorVersion) {
            case 45: return "Java 1.1";
            case 46: return "Java 1.2";
            case 47: return "Java 1.3";
            case 48: return "Java 1.4";
            case 49: return "Java 5";
            case 50: return "Java 6";
            case 51: return "Java 7";
            case 52: return "Java 8";
            case 53: return "Java 9";
            case 54: return "Java 10";
            case 55: return "Java 11";
            case 56: return "Java 12";
            case 57: return "Java 13";
            case 58: return "Java 14";
            case 59: return "Java 15";
            case 60: return "Java 16";
            case 61: return "Java 17";
            case 62: return "Java 18";
            case 63: return "Java 19";
            case 64: return "Java 20";
            case 65: return "Java 21";
            case 66: return "Java 22";
            case 67: return "Java 23";
            case 68: return "Java 24";
            default:
                if (majorVersion > 68) {
                    return "Java " + (majorVersion - 44);
                }
                return "v" + majorVersion;
        }
    }

    private JPanel createMainMethodsSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(JStudioTheme.getBgTertiary());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Section header
        JLabel headerLabel = new JLabel("Entry Points (main methods)");
        headerLabel.setFont(JStudioTheme.getUIFont(14).deriveFont(Font.BOLD));
        headerLabel.setForeground(JStudioTheme.getTextPrimary());
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(headerLabel);

        panel.add(Box.createVerticalStrut(12));

        // Container for main method links
        mainMethodsPanel = new JPanel();
        mainMethodsPanel.setLayout(new BoxLayout(mainMethodsPanel, BoxLayout.Y_AXIS));
        mainMethodsPanel.setBackground(JStudioTheme.getBgTertiary());
        mainMethodsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel noMainLabel = new JLabel("No project loaded");
        noMainLabel.setForeground(JStudioTheme.getTextSecondary());
        noMainLabel.setFont(JStudioTheme.getUIFont(12));
        mainMethodsPanel.add(noMainLabel);

        panel.add(mainMethodsPanel);

        return panel;
    }

    private JPanel createQuickActionsSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(JStudioTheme.getBgTertiary());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Section header
        JLabel headerLabel = new JLabel("Quick Actions");
        headerLabel.setFont(JStudioTheme.getUIFont(14).deriveFont(Font.BOLD));
        headerLabel.setForeground(JStudioTheme.getTextPrimary());
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(headerLabel);

        panel.add(Box.createVerticalStrut(12));

        // Action buttons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonsPanel.setBackground(JStudioTheme.getBgTertiary());
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        buttonsPanel.add(createActionButton("Open File", Icons.getIcon("folder"), mainFrame::showOpenDialog));
        buttonsPanel.add(createActionButton("Search", Icons.getIcon("search"), mainFrame::showFindInProjectDialog));
        buttonsPanel.add(createActionButton("Transforms", Icons.getIcon("settings"), mainFrame::showTransformDialog));

        panel.add(buttonsPanel);

        return panel;
    }

    private JButton createActionButton(String text, javax.swing.Icon icon, Runnable action) {
        JButton button = new JButton(text, icon);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(JStudioTheme.getBgSecondary());
            }
        });

        return button;
    }

    /**
     * Set the project model and refresh the display.
     */
    public void setProjectModel(ProjectModel project) {
        this.projectModel = project;
        refresh();
    }

    /**
     * Refresh the welcome tab with current project info.
     */
    public void refresh() {
        if (projectModel == null) {
            projectNameLabel.setText("No project loaded");
            classCountLabel.setText("0");
            methodCountLabel.setText("0");
            fieldCountLabel.setText("0");
            packageCountLabel.setText("0");
            interfaceCountLabel.setText("0");
            updateVersionStatsPanel(new TreeMap<>());
            updateMainMethodsPanel(new ArrayList<>());
            return;
        }

        String projectName = projectModel.getSourceFile() != null
                ? projectModel.getSourceFile().getName()
                : "Unknown";
        projectNameLabel.setText(projectName);

        List<ClassEntryModel> allClasses = projectModel.getAllClasses();

        int methodCount = 0;
        int fieldCount = 0;
        int interfaceCount = 0;
        int classCount = 0;
        Set<String> packages = new TreeSet<>();
        Map<Integer, Integer> versionCounts = new TreeMap<>();

        for (ClassEntryModel cls : allClasses) {
            methodCount += cls.getMethods().size();
            fieldCount += cls.getFields().size();

            int access = cls.getClassFile().getAccess();
            if ((access & 0x0200) != 0) {
                interfaceCount++;
            } else {
                classCount++;
            }

            String className = cls.getClassName();
            int lastSlash = className.lastIndexOf('/');
            String pkg = lastSlash > 0 ? className.substring(0, lastSlash) : "(default)";
            packages.add(pkg);

            int majorVersion = cls.getClassFile().getMajorVersion();
            versionCounts.merge(majorVersion, 1, Integer::sum);
        }

        classCountLabel.setText(String.valueOf(classCount));
        interfaceCountLabel.setText(String.valueOf(interfaceCount));
        methodCountLabel.setText(String.valueOf(methodCount));
        fieldCountLabel.setText(String.valueOf(fieldCount));
        packageCountLabel.setText(String.valueOf(packages.size()));

        updateVersionStatsPanel(versionCounts);

        List<MainMethodInfo> mainMethods = findMainMethods();
        updateMainMethodsPanel(mainMethods);
    }

    private void updateVersionStatsPanel(Map<Integer, Integer> versionCounts) {
        versionStatsPanel.removeAll();

        if (versionCounts.isEmpty()) {
            JLabel noVersionLabel = new JLabel("No classes loaded");
            noVersionLabel.setForeground(JStudioTheme.getTextSecondary());
            versionStatsPanel.add(noVersionLabel);
        } else {
            for (Map.Entry<Integer, Integer> entry : versionCounts.entrySet()) {
                int majorVersion = entry.getKey();
                int count = entry.getValue();
                String javaVersion = majorVersionToJavaVersion(majorVersion);

                JPanel chip = createVersionChip(javaVersion, count);
                versionStatsPanel.add(chip);
            }
        }

        versionStatsPanel.revalidate();
        versionStatsPanel.repaint();
    }

    private JPanel createVersionChip(String version, int count) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        chip.setBackground(JStudioTheme.getBgSecondary());
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));

        JLabel versionLabel = new JLabel(version);
        versionLabel.setForeground(JStudioTheme.getAccent());
        versionLabel.setFont(JStudioTheme.getUIFont(11).deriveFont(Font.BOLD));
        chip.add(versionLabel);

        JLabel countLabel = new JLabel("(" + count + ")");
        countLabel.setForeground(JStudioTheme.getTextSecondary());
        countLabel.setFont(JStudioTheme.getUIFont(11));
        chip.add(countLabel);

        return chip;
    }

    private List<MainMethodInfo> findMainMethods() {
        List<MainMethodInfo> result = new ArrayList<>();
        if (projectModel == null) return result;

        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                MethodEntry method = methodModel.getMethodEntry();
                // Check for public static void main(String[])
                if ("main".equals(method.getName())
                        && "([Ljava/lang/String;)V".equals(method.getDesc())
                        && (method.getAccess() & 0x0009) == 0x0009) { // public static
                    result.add(new MainMethodInfo(classEntry, methodModel));
                }
            }
        }

        return result;
    }

    private void updateMainMethodsPanel(List<MainMethodInfo> mainMethods) {
        mainMethodsPanel.removeAll();

        if (mainMethods.isEmpty()) {
            JLabel noMainLabel = new JLabel(projectModel == null
                    ? "No project loaded"
                    : "No main() methods found");
            noMainLabel.setForeground(JStudioTheme.getTextSecondary());
            noMainLabel.setFont(JStudioTheme.getUIFont(12));
            mainMethodsPanel.add(noMainLabel);
        } else {
            for (MainMethodInfo info : mainMethods) {
                mainMethodsPanel.add(createMainMethodLink(info));
                mainMethodsPanel.add(Box.createVerticalStrut(4));
            }
        }

        mainMethodsPanel.revalidate();
        mainMethodsPanel.repaint();
    }

    private JPanel createMainMethodLink(MainMethodInfo info) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        panel.setBackground(JStudioTheme.getBgTertiary());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Class icon
        JLabel iconLabel = new JLabel(info.classEntry.getIcon());
        panel.add(iconLabel);

        // Clickable class name
        String safeClassName = sanitize(info.classEntry.getClassName());
        JLabel classLink = new JLabel(safeClassName);
        classLink.setForeground(JStudioTheme.getAccent());
        classLink.setFont(JStudioTheme.getCodeFont(12));
        classLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        classLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                mainFrame.openClassInEditor(info.classEntry);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                classLink.setText("<html><u>" + safeClassName + "</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                classLink.setText(safeClassName);
            }
        });
        panel.add(classLink);

        // main() indicator
        JLabel mainLabel = new JLabel(".main(String[])");
        mainLabel.setForeground(JStudioTheme.getTextSecondary());
        mainLabel.setFont(JStudioTheme.getCodeFont(12));
        panel.add(mainLabel);

        return panel;
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Helper class to hold main method info.
     */
    private static class MainMethodInfo {
        final ClassEntryModel classEntry;
        final MethodEntryModel methodEntry;

        MainMethodInfo(ClassEntryModel classEntry, MethodEntryModel methodEntry) {
            this.classEntry = classEntry;
            this.methodEntry = methodEntry;
        }
    }
}
