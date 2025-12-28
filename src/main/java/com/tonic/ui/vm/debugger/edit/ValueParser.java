package com.tonic.ui.vm.debugger.edit;

import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.execution.state.ValueTag;

public final class ValueParser {

    private ValueParser() {}

    public static ConcreteValue parse(String input, ValueTag tag) throws ValueParseException {
        if (input == null || input.trim().isEmpty()) {
            throw new ValueParseException("Value cannot be empty");
        }

        String trimmed = input.trim();

        switch (tag) {
            case INT:
                return parseInteger(trimmed);
            case LONG:
                return parseLong(trimmed);
            case FLOAT:
                return parseFloat(trimmed);
            case DOUBLE:
                return parseDouble(trimmed);
            case REFERENCE:
            case NULL:
                return parseReference(trimmed);
            default:
                throw new ValueParseException("Unsupported type: " + tag);
        }
    }

    private static ConcreteValue parseInteger(String input) throws ValueParseException {
        try {
            String cleaned = input.replaceAll("[_,]", "");
            int value;

            if (cleaned.equalsIgnoreCase("true")) {
                return ConcreteValue.intValue(1);
            }
            if (cleaned.equalsIgnoreCase("false")) {
                return ConcreteValue.intValue(0);
            }

            if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
                value = Integer.parseUnsignedInt(cleaned.substring(2), 16);
            } else if (cleaned.startsWith("-0x") || cleaned.startsWith("-0X")) {
                value = -Integer.parseUnsignedInt(cleaned.substring(3), 16);
            } else if (cleaned.startsWith("0b") || cleaned.startsWith("0B")) {
                value = Integer.parseUnsignedInt(cleaned.substring(2), 2);
            } else if (cleaned.startsWith("-0b") || cleaned.startsWith("-0B")) {
                value = -Integer.parseUnsignedInt(cleaned.substring(3), 2);
            } else if (cleaned.length() > 1 && cleaned.startsWith("0") &&
                       !cleaned.contains(".") && cleaned.matches("-?0[0-7]+")) {
                value = Integer.parseInt(cleaned, 8);
            } else if (cleaned.startsWith("'") && cleaned.endsWith("'") && cleaned.length() >= 3) {
                char c = parseCharLiteral(cleaned);
                value = c;
            } else {
                value = Integer.parseInt(cleaned);
            }
            return ConcreteValue.intValue(value);
        } catch (NumberFormatException e) {
            throw new ValueParseException("Invalid integer: '" + input + "'");
        }
    }

    private static ConcreteValue parseLong(String input) throws ValueParseException {
        try {
            String cleaned = input.replaceAll("[_,LlDd]", "");
            long value;

            if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
                value = Long.parseUnsignedLong(cleaned.substring(2), 16);
            } else if (cleaned.startsWith("-0x") || cleaned.startsWith("-0X")) {
                value = -Long.parseUnsignedLong(cleaned.substring(3), 16);
            } else if (cleaned.startsWith("0b") || cleaned.startsWith("0B")) {
                value = Long.parseUnsignedLong(cleaned.substring(2), 2);
            } else if (cleaned.startsWith("-0b") || cleaned.startsWith("-0B")) {
                value = -Long.parseUnsignedLong(cleaned.substring(3), 2);
            } else {
                value = Long.parseLong(cleaned);
            }
            return ConcreteValue.longValue(value);
        } catch (NumberFormatException e) {
            throw new ValueParseException("Invalid long: '" + input + "'");
        }
    }

    private static ConcreteValue parseFloat(String input) throws ValueParseException {
        try {
            String cleaned = input.replaceAll("[fF]$", "");

            if ("NaN".equalsIgnoreCase(cleaned)) {
                return ConcreteValue.floatValue(Float.NaN);
            }
            if ("Infinity".equalsIgnoreCase(cleaned) || "+Infinity".equalsIgnoreCase(cleaned)) {
                return ConcreteValue.floatValue(Float.POSITIVE_INFINITY);
            }
            if ("-Infinity".equalsIgnoreCase(cleaned)) {
                return ConcreteValue.floatValue(Float.NEGATIVE_INFINITY);
            }

            float value = Float.parseFloat(cleaned);
            return ConcreteValue.floatValue(value);
        } catch (NumberFormatException e) {
            throw new ValueParseException("Invalid float: '" + input + "'");
        }
    }

    private static ConcreteValue parseDouble(String input) throws ValueParseException {
        try {
            String cleaned = input.replaceAll("[dD]$", "");

            if ("NaN".equalsIgnoreCase(cleaned)) {
                return ConcreteValue.doubleValue(Double.NaN);
            }
            if ("Infinity".equalsIgnoreCase(cleaned) || "+Infinity".equalsIgnoreCase(cleaned)) {
                return ConcreteValue.doubleValue(Double.POSITIVE_INFINITY);
            }
            if ("-Infinity".equalsIgnoreCase(cleaned)) {
                return ConcreteValue.doubleValue(Double.NEGATIVE_INFINITY);
            }

            double value = Double.parseDouble(cleaned);
            return ConcreteValue.doubleValue(value);
        } catch (NumberFormatException e) {
            throw new ValueParseException("Invalid double: '" + input + "'");
        }
    }

    private static ConcreteValue parseReference(String input) throws ValueParseException {
        if ("null".equalsIgnoreCase(input)) {
            return ConcreteValue.nullRef();
        }
        throw new ValueParseException("Cannot create object references. Only 'null' is allowed.");
    }

    private static char parseCharLiteral(String input) throws ValueParseException {
        String inner = input.substring(1, input.length() - 1);
        if (inner.length() == 1) {
            return inner.charAt(0);
        }
        if (inner.startsWith("\\")) {
            if (inner.length() == 2) {
                switch (inner.charAt(1)) {
                    case 'n': return '\n';
                    case 't': return '\t';
                    case 'r': return '\r';
                    case '\\': return '\\';
                    case '\'': return '\'';
                    case '"': return '"';
                    case '0': return '\0';
                    default:
                        throw new ValueParseException("Unknown escape sequence: " + inner);
                }
            }
            if (inner.charAt(1) == 'u' && inner.length() == 6) {
                try {
                    return (char) Integer.parseInt(inner.substring(2), 16);
                } catch (NumberFormatException e) {
                    throw new ValueParseException("Invalid unicode escape: " + inner);
                }
            }
        }
        throw new ValueParseException("Invalid character literal: " + input);
    }

    public static String formatTypeName(ValueTag tag) {
        switch (tag) {
            case INT: return "int (32-bit integer)";
            case LONG: return "long (64-bit integer)";
            case FLOAT: return "float (32-bit floating point)";
            case DOUBLE: return "double (64-bit floating point)";
            case REFERENCE: return "reference (object)";
            case NULL: return "null";
            default: return tag.name();
        }
    }

    public static boolean isEditable(ValueTag tag) {
        return tag == ValueTag.INT ||
               tag == ValueTag.LONG ||
               tag == ValueTag.FLOAT ||
               tag == ValueTag.DOUBLE ||
               tag == ValueTag.REFERENCE ||
               tag == ValueTag.NULL;
    }
}
