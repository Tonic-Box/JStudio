package com.tonic.ui.core.util;

/** Helpers that turn JVM internal names and method descriptors into readable form. */
public final class JvmDescriptorFormatter
{

    private JvmDescriptorFormatter()
    {
    }

    /**
     * Strips the package from an internal class name, so java/lang/String gives String.
     *
     * @param internalName the class's internal name, with slashes
     * @return the part after the last slash, or the whole name if it has none
     */
    public static String getSimpleClassName(String internalName)
    {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    /**
     * Formats a method descriptor's parameters with simple type names, so (ILjava/lang/String;)V gives (int, String).
     *
     * @param descriptor the method descriptor
     * @return the parenthesized list, or () for a null, parameterless or malformed descriptor
     */
    public static String formatDescriptorParams(String descriptor)
    {
        if (descriptor == null || !descriptor.startsWith("("))
        {
            return "()";
        }
        int endParen = descriptor.indexOf(')');
        if (endParen < 0)
        {
            return "()";
        }
        String params = descriptor.substring(1, endParen);
        if (params.isEmpty())
        {
            return "()";
        }
        StringBuilder result = new StringBuilder("(");
        int i = 0;
        boolean first = true;
        while (i < params.length())
        {
            if (!first)
            {
                result.append(", ");
            }
            first = false;
            char c = params.charAt(i);
            switch (c)
            {
                case 'B':
                    result.append("byte");
                    i++;
                    break;
                case 'C':
                    result.append("char");
                    i++;
                    break;
                case 'D':
                    result.append("double");
                    i++;
                    break;
                case 'F':
                    result.append("float");
                    i++;
                    break;
                case 'I':
                    result.append("int");
                    i++;
                    break;
                case 'J':
                    result.append("long");
                    i++;
                    break;
                case 'S':
                    result.append("short");
                    i++;
                    break;
                case 'Z':
                    result.append("boolean");
                    i++;
                    break;
                case 'V':
                    result.append("void");
                    i++;
                    break;
                case '[':
                    int arrayDims = 0;
                    while (i < params.length() && params.charAt(i) == '[')
                    {
                        arrayDims++;
                        i++;
                    }
                    String elementType = parseOneType(params, i);
                    i += rawTypeLength(params, i);
                    result.append(elementType);
                    result.append("[]".repeat(Math.max(0, arrayDims)));
                    break;
                case 'L':
                    int semi = params.indexOf(';', i);
                    if (semi > i)
                    {
                        String className = params.substring(i + 1, semi);
                        result.append(getSimpleClassName(className));
                        i = semi + 1;
                    }
                    else
                    {
                        i++;
                    }
                    break;
                default:
                    i++;
            }
        }
        result.append(")");
        return result.toString();
    }

    private static String parseOneType(String params, int i)
    {
        if (i >= params.length())
        {
            return "?";
        }
        char c = params.charAt(i);
        switch (c)
        {
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'D':
                return "double";
            case 'F':
                return "float";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'S':
                return "short";
            case 'Z':
                return "boolean";
            case 'V':
                return "void";
            case 'L':
                int semi = params.indexOf(';', i);
                if (semi > i)
                {
                    return getSimpleClassName(params.substring(i + 1, semi));
                }
                return "Object";
            default:
                return "?";
        }
    }

    private static int rawTypeLength(String params, int i)
    {
        if (i >= params.length())
        {
            return 0;
        }
        char c = params.charAt(i);
        if (c == 'L')
        {
            int semi = params.indexOf(';', i);
            return semi > i ? (semi - i + 1) : 1;
        }
        return 1;
    }
}
