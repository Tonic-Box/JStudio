package com.tonic.ui.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParser {

    private static final Pattern COMMAND_PATTERN = Pattern.compile("^/(\\w+)(?:\\s+(.*))?$");
    private static final Pattern METHOD_CALL_PATTERN = Pattern.compile(
        "^([a-zA-Z_$][\\w.$]*)\\.([a-zA-Z_$][\\w$]*)\\((.*)\\)$"
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern CHAR_LITERAL = Pattern.compile("'(.)'");
    private static final Pattern LONG_LITERAL = Pattern.compile("(-?\\d+)[Ll]");
    private static final Pattern DOUBLE_LITERAL = Pattern.compile("(-?\\d+\\.\\d+)([Dd])?");
    private static final Pattern FLOAT_LITERAL = Pattern.compile("(-?\\d+\\.\\d+)[Ff]");
    private static final Pattern INT_LITERAL = Pattern.compile("-?\\d+");
    private static final Pattern HEX_LITERAL = Pattern.compile("0[xX]([0-9a-fA-F]+)");
    private static final Pattern BOOLEAN_LITERAL = Pattern.compile("true|false");

    public ParseResult parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return ParseResult.empty();
        }

        input = input.trim();

        if (input.startsWith("/")) {
            return parseCommand(input);
        }

        return parseMethodCall(input);
    }

    private ParseResult parseCommand(String input) {
        Matcher matcher = COMMAND_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return ParseResult.error("Invalid command format");
        }

        String command = matcher.group(1).toLowerCase();
        String args = matcher.group(2);

        return ParseResult.command(command, args);
    }

    private ParseResult parseMethodCall(String input) {
        Matcher matcher = METHOD_CALL_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return ParseResult.error("Invalid method call format. Use: ClassName.methodName(args)");
        }

        String className = matcher.group(1).replace('.', '/');
        String methodName = matcher.group(2);
        String argsString = matcher.group(3).trim();

        Object[] args;
        try {
            args = parseArguments(argsString);
        } catch (Exception e) {
            return ParseResult.error("Failed to parse arguments: " + e.getMessage());
        }

        return ParseResult.methodCall(className, methodName, args);
    }

    public Object[] parseArguments(String argsString) throws Exception {
        if (argsString == null || argsString.isEmpty()) {
            return new Object[0];
        }

        List<Object> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int parenDepth = 0;

        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);

            if (c == '"' && (i == 0 || argsString.charAt(i - 1) != '\\')) {
                inString = !inString;
                current.append(c);
            } else if (!inString && c == '(') {
                parenDepth++;
                current.append(c);
            } else if (!inString && c == ')') {
                parenDepth--;
                current.append(c);
            } else if (!inString && c == ',' && parenDepth == 0) {
                String arg = current.toString().trim();
                if (!arg.isEmpty()) {
                    args.add(parseValue(arg));
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String lastArg = current.toString().trim();
        if (!lastArg.isEmpty()) {
            args.add(parseValue(lastArg));
        }

        return args.toArray();
    }

    public Object parseValue(String value) throws Exception {
        value = value.trim();

        if (value.equals("null")) {
            return null;
        }

        Matcher boolMatcher = BOOLEAN_LITERAL.matcher(value);
        if (boolMatcher.matches()) {
            return Boolean.parseBoolean(value);
        }

        Matcher stringMatcher = STRING_LITERAL.matcher(value);
        if (stringMatcher.matches()) {
            return unescapeString(stringMatcher.group(1));
        }

        Matcher charMatcher = CHAR_LITERAL.matcher(value);
        if (charMatcher.matches()) {
            return charMatcher.group(1).charAt(0);
        }

        Matcher hexMatcher = HEX_LITERAL.matcher(value);
        if (hexMatcher.matches()) {
            return Integer.parseInt(hexMatcher.group(1), 16);
        }

        Matcher longMatcher = LONG_LITERAL.matcher(value);
        if (longMatcher.matches()) {
            return Long.parseLong(longMatcher.group(1));
        }

        Matcher floatMatcher = FLOAT_LITERAL.matcher(value);
        if (floatMatcher.matches()) {
            return Float.parseFloat(floatMatcher.group(1));
        }

        Matcher doubleMatcher = DOUBLE_LITERAL.matcher(value);
        if (doubleMatcher.matches()) {
            return Double.parseDouble(doubleMatcher.group(1));
        }

        Matcher intMatcher = INT_LITERAL.matcher(value);
        if (intMatcher.matches()) {
            return Integer.parseInt(value);
        }

        throw new Exception("Unknown value type: " + value);
    }

    private String unescapeString(String s) {
        StringBuilder result = new StringBuilder();
        boolean escape = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n': result.append('\n'); break;
                    case 't': result.append('\t'); break;
                    case 'r': result.append('\r'); break;
                    case '\\': result.append('\\'); break;
                    case '"': result.append('"'); break;
                    case '\'': result.append('\''); break;
                    default: result.append('\\').append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    public String inferDescriptor(Object[] args) {
        StringBuilder sb = new StringBuilder("(");
        for (Object arg : args) {
            sb.append(inferTypeDescriptor(arg));
        }
        sb.append(")");
        return sb.toString();
    }

    private String inferTypeDescriptor(Object arg) {
        if (arg == null) {
            return "Ljava/lang/Object;";
        }
        if (arg instanceof Boolean) {
            return "Z";
        }
        if (arg instanceof Byte) {
            return "B";
        }
        if (arg instanceof Character) {
            return "C";
        }
        if (arg instanceof Short) {
            return "S";
        }
        if (arg instanceof Integer) {
            return "I";
        }
        if (arg instanceof Long) {
            return "J";
        }
        if (arg instanceof Float) {
            return "F";
        }
        if (arg instanceof Double) {
            return "D";
        }
        if (arg instanceof String) {
            return "Ljava/lang/String;";
        }
        return "Ljava/lang/Object;";
    }

    public static class ParseResult {
        public enum Type {
            EMPTY,
            COMMAND,
            METHOD_CALL,
            ERROR
        }

        private final Type type;
        private final String command;
        private final String commandArgs;
        private final String className;
        private final String methodName;
        private final Object[] methodArgs;
        private final String errorMessage;

        private ParseResult(Type type, String command, String commandArgs,
                           String className, String methodName, Object[] methodArgs,
                           String errorMessage) {
            this.type = type;
            this.command = command;
            this.commandArgs = commandArgs;
            this.className = className;
            this.methodName = methodName;
            this.methodArgs = methodArgs;
            this.errorMessage = errorMessage;
        }

        public static ParseResult empty() {
            return new ParseResult(Type.EMPTY, null, null, null, null, null, null);
        }

        public static ParseResult command(String command, String args) {
            return new ParseResult(Type.COMMAND, command, args, null, null, null, null);
        }

        public static ParseResult methodCall(String className, String methodName, Object[] args) {
            return new ParseResult(Type.METHOD_CALL, null, null, className, methodName, args, null);
        }

        public static ParseResult error(String message) {
            return new ParseResult(Type.ERROR, null, null, null, null, null, message);
        }

        public Type getType() {
            return type;
        }

        public String getCommand() {
            return command;
        }

        public String getCommandArgs() {
            return commandArgs;
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }

        public Object[] getMethodArgs() {
            return methodArgs;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isEmpty() {
            return type == Type.EMPTY;
        }

        public boolean isCommand() {
            return type == Type.COMMAND;
        }

        public boolean isMethodCall() {
            return type == Type.METHOD_CALL;
        }

        public boolean isError() {
            return type == Type.ERROR;
        }
    }
}
