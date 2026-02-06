@file:Suppress("UnstableApiUsage")

package com.centronias.tiner.robustyaml

import com.centronias.tiner.textRangeIn
import com.intellij.codeInsight.daemon.*
import com.intellij.codeInsight.generation.actions.PresentableCodeInsightActionHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.list.buildTargetPopupWithMultiSelect
import com.intellij.util.Function
import org.jetbrains.yaml.psi.YAMLFile

private typealias LineMarkerInfos = MutableCollection<in LineMarkerInfo<*>>

private data object PrototypeInheritors : GutterIconDescriptor.Option(
    "robust-yaml.prototype-inheritors",
    Bundle.message("gutter-icons.name.prototype-inheritors"),
    AllIcons.Gutter.ImplementedMethod,
)

class RobustYamlLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): @GutterName String = Bundle.message("gutter-icons.name")
    override fun getOptions(): Array<Option> = arrayOf(PrototypeInheritors)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        if (elements.isEmpty()) return
        if (DumbService.getInstance(elements.first().project).isDumb) return
        val file = elements.first().containingFile as? YAMLFile ?: return
        if (!file.isInPrototypes) return

        elements.forEach { element ->
            Prototype.fromSequenceItemMarkerElement(element)?.collectMarkers(element, result)
        }
    }

    private fun Prototype.collectMarkers(element: PsiElement, result: LineMarkerInfos) {
        if (!PrototypeInheritors.isEnabled) return
        if (inheritors().none()) return

        result.add(
            Lmi(
                element,
                PrototypeInheritors,
                { Bundle.message("gutter-icons.tooltip.prototype-inheritors") },
                { e, elt ->
                    val proto = Prototype.fromSequenceItemMarkerElement(elt) ?: return@Lmi
                    PrototypeNavigation.goToInheritors(proto)?.show(RelativePoint(e))
                },
            ),
        )
    }
}

private class Lmi(
    element: PsiElement,
    option: GutterIconDescriptor.Option,
    tooltipProvider: Function<in PsiElement, @NlsContexts.Tooltip String>,
    navHandler: GutterIconNavigationHandler<PsiElement>,
) : LineMarkerInfo<PsiElement>(
    element,
    element.textRangeIn(element.containingFile),
    requireNotNull(option.icon) { "Missing icon on $option" },
    tooltipProvider,
    navHandler,
    GutterIconRenderer.Alignment.RIGHT,
    { option.name },
)

object PrototypeNavigation {
    private const val MAXIMUM_POPUP_ENTRIES = 100

    fun goToInheritors(proto: Prototype): JBPopup? {
        val inheritors = proto.inheritors().take(MAXIMUM_POPUP_ENTRIES).toList()
        return when (val single = inheritors.singleOrNull()) {
            null -> buildTargetPopupWithMultiSelect(
                inheritors,
                Prototype::presentation,
                { true },
            ).setTitle(
                Bundle.message(
                    "navigation.chooser.title.prototype-inheritor",
                    proto.toString(),
                    if (inheritors.size >= MAXIMUM_POPUP_ENTRIES) "${inheritors.size}+" else inheritors.size,
                ),
            ).setItemChosenCallback { goTo(it) }
                .createPopup()

            else -> goTo(single)
        }
    }

    fun goTo(proto: Prototype): Nothing? {
        proto.source.takeIf { it.canNavigate() }?.navigate(true)
        return null
    }
}

class PrototypeGoToInheritorsHandler : PresentableCodeInsightActionHandler {
    companion object {
    }

    override fun update(
        editor: Editor,
        file: PsiFile,
        presentation: Presentation?,
    ) {
        TODO("Not yet implemented")
    }

    override fun invoke(
        project: Project,
        editor: Editor,
        psiFile: PsiFile,
    ) {
        TODO("Not yet implemented")
    }

    override fun startInWriteAction() = false
}