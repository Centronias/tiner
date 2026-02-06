@file:Suppress("UnstableApiUsage")

package com.centronias.tiner

import com.intellij.find.usages.api.PsiUsage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.declarations.PolySymbolDeclarationProvider
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType

fun PsiElement.toFileLocationString(): String = "${containingFile.virtualFile.path}${textRangeIn(containingFile)}"

/** Assuming [outer]'s text contains the receiver's text, returns a [TextRange] which describes the receivers textual
 * position in [outer]. */
fun PsiElement.textRangeIn(outer: PsiElement): TextRange {
    val inner = textRange
    val startOffset = inner.startOffset - outer.textRange.startOffset
    return TextRange(startOffset, startOffset + inner.length)
}

inline val PsiElement.ownTextRange get() = ElementManipulators.getValueTextRange(this)

fun PolySymbolDeclarationProvider.Companion.composite(
    vararg providers: PolySymbolDeclarationProvider,
) = object : PolySymbolDeclarationProvider {
    override fun getDeclarations(element: PsiElement, offsetInElement: Int) =
        providers.flatMap { it.getDeclarations(element, offsetInElement) }
}

inline val Project.resourcesDir: ResourcesDir? get() = ResourcesDir.fromProject(this)

@JvmInline
value class ResourcesDir private constructor(val inner: VirtualFile) {
    inline val prototypes: VirtualFile? get() = inner.findChild(PROTOTYPES_DIR)

    companion object {
        fun fromProject(project: Project) =
            project.guessProjectDir()?.findChild(RESOURCES_DIR)?.takeIf { it.isDirectory }?.let { ResourcesDir(it) }

        const val RESOURCES_DIR = "Resources"
        const val PROTOTYPES_DIR = "Prototypes"
    }
}

inline fun <T, reified E : Throwable> Result<T>.recover(block: (E) -> T): Result<T> =
    when (val e = exceptionOrNull()) {
        is E -> runCatching { block(e) }
        else -> this
    }

fun PsiUsage.withType(type: UsageType?): PsiUsage = object : PsiUsage by this {
    private val delegate = this
    override val usageType = type
}