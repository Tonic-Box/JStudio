package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.MainFrame;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Owns the source editor's symbol navigation: Ctrl+Click go-to-definition, identifier/declaration resolution
 * (across the current class then the project), scroll-to-declaration, and the rename / find-usages actions for the
 * declaration under the caret. Collaborators (text area, class entry, the line highlighter, the project model and
 * the "omit annotations" flag) are injected; cross-cutting events use the host component as their source.
 */
final class SourceNavigator {

    /** The kind of declaration recognised on a line, with its menu display name. */
    enum DeclarationType {
        CLASS("Class"),
        METHOD("Method"),
        FIELD("Field");

        final String displayName;

        DeclarationType(String displayName) {
            this.displayName = displayName;
        }
    }

    /** A resolved declaration (kind, name, and - for methods - descriptor) at a source line. */
    static class DeclarationInfo {
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

    private final Component host;
    private final RSyntaxTextArea textArea;
    private final ClassEntryModel classEntry;
    private final SourceLineHighlighter highlighter;
    private final BooleanSupplier omitAnnotations;
    private final Supplier<ProjectModel> projectModel;

    SourceNavigator(Component host, RSyntaxTextArea textArea, ClassEntryModel classEntry,
                    SourceLineHighlighter highlighter, BooleanSupplier omitAnnotations,
                    Supplier<ProjectModel> projectModel) {
        this.host = host;
        this.textArea = textArea;
        this.classEntry = classEntry;
        this.highlighter = highlighter;
        this.omitAnnotations = omitAnnotations;
        this.projectModel = projectModel;
        installMouseHandling();
    }

    private void installMouseHandling() {
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

        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    navigateToDefinition(e);
                }
            }
        });
    }

    private void navigateToDefinition(MouseEvent e) {
        if (projectModel.get() == null) {
            return;
        }
        try {
            int offset = textArea.viewToModel2D(e.getPoint());
            if (offset < 0) {
                return;
            }
            String text = textArea.getText();
            String identifier = SourceDeclarationParser.extractIdentifierAt(text, offset);
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
    void navigateToIdentifier(String identifier) {
        ProjectModel project = projectModel.get();
        if (project == null || identifier == null || identifier.isEmpty()) {
            return;
        }
        try {
            ClassFile currentClassFile = classEntry.getClassFile();
            for (MethodEntry method : currentClassFile.getMethods()) {
                if (method.getName().equals(identifier)) {
                    scrollToMethodDefinition(method.getName(), method.getDesc());
                    return;
                }
            }

            for (FieldEntry field : currentClassFile.getFields()) {
                if (field.getName().equals(identifier)) {
                    scrollToFieldDefinition(field.getName());
                    return;
                }
            }

            ClassEntryModel targetClass = findClassBySimpleName(identifier);
            if (targetClass != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(host, targetClass));
                return;
            }

            targetClass = project.getClass(identifier.replace('.', '/'));
            if (targetClass != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(host, targetClass));
                return;
            }

            for (ClassEntryModel cls : project.getAllClasses()) {
                for (MethodEntry method : cls.getClassFile().getMethods()) {
                    if (method.getName().equals(identifier)) {
                        EventBus.getInstance().post(new ClassSelectedEvent(host, cls));
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Scroll to a method definition in the source view and highlight the line. Looks for actual method
     * declarations, not call sites. Uses the descriptor to match the correct overload.
     */
    void scrollToMethodDefinition(String methodName, String methodDesc) {
        if (!omitAnnotations.getAsBoolean() && methodDesc != null && classEntry.getMethodSpans() != null) {
            DecompileResult.MethodSpan span = classEntry.getMethodSpans().get(methodName + methodDesc);
            if (span != null) {
                highlighter.highlightAndScrollToLine(span.getStartLine() - 1);
                return;
            }
        }

        String text = textArea.getText();
        String[] lines = text.split("\n");

        String methodWithParen = methodName + "(";
        String dotMethod = "." + methodName;
        String thisMethod = "this." + methodName;

        String quotedName = Pattern.quote(methodName);
        Pattern declarationPattern = Pattern.compile(
            "^\\s*(public|private|protected|static|final|abstract|synchronized|native|strictfp|\\s)+.*\\s+" +
            quotedName + "\\s*\\("
        );
        Pattern simplePattern = Pattern.compile(
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
            highlighter.highlightAndScrollToLine(matchingLines.get(0));
            return;
        }

        int descParamCount = MethodSignatureMatcher.countDescriptorParams(methodDesc);
        String descReturnType = MethodSignatureMatcher.extractReturnTypeFromDesc(methodDesc);

        for (int lineNum : matchingLines) {
            String line = lines[lineNum];
            String sourceParams = SourceDeclarationParser.extractMethodParams(line);
            int sourceParamCount = MethodSignatureMatcher.countParams(sourceParams);
            String sourceReturnType = MethodSignatureMatcher.extractReturnTypeFromSource(line);
            if (sourceParamCount == descParamCount &&
                MethodSignatureMatcher.paramsMatch(sourceParams, methodDesc) &&
                MethodSignatureMatcher.returnTypeMatches(sourceReturnType, descReturnType)) {
                highlighter.highlightAndScrollToLine(lineNum);
                return;
            }
        }

        for (int lineNum : matchingLines) {
            String line = lines[lineNum];
            String sourceParams = SourceDeclarationParser.extractMethodParams(line);
            int sourceParamCount = MethodSignatureMatcher.countParams(sourceParams);
            String sourceReturnType = MethodSignatureMatcher.extractReturnTypeFromSource(line);
            if (sourceParamCount == descParamCount &&
                MethodSignatureMatcher.returnTypeMatches(sourceReturnType, descReturnType)) {
                highlighter.highlightAndScrollToLine(lineNum);
                return;
            }
        }

        highlighter.highlightAndScrollToLine(matchingLines.get(0));
    }

    /** Scroll to a field definition in the source view and highlight the line. */
    void scrollToFieldDefinition(String fieldName) {
        if (!omitAnnotations.getAsBoolean() && classEntry.getFieldSpans() != null) {
            DecompileResult.MemberSpan span = fieldSpanByName(fieldName);
            if (span != null) {
                highlighter.highlightAndScrollToLine(span.getStartLine() - 1);
                return;
            }
        }

        String text = textArea.getText();
        String[] lines = text.split("\n");

        String dotField = "." + fieldName;

        String quotedName = Pattern.quote(fieldName);
        Pattern declarationPattern = Pattern.compile(
            "^\\s*(public|private|protected|static|final|volatile|transient|\\s)+.*\\s+" +
            quotedName + "\\s*[;=]"
        );
        Pattern simplePattern = Pattern.compile(
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
                highlighter.highlightAndScrollToLine(lineNum);
                return;
            }
        }
    }

    /** Get the word at the current caret position. */
    String getWordAtCaret() {
        try {
            int caretPos = textArea.getCaretPosition();
            String text = textArea.getText();
            if (caretPos < 0 || caretPos > text.length()) {
                return null;
            }

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

    private ClassEntryModel findClassBySimpleName(String simpleName) {
        ProjectModel project = projectModel.get();
        if (project == null) {
            return null;
        }
        for (ClassEntryModel entry : project.getAllClasses()) {
            if (entry.getSimpleName().equals(simpleName)) {
                return entry;
            }
        }
        return null;
    }

    DeclarationInfo getDeclarationAtLine(int lineNumber) {
        DeclarationInfo fromSpans = declarationFromSpans(lineNumber);
        if (fromSpans != null) {
            return fromSpans;
        }
        try {
            int startOffset = textArea.getLineStartOffset(lineNumber - 1);
            int endOffset = textArea.getLineEndOffset(lineNumber - 1);
            String lineText = textArea.getText(startOffset, endOffset - startOffset);

            String className = SourceDeclarationParser.extractClassDeclaration(lineText);
            if (className != null) {
                return new DeclarationInfo(DeclarationType.CLASS, className);
            }

            String methodName = SourceDeclarationParser.extractMethodDeclaration(lineText);
            if (methodName != null) {
                String paramTypes = SourceDeclarationParser.extractMethodParams(lineText);
                MethodEntryModel method = findMethodByNameAndParams(methodName, paramTypes);
                if (method != null) {
                    return new DeclarationInfo(DeclarationType.METHOD, methodName, method.getDescriptor());
                }
                return new DeclarationInfo(DeclarationType.METHOD, methodName);
            }

            String fieldName = SourceDeclarationParser.extractFieldDeclaration(lineText);
            if (fieldName != null) {
                return new DeclarationInfo(DeclarationType.FIELD, fieldName);
            }
        } catch (BadLocationException e) {
            // ignore
        }
        return null;
    }

    /**
     * Resolves the declaration on a 1-based line from the decompiler's member spans (a line belongs to the member
     * whose span contains it; class/method/field spans are disjoint). Returns null when spans are unavailable or
     * annotations are filtered - both shift or remove line data - so callers fall back to the regex extractors.
     */
    private DeclarationInfo declarationFromSpans(int lineNumber) {
        if (omitAnnotations.getAsBoolean()) {
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

        int sourceParamCount = MethodSignatureMatcher.countParams(sourceParams);
        for (MethodEntryModel method : candidates) {
            String desc = method.getDescriptor();
            int descParamCount = MethodSignatureMatcher.countDescriptorParams(desc);
            if (descParamCount == sourceParamCount) {
                if (MethodSignatureMatcher.paramsMatch(sourceParams, desc)) {
                    return method;
                }
            }
        }

        for (MethodEntryModel method : candidates) {
            String desc = method.getDescriptor();
            int descParamCount = MethodSignatureMatcher.countDescriptorParams(desc);
            if (descParamCount == sourceParamCount) {
                return method;
            }
        }

        return candidates.get(0);
    }

    void showRenameDialog(DeclarationInfo decl) {
        if (decl == null) {
            return;
        }

        Window window = SwingUtilities.getWindowAncestor(host);
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

    void findUsagesOfDeclaration(DeclarationInfo decl) {
        if (decl == null) {
            return;
        }

        String className = classEntry.getClassName();
        switch (decl.type) {
            case CLASS:
                EventBus.getInstance().post(FindUsagesEvent.forClass(host, className));
                break;
            case METHOD:
                if (decl.descriptor != null) {
                    EventBus.getInstance().post(FindUsagesEvent.forMethod(
                            host, className, decl.name, decl.descriptor));
                } else {
                    MethodEntryModel method = findMethodByName(decl.name);
                    if (method != null) {
                        EventBus.getInstance().post(FindUsagesEvent.forMethod(
                                host, className, method.getName(), method.getDescriptor()));
                    }
                }
                break;
            case FIELD:
                FieldEntryModel field = findFieldByName(decl.name);
                if (field != null) {
                    EventBus.getInstance().post(FindUsagesEvent.forField(
                            host, className, field.getName(), field.getFieldEntry().getDesc()));
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
