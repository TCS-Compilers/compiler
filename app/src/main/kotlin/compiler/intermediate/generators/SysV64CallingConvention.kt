package compiler.intermediate.generators

import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register

val argPositionToRegister = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)
val callerSavedRegisters = listOf(Register.RAX, Register.RCX, Register.RDX, Register.RSI, Register.RDI, Register.R8, Register.R9, Register.R10, Register.R11)

object SysV64CallingConvention {
    fun genCall(targetFunction: IFTNode.MemoryLabel, args: List<IFTNode>, returnsValue: Boolean): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()
        val usedRegisters = mutableListOf<Register>()

        // First, move arguments to appropriate registers (or push to stack) according to call convention.
        for ((arg, register) in args zip argPositionToRegister) {
            val node = IFTNode.RegisterWrite(register, arg)
            cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
            usedRegisters.add(register)
        }

        var numberOfArgsPushedToStack = 0
        for (arg in args.drop(argPositionToRegister.size).reversed()) {
            val node = IFTNode.StackPush(arg)
            cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
            numberOfArgsPushedToStack += 1
        }

        // Add call instruction to actually call a given function
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.Call(targetFunction, usedRegisters, callerSavedRegisters)
        )

        // Abandon arguments that were previously put on stack
        if (numberOfArgsPushedToStack > 0)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(
                    Register.RSP,
                    IFTNode.Add(
                        IFTNode.RegisterRead(Register.RSP),
                        IFTNode.Const(numberOfArgsPushedToStack * memoryUnitSize.toLong())
                    )
                )
            )

        // At the end create IFTNode to get function result
        val readResultNode: IFTNode? =
            if (returnsValue) IFTNode.RegisterRead(Register.RAX)
            else null
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(cfgBuilder.build(), readResultNode)
    }
}
