package com.tonic.util;

import java.util.ArrayList;
import java.util.List;

/** Splits JVM method descriptors and formats type and method descriptors as readable Java type names using simple class names. */
public class DescriptorParser
{

    private DescriptorParser()
    {
    }

    /**
     * Splits a method descriptor's parameters into readable type names, such as int and String.
     *
     * @param methodDescriptor the method descriptor, or null
     * @return one name per parameter, empty when the descriptor is null or malformed
     */
    public static List<String> parseParameterTypes(String methodDescriptor)
    {
        List<String> out = new ArrayList<>();
        if (methodDescriptor == null)
        {
            return out;
        }
        try
        {
            for (String descriptor : parameterDescriptors(methodDescriptor))
            {
                out.add(formatFieldDescriptor(descriptor));
            }
        }
        catch (IllegalArgumentException e)
        {
            out.clear();
        }
        return out;
    }

    /**
     * Splits a method descriptor's parameters into field descriptors, such as I and [Ljava/lang/String;.
     *
     * @param methodDescriptor the method descriptor
     * @return one descriptor per parameter, in order
     * @throws IllegalArgumentException if the descriptor is null or malformed
     */
    public static List<String> parameterDescriptors(String methodDescriptor)
    {
        if (methodDescriptor == null || !methodDescriptor.startsWith("("))
        {
            throw new IllegalArgumentException("Not a method descriptor: " + methodDescriptor);
        }
        int end = methodDescriptor.indexOf(')');
        if (end < 0)
        {
            throw new IllegalArgumentException("Not a method descriptor: " + methodDescriptor);
        }
        List<String> out = new ArrayList<>();
        int i = 1;
        while (i < end)
        {
            int next = fieldDescriptorEnd(methodDescriptor, i, end);
            out.add(methodDescriptor.substring(i, next));
            i = next;
        }
        return out;
    }

    /**
     * Extracts a method descriptor's return type.
     *
     * @param methodDescriptor the method descriptor
     * @return the return type's field descriptor, V for void
     * @throws IllegalArgumentException if the descriptor is null or malformed
     */
    public static String returnDescriptor(String methodDescriptor)
    {
        parameterDescriptors(methodDescriptor);
        int start = methodDescriptor.indexOf(')') + 1;
        if (start >= methodDescriptor.length())
        {
            throw new IllegalArgumentException("Method descriptor has no return type: " + methodDescriptor);
        }
        if (methodDescriptor.charAt(start) == 'V' && start + 1 == methodDescriptor.length())
        {
            return "V";
        }
        if (fieldDescriptorEnd(methodDescriptor, start, methodDescriptor.length()) != methodDescriptor.length())
        {
            throw new IllegalArgumentException("Malformed return type: " + methodDescriptor);
        }
        return methodDescriptor.substring(start);
    }

    private static int fieldDescriptorEnd(String descriptor, int start, int limit)
    {
        int i = start;
        while (i < limit && descriptor.charAt(i) == '[')
        {
            i++;
        }
        if (i >= limit)
        {
            throw new IllegalArgumentException("Malformed descriptor: " + descriptor);
        }
        char tag = descriptor.charAt(i);
        if (tag == 'L')
        {
            int semicolon = descriptor.indexOf(';', i);
            if (semicolon < 0 || semicolon >= limit || semicolon == i + 1)
            {
                throw new IllegalArgumentException("Malformed descriptor: " + descriptor);
            }
            return semicolon + 1;
        }
        if ("BCDFIJSZ".indexOf(tag) < 0)
        {
            throw new IllegalArgumentException("Malformed descriptor: " + descriptor);
        }
        return i + 1;
    }

    /**
     * Formats one field descriptor as a readable type name with trailing brackets for each array dimension.
     *
     * @param desc the field descriptor
     * @return the type name, "?" when the descriptor is null or empty, or the descriptor itself when its tag is unknown or a class name is unterminated
     */
    public static String formatFieldDescriptor(String desc)
    {
        if (desc == null || desc.isEmpty())
        {
            return "?";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;

        int arrayDim = 0;
        while (i < desc.length() && desc.charAt(i) == '[')
        {
            arrayDim++;
            i++;
        }

        if (i < desc.length())
        {
            char c = desc.charAt(i);
            switch (c)
            {
                case 'B':
                    result.append("byte");
                    break;
                case 'C':
                    result.append("char");
                    break;
                case 'D':
                    result.append("double");
                    break;
                case 'F':
                    result.append("float");
                    break;
                case 'I':
                    result.append("int");
                    break;
                case 'J':
                    result.append("long");
                    break;
                case 'S':
                    result.append("short");
                    break;
                case 'Z':
                    result.append("boolean");
                    break;
                case 'V':
                    result.append("void");
                    break;
                case 'L':
                    int semicolon = desc.indexOf(';', i);
                    if (semicolon > i)
                    {
                        String className = desc.substring(i + 1, semicolon);
                        result.append(extractSimpleName(className));
                    }
                    else
                    {
                        return desc;
                    }
                    break;
                default:
                    result.append(desc);
                    break;
            }
        }

        result.append("[]".repeat(Math.max(0, arrayDim)));

        return result.toString();
    }

    /**
     * Formats a method descriptor's return type.
     *
     * @param methodDescriptor the method descriptor
     * @return the readable return type, or "void" when the descriptor is null, empty or has nothing after the parameter list
     */
    public static String formatReturnType(String methodDescriptor)
    {
        if (methodDescriptor == null || methodDescriptor.isEmpty())
        {
            return "void";
        }
        int parenEnd = methodDescriptor.indexOf(')');
        if (parenEnd < 0 || parenEnd + 1 >= methodDescriptor.length())
        {
            return "void";
        }
        return formatFieldDescriptor(methodDescriptor.substring(parenEnd + 1));
    }

    /**
     * Formats a method descriptor's parameters as a comma-separated list.
     *
     * @param methodDescriptor the method descriptor
     * @return the readable parameter list, or empty when there are none or the descriptor is malformed
     */
    public static String formatMethodParams(String methodDescriptor)
    {
        if (methodDescriptor == null || methodDescriptor.isEmpty())
        {
            return "";
        }

        int paramStart = methodDescriptor.indexOf('(') + 1;
        int paramEnd = methodDescriptor.indexOf(')');
        if (paramStart <= 0 || paramEnd < 0 || paramStart >= paramEnd)
        {
            return "";
        }

        String params = methodDescriptor.substring(paramStart, paramEnd);
        return formatParamList(params);
    }

    private static String formatParamList(String params)
    {
        StringBuilder result = new StringBuilder();
        int i = 0;
        boolean first = true;

        while (i < params.length())
        {
            if (!first) result.append(", ");
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
                    int arrayDim = 0;
                    while (i < params.length() && params.charAt(i) == '[')
                    {
                        arrayDim++;
                        i++;
                    }
                    if (i < params.length())
                    {
                        String elem;
                        if (params.charAt(i) == 'L')
                        {
                            int semi = params.indexOf(';', i);
                            if (semi > i)
                            {
                                elem = extractSimpleName(params.substring(i + 1, semi));
                                i = semi + 1;
                            }
                            else
                            {
                                elem = "?";
                                i++;
                            }
                        }
                        else
                        {
                            elem = formatPrimitive(params.charAt(i));
                            i++;
                        }
                        result.append(elem);
                        result.append("[]".repeat(Math.max(0, arrayDim)));
                    }
                    break;
                case 'L':
                    int semicolon = params.indexOf(';', i);
                    if (semicolon > i)
                    {
                        result.append(extractSimpleName(params.substring(i + 1, semicolon)));
                        i = semicolon + 1;
                    }
                    else
                    {
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

    private static String formatPrimitive(char c)
    {
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
            default:
                return String.valueOf(c);
        }
    }

    /**
     * Strips the package from an internal class name.
     *
     * @param internalName the class's internal name, with slashes
     * @return the part after the last slash, or empty when the name is null or empty
     */
    public static String extractSimpleName(String internalName)
    {
        if (internalName == null || internalName.isEmpty())
        {
            return "";
        }
        int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash >= 0)
        {
            return internalName.substring(lastSlash + 1);
        }
        return internalName;
    }
}
