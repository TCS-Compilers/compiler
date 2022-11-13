package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.E2eAsserter.assertErrorOfType
import org.junit.Ignore
import org.junit.Test

class TypeCheckingErrorsTest {
    private fun assertInvalidTypeError(program: String) {
        assertErrorOfType(program, Diagnostic.InvalidType::class)
    }

    private fun assertConditionalMismatchError(program: String) {
        assertErrorOfType(program, Diagnostic.ConditionalTypesMismatch::class)
    }

    private fun assertUninitializedGlobalVariableError(program: String) {
        assertErrorOfType(program, Diagnostic.UninitializedGlobalVariable::class)
    }

    private fun assertConstantWithoutValueError(program: String) {
        assertErrorOfType(program, Diagnostic.ConstantWithoutValue::class)
    }

    @Ignore
    @Test
    fun `test define variable with unit type`() {
        assertInvalidTypeError("czynność test() { zm a: Nic; }")
        assertInvalidTypeError("czynność test() { wart a: Nic; }")
        assertInvalidTypeError("czynność test() { stała a: Nic; }")
    }

    @Ignore
    @Test
    fun `test function parameter with unit type`() {
        assertInvalidTypeError(
            """
            czynność f(a: Nic) {
                zakończ
            }
            """
        )
    }

    @Ignore
    @Test
    fun `test instantiate variable with wrong type`() {
        assertInvalidTypeError("zm a: Liczba = prawda;")
        assertInvalidTypeError("zm a: Liczba = fałsz;")
        assertInvalidTypeError("czynność test() { zm a: Liczba = (5 > 3) lub 0 == -1; }")
        assertInvalidTypeError("czynność test() { zm b: Czy = fałsz; zm a: Liczba = b; }")
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zwróć prawda
                }
                czynność g() {
                    zm a: Liczba = f()
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Liczba = f()
                }
            """
        )

        assertInvalidTypeError("zm a: Czy = 0;")
        assertInvalidTypeError("zm a: Czy = 13234;")
        assertInvalidTypeError("czynność test() { zm a: Czy = 5 * 3 - (1 % 2); }")
        assertInvalidTypeError("czynność test() { zm b: Liczba = 4; zm a: Czy = b; }")
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zwróć 3
                }
                czynność g() {
                    zm a: Czy = f()
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Czy = f()
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test uninitialized global variables`() {
        assertUninitializedGlobalVariableError("zm a: Liczba;")
        assertUninitializedGlobalVariableError("zm a: Czy;")
        assertUninitializedGlobalVariableError("wart a: Liczba;")
        assertUninitializedGlobalVariableError("wart a: Czy;")
    }

    @Ignore
    @Test
    fun `test constant without value`() {
        assertConstantWithoutValueError("stała a: Liczba;")
        assertConstantWithoutValueError("stała a: Czy;")
        assertConstantWithoutValueError(
            """
                czynność f() {
                    stała a: Liczba
                }
            """
        )
        assertConstantWithoutValueError(
            """
                czynność f() {
                    stała a: Czy
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test assignment to variable of wrong type`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 1 
                    a = prawda
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 1
                    a = fałsz
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 1
                    a = (5 > 3) lub 0 == -1
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm b: Czy = fałsz
                    zm a: Liczba
                    a = b
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zwróć prawda
                }
                czynność g() {
                    zm a: Liczba
                    a = f()
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Liczba
                    a = f()
                }
            """
        )

        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Czy
                    a = 0
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Czy
                    a = -13
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Czy
                    a = 5 * 3 - (1 % 2)
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm b: Liczba = 4; 
                    zm a: Czy
                    a = b
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zwróć 3
                }
                czynność g() {
                    zm a: Czy
                    a = f()
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Czy
                    a = f()
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test integer operators with wrong types`() {
        val binaryOperators = listOf("+", "-", "*", "/", "%", "^", "&", "|", "<<", ">>")
        val unaryOperators = listOf("-", "~")

        for (operator in binaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Liczba = a $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Liczba = b $operator a;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Liczba = f() $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm a: Liczba = 4
                        zm c: Liczba = a $operator f();
                    }
                """
            )
        }

        for (operator in unaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Liczba = $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm c: Liczba = $operator f();
                    }
                """
            )
        }
    }

    @Ignore
    @Test
    fun `test boolean operators with wrong types`() {
        val binaryOperators = listOf("oraz", "lub", "wtw", "albo")
        val unaryOperators = listOf("nie")

        for (operator in binaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = a $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = b $operator a;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Czy = f() $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm a: Liczba = 4
                        zm c: Czy = a $operator f();
                    }
                """
            )
        }

        for (operator in unaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm b: Liczba = 17
                        zm c: Czy = $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm c: Czy = $operator f();
                    }
                """
            )
        }
    }

    @Ignore
    @Test
    fun `test comparison operators with wrong types`() {
        val comparisonOperators = listOf("==", "!=", ">", ">=", "<", "<=")

        for (operator in comparisonOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = a $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = b $operator a;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Czy = f() $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm a: Liczba = 4
                        zm c: Czy = a $operator f();
                    }
                """
            )
        }
    }

    @Ignore
    @Test
    fun `test conditional operator with wrong types`() {
        assertConditionalMismatchError("czynność test() { zm a: Liczba = prawda ? 10 : fałsz; }")
        assertConditionalMismatchError("czynność test() { zm a: Liczba = prawda ? prawda : 13; }")
        assertConditionalMismatchError("czynność test() { zm a: Liczba = 34 ? fałsz : 14; }")
        assertInvalidTypeError("czynność test() { zm a: Liczba = 34 ? 10 : 31; }")
        assertInvalidTypeError("czynność test() { zm a: Liczba = 99 ? prawda : fałsz; }")
        assertInvalidTypeError("czynność test() { zm a: Czy = fałsz ? 11 : 16; }")
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    f() ? 10 : 11                            
                }
            """
        )
        assertConditionalMismatchError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    prawda ? f() : 11                            
                }
            """
        )
        assertConditionalMismatchError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    prawda ? 12 : f()                   
                }
            """
        )
        assertConditionalMismatchError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    prawda ? fałsz : f()                   
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong return type`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    zwróć 4                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zwróć fałsz                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zakończ                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zwróć fałsz                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zakończ                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zwróć 654
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong default parameter type`() {
        assertInvalidTypeError(
            """
                czynność f(a: Liczba = fałsz) {
                    zakończ
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f(a: Czy = 13) {
                    zakończ
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong function call argument type`() {
        assertInvalidTypeError(
            """
                czynność f(a: Liczba) {
                    zakończ
                }
                czynność g() {
                    f(fałsz)
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Liczba) {
                    zakończ
                }
                czynność g() {
                    f(h())
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f(a: Czy) {
                    zakończ
                }
                czynność g() {
                    f(7)
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Czy) {
                    zakończ
                }
                czynność g() {
                    f(h())
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f(a: Liczba, b: Liczba = 2, c: Liczba = 3) -> Liczba {
                    zwróć a + b + c
                }
                czynność g() {
                    f(3, c = prawda)
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Liczba, b: Liczba = 2, c: Liczba = 3) -> Liczba {
                    zwróć a + b + c
                }
                czynność g() {
                    f(3, c = h())
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f(a: Czy, b: Czy = fałsz, c: Czy = fałsz) -> Czy {
                    zwróć a lub b lub c
                }
                czynność g() {
                    f(fałsz, c = 3)
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Czy, b: Czy = fałsz, c: Czy = fałsz) -> Czy {
                    zwróć a lub b lub c
                }
                czynność g() {
                    f(fałsz, c = h())
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong expression type in if else statements`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    jeśli (4) {
                        zakończ
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    wart a: Liczba = 15
                    jeśli (a) {
                        zakończ
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność g() {
                    zakończ
                }
                czynność f() {
                    jeśli (g()) {
                        zakończ
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    jeśli (fałsz) {
                        zakończ
                    } zaś gdy (555) {
                        zakończ      
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    wart a: Liczba = 15
                    jeśli (a < 10) {
                        zakończ
                    } zaś gdy (a) {
                        zakończ      
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność g() {
                    zakończ
                }
                czynność f() {
                    jeśli (fałsz) {
                        zakończ
                    } zaś gdy (g()) {
                        zakończ      
                    }
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong expression type in while loop`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (13) {
                        a = a + 1
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (a) {
                        a = a + 1
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność g() {
                    zakończ
                }
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (g()) {
                        a = a + 1
                    }
                }
            """
        )
    }
}
