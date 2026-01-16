package com.tonic.ui.editor.ast;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;
import java.util.Set;

public class ASTTokenMaker extends AbstractTokenMaker {

    private static final Set<String> STATEMENT_NODES = Set.of(
            "BlockStmt", "ExprStmt", "IfStmt", "WhileStmt", "DoWhileStmt", "ForStmt",
            "ForEachStmt", "SwitchStmt", "CaseStmt", "DefaultStmt", "BreakStmt",
            "ContinueStmt", "ReturnStmt", "ThrowStmt", "TryStmt", "CatchClause",
            "FinallyBlock", "SynchronizedStmt", "AssertStmt", "LabeledStmt",
            "EmptyStmt", "LocalVarDecl", "VarDeclarator"
    );

    private static final Set<String> EXPRESSION_NODES = Set.of(
            "BinaryExpr", "UnaryExpr", "AssignExpr", "TernaryExpr", "CastExpr",
            "InstanceOfExpr", "MethodCall", "FieldAccess", "ArrayAccess", "ArrayCreation",
            "ObjectCreation", "LambdaExpr", "MethodRef", "ClassExpr", "ThisExpr",
            "SuperExpr", "NameExpr", "LiteralExpr", "NullLiteral", "BoolLiteral",
            "IntLiteral", "LongLiteral", "FloatLiteral", "DoubleLiteral", "CharLiteral",
            "StringLiteral", "ParenExpr", "ConditionalExpr"
    );

    private static final Set<String> TYPE_NODES = Set.of(
            "PrimitiveType", "ClassType", "ArrayType", "VoidType", "TypeParameter",
            "WildcardType", "UnionType", "IntersectionType"
    );

    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "while", "do", "for", "switch", "case", "default",
            "break", "continue", "return", "throw", "try", "catch", "finally",
            "synchronized", "assert", "new", "instanceof", "this", "super",
            "true", "false", "null"
    );

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "long", "short", "byte", "char", "float", "double", "boolean", "void"
    );

    public static final int TOKEN_NODE = Token.RESERVED_WORD;
    public static final int TOKEN_EXPR_NODE = Token.FUNCTION;
    public static final int TOKEN_TYPE_NODE = Token.DATA_TYPE;
    public static final int TOKEN_LABEL = Token.VARIABLE;
    public static final int TOKEN_STRING = Token.LITERAL_STRING_DOUBLE_QUOTE;
    public static final int TOKEN_NUMBER = Token.LITERAL_NUMBER_DECIMAL_INT;
    public static final int TOKEN_KEYWORD = Token.RESERVED_WORD_2;
    public static final int TOKEN_COMMENT = Token.COMMENT_EOL;
    public static final int TOKEN_BRACKET = Token.SEPARATOR;
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
        int currentTokenType = initialTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {
                case Token.NULL:
                    currentTokenStart = i;

                    if (c == '/' && i + 1 < end && array[i + 1] == '/') {
                        currentTokenType = TOKEN_COMMENT;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = Token.WHITESPACE;
                    } else if (c == '"') {
                        currentTokenType = TOKEN_STRING;
                    } else if (c == '\'') {
                        currentTokenType = Token.LITERAL_CHAR;
                    } else if (Character.isDigit(c) || (c == '-' && i + 1 < end && Character.isDigit(array[i + 1]))) {
                        currentTokenType = TOKEN_NUMBER;
                    } else if (Character.isLetter(c) || c == '_') {
                        currentTokenType = Token.IDENTIFIER;
                    } else if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}') {
                        addToken(text, currentTokenStart, i, TOKEN_BRACKET, newStartOffset + currentTokenStart);
                    } else if (c == ':') {
                        addToken(text, currentTokenStart, i, Token.SEPARATOR, newStartOffset + currentTokenStart);
                    } else if (c == ',' || c == ';' || c == '.') {
                        addToken(text, currentTokenStart, i, Token.SEPARATOR, newStartOffset + currentTokenStart);
                    } else if (c == '=' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
                               c == '&' || c == '|' || c == '^' || c == '!' || c == '<' || c == '>' || c == '?') {
                        addToken(text, currentTokenStart, i, Token.OPERATOR, newStartOffset + currentTokenStart);
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

                case TOKEN_STRING:
                    if (c == '"' && (i == currentTokenStart + 1 || array[i - 1] != '\\')) {
                        addToken(text, currentTokenStart, i, TOKEN_STRING, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                    }
                    break;

                case Token.LITERAL_CHAR:
                    if (c == '\'' && (i == currentTokenStart + 1 || array[i - 1] != '\\')) {
                        addToken(text, currentTokenStart, i, Token.LITERAL_CHAR, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                    }
                    break;

                case TOKEN_NUMBER:
                    if (!Character.isDigit(c) && c != '.' && c != 'L' && c != 'l' && c != 'x' && c != 'X' && !((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                        addToken(text, currentTokenStart, i - 1, TOKEN_NUMBER, newStartOffset + currentTokenStart);
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
            case TOKEN_STRING:
                addToken(text, currentTokenStart, end - 1, TOKEN_STRING, newStartOffset + currentTokenStart);
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

        if (STATEMENT_NODES.contains(word)) {
            return TOKEN_NODE;
        }

        if (EXPRESSION_NODES.contains(word)) {
            return TOKEN_EXPR_NODE;
        }

        if (TYPE_NODES.contains(word)) {
            return TOKEN_TYPE_NODE;
        }

        if (KEYWORDS.contains(word)) {
            return TOKEN_KEYWORD;
        }

        if (PRIMITIVE_TYPES.contains(word)) {
            return TOKEN_TYPE_NODE;
        }

        if (word.equals("true") || word.equals("false") || word.equals("null")) {
            return TOKEN_KEYWORD;
        }

        if (word.endsWith("Stmt") || word.endsWith("Expr") || word.endsWith("Decl") || word.endsWith("Type")) {
            return TOKEN_NODE;
        }

        int colonAfter = end + 1;
        if (colonAfter < array.length) {
            int j = colonAfter;
            while (j < array.length && Character.isWhitespace(array[j])) {
                j++;
            }
            if (j < array.length && array[j] == ':') {
                return TOKEN_LABEL;
            }
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
