package com.tonic.ui.editor.bytecode;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;
import java.util.Set;

public class BytecodeTokenMaker extends AbstractTokenMaker {

    private static final Set<String> LOAD_OPCODES = Set.of(
            "aaload", "aload", "aload_0", "aload_1", "aload_2", "aload_3",
            "baload", "caload", "daload", "dload", "dload_0", "dload_1", "dload_2", "dload_3",
            "faload", "fload", "fload_0", "fload_1", "fload_2", "fload_3",
            "iaload", "iload", "iload_0", "iload_1", "iload_2", "iload_3",
            "laload", "lload", "lload_0", "lload_1", "lload_2", "lload_3",
            "saload", "ldc", "ldc_w", "ldc2_w"
    );

    private static final Set<String> STORE_OPCODES = Set.of(
            "aastore", "astore", "astore_0", "astore_1", "astore_2", "astore_3",
            "bastore", "castore", "dastore", "dstore", "dstore_0", "dstore_1", "dstore_2", "dstore_3",
            "fastore", "fstore", "fstore_0", "fstore_1", "fstore_2", "fstore_3",
            "iastore", "istore", "istore_0", "istore_1", "istore_2", "istore_3",
            "lastore", "lstore", "lstore_0", "lstore_1", "lstore_2", "lstore_3",
            "sastore"
    );

    private static final Set<String> INVOKE_OPCODES = Set.of(
            "invokedynamic", "invokeinterface", "invokespecial", "invokestatic", "invokevirtual"
    );

    private static final Set<String> FIELD_OPCODES = Set.of(
            "getfield", "getstatic", "putfield", "putstatic"
    );

    private static final Set<String> BRANCH_OPCODES = Set.of(
            "goto", "goto_w", "if_acmpeq", "if_acmpne",
            "if_icmpeq", "if_icmpge", "if_icmpgt", "if_icmple", "if_icmplt", "if_icmpne",
            "ifeq", "ifge", "ifgt", "ifle", "iflt", "ifne", "ifnonnull", "ifnull",
            "jsr", "jsr_w", "lookupswitch", "ret", "tableswitch"
    );

    private static final Set<String> STACK_OPCODES = Set.of(
            "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1", "dup2_x2",
            "pop", "pop2", "swap", "nop"
    );

    private static final Set<String> CONST_OPCODES = Set.of(
            "aconst_null", "bipush", "sipush",
            "dconst_0", "dconst_1",
            "fconst_0", "fconst_1", "fconst_2",
            "iconst_0", "iconst_1", "iconst_2", "iconst_3", "iconst_4", "iconst_5", "iconst_m1",
            "lconst_0", "lconst_1"
    );

    private static final Set<String> RETURN_OPCODES = Set.of(
            "areturn", "dreturn", "freturn", "ireturn", "lreturn", "return", "athrow"
    );

    private static final Set<String> NEW_OPCODES = Set.of(
            "new", "newarray", "anewarray", "multianewarray"
    );

    private static final Set<String> ARITHMETIC_OPCODES = Set.of(
            "dadd", "ddiv", "dmul", "dneg", "drem", "dsub",
            "fadd", "fdiv", "fmul", "fneg", "frem", "fsub",
            "iadd", "iand", "idiv", "imul", "ineg", "ior", "irem", "ishl", "ishr", "isub", "iushr", "ixor",
            "ladd", "land", "ldiv", "lmul", "lneg", "lor", "lrem", "lshl", "lshr", "lsub", "lushr", "lxor"
    );

    private static final Set<String> TYPE_OPCODES = Set.of(
            "checkcast", "instanceof", "d2f", "d2i", "d2l",
            "f2d", "f2i", "f2l", "i2b", "i2c", "i2d", "i2f", "i2l", "i2s",
            "l2d", "l2f", "l2i", "arraylength", "dcmpg", "dcmpl", "fcmpg", "fcmpl", "lcmp",
            "monitorenter", "monitorexit", "wide"
    );

    public static final int TOKEN_OFFSET = Token.LITERAL_NUMBER_DECIMAL_INT;
    public static final int TOKEN_OPCODE_LOAD = Token.RESERVED_WORD;
    public static final int TOKEN_OPCODE_STORE = Token.RESERVED_WORD_2;
    public static final int TOKEN_OPCODE_INVOKE = Token.FUNCTION;
    public static final int TOKEN_OPCODE_FIELD = Token.VARIABLE;
    public static final int TOKEN_OPCODE_BRANCH = Token.DATA_TYPE;
    public static final int TOKEN_OPCODE_STACK = Token.OPERATOR;
    public static final int TOKEN_OPCODE_CONST = Token.LITERAL_NUMBER_HEXADECIMAL;
    public static final int TOKEN_OPCODE_RETURN = Token.ANNOTATION;
    public static final int TOKEN_OPCODE_NEW = Token.PREPROCESSOR;
    public static final int TOKEN_OPCODE_ARITHMETIC = Token.LITERAL_NUMBER_FLOAT;
    public static final int TOKEN_OPCODE_TYPE = Token.MARKUP_TAG_NAME;
    public static final int TOKEN_COMMENT = Token.COMMENT_EOL;
    public static final int TOKEN_HEADER = Token.MARKUP_TAG_ATTRIBUTE;
    public static final int TOKEN_DIVIDER = Token.COMMENT_DOCUMENTATION;

    @Override
    public TokenMap getWordsToHighlight() {
        return new TokenMap();
    }

    @Override
    public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
        if (tokenType == Token.IDENTIFIER) {
            int value = wordsToHighlight.get(segment, start, end);
            if (value != -1) {
                tokenType = value;
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset);
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

        boolean lineStarted = false;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {
                case Token.NULL:
                    currentTokenStart = i;

                    if (c == '/' && i + 1 < end && array[i + 1] == '/') {
                        currentTokenType = TOKEN_COMMENT;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = Token.WHITESPACE;
                    } else if (Character.isDigit(c) && !lineStarted) {
                        currentTokenType = TOKEN_OFFSET;
                    } else if (Character.isLetter(c) || c == '_') {
                        currentTokenType = Token.IDENTIFIER;
                        lineStarted = true;
                    } else if (c == ':') {
                        addToken(text, currentTokenStart, i, Token.SEPARATOR, newStartOffset + currentTokenStart);
                    } else if (c == '#' || c == '(' || c == ')' || c == '[' || c == ']' || c == '<' || c == '>' || c == ';' || c == ',') {
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

                case TOKEN_OFFSET:
                    if (c == ':') {
                        addToken(text, currentTokenStart, i - 1, TOKEN_OFFSET, newStartOffset + currentTokenStart);
                        addToken(text, i, i, Token.SEPARATOR, newStartOffset + i);
                        currentTokenType = Token.NULL;
                        lineStarted = true;
                    } else if (!Character.isDigit(c)) {
                        addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        i--;
                        lineStarted = true;
                    }
                    break;

                case Token.IDENTIFIER:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        int tokenType = getOpcodeTokenType(array, currentTokenStart, i - 1);
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
                int tokenType = getOpcodeTokenType(array, currentTokenStart, end - 1);
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

    private int getOpcodeTokenType(char[] array, int start, int end) {
        String word = new String(array, start, end - start + 1).toLowerCase();

        if (LOAD_OPCODES.contains(word)) return TOKEN_OPCODE_LOAD;
        if (STORE_OPCODES.contains(word)) return TOKEN_OPCODE_STORE;
        if (INVOKE_OPCODES.contains(word)) return TOKEN_OPCODE_INVOKE;
        if (FIELD_OPCODES.contains(word)) return TOKEN_OPCODE_FIELD;
        if (BRANCH_OPCODES.contains(word)) return TOKEN_OPCODE_BRANCH;
        if (STACK_OPCODES.contains(word)) return TOKEN_OPCODE_STACK;
        if (CONST_OPCODES.contains(word)) return TOKEN_OPCODE_CONST;
        if (RETURN_OPCODES.contains(word)) return TOKEN_OPCODE_RETURN;
        if (NEW_OPCODES.contains(word)) return TOKEN_OPCODE_NEW;
        if (ARITHMETIC_OPCODES.contains(word)) return TOKEN_OPCODE_ARITHMETIC;
        if (TYPE_OPCODES.contains(word)) return TOKEN_OPCODE_TYPE;

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

        if (comment.startsWith("// Method ")) {
            return TOKEN_DIVIDER;
        }

        return TOKEN_COMMENT;
    }
}
