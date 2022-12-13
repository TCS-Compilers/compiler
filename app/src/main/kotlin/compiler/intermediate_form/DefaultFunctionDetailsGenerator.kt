package compiler.intermediate_form

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.common.intermediate_form.FunctionDetailsGenerator
import compiler.common.reference_collections.referenceHashMapOf

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

const val memoryUnitSize: ULong = 8u
val argPositionToRegister = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)
val calleeSavedRegistersWithoutRSPAndRBP = listOf(Register.RBX, Register.R12, Register.R13, Register.R14, Register.R15)
const val DISPLAY_LABEL_IN_MEMORY = "display"

// Function Details Generator consistent with SystemV AMD64 calling convention
data class DefaultFunctionDetailsGenerator(
    val parameters: List<NamedNode>,
    val variableToStoreFunctionResult: Variable?,
    val functionLocationInCode: IntermediateFormTreeNode.MemoryLabel,
    val depth: ULong,
    val variablesLocationTypes: Map<NamedNode, VariableLocationType>, // should contain parameters
    val displayAddress: IntermediateFormTreeNode,
    val createRegisterFor: (NamedNode) -> Register = { Register() } // for testing
) : FunctionDetailsGenerator {

    private val variablesStackOffsets: MutableMap<NamedNode, ULong> = referenceHashMapOf()
    private val variablesRegisters: MutableMap<NamedNode, Register> = referenceHashMapOf()
    private var variablesTotalOffset: ULong = 0u
    private val previousDisplayEntryRegister = Register()

    init {
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {
                VariableLocationType.MEMORY -> {
                    variablesTotalOffset += memoryUnitSize
                    variablesStackOffsets[variable] = variablesTotalOffset
                }

                VariableLocationType.REGISTER -> {
                    variablesRegisters[variable] = createRegisterFor(variable)
                }
            }
        }
    }

    override fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()

        // First, move arguments to appropriate registers (or push to stack) according to call convention.
        for ((arg, register) in args zip argPositionToRegister) {
            val node = IntermediateFormTreeNode.RegisterWrite(register, arg)
            cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
        }

        var numberOfArgsPushedToStack = 0
        for (arg in args.drop(argPositionToRegister.size).reversed()) {
            val node = IntermediateFormTreeNode.StackPush(arg)
            cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
            numberOfArgsPushedToStack += 1
        }

        // Add call instruction to actually call a given function
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.Call(functionLocationInCode)
        )

        // Abandon arguments that were previously put on stack
        if (numberOfArgsPushedToStack > 0)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.Add(
                    IntermediateFormTreeNode.RegisterRead(Register.RSP),
                    IntermediateFormTreeNode.Const(numberOfArgsPushedToStack * memoryUnitSize.toLong())
                )
            )

        // At the end create IFTNode to get function result
        val readResultNode: IFTNode? =
            if (variableToStoreFunctionResult != null) IntermediateFormTreeNode.RegisterRead(Register.RAX)
            else null
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(cfgBuilder.build(), readResultNode)
    }

    override fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // backup rbp
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(Register.RBP))
        )

        // update rbp
        val movRbpRsp = IntermediateFormTreeNode.RegisterWrite(
            Register.RBP,
            IntermediateFormTreeNode.RegisterRead(Register.RSP)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRbpRsp)

        // allocate memory for local variables
        val subRsp = IntermediateFormTreeNode.RegisterWrite(
            Register.RSP,
            IntermediateFormTreeNode.Subtract(
                IntermediateFormTreeNode.RegisterRead(Register.RSP),
                IntermediateFormTreeNode.Const(variablesTotalOffset.toLong())
            )
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, subRsp)

        // update display
        val savePreviousRbp = IntermediateFormTreeNode.RegisterWrite(
            previousDisplayEntryRegister,
            IntermediateFormTreeNode.MemoryRead(displayElementAddress())
        )
        val updateRbpAtDepth = IntermediateFormTreeNode.MemoryWrite(
            displayElementAddress(),
            IntermediateFormTreeNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, savePreviousRbp)
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, updateRbpAtDepth)

        // move args from Registers and Stack
        for ((param, register) in parameters zip argPositionToRegister) {
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param,
                    IntermediateFormTreeNode.RegisterRead(register),
                    true
                )
            )
        }
        for (param in parameters.drop(argPositionToRegister.size).withIndex()) {
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param.value,
                    IntermediateFormTreeNode.Add(
                        IntermediateFormTreeNode.RegisterRead(Register.RBP), // add 2 to account old RBP and return address
                        IntermediateFormTreeNode.Const((param.index + 2) * memoryUnitSize.toLong())
                    ),
                    true
                ),
            )
        }

        // backup callee-saved registers
        for (register in calleeSavedRegistersWithoutRSPAndRBP.reversed())
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(register))
            )

        return cfgBuilder.build()
    }

    override fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // restore callee-saved registers
        for (register in calleeSavedRegistersWithoutRSPAndRBP)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.RegisterWrite(register, IntermediateFormTreeNode.StackPop())
            )

        // move result to RAX
        if (variableToStoreFunctionResult != null)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.RegisterWrite(Register.RAX, genRead(variableToStoreFunctionResult, true))
            )

        // restore previous rbp in display at depth
        val restorePreviousDisplayEntry = IntermediateFormTreeNode.MemoryWrite(
            displayElementAddress(),
            IntermediateFormTreeNode.RegisterRead(previousDisplayEntryRegister)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, restorePreviousDisplayEntry)

        // restore rsp
        val movRspRbp = IntermediateFormTreeNode.RegisterWrite(
            Register.RSP,
            IntermediateFormTreeNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRspRbp)

        // restore rbp
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.RegisterWrite(Register.RBP, IntermediateFormTreeNode.StackPop())
        )

        return cfgBuilder.build()
    }

    override fun setAdditionalOffsetForSpilledRegisters(numRegisters: ULong) {
        // write a value to some private, previously empty IFTNode
        // update genPrologue + genEpilogue to take this into account
        TODO()
    }

    private fun genAccess(namedNode: NamedNode, isDirect: Boolean, regAccessGenerator: (Register) -> IFTNode, memAccessGenerator: (IFTNode) -> IFTNode): IFTNode {
        // requires correct value for RBP register
        return if (isDirect) {
            when (variablesLocationTypes[namedNode]!!) {
                VariableLocationType.MEMORY -> {
                    memAccessGenerator(
                        IntermediateFormTreeNode.Subtract(
                            IntermediateFormTreeNode.RegisterRead(Register.RBP),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[namedNode]!!.toLong())
                        )
                    )
                }

                VariableLocationType.REGISTER ->
                    regAccessGenerator(variablesRegisters[namedNode]!!)
            }
        } else {
            if (variablesLocationTypes[namedNode]!! == VariableLocationType.REGISTER)
                throw IndirectRegisterAccess()

            memAccessGenerator(
                IntermediateFormTreeNode.Subtract(
                    IntermediateFormTreeNode.MemoryRead(displayElementAddress()),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[namedNode]!!.toLong())
                )
            )
        }
    }

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IntermediateFormTreeNode =
        genAccess(namedNode, isDirect, { IntermediateFormTreeNode.RegisterRead(it) }, { IntermediateFormTreeNode.MemoryRead(it) })

    override fun genWrite(namedNode: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode =
        genAccess(namedNode, isDirect, { IntermediateFormTreeNode.RegisterWrite(it, value) }, { IntermediateFormTreeNode.MemoryWrite(it, value) })

    private fun displayElementAddress(): IntermediateFormTreeNode {
        return IntermediateFormTreeNode.Add(
            displayAddress,
            IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
        )
    }

    class IndirectRegisterAccess : Exception()
}
