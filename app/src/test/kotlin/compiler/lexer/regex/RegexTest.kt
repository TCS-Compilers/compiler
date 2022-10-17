/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package compiler.lexer.regex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val ATOMIC_AB = Regex.Atomic(setOf('a', 'b'))
private val ATOMIC_AC = Regex.Atomic(setOf('a', 'c'))
private val EMPTY = Regex.Empty()
private val EPSILON = Regex.Epsilon()
private val CONCAT_EP_EM = Regex.Concat(EPSILON, EMPTY)
private val CONCAT_EM_EP = Regex.Concat(EMPTY, EPSILON)
private val STAR_AB = Regex.Star(ATOMIC_AB)
private val UNION_EP_EM = Regex.Union(EPSILON, EMPTY)
private val UNION_EM_EP = Regex.Union(EMPTY, EPSILON)

class RegexTest {
    private fun <T : Comparable<T>> assertOrdered(desiredOrder: List<T>) {
        for (i in 0..(desiredOrder.size - 1)) {
            for (j in 0..(desiredOrder.size - 1)) {
                if (i > j) assertTrue(desiredOrder[i] > desiredOrder[j])
                else if (i < j) assertTrue(desiredOrder[i] < desiredOrder[j])
            }
        }
    }

    private fun <T> assertAllNotEqual(elements: List<T>) {
        for (i in 0..(elements.size - 1)) {
            for (j in 0..(elements.size - 1)) {
                if (i != j) assertNotEquals(elements[i], elements[j])
            }
        }
    }

    private fun <T> assertEqualsWellDefined(controlElement: T, equalElement: T, notEqualElement: T) {
        assertEquals(controlElement, equalElement)
        assertNotEquals(controlElement, notEqualElement)
    }

    @Test fun `test regexes of different kind are not equal`() {
        assertAllNotEqual(listOf(ATOMIC_AB, CONCAT_EP_EM, EMPTY, EPSILON, UNION_EP_EM, STAR_AB))
    }

    @Test fun `test equals operator is well defined for regexes of same kind`() {
        assertEquals<Regex>(EMPTY, Regex.Empty())

        assertEquals<Regex>(EPSILON, Regex.Epsilon())

        assertEqualsWellDefined(ATOMIC_AB, Regex.Atomic(setOf('a', 'b')), ATOMIC_AC)

        assertEqualsWellDefined(STAR_AB, Regex.Star(ATOMIC_AB), Regex.Star(EMPTY))

        assertEqualsWellDefined(UNION_EP_EM, Regex.Union(EPSILON, EMPTY), UNION_EM_EP)

        assertEqualsWellDefined(CONCAT_EP_EM, Regex.Concat(EPSILON, EMPTY), CONCAT_EM_EP)
    }

    @Test fun `test regexes are sorted lexicographically by type`() {
        // also ensures atomic is the smallest, which is important for us
        assertOrdered(listOf(ATOMIC_AB, CONCAT_EP_EM, EMPTY, EPSILON, STAR_AB, UNION_EP_EM))
    }

    @Test fun `test atomics are sorted lexicographically by chars`() {
        assertOrdered(listOf(Regex.Atomic(setOf('a')), ATOMIC_AB, ATOMIC_AC))
    }

    @Test fun `test concats are sorted lexicographically by children`() {
        assertOrdered(listOf(CONCAT_EM_EP, Regex.Concat(EPSILON, ATOMIC_AB), Regex.Concat(EPSILON, ATOMIC_AC), CONCAT_EP_EM))
    }

    @Test fun `test stars are sorted by children`() {
        assertOrdered(listOf(STAR_AB, Regex.Star(ATOMIC_AC), Regex.Star(EPSILON)))
    }

    @Test fun `test unions are sorted lexicographically by children`() {
        assertOrdered(listOf(UNION_EM_EP, Regex.Union(EPSILON, ATOMIC_AB), Regex.Union(EPSILON, ATOMIC_AC), UNION_EP_EM))
    }
}
