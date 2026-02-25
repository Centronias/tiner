@file:Suppress("UnstableApiUsage")

package com.centronias.tiner.robustyaml

import com.centronias.tiner.ownTextRange
import com.centronias.tiner.pointer
import com.centronias.tiner.yamlTextPattern
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.yaml.psi.YAMLScalar

interface PrototypeReferenceLike {
    val id: PrototypeId
}

data class PrototypeReference(
    override val source: YAMLScalar,
) : RobustYamlPsiSourcedSymbol, RobustYamlPsiSymbolReference, PrototypeReferenceLike {
    override val id: PrototypeId.Valid = PrototypeId.from(source) as PrototypeId.Valid

    override val referenceUsageType: UsageType? = when {
        Prototype.Parents.pattern.accepts(source) -> UsageType(Bundle.messagePointer("usage.prototype.parent-declaration"))
        else -> null
    }

    override fun getElement() = source
    override fun getRangeInElement() = source.ownTextRange
    override fun resolveReference() = source.project.prototypes()[id]
    override fun createPointer(): Pointer<out Symbol?> {
        val source = source.createSmartPointer()
        return pointer { PrototypeReference(source.deref()) }
    }

    override fun toString() = id.toString()

    companion object {
        val pattern = yamlTextPattern("""[a-zA-Z_][a-zA-Z0-9_-]*""".toRegex())
    }
}

class PrototypeReferenceProvider : PsiSymbolReferenceProvider {
    override fun getReferences(
        element: PsiExternalReferenceHost,
        hints: PsiSymbolReferenceHints,
    ) = if (element is YAMLScalar && Prototype.fromIdElement(element) == null) {
        listOf(PrototypeReference(element))
    } else {
        emptyList()
    }

    override fun getSearchRequests(project: Project, target: Symbol) = emptyList<Nothing>()
}