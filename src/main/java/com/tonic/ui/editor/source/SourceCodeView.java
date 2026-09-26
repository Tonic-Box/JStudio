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
import com.tonic.model.MethodEntryModel;
import com.tonic.ui.debug.Breakpoint;
import com.tonic.ui.debug.BreakpointGutterController;
import com.tonic.model.ProjectModel;
import com.tonic.model.Snapshot;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.service.LocalVariableRenamer;
import com.tonic.service.ProjectDatabaseService;
import com.tonic.ui.dialog.RenameLocalDialog;
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
public class SourceCodeView extends JPanel implements ThemeChangeListener, EditorView
{

    private final ClassEntryModel classEntry;
    @Getter
    private static final int HINT_SCROLL_PADDING = 16;

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final SearchPanel searchPanel;
    private ProjectModel projectModel;

    private boolean loaded = false;
    private boolean omitAnnotations = false;
    private int[] annotationLineMap = null;
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
    private Runnable onRecompiled;

    /**
     * Creates the view for a class; the source is decompiled on the first refresh.
     *
     * @param classEntry the class to show
     */
    public SourceCodeView(ClassEntryModel classEntry)
    {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        textArea = new RSyntaxTextArea()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                usageLens.paint((Graphics2D) g);
                if (runtimeHints != null)
                {
                    runtimeHints.paint((Graphics2D) g);
                }
            }

            @Override
            public String getToolTipText(MouseEvent e)
            {
                if (runtimeHints != null)
                {
                    String t = runtimeHints.tooltipAt(e.getPoint());
                    if (t != null)
                    {
                        return t;
                    }
                }
                return super.getToolTipText(e);
            }

            @Override
            public Dimension getPreferredSize()
            {
                Dimension d = super.getPreferredSize();
                if (runtimeHints != null)
                {
                    int need = runtimeHints.requiredWidth();
                    if (need > 0 && need + HINT_SCROLL_PADDING > d.width)
                    {
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

        runGutter = new RunGutterController(textArea, scrollPane, classEntry, () -> omitAnnotations, this::runMainViaMainFrame);
        breakpointGutter = new BreakpointGutterController(textArea, scrollPane, new SourceBreakpointMapper(classEntry));
        runtimeHints = new RuntimeHintController(textArea, classEntry);
        usageLens = new UsageLensController(textArea, classEntry, () -> dirty, () -> projectModel, () -> annotationLineMap);
        navigator = new SourceNavigator(this, textArea, classEntry, lineHighlighter, () -> omitAnnotations, () -> projectModel);

        loadingOverlay = new LoadingOverlay();

        compileToolbar = new FloatingCompileToolbar(this::doRecompile, this::discardChanges);
        compileToolbar.setLineNavigator(line ->
        {
            if (line > 0)
            {
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

        applyTheme();

        setupLineActivation();

        setupContextMenu();

        commentGutter.attach();

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private void setupDocumentListener()
    {
        textArea.addPropertyChangeListener(RSyntaxTextArea.PARSER_NOTICES_PROPERTY, evt ->
        {
            if (dirty)
            {
                updateToolbarState();
            }
        });
        textArea.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                onSourceChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                onSourceChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                onSourceChanged();
            }
        });
    }

    private void onSourceChanged()
    {
        if (ignoreDocumentChanges)
        {
            return;
        }

        String currentText = textArea.getText();
        boolean nowDirty = originalSource != null && !currentText.equals(originalSource);

        if (nowDirty != dirty)
        {
            dirty = nowDirty;
            if (dirty)
            {
                compilerParser.setEnabled(true);
                compileToolbar.showModified();
                compileToolbar.setLivePatchMode(LiveAttachService.getInstance().isAttached());
                usageLens.clear();
            }
            else
            {
                compilerParser.setEnabled(false);
                compileToolbar.hideToolbar();
                usageLens.scheduleUpdate();
            }
        }

        if (dirty)
        {
            SwingUtilities.invokeLater(this::updateToolbarState);
        }
    }

    private void updateToolbarState()
    {
        int errorCount = compilerParser.getErrorCount();
        int warningCount = compilerParser.getWarningCount();
        compileToolbar.showWithErrors(errorCount, warningCount);
    }

    /**
     * Sets the callback run after a successful recompile.
     *
     * @param onRecompiled the callback, typically the owning tab refreshing its views
     */
    public void setOnRecompiled(Runnable onRecompiled)
    {
        this.onRecompiled = onRecompiled;
    }

    private void doRecompile()
    {
        if (!dirty)
        {
            return;
        }

        com.tonic.service.history.LocalHistoryService.getInstance()
                .snapshot("Recompile " + classEntry.getSimpleName(), com.tonic.model.Snapshot.Trigger.RECOMPILE);

        String source = textArea.getText();
        final String baselineSource = originalSource;
        compileToolbar.showCompiling();

        SwingWorker<CompilationResult, Void> worker = new SwingWorker<>()
        {
            @Override
            protected CompilationResult doInBackground()
            {
                ClassPool pool = projectModel != null ? projectModel.getClassPool() : null;
                Set<String> changed = MethodBodyDiff.changedMethods(baselineSource, source, pool, classEntry.getClassName());
                return compilerParser.compile(source, pool, changed.isEmpty() ? null : changed);
            }

            @Override
            protected void done()
            {
                try
                {
                    CompilationResult result = get();
                    if (result.isSuccess())
                    {
                        classEntry.updateClassFile(result.getCompiledClass());
                        classEntry.setDecompilationCache(source);
                        compilerParser.setOriginalClass(result.getCompiledClass());
                        if (projectModel != null)
                        {
                            projectModel.setXrefDatabase(null);
                            projectModel.markDirty();
                        }
                        originalSource = source;
                        dirty = false;
                        if (onRecompiled != null)
                        {
                            onRecompiled.run();
                        }
                        if (result.hasWarnings())
                        {
                            compileToolbar.showWithErrors(0, result.getWarningCount(), result.getErrors());
                        }
                        else
                        {
                            compileToolbar.showSuccess(result.getCompilationTimeMs());
                        }

                        if (LiveAttachService.getInstance().isAttached())
                        {
                            livePatch(baselineSource, source);
                        }
                        else
                        {
                            scheduleToolbarHide(1500);
                        }
                    }
                    else
                    {
                        compileToolbar.showWithErrors(result.getErrorCount(), result.getWarningCount(), result.getErrors());
                    }
                }
                catch (Exception e)
                {
                    compileToolbar.showWithErrors(1, 0, Collections.singletonList(CompilationError.error(1, 1, 0, 1, "Compilation failed: " + e.getMessage())));
                }
            }
        };

        worker.execute();
    }

    private void livePatch(String baselineSource, String editedSource)
    {
        LiveAttachService svc = LiveAttachService.getInstance();
        if (!svc.isAttached())
        {
            scheduleToolbarHide(1500);
            return;
        }
        final LiveSession session = svc.getSession();
        final String internalName = classEntry.getClassName();
        final ClassFile edited = classEntry.getClassFile();
        final ClassPool classPool = projectModel != null ? projectModel.getClassPool() : null;
        final Set<String> changedMethods = MethodBodyDiff.changedMethods(baselineSource, editedSource, classPool, internalName);
        compileToolbar.showPatching();
        SwingWorkers.run(() ->
        {
            byte[] bytes = changedMethods.isEmpty()
                    ? LivePatch.buildRedefineBytes(session, internalName, edited)
                    : LivePatch.buildGraftedRedefineBytes(session, internalName, edited, changedMethods);
            session.redefineClass(internalName, bytes);
            return bytes;
        }, runningBytes ->
        {
            syncModelToRunningClass(runningBytes);
            compileToolbar.showPatched();
            scheduleToolbarHide(2000);
        }, err -> compileToolbar.showPatchFailed(err.getMessage()));
    }

    private void syncModelToRunningClass(byte[] runningBytes)
    {
        try
        {
            classEntry.updateClassFile(new ClassFile(new ByteArrayInputStream(runningBytes)));
        }
        catch (Exception ignored)
        {
        }
    }

    private void scheduleToolbarHide(int delayMs)
    {
        javax.swing.Timer hideTimer = new javax.swing.Timer(delayMs, evt ->
        {
            if (!SourceCodeView.this.dirty)
            {
                compileToolbar.hideToolbar();
            }
        });
        hideTimer.setRepeats(false);
        hideTimer.start();
    }

    private void discardChanges()
    {
        if (originalSource != null)
        {
            ignoreDocumentChanges = true;
            textArea.setText(originalSource);
            textArea.setCaretPosition(0);
            ignoreDocumentChanges = false;
            dirty = false;
            compileToolbar.hideToolbar();
        }
    }

    @Override
    public void onThemeChanged(Theme newTheme)
    {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void setupLineActivation()
    {
        textArea.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 2
                        || (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
                        || onLineActivated == null)
                {
                    return;
                }
                try
                {
                    int offset = textArea.viewToModel2D(e.getPoint());
                    int line = textArea.getLineOfOffset(offset);
                    if (line >= 0)
                    {
                        onLineActivated.accept(line);
                    }
                }
                catch (Exception ex)
                {
                }
            }
        });
    }

    private void setupContextMenu()
    {
        textArea.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    showContextMenu(e);
                }
            }
        });
    }

    /**
     * Enables or disables the usage-count lenses, recomputing or clearing them immediately.
     *
     * @param enabled whether to show the lenses
     */
    public void setUsageLensEnabled(boolean enabled)
    {
        usageLens.setEnabled(enabled);
    }

    /** Re-renders breakpoint dots and re-arms the gutter; called when the debug session connects/disconnects. */
    public void refreshBreakpointGutter()
    {
        breakpointGutter.updateIcons();
    }

    private void showContextMenu(MouseEvent e)
    {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        int clickOffset = textArea.viewToModel2D(e.getPoint());
        int lineNumber = 1;
        try
        {
            lineNumber = textArea.getLineOfOffset(clickOffset) + 1;
        }
        catch (BadLocationException ex)
        {
        }
        final int line = lineNumber;

        Breakpoint breakpoint = breakpointGutter.breakpointAt(line);
        if (breakpoint != null)
        {
            JMenuItem bpItem = createMenuItem(breakpointGutter.isSet(breakpoint) ? "Remove Breakpoint" : "Add Breakpoint", null);
            bpItem.addActionListener(ev -> breakpointGutter.toggle(breakpoint));
            menu.add(bpItem);
            menu.addSeparator();
        }

        JMenuItem copyItem = createMenuItem("Copy", Icons.getIcon("copy"));
        copyItem.addActionListener(ev -> copySelection());
        copyItem.setEnabled(textArea.getSelectedText() != null && !textArea.getSelectedText().isEmpty());
        menu.add(copyItem);

        menu.addSeparator();

        JMenuItem gotoItem = createMenuItem("Go to Definition", null);
        String selectedText = textArea.getSelectedText();
        String wordAtCaret = navigator.getWordAtCaret();
        String targetIdentifier = (selectedText != null && !selectedText.isEmpty()) ? selectedText : wordAtCaret;
        gotoItem.addActionListener(ev ->
        {
            if (targetIdentifier != null && !targetIdentifier.isEmpty())
            {
                navigator.navigateToIdentifier(targetIdentifier);
            }
        });
        gotoItem.setEnabled(targetIdentifier != null && !targetIdentifier.isEmpty());
        menu.add(gotoItem);

        SourceNavigator.DeclarationInfo decl = navigator.getDeclarationAtLine(line);
        if (decl != null)
        {
            JMenuItem renameItem = createMenuItem("Rename " + decl.type.displayName + " '" + decl.name + "'...", null);
            renameItem.addActionListener(ev -> navigator.showRenameDialog(decl));
            menu.add(renameItem);

            JMenuItem findUsagesItem = createMenuItem("Find Usages of " + decl.type.displayName + " '" + decl.name + "'", Icons.getIcon("search"));
            findUsagesItem.addActionListener(ev -> navigator.findUsagesOfDeclaration(decl));
            menu.add(findUsagesItem);
        }

        String localWord = identifierAt(clickOffset);
        LocalVariableRenamer.Target localTarget =
                localWord != null ? LocalVariableRenamer.locate(classEntry, line, localWord) : null;
        if (localTarget != null)
        {
            JMenuItem renameLocal = createMenuItem("Rename local '" + localTarget.oldName + "'...", null);
            renameLocal.addActionListener(ev -> renameLocal(localTarget));
            menu.add(renameLocal);
        }

        menu.addSeparator();

        JMenuItem commentItem = createMenuItem("Add Comment at Line " + line + "...", Icons.getIcon("comment"));
        commentItem.addActionListener(ev -> commentGutter.addCommentAtLine(line));
        menu.add(commentItem);

        int commentsAtLine = commentGutter.countCommentsAtLine(line);
        if (commentsAtLine > 0)
        {
            JMenuItem viewCommentItem = createMenuItem("View Comments at Line " + line + " (" + commentsAtLine + ")", null);
            viewCommentItem.addActionListener(ev -> commentGutter.viewCommentsAtLine(line));
            menu.add(viewCommentItem);
        }

        menu.addSeparator();

        JMenuItem codeAnalysisItem = createMenuItem("Run Code Analysis", Icons.getIcon("analyze"));
        codeAnalysisItem.addActionListener(ev -> runCodeAnalysis());
        menu.add(codeAnalysisItem);

        menu.addSeparator();

        JMenuItem bookmarkItem = createMenuItem("Add Bookmark for This Class...", Icons.getIcon("bookmark"));
        bookmarkItem.addActionListener(ev -> addBookmark());
        menu.add(bookmarkItem);

        menu.show(textArea, e.getX(), e.getY());
    }

    private JMenuItem createMenuItem(String text, Icon icon)
    {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        if (icon != null)
        {
            item.setIcon(icon);
        }
        return item;
    }

    private String identifierAt(int offset)
    {
        try
        {
            String text = textArea.getText();
            if (offset < 0 || offset > text.length())
            {
                return null;
            }
            int start = offset;
            int end = offset;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1)))
            {
                start--;
            }
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end)))
            {
                end++;
            }
            return start < end ? text.substring(start, end) : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private void renameLocal(LocalVariableRenamer.Target target)
    {
        RenameLocalDialog dialog = new RenameLocalDialog(SwingUtilities.getWindowAncestor(this), target.oldName);
        dialog.setVisible(true);
        if (!dialog.isConfirmed())
        {
            return;
        }
        String newName = dialog.getNewName();
        if (newName.equals(target.oldName))
        {
            return;
        }
        if (LocalVariableRenamer.wouldConflict(classEntry, target, newName))
        {
            JOptionPane.showMessageDialog(this, "A local named '" + newName + "' is already in scope here.", "Rename Conflict", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int caretLine = 0;
        int caretCol = 0;
        try
        {
            int caretOffset = textArea.getCaretPosition();
            caretLine = textArea.getLineOfOffset(caretOffset);
            caretCol = caretOffset - textArea.getLineStartOffset(caretLine);
        }
        catch (BadLocationException ignored)
        {
        }
        Point viewPos = scrollPane.getViewport().getViewPosition();

        LocalHistoryService.getInstance().snapshot("Rename local " + target.oldName, Snapshot.Trigger.RENAME);
        if (!LocalVariableRenamer.rename(classEntry, target, newName))
        {
            JOptionPane.showMessageDialog(this, "Could not rename the local variable.", "Rename Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int line = caretLine;
        final int col = caretCol;
        pendingNavigation = () -> restoreCaretAndScroll(line, col, viewPos);
        reload();
        if (onRecompiled != null)
        {
            onRecompiled.run();
        }
    }

    private void restoreCaretAndScroll(int line, int col, Point viewPos)
    {
        try
        {
            int targetLine = Math.max(0, Math.min(line, textArea.getLineCount() - 1));
            int lineStart = textArea.getLineStartOffset(targetLine);
            int lineEnd = textArea.getLineEndOffset(targetLine);
            textArea.setCaretPosition(Math.min(lineStart + col, Math.max(lineStart, lineEnd - 1)));
        }
        catch (BadLocationException ignored)
        {
        }
        SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(viewPos));
    }

    private void addBookmark()
    {
        String name = JOptionPane.showInputDialog(this, "Bookmark name:", "Add Bookmark", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty())
        {
            Bookmark bookmark = new Bookmark(classEntry.getClassName(), name.trim());
            ProjectDatabaseService.getInstance().addBookmark(bookmark);
        }
    }

    private void runCodeAnalysis()
    {
        Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame))
        {
            parent = parent.getParent();
        }
        if (parent != null)
        {
            ((MainFrame) parent).runCodeAnalysis();
        }
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        runGutter.attach();
        breakpointGutter.attach();
        runtimeHints.attach();
    }

    @Override
    public void removeNotify()
    {
        runGutter.detach();
        breakpointGutter.detach();
        runtimeHints.detach();
        commentGutter.detach();
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.removeNotify();
    }

    private void runMainViaMainFrame()
    {
        Container parent = getParent();
        while (parent != null && !(parent instanceof MainFrame))
        {
            parent = parent.getParent();
        }
        if (parent != null)
        {
            ((MainFrame) parent).runMainClass(classEntry);
        }
    }

    private void highlightAndScrollToLine(int lineNumber)
    {
        lineHighlighter.highlightAndScrollToLine(lineNumber);
    }

    /**
     * Highlights a line.
     *
     * @param lineNumber the 0-based line
     */
    public void highlightLine(int lineNumber)
    {
        lineHighlighter.highlightLine(lineNumber);
    }

    /**
     * Highlights a line in the dual view's link color, for cross-pane linking.
     *
     * @param lineNumber the 0-based line
     */
    public void highlightLinkedLine(int lineNumber)
    {
        lineHighlighter.highlightLinkedLine(lineNumber);
    }

    /** Clear the current line highlight. */
    public void clearHighlight()
    {
        lineHighlighter.clearHighlight();
    }

    /**
     * Sets the listener fired on a plain double-click, which the dual view uses for cross-pane highlighting.
     *
     * @param onLineActivated receives the 0-based line double-clicked
     */
    public void setOnLineActivated(IntConsumer onLineActivated)
    {
        this.onLineActivated = onLineActivated;
    }

    /**
     * Finds the method whose source contains the caret.
     *
     * @return the method, or null where the caret is outside every method
     */
    public MethodEntryModel methodAtCaret()
    {
        SourceNavigator.DeclarationInfo declaration = navigator.getDeclarationAtLine(textArea.getCaretLineNumber() + 1);
        if (declaration == null || declaration.type != SourceNavigator.DeclarationType.METHOD)
        {
            return null;
        }
        for (MethodEntryModel method : classEntry.getMethods())
        {
            if (method.getName().equals(declaration.name) && (declaration.descriptor == null || declaration.descriptor.equals(method.getDescriptor())))
            {
                return method;
            }
        }
        return null;
    }

    /**
     * Scrolls to and highlights a method's declaration, loading the source first if needed.
     *
     * @param methodName the method name
     * @param methodDesc the method descriptor
     */
    public void scrollToMethodDeclaration(String methodName, String methodDesc)
    {
        if (!loaded)
        {
            refresh();
        }
        navigator.scrollToMethodDefinition(methodName, methodDesc);
    }

    /**
     * Scrolls to and highlights a field's declaration, loading the source first if needed.
     *
     * @param fieldName the field name
     */
    public void scrollToFieldDeclaration(String fieldName)
    {
        if (!loaded)
        {
            refresh();
        }
        navigator.scrollToFieldDefinition(fieldName);
    }

    /**
     * Sets the project used to resolve navigation into other classes.
     *
     * @param projectModel the open project
     */
    public void setProjectModel(ProjectModel projectModel)
    {
        this.projectModel = projectModel;
    }

    private void applyTheme()
    {
        setBackground(JStudioTheme.getBgTertiary());
        JavaEditorFactory.applyTheme(textArea, scrollPane);
        repaint();
    }

    /** Discards the cached and edited source and decompiles the class again from its current bytecode. */
    public void reload()
    {
        classEntry.invalidateDecompilationCache();
        loaded = false;
        dirty = false;
        refresh();
    }

    /** Shows the cached source if there is one, otherwise decompiles in the background unless already loaded. */
    public void refresh()
    {
        String cachedSource = classEntry.getDecompilationCache();
        if (cachedSource != null)
        {
            String textToSet = applyAnnotationFilter(cachedSource);
            applyTextToEditor(textToSet);
            loaded = true;
            commentGutter.updateIcons();
            runGutter.updateIcons();
            breakpointGutter.updateIcons();
            usageLens.scheduleUpdate();
            return;
        }

        if (loaded)
        {
            return;
        }

        cancelCurrentWorker();
        textArea.setText("");
        loadingOverlay.showLoading("Decompiling " + classEntry.getSimpleName() + "...");

        currentWorker = new SwingWorker<>()
        {
            private DecompileResult decompileResult;

            @Override
            protected String doInBackground()
            {
                try
                {
                    DecompileResult result = new ClassDecompiler(classEntry.getClassFile()).decompileWithLineMap();
                    decompileResult = result;
                    return result.getSource();
                }
                catch (Exception e)
                {
                    return "// Decompilation failed: " + e.getMessage() + "\n\n" +
                            "// Class: " + classEntry.getClassName() + "\n" +
                            "// Error: " + e.getClass().getSimpleName() + "\n";
                }
            }

            @Override
            protected void done()
            {
                if (isCancelled())
                {
                    loadingOverlay.hideLoading();
                    return;
                }
                try
                {
                    String source = get();
                    if (decompileResult != null)
                    {
                        classEntry.setDecompilationCache(source, decompileResult.getLineMaps(), decompileResult.getMethodSpans(), decompileResult.getFieldSpans(), decompileResult.getClassSpan());
                    }
                    else
                    {
                        classEntry.setDecompilationCache(source);
                    }
                    String textToSet = applyAnnotationFilter(source);
                    applyTextToEditor(textToSet);
                    loaded = true;
                    loadingOverlay.hideLoading();
                    commentGutter.updateIcons();
                    runGutter.updateIcons();
                    breakpointGutter.updateIcons();
                    usageLens.scheduleUpdate();
                    Runnable navigation = pendingNavigation;
                    pendingNavigation = null;
                    if (navigation != null)
                    {
                        navigation.run();
                    }
                }
                catch (Exception e)
                {
                    pendingNavigation = null;
                    loadingOverlay.hideLoading();
                    textArea.setText("// Failed to decompile: " + e.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    /**
     * Scrolls to the source line of the statement at a bytecode offset and selects a token on it, deferring until an in-flight decompile finishes.
     *
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset
     * @param selectToken the text to select on the line
     * @return false when no line map is available (annotations hidden, or no map for the method), so the caller can fall back to method-level navigation; true otherwise, including when deferred
     */
    public boolean scrollToSourceOffset(String methodName, String methodDesc, int pc, String selectToken)
    {
        if (!loaded)
        {
            refresh();
        }
        if (currentWorker != null && !currentWorker.isDone())
        {
            pendingNavigation = () -> applyScrollToSourceOffset(methodName, methodDesc, pc, selectToken);
            return true;
        }
        return applyScrollToSourceOffset(methodName, methodDesc, pc, selectToken);
    }

    private boolean applyScrollToSourceOffset(String methodName, String methodDesc, int pc, String selectToken)
    {
        if (omitAnnotations || pc < 0)
        {
            return false;
        }
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        if (maps == null)
        {
            return false;
        }
        NavigableMap<Integer, Integer> lineMap = maps.get(methodName + methodDesc);
        if (lineMap == null || lineMap.isEmpty())
        {
            return false;
        }
        Map.Entry<Integer, Integer> ceiling = lineMap.ceilingEntry(pc);
        Map.Entry<Integer, Integer> floor = lineMap.floorEntry(pc);
        int primary = ceiling != null ? ceiling.getValue() : floor.getValue();
        int secondary = floor != null ? floor.getValue() : primary;

        int line = lineHighlighter.pickLineContaining(selectToken, primary, secondary);
        if (line < 0)
        {
            line = primary;
        }
        final int target = line;
        SwingUtilities.invokeLater(() ->
        {
            highlightAndScrollToLine(target - 1);
            lineHighlighter.selectTokenOnLine(target - 1, selectToken);
        });
        return true;
    }

    private void cancelCurrentWorker()
    {
        if (currentWorker != null && !currentWorker.isDone())
        {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private void applyTextToEditor(String text)
    {
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

        SwingUtilities.invokeLater(() ->
        {
            textArea.setBracketMatchingEnabled(true);
            textArea.setAnimateBracketMatching(true);
            textArea.setPaintMatchedBracketPair(true);
            if (text.length() < 100000)
            {
                textArea.setCodeFoldingEnabled(true);
            }
        });
    }

    /**
     * Sets whether annotations are hidden, re-showing the loaded source and discarding edits.
     *
     * @param omit whether to hide annotations
     */
    public void setOmitAnnotations(boolean omit)
    {
        this.omitAnnotations = omit;
        if (loaded && classEntry.getDecompilationCache() != null)
        {
            String source = classEntry.getDecompilationCache();
            String textToSet = applyAnnotationFilter(source);
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

    private String applyAnnotationFilter(String source)
    {
        if (!omitAnnotations)
        {
            annotationLineMap = null;
            return source;
        }
        AnnotationFilter.Filtered filtered = AnnotationFilter.filterWithMap(classEntry.getClassFile(), source);
        annotationLineMap = filtered.lineMap;
        return filtered.text;
    }

    /**
     * Returns the editor's text, including unsaved edits.
     *
     * @return the current text
     */
    public String getText()
    {
        return textArea.getText();
    }

    /** Copies the selection to the clipboard, if any. */
    public void copySelection()
    {
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.isEmpty())
        {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    /**
     * Moves the caret to a line and focuses the editor; an out-of-range line is ignored.
     *
     * @param line the 1-based line
     */
    public void goToLine(int line)
    {
        try
        {
            int offset = textArea.getLineStartOffset(line - 1);
            textArea.setCaretPosition(offset);
            textArea.requestFocus();
        }
        catch (Exception e)
        {
        }
    }

    /** Shows the inline search panel. */
    public void showFindDialog()
    {
        searchPanel.showPanel();
    }

    /**
     * Returns the editor's selection.
     *
     * @return the selected text, or null when nothing is selected
     */
    public String getSelectedText()
    {
        return textArea.getSelectedText();
    }

    /**
     * Finds and selects the next case-insensitive match of the text.
     *
     * @param text the text to find; null or empty does nothing
     */
    public void scrollToText(String text)
    {
        if (text == null || text.isEmpty()) return;

        SearchContext context = new SearchContext(text);
        context.setMatchCase(false);
        context.setWholeWord(false);
        SearchEngine.find(textArea, context);
    }

    /**
     * Sets the code font size.
     *
     * @param size the point size
     */
    public void setFontSize(int size)
    {
        textArea.setFont(JStudioTheme.getCodeFont(size));
    }

    /**
     * Turns word wrap on or off.
     *
     * @param enabled whether to wrap
     */
    public void setWordWrap(boolean enabled)
    {
        textArea.setLineWrap(enabled);
        textArea.setWrapStyleWord(enabled);
    }

}
