package com.tonic.ui.editor.source;

/**
 * Pure matching between a JVM method descriptor and a source-level method signature line: parameter counts, per-type
 * compatibility (simple-name aware, generics/array tolerant) and return type. Used by the source view's navigation
 * to pick the correct overload's declaration line. String in, boolean/int/String out - freely unit-testable.
 */
final class MethodSignatureMatcher {

    private MethodSignatureMatcher() {
    }

    /** The number of comma-separated parameters in a source parameter list (generics-aware), or 0 when empty. */
    static int countParams(String sourceParams) {
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

    /** The number of parameters encoded in a JVM method descriptor. */
    static int countDescriptorParams(String desc) {
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

    /** Whether the source parameter list is type-compatible with the descriptor's parameters. */
    static boolean paramsMatch(String sourceParams, String desc) {
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

    private static String extractDescType(String desc, int index) {
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

    private static int skipDescType(String desc, int index) {
        char c = desc.charAt(index);
        if (c == '[') {
            return skipDescType(desc, index + 1);
        } else if (c == 'L') {
            return desc.indexOf(';', index) + 1;
        } else {
            return index + 1;
        }
    }

    private static boolean typeMatches(String sourceType, String descType) {
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

    /** The simple return type name encoded after the descriptor's {@code ')'}, or null. */
    static String extractReturnTypeFromDesc(String desc) {
        if (desc == null) return null;
        int parenClose = desc.indexOf(')');
        if (parenClose < 0 || parenClose >= desc.length() - 1) return null;
        return extractDescType(desc, parenClose + 1);
    }

    /** The declared return type token preceding the method name on a source signature line, or null. */
    static String extractReturnTypeFromSource(String line) {
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

    /** Whether the source return type matches the descriptor return type (null on either side is permissive). */
    static boolean returnTypeMatches(String sourceReturnType, String descReturnType) {
        if (sourceReturnType == null || descReturnType == null) return true;
        return typeMatches(sourceReturnType, descReturnType);
    }
}
