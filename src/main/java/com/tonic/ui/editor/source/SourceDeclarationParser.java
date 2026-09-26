package com.tonic.ui.editor.source;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SourceDeclarationParser
{

    private SourceDeclarationParser()
    {
    }

    private static final Pattern CLASS_DECL_PATTERN = Pattern.compile("^\\s*(?:public|private|protected|abstract|final|static|strictfp|\\s)*\\s*(?:class|interface|enum|@interface)\\s+(\\w+)");

    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile("^\\s*(?:public|private|protected|static|final|abstract|synchronized|native|strictfp|\\s)*" + "(?:<[^>]+>\\s*)?" + "\\w+(?:<[^>]*>)?(?:\\[])*\\s+" + "(\\w+)\\s*\\(");

    private static final Pattern FIELD_DECL_PATTERN = Pattern.compile("^\\s*(?:public|private|protected|static|final|volatile|transient|\\s)*" + "\\w+(?:<[^>]*>)?(?:\\[])*\\s+" + "(\\w+)\\s*[;=]");

    static String extractIdentifierAt(String text, int offset)
    {
        if (offset < 0 || offset >= text.length())
        {
            return null;
        }

        int start = offset;
        while (start > 0 && isIdentifierChar(text.charAt(start - 1)))
        {
            start--;
        }

        int end = offset;
        while (end < text.length() && isIdentifierChar(text.charAt(end)))
        {
            end++;
        }

        if (start == end)
        {
            return null;
        }
        return text.substring(start, end);
    }

    private static boolean isIdentifierChar(char c)
    {
        return Character.isJavaIdentifierPart(c) || c == '.';
    }

    static String extractClassDeclaration(String line)
    {
        Matcher m = CLASS_DECL_PATTERN.matcher(line);
        if (m.find())
        {
            return m.group(1);
        }
        return null;
    }

    static String extractMethodDeclaration(String line)
    {
        String trimmed = line.trim();
        if (trimmed.startsWith("return") || trimmed.startsWith("if") ||
                trimmed.startsWith("while") || trimmed.startsWith("for") ||
                trimmed.startsWith("switch") || trimmed.startsWith("new ") ||
                trimmed.startsWith("throw ") || trimmed.startsWith("//") ||
                trimmed.startsWith("/*") || trimmed.startsWith("*"))
        {
            return null;
        }
        if (line.contains(" new ") || line.contains("="))
        {
            return null;
        }

        Matcher m = METHOD_DECL_PATTERN.matcher(line);
        if (m.find())
        {
            String name = m.group(1);
            if (!name.equals("if") && !name.equals("while") && !name.equals("for") &&
                    !name.equals("switch") && !name.equals("catch") && !name.equals("synchronized"))
            {
                return name;
            }
        }
        return null;
    }

    static String extractFieldDeclaration(String line)
    {
        String trimmed = line.trim();
        if (trimmed.startsWith("return") || trimmed.startsWith("//") ||
                trimmed.startsWith("/*") || trimmed.startsWith("*") ||
                trimmed.contains("("))
        {
            return null;
        }

        Matcher m = FIELD_DECL_PATTERN.matcher(line);
        if (m.find())
        {
            return m.group(1);
        }
        return null;
    }

    static String extractMethodParams(String line)
    {
        int parenStart = line.indexOf('(');
        int parenEnd = line.lastIndexOf(')');
        if (parenStart >= 0 && parenEnd > parenStart)
        {
            return line.substring(parenStart + 1, parenEnd).trim();
        }
        return "";
    }
}
