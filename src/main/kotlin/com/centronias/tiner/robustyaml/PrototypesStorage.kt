package com.centronias.tiner.robustyaml

import com.centronias.tiner.resourcesDir
import com.intellij.openapi.project.Project
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.MultiMap
import org.jetbrains.yaml.psi.*

interface PrototypesStorage {
    operator fun get(id: PrototypeId.Valid): Collection<Prototype>

    companion object {
        suspend fun new(project: Project): PrototypesStorage {
            val prototypesDir = project.resourcesDir?.prototypes
                ?: return Impl(emptyList())

            return reportSequentialProgress { progress ->
                val candidateMappings =
                    progress.indeterminateStep(Bundle.message("prototype.storage.progress.step-title.discovery")) {
                        buildList {
                            PsiManager.getInstance(project).findDirectory(prototypesDir)?.accept(
                                object : YamlPsiElementVisitor() {
                                    override fun visitDirectory(dir: PsiDirectory) {
                                        super.visitDirectory(dir)
                                        dir.acceptChildren(this)
                                    }

                                    override fun visitFile(psiFile: PsiFile) {
                                        super.visitFile(psiFile)
                                        if (psiFile is YAMLFile) {
                                            psiFile.documents.forEach { it.accept(this) }
                                        }
                                    }

                                    override fun visitDocument(document: YAMLDocument) {
                                        super.visitDocument(document)
                                        document.topLevelValue?.accept(this)
                                    }

                                    override fun visitSequence(sequence: YAMLSequence) {
                                        super.visitSequence(sequence)
                                        sequence.items.forEach { it.value?.accept(this) }
                                    }

                                    override fun visitMapping(mapping: YAMLMapping) {
                                        super.visitMapping(mapping)
                                        add(mapping)
                                    }
                                },
                            )
                        }
                    }

                val protos= progress.itemStep(Bundle.message("prototype.storage.progress.step-title.loading-prototypes")) {
                    candidateMappings.mapWithProgress { Prototype.from(it) }
                        .filterNotNull()
                }

                progress.indeterminateStep(Bundle.message("prototype.storage.progress.step-title.assembling-prototypes")) {
                    Impl(protos)
                }
            }
        }
    }

    private class Impl(prototypes: Iterable<Prototype>) : PrototypesStorage {
        private val byId: MultiMap<PrototypeId.Valid, Prototype> =
            MultiMap.create<PrototypeId.Valid, Prototype>().apply {
                prototypes.forEach {
                    if (it.id is PrototypeId.Valid) {
                        putValue(it.id, it)
                    }
                }
            }

        override fun get(id: PrototypeId.Valid): Collection<Prototype> = byId[id].toSet()
    }
}