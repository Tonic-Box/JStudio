package com.tonic.ui.query;

import com.tonic.event.EventBus;
import com.tonic.event.events.ProjectLoadedEvent;
import com.tonic.parser.ClassPool;
import com.tonic.ui.MainFrame;
import com.tonic.model.ProjectModel;
import com.tonic.analysis.query.exec.QueryBatchRunner;
import com.tonic.analysis.query.exec.QueryService;
import com.tonic.analysis.query.planner.QueryMatch;
import com.tonic.analysis.query.planner.QueryTarget;
import com.tonic.service.ProjectService;
import com.tonic.ui.core.component.WrapLayout;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.SyntaxColors;
import com.tonic.ui.theme.ThemeManager;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.Segment;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class QueryExplorerPanel extends JPanel {

    private static final String SYNTAX_STYLE_QUERY = "text/jstudio-query";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_QUERY, "com.tonic.ui.query.QueryTokenMaker");
    }

    private RSyntaxTextArea queryInput;
    private JButton runButton;
    private JButton stopButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    private JSpinner timeBudgetSpinner;

    private JTable resultsTable;
    private TreeResultTableModel tableModel;

    private QueryService queryService;
    private ClassPool boundClassPool;
    private volatile boolean running;
    private final MainFrame mainFrame;

    public QueryExplorerPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        applyTheme();
        // Don't let the panel's internal preferred sizes pin the tool-window column width; the
        // scroll panes absorb any shortfall, so the split stays freely resizable.
        setMinimumSize(new Dimension(0, 0));
        ThemeManager.getInstance().addThemeChangeListener(t -> SwingUtilities.invokeLater(this::applyTheme));
        // Loading a new project replaces the class pool, so prior results no longer apply — clear them.
        EventBus.getInstance().register(ProjectLoadedEvent.class, e -> clearResults());
    }

    /** Clears stale query results when a new project replaces the current one. */
    private void clearResults() {
        tableModel.clear();
        statusLabel.setText("Enter a query and click Run");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
    }

    /**
     * (Re)builds the query service when the active project's class pool changes, so a long-lived
     * panel always queries the current project rather than the one present when it was created.
     */
    private boolean ensureService(ProjectModel project) {
        if (project == null) {
            return false;
        }
        if (queryService == null || project.getClassPool() != boundClassPool) {
            if (queryService != null) {
                queryService.shutdown();
            }
            boundClassPool = project.getClassPool();
            queryService = new QueryService(boundClassPool);
        }
        return true;
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createInputPanel(), createResultsPanel());
        split.setResizeWeight(0.4);
        split.setBorder(null);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        setupKeyboardShortcuts();
    }

    private void applyTheme() {
        Color bgPrimary = JStudioTheme.getBgPrimary();
        Color bgSecondary = JStudioTheme.getBgSecondary();
        Color bgSurface = JStudioTheme.getBgSurface();
        Color textPrimary = JStudioTheme.getTextPrimary();
        Color textSecondary = JStudioTheme.getTextSecondary();
        Color accent = JStudioTheme.getAccent();
        Color border = JStudioTheme.getBorder();
        Color selection = JStudioTheme.getSelection();

        setBackground(bgPrimary);

        queryInput.setBackground(JStudioTheme.getBgTertiary());
        queryInput.setForeground(textPrimary);
        queryInput.setCaretColor(textPrimary);
        queryInput.setSelectionColor(selection);
        queryInput.setSelectedTextColor(textPrimary);
        queryInput.setCurrentLineHighlightColor(JStudioTheme.getLineHighlight());
        queryInput.setMatchedBracketBGColor(selection);
        queryInput.setMatchedBracketBorderColor(accent);
        queryInput.setFont(JStudioTheme.getCodeFont(13));
        applyQuerySyntaxScheme();

        resultsTable.setBackground(bgSurface);
        resultsTable.setForeground(textPrimary);
        resultsTable.setSelectionBackground(selection);
        resultsTable.setSelectionForeground(textPrimary);
        resultsTable.setGridColor(border);
        resultsTable.setFont(JStudioTheme.getUIFont(12));

        JTableHeader header = resultsTable.getTableHeader();
        header.setBackground(bgSecondary);
        header.setForeground(textPrimary);
        header.setFont(JStudioTheme.getUIFont(12).deriveFont(Font.BOLD));

        resultsTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                QueryMatch result = tableModel.getResultAt(row);
                boolean isChildRow = tableModel.isChildRow(row);
                setBackground(isSelected ? selection : bgSurface);
                if (isChildRow) {
                    setForeground(isSelected ? textPrimary : textSecondary);
                    setFont(getFont().deriveFont(Font.PLAIN));
                } else {
                    setForeground(isSelected ? textPrimary : accent);
                    setFont(getFont().deriveFont(result != null && result.hasEvidence() ? Font.BOLD : Font.PLAIN));
                }
                return this;
            }
        });

        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBackground(isSelected ? selection : bgSurface);
                setForeground(isSelected ? textPrimary : textSecondary);
                return this;
            }
        };
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(defaultRenderer);
        resultsTable.getColumnModel().getColumn(2).setCellRenderer(defaultRenderer);

        statusLabel.setForeground(textSecondary);
        statusLabel.setFont(JStudioTheme.getUIFont(12));

        applyThemeToPanel(this, bgPrimary, textPrimary, border);
    }

    private void applyQuerySyntaxScheme() {
        SyntaxScheme scheme = queryInput.getSyntaxScheme();
        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.RESERVED_WORD, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.RESERVED_WORD_2, SyntaxColors.getJavaMethod());
        setTokenStyle(scheme, Token.FUNCTION, SyntaxColors.getJavaOperator());
        setTokenStyle(scheme, Token.DATA_TYPE, SyntaxColors.getJavaType());
        setTokenStyle(scheme, Token.OPERATOR, SyntaxColors.getJavaOperator());
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.REGEX, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_BOOLEAN, SyntaxColors.getJavaConstant());
        queryInput.revalidate();
        queryInput.repaint();
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }

    private void applyThemeToPanel(JPanel panel, Color bg, Color fg, Color borderColor) {
        panel.setBackground(bg);

        if (panel.getBorder() instanceof TitledBorder) {
            TitledBorder tb = (TitledBorder) panel.getBorder();
            tb.setTitleColor(fg);
            panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor),
                tb.getTitle(),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                JStudioTheme.getUIFont(12).deriveFont(Font.BOLD),
                fg
            ));
        }

        for (Component c : panel.getComponents()) {
            if (c instanceof JPanel) {
                JPanel jp = (JPanel) c;
                applyThemeToPanel(jp, bg, fg, borderColor);
            } else if (c instanceof JSplitPane) {
                JSplitPane sp = (JSplitPane) c;
                sp.setBackground(bg);
                sp.setBorder(null);
                for (Component child : sp.getComponents()) {
                    if (child instanceof JPanel) {
                        applyThemeToPanel((JPanel) child, bg, fg, borderColor);
                    }
                }
            } else if (c instanceof JLabel) {
                JLabel label = (JLabel) c;
                label.setForeground(fg);
                label.setFont(JStudioTheme.getUIFont(12));
            } else if (c instanceof JButton) {
                JButton button = (JButton) c;
                button.setBackground(JStudioTheme.getBgSecondary());
                button.setForeground(fg);
                button.setFont(JStudioTheme.getUIFont(12));
                button.setFocusPainted(false);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)
                ));
            } else if (c instanceof JSpinner) {
                JSpinner spinner = (JSpinner) c;
                spinner.setBackground(JStudioTheme.getBgSurface());
                spinner.setForeground(fg);
                spinner.setFont(JStudioTheme.getUIFont(12));
                JComponent editor = spinner.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    JSpinner.DefaultEditor de = (JSpinner.DefaultEditor) editor;
                    de.getTextField().setBackground(JStudioTheme.getBgSurface());
                    de.getTextField().setForeground(fg);
                    de.getTextField().setCaretColor(fg);
                }
            } else if (c instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) c;
                sp.setBackground(bg);
                sp.getViewport().setBackground(JStudioTheme.getBgSurface());
                sp.setBorder(BorderFactory.createLineBorder(borderColor));
            } else if (c instanceof JProgressBar) {
                JProgressBar pb = (JProgressBar) c;
                pb.setBackground(JStudioTheme.getBgSecondary());
                pb.setForeground(JStudioTheme.getAccent());
                pb.setBorder(BorderFactory.createLineBorder(borderColor));
            }
        }
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Query Input"));

        queryInput = new RSyntaxTextArea(6, 40);
        queryInput.setSyntaxEditingStyle(SYNTAX_STYLE_QUERY);
        queryInput.setText("FIND methods\nWHERE HAS call\nWHERE (name == \"println\")");
        queryInput.setTabSize(4);
        queryInput.setAntiAliasingEnabled(true);
        queryInput.setCodeFoldingEnabled(false);
        queryInput.setBracketMatchingEnabled(true);
        queryInput.setHighlightCurrentLine(true);
        panel.add(new JScrollPane(queryInput), BorderLayout.CENTER);

        JPanel configPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 5));

        configPanel.add(new JLabel("Time budget (s):"));
        timeBudgetSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 300, 10));
        configPanel.add(timeBudgetSpinner);

        configPanel.add(Box.createHorizontalStrut(20));

        runButton = new JButton("Run Query");
        runButton.setMnemonic(KeyEvent.VK_R);
        runButton.addActionListener(e -> runQuery());
        configPanel.add(runButton);

        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopQuery());
        configPanel.add(stopButton);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 22));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        configPanel.add(progressBar);

        panel.add(configPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Results"));

        tableModel = new TreeResultTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setRowHeight(24);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(450);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(100);

        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = resultsTable.rowAtPoint(e.getPoint());
                int col = resultsTable.columnAtPoint(e.getPoint());

                if (row >= 0 && col == 0) {
                    QueryMatch result = tableModel.getResultAt(row);
                    if (result != null && result.hasEvidence() && !tableModel.isChildRow(row)) {
                        tableModel.toggleExpand(row);
                        return;
                    }
                }

                if (e.getClickCount() == 2) {
                    navigateToSelectedResult();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setPreferredSize(new Dimension(850, 160));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Enter a query and click Run");
        statusPanel.add(statusLabel);
        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e -> showHelp());
        panel.add(helpButton);

        return panel;
    }

    private void setupKeyboardShortcuts() {
        registerKeyboardAction(
            e -> runQuery(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    private void runQuery() {
        if (running) return;

        String queryText = queryInput.getText().trim();
        if (queryText.isEmpty()) {
            statusLabel.setText("Please enter a query");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (!ensureService(project)) {
            statusLabel.setText("No project loaded. Load a JAR or class file first.");
            return;
        }
        queryService.setUserClassNames(project.getUserClassNames());

        executeQuery(queryText);
    }

    private void executeQuery(String queryText) {
        running = true;
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setIndeterminate(false);
        statusLabel.setText("Parsing query...");
        tableModel.clear();

        QueryService.QueryConfig config = QueryService.QueryConfig.builder()
            .timeBudgetMs((Integer) timeBudgetSpinner.getValue() * 1000L)
            .build();

        queryService.executeAsync(queryText, config, new QueryBatchRunner.ProgressListener() {
            @Override
            public void onPhaseStart(String phase, int total) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(phase + " (" + total + " items)...");
                    progressBar.setValue(0);
                    progressBar.setMaximum(total);
                });
            }

            @Override
            public void onProgress(int current, int total, String message) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(current);
                    statusLabel.setText(message);
                });
            }

            @Override
            public void onComplete(int matchCount) {
            }
        }).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            running = false;
            runButton.setEnabled(true);
            stopButton.setEnabled(false);
            progressBar.setVisible(false);

            if (result.hasError()) {
                statusLabel.setText("Error: " + result.error());
                statusLabel.setForeground(JStudioTheme.getError());
            } else {
                tableModel.setResults(result.results());
                int methodCount = result.resultCount();
                int siteCount = result.results().stream()
                    .mapToInt(r -> r.hasEvidence() ? r.getEvidence().size() : 0)
                    .sum();
                String statusText = siteCount > 0
                    ? "Found " + methodCount + " methods with " + siteCount + " call sites in " +
                      result.executionTimeMs() + "ms (click [+] to expand)"
                    : "Found " + methodCount + " matches in " + result.executionTimeMs() + "ms";
                statusLabel.setText(statusText);
                statusLabel.setForeground(JStudioTheme.getSuccess());
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                running = false;
                runButton.setEnabled(true);
                stopButton.setEnabled(false);
                progressBar.setVisible(false);
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setForeground(JStudioTheme.getError());
            });
            return null;
        });
    }

    private void stopQuery() {
        queryService.cancel();
        statusLabel.setText("Stopping...");
        statusLabel.setForeground(JStudioTheme.getWarning());
    }

    private void navigateToSelectedResult() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) return;

        QueryMatch result = tableModel.getResultAt(row);
        if (result == null) return;

        QueryTarget target = result.getTarget();
        String name = displayName(result);
        if (target == null) {
            statusLabel.setText("No navigation target for: " + name);
            statusLabel.setForeground(JStudioTheme.getWarning());
            return;
        }

        if (mainFrame != null) {
            boolean success = mainFrame.navigateToTarget(target);
            if (success) {
                statusLabel.setText("Navigated to: " + name);
                statusLabel.setForeground(JStudioTheme.getSuccess());
            } else {
                statusLabel.setText("Failed to navigate to: " + name);
                statusLabel.setForeground(JStudioTheme.getError());
            }
        } else {
            statusLabel.setText("Navigate to: " + name);
            statusLabel.setForeground(JStudioTheme.getInfo());
        }
    }

    /** The display name for a match: a method/PC signature or class name, derived from its target. */
    private static String displayName(QueryMatch match) {
        QueryTarget target = match.getTarget();
        if (target instanceof QueryTarget.MethodTarget) {
            return ((QueryTarget.MethodTarget) target).getSignature();
        }
        if (target instanceof QueryTarget.PCTarget) {
            return ((QueryTarget.PCTarget) target).getSignature();
        }
        if (target instanceof QueryTarget.ClassTarget) {
            return ((QueryTarget.ClassTarget) target).className();
        }
        Object cls = match.getAttribute("class");
        return cls != null ? cls.toString() : "(match)";
    }

    private void showHelp() {
        String bg = toHex(JStudioTheme.getBgSurface());
        String fg = toHex(JStudioTheme.getTextPrimary());
        String accent = toHex(JStudioTheme.getAccent());
        String muted = toHex(JStudioTheme.getTextSecondary());
        String codeBg = toHex(JStudioTheme.getBgTertiary());
        String ui = JStudioTheme.getUIFont(13).getFamily();
        String mono = JStudioTheme.getCodeFont(12).getFamily();

        String css =
            "body { background:" + bg + "; color:" + fg + "; font-family:'" + ui + "'; font-size:11pt; margin:12px; }" +
            "h2 { color:" + accent + "; font-size:15pt; margin:0 0 2px 0; }" +
            "h3 { color:" + accent + "; font-size:11pt; margin:16px 0 5px 0; }" +
            "p { margin:5px 0; }" +
            ".note { color:" + muted + "; font-size:10pt; }" +
            ".m { font-family:'" + mono + "'; }" +
            ".sub { color:" + accent + "; font-family:'" + mono + "'; }" +
            ".extitle { color:" + accent + "; font-size:10pt; margin:12px 0 1px 2px; }" +
            "td { padding:1px 16px 1px 0; vertical-align:top; }" +
            "pre { background:" + codeBg + "; font-family:'" + mono + "'; font-size:10pt; padding:8px; margin:3px 0; }";

        String html = "<html><head><style>" + css + "</style></head><body>" +
            "<h2>Query DSL</h2>" +
            "<p class='note'>Keywords are case-insensitive; quoted strings and type names match as written.</p>" +

            "<h3>Structure</h3>" +
            "<pre>FIND &lt;target&gt; [IN &lt;scope&gt;] [WHERE &lt;expr&gt;] [ORDER BY col [ASC|DESC]] [LIMIT n]</pre>" +
            "<p class='note'>ORDER BY sorts on a result column (<span class='m'>class method matches</span>), numeric-aware; LIMIT caps the rows.</p>" +
            "<p><b>Targets:</b> <span class='m'>methods</span>, <span class='m'>classes</span></p>" +

            "<h3>Scope</h3>" +
            "<table>" +
            "<tr><td class='m'>IN ALL</td><td>all loaded classes (default)</td></tr>" +
            "<tr><td class='m'>IN class \"pat\"</td><td>classes matching a string or /regex/</td></tr>" +
            "<tr><td class='m'>IN method \"pat\"</td><td>methods matching a string or /regex/</td></tr>" +
            "<tr><td class='m'>DURING &lt;clinit&gt;</td><td>static initializers (optionally + a class pattern)</td></tr>" +
            "</table>" +

            "<h3>WHERE expression</h3>" +
            "<table>" +
            "<tr><td><b>comparison</b></td><td class='m'>accessor OP operand</td></tr>" +
            "<tr><td><b>quantifier</b></td><td class='m'>HAS|ANY|ALL|NONE &lt;selector&gt; WHERE ( expr )</td></tr>" +
            "<tr><td></td><td class='m'>COUNT( &lt;selector&gt; [WHERE (expr)] ) OP n</td></tr>" +
            "<tr><td><b>boolean</b></td><td class='m'>expr AND expr&nbsp;&nbsp; expr OR expr&nbsp;&nbsp; NOT expr&nbsp;&nbsp; ( expr )</td></tr>" +
            "<tr><td><b>sequence</b></td><td class='m'>SEQUENCE [ step, step, .. ]&nbsp;&nbsp;<span class='note'>(see Instruction patterns)</span></td></tr>" +
            "</table>" +

            "<h3>Accessors</h3>" +
            "<table>" +
            "<tr><td class='sub'>method</td><td class='m'>name owner descriptor arity modifiers line opcodes</td></tr>" +
            "<tr><td class='sub'>class</td><td class='m'>name modifiers</td></tr>" +
            "<tr><td class='sub'>call</td><td class='m'>name owner descriptor arity kind opcode</td></tr>" +
            "<tr><td class='sub'>arg(n)</td><td class='m'>value type kind</td></tr>" +
            "<tr><td class='sub'>field</td><td class='m'>name owner descriptor kind</td></tr>" +
            "<tr><td class='sub'>insn</td><td class='m'>opcode index line</td></tr>" +
            "<tr><td class='sub'>SSA / CFG</td><td class='m'>recursive&nbsp;&nbsp; method.loops&nbsp;&nbsp; method.blocks&nbsp;&nbsp; (call|insn).inLoop&nbsp;&nbsp; (call|insn).loopDepth</td></tr>" +
            "</table>" +

            "<h3>Selectors</h3>" +
            "<p class='m'>call&nbsp;&nbsp; arg&nbsp;&nbsp; insn&nbsp;&nbsp; field <span class='note'>(quantify with HAS/ANY/ALL/NONE/COUNT)</span></p>" +

            "<h3>Operators</h3>" +
            "<p class='m'>==&nbsp; !=&nbsp; &lt;&nbsp; &lt;=&nbsp; &gt;&nbsp; &gt;=&nbsp;&nbsp; matches /re/&nbsp;&nbsp; contains \"s\"&nbsp;&nbsp; startsWith&nbsp;&nbsp; endsWith&nbsp;&nbsp; IN [a,b,c]</p>" +
            "<p><b>Data-flow:</b> <span class='m'>flowsTo&nbsp;&nbsp; flowsFrom</span> <span class='note'>(right side is an accessor)</span><br>" +
            "<span class='note'>endpoints:</span> <span class='m'>param(n)&nbsp;&nbsp; return&nbsp;&nbsp; arg(n)&nbsp;&nbsp; insn</span></p>" +

            "<h3>Operands</h3>" +
            "<table>" +
            "<tr><td><b>numbers</b></td><td class='m'>999&nbsp;&nbsp; 0xCAFE</td></tr>" +
            "<tr><td><b>strings</b></td><td class='m'>\"text\"</td></tr>" +
            "<tr><td><b>regex</b></td><td class='m'>/pattern/i</td></tr>" +
            "<tr><td><b>types</b></td><td class='m'>int&nbsp;&nbsp; java.lang.String</td></tr>" +
            "<tr><td><b>kinds</b></td><td class='m'>static virtual literal local field read write</td></tr>" +
            "</table>" +

            "<h3>Instruction patterns</h3>" +
            "<p><span class='m'>SEQUENCE [ … ]</span> (alias <span class='m'>SEQ</span>) matches an ordered run of "
            + "instructions appearing <i>anywhere</i> in a method. Steps are adjacent by default; use "
            + "<span class='m'>..</span> for a gap.</p>" +
            "<table>" +
            "<tr><td class='m'>new</td><td>an opcode mnemonic (case-insensitive)</td></tr>" +
            "<tr><td class='m'>_</td><td>any single instruction</td></tr>" +
            "<tr><td class='m'>..</td><td>a gap of any length (zero or more)</td></tr>" +
            "<tr><td class='m'>( … )</td><td>a full predicate on the instruction, e.g. (opcode matches /^invoke/)</td></tr>" +
            "</table>" +
            "<p><span class='note'>repetition (suffix any step):</span> <span class='m'>*</span> 0+&nbsp;&nbsp; "
            + "<span class='m'>+</span> 1+&nbsp;&nbsp; <span class='m'>{n}</span> exactly n&nbsp;&nbsp; "
            + "<span class='m'>{n,m}</span> n..m&nbsp;&nbsp; <span class='m'>{n,}</span> n+</p>" +
            "<p><b>opcodes shorthand:</b> <span class='m'>opcodes</span> is the method's space-joined mnemonics, "
            + "so a quick opcode-only shape is just a regex: <span class='m'>opcodes matches /new dup .* invokespecial/</span></p>" +

            "<h3>Examples</h3>" +
            buildExampleBoxes() +
            "</body></html>";

        JEditorPane editor = new JEditorPane("text/html", html);
        editor.setEditable(false);
        editor.setBackground(JStudioTheme.getBgSurface());
        editor.setBorder(BorderFactory.createEmptyBorder());
        editor.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editor);
        scrollPane.setPreferredSize(new Dimension(660, 560));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        JOptionPane.showMessageDialog(this, scrollPane, "Query DSL Help",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String buildExampleBoxes() {
        String[][] examples = {
            {"Find callers of println", "FIND methods WHERE HAS call WHERE (name == \"println\")"},
            {"One int argument equal to 999", "FIND methods WHERE HAS call WHERE (COUNT(arg) == 1 AND arg(0).value == 999)"},
            {"Public getters", "FIND methods WHERE method.name matches /^get/ AND method.modifiers contains public"},
            {"Test classes", "FIND classes WHERE class.name endsWith \"Test\""},
            {"Crypto calls", "FIND methods WHERE HAS call WHERE (owner matches /Cipher/ AND name == \"doFinal\")"},
            {"Large methods in a package", "FIND methods IN class \"com/example/.*\" WHERE COUNT(insn) > 100"},
            {"Recursive, calls inside a loop", "FIND methods WHERE recursive AND HAS call WHERE (inLoop)"},
            {"Returns its first parameter", "FIND methods WHERE param(0) flowsTo return"},
            {"Forwards a parameter to a call", "FIND methods WHERE HAS call WHERE (arg(0) flowsFrom param(0))"},
            {"Allocation pattern (new .. invokespecial)", "FIND methods WHERE SEQUENCE [ new, dup, .., invokespecial ]"},
            {"Opcode shape via regex", "FIND methods WHERE opcodes matches /new dup .* invokespecial/"},
            {"Biggest methods first", "FIND methods WHERE COUNT(insn) > 50 ORDER BY matches DESC LIMIT 20"},
        };
        StringBuilder sb = new StringBuilder();
        for (String[] example : examples) {
            sb.append("<p class='extitle'>").append(example[0]).append("</p>")
              .append("<pre>").append(highlightQuery(example[1])).append("</pre>");
        }
        return sb.toString();
    }

    /** Tokenizes a query with {@link QueryTokenMaker} and renders it as color-spanned HTML. */
    private static String highlightQuery(String query) {
        Segment segment = new Segment(query.toCharArray(), 0, query.length());
        Token token = new QueryTokenMaker().getTokenList(segment, Token.NULL, 0);
        StringBuilder sb = new StringBuilder();
        while (token != null && token.isPaintable()) {
            String lexeme = token.getLexeme();
            if (lexeme != null) {
                sb.append("<span style='color:").append(toHex(colorFor(token.getType()))).append("'>")
                  .append(escapeHtml(lexeme)).append("</span>");
            }
            token = token.getNextToken();
        }
        return sb.toString();
    }

    private static Color colorFor(int tokenType) {
        switch (tokenType) {
            case Token.RESERVED_WORD:
                return SyntaxColors.getJavaKeyword();
            case Token.RESERVED_WORD_2:
                return SyntaxColors.getJavaMethod();
            case Token.FUNCTION:
            case Token.OPERATOR:
                return SyntaxColors.getJavaOperator();
            case Token.DATA_TYPE:
                return SyntaxColors.getJavaType();
            case Token.SEPARATOR:
                return JStudioTheme.getTextSecondary();
            case Token.LITERAL_STRING_DOUBLE_QUOTE:
            case Token.REGEX:
                return SyntaxColors.getJavaString();
            case Token.LITERAL_NUMBER_DECIMAL_INT:
                return SyntaxColors.getJavaNumber();
            case Token.LITERAL_BOOLEAN:
                return SyntaxColors.getJavaConstant();
            default:
                return JStudioTheme.getTextPrimary();
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Releases the background query executor; call when the host window is disposed. */
    public void shutdown() {
        if (queryService != null) {
            queryService.shutdown();
        }
    }

    private static class TreeResultTableModel extends AbstractTableModel {
        private List<QueryMatch> rootResults = new ArrayList<>();
        private final List<FlatRow> flatRows = new ArrayList<>();
        private final Set<QueryMatch> expandedRows = new HashSet<>();

        public void setResults(List<QueryMatch> results) {
            this.rootResults = results != null ? new ArrayList<>(results) : new ArrayList<>();
            this.expandedRows.clear();
            rebuildFlatList();
        }

        public void clear() {
            rootResults.clear();
            expandedRows.clear();
            flatRows.clear();
            fireTableDataChanged();
        }

        private void rebuildFlatList() {
            flatRows.clear();
            for (QueryMatch root : rootResults) {
                flatRows.add(new FlatRow(root, false));
                if (expandedRows.contains(root) && root.hasEvidence()) {
                    for (QueryMatch child : root.getEvidence()) {
                        flatRows.add(new FlatRow(child, true));
                    }
                }
            }
            fireTableDataChanged();
        }

        public void toggleExpand(int row) {
            if (row < 0 || row >= flatRows.size()) return;
            FlatRow flat = flatRows.get(row);
            if (flat.child || !flat.match.hasEvidence()) return;

            if (expandedRows.contains(flat.match)) {
                expandedRows.remove(flat.match);
            } else {
                expandedRows.add(flat.match);
            }
            rebuildFlatList();
        }

        public boolean isExpanded(QueryMatch match) {
            return expandedRows.contains(match);
        }

        public QueryMatch getResultAt(int row) {
            if (row >= 0 && row < flatRows.size()) {
                return flatRows.get(row).match;
            }
            return null;
        }

        public boolean isChildRow(int row) {
            return row >= 0 && row < flatRows.size() && flatRows.get(row).child;
        }

        @Override
        public int getRowCount() {
            return flatRows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0: return "Match";
                case 1: return "Details";
                case 2: return "Sites";
                default: return "";
            }
        }

        @Override
        public Object getValueAt(int row, int column) {
            FlatRow flat = flatRows.get(row);
            QueryMatch result = flat.match;
            switch (column) {
                case 0:
                    String prefix = "";
                    if (flat.child) {
                        prefix = "    ";
                    } else if (result.hasEvidence()) {
                        prefix = isExpanded(result) ? "[-] " : "[+] ";
                    }
                    return prefix + rowLabel(result, flat.child);
                case 1:
                    return formatAttributes(result.getAttributes());
                case 2:
                    if (flat.child) {
                        QueryTarget t = result.getTarget();
                        return t instanceof QueryTarget.PCTarget
                                ? "pc=" + ((QueryTarget.PCTarget) t).pc() : "";
                    }
                    return result.hasEvidence() ? result.getEvidence().size() + " sites" : "";
                default:
                    return "";
            }
        }

        /** Builds the display label UI-side (the engine no longer carries a presentation label). */
        private static String rowLabel(QueryMatch match, boolean child) {
            if (child) {
                Object detail = match.getAttribute("detail");
                return detail != null ? detail.toString() : displayName(match);
            }
            String base = displayName(match);
            if (match.hasEvidence()) {
                int n = match.getEvidence().size();
                return base + " (" + n + " match" + (n != 1 ? "es" : "") + ")";
            }
            return base;
        }

        private String formatAttributes(Map<String, Object> attributes) {
            if (attributes == null || attributes.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String key = entry.getKey();
                if (key.equals("class") || key.equals("method") || key.equals("matches")
                        || key.equals("pc") || key.equals("detail")) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(key).append("=").append(entry.getValue());
            }
            return sb.toString();
        }

        private static final class FlatRow {
            final QueryMatch match;
            final boolean child;

            FlatRow(QueryMatch match, boolean child) {
                this.match = match;
                this.child = child;
            }
        }
    }
}
