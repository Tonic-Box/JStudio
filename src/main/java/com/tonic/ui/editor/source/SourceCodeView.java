package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.MainFrame;
import com.tonic.ui.model.Bookmark;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.Comment;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectDatabaseService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.SyntaxColors;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

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
import javax.swing.SwingWorker;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/**
 * Source code view using RSyntaxTextArea for Java syntax highlighting.
 */
public class SourceCodeView extends JPanel implements ThemeManager.ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private ProjectModel projectModel;

    private boolean loaded = false;
    private boolean omitAnnotations = false;
    private java.util.List<GutterIconInfo> commentIcons = new java.util.ArrayList<>();

    public SourceCodeView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        // Create syntax-highlighted text area
        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setEditable(false);
        textArea.setFont(JStudioTheme.getCodeFont(13));

        // Enable bracket matching to highlight both brackets in a pair
        textArea.setBracketMatchingEnabled(true);
        textArea.setAnimateBracketMatching(true);
        textArea.setPaintMatchedBracketPair(true);

        // Scroll pane with line numbers
        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setIconRowHeaderEnabled(true);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        // Apply theme (must be after scrollPane is created)
        applyTheme();

        // Setup Ctrl+Click for Go to Definition
        setupGoToDefinition();

        // Setup right-click context menu
        setupContextMenu();

        // Listen for comment changes to update gutter icons
        ProjectDatabaseService.getInstance().addListener((db, dirty) -> {
            SwingUtilities.invokeLater(this::updateCommentGutterIcons);
        });

        ThemeManager.getInstance().addThemeChangeListener(this);
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
        gotoItem.addActionListener(ev -> navigateToDefinition(e));
        String selectedText = textArea.getSelectedText();
        gotoItem.setEnabled(selectedText != null && !selectedText.isEmpty());
        menu.add(gotoItem);

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
        java.util.List<Comment> comments = ProjectDatabaseService.getInstance()
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
        java.util.List<Comment> comments = ProjectDatabaseService.getInstance()
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
        if (parent instanceof MainFrame) {
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

        java.util.List<Comment> comments = ProjectDatabaseService.getInstance()
                .getDatabase().getComments().getCommentsForClass(classEntry.getClassName());

        if (comments.isEmpty()) {
            return;
        }

        // Group comments by line number
        java.util.Map<Integer, java.util.List<Comment>> commentsByLine = new java.util.HashMap<>();
        for (Comment c : comments) {
            int line = c.getLineNumber();
            if (line > 0) {
                commentsByLine.computeIfAbsent(line, k -> new java.util.ArrayList<>()).add(c);
            }
        }

        // Add gutter icons for each line with comments
        javax.swing.Icon commentIcon = Icons.getIcon("comment");
        for (java.util.Map.Entry<Integer, java.util.List<Comment>> entry : commentsByLine.entrySet()) {
            int lineNumber = entry.getKey();
            java.util.List<Comment> lineComments = entry.getValue();

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
            // Get the position in the text
            int offset = textArea.viewToModel2D(e.getPoint());
            if (offset < 0) return;

            // Extract the identifier at this position
            String text = textArea.getText();
            String identifier = extractIdentifierAt(text, offset);

            if (identifier == null || identifier.isEmpty()) return;

            // Try to find a class matching this identifier
            ClassEntryModel targetClass = findClassBySimpleName(identifier);
            if (targetClass != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(this, targetClass));
                return;
            }

            // Could also try to find by fully qualified name
            targetClass = projectModel.getClass(identifier.replace('.', '/'));
            if (targetClass != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(this, targetClass));
            }
        } catch (Exception ex) {
            // Ignore navigation errors
        }
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
        if (loaded && classEntry.getDecompilationCache() != null) {
            // Use cached decompilation
            String source = classEntry.getDecompilationCache();
            textArea.setText(omitAnnotations ? filterAnnotations(source) : source);
            textArea.setCaretPosition(0);
            updateCommentGutterIcons();
            return;
        }

        // Show loading message
        textArea.setText("// Decompiling " + classEntry.getClassName() + "...\n");

        // Decompile in background
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
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
                try {
                    String source = get();
                    classEntry.setDecompilationCache(source);
                    textArea.setText(omitAnnotations ? filterAnnotations(source) : source);
                    textArea.setCaretPosition(0);
                    loaded = true;
                    updateCommentGutterIcons();
                } catch (Exception e) {
                    textArea.setText("// Failed to decompile: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Set whether to omit annotations from decompiled output display.
     */
    public void setOmitAnnotations(boolean omit) {
        this.omitAnnotations = omit;
        if (loaded && classEntry.getDecompilationCache() != null) {
            String source = classEntry.getDecompilationCache();
            textArea.setText(omitAnnotations ? filterAnnotations(source) : source);
            textArea.setCaretPosition(0);
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
        int removed = 0;

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (isAnnotationStart(trimmed, cleanName)) {
                removed++;
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
        if (trimmed.contains("(") || trimmed.contains(")") ||
            trimmed.contains("{") || trimmed.contains("}") ||
            trimmed.contains("=") || trimmed.contains(";")) {
            return true;
        }
        return false;
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

    private String lastSearch;

    /**
     * Show find dialog.
     */
    public void showFindDialog() {
        String input = (String) JOptionPane.showInputDialog(
            this,
            "Find:",
            "Find",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            lastSearch
        );
        lastSearch = input;
        if (input != null && !input.isEmpty()) {
            SearchContext context = new SearchContext(input);
            context.setMatchCase(false);
            context.setWholeWord(false);
            SearchEngine.find(textArea, context);
        }
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

    /**
     * Get the text area for direct access (e.g., for Ctrl+Click).
     */
    public RSyntaxTextArea getTextArea() {
        return textArea;
    }
}
