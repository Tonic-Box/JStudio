package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.editor.SearchPanel;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.FindUsagesEvent;
import com.tonic.ui.MainFrame;
import com.tonic.ui.model.Bookmark;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.Comment;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectDatabaseService;
import com.tonic.ui.theme.*;

import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;

/**
 * Source code view using RSyntaxTextArea for Java syntax highlighting.
 */
public class SourceCodeView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    /**
     * -- GETTER --
     *  Get the text area for direct access (e.g., for Ctrl+Click).
     */
    @Getter
    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final SearchPanel searchPanel;
    private ProjectModel projectModel;

    private boolean loaded = false;
    private boolean omitAnnotations = false;
    private final List<GutterIconInfo> commentIcons = new ArrayList<>();
    private Object currentLineHighlight;
    private final LoadingOverlay loadingOverlay;
    private SwingWorker<String, Void> currentWorker;

    private final FloatingCompileToolbar compileToolbar;
    private final SourceCompilerParser compilerParser;
    private String originalSource;
    @Getter
    private boolean dirty = false;
    private boolean editableMode = true;
    private boolean ignoreDocumentChanges = false;

    public SourceCodeView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setAntiAliasingEnabled(true);
        textArea.setEditable(true);
        textArea.setFont(JStudioTheme.getCodeFont(13));

        compilerParser = new SourceCompilerParser();
        compilerParser.setOriginalClass(classEntry.getClassFile());
        textArea.addParser(compilerParser);

        ErrorStrip errorStrip = new ErrorStrip(textArea);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setIconRowHeaderEnabled(true);
        scrollPane.setBorder(null);

        loadingOverlay = new LoadingOverlay();

        compileToolbar = new FloatingCompileToolbar(this::doRecompile, this::discardChanges);
        compileToolbar.setLineNavigator(line -> {
            if (line > 0) {
                goToLine(line);
                highlightLine(line - 1);
            }
        });

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(scrollPane, BorderLayout.CENTER);
        editorPanel.add(errorStrip, BorderLayout.LINE_END);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new OverlayLayout(contentPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        editorPanel.setAlignmentX(0.5f);
        editorPanel.setAlignmentY(0.5f);
        contentPanel.add(loadingOverlay);
        contentPanel.add(editorPanel);

        add(compileToolbar, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        searchPanel = new SearchPanel(textArea);
        add(searchPanel, BorderLayout.SOUTH);

        setupDocumentListener();

        // Apply theme (must be after scrollPane is created)
        applyTheme();

        // Setup Ctrl+Click for Go to Definition
        setupGoToDefinition();

        // Setup right-click context menu
        setupContextMenu();

        // Listen for comment changes to update gutter icons
        ProjectDatabaseService.getInstance().addListener((db, dirty) -> SwingUtilities.invokeLater(this::updateCommentGutterIcons));

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void setupDocumentListener() {
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSourceChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSourceChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSourceChanged();
            }
        });
    }

    private void onSourceChanged() {
        if (ignoreDocumentChanges || !editableMode) {
            return;
        }

        String currentText = textArea.getText();
        boolean nowDirty = originalSource != null && !currentText.equals(originalSource);

        if (nowDirty != dirty) {
            dirty = nowDirty;
            if (dirty) {
                compilerParser.setEnabled(true);
                compileToolbar.showModified();
            } else {
                compilerParser.setEnabled(false);
                compileToolbar.hideToolbar();
            }
        }

        if (dirty) {
            SwingUtilities.invokeLater(this::updateToolbarState);
        }
    }

    private void updateToolbarState() {
        int errorCount = compilerParser.getErrorCount();
        int warningCount = compilerParser.getWarningCount();
        compileToolbar.showWithErrors(errorCount, warningCount);
    }

    private void doRecompile() {
        if (!dirty) {
            return;
        }

        String source = textArea.getText();
        compileToolbar.showCompiling();

        SwingWorker<CompilationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CompilationResult doInBackground() {
                return compilerParser.compile(source, projectModel != null ? projectModel.getClassPool() : null);
            }

            @Override
            protected void done() {
                try {
                    CompilationResult result = get();
                    if (result.isSuccess()) {
                        classEntry.updateClassFile(result.getCompiledClass());
                        classEntry.setDecompilationCache(source);
                        compilerParser.setOriginalClass(result.getCompiledClass());
                        originalSource = source;
                        dirty = false;
                        compileToolbar.showSuccess(result.getCompilationTimeMs());

                        javax.swing.Timer hideTimer = new javax.swing.Timer(1500, evt -> {
                            if (!SourceCodeView.this.dirty) {
                                compileToolbar.hideToolbar();
                            }
                        });
                        hideTimer.setRepeats(false);
                        hideTimer.start();
                    } else {
                        compileToolbar.showWithErrors(result.getErrorCount(), result.getWarningCount(), result.getErrors());
                    }
                } catch (Exception e) {
                    compileToolbar.showWithErrors(1, 0, java.util.Collections.singletonList(
                            CompilationError.error(1, 1, 0, 1, "Compilation failed: " + e.getMessage())
                    ));
                }
            }
        };

        worker.execute();
    }

    private void discardChanges() {
        if (originalSource != null) {
            ignoreDocumentChanges = true;
            textArea.setText(originalSource);
            textArea.setCaretPosition(0);
            ignoreDocumentChanges = false;
            dirty = false;
            compileToolbar.hideToolbar();
        }
    }

    public void setEditableMode(boolean editable) {
        this.editableMode = editable;
        textArea.setEditable(editable);
        compilerParser.setEnabled(editable);
        if (!editable) {
            compileToolbar.hideToolbar();
        }
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    /**
     * Setup Ctrl+Click navigation to definitions.
     */
    private void setupGoToDefinition() {
        // Change cursor when Ctrl is pressed
        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    textArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                }
            }
        });

        // Handle Ctrl+Click
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    navigateToDefinition(e);
                }
            }
        });
    }

    private void setupContextMenu() {
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
        });
    }

    private void showContextMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        int clickOffset = textArea.viewToModel2D(e.getPoint());
        int lineNumber = 1;
        try {
            lineNumber = textArea.getLineOfOffset(clickOffset) + 1;
        } catch (BadLocationException ex) {
            // Use default line 1
        }
        final int line = lineNumber;

        // Copy
        JMenuItem copyItem = createMenuItem("Copy", Icons.getIcon("copy"));
        copyItem.addActionListener(ev -> copySelection());
        copyItem.setEnabled(textArea.getSelectedText() != null && !textArea.getSelectedText().isEmpty());
        menu.add(copyItem);

        menu.addSeparator();

        // Go to Definition
        JMenuItem gotoItem = createMenuItem("Go to Definition", null);
        String selectedText = textArea.getSelectedText();
        String wordAtCaret = getWordAtCaret();
        String targetIdentifier = (selectedText != null && !selectedText.isEmpty()) ? selectedText : wordAtCaret;
        gotoItem.addActionListener(ev -> {
            if (targetIdentifier != null && !targetIdentifier.isEmpty()) {
                navigateToIdentifier(targetIdentifier);
            }
        });
        gotoItem.setEnabled(targetIdentifier != null && !targetIdentifier.isEmpty());
        menu.add(gotoItem);

        // Rename and Find Usages (only for declarations on the current line)
        DeclarationInfo decl = getDeclarationAtLine(line);
        if (decl != null) {
            JMenuItem renameItem = createMenuItem("Rename " + decl.type.displayName + " '" + decl.name + "'...", null);
            renameItem.addActionListener(ev -> showRenameDialog(decl));
            menu.add(renameItem);

            JMenuItem findUsagesItem = createMenuItem("Find Usages of " + decl.type.displayName + " '" + decl.name + "'", Icons.getIcon("search"));
            findUsagesItem.addActionListener(ev -> findUsagesOfDeclaration(decl));
            menu.add(findUsagesItem);
        }

        menu.addSeparator();

        // Add Comment at Line
        JMenuItem commentItem = createMenuItem("Add Comment at Line " + line + "...", Icons.getIcon("comment"));
        commentItem.addActionListener(ev -> addCommentAtLine(line));
        menu.add(commentItem);

        // View Comments at Line
        int commentsAtLine = countCommentsAtLine(line);
        if (commentsAtLine > 0) {
            JMenuItem viewCommentItem = createMenuItem("View Comments at Line " + line + " (" + commentsAtLine + ")", null);
            viewCommentItem.addActionListener(ev -> viewCommentsAtLine(line));
            menu.add(viewCommentItem);
        }

        menu.addSeparator();

        // Run Simulation Analysis
        JMenuItem simulationItem = createMenuItem("Run Simulation Analysis", Icons.getIcon("analyze"));
        simulationItem.addActionListener(ev -> runSimulationAnalysis());
        menu.add(simulationItem);

        menu.addSeparator();

        // Add Bookmark
        JMenuItem bookmarkItem = createMenuItem("Add Bookmark for This Class...", Icons.getIcon("bookmark"));
        bookmarkItem.addActionListener(ev -> addBookmark());
        menu.add(bookmarkItem);

        menu.show(textArea, e.getX(), e.getY());
    }

    private JMenuItem createMenuItem(String text, javax.swing.Icon icon) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        if (icon != null) {
            item.setIcon(icon);
        }
        return item;
    }

    private void addCommentAtLine(int lineNumber) {
        JTextArea commentArea = new JTextArea(5, 40);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        JScrollPane commentScroll = new JScrollPane(commentArea);

        int result = JOptionPane.showConfirmDialog(
                this,
                commentScroll,
                "Add Comment at Line " + lineNumber + " in " + classEntry.getSimpleName(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !commentArea.getText().trim().isEmpty()) {
            Comment comment = new Comment(classEntry.getClassName(), lineNumber, commentArea.getText().trim());
            comment.setType(Comment.Type.LINE);
            ProjectDatabaseService.getInstance().addComment(comment);
        }
    }

    private int countCommentsAtLine(int lineNumber) {
        if (!ProjectDatabaseService.getInstance().hasDatabase()) {
            return 0;
        }
        List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());
        int count = 0;
        for (Comment c : comments) {
            if (c.getLineNumber() == lineNumber) {
                count++;
            }
        }
        return count;
    }

    private void viewCommentsAtLine(int lineNumber) {
        if (!ProjectDatabaseService.getInstance().hasDatabase()) {
            return;
        }
        List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());
        StringBuilder sb = new StringBuilder();
        for (Comment c : comments) {
            if (c.getLineNumber() == lineNumber) {
                if (sb.length() > 0) {
                    sb.append("\n---\n");
                }
                sb.append(c.getText());
            }
        }
        if (sb.length() > 0) {
            JTextArea viewArea = new JTextArea(sb.toString());
            viewArea.setEditable(false);
            viewArea.setLineWrap(true);
            viewArea.setWrapStyleWord(true);
            viewArea.setRows(Math.min(10, sb.toString().split("\n").length + 2));
            viewArea.setColumns(50);
            JScrollPane viewScroll = new JScrollPane(viewArea);
            JOptionPane.showMessageDialog(this, viewScroll,
                    "Comments at Line " + lineNumber, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void addBookmark() {
        String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            Bookmark bookmark = new Bookmark(classEntry.getClassName(), name.trim());
            ProjectDatabaseService.getInstance().addBookmark(bookmark);
        }
    }

    private void runSimulationAnalysis() {
        java.awt.Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            ((MainFrame) parent).runSimulationAnalysis();
        }
    }

    private void updateCommentGutterIcons() {
        Gutter gutter = scrollPane.getGutter();

        // Remove existing comment icons
        for (GutterIconInfo iconInfo : commentIcons) {
            gutter.removeTrackingIcon(iconInfo);
        }
        commentIcons.clear();

        // Get comments for this class
        if (!ProjectDatabaseService.getInstance().hasDatabase()) {
            return;
        }

        List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());

        if (comments.isEmpty()) {
            return;
        }

        // Group comments by line number
        Map<Integer, List<Comment>> commentsByLine = new HashMap<>();
        for (Comment c : comments) {
            int line = c.getLineNumber();
            if (line > 0) {
                commentsByLine.computeIfAbsent(line, k -> new ArrayList<>()).add(c);
            }
        }

        // Add gutter icons for each line with comments
        javax.swing.Icon commentIcon = Icons.getIcon("comment");
        for (Map.Entry<Integer, List<Comment>> entry : commentsByLine.entrySet()) {
            int lineNumber = entry.getKey();
            List<Comment> lineComments = entry.getValue();

            // Build tooltip
            StringBuilder tooltip = new StringBuilder("<html>");
            for (int i = 0; i < lineComments.size(); i++) {
                if (i > 0) {
                    tooltip.append("<hr>");
                }
                String text = lineComments.get(i).getText();
                if (text.length() > 100) {
                    text = text.substring(0, 97) + "...";
                }
                tooltip.append(escapeHtml(text).replace("\n", "<br>"));
            }
            tooltip.append("</html>");

            try {
                GutterIconInfo iconInfo = gutter.addLineTrackingIcon(lineNumber - 1, commentIcon, tooltip.toString());
                commentIcons.add(iconInfo);
            } catch (BadLocationException e) {
                // Line doesn't exist, skip
            }
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Navigate to the definition of the identifier under the cursor.
     */
    private void navigateToDefinition(MouseEvent e) {
        if (projectModel == null) return;

        try {
            int offset = textArea.viewToModel2D(e.getPoint());
            if (offset < 0) return;

            String text = textArea.getText();
            String identifier = extractIdentifierAt(text, offset);

            if (identifier != null && !identifier.isEmpty()) {
                navigateToIdentifier(identifier);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Navigate to the definition of a given identifier.
     * Searches: current class methods -> current class fields -> project classes -> project methods.
     */
    private void navigateToIdentifier(String identifier) {
        if (projectModel == null || identifier == null || identifier.isEmpty()) return;

        try {
            // 1. Check current class methods
            ClassFile currentClassFile = classEntry.getClassFile();
            for (MethodEntry method : currentClassFile.getMethods()) {
                if (method.getName().equals(identifier)) {
                    scrollToMethodDefinition(method.getName(), method.getDesc());
                    return;
                }
            }

            // 2. Check current class fields
            for (FieldEntry field : currentClassFile.getFields()) {
                if (field.getName().equals(identifier)) {
                    scrollToFieldDefinition(field.getName());
                    return;
                }
            }

            // 3. Search project classes by simple name
            ClassEntryModel targetClass = findClassBySimpleName(identifier);
            if (targetClass != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(this, targetClass));
                return;
            }

            // 4. Search project classes by fully qualified name
            targetClass = projectModel.getClass(identifier.replace('.', '/'));
            if (targetClass != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(this, targetClass));
                return;
            }

            // 5. Search all project classes for a method with this name
            for (ClassEntryModel cls : projectModel.getAllClasses()) {
                for (MethodEntry method : cls.getClassFile().getMethods()) {
                    if (method.getName().equals(identifier)) {
                        EventBus.getInstance().post(new ClassSelectedEvent(this, cls));
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Scroll to a method definition in the source view and highlight the line.
     * Looks for actual method declarations, not call sites.
     * Uses descriptor to match correct overload.
     */
    private void scrollToMethodDefinition(String methodName, String methodDesc) {
        String text = textArea.getText();
        String[] lines = text.split("\n");

        String methodWithParen = methodName + "(";
        String dotMethod = "." + methodName;
        String thisMethod = "this." + methodName;

        String quotedName = java.util.regex.Pattern.quote(methodName);
        java.util.regex.Pattern declarationPattern = java.util.regex.Pattern.compile(
            "^\\s*(public|private|protected|static|final|abstract|synchronized|native|strictfp|\\s)+.*\\s+" +
            quotedName + "\\s*\\("
        );
        java.util.regex.Pattern simplePattern = java.util.regex.Pattern.compile(
            "^\\s+\\w+.*\\s+" + quotedName + "\\s*\\("
        );

        List<Integer> matchingLines = new ArrayList<>();
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            if (!line.contains(methodWithParen)) {
                continue;
            }

            if (line.contains(dotMethod) || line.contains(thisMethod)) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.startsWith("return") || trimmed.startsWith("if") || trimmed.startsWith("while")) {
                continue;
            }

            boolean isDeclaration = declarationPattern.matcher(line).find() ||
                                   simplePattern.matcher(line).find();

            if (isDeclaration) {
                matchingLines.add(lineNum);
            }
        }

        if (matchingLines.isEmpty()) {
            return;
        }

        if (matchingLines.size() == 1 || methodDesc == null) {
            highlightAndScrollToLine(matchingLines.get(0));
            return;
        }

        int descParamCount = countDescriptorParams(methodDesc);
        String descReturnType = extractReturnTypeFromDesc(methodDesc);

        for (int lineNum : matchingLines) {
            String line = lines[lineNum];
            String sourceParams = extractMethodParams(line);
            int sourceParamCount = countParams(sourceParams);
            String sourceReturnType = extractReturnTypeFromSource(line);
            if (sourceParamCount == descParamCount &&
                paramsMatch(sourceParams, methodDesc) &&
                returnTypeMatches(sourceReturnType, descReturnType)) {
                highlightAndScrollToLine(lineNum);
                return;
            }
        }

        for (int lineNum : matchingLines) {
            String line = lines[lineNum];
            String sourceParams = extractMethodParams(line);
            int sourceParamCount = countParams(sourceParams);
            String sourceReturnType = extractReturnTypeFromSource(line);
            if (sourceParamCount == descParamCount &&
                returnTypeMatches(sourceReturnType, descReturnType)) {
                highlightAndScrollToLine(lineNum);
                return;
            }
        }

        highlightAndScrollToLine(matchingLines.get(0));
    }

    /**
     * Scroll to a field definition in the source view and highlight the line.
     * Looks for field declarations with type annotations.
     */
    private void scrollToFieldDefinition(String fieldName) {
        String text = textArea.getText();
        String[] lines = text.split("\n");

        String dotField = "." + fieldName;

        String quotedName = java.util.regex.Pattern.quote(fieldName);
        java.util.regex.Pattern declarationPattern = java.util.regex.Pattern.compile(
            "^\\s*(public|private|protected|static|final|volatile|transient|\\s)+.*\\s+" +
            quotedName + "\\s*[;=]"
        );
        java.util.regex.Pattern simplePattern = java.util.regex.Pattern.compile(
            "^\\s+\\w+.*\\s+" + quotedName + "\\s*[;=]"
        );

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            if (!line.contains(fieldName)) {
                continue;
            }

            if (line.contains(dotField)) {
                continue;
            }

            boolean isDeclaration = declarationPattern.matcher(line).find() ||
                                   simplePattern.matcher(line).find();

            if (isDeclaration) {
                highlightAndScrollToLine(lineNum);
                return;
            }
        }
    }

    /**
     * Highlight a specific line and scroll to make it visible.
     * Places caret on line below so the highlighted line is clearly visible.
     */
    private void highlightAndScrollToLine(int lineNumber) {
        clearHighlight();
        try {
            currentLineHighlight = textArea.addLineHighlight(lineNumber, JStudioTheme.getLineHighlight());

            int caretLine = Math.max(lineNumber - 1, 0);
            int caretOffset = textArea.getLineStartOffset(caretLine);
            textArea.setCaretPosition(caretOffset);
            textArea.getCaret().setVisible(true);

            int highlightOffset = textArea.getLineStartOffset(lineNumber);
            java.awt.Rectangle rect = textArea.modelToView2D(highlightOffset).getBounds();
            if (rect != null) {
                rect.height = textArea.getHeight() / 3;
                textArea.scrollRectToVisible(rect);
            }
        } catch (BadLocationException e) {
            // ignore
        }
    }

    /**
     * Highlight a specific line (0-based line number).
     */
    public void highlightLine(int lineNumber) {
        highlightAndScrollToLine(lineNumber);
    }

    /**
     * Clear the current line highlight.
     */
    public void clearHighlight() {
        if (currentLineHighlight != null) {
            textArea.removeLineHighlight(currentLineHighlight);
            currentLineHighlight = null;
        }
    }

    /**
     * Scroll to and highlight a method declaration line.
     */
    public void scrollToMethodDeclaration(String methodName, String methodDesc) {
        if (!loaded) {
            refresh();
        }
        scrollToMethodDefinition(methodName, methodDesc);
    }

    /**
     * Scroll to and highlight a field declaration line.
     */
    public void scrollToFieldDeclaration(String fieldName) {
        if (!loaded) {
            refresh();
        }
        scrollToFieldDefinition(fieldName);
    }

    /**
     * Extract the Java identifier at the given offset.
     */
    private String extractIdentifierAt(String text, int offset) {
        if (offset < 0 || offset >= text.length()) return null;

        // Find start of identifier
        int start = offset;
        while (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            start--;
        }

        // Find end of identifier
        int end = offset;
        while (end < text.length() && isIdentifierChar(text.charAt(end))) {
            end++;
        }

        if (start == end) return null;
        return text.substring(start, end);
    }

    /**
     * Check if a character can be part of a Java identifier.
     */
    private boolean isIdentifierChar(char c) {
        return Character.isJavaIdentifierPart(c) || c == '.';
    }

    /**
     * Get the word at the current caret position.
     */
    private String getWordAtCaret() {
        try {
            int caretPos = textArea.getCaretPosition();
            String text = textArea.getText();
            if (caretPos < 0 || caretPos > text.length()) return null;

            int start = caretPos;
            int end = caretPos;

            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                start--;
            }
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
                end++;
            }

            if (start < end) {
                return text.substring(start, end);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Find a class by its simple name.
     */
    private ClassEntryModel findClassBySimpleName(String simpleName) {
        if (projectModel == null) return null;

        for (ClassEntryModel entry : projectModel.getAllClasses()) {
            if (entry.getSimpleName().equals(simpleName)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Set the project model for navigation.
     */
    public void setProjectModel(ProjectModel projectModel) {
        this.projectModel = projectModel;
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());

        textArea.setBackground(JStudioTheme.getBgTertiary());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setSelectionColor(JStudioTheme.getSelection());
        textArea.setCurrentLineHighlightColor(JStudioTheme.getLineHighlight());
        textArea.setFadeCurrentLineHighlight(true);
        textArea.setMatchedBracketBGColor(JStudioTheme.getSelection());
        textArea.setMatchedBracketBorderColor(JStudioTheme.getAccent());

        scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());

        // Apply syntax colors
        SyntaxScheme scheme = textArea.getSyntaxScheme();

        // Keywords (if, for, class, etc.)
        setTokenStyle(scheme, Token.RESERVED_WORD, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.RESERVED_WORD_2, SyntaxColors.getJavaKeyword());

        // Data types (int, String, etc.)
        setTokenStyle(scheme, Token.DATA_TYPE, SyntaxColors.getJavaType());

        // Strings
        setTokenStyle(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_CHAR, SyntaxColors.getJavaString());

        // Numbers
        setTokenStyle(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_FLOAT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_HEXADECIMAL, SyntaxColors.getJavaNumber());

        // Comments
        setTokenStyle(scheme, Token.COMMENT_EOL, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_MULTILINE, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_DOCUMENTATION, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_KEYWORD, SyntaxColors.getJavaAnnotation());

        // Functions/methods
        setTokenStyle(scheme, Token.FUNCTION, SyntaxColors.getJavaMethod());

        // Operators
        setTokenStyle(scheme, Token.OPERATOR, SyntaxColors.getJavaOperator());

        // Annotations
        setTokenStyle(scheme, Token.ANNOTATION, SyntaxColors.getJavaAnnotation());

        // Identifiers
        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());

        // Literals (null, true, false)
        setTokenStyle(scheme, Token.LITERAL_BOOLEAN, SyntaxColors.getJavaConstant());

        // Separators (braces, parens, etc.)
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextPrimary());

        repaint();
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }

    /**
     * Refresh/reload the source view.
     */
    public void refresh() {
        String cachedSource = classEntry.getDecompilationCache();
        if (cachedSource != null) {
            String textToSet = omitAnnotations ? filterAnnotations(cachedSource) : cachedSource;
            applyTextToEditor(textToSet);
            loaded = true;
            updateCommentGutterIcons();
            return;
        }

        if (loaded) {
            return;
        }

        cancelCurrentWorker();
        textArea.setText("");
        loadingOverlay.showLoading("Decompiling " + classEntry.getSimpleName() + "...");

        currentWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    ClassDecompiler decompiler = new ClassDecompiler(classEntry.getClassFile());
                    return decompiler.decompile();
                } catch (Exception e) {
                    return "// Decompilation failed: " + e.getMessage() + "\n\n" +
                            "// Class: " + classEntry.getClassName() + "\n" +
                            "// Error: " + e.getClass().getSimpleName() + "\n";
                }
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    loadingOverlay.hideLoading();
                    return;
                }
                try {
                    String source = get();
                    classEntry.setDecompilationCache(source);
                    String textToSet = omitAnnotations ? filterAnnotations(source) : source;
                    applyTextToEditor(textToSet);
                    loaded = true;
                    loadingOverlay.hideLoading();
                    updateCommentGutterIcons();
                } catch (Exception e) {
                    loadingOverlay.hideLoading();
                    textArea.setText("// Failed to decompile: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private void applyTextToEditor(String text) {
        ignoreDocumentChanges = true;
        textArea.setCodeFoldingEnabled(false);
        textArea.setBracketMatchingEnabled(false);
        textArea.setText(text);
        textArea.setCaretPosition(0);
        originalSource = text;
        dirty = false;
        compileToolbar.hideToolbar();
        ignoreDocumentChanges = false;

        SwingUtilities.invokeLater(() -> {
            textArea.setBracketMatchingEnabled(true);
            textArea.setAnimateBracketMatching(true);
            textArea.setPaintMatchedBracketPair(true);
            if (text.length() < 100000) {
                textArea.setCodeFoldingEnabled(true);
            }
        });
    }

    /**
     * Set whether to omit annotations from decompiled output display.
     */
    public void setOmitAnnotations(boolean omit) {
        this.omitAnnotations = omit;
        if (loaded && classEntry.getDecompilationCache() != null) {
            String source = classEntry.getDecompilationCache();
            String textToSet = omitAnnotations ? filterAnnotations(source) : source;
            ignoreDocumentChanges = true;
            textArea.setText(textToSet);
            textArea.setCaretPosition(0);
            originalSource = textToSet;
            dirty = false;
            compileToolbar.hideToolbar();
            ignoreDocumentChanges = false;
        }
    }

    /**
     * Filter out annotations from source code using annotation names from the class file.
     */
    private String filterAnnotations(String source) {
        Set<String> annotationNames = collectAnnotationNames();
        if (annotationNames.isEmpty()) {
            return source;
        }

        String result = source;
        for (String annoName : annotationNames) {
            result = removeAnnotation(result, annoName);
        }

        result = removeEmptyAnnotationLines(result);
        return result;
    }

    /**
     * Collect all annotation simple names from the class file (class, methods, fields).
     */
    private Set<String> collectAnnotationNames() {
        Set<String> names = new HashSet<>();
        ClassFile classFile = classEntry.getClassFile();

        collectAnnotationsFromAttributes(getClassAttributes(classFile), classFile, names);

        for (MethodEntry method : classFile.getMethods()) {
            collectAnnotationsFromAttributes(method.getAttributes(), classFile, names);
        }

        for (FieldEntry field : classFile.getFields()) {
            collectAnnotationsFromAttributes(field.getAttributes(), classFile, names);
        }

        return names;
    }

    private List<Attribute> getClassAttributes(ClassFile classFile) {
        List<Attribute> attrs = classFile.getClassAttributes();
        return attrs != null ? attrs : List.of();
    }

    private void collectAnnotationsFromAttributes(List<Attribute> attributes, ClassFile classFile, Set<String> names) {
        for (Attribute attr : attributes) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute annoAttr = (RuntimeVisibleAnnotationsAttribute) attr;
                for (Annotation anno : annoAttr.getAnnotations()) {
                    String simpleName = resolveAnnotationSimpleName(anno, classFile);
                    if (simpleName != null && !simpleName.isEmpty()) {
                        names.add(simpleName);
                    }
                }
            }
        }
    }

    private String resolveAnnotationSimpleName(Annotation anno, ClassFile classFile) {
        try {
            Object item = classFile.getConstPool().getItem(anno.getTypeIndex());
            if (item instanceof Utf8Item) {
                String type = ((Utf8Item) item).getValue();
                if (type.startsWith("L") && type.endsWith(";")) {
                    type = type.substring(1, type.length() - 1);
                }
                int lastSlash = type.lastIndexOf('/');
                return lastSlash >= 0 ? type.substring(lastSlash + 1) : type;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Remove a specific annotation (including multi-line with nested parens) from source.
     * Also handles obfuscated annotations where the type name contains newlines.
     */
    private String removeAnnotation(String source, String annotationName) {
        String cleanName = annotationName.replace("\r", "").split("\n")[0].trim();
        if (cleanName.isEmpty()) {
            return source;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = source.split("\n", -1);
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (isAnnotationStart(trimmed, cleanName)) {
                int parenDepth = countChar(line, '(') - countChar(line, ')');

                while (parenDepth > 0 && i + 1 < lines.length) {
                    i++;
                    int delta = countChar(lines[i], '(') - countChar(lines[i], ')');
                    parenDepth += delta;
                }
                i++;

                while (i < lines.length) {
                    String nextTrimmed = lines[i].trim();
                    if (isActualJavaCode(nextTrimmed)) {
                        break;
                    }
                    i++;
                }
                continue;
            }

            result.append(line);
            if (i < lines.length - 1) {
                result.append("\n");
            }
            i++;
        }

        return result.toString();
    }

    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "public", "private", "protected", "static", "final", "abstract",
        "native", "synchronized", "transient", "volatile", "strictfp",
        "class", "interface", "enum", "record", "extends", "implements",
        "void", "boolean", "byte", "char", "short", "int", "long", "float", "double",
        "package", "import", "return", "if", "else", "for", "while", "do",
        "switch", "case", "default", "break", "continue", "throw", "throws",
        "try", "catch", "finally", "new", "this", "super", "instanceof"
    );

    private boolean isActualJavaCode(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("@")) {
            return true;
        }
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            return true;
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("}") || trimmed.endsWith(";")) {
            return true;
        }
        String firstToken = trimmed.split("\\s+|\\(|<")[0];
        if (JAVA_KEYWORDS.contains(firstToken)) {
            return true;
        }
        return trimmed.contains("(") || trimmed.contains(")") ||
                trimmed.contains("{") || trimmed.contains("}") ||
                trimmed.contains("=") || trimmed.contains(";");
    }

    private boolean isAnnotationStart(String trimmed, String annotationName) {
        if (!trimmed.startsWith("@")) {
            return false;
        }

        String afterAt = trimmed.substring(1);
        int endOfName = 0;
        while (endOfName < afterAt.length()) {
            char c = afterAt.charAt(endOfName);
            if (!Character.isJavaIdentifierPart(c) && c != '.') {
                break;
            }
            endOfName++;
        }

        if (endOfName == 0) {
            return false;
        }

        String fullAnnoName = afterAt.substring(0, endOfName);
        String simpleName = fullAnnoName;
        int lastDot = fullAnnoName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleName = fullAnnoName.substring(lastDot + 1);
        }

        if (!simpleName.equals(annotationName)) {
            return false;
        }

        if (endOfName == afterAt.length()) {
            return true;
        }
        char next = afterAt.charAt(endOfName);
        return next == '(' || next == ' ' || next == '\t' || next == '\r' || next == '\n';
    }

    private int countChar(String s, char c) {
        int count = 0;
        boolean inString = false;
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && prev != '\\') {
                inString = !inString;
            } else if (!inString && ch == c) {
                count++;
            }
            prev = ch;
        }
        return count;
    }

    /**
     * Remove any remaining standalone annotation lines not in our collected set.
     */
    private String removeEmptyAnnotationLines(String source) {
        return source;
    }

    /**
     * Get the current text.
     */
    public String getText() {
        return textArea.getText();
    }

    /**
     * Copy current selection to clipboard.
     */
    public void copySelection() {
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    /**
     * Go to a specific line.
     */
    public void goToLine(int line) {
        try {
            int offset = textArea.getLineStartOffset(line - 1);
            textArea.setCaretPosition(offset);
            textArea.requestFocus();
        } catch (Exception e) {
            // Line out of range
        }
    }

    /**
     * Show inline search panel.
     */
    public void showFindDialog() {
        searchPanel.showPanel();
    }

    /**
     * Hide the search panel.
     */
    public void hideSearchPanel() {
        searchPanel.setHidden();
    }

    /**
     * Get the selected text.
     */
    public String getSelectedText() {
        return textArea.getSelectedText();
    }

    /**
     * Scroll to and highlight text.
     */
    public void scrollToText(String text) {
        if (text == null || text.isEmpty()) return;

        SearchContext context = new SearchContext(text);
        context.setMatchCase(false);
        context.setWholeWord(false);
        SearchEngine.find(textArea, context);
    }

    /**
     * Set the font size.
     */
    public void setFontSize(int size) {
        textArea.setFont(JStudioTheme.getCodeFont(size));
    }

    /**
     * Set word wrap enabled/disabled.
     */
    public void setWordWrap(boolean enabled) {
        textArea.setLineWrap(enabled);
        textArea.setWrapStyleWord(enabled);
    }

    private enum DeclarationType {
        CLASS("Class"),
        METHOD("Method"),
        FIELD("Field");

        final String displayName;

        DeclarationType(String displayName) {
            this.displayName = displayName;
        }
    }

    private static class DeclarationInfo {
        final DeclarationType type;
        final String name;
        final String descriptor;

        DeclarationInfo(DeclarationType type, String name) {
            this(type, name, null);
        }

        DeclarationInfo(DeclarationType type, String name, String descriptor) {
            this.type = type;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private DeclarationInfo getDeclarationAtLine(int lineNumber) {
        try {
            int startOffset = textArea.getLineStartOffset(lineNumber - 1);
            int endOffset = textArea.getLineEndOffset(lineNumber - 1);
            String lineText = textArea.getText(startOffset, endOffset - startOffset);

            String className = extractClassDeclaration(lineText);
            if (className != null) {
                return new DeclarationInfo(DeclarationType.CLASS, className);
            }

            String methodName = extractMethodDeclaration(lineText);
            if (methodName != null) {
                String paramTypes = extractMethodParams(lineText);
                MethodEntryModel method = findMethodByNameAndParams(methodName, paramTypes);
                if (method != null) {
                    return new DeclarationInfo(DeclarationType.METHOD, methodName, method.getDescriptor());
                }
                return new DeclarationInfo(DeclarationType.METHOD, methodName);
            }

            String fieldName = extractFieldDeclaration(lineText);
            if (fieldName != null) {
                return new DeclarationInfo(DeclarationType.FIELD, fieldName);
            }
        } catch (BadLocationException e) {
            // ignore
        }
        return null;
    }

    private static final java.util.regex.Pattern CLASS_DECL_PATTERN = java.util.regex.Pattern.compile(
        "^\\s*(?:public|private|protected|abstract|final|static|strictfp|\\s)*\\s*(?:class|interface|enum|@interface)\\s+(\\w+)"
    );

    private String extractClassDeclaration(String line) {
        java.util.regex.Matcher m = CLASS_DECL_PATTERN.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static final java.util.regex.Pattern METHOD_DECL_PATTERN = java.util.regex.Pattern.compile(
        "^\\s*(?:public|private|protected|static|final|abstract|synchronized|native|strictfp|\\s)*" +
        "(?:<[^>]+>\\s*)?" +
        "\\w+(?:<[^>]*>)?(?:\\[\\])*\\s+" +
        "(\\w+)\\s*\\("
    );

    private String extractMethodDeclaration(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("return") || trimmed.startsWith("if") ||
            trimmed.startsWith("while") || trimmed.startsWith("for") ||
            trimmed.startsWith("switch") || trimmed.startsWith("new ") ||
            trimmed.startsWith("throw ") || trimmed.startsWith("//") ||
            trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            return null;
        }
        if (line.contains(" new ") || line.contains("=")) {
            return null;
        }

        java.util.regex.Matcher m = METHOD_DECL_PATTERN.matcher(line);
        if (m.find()) {
            String name = m.group(1);
            if (!name.equals("if") && !name.equals("while") && !name.equals("for") &&
                !name.equals("switch") && !name.equals("catch") && !name.equals("synchronized")) {
                return name;
            }
        }
        return null;
    }

    private static final java.util.regex.Pattern FIELD_DECL_PATTERN = java.util.regex.Pattern.compile(
        "^\\s*(?:public|private|protected|static|final|volatile|transient|\\s)*" +
        "\\w+(?:<[^>]*>)?(?:\\[\\])*\\s+" +
        "(\\w+)\\s*[;=]"
    );

    private String extractFieldDeclaration(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("return") || trimmed.startsWith("//") ||
            trimmed.startsWith("/*") || trimmed.startsWith("*") ||
            trimmed.contains("(")) {
            return null;
        }

        java.util.regex.Matcher m = FIELD_DECL_PATTERN.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractMethodParams(String line) {
        int parenStart = line.indexOf('(');
        int parenEnd = line.lastIndexOf(')');
        if (parenStart >= 0 && parenEnd > parenStart) {
            return line.substring(parenStart + 1, parenEnd).trim();
        }
        return "";
    }

    private MethodEntryModel findMethodByNameAndParams(String name, String sourceParams) {
        List<MethodEntryModel> candidates = new ArrayList<>();
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (method.getName().equals(name)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        int sourceParamCount = countParams(sourceParams);
        for (MethodEntryModel method : candidates) {
            String desc = method.getDescriptor();
            int descParamCount = countDescriptorParams(desc);
            if (descParamCount == sourceParamCount) {
                if (paramsMatch(sourceParams, desc)) {
                    return method;
                }
            }
        }

        for (MethodEntryModel method : candidates) {
            String desc = method.getDescriptor();
            int descParamCount = countDescriptorParams(desc);
            if (descParamCount == sourceParamCount) {
                return method;
            }
        }

        return candidates.get(0);
    }

    private int countParams(String sourceParams) {
        if (sourceParams == null || sourceParams.isEmpty()) {
            return 0;
        }
        int count = 0;
        int depth = 0;
        for (int i = 0; i < sourceParams.length(); i++) {
            char c = sourceParams.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) count++;
        }
        return count + 1;
    }

    private int countDescriptorParams(String desc) {
        int count = 0;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') {
                while (i < desc.length() && desc.charAt(i) != ';') i++;
            } else if (c == '[') {
                i++;
                continue;
            }
            count++;
            i++;
        }
        return count;
    }

    private boolean paramsMatch(String sourceParams, String desc) {
        String[] sourceTypes = sourceParams.split(",");
        int descIndex = 1;
        int paramIndex = 0;

        while (descIndex < desc.length() && desc.charAt(descIndex) != ')') {
            if (paramIndex >= sourceTypes.length) {
                return false;
            }
            String sourceType = sourceTypes[paramIndex].trim();
            int spaceIdx = sourceType.lastIndexOf(' ');
            if (spaceIdx > 0) {
                sourceType = sourceType.substring(0, spaceIdx).trim();
            }
            int genericIdx = sourceType.indexOf('<');
            if (genericIdx > 0) {
                sourceType = sourceType.substring(0, genericIdx);
            }
            sourceType = sourceType.replace("[]", "");

            String descType = extractDescType(desc, descIndex);
            if (!typeMatches(sourceType, descType)) {
                return false;
            }

            descIndex = skipDescType(desc, descIndex);
            paramIndex++;
        }
        return paramIndex == sourceTypes.length || (sourceTypes.length == 1 && sourceTypes[0].trim().isEmpty());
    }

    private String extractDescType(String desc, int index) {
        char c = desc.charAt(index);
        switch (c) {
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case 'V': return "void";
            case '[': return extractDescType(desc, index + 1) + "[]";
            case 'L':
                int end = desc.indexOf(';', index);
                String className = desc.substring(index + 1, end);
                int lastSlash = className.lastIndexOf('/');
                return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
            default: return "";
        }
    }

    private int skipDescType(String desc, int index) {
        char c = desc.charAt(index);
        if (c == '[') {
            return skipDescType(desc, index + 1);
        } else if (c == 'L') {
            return desc.indexOf(';', index) + 1;
        } else {
            return index + 1;
        }
    }

    private boolean typeMatches(String sourceType, String descType) {
        if (sourceType.equals(descType)) {
            return true;
        }
        String simpleSource = sourceType;
        int dotIdx = simpleSource.lastIndexOf('.');
        if (dotIdx >= 0) {
            simpleSource = simpleSource.substring(dotIdx + 1);
        }
        return simpleSource.equals(descType);
    }

    private String extractReturnTypeFromDesc(String desc) {
        if (desc == null) return null;
        int parenClose = desc.indexOf(')');
        if (parenClose < 0 || parenClose >= desc.length() - 1) return null;
        return extractDescType(desc, parenClose + 1);
    }

    private String extractReturnTypeFromSource(String line) {
        String trimmed = line.trim();
        int parenIdx = trimmed.indexOf('(');
        if (parenIdx < 0) return null;

        String beforeParen = trimmed.substring(0, parenIdx).trim();
        String[] parts = beforeParen.split("\\s+");
        if (parts.length < 2) return null;

        String returnType = parts[parts.length - 2];
        int genericIdx = returnType.indexOf('<');
        if (genericIdx > 0) {
            returnType = returnType.substring(0, genericIdx);
        }
        return returnType;
    }

    private boolean returnTypeMatches(String sourceReturnType, String descReturnType) {
        if (sourceReturnType == null || descReturnType == null) return true;
        return typeMatches(sourceReturnType, descReturnType);
    }

    private void showRenameDialog(DeclarationInfo decl) {
        if (decl == null) {
            return;
        }

        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (!(window instanceof MainFrame)) {
            return;
        }
        MainFrame mainFrame = (MainFrame) window;

        switch (decl.type) {
            case CLASS:
                mainFrame.showRenameClassDialog(classEntry);
                break;
            case METHOD:
                MethodEntryModel methodModel = findMethodByName(decl.name);
                if (methodModel != null) {
                    mainFrame.showRenameMethodDialog(classEntry, methodModel);
                }
                break;
            case FIELD:
                FieldEntryModel fieldModel = findFieldByName(decl.name);
                if (fieldModel != null) {
                    mainFrame.showRenameFieldDialog(classEntry, fieldModel);
                }
                break;
        }
    }

    private void findUsagesOfDeclaration(DeclarationInfo decl) {
        if (decl == null) {
            return;
        }

        String className = classEntry.getClassName();
        switch (decl.type) {
            case CLASS:
                EventBus.getInstance().post(FindUsagesEvent.forClass(this, className));
                break;
            case METHOD:
                if (decl.descriptor != null) {
                    EventBus.getInstance().post(FindUsagesEvent.forMethod(
                            this, className, decl.name, decl.descriptor));
                } else {
                    MethodEntryModel method = findMethodByName(decl.name);
                    if (method != null) {
                        EventBus.getInstance().post(FindUsagesEvent.forMethod(
                                this, className, method.getName(), method.getDescriptor()));
                    }
                }
                break;
            case FIELD:
                FieldEntryModel field = findFieldByName(decl.name);
                if (field != null) {
                    EventBus.getInstance().post(FindUsagesEvent.forField(
                            this, className, field.getName(), field.getFieldEntry().getDesc()));
                }
                break;
        }
    }

    private MethodEntryModel findMethodByName(String name) {
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private FieldEntryModel findFieldByName(String name) {
        for (FieldEntryModel field : classEntry.getFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }
}
