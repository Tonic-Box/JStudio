package com.tonic.script.engine;

import lombok.Getter;

import java.util.List;

/** A node of the script language syntax tree; each node kind is a nested class dispatched through the visitor. */
public abstract class ScriptAST
{

    /** A visitor over script syntax nodes, one method per node kind. */
    public interface Visitor<T>
    {
        /**
         * Visits a literal.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitLiteral(LiteralExpr expr);

        /**
         * Visits a variable reference.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitIdentifier(IdentifierExpr expr);

        /**
         * Visits a binary operation.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitBinary(BinaryExpr expr);

        /**
         * Visits a unary operation.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitUnary(UnaryExpr expr);

        /**
         * Visits a function call.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitCall(CallExpr expr);

        /**
         * Visits a property access.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitMemberAccess(MemberAccessExpr expr);

        /**
         * Visits an arrow function.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitArrowFunction(ArrowFunctionExpr expr);

        /**
         * Visits an index access.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitArrayAccess(ArrayAccessExpr expr);

        /**
         * Visits a conditional expression.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitTernary(TernaryExpr expr);

        /**
         * Visits an expression statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitExpressionStmt(ExpressionStmt stmt);

        /**
         * Visits a let or const declaration.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitVarDecl(VarDeclStmt stmt);

        /**
         * Visits an if statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitIf(IfStmt stmt);

        /**
         * Visits a return statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitReturn(ReturnStmt stmt);

        /**
         * Visits a block.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitBlock(BlockStmt stmt);

        /**
         * Visits a while loop.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitWhile(WhileStmt stmt);

        /**
         * Visits a C-style for loop.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitFor(ForStmt stmt);

        /**
         * Visits a for-in or for-of loop.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitForEach(ForEachStmt stmt);

        /**
         * Visits a break statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitBreak(BreakStmt stmt);

        /**
         * Visits a continue statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitContinue(ContinueStmt stmt);

        /**
         * Visits a try statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitTry(TryStmt stmt);

        /**
         * Visits a throw statement.
         *
         * @param stmt the node
         * @return the visitor's result
         */
        T visitThrow(ThrowStmt stmt);

        /**
         * Visits an increment or decrement.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitUpdate(UpdateExpr expr);

        /**
         * Visits an assignment.
         *
         * @param expr the node
         * @return the visitor's result
         */
        T visitAssignment(AssignmentExpr expr);
    }

    /**
     * Dispatches to the visitor method for this node's kind.
     *
     * @param <T> the visitor's result type
     * @param visitor the visitor
     * @return the visitor's result
     */
    public abstract <T> T accept(Visitor<T> visitor);

    /** A literal value: a number, string, boolean or null. */
    @Getter
    public static class LiteralExpr extends ScriptAST
    {
        private final Object value;

        /**
         * Creates a literal.
         *
         * @param value the Java value: a Double, String, Boolean or null
         */
        public LiteralExpr(Object value)
        {
            this.value = value;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitLiteral(this);
        }
    }

    /** A reference to a variable by name. */
    @Getter
    public static class IdentifierExpr extends ScriptAST
    {
        private final String name;

        /**
         * Creates a reference.
         *
         * @param name the variable name
         */
        public IdentifierExpr(String name)
        {
            this.name = name;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitIdentifier(this);
        }
    }

    /** A binary operation such as arithmetic, comparison or a logical operator. */
    @Getter
    public static class BinaryExpr extends ScriptAST
    {
        private final ScriptAST left;
        private final String operator;
        private final ScriptAST right;

        /**
         * Creates a binary operation.
         *
         * @param left the left operand
         * @param operator the operator text
         * @param right the right operand
         */
        public BinaryExpr(ScriptAST left, String operator, ScriptAST right)
        {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitBinary(this);
        }
    }

    /** A prefix unary operation such as negation or logical not. */
    @Getter
    public static class UnaryExpr extends ScriptAST
    {
        private final String operator;
        private final ScriptAST operand;

        /**
         * Creates a unary operation.
         *
         * @param operator the operator text
         * @param operand the operand
         */
        public UnaryExpr(String operator, ScriptAST operand)
        {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitUnary(this);
        }
    }

    /** A call of a function value with arguments. */
    @Getter
    public static class CallExpr extends ScriptAST
    {
        private final ScriptAST callee;
        private final List<ScriptAST> arguments;

        /**
         * Creates a call.
         *
         * @param callee the expression giving the function
         * @param arguments the argument expressions, in order
         */
        public CallExpr(ScriptAST callee, List<ScriptAST> arguments)
        {
            this.callee = callee;
            this.arguments = arguments;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitCall(this);
        }
    }

    /** A property access by name, plain or optional-chained. */
    @Getter
    public static class MemberAccessExpr extends ScriptAST
    {
        private final ScriptAST object;
        private final String member;
        private final boolean optional;

        /**
         * Creates a property access.
         *
         * @param object the expression owning the property
         * @param member the property name
         * @param optional true for an optional-chained access, which yields null on a null object
         */
        public MemberAccessExpr(ScriptAST object, String member, boolean optional)
        {
            this.object = object;
            this.member = member;
            this.optional = optional;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitMemberAccess(this);
        }
    }

    /** An arrow function literal. */
    @Getter
    public static class ArrowFunctionExpr extends ScriptAST
    {
        private final List<String> parameters;
        private final ScriptAST body;

        /**
         * Creates an arrow function.
         *
         * @param parameters the parameter names, in order
         * @param body a block, or a single expression whose value is returned
         */
        public ArrowFunctionExpr(List<String> parameters, ScriptAST body)
        {
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitArrowFunction(this);
        }
    }

    /** An index access on an array or object. */
    @Getter
    public static class ArrayAccessExpr extends ScriptAST
    {
        private final ScriptAST array;
        private final ScriptAST index;

        /**
         * Creates an index access.
         *
         * @param array the expression being indexed
         * @param index the index expression
         */
        public ArrayAccessExpr(ScriptAST array, ScriptAST index)
        {
            this.array = array;
            this.index = index;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitArrayAccess(this);
        }
    }

    /** A conditional expression choosing between two values. */
    @Getter
    public static class TernaryExpr extends ScriptAST
    {
        private final ScriptAST condition;
        private final ScriptAST thenBranch;
        private final ScriptAST elseBranch;

        /**
         * Creates a conditional expression.
         *
         * @param condition the condition
         * @param thenBranch the value when true
         * @param elseBranch the value when false
         */
        public TernaryExpr(ScriptAST condition, ScriptAST thenBranch, ScriptAST elseBranch)
        {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitTernary(this);
        }
    }

    /** An expression evaluated as a statement. */
    @Getter
    public static class ExpressionStmt extends ScriptAST
    {
        private final ScriptAST expression;

        /**
         * Creates an expression statement.
         *
         * @param expression the expression
         */
        public ExpressionStmt(ScriptAST expression)
        {
            this.expression = expression;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitExpressionStmt(this);
        }
    }

    /** A let or const declaration. */
    @Getter
    public static class VarDeclStmt extends ScriptAST
    {
        private final String name;
        private final ScriptAST initializer;
        private final boolean constant;

        /**
         * Creates a declaration.
         *
         * @param name the variable name
         * @param initializer the initial value, or null for none
         * @param constant true for const
         */
        public VarDeclStmt(String name, ScriptAST initializer, boolean constant)
        {
            this.name = name;
            this.initializer = initializer;
            this.constant = constant;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitVarDecl(this);
        }
    }

    /** An if statement with an optional else branch. */
    @Getter
    public static class IfStmt extends ScriptAST
    {
        private final ScriptAST condition;
        private final ScriptAST thenBranch;
        private final ScriptAST elseBranch;

        /**
         * Creates an if statement.
         *
         * @param condition the condition
         * @param thenBranch the statement run when true
         * @param elseBranch the statement run when false, or null for none
         */
        public IfStmt(ScriptAST condition, ScriptAST thenBranch, ScriptAST elseBranch)
        {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitIf(this);
        }
    }

    /** A return statement. */
    @Getter
    public static class ReturnStmt extends ScriptAST
    {
        private final ScriptAST value;

        /**
         * Creates a return statement.
         *
         * @param value the returned expression, or null for a bare return
         */
        public ReturnStmt(ScriptAST value)
        {
            this.value = value;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitReturn(this);
        }
    }

    /** A braced list of statements. */
    @Getter
    public static class BlockStmt extends ScriptAST
    {
        private final List<ScriptAST> statements;

        /**
         * Creates a block.
         *
         * @param statements the statements, in order
         */
        public BlockStmt(List<ScriptAST> statements)
        {
            this.statements = statements;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitBlock(this);
        }
    }

    /** A while loop. */
    @Getter
    public static class WhileStmt extends ScriptAST
    {
        private final ScriptAST condition;
        private final ScriptAST body;

        /**
         * Creates a while loop.
         *
         * @param condition the loop condition
         * @param body the loop body
         */
        public WhileStmt(ScriptAST condition, ScriptAST body)
        {
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitWhile(this);
        }
    }

    /** A C-style for loop. */
    @Getter
    public static class ForStmt extends ScriptAST
    {
        private final ScriptAST init;
        private final ScriptAST condition;
        private final ScriptAST update;
        private final ScriptAST body;

        /**
         * Creates a for loop.
         *
         * @param init the initializer, or null for none
         * @param condition the loop condition, or null to loop until break
         * @param update the update expression, or null for none
         * @param body the loop body
         */
        public ForStmt(ScriptAST init, ScriptAST condition, ScriptAST update, ScriptAST body)
        {
            this.init = init;
            this.condition = condition;
            this.update = update;
            this.body = body;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitFor(this);
        }
    }

    /** A for-in loop over keys or a for-of loop over values. */
    @Getter
    public static class ForEachStmt extends ScriptAST
    {
        private final String varName;
        private final boolean constant;
        private final ScriptAST iterable;
        private final ScriptAST body;
        private final boolean forIn;

        /**
         * Creates a for-in or for-of loop.
         *
         * @param varName the loop variable name
         * @param constant true when the loop variable is declared const
         * @param iterable the expression iterated
         * @param body the loop body
         * @param forIn true for for-in, false for for-of
         */
        public ForEachStmt(String varName, boolean constant, ScriptAST iterable, ScriptAST body, boolean forIn)
        {
            this.varName = varName;
            this.constant = constant;
            this.iterable = iterable;
            this.body = body;
            this.forIn = forIn;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitForEach(this);
        }
    }

    /** A break statement. */
    @Getter
    public static class BreakStmt extends ScriptAST
    {
        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitBreak(this);
        }
    }

    /** A continue statement. */
    @Getter
    public static class ContinueStmt extends ScriptAST
    {
        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitContinue(this);
        }
    }

    /** A try statement with an optional catch and an optional finally, at least one present. */
    @Getter
    public static class TryStmt extends ScriptAST
    {
        private final ScriptAST tryBlock;
        private final String catchParam;
        private final ScriptAST catchBlock;
        private final ScriptAST finallyBlock;

        /**
         * Creates a try statement.
         *
         * @param tryBlock the guarded block
         * @param catchParam the name bound to the caught error, or null
         * @param catchBlock the catch block, or null for none
         * @param finallyBlock the finally block, or null for none
         */
        public TryStmt(ScriptAST tryBlock, String catchParam, ScriptAST catchBlock, ScriptAST finallyBlock)
        {
            this.tryBlock = tryBlock;
            this.catchParam = catchParam;
            this.catchBlock = catchBlock;
            this.finallyBlock = finallyBlock;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitTry(this);
        }
    }

    /** A throw statement. */
    @Getter
    public static class ThrowStmt extends ScriptAST
    {
        private final ScriptAST expression;

        /**
         * Creates a throw statement.
         *
         * @param expression the thrown value
         */
        public ThrowStmt(ScriptAST expression)
        {
            this.expression = expression;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitThrow(this);
        }
    }

    /** An increment or decrement, prefix or postfix. */
    @Getter
    public static class UpdateExpr extends ScriptAST
    {
        private final ScriptAST operand;
        private final String operator;
        private final boolean prefix;

        /**
         * Creates an increment or decrement.
         *
         * @param operand the variable or property updated
         * @param operator the operator text, ++ or --
         * @param prefix true for the prefix form
         */
        public UpdateExpr(ScriptAST operand, String operator, boolean prefix)
        {
            this.operand = operand;
            this.operator = operator;
            this.prefix = prefix;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitUpdate(this);
        }
    }

    /** An assignment, plain or compound. */
    @Getter
    public static class AssignmentExpr extends ScriptAST
    {
        private final ScriptAST target;
        private final String operator;
        private final ScriptAST value;

        /**
         * Creates an assignment.
         *
         * @param target the variable, property or index assigned
         * @param operator the assignment operator text
         * @param value the assigned expression
         */
        public AssignmentExpr(ScriptAST target, String operator, ScriptAST value)
        {
            this.target = target;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public <T> T accept(Visitor<T> visitor)
        {
            return visitor.visitAssignment(this);
        }
    }
}
