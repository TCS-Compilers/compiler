package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf
import java.lang.RuntimeException

class IncorrectControlFlowGraphError(message: String) : RuntimeException(message)

class ControlFlowGraphBuilder(var entryTreeRoot: IFTNode? = null) {
    private var unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var treeRoots = ArrayList<IFTNode>()
    private val finalTreeRoots: List<IFTNode> get() = treeRoots.filter {
        it !in unconditionalLinks && it !in conditionalTrueLinks && it !in conditionalFalseLinks
    }

    init {
        entryTreeRoot?.let { treeRoots.add(it) }
    }

    fun addLink(from: Pair<IFTNode, CFGLinkType>?, to: IFTNode, addDestination: Boolean = true) {
        if (addDestination && !treeRoots.contains(to))
            treeRoots.add(to)
        if (from != null) {
            if (!treeRoots.contains(from.first))
                treeRoots.add(from.first)

            val links = when (from.second) {
                CFGLinkType.UNCONDITIONAL -> unconditionalLinks
                CFGLinkType.CONDITIONAL_TRUE -> conditionalTrueLinks
                CFGLinkType.CONDITIONAL_FALSE -> conditionalFalseLinks
            }
            links[from.first] = to
        } else {
            entryTreeRoot = to
            if (!treeRoots.contains(to))
                treeRoots.add(to)
        }
    }

    fun addLinkFromAllFinalRoots(linkType: CFGLinkType, to: IFTNode) {
        val linksToAdd = finalTreeRoots.map { Pair(Pair(it, linkType), to) }.toList()
        for (link in linksToAdd)
            addLink(link.first, link.second)
        if (entryTreeRoot == null)
            makeRoot(to)
    }

    fun makeRoot(root: IFTNode) {
        if (entryTreeRoot != null)
            throw IncorrectControlFlowGraphError("Tried to create second entryTreeRoot in CFGBuilder")
        entryTreeRoot = root
        if (!treeRoots.contains(root))
            treeRoots.add(root)
    }

    fun addAllFrom(cfg: ControlFlowGraph, modifyEntryTreeRoot: Boolean = false) {
        treeRoots.addAll(cfg.treeRoots)

        if (modifyEntryTreeRoot || entryTreeRoot == null)
            entryTreeRoot = cfg.entryTreeRoot
        unconditionalLinks.putAll(cfg.unconditionalLinks)
        conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
        conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)
    }

    fun updateNodes(nodeFilter: (IFTNode) -> Boolean, nodeUpdate: (IFTNode) -> IFTNode) {
        val newTreeRoots = treeRoots.associateWith {
            if (nodeFilter(it)) nodeUpdate(it) else it
        }

        fun linkReplacer(fromAndTo: Map.Entry<IFTNode, IFTNode>): Pair<IFTNode, IFTNode> {
            return Pair(newTreeRoots[fromAndTo.key]!!, newTreeRoots[fromAndTo.value]!!)
        }

        unconditionalLinks = referenceHashMapOf(unconditionalLinks.map { linkReplacer(it) }.toList())
        conditionalTrueLinks = referenceHashMapOf(conditionalTrueLinks.map { linkReplacer(it) }.toList())
        conditionalFalseLinks = referenceHashMapOf(conditionalFalseLinks.map { linkReplacer(it) }.toList())
        entryTreeRoot = newTreeRoots[entryTreeRoot]
        treeRoots = ArrayList(treeRoots.map { newTreeRoots[it]!! })
    }

    fun build(): ControlFlowGraph {
        return ControlFlowGraph(
            treeRoots,
            entryTreeRoot,
            unconditionalLinks,
            conditionalTrueLinks,
            conditionalFalseLinks
        )
    }

    fun mergeUnconditionally(cfg: ControlFlowGraph): ControlFlowGraphBuilder {
        build().finalTreeRoots.forEach {
            addLink(Pair(it, CFGLinkType.UNCONDITIONAL), cfg.entryTreeRoot!!, false)
        }
        addAllFrom(cfg)
        if (entryTreeRoot == null) entryTreeRoot = cfg.entryTreeRoot
        return this
    }

    fun mergeConditionally(cfgTrue: ControlFlowGraph, cfgFalse: ControlFlowGraph): ControlFlowGraphBuilder {
        build().finalTreeRoots.forEach {
            addLink(Pair(it, CFGLinkType.CONDITIONAL_TRUE), cfgTrue.entryTreeRoot!!, false)
            addLink(Pair(it, CFGLinkType.CONDITIONAL_FALSE), cfgFalse.entryTreeRoot!!, false)
        }
        addAllFrom(cfgTrue)
        addAllFrom(cfgFalse)
        return this
    }

    fun addSingleTree(iftNode: IntermediateFormTreeNode): ControlFlowGraphBuilder {
        mergeUnconditionally(
            ControlFlowGraph(
                listOf(iftNode),
                iftNode, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf()
            )
        )
        return this
    }
}
