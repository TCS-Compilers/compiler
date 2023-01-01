package compiler.lowlevel.dataflow

import compiler.intermediate.Register
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction
import compiler.utils.ReferenceSet
import compiler.utils.combineReferenceSets
import compiler.utils.referenceHashMapOf
import compiler.utils.referenceHashSetOf
import compiler.utils.referenceMapOf

object Liveness {
    data class LivenessGraphs(
        val interferenceGraph: Map<Register, Set<Register>>,
        val copyGraph: Map<Register, Set<Register>>,
    )

    fun inducedSubgraph(graph: Map<Register, Set<Register>>, subset: Set<Register>): Map<Register, Set<Register>> =
        graph.entries
            .filter { it.key in subset }
            .associate { it.key to it.value.filter { vertex -> vertex in subset }.toSet() }.toMap()

    object LivenessDataFlowAnalyzer : DataFlowAnalyzer<ReferenceSet<Register>>() {
        override val backward = true

        override val entryPointValue: ReferenceSet<Register> = referenceHashSetOf()

        override val latticeMaxElement: ReferenceSet<Register> = referenceHashSetOf()

        override fun latticeMeetFunction(elements: Collection<ReferenceSet<Register>>): ReferenceSet<Register> {
            return combineReferenceSets(elements.toList())
        }

        override fun transferFunction(instruction: Instruction, inValue: ReferenceSet<Register>): ReferenceSet<Register> {
            val gen = instruction.regsUsed
            val kill = instruction.regsDefined
            return combineReferenceSets(referenceHashSetOf(gen.toList()), referenceHashSetOf(inValue.filter { it !in kill }))
        }
    }

    fun computeLiveness(linearProgram: List<Asmable>): LivenessGraphs {
        val instructionList = linearProgram.filterIsInstance<Instruction>()

        // find all registers, prepare empty graphs
        val allRegisters = referenceHashSetOf<Register>()
        instructionList.forEach {
            allRegisters.addAll(it.regsDefined)
            allRegisters.addAll(it.regsUsed)
        }

        val interferenceGraph = referenceHashMapOf(allRegisters.map { it to referenceHashSetOf<Register>() })
        val copyGraph = referenceHashMapOf(allRegisters.map { it to referenceHashSetOf<Register>() })

        // run analysis and fill the graphs
        val dataFlowResult = LivenessDataFlowAnalyzer.analyze(linearProgram)
        val outLiveRegisters = referenceMapOf(dataFlowResult.outValues.map { (instr, regs) -> instr to combineReferenceSets(regs, referenceHashSetOf(instr.regsDefined.toList())) })

        instructionList.forEach {
            for (definedReg in it.regsDefined)
                for (regLiveOnOutput in outLiveRegisters[it]!!)
                    if (definedReg !== regLiveOnOutput && !(it is Instruction.InPlaceInstruction.MoveRR && it.reg_dest === definedReg && it.reg_src === regLiveOnOutput)) {
                        interferenceGraph[definedReg]!!.add(regLiveOnOutput)
                        interferenceGraph[regLiveOnOutput]!!.add(definedReg)
                    }

            if (it is Instruction.InPlaceInstruction.MoveRR) {
                copyGraph[it.reg_dest]!!.add(it.reg_src)
                copyGraph[it.reg_src]!!.add(it.reg_dest)
            }
        }

        return LivenessGraphs(interferenceGraph, copyGraph)
    }
}
