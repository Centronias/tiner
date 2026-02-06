@file:Suppress("UnstableApiUsage")

package com.centronias.tiner.robustyaml

import com.centronias.tiner.*
import com.intellij.find.usages.api.UsageHandler
import com.intellij.ide.presentation.VirtualFilePresentation
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiElementPattern
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.util.mappingNotNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.yaml.psi.*

@ConsistentCopyVisibility
data class Prototype private constructor(
    override val source: YAMLMapping,
) : RobustYamlPsiSourcedSymbol, RobustYamlSymbolWithUsages, RenameTarget, PsiSymbolDeclaration, PrototypeReferenceLike {
    override val containingPrototype: Prototype = this

    val idValue: YAMLValue? = source[Id]
    override val id = PrototypeId.from(idValue)

    private val typeValue: YAMLValue? = source[Type]
    private val type: PrototypeTypeReference? = when (typeValue) {
        is YAMLScalar -> PrototypeTypeReference(typeValue)
        else -> null
    }

    private val parentsValue: YAMLValue? = source[Parents]
    val parents: PrototypeParents? = PrototypeParents.from(parentsValue)

    fun immediateInheritors(): Sequence<Prototype> = usages { it is PrototypeReference }
        .mappingNotNull {
            it as PrototypeReference

            // We may be finding usages outside of the protos directory, so don't assume we can get a prototype.
            val child = it.source.tryFindContainingPrototype() ?: return@mappingNotNull null
            if (!child.type.sameAs(type, nullsAreSame = true)) return@mappingNotNull null
            if (child.parents == null || this !in child.parents) return@mappingNotNull null

            child
        }
        .asSequence()

    /** Returns breadth-first. */
    fun inheritors(): Sequence<Prototype> = sequence {
        val queue = ArrayDeque(listOf(immediateInheritors()))
        while (true) {
            when (val batch = queue.removeFirstOrNull()) {
                null -> return@sequence
                else -> batch.forEach {
                    yield(it)
                    queue.add(it.inheritors())
                }
            }
        }
    }

    override fun toString() = "$id ($type)"
    override val searchText: String
        get() = when (id) {
            is PrototypeId.Valid -> id.text
            else -> ""
        }
    override val project: Project get() = source.project

    override fun createPointer(): Pointer<Prototype> {
        val source = source.createSmartPointer()
        return pointer { Prototype(source.deref()) }
    }

    override fun computePresentation(): TargetPresentation = runBlocking {
        readAction {
            TargetPresentation.builder(this@Prototype.toString())
                .icon(VirtualFilePresentation.getIcon(source.containingFile.virtualFile))
                .containerText(
                    Bundle.message("presentation.container.name.prototypes").takeIf { source.isInPrototypeFile() },
                )
                .locationText(SymbolPresentationUtil.getFilePathPresentation(source.containingFile))
//              .backgroundColor() // TODO I can't figure out how to get the color of the element based on its source.
                .presentation()
        }
    }

    override fun presentation() = computePresentation()
    override fun navigationRequest() = source.navigationRequest()

    override val usageHandler = UsageHandler.createEmptyUsageHandler(targetName)
    override val targetName
        get() = when (id) {
            is PrototypeId.Valid -> id.text
            else -> ""
        }
    override val maximalSearchScope: SearchScope
        get() = GlobalSearchScope.allScope(source.project)

    override fun getDeclaringElement() = source
    override fun getRangeInDeclaringElement(): TextRange = idValue?.textRangeIn(source) ?: TextRange.EMPTY_RANGE
    override fun getSymbol() = this

    companion object {
        fun fromSequenceItemMarkerElement(element: PsiElement): Prototype? {
            val parent = element.parent
            if (!(element.children.isEmpty() && element.text == "-" && parent is YAMLSequenceItem))
                return null

            return parent.value?.let { from(it) }
        }

        fun fromIdElement(element: PsiElement): Prototype? = if (element !is YAMLScalar ||
            !Id.pattern.accepts(element)
        ) {
            null
        } else {
            element.parent?.parent?.let { from(it) }
        }

        fun from(element: PsiElement): Prototype? =
            if (element !is YAMLMapping || !positionInFilePattern.accepts(element)) {
                null
            } else {
                CachedValuesManager.getCachedValue(element) {
                    CachedValueProvider.Result.create(Prototype(element), element)
                }
            }

        val positionInFilePattern: PsiElementPattern.Capture<YAMLMapping> =
            psiElementPattern<YAMLMapping>().withParents(
                YAMLSequenceItem::class.java,
                YAMLSequence::class.java,
                YAMLDocument::class.java,
            )

        val Type = PrototypeField<PrototypeTypeReference>(
            TYPE_KEY,
            yamlTextPattern(csharpIdentifierRegex),
        )
        val Id = PrototypeField<String>(
            ID_KEY,
            yamlTextPattern(prototypeIdentifierRegex),
        )
        val Parents = PrototypeField<PrototypeParents>(
            PARENTS_KEY,
            setOf(PARENT_KEY),
            PrototypeParents.pattern,
        )
    }
}

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
@ConsistentCopyVisibility
data class PrototypeParents private constructor(
    override val source: YAMLValue,
    private val delegate: List<PrototypeReference>,
) : RobustYamlPsiSourcedSymbol, List<PrototypeReference> by delegate {
    private constructor(source: YAMLValue) : this(
        source,
        when (source) {
            is YAMLSequence -> source.values.filterIsInstance<YAMLScalar>().map { PrototypeReference(it) }
            is YAMLScalar -> listOfNotNull(PrototypeReference(source))
            else -> error("Invalid source element for ${PrototypeParents::class.simpleName}: ${source.text}")
        },
    )

    override fun createPointer(): Pointer<out Symbol?> {
        val source = source.createSmartPointer()
        return pointer { PrototypeParents(source.deref()) }
    }

    operator fun contains(ref: PrototypeReferenceLike): Boolean = any { it.id == ref.id }

    companion object {
        fun from(element: YAMLValue?): PrototypeParents? = if (element != null && pattern.accepts(element)) {
            PrototypeParents(element)
        } else {
            null
        }

        val pattern = listOf(
            PrototypeReference.pattern,
            psiElementPattern<YAMLSequence>().withElements(PrototypeReference.pattern),
        ).orred()
    }
}

sealed interface PrototypeId {
    @JvmInline
    value class Valid(val text: String) : PrototypeId {
        override fun toString(): String = text
    }

    data object Unknown : PrototypeId {
        override fun toString(): String = "<unknown>"
    }

    data object NonText : PrototypeId {
        override fun toString(): String = "<non-text>"
    }

    companion object {
        fun from(value: YAMLValue?): PrototypeId {
            return when (value) {
                is YAMLScalar -> Valid(value.textValue)
                null -> Unknown
                else -> NonText
            }
        }
    }
}

data class PrototypeField<T>(
    val mainKey: String,
    val alternateKeys: Set<String>,
    /** Pattern to check the value. [pattern] includes checking the position in the prototype. */
    private val valuePattern: ElementPattern<out YAMLValue>,
) {
    constructor(keyId: String, patternInPrototype: ElementPattern<out YAMLValue>) : this(
        keyId,
        emptySet(),
        patternInPrototype,
    )

    val keys get() = buildList { add(mainKey); addAll(alternateKeys) }

    /** Pattern to check the value and position in a prototype. */
    val pattern: ElementPattern<YAMLValue> =
        psiElementPattern<YAMLValue>()
            .withParent(keys.map { YAMLKeyValue::getKeyText.pattern(pattern(it)) }.orred())
            .and(valuePattern)
}

operator fun YAMLMapping.get(field: PrototypeField<*>) = field.keys.firstNotNullOfOrNull { getKeyValueByKey(it) }?.value

private const val ID_KEY = "id"
private const val TYPE_KEY = "type"

private const val PARENTS_KEY = "parents"
private const val PARENT_KEY = "parent"
private val parentsValuePattern = psiElementPattern<YAMLPsiElement>().withParent(
    psiElementPattern<YAMLKeyValue>().with(
        YAMLKeyValue::getKeyText,
        oneOfPatterns(
            pattern("parent"),
            pattern("parents"),
        ),
    ),
)
