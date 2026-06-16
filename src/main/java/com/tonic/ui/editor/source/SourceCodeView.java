package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.editor.SearchPanel;
import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.ui.MainFrame;
import com.tonic.model.Bookmark;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.Comment;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.event.events.StatusMessageEvent;
import com.tonic.service.ProjectDatabaseService;
import com.tonic.service.XrefQueryService;
import com.tonic.util.Settings;
import com.tonic.ui.theme.*;

import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
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
import java.util.NavigableMap;
import java.util.Set;
import java.util.function.IntConsumer;
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
    private final List<GutterIconInfo> runIcons = new ArrayList<>();
    private final java.util.Set<Integer> runLines = new java.util.HashSet<>();
    private Object currentLineHighlight;
    private final LoadingOverlay loadingOverlay;
    private SwingWorker<String, Void> currentWorker;
    private Runnable pendingNavigation;
    private IntConsumer onLineActivated;

    private final UsageLensOverlay lensOverlay = new UsageLensOverlay();
    private boolean usageLensEnabled = Settings.getInstance().isUsageLensEnabled();
    private int lensGeneration;

    private final FloatingCompileToolbar compileToolbar;
    private final SourceCompilerParser compilerParser;
    private String originalSource;
    @Getter
    private boolean dirty = false;
    private boolean ignoreDocumentChanges = false;
    /** Invoked after a successful recompile so the owning tab can refresh its other views. */
    private Runnable onRecompiled;

    public SourceCodeView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textArea = new RSyntaxTextArea() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                lensOverlay.paint((java.awt.Graphics2D) g, this);
            }
        };
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

        scrollPane.getGutter().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (runLines.isEmpty()) {
                    return;
                }
                int offset = textArea.viewToModel2D(new java.awt.Point(0, e.getY()));
                if (offset < 0) {
                    return;
                }
                try {
                    if (runLines.contains(textArea.getLineOfOffset(offset) + 1)) {
                        runMainViaMainFrame();
                    }
                } catch (BadLocationException ignored) {
                }
            }
        });

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

        searchPanel = new SearchPanel(textArea, scrollPane);
        add(searchPanel, BorderLayout.SOUTH);

        setupDocumentListener();

        // Apply theme (must be after scrollPane is created)
        applyTheme();

        // Setup Ctrl+Click for Go to Definition
        setupGoToDefinition();

        // Setup right-click context menu
        setupContextMenu();

        // Setup clickable usage-count lenses
        setupUsageLensMouseHandling();

        // Listen for comment changes to update gutter icons
        ProjectDatabaseService.getInstance().addListener((db, dirty) -> SwingUtilities.invokeLater(this::updateCommentGutterIcons));

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void setupDocumentListener() {
        textArea.addPropertyChangeListener(RSyntaxTextArea.PARSER_NOTICES_PROPERTY, evt -> {
            if (dirty) {
                updateToolbarState();
            }
        });
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
        if (ignoreDocumentChanges) {
            return;
        }

        String currentText = textArea.getText();
        boolean nowDirty = originalSource != null && !currentText.equals(originalSource);

        if (nowDirty != dirty) {
            dirty = nowDirty;
            if (dirty) {
                compilerParser.setEnabled(true);
                compileToolbar.showModified();
                compileToolbar.setLivePatchMode(com.tonic.ui.live.LiveAttachService.getInstance().isAttached());
                lensOverlay.clear();
            } else {
                compilerParser.setEnabled(false);
                compileToolbar.hideToolbar();
                scheduleUsageLensUpdate();
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

    /** Sets a callback invoked after a successful recompile (the owning tab refreshes its views). */
    public void setOnRecompiled(Runnable onRecompiled) {
        this.onRecompiled = onRecompiled;
    }

    private void doRecompile() {
        if (!dirty) {
            return;
        }

        String source = textArea.getText();
        // The source as it was before this edit - captured now because a successful recompile overwrites
        // originalSource below; the live patch diffs against it to graft only the methods that changed.
        final String baselineSource = originalSource;
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
                        if (projectModel != null) {
                            projectModel.setXrefDatabase(null);
                        }
                        originalSource = source;
                        dirty = false;
                        if (onRecompiled != null) {
                            onRecompiled.run();
                        }
                        if (result.hasWarnings()) {
                            compileToolbar.showWithErrors(0, result.getWarningCount(), result.getErrors());
                        } else {
                            compileToolbar.showSuccess(result.getCompilationTimeMs());
                        }

                        if (com.tonic.ui.live.LiveAttachService.getInstance().isAttached()) {
                            livePatch(baselineSource, source);
                        } else {
                            scheduleToolbarHide(1500);
                        }
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

    /**
     * Pushes the just-recompiled class to the attached JVM. Only the method bodies that changed since
     * {@code baselineSource} are grafted onto the running class (see {@link com.tonic.ui.live.LivePatch}), so
     * untouched methods and synthetic members keep the running class's exact bytes and the redefine is accepted
     * even when a decompile/recompile round-trip would have perturbed synthetics. After a successful patch the
     * in-memory model is synced to the running class. Runs the fetch/graft/redefine off the EDT.
     */
    private void livePatch(String baselineSource, String editedSource) {
        com.tonic.ui.live.LiveAttachService svc = com.tonic.ui.live.LiveAttachService.getInstance();
        if (!svc.isAttached()) {
            scheduleToolbarHide(1500);
            return;
        }
        final com.tonic.live.LiveSession session = svc.getSession();
        final String internalName = classEntry.getClassName();
        final com.tonic.parser.ClassFile edited = classEntry.getClassFile();
        final com.tonic.parser.ClassPool classPool = projectModel != null ? projectModel.getClassPool() : null;
        final java.util.Set<String> changedMethods = com.tonic.ui.live.MethodBodyDiff.changedMethods(
                baselineSource, editedSource, classPool, internalName);
        compileToolbar.showPatching();
        com.tonic.ui.core.SwingWorkers.run(
                () -> {
                    byte[] bytes = changedMethods.isEmpty()
                            ? com.tonic.ui.live.LivePatch.buildRedefineBytes(session, internalName, edited)
                            : com.tonic.ui.live.LivePatch.buildGraftedRedefineBytes(
                                    session, internalName, edited, changedMethods);
                    session.redefineClass(internalName, bytes);
                    return bytes;
                },
                runningBytes -> {
                    syncModelToRunningClass(runningBytes);
                    compileToolbar.showPatched();
                    scheduleToolbarHide(2000);
                },
                err -> compileToolbar.showPatchFailed(err.getMessage()));
    }

    /**
     * Replaces the in-memory class model with exactly what is now running in the JVM after a patch, so the
     * bytecode view and the next recompile baseline stay identical to the live class. A refresh failure must
     * not fail an already-applied patch, so it is swallowed.
     */
    private void syncModelToRunningClass(byte[] runningBytes) {
        try {
            classEntry.updateClassFile(new com.tonic.parser.ClassFile(new java.io.ByteArrayInputStream(runningBytes)));
        } catch (Exception ignored) {
            // The patch already succeeded; keeping the stale model is acceptable.
        }
    }

    private void scheduleToolbarHide(int delayMs) {
        javax.swing.Timer hideTimer = new javax.swing.Timer(delayMs, evt -> {
            if (!SourceCodeView.this.dirty) {
                compileToolbar.hideToolbar();
            }
        });
        hideTimer.setRepeats(false);
        hideTimer.start();
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

        // Handle double-click line activation (dual view cross-pane linking)
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 2
                        || (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
                        || onLineActivated == null) {
                    return;
                }
                try {
                    int offset = textArea.viewToModel2D(e.getPoint());
                    int line = textArea.getLineOfOffset(offset);
                    if (line >= 0) {
                        onLineActivated.accept(line);
                    }
                } catch (Exception ex) {
                    // Ignore
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

    private void setupUsageLensMouseHandling() {
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 1
                        || (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    return;
                }
                UsageLens.LensEntry lens = lensOverlay.hitTest(e.getPoint());
                if (lens != null) {
                    postFindUsages(lens);
                }
            }
        });
        textArea.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    return;
                }
                boolean overLens = lensOverlay.hitTest(e.getPoint()) != null;
                textArea.setCursor(Cursor.getPredefinedCursor(
                        overLens ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }
        });
    }

    /**
     * Recomputes the usage-count lenses on a background worker: ensures the xref database exists
     * (built once per project, the same database Find Usages uses), counts usages for each emitted
     * method, field, and the class with the identical filtered query, then places entries from the
     * decompiler's member spans. Cleared instead when lenses are disabled, annotations are filtered
     * (line numbers shift), the source has been edited, or span data is unavailable.
     */
    private void scheduleUsageLensUpdate() {
        int generation = ++lensGeneration;
        Map<String, DecompileResult.MethodSpan> methodSpans = classEntry.getMethodSpans();
        Map<String, DecompileResult.MemberSpan> fieldSpans = classEntry.getFieldSpans();
        DecompileResult.MemberSpan classSpan = classEntry.getClassSpan();
        if (!usageLensEnabled || omitAnnotations || dirty || projectModel == null || methodSpans == null) {
            lensOverlay.clear();
            textArea.repaint();
            return;
        }
        String className = classEntry.getClassName();
        ProjectModel project = projectModel;
        List<MethodEntryModel> methods = classEntry.getMethods();
        List<FieldEntryModel> fields = classEntry.getFields();
        new SwingWorker<List<UsageLens.LensTarget>, Void>() {
            @Override
            protected List<UsageLens.LensTarget> doInBackground() {
                if (project.getXrefDatabase() == null || project.getXrefDatabase().isEmpty()) {
                    EventBus.getInstance().post(new StatusMessageEvent(this, "Building cross-reference database..."));
                    XrefQueryService.ensureDatabase(project);
                    EventBus.getInstance().post(new StatusMessageEvent(this, "Cross-reference database ready."));
                }
                List<UsageLens.LensTarget> targets = new ArrayList<>();
                for (MethodEntryModel method : methods) {
                    DecompileResult.MemberSpan span = methodSpans.get(method.getName() + method.getDescriptor());
                    if (span != null) {
                        int count = XrefQueryService.getUsages(project, FindUsagesEvent.TargetType.METHOD,
                                className, method.getName(), method.getDescriptor()).size();
                        targets.add(new UsageLens.LensTarget(FindUsagesEvent.TargetType.METHOD,
                                method.getName(), method.getDescriptor(), span, count));
                    }
                }
                if (fieldSpans != null) {
                    for (FieldEntryModel field : fields) {
                        DecompileResult.MemberSpan span = fieldSpans.get(field.getName() + field.getDescriptor());
                        if (span != null) {
                            int count = XrefQueryService.getUsages(project, FindUsagesEvent.TargetType.FIELD,
                                    className, field.getName(), field.getDescriptor()).size();
                            targets.add(new UsageLens.LensTarget(FindUsagesEvent.TargetType.FIELD,
                                    field.getName(), field.getDescriptor(), span, count));
                        }
                    }
                }
                if (classSpan != null) {
                    int count = XrefQueryService.getUsages(project, FindUsagesEvent.TargetType.CLASS,
                            className, null, null).size();
                    targets.add(new UsageLens.LensTarget(FindUsagesEvent.TargetType.CLASS,
                            className, null, classSpan, count));
                }
                return targets;
            }

            @Override
            protected void done() {
                if (generation != lensGeneration || dirty) {
                    return;
                }
                try {
                    List<UsageLens.LensTarget> targets = get();
                    String[] lines = textArea.getText().split("\n", -1);
                    lensOverlay.setEntries(UsageLens.compute(lines, targets));
                    textArea.repaint();
                } catch (Exception e) {
                    // Leave existing lenses untouched
                }
            }
        }.execute();
    }

    /** Opens Find Usages for the member a lens belongs to - the same event the navigator posts. */
    private void postFindUsages(UsageLens.LensEntry lens) {
        String className = classEntry.getClassName();
        switch (lens.targetType) {
            case METHOD:
                EventBus.getInstance().post(FindUsagesEvent.forMethod(
                        this, className, lens.memberName, lens.memberDescriptor));
                break;
            case FIELD:
                EventBus.getInstance().post(FindUsagesEvent.forField(
                        this, className, lens.memberName, lens.memberDescriptor));
                break;
            case CLASS:
                EventBus.getInstance().post(FindUsagesEvent.forClass(this, className));
                break;
        }
    }

    /** Enables or disables the usage-count lenses, recomputing or clearing them immediately. */
    public void setUsageLensEnabled(boolean enabled) {
        this.usageLensEnabled = enabled;
        scheduleUsageLensUpdate();
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

        // Run Code Analysis
        JMenuItem codeAnalysisItem = createMenuItem("Run Code Analysis", Icons.getIcon("analyze"));
        codeAnalysisItem.addActionListener(ev -> runCodeAnalysis());
        menu.add(codeAnalysisItem);

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

    private void runCodeAnalysis() {
        java.awt.Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            ((MainFrame) parent).runCodeAnalysis();
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

    /**
     * Adds a clickable green Run icon to the gutter on the {@code main} method's line (static-analysis mode
     * only - hidden while attached, and when annotations are omitted since that shifts source lines).
     */
    private void updateRunGutterIcons() {
        Gutter gutter = scrollPane.getGutter();
        for (GutterIconInfo info : runIcons) {
            gutter.removeTrackingIcon(info);
        }
        runIcons.clear();
        runLines.clear();

        if (omitAnnotations || classEntry.getMethodSpans() == null || !classEntry.hasMainMethod()
                || com.tonic.ui.live.LiveAttachService.getInstance().isAttached()) {
            return;
        }
        DecompileResult.MethodSpan span = classEntry.getMethodSpans().get("main([Ljava/lang/String;)V");
        if (span == null) {
            return;
        }
        int line = span.getStartLine();
        try {
            runIcons.add(gutter.addLineTrackingIcon(line - 1, Icons.getIcon("run"), "Run main()"));
            runLines.add(line);
        } catch (BadLocationException ignored) {
        }
    }

    private void runMainViaMainFrame() {
        java.awt.Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            ((MainFrame) parent).runMainClass(classEntry);
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
        if (!omitAnnotations && methodDesc != null && classEntry.getMethodSpans() != null) {
            DecompileResult.MethodSpan span = classEntry.getMethodSpans().get(methodName + methodDesc);
            if (span != null) {
                highlightAndScrollToLine(span.getStartLine() - 1);
                return;
            }
        }

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
        if (!omitAnnotations && classEntry.getFieldSpans() != null) {
            DecompileResult.MemberSpan span = fieldSpanByName(fieldName);
            if (span != null) {
                highlightAndScrollToLine(span.getStartLine() - 1);
                return;
            }
        }

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
        highlightAndScrollToLine(lineNumber, JStudioTheme.getLineHighlight());
    }

    private void highlightAndScrollToLine(int lineNumber, Color highlightColor) {
        clearHighlight();
        try {
            currentLineHighlight = textArea.addLineHighlight(lineNumber, highlightColor);

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
     * Highlight a 0-based line with the dual view's visible link color, used for cross-pane linking
     * so the linked line stands out from the faint current-line highlight on the adjacent caret line.
     */
    public void highlightLinkedLine(int lineNumber) {
        highlightAndScrollToLine(lineNumber, JStudioTheme.getLinkHighlight());
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
     * Registers a listener fired with the 0-based line on a plain double-click, used by the dual view
     * to drive cross-pane highlighting. Ctrl+Click navigation and the context menu are unaffected.
     */
    public void setOnLineActivated(IntConsumer onLineActivated) {
        this.onLineActivated = onLineActivated;
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
        JavaEditorFactory.applyTheme(textArea, scrollPane);
        repaint();
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
            updateRunGutterIcons();
            scheduleUsageLensUpdate();
            return;
        }

        if (loaded) {
            return;
        }

        cancelCurrentWorker();
        textArea.setText("");
        loadingOverlay.showLoading("Decompiling " + classEntry.getSimpleName() + "...");

        currentWorker = new SwingWorker<>() {
            private DecompileResult decompileResult;

            @Override
            protected String doInBackground() {
                try {
                    DecompileResult result = new ClassDecompiler(classEntry.getClassFile()).decompileWithLineMap();
                    decompileResult = result;
                    return result.getSource();
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
                    if (decompileResult != null) {
                        classEntry.setDecompilationCache(source, decompileResult.getLineMaps(),
                                decompileResult.getMethodSpans(), decompileResult.getFieldSpans(),
                                decompileResult.getClassSpan());
                    } else {
                        classEntry.setDecompilationCache(source);
                    }
                    String textToSet = omitAnnotations ? filterAnnotations(source) : source;
                    applyTextToEditor(textToSet);
                    loaded = true;
                    loadingOverlay.hideLoading();
                    updateCommentGutterIcons();
                    scheduleUsageLensUpdate();
                    Runnable navigation = pendingNavigation;
                    pendingNavigation = null;
                    if (navigation != null) {
                        navigation.run();
                    }
                } catch (Exception e) {
                    pendingNavigation = null;
                    loadingOverlay.hideLoading();
                    textArea.setText("// Failed to decompile: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    /**
     * Scrolls to the source line containing the statement at the given bytecode offset and selects
     * the referenced token on it. Resolution uses the decompiler's per-method offset-to-line map:
     * the ceiling entry is preferred (inlined expressions are emitted by a later-offset consumer
     * statement) with the floor entry second, verified by token presence; the lines between the two
     * candidates are scanned when neither matches. If a decompile is in flight the navigation is
     * deferred until its text lands. Returns false when no line map is available (plain cached
     * source, annotation filtering active) so callers can fall back to method-level navigation.
     */
    public boolean scrollToSourceOffset(String methodName, String methodDesc, int pc, String selectToken) {
        if (!loaded) {
            refresh();
        }
        if (currentWorker != null && !currentWorker.isDone()) {
            pendingNavigation = () -> applyScrollToSourceOffset(methodName, methodDesc, pc, selectToken);
            return true;
        }
        return applyScrollToSourceOffset(methodName, methodDesc, pc, selectToken);
    }

    private boolean applyScrollToSourceOffset(String methodName, String methodDesc, int pc, String selectToken) {
        if (omitAnnotations || pc < 0) {
            return false;
        }
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        if (maps == null) {
            return false;
        }
        NavigableMap<Integer, Integer> lineMap = maps.get(methodName + methodDesc);
        if (lineMap == null || lineMap.isEmpty()) {
            return false;
        }
        Map.Entry<Integer, Integer> ceiling = lineMap.ceilingEntry(pc);
        Map.Entry<Integer, Integer> floor = lineMap.floorEntry(pc);
        int primary = ceiling != null ? ceiling.getValue() : floor.getValue();
        int secondary = floor != null ? floor.getValue() : primary;

        int line = pickLineContaining(selectToken, primary, secondary);
        if (line < 0) {
            line = primary;
        }
        highlightAndScrollToLine(line - 1);
        selectTokenOnLine(line - 1, selectToken);
        return true;
    }

    /**
     * Picks, from the two candidate lines and the span between them, the first 1-based line whose
     * text contains the token (call form {@code token(} preferred), or -1 when none does.
     */
    private int pickLineContaining(String token, int primary, int secondary) {
        if (token == null || token.isEmpty()) {
            return -1;
        }
        String callForm = token + "(";
        if (lineContains(primary, callForm)) return primary;
        if (lineContains(secondary, callForm)) return secondary;
        if (lineContains(primary, token)) return primary;
        if (lineContains(secondary, token)) return secondary;
        int from = Math.min(primary, secondary);
        int to = Math.max(primary, secondary);
        for (int l = from; l <= to; l++) {
            if (lineContains(l, callForm) || lineContains(l, token)) {
                return l;
            }
        }
        return -1;
    }

    private boolean lineContains(int oneBasedLine, String token) {
        return lineText(oneBasedLine).contains(token);
    }

    private String lineText(int oneBasedLine) {
        try {
            int line = oneBasedLine - 1;
            if (line < 0 || line >= textArea.getLineCount()) {
                return "";
            }
            int start = textArea.getLineStartOffset(line);
            int end = textArea.getLineEndOffset(line);
            return textArea.getText(start, end - start);
        } catch (BadLocationException e) {
            return "";
        }
    }

    private void selectTokenOnLine(int zeroBasedLine, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            int start = textArea.getLineStartOffset(zeroBasedLine);
            int end = textArea.getLineEndOffset(zeroBasedLine);
            String text = textArea.getText(start, end - start);
            int idx = bestTokenIndex(text, token);
            if (idx >= 0) {
                textArea.select(start + idx, start + idx + token.length());
                textArea.getCaret().setSelectionVisible(true);
                textArea.requestFocusInWindow();
            }
        } catch (BadLocationException e) {
            // Leave the line highlight as the navigation result
        }
    }

    /**
     * The index of the token occurrence to select on a line, preferring a real code reference over an
     * incidental match inside a string/char literal (e.g. the {@code "IDK_LOL: "} in
     * {@code println("IDK_LOL: " + obj.IDK_LOL)}). Occurrences inside literals are skipped; a member
     * access ({@code .token}) or call ({@code token(}) wins; otherwise the first non-literal whole-word
     * match; finally the first raw match so a token that only appears in a literal still selects.
     */
    private static int bestTokenIndex(String text, String token) {
        int firstWord = -1;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1)) {
            if (isInsideLiteral(text, i)) {
                continue;
            }
            char before = i > 0 ? text.charAt(i - 1) : '\0';
            int afterPos = i + token.length();
            char after = afterPos < text.length() ? text.charAt(afterPos) : '\0';
            if (Character.isJavaIdentifierPart(before) || Character.isJavaIdentifierPart(after)) {
                continue;
            }
            if (before == '.' || after == '(') {
                return i;
            }
            if (firstWord < 0) {
                firstWord = i;
            }
        }
        return firstWord >= 0 ? firstWord : text.indexOf(token);
    }

    /** Whether index {@code i} in the line falls inside a double- or single-quoted literal. */
    private static boolean isInsideLiteral(String text, int i) {
        boolean inString = false;
        boolean inChar = false;
        for (int j = 0; j < i && j < text.length(); j++) {
            char c = text.charAt(j);
            if ((inString || inChar) && c == '\\') {
                j++;
                continue;
            }
            if (c == '"' && !inChar) {
                inString = !inString;
            } else if (c == '\'' && !inString) {
                inChar = !inChar;
            }
        }
        return inString || inChar;
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private void applyTextToEditor(String text) {
        ignoreDocumentChanges = true;
        lensOverlay.clear();
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
            scheduleUsageLensUpdate();
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
        DeclarationInfo fromSpans = declarationFromSpans(lineNumber);
        if (fromSpans != null) {
            return fromSpans;
        }
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

    /**
     * Resolves the declaration on a 1-based line from the decompiler's member spans (a line belongs
     * to the member whose span contains it; class/method/field spans are disjoint). Returns null when
     * spans are unavailable or annotations are filtered - both shift or remove line data - so callers
     * fall back to the regex extractors.
     */
    private DeclarationInfo declarationFromSpans(int lineNumber) {
        if (omitAnnotations) {
            return null;
        }
        DecompileResult.MemberSpan classSpan = classEntry.getClassSpan();
        if (classSpan != null && classSpan.contains(lineNumber)) {
            return new DeclarationInfo(DeclarationType.CLASS, classEntry.getSimpleName());
        }
        Map<String, DecompileResult.MethodSpan> methodSpans = classEntry.getMethodSpans();
        if (methodSpans != null) {
            for (MethodEntryModel method : classEntry.getMethods()) {
                DecompileResult.MethodSpan span = methodSpans.get(method.getName() + method.getDescriptor());
                if (span != null && span.contains(lineNumber)) {
                    return new DeclarationInfo(DeclarationType.METHOD, method.getName(), method.getDescriptor());
                }
            }
        }
        Map<String, DecompileResult.MemberSpan> fieldSpans = classEntry.getFieldSpans();
        if (fieldSpans != null) {
            for (FieldEntryModel field : classEntry.getFields()) {
                DecompileResult.MemberSpan span = fieldSpans.get(field.getName() + field.getDescriptor());
                if (span != null && span.contains(lineNumber)) {
                    return new DeclarationInfo(DeclarationType.FIELD, field.getName(), field.getDescriptor());
                }
            }
        }
        return null;
    }

    /** The field span for a field by its (class-unique) name, or null. */
    private DecompileResult.MemberSpan fieldSpanByName(String fieldName) {
        Map<String, DecompileResult.MemberSpan> fieldSpans = classEntry.getFieldSpans();
        if (fieldSpans == null) {
            return null;
        }
        for (FieldEntryModel field : classEntry.getFields()) {
            if (field.getName().equals(fieldName)) {
                return fieldSpans.get(field.getName() + field.getDescriptor());
            }
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
        "\\w+(?:<[^>]*>)?(?:\\[])*\\s+" +
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
        "\\w+(?:<[^>]*>)?(?:\\[])*\\s+" +
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
