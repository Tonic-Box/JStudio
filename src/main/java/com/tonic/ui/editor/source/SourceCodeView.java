package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.SyntaxColors;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        // Apply theme (must be after scrollPane is created)
        applyTheme();

        // Setup Ctrl+Click for Go to Definition
        setupGoToDefinition();

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
            textArea.setText(classEntry.getDecompilationCache());
            textArea.setCaretPosition(0);
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
                    textArea.setText(source);
                    textArea.setCaretPosition(0);
                    loaded = true;
                } catch (Exception e) {
                    textArea.setText("// Failed to decompile: " + e.getMessage());
                }
            }
        };

        worker.execute();
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
     * Show find dialog.
     */
    public void showFindDialog() {
        String input = JOptionPane.showInputDialog(this, "Find:", "Find",
                JOptionPane.PLAIN_MESSAGE);
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
