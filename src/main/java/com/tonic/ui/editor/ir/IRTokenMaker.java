package com.tonic.ui.editor.ir;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;
import java.util.Set;

public class IRTokenMaker extends AbstractTokenMaker {

    private static final Set<String> CONTROL_KEYWORDS = Set.of(
            "if", "goto", "return", "switch", "throw", "tableswitch", "lookupswitch"
    );

    private static final Set<String> INVOKE_KEYWORDS = Set.of(
            "invoke", "invokevirtual", "invokeinterface", "invokespecial", "invokestatic", "invokedynamic"
    );

    private static final Set<String> FIELD_KEYWORDS = Set.of(
            "getfield", "putfield", "getstatic", "putstatic"
    );

    private static final Set<String> ARITHMETIC_KEYWORDS = Set.of(
            "add", "sub", "mul", "div", "rem", "neg",
            "and", "or", "xor", "shl", "shr", "ushr",
            "iadd", "isub", "imul", "idiv", "irem", "ineg",
            "ladd", "lsub", "lmul", "ldiv", "lrem", "lneg",
            "fadd", "fsub", "fmul", "fdiv", "frem", "fneg",
            "dadd", "dsub", "dmul", "ddiv", "drem", "dneg",
            "iand", "ior", "ixor", "ishl", "ishr", "iushr",
            "land", "lor", "lxor", "lshl", "lshr", "lushr"
    );

    private static final Set<String> ARRAY_KEYWORDS = Set.of(
            "arrayload", "arraystore", "aaload", "aastore",
            "baload", "bastore", "caload", "castore",
            "saload", "sastore", "iaload", "iastore",
            "laload", "lastore", "faload", "fastore",
            "daload", "dastore", "arraylength"
    );

    private static final Set<String> NEW_KEYWORDS = Set.of(
            "new", "newarray", "anewarray", "multianewarray"
    );

    private static final Set<String> CAST_KEYWORDS = Set.of(
            "cast", "checkcast", "instanceof",
            "i2l", "i2f", "i2d", "l2i", "l2f", "l2d",
            "f2i", "f2l", "f2d", "d2i", "d2l", "d2f",
            "i2b", "i2c", "i2s"
    );

    private static final Set<String> LOAD_STORE_KEYWORDS = Set.of(
            "load", "store", "loadlocal", "storelocal"
    );

    public static final int TOKEN_BLOCK = Token.RESERVED_WORD;
    public static final int TOKEN_PHI = Token.RESERVED_WORD_2;
    public static final int TOKEN_VALUE = Token.VARIABLE;
    public static final int TOKEN_OPERATOR = Token.OPERATOR;
    public static final int TOKEN_CONTROL = Token.DATA_TYPE;
    public static final int TOKEN_INVOKE = Token.FUNCTION;
    public static final int TOKEN_FIELD = Token.MARKUP_TAG_NAME;
    public static final int TOKEN_CONST = Token.LITERAL_NUMBER_DECIMAL_INT;
    public static final int TOKEN_NEW = Token.PREPROCESSOR;
    public static final int TOKEN_CAST = Token.ANNOTATION;
    public static final int TOKEN_COMMENT = Token.COMMENT_EOL;
    public static final int TOKEN_HEADER = Token.MARKUP_TAG_ATTRIBUTE;
    public static final int TOKEN_DIVIDER = Token.COMMENT_DOCUMENTATION;

    @Override
    public TokenMap getWordsToHighlight() {
        return new TokenMap();
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int count = text.count;
        int end = offset + count;

        int newStartOffset = startOffset - offset;
        int currentTokenStart = offset;
        int currentTokenType = Token.NULL;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {
                case Token.NULL:
                    currentTokenStart = i;

                    if (c == '/' && i + 1 < end && array[i + 1] == '/') {
                        currentTokenType = TOKEN_COMMENT;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = Token.WHITESPACE;
                    } else if (c == 'v' && i + 1 < end && Character.isDigit(array[i + 1])) {
                        currentTokenType = TOKEN_VALUE;
                    } else if (Character.isDigit(c) || (c == '-' && i + 1 < end && Character.isDigit(array[i + 1]))) {
                        currentTokenType = TOKEN_CONST;
                    } else if (Character.isLetter(c) || c == '_') {
                        currentTokenType = Token.IDENTIFIER;
                    } else if (c == '=' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
                               c == '&' || c == '|' || c == '^' || c == '~' || c == '<' || c == '>') {
                        addToken(text, currentTokenStart, i, TOKEN_OPERATOR, newStartOffset + currentTokenStart);
                    } else if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' ||
                               c == ':' || c == ';' || c == ',' || c == '.') {
                        addToken(text, currentTokenStart, i, Token.SEPARATOR, newStartOffset + currentTokenStart);
                    } else if (c == '#') {
                        currentTokenType = TOKEN_CONST;
                    } else if (c == '"') {
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                    } else {
                        addToken(text, currentTokenStart, i, Token.IDENTIFIER, newStartOffset + currentTokenStart);
                    }
                    break;

                case Token.WHITESPACE:
                    if (!Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case TOKEN_VALUE:
                    if (!Character.isDigit(c)) {
                        addToken(text, currentTokenStart, i - 1, TOKEN_VALUE, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case TOKEN_CONST:
                    if (!Character.isDigit(c) && c != '.' && c != 'L' && c != 'l' && c != 'x' && c != 'X' &&
                            !((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                        addToken(text, currentTokenStart, i - 1, TOKEN_CONST, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.IDENTIFIER:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        int tokenType = classifyIdentifier(array, currentTokenStart, i - 1);
                        addToken(text, currentTokenStart, i - 1, tokenType, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.LITERAL_STRING_DOUBLE_QUOTE:
                    if (c == '"' && array[i - 1] != '\\') {
                        addToken(text, currentTokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                    }
                    break;

                case TOKEN_COMMENT:
                    break;

                default:
                    if (Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1, currentTokenType, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
            }
        }

        switch (currentTokenType) {
            case Token.NULL:
                addNullToken();
                break;
            case Token.IDENTIFIER:
                int tokenType = classifyIdentifier(array, currentTokenStart, end - 1);
                addToken(text, currentTokenStart, end - 1, tokenType, newStartOffset + currentTokenStart);
                addNullToken();
                break;
            case TOKEN_COMMENT:
                int commentTokenType = classifyComment(array, currentTokenStart, end - 1);
                addToken(text, currentTokenStart, end - 1, commentTokenType, newStartOffset + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
                addNullToken();
        }

        return firstToken;
    }

    private int classifyIdentifier(char[] array, int start, int end) {
        String word = new String(array, start, end - start + 1);
        String lower = word.toLowerCase();

        if (word.equals("BLOCK") || lower.equals("block")) {
            return TOKEN_BLOCK;
        }

        if (word.equals("PHI") || lower.equals("phi")) {
            return TOKEN_PHI;
        }

        if (lower.equals("const")) {
            return TOKEN_CONST;
        }

        if (CONTROL_KEYWORDS.contains(lower)) {
            return TOKEN_CONTROL;
        }

        if (INVOKE_KEYWORDS.contains(lower)) {
            return TOKEN_INVOKE;
        }

        if (FIELD_KEYWORDS.contains(lower)) {
            return TOKEN_FIELD;
        }

        if (ARITHMETIC_KEYWORDS.contains(lower)) {
            return TOKEN_OPERATOR;
        }

        if (ARRAY_KEYWORDS.contains(lower)) {
            return TOKEN_FIELD;
        }

        if (NEW_KEYWORDS.contains(lower)) {
            return TOKEN_NEW;
        }

        if (CAST_KEYWORDS.contains(lower)) {
            return TOKEN_CAST;
        }

        if (LOAD_STORE_KEYWORDS.contains(lower)) {
            return TOKEN_VALUE;
        }

        return Token.IDENTIFIER;
    }

    private int classifyComment(char[] array, int start, int end) {
        String comment = new String(array, start, end - start + 1);

        if (comment.contains("=========")) {
            return TOKEN_DIVIDER;
        }

        if (comment.contains("public") || comment.contains("private") ||
            comment.contains("protected") || comment.contains("static") ||
            comment.contains("final") || comment.contains("abstract") ||
            comment.contains("synchronized") || comment.contains("native")) {
            return TOKEN_HEADER;
        }

        if (comment.startsWith("// Method ") || comment.startsWith("// Class:") ||
            comment.startsWith("// Super:") || comment.startsWith("// Implements:")) {
            return TOKEN_DIVIDER;
        }

        return TOKEN_COMMENT;
    }
}
