package com.tonic.ui.script.engine;

import lombok.Getter;

import java.util.List;

/**
 * AST nodes for the JStudio script language.
 */
public abstract class ScriptAST {

    /**
     * Visitor interface for AST traversal.
     */
    public interface Visitor<T> {
        // Expressions
        T visitLiteral(LiteralExpr expr);
        T visitIdentifier(IdentifierExpr expr);
        T visitBinary(BinaryExpr expr);
        T visitUnary(UnaryExpr expr);
        T visitCall(CallExpr expr);
        T visitMemberAccess(MemberAccessExpr expr);
        T visitArrowFunction(ArrowFunctionExpr expr);
        T visitArrayAccess(ArrayAccessExpr expr);
        T visitTernary(TernaryExpr expr);

        // Statements
        T visitExpressionStmt(ExpressionStmt stmt);
        T visitVarDecl(VarDeclStmt stmt);
        T visitIf(IfStmt stmt);
        T visitReturn(ReturnStmt stmt);
        T visitBlock(BlockStmt stmt);

        // Loop statements
        T visitWhile(WhileStmt stmt);
        T visitFor(ForStmt stmt);
        T visitForEach(ForEachStmt stmt);
        T visitBreak(BreakStmt stmt);
        T visitContinue(ContinueStmt stmt);

        // Exception statements
        T visitTry(TryStmt stmt);
        T visitThrow(ThrowStmt stmt);

        // Update expression (++ and --)
        T visitUpdate(UpdateExpr expr);

        // Compound assignment (+=, -=, etc.)
        T visitAssignment(AssignmentExpr expr);
    }

    public abstract <T> T accept(Visitor<T> visitor);

    // ==================== Expressions ====================

    @Getter
    public static class LiteralExpr extends ScriptAST {
        private final Object value; // String, Double, Boolean, null

        public LiteralExpr(Object value) {
            this.value = value;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitLiteral(this);
        }
    }

    @Getter
    public static class IdentifierExpr extends ScriptAST {
        private final String name;

        public IdentifierExpr(String name) {
            this.name = name;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitIdentifier(this);
        }
    }

    @Getter
    public static class BinaryExpr extends ScriptAST {
        private final ScriptAST left;
        private final String operator;
        private final ScriptAST right;

        public BinaryExpr(ScriptAST left, String operator, ScriptAST right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitBinary(this);
        }
    }

    @Getter
    public static class UnaryExpr extends ScriptAST {
        private final String operator;
        private final ScriptAST operand;

        public UnaryExpr(String operator, ScriptAST operand) {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitUnary(this);
        }
    }

    @Getter
    public static class CallExpr extends ScriptAST {
        private final ScriptAST callee;
        private final List<ScriptAST> arguments;

        public CallExpr(ScriptAST callee, List<ScriptAST> arguments) {
            this.callee = callee;
            this.arguments = arguments;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitCall(this);
        }
    }

    @Getter
    public static class MemberAccessExpr extends ScriptAST {
        private final ScriptAST object;
        private final String member;
        private final boolean optional; // ?. operator

        public MemberAccessExpr(ScriptAST object, String member, boolean optional) {
            this.object = object;
            this.member = member;
            this.optional = optional;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitMemberAccess(this);
        }
    }

    @Getter
    public static class ArrowFunctionExpr extends ScriptAST {
        private final List<String> parameters;
        private final ScriptAST body; // Expression or BlockStmt

        public ArrowFunctionExpr(List<String> parameters, ScriptAST body) {
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitArrowFunction(this);
        }
    }

    @Getter
    public static class ArrayAccessExpr extends ScriptAST {
        private final ScriptAST array;
        private final ScriptAST index;

        public ArrayAccessExpr(ScriptAST array, ScriptAST index) {
            this.array = array;
            this.index = index;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitArrayAccess(this);
        }
    }

    @Getter
    public static class TernaryExpr extends ScriptAST {
        private final ScriptAST condition;
        private final ScriptAST thenBranch;
        private final ScriptAST elseBranch;

        public TernaryExpr(ScriptAST condition, ScriptAST thenBranch, ScriptAST elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitTernary(this);
        }
    }

    // ==================== Statements ====================

    @Getter
    public static class ExpressionStmt extends ScriptAST {
        private final ScriptAST expression;

        public ExpressionStmt(ScriptAST expression) {
            this.expression = expression;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    @Getter
    public static class VarDeclStmt extends ScriptAST {
        private final String name;
        private final ScriptAST initializer;
        private final boolean constant; // let vs const

        public VarDeclStmt(String name, ScriptAST initializer, boolean constant) {
            this.name = name;
            this.initializer = initializer;
            this.constant = constant;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitVarDecl(this);
        }
    }

    @Getter
    public static class IfStmt extends ScriptAST {
        private final ScriptAST condition;
        private final ScriptAST thenBranch;
        private final ScriptAST elseBranch; // nullable

        public IfStmt(ScriptAST condition, ScriptAST thenBranch, ScriptAST elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitIf(this);
        }
    }

    @Getter
    public static class ReturnStmt extends ScriptAST {
        private final ScriptAST value; // nullable

        public ReturnStmt(ScriptAST value) {
            this.value = value;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitReturn(this);
        }
    }

    @Getter
    public static class BlockStmt extends ScriptAST {
        private final List<ScriptAST> statements;

        public BlockStmt(List<ScriptAST> statements) {
            this.statements = statements;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitBlock(this);
        }
    }

    // ==================== Loop Statements ====================

    @Getter
    public static class WhileStmt extends ScriptAST {
        private final ScriptAST condition;
        private final ScriptAST body;

        public WhileStmt(ScriptAST condition, ScriptAST body) {
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitWhile(this);
        }
    }

    @Getter
    public static class ForStmt extends ScriptAST {
        private final ScriptAST init;       // nullable (VarDeclStmt or ExpressionStmt)
        private final ScriptAST condition;  // nullable
        private final ScriptAST update;     // nullable
        private final ScriptAST body;

        public ForStmt(ScriptAST init, ScriptAST condition, ScriptAST update, ScriptAST body) {
            this.init = init;
            this.condition = condition;
            this.update = update;
            this.body = body;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitFor(this);
        }
    }

    @Getter
    public static class ForEachStmt extends ScriptAST {
        private final String varName;
        private final boolean constant;    // const vs let
        private final ScriptAST iterable;
        private final ScriptAST body;
        private final boolean forIn;       // for-in (keys) vs for-of (values)

        public ForEachStmt(String varName, boolean constant, ScriptAST iterable, ScriptAST body, boolean forIn) {
            this.varName = varName;
            this.constant = constant;
            this.iterable = iterable;
            this.body = body;
            this.forIn = forIn;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitForEach(this);
        }
    }

    @Getter
    public static class BreakStmt extends ScriptAST {
        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitBreak(this);
        }
    }

    @Getter
    public static class ContinueStmt extends ScriptAST {
        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitContinue(this);
        }
    }

    // ==================== Exception Statements ====================

    @Getter
    public static class TryStmt extends ScriptAST {
        private final ScriptAST tryBlock;
        private final String catchParam;     // nullable if no catch
        private final ScriptAST catchBlock;  // nullable
        private final ScriptAST finallyBlock; // nullable

        public TryStmt(ScriptAST tryBlock, String catchParam, ScriptAST catchBlock, ScriptAST finallyBlock) {
            this.tryBlock = tryBlock;
            this.catchParam = catchParam;
            this.catchBlock = catchBlock;
            this.finallyBlock = finallyBlock;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitTry(this);
        }
    }

    @Getter
    public static class ThrowStmt extends ScriptAST {
        private final ScriptAST expression;

        public ThrowStmt(ScriptAST expression) {
            this.expression = expression;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitThrow(this);
        }
    }

    // ==================== Additional Expressions ====================

    @Getter
    public static class UpdateExpr extends ScriptAST {
        private final ScriptAST operand;
        private final String operator;  // ++ or --
        private final boolean prefix;   // ++x vs x++

        public UpdateExpr(ScriptAST operand, String operator, boolean prefix) {
            this.operand = operand;
            this.operator = operator;
            this.prefix = prefix;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitUpdate(this);
        }
    }

    @Getter
    public static class AssignmentExpr extends ScriptAST {
        private final ScriptAST target;
        private final String operator;  // +=, -=, *=, /=
        private final ScriptAST value;

        public AssignmentExpr(ScriptAST target, String operator, ScriptAST value) {
            this.target = target;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitAssignment(this);
        }
    }
}
