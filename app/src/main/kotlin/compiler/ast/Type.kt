package compiler.ast

sealed class Type {
    object Unit : Type()
    object Boolean : Type()
    object Number : Type()
    data class Array(val elementType: Type) : Type()

    override fun toString(): String = when (this) {
        Boolean -> "Czy"
        Number -> "Liczba"
        Unit -> "Nic"
        is Array -> "[$elementType]"
    }
}
