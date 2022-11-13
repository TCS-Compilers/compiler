package compiler.ast

import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.lexer_grammar.TokenType
import compiler.parser.ParseTree
import compiler.parser.grammar.NonTerminalType
import compiler.parser.grammar.ParserGrammar.Productions
import compiler.parser.grammar.Symbol

object AstFactory {
    class AstCreationFailed : Throwable()

    // helper methods
    private fun ParseTree<Symbol>.token(): TokenType? = (symbol as? Symbol.Terminal)?.tokenType
    private fun ParseTree<Symbol>.nonTerm(): NonTerminalType? = (symbol as? Symbol.NonTerminal)?.nonTerminal

    private fun ParseTree.Branch<Symbol>.getFilteredChildren(): List<ParseTree<Symbol>> = children.filter {
        it.token() !in listOf(TokenType.NEWLINE, TokenType.SEMICOLON)
    }

    private fun skipPassThroughExpressions(parseTree: ParseTree.Branch<Symbol>): ParseTree.Branch<Symbol> {
        return if (parseTree.nonTerm() in listOf(NonTerminalType.EXPR2048, NonTerminalType.E_EXPR2048) || parseTree.children.size > 1)
            parseTree
        else
            skipPassThroughExpressions(parseTree.children.first() as ParseTree.Branch)
    }

    private fun extractIdentifier(parseTree: ParseTree.Branch<Symbol>, diagnostics: Diagnostics): String {
        val properNode = skipPassThroughExpressions(parseTree)
        if (properNode.production !in listOf(Productions.expr2048Identifier, Productions.eExpr2048Identifier)) {
            diagnostics.report(Diagnostic.ParserError(properNode.symbol, properNode.start, properNode.end, listOf(Symbol.Terminal(TokenType.IDENTIFIER))))
            throw AstCreationFailed()
        }
        return (properNode.children.first() as ParseTree.Leaf).content
    }

    private fun rotateExpressionLeft(parseTree: ParseTree.Branch<Symbol>): ParseTree.Branch<Symbol> {
        val children = parseTree.getFilteredChildren()
        val leftChild = children[0]
        val rightChild = children[2]
        val grandchildren = (rightChild as ParseTree.Branch).getFilteredChildren()
        if (grandchildren.size != 3)
            return parseTree
        val leftGrandchild = grandchildren[0]
        val rightGrandchild = grandchildren[2]

        val newLeftChild = ParseTree.Branch(leftChild.start, leftGrandchild.end, parseTree.symbol, listOf(leftChild, children[1], leftGrandchild), parseTree.production)
        val newTopLevelNode = ParseTree.Branch(leftChild.start, rightGrandchild.end, rightChild.symbol, listOf(newLeftChild, grandchildren[1], rightGrandchild), rightChild.production)

        return rotateExpressionLeft(newTopLevelNode)
    }

    // node processor methods
    fun createFromParseTree(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Program {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()
        val globals = children.map {
            when (it.nonTerm()) {
                NonTerminalType.VAR_DECL -> Program.Global.VariableDefinition(processVariableDeclaration(it, diagnostics))
                NonTerminalType.FUNC_DEF -> Program.Global.FunctionDefinition(processFunctionDefinition(it, diagnostics))
                else -> throw IllegalArgumentException()
            }
        }
        return Program(globals)
    }

    private fun processType(parseTree: ParseTree<Symbol>): Type {
        return when ((parseTree as ParseTree.Branch).children[0].token()) {
            TokenType.TYPE_INTEGER -> Type.Number
            TokenType.TYPE_BOOLEAN -> Type.Boolean
            TokenType.TYPE_UNIT -> Type.Unit
            else -> throw IllegalArgumentException()
        }
    }

    private fun processConst(parseTree: ParseTree<Symbol>): Expression {
        return when ((parseTree as ParseTree.Branch).children[0].token()) {
            TokenType.INTEGER -> Expression.NumberLiteral((parseTree.children[0] as ParseTree.Leaf).content.toInt())
            TokenType.TRUE_CONSTANT -> Expression.BooleanLiteral(true)
            TokenType.FALSE_CONSTANT -> Expression.BooleanLiteral(false)
            TokenType.UNIT_CONSTANT -> Expression.UnitLiteral
            else -> throw IllegalArgumentException()
        }
    }

    private fun processVariableDeclaration(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Variable {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val kind = when (children[0].token()) {
            TokenType.VARIABLE -> Variable.Kind.VARIABLE
            TokenType.VALUE -> Variable.Kind.VALUE
            TokenType.CONSTANT -> Variable.Kind.CONSTANT
            else -> throw IllegalArgumentException()
        }
        val name = (children[1] as ParseTree.Leaf).content
        val type = processType(children[3])
        val value = if (children.lastIndex == 5) processExpression(children[5], diagnostics) else null

        return Variable(kind, name, type, value)
    }

    private fun processFunctionDefinition(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Function {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val name = (children[1] as ParseTree.Leaf).content
        val parameters = processFunctionDefinitionParameters(children[3], diagnostics)
        val returnType = if (children[5].token() == TokenType.ARROW) processType(children[6]) else Type.Unit
        val body = processManyStatements(children[children.lastIndex - 1], diagnostics)

        return Function(name, parameters, returnType, body)
    }

    private fun processFunctionDefinitionParameters(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): List<Function.Parameter> {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()
        val parameters = ArrayList<Function.Parameter>()

        var it = 0
        while (it < children.size) {
            val grandchildren = (children[it] as ParseTree.Branch).getFilteredChildren()
            val name = (grandchildren[0] as ParseTree.Leaf).content
            val type = processType(grandchildren[2])
            val defaultValue = if (it + 1 < children.size && children[it + 1].token() == TokenType.ASSIGNMENT) {
                it += 3
                processExpression(children[it - 1], diagnostics)
            } else {
                it += 2
                null
            }
            parameters.add(Function.Parameter(name, type, defaultValue))
        }

        return parameters
    }

    private fun processFunctionCallArguments(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): List<Expression.FunctionCall.Argument> {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()
        val arguments = ArrayList<Expression.FunctionCall.Argument>()

        var it = 0
        while (it < children.size) {
            if (it + 1 < children.size && children[it + 1].token() == TokenType.ASSIGNMENT) {
                val name = extractIdentifier(children[it] as ParseTree.Branch, diagnostics)
                val value = processExpression(children[it + 2], diagnostics)
                it += 4
                arguments.add(Expression.FunctionCall.Argument(name, value))
            } else {
                val value = processExpression(children[it], diagnostics)
                it += 2
                arguments.add(Expression.FunctionCall.Argument(null, value))
            }
        }

        return arguments
    }

    private val unaryOperations = mapOf(
        Productions.expr2048UnaryPlus to Expression.UnaryOperation.Kind.PLUS,
        Productions.eExpr2048UnaryPlus to Expression.UnaryOperation.Kind.PLUS,
        Productions.expr2048UnaryMinus to Expression.UnaryOperation.Kind.MINUS,
        Productions.eExpr2048UnaryMinus to Expression.UnaryOperation.Kind.MINUS,
        Productions.expr2048UnaryBoolNot to Expression.UnaryOperation.Kind.NOT,
        Productions.eExpr2048UnaryBoolNot to Expression.UnaryOperation.Kind.NOT,
        Productions.expr2048UnaryBitNot to Expression.UnaryOperation.Kind.BIT_NOT,
        Productions.eExpr2048UnaryBitNot to Expression.UnaryOperation.Kind.BIT_NOT,
    )

    private val binaryOperations = mapOf(
        Productions.expr2BoolOr to Expression.BinaryOperation.Kind.OR,
        Productions.eExpr2BoolOr to Expression.BinaryOperation.Kind.OR,
        Productions.expr4BoolAnd to Expression.BinaryOperation.Kind.AND,
        Productions.eExpr4BoolAnd to Expression.BinaryOperation.Kind.AND,
        Productions.expr8BoolXor to Expression.BinaryOperation.Kind.XOR,
        Productions.eExpr8BoolXor to Expression.BinaryOperation.Kind.XOR,
        Productions.expr8BoolIff to Expression.BinaryOperation.Kind.IFF,
        Productions.eExpr8BoolIff to Expression.BinaryOperation.Kind.IFF,
        Productions.expr16Equal to Expression.BinaryOperation.Kind.EQUALS,
        Productions.eExpr16Equal to Expression.BinaryOperation.Kind.EQUALS,
        Productions.expr16NotEqual to Expression.BinaryOperation.Kind.NOT_EQUALS,
        Productions.eExpr16NotEqual to Expression.BinaryOperation.Kind.NOT_EQUALS,
        Productions.expr16LessThan to Expression.BinaryOperation.Kind.LESS_THAN,
        Productions.eExpr16LessThan to Expression.BinaryOperation.Kind.LESS_THAN,
        Productions.expr16LessOrEq to Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS,
        Productions.eExpr16LessOrEq to Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS,
        Productions.expr16GreaterThan to Expression.BinaryOperation.Kind.GREATER_THAN,
        Productions.eExpr16GreaterThan to Expression.BinaryOperation.Kind.GREATER_THAN,
        Productions.expr16GreaterOrEq to Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS,
        Productions.eExpr16GreaterOrEq to Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS,
        Productions.expr32BitOr to Expression.BinaryOperation.Kind.BIT_OR,
        Productions.eExpr32BitOr to Expression.BinaryOperation.Kind.BIT_OR,
        Productions.expr64BitXor to Expression.BinaryOperation.Kind.BIT_XOR,
        Productions.eExpr64BitXor to Expression.BinaryOperation.Kind.BIT_XOR,
        Productions.expr128BitAnd to Expression.BinaryOperation.Kind.BIT_AND,
        Productions.eExpr128BitAnd to Expression.BinaryOperation.Kind.BIT_AND,
        Productions.expr256ShiftLeft to Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT,
        Productions.eExpr256ShiftLeft to Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT,
        Productions.expr256ShiftRight to Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT,
        Productions.eExpr256ShiftRight to Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT,
        Productions.expr512Plus to Expression.BinaryOperation.Kind.ADD,
        Productions.eExpr512Plus to Expression.BinaryOperation.Kind.ADD,
        Productions.expr512Minus to Expression.BinaryOperation.Kind.SUBTRACT,
        Productions.eExpr512Minus to Expression.BinaryOperation.Kind.SUBTRACT,
        Productions.expr1024Multiply to Expression.BinaryOperation.Kind.MULTIPLY,
        Productions.eExpr1024Multiply to Expression.BinaryOperation.Kind.MULTIPLY,
        Productions.expr1024Divide to Expression.BinaryOperation.Kind.DIVIDE,
        Productions.eExpr1024Divide to Expression.BinaryOperation.Kind.DIVIDE,
        Productions.expr1024Modulo to Expression.BinaryOperation.Kind.MODULO,
        Productions.eExpr1024Modulo to Expression.BinaryOperation.Kind.MODULO
    )

    private fun processExpression(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Expression {
        val properNode = skipPassThroughExpressions(parseTree as ParseTree.Branch)
        val children = properNode.getFilteredChildren()

        return when (properNode.production) {
            in listOf(Productions.exprTernary, Productions.eExprTernary) -> {
                val conditionExpr = processExpression(children[0], diagnostics)
                val trueBranchExpr = processExpression(children[2], diagnostics)
                val falseBranchExpr = processExpression(children[4], diagnostics)
                Expression.Conditional(conditionExpr, trueBranchExpr, falseBranchExpr)
            }
            in binaryOperations.keys -> {
                val rotatedNode = rotateExpressionLeft(properNode)
                val newChildren = rotatedNode.getFilteredChildren()
                val leftExpr = processExpression(newChildren[0], diagnostics)
                val rightExpr = processExpression(newChildren[2], diagnostics)
                Expression.BinaryOperation(binaryOperations.getValue(rotatedNode.production), leftExpr, rightExpr)
            }
            in unaryOperations.keys -> {
                val subExpr = processExpression(children[1], diagnostics)
                Expression.UnaryOperation(unaryOperations.getValue(properNode.production), subExpr)
            }
            in listOf(Productions.expr2048Call, Productions.eExpr2048Call) -> {
                val name = (children[0] as ParseTree.Leaf).content
                val args = processFunctionCallArguments(children[2], diagnostics)
                Expression.FunctionCall(name, args)
            }
            in listOf(Productions.expr2048Identifier, Productions.eExpr2048Identifier) ->
                Expression.Variable((children[0] as ParseTree.Leaf).content)
            in listOf(Productions.expr2048Const, Productions.eExpr2048Const) ->
                processConst(children[0])
            in listOf(Productions.expr2048Parenthesis, Productions.eExpr2048Parenthesis) ->
                processExpression(children[1], diagnostics)
            else -> throw IllegalArgumentException()
        }
    }

    private fun processManyStatements(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): StatementBlock {
        return (parseTree as ParseTree.Branch).getFilteredChildren().map { processStatement(it, diagnostics) }
    }

    private fun processMaybeBlock(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): StatementBlock {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        return when (parseTree.production) {
            in listOf(Productions.maybeBlockNonBrace, Productions.nonIfMaybeBlockNonBrace) ->
                listOf(processStatement(children[0], diagnostics))
            in listOf(Productions.maybeBlockBraces, Productions.nonIfMaybeBlockBraces) ->
                processManyStatements(children[1], diagnostics)
            else -> throw IllegalArgumentException()
        }
    }

    private fun processIfStatement(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Statement {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val segments = ArrayList<Pair<Expression, StatementBlock>>()
        var it = 0
        while (it < children.size && children[it].token() in listOf(TokenType.IF, TokenType.ELSE_IF)) {
            val conditionExpr = processExpression(children[it + 2], diagnostics)
            val bodyBlock = processMaybeBlock(children[it + 4], diagnostics)
            segments.add(Pair(conditionExpr, bodyBlock))
            it += 5
        }
        val elseSegment = if (it < children.size) processMaybeBlock(children[it + 1], diagnostics) else listOf()

        return segments.slice(0 until segments.lastIndex).foldRight(
            Statement.Conditional(segments.last().first, segments.last().second, elseSegment),
            { segment, elseBranch -> Statement.Conditional(segment.first, segment.second, listOf(elseBranch)) }
        )
    }

    private fun processStatement(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Statement {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        return when (parseTree.production) {
            Productions.statementNonBrace ->
                processStatement(children[0], diagnostics)
            Productions.statementBraces ->
                Statement.Block(processManyStatements(children[1], diagnostics))
            in listOf(Productions.nonBraceStatementAtomic, Productions.nonIfNonBraceStatementAtomic) ->
                processAtomicStatement(children[0], diagnostics)
            Productions.nonBraceStatementIf ->
                processIfStatement(parseTree, diagnostics)
            in listOf(Productions.nonBraceStatementWhile, Productions.nonIfNonBraceStatementWhile) -> {
                val conditionExpr = processExpression(children[2], diagnostics)
                val bodyBlock = processMaybeBlock(children[4], diagnostics)
                Statement.Loop(conditionExpr, bodyBlock)
            }
            in listOf(Productions.nonBraceStatementFuncDef, Productions.nonIfNonBraceStatementFuncDef) ->
                Statement.FunctionDefinition(processFunctionDefinition(children[0], diagnostics))
            else -> throw IllegalArgumentException()
        }
    }

    private fun processAtomicStatement(parseTree: ParseTree<Symbol>, diagnostics: Diagnostics): Statement {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        return when (parseTree.production) {
            Productions.atomicExpr ->
                Statement.Evaluation(processExpression(children[0], diagnostics))
            Productions.atomicAssignment -> {
                val lhsName = extractIdentifier(children[0] as ParseTree.Branch, diagnostics)
                val rhsExpr = processExpression(children[2], diagnostics)
                Statement.Assignment(lhsName, rhsExpr)
            }
            Productions.atomicBreak ->
                Statement.LoopBreak
            Productions.atomicContinue ->
                Statement.LoopContinuation
            Productions.atomicReturnUnit ->
                Statement.FunctionReturn(Expression.UnitLiteral)
            Productions.atomicReturn ->
                Statement.FunctionReturn(processExpression(children[1], diagnostics))
            Productions.atomicVarDef ->
                Statement.VariableDefinition(processVariableDeclaration(children[0], diagnostics))
            else -> throw IllegalArgumentException()
        }
    }
}
