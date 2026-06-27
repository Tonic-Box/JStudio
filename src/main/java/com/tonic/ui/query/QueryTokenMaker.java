package com.tonic.ui.query;

import com.tonic.util.Opcode;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;
import java.util.HashSet;
import java.util.Set;

/**
 * RSyntaxTextArea token maker for the Query DSL. Highlights the closed grammar — clause and
 * structural keywords, word operators, symbol operators, separators, and literals (string, regex,
 * number, boolean/null) — while leaving the open accessor vocabulary ({@code name}, {@code value},
 * {@code arg}, {@code call}, ...) as plain identifiers, so it never drifts from the registry. Keyword
 * matching is case-insensitive, mirroring the lexer.
 */
public class QueryTokenMaker extends AbstractTokenMaker {

    private static final Set<String> KEYWORDS = Set.of(
            "find", "show", "in", "where", "limit", "order", "by", "as",
            "has", "any", "all", "none", "count", "and", "or", "not", "sequence", "seq", "during", "asc", "desc");

    private static final Set<String> WORD_OPERATORS = Set.of(
            "matches", "contains", "startswith", "endswith", "flowsto", "flowsfrom");

    private static final Set<String> TYPES = Set.of(
            "void", "boolean", "byte", "char", "short", "int", "long", "float", "double");

    private static final Set<String> CONSTANTS = Set.of("true", "false", "null");

    /** Every JVM opcode mnemonic, sourced from YABR's {@link Opcode} enum so it never drifts. */
    private static final Set<String> OPCODES = opcodeMnemonics();

    private static final String REGEX_FLAGS = "imsxuUdcl";

    private static Set<String> opcodeMnemonics() {
        Set<String> set = new HashSet<>();
        for (Opcode op : Opcode.values()) {
            String m = op.getMnemonic();
            if (m != null) {
                set.add(m.toLowerCase());
            }
        }
        return set;
    }

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
                    if (Character.isWhitespace(c)) {
                        currentTokenType = Token.WHITESPACE;
                    } else if (c == '"') {
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                    } else if (c == '/') {
                        currentTokenType = Token.REGEX;
                    } else if (Character.isDigit(c)) {
                        currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
                    } else if (Character.isLetter(c) || c == '_') {
                        currentTokenType = Token.IDENTIFIER;
                    } else if (c == '=' || c == '!' || c == '<' || c == '>') {
                        if (i + 1 < end && array[i + 1] == '=') {
                            addToken(text, i, i + 1, Token.OPERATOR, newStartOffset + i);
                            i++;
                        } else {
                            addToken(text, i, i, Token.OPERATOR, newStartOffset + i);
                        }
                    } else if (c == '*' || c == '+') {
                        addToken(text, i, i, Token.OPERATOR, newStartOffset + i);
                    } else if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}'
                            || c == ',' || c == '.') {
                        addToken(text, i, i, Token.SEPARATOR, newStartOffset + i);
                    } else {
                        addToken(text, i, i, Token.IDENTIFIER, newStartOffset + i);
                    }
                    break;

                case Token.WHITESPACE:
                    if (!Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.IDENTIFIER:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        addToken(text, currentTokenStart, i - 1, classifyWord(array, currentTokenStart, i - 1), newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.LITERAL_NUMBER_DECIMAL_INT:
                    if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') {
                        addToken(text, currentTokenStart, i - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart);
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

                case Token.REGEX:
                    if (c == '/' && array[i - 1] != '\\') {
                        int regexEnd = i;
                        while (regexEnd + 1 < end && REGEX_FLAGS.indexOf(array[regexEnd + 1]) >= 0) {
                            regexEnd++;
                        }
                        addToken(text, currentTokenStart, regexEnd, Token.REGEX, newStartOffset + currentTokenStart);
                        i = regexEnd;
                        currentTokenType = Token.NULL;
                    }
                    break;

                default:
                    break;
            }
        }

        switch (currentTokenType) {
            case Token.NULL:
                addNullToken();
                break;
            case Token.IDENTIFIER:
                addToken(text, currentTokenStart, end - 1, classifyWord(array, currentTokenStart, end - 1), newStartOffset + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
                addNullToken();
        }

        return firstToken;
    }

    private int classifyWord(char[] array, int start, int end) {
        String lower = new String(array, start, end - start + 1).toLowerCase();
        if (KEYWORDS.contains(lower)) {
            return Token.RESERVED_WORD;
        }
        if (WORD_OPERATORS.contains(lower)) {
            return Token.FUNCTION;
        }
        if (TYPES.contains(lower)) {
            return Token.DATA_TYPE;
        }
        if (CONSTANTS.contains(lower)) {
            return Token.LITERAL_BOOLEAN;
        }
        if (OPCODES.contains(lower)) {
            return Token.RESERVED_WORD_2;
        }
        return Token.IDENTIFIER;
    }
}
