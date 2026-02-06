package com.centronias.tiner

sealed interface WithErrors<out T, out E> {
    companion object {
        fun <T> just(value: T): WithErrors<T, Nothing> = Value(value)
        fun <E> errors(errors: Iterable<E>): WithErrors<Nothing, E> = Error(errors.toSet())
    }
}

@JvmInline
private value class Value<out T>(val value: T) : WithErrors<T, Nothing>

private data class ValueAndErrors<out T, out E>(val value: T, val errors: Set<E>) : WithErrors<T, E>

@JvmInline
private value class Error<out E>(val errors: Set<E>) : WithErrors<Nothing, E>

fun <T, E> WithErrors<T, E>.append(errors: Iterable<E>): WithErrors<T, E> = when (this) {
    is Error -> Error(this.errors + errors)
    is Value -> ValueAndErrors(value, errors.toSet())
    is ValueAndErrors -> ValueAndErrors(value, this.errors + errors)
}

fun <T, R, E> WithErrors<T, E>.flatMap(transform: (T) -> WithErrors<R, E>): WithErrors<R, E> = when (this) {
    is Error -> this
    is Value -> transform(value)
    is ValueAndErrors -> transform(value).append(errors)
}