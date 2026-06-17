package com.tonic.ui.live.eval;

import com.tonic.live.LiveSession;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.editor.source.JavaEditorFactory;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.JStudioTheme;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-modal scratch pad for running arbitrary Java inside the attached JVM. The snippet is compiled against
 * the target's pulled classes ({@link SnippetCompiler}) and executed there ({@link LiveSession#eval}) in a
 * throwaway child of a chosen context class's loader; stdout/result/exceptions come back into the console.
 *
 * <p>Stateless per run (each Run is a fresh class). Running arbitrary code in a live JVM is as powerful as
 * live redefinition, so the first run in a session asks for confirmation.
 */
public final class LiveScratchPadDialog extends JDialog {

    private static final String DEFAULT_SNIPPET = String.join("\n",
            "// Write Java statements; a trailing 'return <expr>;' becomes the result (no return -> true).",
            "// 'import' lines at the top are supported. Public members of the context class's",
            "// classloader are visible. Ctrl+Space completes; Ctrl+Enter runs.",
            "",
            "return 2 + 2;");

    private static boolean accepted;

    private final JComboBox<String> contextSelector = new JComboBox<>();
    private final RSyntaxTextArea editor;
    private final RTextScrollPane editorScroll;
    private final JTextArea console = new JTextArea();
    private final JButton runButton = new JButton("Run");

    private ProjectModel project;
    private SnippetCompiler compiler;
    private AutoCompletion autoCompletion;

    public LiveScratchPadDialog(Frame owner) {
        super(owner, "Java Scratch Pad", false);
        editor = JavaEditorFactory.createEditor(true);
        editor.setText(DEFAULT_SNIPPET);
        editorScroll = JavaEditorFactory.createScrollPane(editor);
        JavaEditorFactory.applyTheme(editor, editorScroll);

        buildUi();
        installRunShortcut();

        setSize(760, 620);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(JStudioTheme.getBgTertiary());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.setOpaque(false);
        JLabel label = new JLabel("Context class:");
        label.setForeground(JStudioTheme.getTextSecondary());
        contextSelector.setRenderer(new BinaryNameRenderer());
        contextSelector.setToolTipText("The snippet runs in a throwaway child of this class's classloader");
        top.add(label);
        top.add(contextSelector);

        console.setEditable(false);
        console.setLineWrap(true);
        console.setWrapStyleWord(false);
        console.setFont(JStudioTheme.getCodeFont(13));
        console.setBackground(JStudioTheme.getBgSecondary());
        console.setForeground(JStudioTheme.getTextPrimary());
        console.setCaretColor(JStudioTheme.getTextPrimary());
        console.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, consoleScroll);
        split.setResizeWeight(0.65);
        split.setBorder(null);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bottom.setOpaque(false);
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> console.setText(""));
        runButton.setToolTipText("Compile and run in the attached JVM (Ctrl+Enter)");
        runButton.addActionListener(e -> runSnippet());
        bottom.add(clearButton);
        bottom.add(runButton);

        content.add(top, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void installRunShortcut() {
        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
        editor.getInputMap().put(ctrlEnter, "scratch-run");
        editor.getActionMap().put("scratch-run", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runSnippet();
            }
        });
    }

    /** Binds the dialog to {@code project}: rebuilds the compiler, completion, and context list. */
    public void setProject(ProjectModel project) {
        this.project = project;
        this.compiler = new SnippetCompiler(new ProjectClasspath(project), targetRelease(project));

        if (autoCompletion != null) {
            autoCompletion.uninstall();
        }
        ScratchCompletionProvider provider = new ScratchCompletionProvider(project);
        provider.setAutoActivationRules(true, ".");
        autoCompletion = new AutoCompletion(provider);
        autoCompletion.setAutoCompleteEnabled(true);
        autoCompletion.setAutoCompleteSingleChoices(false);
        autoCompletion.setAutoActivationEnabled(true);
        autoCompletion.setAutoActivationDelay(250);
        autoCompletion.setShowDescWindow(false);
        autoCompletion.setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK));
        autoCompletion.install(editor);

        List<String> names = new ArrayList<>(project.getUserClassNames());
        names.sort(String::compareTo);
        contextSelector.removeAllItems();
        for (String name : names) {
            contextSelector.addItem(name);
        }
    }

    /** Pre-selects {@code internalName} as the context class if present. */
    public void setContextClass(String internalName) {
        if (internalName != null) {
            contextSelector.setSelectedItem(internalName);
        }
    }

    /**
     * The Java release to compile snippets for: derived from the highest class-file version among the target's
     * pulled classes (which is at most the target JVM's runtime version), so a compiled snippet can always be
     * defined by the attached JVM even when it is older than the JDK running JStudio.
     */
    private static int targetRelease(ProjectModel project) {
        int maxMajor = 0;
        for (ClassEntryModel entry : project.getAllClasses()) {
            int major = entry.getClassFile().getMajorVersion();
            if (major > maxMajor) {
                maxMajor = major;
            }
        }
        return SnippetCompiler.releaseForMajorVersion(maxMajor);
    }

    private void runSnippet() {
        LiveAttachService service = LiveAttachService.getInstance();
        if (!service.isAttached() || project == null || compiler == null) {
            appendln("Not attached to a live JVM.");
            return;
        }
        String contextClass = (String) contextSelector.getSelectedItem();
        if (contextClass == null) {
            appendln("Pick a context class first.");
            return;
        }
        if (!confirmFirstRun()) {
            return;
        }
        String snippet = editor.getText();
        LiveSession session = service.getSession();

        runButton.setEnabled(false);
        appendln("> running...");
        SwingWorkers.run(
                () -> {
                    SnippetCompiler.Result result = compiler.compile(snippet);
                    if (!result.isSuccess()) {
                        return "compile failed:\n" + String.join("\n", result.getMessages());
                    }
                    return session.eval(result.getClasses(), result.getMainBinaryName(), contextClass);
                },
                output -> {
                    appendln(output);
                    runButton.setEnabled(true);
                },
                error -> {
                    appendln("eval error: " + error.getMessage());
                    runButton.setEnabled(true);
                });
    }

    private boolean confirmFirstRun() {
        if (accepted) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "This runs arbitrary Java inside the attached JVM - as powerful and risky as live class\n"
                        + "redefinition. It executes on the live connection thread, so a blocking or looping\n"
                        + "snippet stalls the connection until it returns. Continue?",
                "Run code in attached JVM?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            accepted = true;
            return true;
        }
        return false;
    }

    private void appendln(String text) {
        if (console.getDocument().getLength() > 0) {
            console.append("\n");
        }
        console.append(text);
        console.setCaretPosition(console.getDocument().getLength());
    }

    /** Renders class items in readable dotted form while keeping internal names as the item values. */
    private static final class BinaryNameRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            String text = value instanceof String ? ((String) value).replace('/', '.') : "";
            Component c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            setHorizontalAlignment(SwingConstants.LEFT);
            return c;
        }
    }
}
