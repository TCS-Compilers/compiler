package compiler.common.dfa.state_dfa

import compiler.common.dfa.AbstractDfa
import compiler.common.dfa.DfaWalk

interface Dfa<A, R> : AbstractDfa<A, R> {
    val startState: DfaState<A, R>

    override fun newWalk(): DfaWalk<A, R> {
        return object : DfaWalk<A, R> {
            private var currentState: DfaState<A, R>? = startState
            override fun getAcceptingStateTypeOrNull(): R? {
                return currentState?.result
            }

            override fun isDead(): Boolean {
                return currentState == null || (currentState?.result == null && currentState?.possibleSteps?.isEmpty() == true)
            }

            override fun step(a: A) {
                currentState = currentState?.possibleSteps?.get(a)
            }
        }
    }

    private fun dfs(visit: (DfaState<A, R>) -> Unit) {
        val visited: MutableSet<DfaState<A, R>> = HashSet()
        fun dfs(state: DfaState<A, R>) {
            if (!visited.add(state)) return
            visit(state)
            for ((_, neighbour) in state.possibleSteps) dfs(neighbour)
        }
        dfs(startState)
    }

    fun getPredecessors(state: DfaState<A, R>): Map<A, Collection<DfaState<A, R>>> {
        val predecessors: MutableMap<A, HashSet<DfaState<A, R>>> = HashMap()
        dfs { visitedState ->
            for ((edge, neighbour) in visitedState.possibleSteps) {
                if (neighbour == state) {
                    predecessors.getOrPut(edge) { HashSet() }.add(visitedState)
                }
            }
        }
        return predecessors
    }

    fun getAcceptingStates(): Collection<DfaState<A, R>> {
        val acceptingStates: MutableSet<DfaState<A, R>> = HashSet()
        dfs { visitedState -> visitedState.result?.let { acceptingStates.add(visitedState) } }
        return acceptingStates
    }
}
