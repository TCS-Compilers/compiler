package compiler.intermediate.generators

import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.IFTNode
import compiler.utils.referenceHashMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalVariableAccessGeneratorTest {

    private fun createGlobalVariablesAccessGeneratorForVariables(varList: List<Variable>): GlobalVariableAccessGenerator =
        referenceHashMapOf<Any, VariablePropertiesAnalyzer.VariableProperties>().apply {
            putAll(
                varList.associateWith {
                    VariablePropertiesAnalyzer.VariableProperties(
                        owner = VariablePropertiesAnalyzer.GlobalContext
                    )
                }
            )
        }.let {
            GlobalVariableAccessGenerator(it)
        }

    private fun createProperMemoryRead(offset: Long): IFTNode =
        IFTNode.MemoryRead(
            IFTNode.Add(
                IFTNode.MemoryLabel("globals"),
                IFTNode.Const(offset)
            )
        )

    private fun createProperMemoryWrite(offset: Long, value: IFTNode) =
        IFTNode.MemoryWrite(
            IFTNode.Add(
                IFTNode.MemoryLabel("globals"),
                IFTNode.Const(offset)
            ),
            value
        )

    @Test
    fun `globals - read and write`() {
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, null)
        val globalVariablesAccessGenerator =
            createGlobalVariablesAccessGeneratorForVariables(listOf(variable))

        assertEquals(
            globalVariablesAccessGenerator.genRead(variable, false),
            createProperMemoryRead(0)
        )

        val value = IFTNode.Const(10)
        assertEquals(
            globalVariablesAccessGenerator.genWrite(variable, value, false),
            createProperMemoryWrite(0, value)
        )
    }

    @Test
    fun `globals - multiple variables`() {
        val variables = listOf("x", "y", "z").map { Variable(Variable.Kind.VARIABLE, it, Type.Number, null) }
        val globalVariablesAccessGenerator =
            createGlobalVariablesAccessGeneratorForVariables(variables)

        val readNodes = variables.map { globalVariablesAccessGenerator.genRead(it, false) }

        assertTrue(
            (0 until 3).all { index ->
                readNodes.filter { it == createProperMemoryRead(index * GlobalVariableAccessGenerator.VARIABLE_SIZE) }.size == 1
            }
        )
    }
}