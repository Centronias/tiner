@file:Suppress("UnstableApiUsage")

package com.centronias.tiner.robustyaml

import com.centronias.tiner.ResourcesDir
import com.centronias.tiner.withType
import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Query
import kotlinx.coroutines.runBlocking
import org.jetbrains.yaml.psi.YAMLFile

val csharpIdentifierRegex: Regex = """[a-zA-Z_][a-zA-Z0-9_]*""".toRegex()
val prototypeIdentifierRegex: Regex = """[a-zA-Z_][a-zA-Z0-9_-]*""".toRegex()

inline val YAMLFile.isInPrototypes: Boolean
    get() {
        val prototypes = ResourcesDir.fromProject(project)?.prototypes?.toNioPath() ?: return false
        val vf = virtualFile?.toNioPath() ?: return false
        return vf.startsWith(prototypes)
    }

fun Project.prototypes(): PrototypesStorage = CachedValuesManager.getManager(this).getCachedValue(this) {
    CachedValueProvider.Result.create(
        runBlocking { PrototypesStorage.new(this@prototypes) },
        // TODO Flushing the WHOLE cache on a PSI change anywhere in the project is probably awful. idk
        PsiModificationTracker.MODIFICATION_COUNT,
    )
}

class PrototypeDeclarationProvider : PsiSymbolDeclarationProvider {
    override fun getDeclarations(element: PsiElement, offsetInElement: Int) = listOfNotNull(Prototype.from(element))
}

class PrototypeUsageSearcher : UsageSearcher {
    override fun collectSearchRequest(parameters: UsageSearchParameters): Query<out Usage>? =
        when (val target = parameters.target) {
            !is RobustYamlSymbolWithUsages -> null
            else if (target.searchText.isEmpty()) -> null
            else -> target.usages().mapping { PsiUsage.textUsage(it).withType(it.referenceUsageType) }
        }
}