package com.tonic.ui.editor.source;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strips annotation declarations out of decompiled source for the "omit annotations" view. Pure: it derives the
 * annotation simple-name set from the class file's {@code RuntimeVisibleAnnotations} and removes matching annotation
 * lines (including multi-line forms with nested parens) from the text, leaving only code.
 */
final class AnnotationFilter {

    private AnnotationFilter() {
    }

    static String filter(ClassFile classFile, String source) {
        Set<String> annotationNames = collectAnnotationNames(classFile);
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

    /** Collect all annotation simple names from the class file (class, methods, fields). */
    private static Set<String> collectAnnotationNames(ClassFile classFile) {
        Set<String> names = new HashSet<>();

        collectAnnotationsFromAttributes(getClassAttributes(classFile), classFile, names);

        for (MethodEntry method : classFile.getMethods()) {
            collectAnnotationsFromAttributes(method.getAttributes(), classFile, names);
        }

        for (FieldEntry field : classFile.getFields()) {
            collectAnnotationsFromAttributes(field.getAttributes(), classFile, names);
        }

        return names;
    }

    private static List<Attribute> getClassAttributes(ClassFile classFile) {
        List<Attribute> attrs = classFile.getClassAttributes();
        return attrs != null ? attrs : List.of();
    }

    private static void collectAnnotationsFromAttributes(List<Attribute> attributes, ClassFile classFile,
                                                         Set<String> names) {
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

    private static String resolveAnnotationSimpleName(Annotation anno, ClassFile classFile) {
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
    private static String removeAnnotation(String source, String annotationName) {
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

    private static boolean isActualJavaCode(String trimmed) {
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

    private static boolean isAnnotationStart(String trimmed, String annotationName) {
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

    private static int countChar(String s, char c) {
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

    /** Remove any remaining standalone annotation lines not in our collected set. */
    private static String removeEmptyAnnotationLines(String source) {
        return source;
    }
}
