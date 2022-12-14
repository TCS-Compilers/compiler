package compiler.grammar

import compiler.grammar.GrammarAnalysisTest.DfaFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class GrammarAnalysisTest11 {

    // COMPLICATED NON NULLABLE GRAMMAR (WITH PLUGGED TERMINAL)
    // To test if we can detect t \notin FIRST(start) [t occurs after sth non trivially non nullable]
    //
    // Grammar:
    //   Sigma = { start, Q, R, s, t }
    //   Productions = {
    //      start --> Q R R (start) + R t Q Q (dfaCNNG1)
    //      Q --> s (dfaQ1)
    //      R --> QQ + s (dfaR1)
    //   }
    //

    private val start = "start"
    private val symQ = "Q"
    private val symR = "R"
    private val syms = "s"
    private val symt = "t"

    private val expectedNullable = setOf<GrammarSymbol>()

    private val expectedFirst = mapOf(
        start to setOf(start, symQ, symR, syms),
        symQ to setOf(symQ, syms),
        symR to setOf(symQ, symR, syms),
        syms to setOf(syms),
        symt to setOf(symt),
    )

    private val expectedFollow = mapOf(
        start to setOf(),
        symQ to setOf(start, symQ, symR, syms, symt),
        symR to setOf(start, symQ, symR, syms, symt),
        syms to setOf(start, symQ, symR, syms, symt),
        symt to setOf(symQ, syms),
    )

    private val dfaCNNG = DfaFactory.createDfa(
        "startState",
        listOf(
            "startState",
            "state11", "state12", "state13",
            "state21", "state22", "state23",
            "accState",
        ),
        mapOf(
            Pair("startState", symQ) to "state11",
            Pair("state11", symR) to "state12",
            Pair("state12", symR) to "state13",
            Pair("state13", start) to "accState",
            Pair("startState", symR) to "state21",
            Pair("state21", symt) to "state22",
            Pair("state22", symQ) to "state23",
            Pair("state23", symQ) to "accState",
        ),
        "CNNG",
    )

    private val dfaQ = DfaFactory.createDfa(
        "startState",
        listOf("startState", "accState"),
        mapOf(
            Pair("startState", syms) to "accState",
        ),
        "Q",
    )

    private val dfaR = DfaFactory.createDfa(
        "startState",
        listOf("startState", "state1", "accState"),
        mapOf(
            Pair("startState", symQ) to "state1",
            Pair("state1", symQ) to "accState",
            Pair("startState", syms) to "accState",
        ),
        "R",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaCNNG,
            symQ to dfaQ,
            symR to dfaR,
        ),
    )

    @Test
    fun `test nullable for complicated non nullable grammar with plugged terminal`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for complicated non nullable grammar with plugged terminal`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for complicated non nullable grammar with plugged terminal`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
