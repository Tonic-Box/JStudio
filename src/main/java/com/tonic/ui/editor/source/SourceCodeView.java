package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.editor.SearchPanel;
import com.tonic.ui.editor.view.EditorView;
import com.tonic.ui.MainFrame;
import com.tonic.model.Bookmark;
import com.tonic.model.ClassEntryModel;
import com.tonic.ui.debug.Breakpoint;
import com.tonic.ui.debug.BreakpointGutterController;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectDatabaseService;
import com.tonic.live.LiveSession;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.live.LivePatch;
import com.tonic.ui.live.MethodBodyDiff;
import com.tonic.ui.theme.*;

import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.ByteArrayInputStream;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.IntConsumer;
import javax.swing.SwingUtilities;

/**
 * Source code view using RSyntaxTextArea for Java syntax highlighting.
 */
public class SourceCodeView extends JPanel implements ThemeChangeListener, EditorView {

    private final ClassEntryModel classEntry;
    /**
     * -- GETTER --
     *  Get the text area for direct access (e.g., for Ctrl+Click).
     */
    @Getter
    private static final int HINT_SCROLL_PADDING = 16;

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final SearchPanel searchPanel;
    private ProjectModel projectModel;

    private boolean loaded = false;
    private boolean omitAnnotations = false;
    private final SourceLineHighlighter lineHighlighter;
    private final CommentGutterController commentGutter;
    private final RunGutterController runGutter;
    private final BreakpointGutterController breakpointGutter;
    private final RuntimeHintController runtimeHints;
    private final LoadingOverlay loadingOverlay;
    private SwingWorker<String, Void> currentWorker;
    private Runnable pendingNavigation;
    private IntConsumer onLineActivated;

    private final UsageLensController usageLens;
    private final SourceNavigator navigator;

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
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                usageLens.paint((Graphics2D) g);
                if (runtimeHints != null) {
                    runtimeHints.paint((Graphics2D) g);
                }
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                if (runtimeHints != null) {
                    String t = runtimeHints.tooltipAt(e.getPoint());
                    if (t != null) {
                        return t;
                    }
                }
                return super.getToolTipText(e);
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (runtimeHints != null) {
                    int need = runtimeHints.requiredWidth();
                    if (need > 0 && need + HINT_SCROLL_PADDING > d.width) {
                        d.width = need + HINT_SCROLL_PADDING;
                    }
                }
                return d;
            }
        };
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setAntiAliasingEnabled(true);
        textArea.setEditable(true);
        textArea.setFont(JStudioTheme.getCodeFont(13));
        lineHighlighter = new SourceLineHighlighter(textArea);

        compilerParser = new SourceCompilerParser();
        compilerParser.setOriginalClass(classEntry.getClassFile());
        textArea.addParser(compilerParser);

        ErrorStrip errorStrip = new ErrorStrip(textArea);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setIconRowHeaderEnabled(true);
        scrollPane.setBorder(null);
        commentGutter = new CommentGutterController(this, scrollPane, classEntry);

        runGutter = new RunGutterController(textArea, scrollPane, classEntry,
                () -> omitAnnotations, this::runMainViaMainFrame);
        breakpointGutter = new BreakpointGutterController(textArea, scrollPane, new SourceBreakpointMapper(classEntry));
        runtimeHints = new RuntimeHintController(textArea, classEntry);
        usageLens = new UsageLensController(textArea, classEntry,
                () -> omitAnnotations, () -> dirty, () -> projectModel);
        navigator = new SourceNavigator(this, textArea, classEntry, lineHighlighter,
                () -> omitAnnotations, () -> projectModel);

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
        setupLineActivation();

        // Setup right-click context menu
        setupContextMenu();


        // Listen for comment changes to update gutter icons
        commentGutter.attach();

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
                compileToolbar.setLivePatchMode(LiveAttachService.getInstance().isAttached());
                usageLens.clear();
            } else {
                compilerParser.setEnabled(false);
                compileToolbar.hideToolbar();
                usageLens.scheduleUpdate();
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

        // Snapshot the pre-edit bytecode BEFORE compiling - the compiler mutates the live ClassFile in place, so
        // capturing later would record the already-changed bytes.
        com.tonic.service.history.LocalHistoryService.getInstance()
                .snapshot("Recompile " + classEntry.getSimpleName(), com.tonic.model.Snapshot.Trigger.RECOMPILE);

        String source = textArea.getText();
        // The source as it was before this edit - captured now because a successful recompile overwrites
        // originalSource below; the live patch diffs against it to graft only the methods that changed.
        final String baselineSource = originalSource;
        compileToolbar.showCompiling();

        SwingWorker<CompilationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CompilationResult doInBackground() {
                ClassPool pool = projectModel != null ? projectModel.getClassPool() : null;
                // Re-lower only the methods whose body actually changed; an empty/undeterminable diff falls back to
                // recompiling everything, so an edit is never silently dropped.
                Set<String> changed = MethodBodyDiff.changedMethods(
                        baselineSource, source, pool, classEntry.getClassName());
                return compilerParser.compile(source, pool, changed.isEmpty() ? null : changed);
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
                            projectModel.markDirty();
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

                        if (LiveAttachService.getInstance().isAttached()) {
                            livePatch(baselineSource, source);
                        } else {
                            scheduleToolbarHide(1500);
                        }
                    } else {
                        compileToolbar.showWithErrors(result.getErrorCount(), result.getWarningCount(), result.getErrors());
                    }
                } catch (Exception e) {
                    compileToolbar.showWithErrors(1, 0, Collections.singletonList(
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
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached()) {
            scheduleToolbarHide(1500);
            return;
        }
        final LiveSession session = svc.getSession();
        final String internalName = classEntry.getClassName();
        final ClassFile edited = classEntry.getClassFile();
        final ClassPool classPool = projectModel != null ? projectModel.getClassPool() : null;
        final Set<String> changedMethods = MethodBodyDiff.changedMethods(
                baselineSource, editedSource, classPool, internalName);
        compileToolbar.showPatching();
        SwingWorkers.run(
                () -> {
                    byte[] bytes = changedMethods.isEmpty()
                            ? LivePatch.buildRedefineBytes(session, internalName, edited)
                            : LivePatch.buildGraftedRedefineBytes(
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
            classEntry.updateClassFile(new ClassFile(new ByteArrayInputStream(runningBytes)));
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
    private void setupLineActivation() {
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

    /** Enables or disables the usage-count lenses, recomputing or clearing them immediately. */
    public void setUsageLensEnabled(boolean enabled) {
        usageLens.setEnabled(enabled);
    }

    /** Re-renders breakpoint dots and re-arms the gutter; called when the debug session connects/disconnects. */
    public void refreshBreakpointGutter() {
        breakpointGutter.updateIcons();
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

        // Add/Remove Breakpoint (only while the debugger is connected and the line is an executable location)
        Breakpoint breakpoint = breakpointGutter.breakpointAt(line);
        if (breakpoint != null) {
            JMenuItem bpItem = createMenuItem(
                    breakpointGutter.isSet(breakpoint) ? "Remove Breakpoint" : "Add Breakpoint", null);
            bpItem.addActionListener(ev -> breakpointGutter.toggle(breakpoint));
            menu.add(bpItem);
            menu.addSeparator();
        }

        // Copy
        JMenuItem copyItem = createMenuItem("Copy", Icons.getIcon("copy"));
        copyItem.addActionListener(ev -> copySelection());
        copyItem.setEnabled(textArea.getSelectedText() != null && !textArea.getSelectedText().isEmpty());
        menu.add(copyItem);

        menu.addSeparator();

        // Go to Definition
        JMenuItem gotoItem = createMenuItem("Go to Definition", null);
        String selectedText = textArea.getSelectedText();
        String wordAtCaret = navigator.getWordAtCaret();
        String targetIdentifier = (selectedText != null && !selectedText.isEmpty()) ? selectedText : wordAtCaret;
        gotoItem.addActionListener(ev -> {
            if (targetIdentifier != null && !targetIdentifier.isEmpty()) {
                navigator.navigateToIdentifier(targetIdentifier);
            }
        });
        gotoItem.setEnabled(targetIdentifier != null && !targetIdentifier.isEmpty());
        menu.add(gotoItem);

        // Rename and Find Usages (only for declarations on the current line)
        SourceNavigator.DeclarationInfo decl = navigator.getDeclarationAtLine(line);
        if (decl != null) {
            JMenuItem renameItem = createMenuItem("Rename " + decl.type.displayName + " '" + decl.name + "'...", null);
            renameItem.addActionListener(ev -> navigator.showRenameDialog(decl));
            menu.add(renameItem);

            JMenuItem findUsagesItem = createMenuItem("Find Usages of " + decl.type.displayName + " '" + decl.name + "'", Icons.getIcon("search"));
            findUsagesItem.addActionListener(ev -> navigator.findUsagesOfDeclaration(decl));
            menu.add(findUsagesItem);
        }

        menu.addSeparator();

        // Add Comment at Line
        JMenuItem commentItem = createMenuItem("Add Comment at Line " + line + "...", Icons.getIcon("comment"));
        commentItem.addActionListener(ev -> commentGutter.addCommentAtLine(line));
        menu.add(commentItem);

        // View Comments at Line
        int commentsAtLine = commentGutter.countCommentsAtLine(line);
        if (commentsAtLine > 0) {
            JMenuItem viewCommentItem = createMenuItem("View Comments at Line " + line + " (" + commentsAtLine + ")", null);
            viewCommentItem.addActionListener(ev -> commentGutter.viewCommentsAtLine(line));
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

    private JMenuItem createMenuItem(String text, Icon icon) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        if (icon != null) {
            item.setIcon(icon);
        }
        return item;
    }

    private void addBookmark() {
        String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            Bookmark bookmark = new Bookmark(classEntry.getClassName(), name.trim());
            ProjectDatabaseService.getInstance().addBookmark(bookmark);
        }
    }

    private void runCodeAnalysis() {
        Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            ((MainFrame) parent).runCodeAnalysis();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        runGutter.attach();
        breakpointGutter.attach();
        runtimeHints.attach();
    }

    @Override
    public void removeNotify() {
        runGutter.detach();
        breakpointGutter.detach();
        runtimeHints.detach();
        commentGutter.detach();
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.removeNotify();
    }

    private void runMainViaMainFrame() {
        Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            ((MainFrame) parent).runMainClass(classEntry);
        }
    }

    /**
     * Navigate to the definition of the identifier under the cursor.
     */
    private void highlightAndScrollToLine(int lineNumber) {
        lineHighlighter.highlightAndScrollToLine(lineNumber);
    }

    /** Highlight a specific line (0-based line number). */
    public void highlightLine(int lineNumber) {
        lineHighlighter.highlightLine(lineNumber);
    }

    /** Highlight a 0-based line with the dual view's link color, used for cross-pane linking. */
    public void highlightLinkedLine(int lineNumber) {
        lineHighlighter.highlightLinkedLine(lineNumber);
    }

    /** Clear the current line highlight. */
    public void clearHighlight() {
        lineHighlighter.clearHighlight();
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
        navigator.scrollToMethodDefinition(methodName, methodDesc);
    }

    /**
     * Scroll to and highlight a field declaration line.
     */
    public void scrollToFieldDeclaration(String fieldName) {
        if (!loaded) {
            refresh();
        }
        navigator.scrollToFieldDefinition(fieldName);
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
     * Forces a fresh decompile from the current bytecode, discarding any loaded/cached or edited source. Plain
     * {@link #refresh()} no-ops once the view has loaded (and re-displays the cache when present), so it cannot show
     * a class that was mutated externally (an AI rename, a script transform). Use this to rebuild the source view.
     */
    public void reload() {
        classEntry.invalidateDecompilationCache();
        loaded = false;
        dirty = false;
        refresh();
    }

    public void refresh() {
        String cachedSource = classEntry.getDecompilationCache();
        if (cachedSource != null) {
            String textToSet = omitAnnotations
                    ? AnnotationFilter.filter(classEntry.getClassFile(), cachedSource) : cachedSource;
            applyTextToEditor(textToSet);
            loaded = true;
            commentGutter.updateIcons();
            runGutter.updateIcons();
            breakpointGutter.updateIcons();
            usageLens.scheduleUpdate();
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
                    String textToSet = omitAnnotations
                    ? AnnotationFilter.filter(classEntry.getClassFile(), source) : source;
                    applyTextToEditor(textToSet);
                    loaded = true;
                    loadingOverlay.hideLoading();
                    commentGutter.updateIcons();
                    runGutter.updateIcons();
                    breakpointGutter.updateIcons();
                    usageLens.scheduleUpdate();
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

        int line = lineHighlighter.pickLineContaining(selectToken, primary, secondary);
        if (line < 0) {
            line = primary;
        }
        // Defer the scroll/select: when the class was just opened (cached source applied synchronously), the
        // text component isn't laid out yet, so an immediate scroll lands nowhere - the old "navigate twice"
        // bug. invokeLater runs it after the pending layout pass; it's harmless when already realized.
        final int target = line;
        SwingUtilities.invokeLater(() -> {
            highlightAndScrollToLine(target - 1);
            lineHighlighter.selectTokenOnLine(target - 1, selectToken);
        });
        return true;
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private void applyTextToEditor(String text) {
        ignoreDocumentChanges = true;
        usageLens.clear();
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
            String textToSet = omitAnnotations
                    ? AnnotationFilter.filter(classEntry.getClassFile(), source) : source;
            ignoreDocumentChanges = true;
            textArea.setText(textToSet);
            textArea.setCaretPosition(0);
            originalSource = textToSet;
            dirty = false;
            compileToolbar.hideToolbar();
            ignoreDocumentChanges = false;
            usageLens.scheduleUpdate();
        }
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

}
