@file:Suppress("UnstableApiUsage")

package com.centronias.tiner

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

fun <T> pointer(block: PointerDslScope.() -> T) = Pointer {
    runCatching { block(PointerDslScopeImpl) }
        .recover { _: PointerDslScopeImpl.FC -> return@Pointer null }
        .getOrThrow()
}

sealed interface PointerDslScope {
    fun <T : PsiElement> SmartPsiElementPointer<T>.deref(): T
    fun <T> Pointer<T>.deref(): T
    fun <T> List<Pointer<T>>.deref(): List<T>
}

private object PointerDslScopeImpl : PointerDslScope {
    override fun <T : PsiElement> SmartPsiElementPointer<T>.deref(): T = dereference() ?: throw FC()
    override fun <T> Pointer<T>.deref(): T = dereference() ?: throw FC()
    override fun <T> List<Pointer<T>>.deref(): List<T> = map { it.deref() ?: throw FC() }

    class FC : Throwable()
}

inline fun <T, R> Pointer<T>.map(crossinline block: (T) -> R) = pointer { block(deref()) }