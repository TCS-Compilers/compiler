package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.TypeCheckingError
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.mutableRefMapOf
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeCheckerTest {
    private val nameResolution = mutableRefMapOf<AstNode, NamedNode>()
    private val argumentResolution = mutableRefMapOf<Expression.FunctionCall.Argument, Function.Parameter>()
    private val diagnosticsList = mutableListOf<Diagnostic>()

    private val diagnostics: Diagnostics = object : Diagnostics {
        override fun report(diagnostic: Diagnostic) { diagnosticsList.add(diagnostic) }
        override fun hasAnyError(): Boolean = throw RuntimeException("This method should not be called")
    }

    // stała x: Liczba = 123

    @Test
    fun `correct global constant`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // wart x: Liczba = 123

    @Test
    fun `correct global value`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, value)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // zm x: Liczba = 123

    @Test
    fun `correct global variable`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // stała x: Liczba

    @Test
    fun `global constant without value`() {
        val variable = Variable(Variable.Kind.CONSTANT, "x", Type.Number, null)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ConstantWithoutValue(variable)), diagnosticsList)
    }

    // wart x: Liczba

    @Test
    fun `uninitialized global value`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.UninitializedGlobalVariable(variable)), diagnosticsList)
    }

    // zm x: Liczba

    @Test
    fun `uninitialized global variable`() {
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, null)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.UninitializedGlobalVariable(variable)), diagnosticsList)
    }

    private fun dummyFunction(name: String): Function {
        val value = Expression.NumberLiteral(123)
        val returnStatement = Statement.FunctionReturn(value)
        return Function(name, listOf(), Type.Number, listOf(returnStatement))
    }

    // czynność f() -> Liczba { zwróć 123 }
    // stała x: Liczba = f()

    @Test
    fun `global constant with non-constant expression as value`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val variable = Variable(Variable.Kind.CONSTANT, "x", Type.Number, call)
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.NonConstantExpression(call)), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }
    // wart x: Liczba = f()

    @Test
    fun `global value with non-constant expression as initial value`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, call)
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.NonConstantExpression(call)), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }
    // zm x: Liczba = f()

    @Test
    fun `global variable with non-constant expression as initial value`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, call)
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.NonConstantExpression(call)), diagnosticsList)
    }

    // zm x: Liczba = prawda

    @Test
    fun `global variable with incorrect type of initial value`() {
        val value = Expression.BooleanLiteral(true)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val program = Program(listOf(Program.Global.VariableDefinition(variable)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność f(x: Liczba) { }

    @Test
    fun `correct global function`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f(x: Liczba = 123) { }

    @Test
    fun `correct global function with default parameter value`() {
        val value = Expression.NumberLiteral(123)
        val parameter = Function.Parameter("x", Type.Number, value)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność g() -> Liczba { zwróć 123 }
    // czynność f(x: Liczba = g()) { }

    @Test
    fun `global function with non-constant expression as default parameter value`() {
        val function1 = dummyFunction("g")
        val call = Expression.FunctionCall("g", listOf())
        nameResolution[Ref(call)] = Ref(function1)
        val parameter = Function.Parameter("x", Type.Number, call)
        val function2 = Function("f", listOf(parameter), Type.Unit, listOf())
        val program = Program(listOf(Program.Global.FunctionDefinition(function1), Program.Global.FunctionDefinition(function2)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.NonConstantExpression(call)), diagnosticsList)
    }

    // czynność f(x: Liczba = prawda) { }

    @Test
    fun `global function with incorrect type of default parameter value`() {
        val value = Expression.BooleanLiteral(true)
        val parameter = Function.Parameter("x", Type.Number, value)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    private fun mainFunction(body: StatementBlock) = Function("główna", listOf(), Type.Unit, body)

    // czynność f() -> Liczba { zwróć 123 }
    // czynność główna() { f() }

    @Test
    fun `correct evaluation`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val evaluation = Statement.Evaluation(call)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { stała x: Liczba = 123 }

    @Test
    fun `correct constant`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { wart x: Liczba = 123 }

    @Test
    fun `correct value`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, value)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { zm x: Liczba = 123 }

    @Test
    fun `correct variable`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { stała x: Liczba }

    @Test
    fun `constant without value`() {
        val variable = Variable(Variable.Kind.CONSTANT, "x", Type.Number, null)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ConstantWithoutValue(variable)), diagnosticsList)
    }

    // czynność główna() { wart x: Liczba }

    @Test
    fun `correct uninitialized value`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { zm x: Liczba }

    @Test
    fun `correct uninitialized variable`() {
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, null)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }
    // czynność główna() { stała x: Liczba = f() }

    @Test
    fun `constant with non-constant expression as value`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val variable = Variable(Variable.Kind.CONSTANT, "x", Type.Number, call)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.NonConstantExpression(call)), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }
    // czynność główna() { wart x: Liczba = f() }

    @Test
    fun `correct value with non-constant expression as initial value`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, call)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }
    // czynność główna() { zm x: Liczba = f() }

    @Test
    fun `correct variable with non-constant expression as initial value`() {
        val function = dummyFunction("f")
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, call)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { zm x: Liczba = prawda }

    @Test
    fun `variable with incorrect type of initial value`() {
        val value = Expression.BooleanLiteral(true)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność główna() { czynność f(x: Liczba) { } }

    @Test
    fun `correct nested function`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val main = mainFunction(listOf(Statement.FunctionDefinition(function)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { czynność f(x: Liczba = 123) { } }

    @Test
    fun `correct nested function with default parameter value`() {
        val value = Expression.NumberLiteral(123)
        val parameter = Function.Parameter("x", Type.Number, value)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val main = mainFunction(listOf(Statement.FunctionDefinition(function)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność g() -> Liczba { zwróć 123 }
    // czynność główna() { czynność f(x: Liczba = g()) { } }

    @Test
    fun `correct nested function with non-constant expression as default parameter value`() {
        val function1 = dummyFunction("g")
        val call = Expression.FunctionCall("g", listOf())
        nameResolution[Ref(call)] = Ref(function1)
        val parameter = Function.Parameter("x", Type.Number, call)
        val function2 = Function("f", listOf(parameter), Type.Unit, listOf())
        val main = mainFunction(listOf(Statement.FunctionDefinition(function2)))
        val program = Program(listOf(Program.Global.FunctionDefinition(function1), Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { czynność f(x: Liczba = prawda) { } }

    @Test
    fun `nested function with incorrect type of default parameter value`() {
        val value = Expression.BooleanLiteral(true)
        val parameter = Function.Parameter("x", Type.Number, value)
        val function = Function("g", listOf(parameter), Type.Unit, listOf())
        val main = mainFunction(listOf(Statement.FunctionDefinition(function)))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność główna() {
    //     zm x: Liczba = 0
    //     x = 1
    // }

    @Test
    fun `correct assignment`() {
        val initialValue = Expression.NumberLiteral(0)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, initialValue)
        val value = Expression.NumberLiteral(1)
        val assignment = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), value)
        nameResolution[Ref(assignment)] = Ref(variable)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable), assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() {
    //     wart x: Liczba = 0
    //     x = 1
    // }

    @Test
    fun `assignment to value`() {
        val initialValue = Expression.NumberLiteral(0)
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, initialValue)
        val value = Expression.NumberLiteral(1)
        val assignment = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), value)
        nameResolution[Ref(assignment)] = Ref(variable)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable), assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ImmutableAssignment(assignment, variable)), diagnosticsList)
    }

    // czynność główna() {
    //     stała x: Liczba = 0
    //     x = 1
    // }

    @Test
    fun `assignment to constant`() {
        val initialValue = Expression.NumberLiteral(0)
        val variable = Variable(Variable.Kind.CONSTANT, "x", Type.Number, initialValue)
        val value = Expression.NumberLiteral(1)
        val assignment = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), value)
        nameResolution[Ref(assignment)] = Ref(variable)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable), assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ImmutableAssignment(assignment, variable)), diagnosticsList)
    }

    // czynność f(x: Liczba) { x = 1 }

    @Test
    fun `assignment to parameter`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val value = Expression.NumberLiteral(1)
        val assignment = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), value)
        nameResolution[Ref(assignment)] = Ref(parameter)
        val function = Function("f", listOf(parameter), Type.Unit, listOf(assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ParameterAssignment(assignment, parameter)), diagnosticsList)
    }

    // czynność f() { }
    // czynność główna() { f = 1 }

    @Test
    fun `assignment to function`() {
        val function = Function("f", listOf(), Type.Unit, listOf())
        val value = Expression.NumberLiteral(1)
        val assignment = Statement.Assignment(Statement.Assignment.LValue.Variable("f"), value)
        nameResolution[Ref(assignment)] = Ref(function)
        val main = mainFunction(listOf(Statement.FunctionDefinition(function), assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.FunctionAssignment(assignment, function)), diagnosticsList)
    }

    // czynność główna() { { nic } }

    @Test
    fun `correct nested block`() {
        val evaluation = Statement.Evaluation(Expression.UnitLiteral())
        val block = Statement.Block(listOf(evaluation))
        val main = mainFunction(listOf(block))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { jeśli (prawda) nic wpp nic }

    @Test
    fun `correct conditional`() {
        val evaluation = Statement.Evaluation(Expression.UnitLiteral())
        val condition = Expression.BooleanLiteral(true)
        val conditional = Statement.Conditional(condition, listOf(evaluation), listOf(evaluation))
        val main = mainFunction(listOf(conditional))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { jeśli (123) nic wpp nic }

    @Test
    fun `conditional with non-boolean condition`() {
        val evaluation = Statement.Evaluation(Expression.UnitLiteral())
        val condition = Expression.NumberLiteral(123)
        val conditional = Statement.Conditional(condition, listOf(evaluation), listOf(evaluation))
        val main = mainFunction(listOf(conditional))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(condition, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // czynność główna() { dopóki (prawda) nic }

    @Test
    fun `correct loop`() {
        val evaluation = Statement.Evaluation(Expression.UnitLiteral())
        val condition = Expression.BooleanLiteral(true)
        val conditional = Statement.Loop(condition, listOf(evaluation))
        val main = mainFunction(listOf(conditional))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { dopóki (prawda) przerwij }

    @Test
    fun `correct loop with break`() {
        val loopBreak = Statement.LoopBreak()
        val condition = Expression.BooleanLiteral(true)
        val conditional = Statement.Loop(condition, listOf(loopBreak))
        val main = mainFunction(listOf(conditional))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { dopóki (prawda) pomiń }

    @Test
    fun `correct loop with continuation`() {
        val loopContinuation = Statement.LoopContinuation()
        val condition = Expression.BooleanLiteral(true)
        val conditional = Statement.Loop(condition, listOf(loopContinuation))
        val main = mainFunction(listOf(conditional))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { dopóki (123) nic }

    @Test
    fun `loop with non-boolean condition`() {
        val evaluation = Statement.Evaluation(Expression.UnitLiteral())
        val condition = Expression.NumberLiteral(123)
        val conditional = Statement.Loop(condition, listOf(evaluation))
        val main = mainFunction(listOf(conditional))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(condition, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }

    @Test
    fun `correct function return`() {
        val value = Expression.NumberLiteral(123)
        val functionReturn = Statement.FunctionReturn(value)
        val function = Function("f", listOf(), Type.Number, listOf(functionReturn))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć prawda }

    @Test
    fun `function return with invalid type`() {
        val value = Expression.BooleanLiteral(true)
        val functionReturn = Statement.FunctionReturn(value)
        val function = Function("f", listOf(), Type.Number, listOf(functionReturn))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność główna() { nic }

    @Test
    fun `unit literal has unit type`() {
        val value = Expression.UnitLiteral()
        val evaluation = Statement.Evaluation(value)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf<Expression, Type>(value to Type.Unit), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { prawda }

    @Test
    fun `boolean literal has boolean type`() {
        val value = Expression.BooleanLiteral(true)
        val evaluation = Statement.Evaluation(value)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf<Expression, Type>(value to Type.Boolean), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { 123 }

    @Test
    fun `number literal has number type`() {
        val value = Expression.NumberLiteral(123)
        val evaluation = Statement.Evaluation(value)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf<Expression, Type>(value to Type.Number), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() {
    //     zm x: Liczba = 123
    //     x
    // }

    @Test
    fun `variable expression has the type of the variable`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val variableExpression = Expression.Variable("x")
        nameResolution[Ref(variableExpression)] = Ref(variable)
        val evaluation = Statement.Evaluation(variableExpression)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable), evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(value to Type.Number, variableExpression to Type.Number), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f(x: Liczba) { x }

    @Test
    fun `parameter expression has the type of the parameter`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val variableExpression = Expression.Variable("x")
        nameResolution[Ref(variableExpression)] = Ref(parameter)
        val evaluation = Statement.Evaluation(variableExpression)
        val function = Function("f", listOf(parameter), Type.Unit, listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf<Expression, Type>(variableExpression to Type.Number), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f() { }
    // czynność główna() { f }

    @Test
    fun `function cannot be used as a value`() {
        val function = Function("f", listOf(), Type.Unit, listOf())
        val variableExpression = Expression.Variable("x")
        nameResolution[Ref(variableExpression)] = Ref(function)
        val evaluation = Statement.Evaluation(variableExpression)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.FunctionAsValue(variableExpression, function)), diagnosticsList)
    }

    // czynność główna() {
    //     zm x: Liczba = 123
    //     x()
    // }

    @Test
    fun `a variable cannot be called`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, value)
        val call = Expression.FunctionCall("x", listOf())
        nameResolution[Ref(call)] = Ref(variable)
        val evaluation = Statement.Evaluation(call)
        val main = mainFunction(listOf(Statement.VariableDefinition(variable), evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.VariableCall(call, variable)), diagnosticsList)
    }

    // czynność f(x: Liczba) {
    //     x()
    // }

    @Test
    fun `a parameter cannot be called`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val call = Expression.FunctionCall("x", listOf())
        nameResolution[Ref(call)] = Ref(parameter)
        val evaluation = Statement.Evaluation(call)
        val function = Function("f", listOf(parameter), Type.Unit, listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ParameterCall(call, parameter)), diagnosticsList)
    }

    // czynność f(x: Liczba) { }
    // czynność główna() { f(123) }

    @Test
    fun `correct function call`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val value = Expression.NumberLiteral(123)
        val argument = Expression.FunctionCall.Argument(null, value)
        val call = Expression.FunctionCall("f", listOf(argument))
        nameResolution[Ref(call)] = Ref(function)
        argumentResolution[Ref(argument)] = Ref(parameter)
        val evaluation = Statement.Evaluation(call)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność f(x: Liczba) { }
    // czynność główna() { f(prawda) }

    @Test
    fun `function call with incorrect type of argument`() {
        val parameter = Function.Parameter("x", Type.Number, null)
        val function = Function("f", listOf(parameter), Type.Unit, listOf())
        val value = Expression.BooleanLiteral(true)
        val argument = Expression.FunctionCall.Argument(null, value)
        val call = Expression.FunctionCall("f", listOf(argument))
        nameResolution[Ref(call)] = Ref(function)
        argumentResolution[Ref(argument)] = Ref(parameter)
        val evaluation = Statement.Evaluation(call)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność f() -> Liczba { zwróć 123 }
    // czynność główna() { f() }

    @Test
    fun `function call has the return type of the function`() {
        val value = Expression.NumberLiteral(123)
        val returnStatement = Statement.FunctionReturn(value)
        val function = Function("f", listOf(), Type.Number, listOf(returnStatement))
        val call = Expression.FunctionCall("f", listOf())
        nameResolution[Ref(call)] = Ref(function)
        val evaluation = Statement.Evaluation(call)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function), Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(value to Type.Number, call to Type.Number), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { nie prawda }

    @Test
    fun `boolean negation has boolean type`() {
        val value = Expression.BooleanLiteral(true)
        val operation = Expression.UnaryOperation(Expression.UnaryOperation.Kind.NOT, value)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(value to Type.Boolean, operation to Type.Boolean), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { nie 123 }

    @Test
    fun `boolean negation with incorrect type of operand`() {
        val value = Expression.NumberLiteral(123)
        val operation = Expression.UnaryOperation(Expression.UnaryOperation.Kind.NOT, value)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // czynność główna() { -123 }

    @Test
    fun `number negation has number type`() {
        val value = Expression.NumberLiteral(123)
        val operation = Expression.UnaryOperation(Expression.UnaryOperation.Kind.MINUS, value)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(value to Type.Number, operation to Type.Number), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { -prawda }

    @Test
    fun `number negation with incorrect type of operand`() {
        val value = Expression.BooleanLiteral(true)
        val operation = Expression.UnaryOperation(Expression.UnaryOperation.Kind.MINUS, value)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(value, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność główna() { prawda oraz fałsz }

    @Test
    fun `boolean conjunction has boolean type`() {
        val leftValue = Expression.BooleanLiteral(true)
        val rightValue = Expression.BooleanLiteral(false)
        val operation = Expression.BinaryOperation(Expression.BinaryOperation.Kind.AND, leftValue, rightValue)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(leftValue to Type.Boolean, rightValue to Type.Boolean, operation to Type.Boolean), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { prawda oraz 123 }

    @Test
    fun `boolean conjunction with incorrect type of operand`() {
        val leftValue = Expression.BooleanLiteral(true)
        val rightValue = Expression.NumberLiteral(123)
        val operation = Expression.BinaryOperation(Expression.BinaryOperation.Kind.AND, leftValue, rightValue)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(rightValue, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // czynność główna() { 2 + 2 }

    @Test
    fun `number addition has number type`() {
        val leftValue = Expression.NumberLiteral(2)
        val rightValue = Expression.NumberLiteral(2)
        val operation = Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, leftValue, rightValue)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(leftValue to Type.Number, rightValue to Type.Number, operation to Type.Number), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { prawda + 2 }

    @Test
    fun `number addition with incorrect type of operand`() {
        val leftValue = Expression.BooleanLiteral(true)
        val rightValue = Expression.NumberLiteral(2)
        val operation = Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, leftValue, rightValue)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(leftValue, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność główna() { 123 == 123 }

    @Test
    fun `number comparison has boolean type`() {
        val leftValue = Expression.NumberLiteral(123)
        val rightValue = Expression.NumberLiteral(123)
        val operation = Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, leftValue, rightValue)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(keyRefMapOf(leftValue to Type.Number, rightValue to Type.Number, operation to Type.Boolean), types)
        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { prawda == 1 }

    @Test
    fun `number comparison with incorrect type of operand`() {
        val leftValue = Expression.BooleanLiteral(true)
        val rightValue = Expression.NumberLiteral(1)
        val operation = Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, leftValue, rightValue)
        val evaluation = Statement.Evaluation(operation)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(leftValue, Type.Boolean, Type.Number)), diagnosticsList)
    }

    // czynność główna() { prawda ? 123 : 456 }

    @Test
    fun `conditonal expression has the same type as both results`() {
        val valueWhenTrue = Expression.NumberLiteral(123)
        val valueWhenFalse = Expression.NumberLiteral(456)
        val condition = Expression.BooleanLiteral(true)
        val conditional = Expression.Conditional(condition, valueWhenTrue, valueWhenFalse)
        val evaluation = Statement.Evaluation(conditional)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        val types = TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(
            keyRefMapOf(
                condition to Type.Boolean,
                valueWhenTrue to Type.Number,
                valueWhenFalse to Type.Number,
                conditional to Type.Number
            ),
            types
        )

        assertEquals(listOf(), diagnosticsList)
    }

    // czynność główna() { 1 ? 123 : 456 }

    @Test
    fun `conditonal expression with incorrect type of condition`() {
        val valueWhenTrue = Expression.NumberLiteral(123)
        val valueWhenFalse = Expression.NumberLiteral(456)
        val condition = Expression.NumberLiteral(1)
        val conditional = Expression.Conditional(condition, valueWhenTrue, valueWhenFalse)
        val evaluation = Statement.Evaluation(conditional)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(condition, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // czynność główna() { prawda ? 123 : fałsz }

    @Test
    fun `conditonal expression with different types of results`() {
        val valueWhenTrue = Expression.NumberLiteral(123)
        val valueWhenFalse = Expression.BooleanLiteral(false)
        val condition = Expression.BooleanLiteral(true)
        val conditional = Expression.Conditional(condition, valueWhenTrue, valueWhenFalse)
        val evaluation = Statement.Evaluation(conditional)
        val main = mainFunction(listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(main)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ConditionalTypesMismatch(conditional, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // przekaźnik f() -> Liczba {}
    // czynność główna() {
    //     otrzymując x: Czy od f() { }
    // }

    @Test
    fun `test generator receiving variable with wrong type`() {
        val generator = Function("f", emptyList(), Type.Number, emptyList(), true)
        val receivingVariable = Variable(Variable.Kind.VALUE, "x", Type.Boolean, null)
        val generatorCall = Expression.FunctionCall("f", emptyList())
        nameResolution[Ref(generatorCall)] = Ref(generator)
        val main = mainFunction(listOf(Statement.ForeachLoop(receivingVariable, generatorCall, emptyList())))
        val program = Program(
            listOf(
                Program.Global.FunctionDefinition(generator),
                Program.Global.FunctionDefinition(main)
            )
        )

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(generatorCall, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // przekaźnik f() -> Czy {
    //    przekaż 12
    // }

    @Test
    fun `test generator yield with wrong expression type`() {
        val generatorYield = Statement.GeneratorYield(Expression.NumberLiteral(12))
        val generator = Function("f", emptyList(), Type.Boolean, listOf(generatorYield), true)
        val program = Program(listOf(Program.Global.FunctionDefinition(generator)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.InvalidType(generatorYield.value, Type.Number, Type.Boolean)), diagnosticsList)
    }

    // przekaźnik f() -> Liczba {
    //     zwróć 12
    // }

    @Test
    fun `test generator return with value`() {
        val generatorReturn = Statement.FunctionReturn(Expression.NumberLiteral(12))
        val generator = Function("f", emptyList(), Type.Boolean, listOf(generatorReturn), true)
        val program = Program(listOf(Program.Global.FunctionDefinition(generator)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.ReturnWithValueInGenerator(generatorReturn)), diagnosticsList)
    }

    // przekaźnik f() -> Liczba {
    //     zakończ
    // }

    @Test
    fun `test generator return`() {
        val generatorReturn = Statement.FunctionReturn(Expression.UnitLiteral(), true)
        val generator = Function("f", emptyList(), Type.Boolean, listOf(generatorReturn), true)
        val program = Program(listOf(Program.Global.FunctionDefinition(generator)))

        TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)

        assertEquals(emptyList(), diagnosticsList)
    }

    // czynność f() -> Liczba {
    //     przekaż 12
    //     zwróć 10
    // }

    @Test
    fun `test yield in non-generator function`() {
        val generatorYield = Statement.GeneratorYield(Expression.NumberLiteral(12))
        val returnStatement = Statement.FunctionReturn(Expression.NumberLiteral(10))
        val function = Function("f", emptyList(), Type.Number, listOf(generatorYield, returnStatement))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        assertFailsWith<TypeChecker.TypeCheckingFailed> {
            TypeChecker.calculateTypes(program, nameResolution, argumentResolution, diagnostics)
        }

        assertEquals(listOf<Diagnostic>(TypeCheckingError.YieldInNonGeneratorFunction(generatorYield)), diagnosticsList)
    }
}
