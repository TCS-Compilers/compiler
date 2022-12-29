package compiler.intermediate.generators

import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.Expression
import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.intermediate.IFTNode
import compiler.utils.ReferenceMap
import compiler.utils.referenceHashMapOf

class GlobalVariableAccessGenerator(variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>) : VariableAccessGenerator {

    companion object {
        const val VARIABLE_SIZE = 8L
        const val GLOBALS_MEMORY_LABEL = "globals"
    }

    private val offsets = referenceHashMapOf<NamedNode, Long>().apply {
        this.putAll(
            variableProperties
                .filter { it.value.owner === VariablePropertiesAnalyzer.GlobalContext }
                .filter { (it.key as Variable).kind != Variable.Kind.CONSTANT }
                .asIterable().sortedBy { (it.key as Variable).name }
                .mapIndexed { index, value ->
                    value.key as Variable to index.toLong() * VARIABLE_SIZE
                }
        )
    }

    private fun getMemoryAddress(namedNode: NamedNode) =
        IFTNode.Add(
            IFTNode.MemoryLabel(GLOBALS_MEMORY_LABEL),
            IFTNode.Const(offsets[namedNode]!!)
        )

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
        if (namedNode in offsets)
            IFTNode.MemoryRead(getMemoryAddress(namedNode))
        else IFTNode.Const(
            (namedNode as Variable).value.let { Expression.getValueOfLiteral(it!!)!! }
        )

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
        IFTNode.MemoryWrite(getMemoryAddress(namedNode), value)
}
