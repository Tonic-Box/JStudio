package com.tonic.ui.editor.source;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strips annotation declarations out of decompiled source for the "omit annotations" view. Pure: it derives the
 * annotation simple-name set from the class file's {@code RuntimeVisibleAnnotations} and removes matching annotation
 * lines (including multi-line forms with nested parens) from the text, leaving only code. Only whole lines are
 * removed, so {@link #filterWithMap} can also return an exact original-to-filtered line map.
 */
final class AnnotationFilter {

    private AnnotationFilter() {
    }

    /** The filtered source plus a map from each original 0-based line to its 0-based filtered line (-1 if removed). */
    static final class Filtered {
        final String text;
        final int[] lineMap;

        Filtered(String text, int[] lineMap) {
            this.text = text;
            this.lineMap = lineMap;
        }
    }

    static String filter(ClassFile classFile, String source) {
        return filterWithMap(classFile, source).text;
    }

    /**
     * Strips annotation declaration lines and returns the filtered text alongside a line map, so callers (e.g. the
     * usage lens) can translate line numbers from the original source to the filtered view. Since only whole lines
     * are removed, the map is exact.
     */
    static Filtered filterWithMap(ClassFile classFile, String source) {
        String[] lines = source.split("\n", -1);
        int[] lineMap = new int[lines.length];
        Set<String> annotationNames = collectAnnotationNames(classFile);
        if (annotationNames.isEmpty()) {
            for (int i = 0; i < lines.length; i++) {
                lineMap[i] = i;
            }
            return new Filtered(source, lineMap);
        }

        StringBuilder result = new StringBuilder();
        int filtered = 0;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (isAnnotationStartAny(line.trim(), annotationNames)) {
                lineMap[i] = -1;
                int parenDepth = countChar(line, '(') - countChar(line, ')');
                while (parenDepth > 0 && i + 1 < lines.length) {
                    i++;
                    lineMap[i] = -1;
                    parenDepth += countChar(lines[i], '(') - countChar(lines[i], ')');
                }
                i++;
                while (i < lines.length && !isActualJavaCode(lines[i].trim())) {
                    lineMap[i] = -1;
                    i++;
                }
                continue;
            }
            if (filtered > 0) {
                result.append("\n");
            }
            result.append(line);
            lineMap[i] = filtered++;
            i++;
        }
        return new Filtered(result.toString(), lineMap);
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

    /** True when {@code trimmed} begins an annotation whose simple name matches any collected annotation. */
    private static boolean isAnnotationStartAny(String trimmed, Set<String> annotationNames) {
        if (!trimmed.startsWith("@")) {
            return false;
        }
        for (String name : annotationNames) {
            String cleanName = name.replace("\r", "").split("\n")[0].trim();
            if (!cleanName.isEmpty() && isAnnotationStart(trimmed, cleanName)) {
                return true;
            }
        }
        return false;
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
}
