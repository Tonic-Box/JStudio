package com.tonic.ui.editor.resource;

import com.tonic.ui.model.ResourceEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;

public class TextResourceView extends JPanel implements ThemeChangeListener {

    private final ResourceEntryModel resource;
    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;

    public TextResourceView(ResourceEntryModel resource) {
        this.resource = resource;
        setLayout(new BorderLayout());

        textArea = new RSyntaxTextArea();
        textArea.setEditable(false);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(JStudioTheme.getCodeFont(12));
        textArea.setSyntaxEditingStyle(detectSyntaxStyle(resource.getName()));

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        loadContent();
        applyTheme();

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void loadContent() {
        String content = new String(resource.getData(), StandardCharsets.UTF_8);
        textArea.setText(content);
        textArea.setCaretPosition(0);
    }

    private String detectSyntaxStyle(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".xml") || lower.endsWith(".xsd") || lower.endsWith(".xsl")) {
            return SyntaxConstants.SYNTAX_STYLE_XML;
        }
        if (lower.endsWith(".json")) {
            return SyntaxConstants.SYNTAX_STYLE_JSON;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return SyntaxConstants.SYNTAX_STYLE_HTML;
        }
        if (lower.endsWith(".css")) {
            return SyntaxConstants.SYNTAX_STYLE_CSS;
        }
        if (lower.endsWith(".js")) {
            return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
        }
        if (lower.endsWith(".properties")) {
            return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return SyntaxConstants.SYNTAX_STYLE_YAML;
        }
        if (lower.endsWith(".md")) {
            return SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
        }
        if (lower.endsWith(".sh") || lower.endsWith(".bat")) {
            return SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
        }
        if (lower.endsWith(".sql")) {
            return SyntaxConstants.SYNTAX_STYLE_SQL;
        }
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        textArea.setBackground(JStudioTheme.getBgTertiary());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setCurrentLineHighlightColor(JStudioTheme.getBgSecondary());
        textArea.setSelectionColor(JStudioTheme.getSelection());

        scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());
        scrollPane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, JStudioTheme.getBorder()));
    }

    public String getText() {
        return textArea.getText();
    }

    public void setFontSize(int size) {
        textArea.setFont(JStudioTheme.getCodeFont(size));
    }
}
