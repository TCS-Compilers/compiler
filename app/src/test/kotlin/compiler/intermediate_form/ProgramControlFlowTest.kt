package compiler.intermediate_form

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramControlFlowTest {

    @Test
    fun `test attachPrologueAndEpilogue for empty function`() {
        val prologue = IntermediateFormTreeNode.MemoryLabel("prologue")
        val bodyCFG = ControlFlowGraphBuilder().build()
        val epilogue = IntermediateFormTreeNode.MemoryLabel("prologue")

        val result = ControlFlow.attachPrologueAndEpilogue(
            bodyCFG,
            ControlFlowGraphBuilder(prologue).build(),
            ControlFlowGraphBuilder(epilogue).build(),
        )

        val expected = ControlFlowGraphBuilder(prologue)
        expected.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            epilogue
        )

        assertEquals(expected.build(), result)
    }

    @Test
    fun `test attachPrologueAndEpilogue function with linear control flow`() {
        val prologue = IntermediateFormTreeNode.MemoryLabel("prologue")
        val middleNode = IntermediateFormTreeNode.Const(123)
        val epilogue = IntermediateFormTreeNode.MemoryLabel("prologue")

        val bodyCFG = ControlFlowGraphBuilder(middleNode).build()

        val result = ControlFlow.attachPrologueAndEpilogue(
            bodyCFG,
            ControlFlowGraphBuilder(prologue).build(),
            ControlFlowGraphBuilder(epilogue).build(),
        )

        val expected = ControlFlowGraphBuilder(prologue)
        expected.addLink(Pair(prologue, CFGLinkType.UNCONDITIONAL), middleNode)
        expected.addLink(Pair(middleNode, CFGLinkType.UNCONDITIONAL), epilogue)

        assertEquals(expected.build(), result)
    }

    @Test
    fun `test attachPrologueAndEpilogue for branching function`() {
        val prologue = IntermediateFormTreeNode.MemoryLabel("prologue")
        val epilogue = IntermediateFormTreeNode.MemoryLabel("prologue")

        val node1 = IntermediateFormTreeNode.MemoryLabel("node1")
        val node2 = IntermediateFormTreeNode.MemoryLabel("node2")
        val node3 = IntermediateFormTreeNode.MemoryLabel("node3")
        val node4T = IntermediateFormTreeNode.MemoryLabel("node4T")
        val node4F = IntermediateFormTreeNode.MemoryLabel("node4F")

        val bodyCFGBuilder = ControlFlowGraphBuilder(node1)
        bodyCFGBuilder.addLink(Pair(node1, CFGLinkType.CONDITIONAL_TRUE), node2)
        bodyCFGBuilder.addLink(Pair(node2, CFGLinkType.CONDITIONAL_FALSE), node3)
        bodyCFGBuilder.addLink(Pair(node3, CFGLinkType.CONDITIONAL_FALSE), node4F)
        bodyCFGBuilder.addLink(Pair(node3, CFGLinkType.CONDITIONAL_TRUE), node4T)

        val result = ControlFlow.attachPrologueAndEpilogue(
            bodyCFGBuilder.build(),
            ControlFlowGraphBuilder(prologue).build(),
            ControlFlowGraphBuilder(epilogue).build(),
        )

        val expected = ControlFlowGraphBuilder(prologue)
        expected.addLink(Pair(prologue, CFGLinkType.UNCONDITIONAL), node1)
        expected.addAllFrom(bodyCFGBuilder.build())

        // fill missing conditional links
        expected.addLink(Pair(node1, CFGLinkType.CONDITIONAL_FALSE), epilogue)
        expected.addLink(Pair(node2, CFGLinkType.CONDITIONAL_TRUE), epilogue)
        // link from both return branches to epilogue
        expected.addLink(Pair(node4T, CFGLinkType.UNCONDITIONAL), epilogue)
        expected.addLink(Pair(node4F, CFGLinkType.UNCONDITIONAL), epilogue)

        assertEquals(expected.build(), result)
    }
}
