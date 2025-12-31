package com.tonic.ui.script.engine;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for JStudio script language.
 */
public class ScriptParser {

    private final List<ScriptToken> tokens;
    private int current = 0;
    @Getter
    private final List<String> errors = new ArrayList<>();

    public ScriptParser(List<ScriptToken> tokens) {
        this.tokens = tokens;
    }

    public List<ScriptAST> parse() {
        List<ScriptAST> statements = new ArrayList<>();

        while (!isAtEnd()) {
            try {
                ScriptAST stmt = parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }
            } catch (ParseException e) {
                errors.add(e.getMessage());
                synchronize();
            }
        }

        return statements;
    }

    // ==================== Statements ====================

    private ScriptAST parseStatement() {
        if (check(ScriptToken.Type.LET) || check(ScriptToken.Type.CONST)) {
            return parseVarDecl();
        }
        if (check(ScriptToken.Type.IF)) {
            return parseIf();
        }
        if (check(ScriptToken.Type.RETURN)) {
            return parseReturn();
        }
        if (check(ScriptToken.Type.LBRACE)) {
            return parseBlock();
        }
        if (check(ScriptToken.Type.WHILE)) {
            return parseWhile();
        }
        if (check(ScriptToken.Type.FOR)) {
            return parseFor();
        }
        if (check(ScriptToken.Type.DO)) {
            return parseDoWhile();
        }
        if (check(ScriptToken.Type.BREAK)) {
            return parseBreak();
        }
        if (check(ScriptToken.Type.CONTINUE)) {
            return parseContinue();
        }
        if (check(ScriptToken.Type.TRY)) {
            return parseTry();
        }
        if (check(ScriptToken.Type.THROW)) {
            return parseThrow();
        }

        return parseExpressionStatement();
    }

    private ScriptAST parseVarDecl() {
        boolean isConst = check(ScriptToken.Type.CONST);
        advance(); // consume let/const

        ScriptToken name = consume(ScriptToken.Type.IDENTIFIER, "Expected variable name");

        ScriptAST initializer = null;
        if (match(ScriptToken.Type.EQUALS)) {
            initializer = parseExpression();
        }

        consumeSemicolon();
        return new ScriptAST.VarDeclStmt(name.getValue(), initializer, isConst);
    }

    private ScriptAST parseIf() {
        advance(); // consume 'if'
        consume(ScriptToken.Type.LPAREN, "Expected '(' after 'if'");
        ScriptAST condition = parseExpression();
        consume(ScriptToken.Type.RPAREN, "Expected ')' after if condition");

        ScriptAST thenBranch = parseStatement();
        ScriptAST elseBranch = null;

        if (match(ScriptToken.Type.ELSE)) {
            elseBranch = parseStatement();
        }

        return new ScriptAST.IfStmt(condition, thenBranch, elseBranch);
    }

    private ScriptAST parseReturn() {
        advance(); // consume 'return'

        ScriptAST value = null;
        if (!check(ScriptToken.Type.SEMICOLON) && !check(ScriptToken.Type.RBRACE) && !isAtEnd()) {
            value = parseExpression();
        }

        consumeSemicolon();
        return new ScriptAST.ReturnStmt(value);
    }

    private ScriptAST parseBlock() {
        advance(); // consume '{'
        List<ScriptAST> statements = new ArrayList<>();

        while (!check(ScriptToken.Type.RBRACE) && !isAtEnd()) {
            ScriptAST stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        consume(ScriptToken.Type.RBRACE, "Expected '}' after block");
        return new ScriptAST.BlockStmt(statements);
    }

    private ScriptAST parseWhile() {
        advance(); // consume 'while'
        consume(ScriptToken.Type.LPAREN, "Expected '(' after 'while'");
        ScriptAST condition = parseExpression();
        consume(ScriptToken.Type.RPAREN, "Expected ')' after while condition");
        ScriptAST body = parseStatement();
        return new ScriptAST.WhileStmt(condition, body);
    }

    private ScriptAST parseDoWhile() {
        advance(); // consume 'do'
        ScriptAST body = parseStatement();
        consume(ScriptToken.Type.WHILE, "Expected 'while' after do body");
        consume(ScriptToken.Type.LPAREN, "Expected '(' after 'while'");
        ScriptAST condition = parseExpression();
        consume(ScriptToken.Type.RPAREN, "Expected ')' after condition");
        consumeSemicolon();
        return new ScriptAST.WhileStmt(condition, body);
    }

    private ScriptAST parseFor() {
        advance(); // consume 'for'
        consume(ScriptToken.Type.LPAREN, "Expected '(' after 'for'");

        if (check(ScriptToken.Type.LET) || check(ScriptToken.Type.CONST)) {
            boolean isConst = check(ScriptToken.Type.CONST);
            advance();
            ScriptToken varName = consume(ScriptToken.Type.IDENTIFIER, "Expected variable name");

            if (check(ScriptToken.Type.OF) || check(ScriptToken.Type.IN)) {
                boolean forIn = check(ScriptToken.Type.IN);
                advance();
                ScriptAST iterable = parseExpression();
                consume(ScriptToken.Type.RPAREN, "Expected ')' after for-each");
                ScriptAST body = parseStatement();
                return new ScriptAST.ForEachStmt(varName.getValue(), isConst, iterable, body, forIn);
            }

            ScriptAST initializer = null;
            if (match(ScriptToken.Type.EQUALS)) {
                initializer = parseExpression();
            }
            ScriptAST init = new ScriptAST.VarDeclStmt(varName.getValue(), initializer, isConst);

            consume(ScriptToken.Type.SEMICOLON, "Expected ';' after for initializer");
            ScriptAST condition = null;
            if (!check(ScriptToken.Type.SEMICOLON)) {
                condition = parseExpression();
            }
            consume(ScriptToken.Type.SEMICOLON, "Expected ';' after for condition");
            ScriptAST update = null;
            if (!check(ScriptToken.Type.RPAREN)) {
                update = parseExpression();
            }
            consume(ScriptToken.Type.RPAREN, "Expected ')' after for clauses");
            ScriptAST body = parseStatement();
            return new ScriptAST.ForStmt(init, condition, update, body);
        }

        ScriptAST init = null;
        if (!check(ScriptToken.Type.SEMICOLON)) {
            init = new ScriptAST.ExpressionStmt(parseExpression());
        }
        consume(ScriptToken.Type.SEMICOLON, "Expected ';' after for initializer");

        ScriptAST condition = null;
        if (!check(ScriptToken.Type.SEMICOLON)) {
            condition = parseExpression();
        }
        consume(ScriptToken.Type.SEMICOLON, "Expected ';' after for condition");

        ScriptAST update = null;
        if (!check(ScriptToken.Type.RPAREN)) {
            update = parseExpression();
        }
        consume(ScriptToken.Type.RPAREN, "Expected ')' after for clauses");

        ScriptAST body = parseStatement();
        return new ScriptAST.ForStmt(init, condition, update, body);
    }

    private ScriptAST parseBreak() {
        advance(); // consume 'break'
        consumeSemicolon();
        return new ScriptAST.BreakStmt();
    }

    private ScriptAST parseContinue() {
        advance(); // consume 'continue'
        consumeSemicolon();
        return new ScriptAST.ContinueStmt();
    }

    private ScriptAST parseTry() {
        advance(); // consume 'try'
        ScriptAST tryBlock = parseBlock();

        String catchParam = null;
        ScriptAST catchBlock = null;
        if (match(ScriptToken.Type.CATCH)) {
            consume(ScriptToken.Type.LPAREN, "Expected '(' after 'catch'");
            ScriptToken param = consume(ScriptToken.Type.IDENTIFIER, "Expected catch parameter");
            catchParam = param.getValue();
            consume(ScriptToken.Type.RPAREN, "Expected ')' after catch parameter");
            catchBlock = parseBlock();
        }

        ScriptAST finallyBlock = null;
        if (match(ScriptToken.Type.FINALLY)) {
            finallyBlock = parseBlock();
        }

        if (catchBlock == null && finallyBlock == null) {
            throw new ParseException("Expected 'catch' or 'finally' after 'try'");
        }

        return new ScriptAST.TryStmt(tryBlock, catchParam, catchBlock, finallyBlock);
    }

    private ScriptAST parseThrow() {
        advance(); // consume 'throw'
        ScriptAST expression = parseExpression();
        consumeSemicolon();
        return new ScriptAST.ThrowStmt(expression);
    }

    private ScriptAST parseExpressionStatement() {
        ScriptAST expr = parseExpression();
        consumeSemicolon();
        return new ScriptAST.ExpressionStmt(expr);
    }

    // ==================== Expressions ====================

    private ScriptAST parseExpression() {
        return parseAssignment();
    }

    private ScriptAST parseAssignment() {
        ScriptAST expr = parseTernary();

        if (match(ScriptToken.Type.EQUALS)) {
            ScriptAST value = parseAssignment();

            if (expr instanceof ScriptAST.IdentifierExpr) {
                return new ScriptAST.BinaryExpr(expr, "=", value);
            } else if (expr instanceof ScriptAST.MemberAccessExpr || expr instanceof ScriptAST.ArrayAccessExpr) {
                return new ScriptAST.BinaryExpr(expr, "=", value);
            }

            throw new ParseException("Invalid assignment target");
        }

        if (match(ScriptToken.Type.PLUS_EQUALS, ScriptToken.Type.MINUS_EQUALS,
                  ScriptToken.Type.STAR_EQUALS, ScriptToken.Type.SLASH_EQUALS)) {
            String operator = previous().getValue();
            ScriptAST value = parseAssignment();

            if (expr instanceof ScriptAST.IdentifierExpr ||
                expr instanceof ScriptAST.MemberAccessExpr ||
                expr instanceof ScriptAST.ArrayAccessExpr) {
                return new ScriptAST.AssignmentExpr(expr, operator, value);
            }

            throw new ParseException("Invalid compound assignment target");
        }

        return expr;
    }

    private ScriptAST parseTernary() {
        ScriptAST expr = parseOr();

        if (match(ScriptToken.Type.QUESTION)) {
            ScriptAST thenBranch = parseExpression();
            consume(ScriptToken.Type.COLON, "Expected ':' in ternary expression");
            ScriptAST elseBranch = parseTernary();
            return new ScriptAST.TernaryExpr(expr, thenBranch, elseBranch);
        }

        return expr;
    }

    private ScriptAST parseOr() {
        ScriptAST expr = parseAnd();

        while (match(ScriptToken.Type.OR)) {
            ScriptAST right = parseAnd();
            expr = new ScriptAST.BinaryExpr(expr, "||", right);
        }

        return expr;
    }

    private ScriptAST parseAnd() {
        ScriptAST expr = parseEquality();

        while (match(ScriptToken.Type.AND)) {
            ScriptAST right = parseEquality();
            expr = new ScriptAST.BinaryExpr(expr, "&&", right);
        }

        return expr;
    }

    private ScriptAST parseEquality() {
        ScriptAST expr = parseComparison();

        while (match(ScriptToken.Type.EQUALS_EQUALS, ScriptToken.Type.NOT_EQUALS)) {
            String operator = previous().getValue();
            ScriptAST right = parseComparison();
            expr = new ScriptAST.BinaryExpr(expr, operator, right);
        }

        return expr;
    }

    private ScriptAST parseComparison() {
        ScriptAST expr = parseTerm();

        while (match(ScriptToken.Type.LESS, ScriptToken.Type.LESS_EQUALS,
                     ScriptToken.Type.GREATER, ScriptToken.Type.GREATER_EQUALS)) {
            String operator = previous().getValue();
            ScriptAST right = parseTerm();
            expr = new ScriptAST.BinaryExpr(expr, operator, right);
        }

        return expr;
    }

    private ScriptAST parseTerm() {
        ScriptAST expr = parseFactor();

        while (match(ScriptToken.Type.PLUS, ScriptToken.Type.MINUS)) {
            String operator = previous().getValue();
            ScriptAST right = parseFactor();
            expr = new ScriptAST.BinaryExpr(expr, operator, right);
        }

        return expr;
    }

    private ScriptAST parseFactor() {
        ScriptAST expr = parseUnary();

        while (match(ScriptToken.Type.STAR, ScriptToken.Type.SLASH, ScriptToken.Type.PERCENT)) {
            String operator = previous().getValue();
            ScriptAST right = parseUnary();
            expr = new ScriptAST.BinaryExpr(expr, operator, right);
        }

        return expr;
    }

    private ScriptAST parseUnary() {
        if (match(ScriptToken.Type.NOT, ScriptToken.Type.MINUS)) {
            String operator = previous().getValue();
            ScriptAST operand = parseUnary();
            return new ScriptAST.UnaryExpr(operator, operand);
        }

        if (match(ScriptToken.Type.PLUS_PLUS, ScriptToken.Type.MINUS_MINUS)) {
            String operator = previous().getValue();
            ScriptAST operand = parseUnary();
            return new ScriptAST.UpdateExpr(operand, operator, true);
        }

        return parsePostfix();
    }

    private ScriptAST parsePostfix() {
        ScriptAST expr = parsePrimary();

        while (true) {
            if (match(ScriptToken.Type.LPAREN)) {
                expr = finishCall(expr);
            } else if (match(ScriptToken.Type.DOT)) {
                ScriptToken name = consume(ScriptToken.Type.IDENTIFIER, "Expected property name after '.'");
                expr = new ScriptAST.MemberAccessExpr(expr, name.getValue(), false);
            } else if (check(ScriptToken.Type.QUESTION) && checkNext()) {
                advance(); // consume '?'
                advance(); // consume '.'
                ScriptToken name = consume(ScriptToken.Type.IDENTIFIER, "Expected property name after '?.'");
                expr = new ScriptAST.MemberAccessExpr(expr, name.getValue(), true);
            } else if (match(ScriptToken.Type.LBRACKET)) {
                ScriptAST index = parseExpression();
                consume(ScriptToken.Type.RBRACKET, "Expected ']' after index");
                expr = new ScriptAST.ArrayAccessExpr(expr, index);
            } else if (match(ScriptToken.Type.PLUS_PLUS, ScriptToken.Type.MINUS_MINUS)) {
                String operator = previous().getValue();
                expr = new ScriptAST.UpdateExpr(expr, operator, false);
            } else {
                break;
            }
        }

        return expr;
    }

    private ScriptAST finishCall(ScriptAST callee) {
        List<ScriptAST> arguments = new ArrayList<>();

        if (!check(ScriptToken.Type.RPAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(ScriptToken.Type.COMMA));
        }

        consume(ScriptToken.Type.RPAREN, "Expected ')' after arguments");
        return new ScriptAST.CallExpr(callee, arguments);
    }

    private ScriptAST parsePrimary() {
        // Literals
        if (match(ScriptToken.Type.NUMBER)) {
            return new ScriptAST.LiteralExpr(Double.parseDouble(previous().getValue()));
        }
        if (match(ScriptToken.Type.STRING)) {
            return new ScriptAST.LiteralExpr(previous().getValue());
        }
        if (match(ScriptToken.Type.TRUE)) {
            return new ScriptAST.LiteralExpr(true);
        }
        if (match(ScriptToken.Type.FALSE)) {
            return new ScriptAST.LiteralExpr(false);
        }
        if (match(ScriptToken.Type.NULL)) {
            return new ScriptAST.LiteralExpr(null);
        }

        // Arrow function with parentheses: (x, y) => expr
        if (check(ScriptToken.Type.LPAREN) && isArrowFunction()) {
            return parseArrowFunction();
        }

        // Grouped expression
        if (match(ScriptToken.Type.LPAREN)) {
            ScriptAST expr = parseExpression();
            consume(ScriptToken.Type.RPAREN, "Expected ')' after expression");
            return expr;
        }

        // Identifier (could be arrow function param)
        if (match(ScriptToken.Type.IDENTIFIER)) {
            String name = previous().getValue();

            // Single-param arrow function: x => expr
            if (check(ScriptToken.Type.ARROW)) {
                advance(); // consume '=>'
                List<String> params = new ArrayList<>();
                params.add(name);
                ScriptAST body = parseArrowBody();
                return new ScriptAST.ArrowFunctionExpr(params, body);
            }

            return new ScriptAST.IdentifierExpr(name);
        }

        throw new ParseException("Expected expression at " + peek());
    }

    private boolean isArrowFunction() {
        // Look ahead to see if this is an arrow function
        int saved = current;
        try {
            if (!match(ScriptToken.Type.LPAREN)) return false;

            // Skip parameters
            int depth = 1;
            while (depth > 0 && !isAtEnd()) {
                if (match(ScriptToken.Type.LPAREN)) depth++;
                else if (match(ScriptToken.Type.RPAREN)) depth--;
                else advance();
            }

            return check(ScriptToken.Type.ARROW);
        } finally {
            current = saved;
        }
    }

    private ScriptAST parseArrowFunction() {
        consume(ScriptToken.Type.LPAREN, "Expected '(' for arrow function");

        List<String> params = new ArrayList<>();
        if (!check(ScriptToken.Type.RPAREN)) {
            do {
                ScriptToken param = consume(ScriptToken.Type.IDENTIFIER, "Expected parameter name");
                params.add(param.getValue());
            } while (match(ScriptToken.Type.COMMA));
        }

        consume(ScriptToken.Type.RPAREN, "Expected ')' after parameters");
        consume(ScriptToken.Type.ARROW, "Expected '=>' after parameters");

        ScriptAST body = parseArrowBody();
        return new ScriptAST.ArrowFunctionExpr(params, body);
    }

    private ScriptAST parseArrowBody() {
        if (check(ScriptToken.Type.LBRACE)) {
            return parseBlock();
        } else {
            return parseExpression();
        }
    }

    // ==================== Helpers ====================

    private boolean match(ScriptToken.Type... types) {
        for (ScriptToken.Type type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(ScriptToken.Type type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private boolean checkNext() {
        if (current + 1 >= tokens.size()) return false;
        return tokens.get(current + 1).getType() == ScriptToken.Type.DOT;
    }

    private ScriptToken advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == ScriptToken.Type.EOF;
    }

    private ScriptToken peek() {
        return tokens.get(current);
    }

    private ScriptToken previous() {
        return tokens.get(current - 1);
    }

    private ScriptToken consume(ScriptToken.Type type, String message) {
        if (check(type)) return advance();
        throw new ParseException(message + " at " + peek());
    }

    private void consumeSemicolon() {
        // Semicolons are optional in many contexts
        match(ScriptToken.Type.SEMICOLON);
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().getType() == ScriptToken.Type.SEMICOLON) return;

            switch (peek().getType()) {
                case LET:
                case CONST:
                case IF:
                case RETURN:
                case FUNCTION:
                case FOR:
                case WHILE:
                case DO:
                case BREAK:
                case CONTINUE:
                case TRY:
                case THROW:
                    return;
            }

            advance();
        }
    }

    private static class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }
}
