@file:Suppress("UnstableApiUsage")

package com.centronias.tiner.robustyaml

import com.centronias.tiner.toFileLocationString
import com.intellij.find.usages.api.SearchTarget
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.findParentInFile
import com.intellij.psi.util.walkUp
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.Query
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLPsiElement

interface RobustYamlSymbol : Symbol
interface RobustYamlPsiSourcedSymbol : RobustYamlSymbol {
    val source: YAMLPsiElement
    val containingPrototype: Prototype
        get() = CachedValuesManager.getCachedValue(source) {
            CachedValueProvider.Result.createSingleDependency(
                checkNotNull(source.tryFindContainingPrototype()) {
                    "Failed to find containing prototype for $source@${source.toFileLocationString()}:${source.text}"
                },
                source.containingFile,
            )
        }
}

fun YAMLPsiElement.tryFindContainingPrototype(): Prototype? = if (isInPrototypeFile()) {
    findParentInFile { Prototype.from(it) != null }
        ?.let { Prototype.from(it) }
} else {
    null
}

fun YAMLPsiElement.isInPrototypeFile(): Boolean = (containingFile as? YAMLFile)?.isInPrototypes ?: false

interface RobustYamlSymbolWithUsages : RobustYamlSymbol, NavigationTarget, SearchTarget {
    val searchText: String
    val project: Project

    abstract override fun createPointer(): Pointer<out RobustYamlSymbolWithUsages>
}

fun <T : RobustYamlSymbolWithUsages> T.usages(
    filter: (RobustYamlPsiSymbolReference) -> Boolean = { true },
): Query<out RobustYamlPsiSymbolReference> {
    val symbolPointer = createPointer()
    return SearchService.getInstance()
        .searchWord(project, searchText)
        .caseSensitive(true)
        .inContexts(
            SearchContext.inCodeHosts(),
            SearchContext.inCode(),
            SearchContext.inPlainText(),
            SearchContext.inStrings(),
        )
        .inScope(GlobalSearchScope.allScope(project))
        .buildQuery(
            LeafOccurrenceMapper.withPointer(symbolPointer) { symbol, (scope, psiElement, offset) ->
                val service = PsiSymbolReferenceService.getService()
                walkUp(psiElement, offset, scope)
                    .asSequence()
                    .forEach { (element, offsetInElement) ->
                        val allFoundReferences = service.getReferences(
                            element,
                            PsiSymbolReferenceHints.offsetHint(offsetInElement),
                        ).asSequence()
                        val foundReferences = allFoundReferences
                            .filterIsInstance<RobustYamlPsiSymbolReference>()
                            .filter { it.rangeInElement.containsOffset(offsetInElement) && it.resolvesTo(symbol) }
                            .filter(filter)
                            .toList()
                        if (foundReferences.isNotEmpty()) {
                            return@withPointer foundReferences
                        }
                    }
                emptyList()
            },
        )
}

interface RobustYamlPsiSymbolReference : RobustYamlSymbol, PsiSymbolReference {
    val referenceUsageType: UsageType?

    companion object {
        fun findSymbolReferences(element: PsiElement) =
            PsiSymbolReferenceService.getService().getReferences(element).asSequence()
                .filterIsInstance<RobustYamlPsiSymbolReference>()
    }
}