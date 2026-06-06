package com.tonic.ui.editor.llvm;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;
import java.util.Set;

/**
 * Syntax highlighter for textual LLVM IR: {@code ;} comments, {@code %local} / {@code @global}
 * identifiers (including quoted symbols), instruction keywords, types, and compare predicates.
 */
public class LlvmTokenMaker extends AbstractTokenMaker {

    private static final Set<String> CONTROL_KEYWORDS = Set.of(
            "define", "declare", "ret", "br", "switch", "call", "phi", "select", "label", "to",
            "unreachable", "target", "datalayout", "triple", "source_filename", "global", "constant"
    );

    private static final Set<String> OPCODE_KEYWORDS = Set.of(
            "add", "sub", "mul", "sdiv", "udiv", "srem", "urem",
            "fadd", "fsub", "fmul", "fdiv", "frem", "fneg",
            "and", "or", "xor", "shl", "lshr", "ashr",
            "icmp", "fcmp",
            "zext", "sext", "trunc", "fptrunc", "fpext", "fptosi", "fptoui", "sitofp", "uitofp",
            "bitcast", "ptrtoint", "inttoptr", "getelementptr", "load", "store", "alloca"
    );

    private static final Set<String> TYPE_KEYWORDS = Set.of(
            "i1", "i8", "i16", "i32", "i64", "i128", "half", "float", "double", "void", "ptr"
    );

    private static final Set<String> PREDICATE_KEYWORDS = Set.of(
            "eq", "ne", "slt", "sle", "sgt", "sge", "ult", "ule", "ugt", "uge",
            "oeq", "one", "olt", "ole", "ogt", "oge", "ord", "uno", "ueq", "une"
    );

    public static final int TOKEN_COMMENT = Token.COMMENT_EOL;
    public static final int TOKEN_LOCAL = Token.VARIABLE;
    public static final int TOKEN_GLOBAL = Token.FUNCTION;
    public static final int TOKEN_CONTROL = Token.RESERVED_WORD;
    public static final int TOKEN_OPCODE = Token.RESERVED_WORD_2;
    public static final int TOKEN_TYPE = Token.DATA_TYPE;
    public static final int TOKEN_PREDICATE = Token.OPERATOR;
    public static final int TOKEN_NUMBER = Token.LITERAL_NUMBER_DECIMAL_INT;
    public static final int TOKEN_STRING = Token.LITERAL_STRING_DOUBLE_QUOTE;

    private static final int STATE_GLOBAL_QUOTED = -2;

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

                    if (c == ';') {
                        currentTokenType = TOKEN_COMMENT;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = Token.WHITESPACE;
                    } else if (c == '%') {
                        currentTokenType = TOKEN_LOCAL;
                    } else if (c == '@') {
                        currentTokenType = TOKEN_GLOBAL;
                    } else if (c == '"') {
                        currentTokenType = TOKEN_STRING;
                    } else if (Character.isDigit(c) || (c == '-' && i + 1 < end && Character.isDigit(array[i + 1]))) {
                        currentTokenType = TOKEN_NUMBER;
                    } else if (Character.isLetter(c) || c == '_' || c == '.') {
                        currentTokenType = Token.IDENTIFIER;
                    } else if (c == '=' || c == '*' || c == '<' || c == '>') {
                        addToken(text, currentTokenStart, i, TOKEN_PREDICATE, newStartOffset + currentTokenStart);
                    } else if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' ||
                               c == ':' || c == ',') {
                        addToken(text, currentTokenStart, i, Token.SEPARATOR, newStartOffset + currentTokenStart);
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

                case TOKEN_LOCAL:
                    if (!isIdentifierChar(c)) {
                        addToken(text, currentTokenStart, i - 1, TOKEN_LOCAL, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case TOKEN_GLOBAL:
                    if (i == currentTokenStart + 1 && c == '"') {
                        currentTokenType = STATE_GLOBAL_QUOTED;
                    } else if (!isIdentifierChar(c)) {
                        addToken(text, currentTokenStart, i - 1, TOKEN_GLOBAL, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case STATE_GLOBAL_QUOTED:
                    if (c == '"' && array[i - 1] != '\\') {
                        addToken(text, currentTokenStart, i, TOKEN_GLOBAL, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                    }
                    break;

                case TOKEN_STRING:
                    if (c == '"' && array[i - 1] != '\\') {
                        addToken(text, currentTokenStart, i, TOKEN_STRING, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                    }
                    break;

                case TOKEN_NUMBER:
                    if (!Character.isLetterOrDigit(c) && c != '.' && c != '-' && c != '+') {
                        addToken(text, currentTokenStart, i - 1, TOKEN_NUMBER, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.IDENTIFIER:
                    if (!isIdentifierChar(c)) {
                        int tokenType = classifyIdentifier(array, currentTokenStart, i - 1);
                        addToken(text, currentTokenStart, i - 1, tokenType, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
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
            case STATE_GLOBAL_QUOTED:
                addToken(text, currentTokenStart, end - 1, TOKEN_GLOBAL, newStartOffset + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
                addNullToken();
        }

        return firstToken;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private int classifyIdentifier(char[] array, int start, int end) {
        String word = new String(array, start, end - start + 1);

        if (TYPE_KEYWORDS.contains(word)) {
            return TOKEN_TYPE;
        }
        if (CONTROL_KEYWORDS.contains(word)) {
            return TOKEN_CONTROL;
        }
        if (OPCODE_KEYWORDS.contains(word)) {
            return TOKEN_OPCODE;
        }
        if (PREDICATE_KEYWORDS.contains(word)) {
            return TOKEN_PREDICATE;
        }
        return Token.IDENTIFIER;
    }
}
