package com.tonic.ui.util;

public class DescriptorParser {

    private DescriptorParser() {
    }

    public static String formatFieldDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "?";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;

        int arrayDim = 0;
        while (i < desc.length() && desc.charAt(i) == '[') {
            arrayDim++;
            i++;
        }

        if (i < desc.length()) {
            char c = desc.charAt(i);
            switch (c) {
                case 'B': result.append("byte"); break;
                case 'C': result.append("char"); break;
                case 'D': result.append("double"); break;
                case 'F': result.append("float"); break;
                case 'I': result.append("int"); break;
                case 'J': result.append("long"); break;
                case 'S': result.append("short"); break;
                case 'Z': result.append("boolean"); break;
                case 'V': result.append("void"); break;
                case 'L':
                    int semicolon = desc.indexOf(';', i);
                    if (semicolon > i) {
                        String className = desc.substring(i + 1, semicolon);
                        result.append(extractSimpleName(className));
                    }
                    break;
                default:
                    result.append(desc);
                    break;
            }
        }

        for (int d = 0; d < arrayDim; d++) {
            result.append("[]");
        }

        return result.toString();
    }

    public static String formatReturnType(String methodDescriptor) {
        if (methodDescriptor == null || methodDescriptor.isEmpty()) {
            return "void";
        }
        int parenEnd = methodDescriptor.indexOf(')');
        if (parenEnd < 0 || parenEnd + 1 >= methodDescriptor.length()) {
            return "void";
        }
        return formatFieldDescriptor(methodDescriptor.substring(parenEnd + 1));
    }

    public static String formatMethodParams(String methodDescriptor) {
        if (methodDescriptor == null || methodDescriptor.isEmpty()) {
            return "";
        }

        int paramStart = methodDescriptor.indexOf('(') + 1;
        int paramEnd = methodDescriptor.indexOf(')');
        if (paramStart <= 0 || paramEnd < 0 || paramStart >= paramEnd) {
            return "";
        }

        String params = methodDescriptor.substring(paramStart, paramEnd);
        return formatParamList(params);
    }

    private static String formatParamList(String params) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        boolean first = true;

        while (i < params.length()) {
            if (!first) result.append(", ");
            first = false;

            char c = params.charAt(i);
            switch (c) {
                case 'B': result.append("byte"); i++; break;
                case 'C': result.append("char"); i++; break;
                case 'D': result.append("double"); i++; break;
                case 'F': result.append("float"); i++; break;
                case 'I': result.append("int"); i++; break;
                case 'J': result.append("long"); i++; break;
                case 'S': result.append("short"); i++; break;
                case 'Z': result.append("boolean"); i++; break;
                case 'V': result.append("void"); i++; break;
                case '[':
                    int arrayDim = 0;
                    while (i < params.length() && params.charAt(i) == '[') {
                        arrayDim++;
                        i++;
                    }
                    if (i < params.length()) {
                        String elem;
                        if (params.charAt(i) == 'L') {
                            int semi = params.indexOf(';', i);
                            if (semi > i) {
                                elem = extractSimpleName(params.substring(i + 1, semi));
                                i = semi + 1;
                            } else {
                                elem = "?";
                                i++;
                            }
                        } else {
                            elem = formatPrimitive(params.charAt(i));
                            i++;
                        }
                        result.append(elem);
                        for (int d = 0; d < arrayDim; d++) {
                            result.append("[]");
                        }
                    }
                    break;
                case 'L':
                    int semicolon = params.indexOf(';', i);
                    if (semicolon > i) {
                        result.append(extractSimpleName(params.substring(i + 1, semicolon)));
                        i = semicolon + 1;
                    } else {
                        i++;
                    }
                    break;
                default:
                    i++;
                    break;
            }
        }
        return result.toString();
    }

    private static String formatPrimitive(char c) {
        switch (c) {
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'Z': return "boolean";
            case 'V': return "void";
            default: return String.valueOf(c);
        }
    }

    public static String extractSimpleName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return "";
        }
        int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash >= 0) {
            return internalName.substring(lastSlash + 1);
        }
        return internalName;
    }
}
