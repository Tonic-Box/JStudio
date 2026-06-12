package com.tonic.ui.vm.debugger;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.MethodEntry;
import com.tonic.service.ProjectService;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.SyntaxColors;
import com.tonic.ui.theme.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Read-only decompiled-source companion to the debugger's bytecode table, showing only the
 * displayed method (sliced from the class source using the decompiler's exact per-method text
 * spans, with gutter numbering kept in whole-document coordinates). Tracks the executing statement
 * during stepping by resolving the current PC through the per-method bytecode-offset-to-line maps,
 * and supports toggling breakpoints from source lines by inverting the same map. Decompiled text,
 * line maps and spans are shared with the editor through the {@link ClassEntryModel} cache.
 */
public class DebuggerSourceView extends JPanel {

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final JLabel statusLabel;
    private final Icon breakpointIcon = new BreakpointDotIcon();

    private IntConsumer breakpointToggler;

    private String loadedClassName;
    private String loadedMethodKey;
    private Map<String, NavigableMap<Integer, Integer>> lineMaps;
    private int lineOffset;
    private Object executionHighlight;
    private int lastExecutionLine = -1;
    private final List<GutterIconInfo> breakpointIcons = new ArrayList<>();
    private SwingWorker<DecompileResult, Void> decompileWorker;
    private Runnable pendingUpdate;

    public DebuggerSourceView() {
        super(new BorderLayout());

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setEditable(false);
        textArea.setHighlightCurrentLine(false);
        textArea.setFont(JStudioTheme.getCodeFont(13));

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setIconRowHeaderEnabled(true);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        statusLabel.setFont(JStudioTheme.getUIFont(11));
        add(statusLabel, BorderLayout.SOUTH);

        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem toggleItem = new JMenuItem("Toggle Breakpoint");
        toggleItem.addActionListener(e -> toggleBreakpointAtDisplayLine(textArea.getCaretLineNumber() + 1));
        contextMenu.add(toggleItem);
        textArea.setComponentPopupMenu(contextMenu);

        scrollPane.getGutter().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int offset = textArea.viewToModel2D(new Point(0, e.getY()));
                if (offset >= 0) {
                    try {
                        toggleBreakpointAtDisplayLine(textArea.getLineOfOffset(offset) + 1);
                    } catch (BadLocationException ignored) {
                    }
                }
            }
        });

        applyTheme();
        ThemeManager.getInstance().addThemeChangeListener(t -> SwingUtilities.invokeLater(this::applyTheme));
    }

    public void setBreakpointToggler(IntConsumer breakpointToggler) {
        this.breakpointToggler = breakpointToggler;
    }

    /**
     * Shows the method's source without an execution highlight (initial method load).
     */
    public void showMethod(MethodEntry method, Set<Integer> breakpointPcs) {
        if (method == null) {
            return;
        }
        Set<Integer> pcs = new HashSet<>(breakpointPcs);
        ensureMethodLoaded(method, () -> {
            clearExecutionHighlight();
            refreshBreakpoints(pcs);
            scrollToMethodStart();
        });
    }

    /**
     * Highlights the statement executing at the given PC, re-slicing to the new method first when
     * stepping crossed a method boundary.
     */
    public void showExecutionPoint(MethodEntry method, int pc, Set<Integer> breakpointPcs) {
        if (method == null) {
            return;
        }
        Set<Integer> pcs = new HashSet<>(breakpointPcs);
        ensureMethodLoaded(method, () -> {
            refreshBreakpoints(pcs);
            highlightExecution(pc);
        });
    }

    public void clearExecutionHighlight() {
        if (executionHighlight != null) {
            textArea.removeLineHighlight(executionHighlight);
            executionHighlight = null;
        }
        lastExecutionLine = -1;
    }

    /**
     * Re-renders the breakpoint gutter dots from the displayed method's breakpoint PCs.
     */
    public void refreshBreakpoints(Set<Integer> breakpointPcs) {
        for (GutterIconInfo info : breakpointIcons) {
            scrollPane.getGutter().removeTrackingIcon(info);
        }
        breakpointIcons.clear();

        NavigableMap<Integer, Integer> map = methodMap();
        if (map == null) {
            return;
        }
        Set<Integer> displayLines = new HashSet<>();
        for (int pc : breakpointPcs) {
            Map.Entry<Integer, Integer> entry = map.floorEntry(pc);
            if (entry == null) {
                entry = map.ceilingEntry(pc);
            }
            if (entry != null) {
                displayLines.add(entry.getValue() - lineOffset);
            }
        }
        for (int line : displayLines) {
            try {
                breakpointIcons.add(scrollPane.getGutter().addLineTrackingIcon(line - 1, breakpointIcon));
            } catch (BadLocationException ignored) {
            }
        }
    }

    /**
     * Inverts the line map (display line back to the statement's start offset) and toggles a
     * breakpoint there. Only lines carrying a mapped statement are valid targets.
     */
    private void toggleBreakpointAtDisplayLine(int oneBasedDisplayLine) {
        if (breakpointToggler == null) {
            return;
        }
        NavigableMap<Integer, Integer> map = methodMap();
        if (map == null) {
            status("No line mapping available for the displayed method");
            return;
        }
        int documentLine = oneBasedDisplayLine + lineOffset;
        int pc = -1;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue() == documentLine && (pc < 0 || entry.getKey() < pc)) {
                pc = entry.getKey();
            }
        }
        if (pc < 0) {
            status("No statement maps to line " + (oneBasedDisplayLine + lineOffset));
            return;
        }
        breakpointToggler.accept(pc);
    }

    private void highlightExecution(int pc) {
        NavigableMap<Integer, Integer> map = methodMap();
        if (map == null || map.isEmpty()) {
            status("No line mapping for the executing method");
            return;
        }
        Map.Entry<Integer, Integer> entry = map.floorEntry(pc);
        if (entry == null) {
            entry = map.ceilingEntry(pc);
        }
        if (entry == null) {
            return;
        }
        int displayLine = entry.getValue() - lineOffset;
        if (displayLine == lastExecutionLine && executionHighlight != null) {
            return;
        }
        clearExecutionHighlight();
        try {
            Color accent = JStudioTheme.getAccent();
            Color execColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 55);
            executionHighlight = textArea.addLineHighlight(displayLine - 1, execColor);
            lastExecutionLine = displayLine;
            scrollToLine(displayLine - 1);
            status(" ");
        } catch (BadLocationException ignored) {
        }
    }

    private void scrollToMethodStart() {
        NavigableMap<Integer, Integer> map = methodMap();
        if (map != null && !map.isEmpty()) {
            scrollToLine(map.firstEntry().getValue() - lineOffset - 1);
        }
    }

    private void scrollToLine(int zeroBasedLine) {
        try {
            int offset = textArea.getLineStartOffset(Math.max(0, zeroBasedLine));
            textArea.setCaretPosition(offset);
            Rectangle rect = textArea.modelToView2D(offset).getBounds();
            rect.height = Math.max(rect.height, textArea.getVisibleRect().height / 3);
            textArea.scrollRectToVisible(rect);
        } catch (BadLocationException ignored) {
        }
    }

    private NavigableMap<Integer, Integer> methodMap() {
        return lineMaps != null && loadedMethodKey != null ? lineMaps.get(loadedMethodKey) : null;
    }

    /**
     * Ensures the method's sliced source (and line maps) are displayed, decompiling the owner class
     * asynchronously on first need; the result is shared with the editor via the model's
     * decompilation cache. {@code onReady} runs once text and maps are in place.
     */
    private void ensureMethodLoaded(MethodEntry method, Runnable onReady) {
        String ownerName = method.getOwnerName();
        String methodKey = method.getName() + method.getDesc();
        if (ownerName.equals(loadedClassName) && methodKey.equals(loadedMethodKey) && lineMaps != null) {
            onReady.run();
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        ClassEntryModel classEntry = project != null ? project.getClass(ownerName) : null;
        if (classEntry == null && project != null) {
            classEntry = project.findClassByName(ownerName);
        }
        if (classEntry == null) {
            loadedClassName = ownerName;
            loadedMethodKey = methodKey;
            lineMaps = null;
            lineOffset = 0;
            clearExecutionHighlight();
            textArea.setText("// Source not available for " + ownerName + "\n"
                    + "// (library class or not part of the loaded project)\n");
            status("Source not available for " + simpleName(ownerName));
            return;
        }

        if (classEntry.getDecompilationCache() != null && classEntry.getSourceLineMaps() != null) {
            applySource(ownerName, methodKey, classEntry.getDecompilationCache(),
                    classEntry.getSourceLineMaps(), classEntry.getMethodSpans());
            onReady.run();
            return;
        }

        pendingUpdate = onReady;
        if (decompileWorker != null && !decompileWorker.isDone()) {
            decompileWorker.cancel(true);
        }
        clearExecutionHighlight();
        textArea.setText("// Decompiling " + simpleName(ownerName) + " ...\n");
        status("Decompiling " + simpleName(ownerName) + " ...");

        final ClassEntryModel target = classEntry;
        decompileWorker = new SwingWorker<>() {
            @Override
            protected DecompileResult doInBackground() {
                return new ClassDecompiler(target.getClassFile()).decompileWithLineMap();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    DecompileResult result = get();
                    target.setDecompilationCache(result.getSource(), result.getLineMaps(),
                            result.getMethodSpans());
                    applySource(ownerName, methodKey, result.getSource(), result.getLineMaps(),
                            result.getMethodSpans());
                    status(" ");
                    Runnable update = pendingUpdate;
                    pendingUpdate = null;
                    if (update != null) {
                        update.run();
                    }
                } catch (Exception e) {
                    pendingUpdate = null;
                    lineMaps = null;
                    textArea.setText("// Decompilation failed: " + e.getMessage() + "\n");
                    status("Decompilation failed");
                }
            }
        };
        decompileWorker.execute();
    }

    /**
     * Displays the method's slice of the class source (whole class when no span is available) and
     * records the line offset that rebases between document and display coordinates. The gutter
     * keeps whole-document numbering so lines match the editor's source view.
     */
    private void applySource(String ownerName, String methodKey, String source,
                             Map<String, NavigableMap<Integer, Integer>> maps,
                             Map<String, DecompileResult.MethodSpan> spans) {
        clearExecutionHighlight();
        textArea.removeAllLineHighlights();
        scrollPane.getGutter().removeAllTrackingIcons();
        breakpointIcons.clear();

        DecompileResult.MethodSpan span = spans != null ? spans.get(methodKey) : null;
        if (span != null) {
            String[] lines = source.split("\n", -1);
            int start = Math.max(1, span.getStartLine());
            int end = Math.min(lines.length, span.getEndLine());
            StringBuilder slice = new StringBuilder();
            for (int i = start; i <= end; i++) {
                slice.append(lines[i - 1]);
                if (i < end) {
                    slice.append('\n');
                }
            }
            textArea.setText(slice.toString());
            lineOffset = start - 1;
            scrollPane.getGutter().setLineNumberingStartIndex(start);
        } else {
            textArea.setText(source);
            lineOffset = 0;
            scrollPane.getGutter().setLineNumberingStartIndex(1);
        }
        textArea.setCaretPosition(0);
        loadedClassName = ownerName;
        loadedMethodKey = methodKey;
        lineMaps = maps;
    }

    private void status(String message) {
        statusLabel.setText(message);
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        textArea.setBackground(JStudioTheme.getBgTertiary());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setSelectionColor(JStudioTheme.getSelection());
        textArea.setFont(JStudioTheme.getCodeFont(13));

        scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBackground(JStudioTheme.getBgSecondary());

        SyntaxScheme scheme = textArea.getSyntaxScheme();
        setTokenStyle(scheme, Token.RESERVED_WORD, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.RESERVED_WORD_2, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.DATA_TYPE, SyntaxColors.getJavaType());
        setTokenStyle(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_CHAR, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_FLOAT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_HEXADECIMAL, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.COMMENT_EOL, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_MULTILINE, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_DOCUMENTATION, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.FUNCTION, SyntaxColors.getJavaMethod());
        setTokenStyle(scheme, Token.OPERATOR, SyntaxColors.getJavaOperator());
        setTokenStyle(scheme, Token.ANNOTATION, SyntaxColors.getJavaAnnotation());
        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.LITERAL_BOOLEAN, SyntaxColors.getJavaConstant());
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextPrimary());
        repaint();
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }

    private static final class BreakpointDotIcon implements Icon {
        private static final int SIZE = 10;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xE0, 0x45, 0x45));
            g2.fillOval(x, y, SIZE, SIZE);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
