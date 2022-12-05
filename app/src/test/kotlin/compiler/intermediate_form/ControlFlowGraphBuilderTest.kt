package compiler.intermediate_form
import compiler.common.reference_collections.referenceHashMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlFlowGraphBuilderTest {
    private val regToRead = Register()
    private val regToWrite = Register()

    private val innerNode = IntermediateFormTreeNode.RegisterRead(regToRead)

    private val entryNode = IntermediateFormTreeNode.RegisterWrite(regToWrite, innerNode)
    private val secondNode = IntermediateFormTreeNode.NoOp()
    private val conditionalTrueNode = IntermediateFormTreeNode.NoOp()
    private val conditionalFalseNode = IntermediateFormTreeNode.NoOp()
    private val afterConditionalNode = IntermediateFormTreeNode.NoOp()

    private val simpleCFG = ControlFlowGraph(
        treeRoots = listOf(entryNode, secondNode, conditionalTrueNode, conditionalFalseNode),
        entryTreeRoot = entryNode,
        unconditionalLinks = referenceHashMapOf(entryNode to secondNode),
        conditionalTrueLinks = referenceHashMapOf(secondNode to conditionalTrueNode),
        conditionalFalseLinks = referenceHashMapOf(secondNode to conditionalFalseNode)
    )

    private val simpleCFGWithExtraFinalNode = ControlFlowGraph(
        treeRoots = listOf(entryNode, secondNode, conditionalTrueNode, conditionalFalseNode, afterConditionalNode),
        entryTreeRoot = entryNode,
        unconditionalLinks = referenceHashMapOf(
            entryNode to secondNode,
            conditionalTrueNode to afterConditionalNode,
            conditionalFalseNode to afterConditionalNode
        ),
        conditionalTrueLinks = referenceHashMapOf(secondNode to conditionalTrueNode),
        conditionalFalseLinks = referenceHashMapOf(secondNode to conditionalFalseNode)
    )

    @Test
    fun `test pass entryTreeRoot in constructor`() {
        val cfg = ControlFlowGraphBuilder(entryNode).build()

        assertEquals(cfg.entryTreeRoot, entryNode)
        assertEquals(cfg.treeRoots, listOf(entryNode))
        assertEquals(cfg.unconditionalLinks, referenceHashMapOf())
        assertEquals(cfg.conditionalFalseLinks, referenceHashMapOf())
        assertEquals(cfg.conditionalTrueLinks, referenceHashMapOf())
    }

    @Test
    fun `test setEntryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.setEntryTreeRoot(entryNode)

        val cfg = cfgBuilder.build()
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfg)
    }

    @Test
    fun `test setEntryTreeRoot when already specified`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        assertFailsWith<IncorrectControlFlowGraphError> { cfgBuilder.setEntryTreeRoot(entryNode) }
    }

    @Test
    fun `test addLink`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLink(null, entryNode)
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test addAllFrom`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addAllFrom(simpleCFG)

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test addLinkFromAllFinalRoots when no entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, entryNode)
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfgBuilder.build())
    }

    @Test
    fun `test addLinkFromAllFinalRoots`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, secondNode)
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.CONDITIONAL_FALSE, conditionalFalseNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        assert(simpleCFG.equalsByValue(cfgBuilder.build()))
    }

    @Test
    fun `test addAllFrom without modifying entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        val remainingThreeNodes = ControlFlowGraphBuilder(secondNode)
        remainingThreeNodes.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        remainingThreeNodes.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)
        cfgBuilder.addAllFrom(remainingThreeNodes.build())
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test mergeUnconditionally`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)

        val remaining = ControlFlowGraphBuilder(secondNode)
        remaining.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        remaining.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)
        cfgBuilder.mergeUnconditionally(remaining.build())

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test mergeConditionally`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)

        cfgBuilder.mergeConditionally(
            ControlFlowGraphBuilder(conditionalTrueNode).build(),
            ControlFlowGraphBuilder(conditionalFalseNode).build()
        )

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test addSingleTree`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addSingleTree(secondNode)
        cfgBuilder.mergeConditionally(
            ControlFlowGraphBuilder(conditionalTrueNode).build(),
            ControlFlowGraphBuilder(conditionalFalseNode).build()
        )

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }
}
