package com.centronias.tiner

import com.intellij.patterns.*
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import kotlin.reflect.KFunction1

inline fun <reified T : Any> pattern(): ObjectPattern.Capture<T> = PlatformPatterns.instanceOf(T::class.java)
fun <T : Any> pattern(equals: T) = StandardPatterns.`object`<T>(equals)
inline fun <reified T : Any> pattern(
    name: String,
    crossinline condition: (T) -> Boolean,
): ObjectPattern<T, *> = PlatformPatterns.instanceOf(T::class.java).with(name, condition)

@JvmName("anyPsiElementPattern")
fun psiElementPattern() = psiElementPattern<PsiElement>()

inline fun <reified T : PsiElement> psiElementPattern(): PsiElementPattern.Capture<T> =
    PlatformPatterns.psiElement(T::class.java)

inline fun <reified T : PsiElement> psiElementPattern(
    name: String,
    crossinline condition: (T) -> Boolean,
): PsiElementPattern.Capture<T> = psiElementPattern<T>().with(name, condition)

inline fun <T : Any, Self : ObjectPattern<T, Self>> Self.with(
    name: String,
    crossinline condition: (T) -> Boolean,
): Self = with(
    object : PatternCondition<T>(name) {
        override fun accepts(t: T, context: ProcessingContext?): Boolean = condition(t)
    },
)

inline fun <reified T, R, Self : ObjectPattern<T, Self>, F : KFunction1<T, R?>> Self.with(
    f: F,
    valuePattern: ElementPattern<out R>,
): Self = with<T, R, Self>(f.name, valuePattern, f)

inline fun <reified T, R, Self : ObjectPattern<T, Self>> Self.with(
    name: String,
    valuePattern: ElementPattern<out R>,
    crossinline accessor: T.() -> R?,
): Self = with(
    object : PropertyPatternCondition<T, R>(name, valuePattern) {
        override fun getPropertyValue(o: Any) = (o as? T)?.accessor()
    },
)

inline fun <reified T : Any, R> KFunction1<T, R?>.pattern(
    valuePattern: ElementPattern<out R>,
): ObjectPattern.Capture<T> = pattern<T>().with(this, valuePattern)

inline fun <reified T : PsiElement, R> KFunction1<T, R?>.psiElementPattern(
    valuePattern: ElementPattern<out R>,
): PsiElementPattern.Capture<T> = psiElementPattern<T>().with(this, valuePattern)

fun <T> collectionPattern(elementPattern: ElementPattern<T>): CollectionPattern<T> =
    PlatformPatterns.collection<T>().all(elementPattern)

fun <T> oneOfPatterns(
    first: ElementPattern<out T>,
    second: ElementPattern<out T>,
    vararg more: ElementPattern<out T>,
): ElementPattern<T> = StandardPatterns.or<T>(first, second, *more)

fun <T> Collection<ElementPattern<out T>>.orred(): ElementPattern<out T> =
    singleOrNull() ?: StandardPatterns.or(*toTypedArray())