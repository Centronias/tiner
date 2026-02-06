@file:Suppress("UnstableApiUsage")

package com.centronias.tiner.robustyaml

import com.centronias.tiner.ownTextRange
import com.centronias.tiner.pointer
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.psi.createSmartPointer
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.yaml.psi.YAMLScalar

data class PrototypeTypeReference(
    override val source: YAMLScalar,
) : RobustYamlPsiSourcedSymbol, RobustYamlPsiSymbolReference {
    val name: String = source.textValue
    override val referenceUsageType: UsageType? = null
    override fun createPointer(): Pointer<out Symbol?> {
        val source = source.createSmartPointer()
        return pointer { PrototypeTypeReference(source.deref()) }
    }

    override fun getElement() = source
    override fun getRangeInElement() = source.ownTextRange
    override fun resolveReference() = emptyList<Nothing>() // Types are declared in C#, so we're never resolving them.

    override fun toString() = name

}

fun PrototypeTypeReference?.sameAs(other: PrototypeTypeReference?, nullsAreSame: Boolean = false) = when {
    this == null || other == null -> nullsAreSame
    else -> name == other.name
}