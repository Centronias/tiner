package com.centronias.tiner

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ObjectPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import org.intellij.lang.annotations.Language
import org.jetbrains.yaml.psi.*

/** Because I have no idea what simple text is supposed to be represented by. */
typealias YAMLText = YAMLScalar

operator fun YAMLMapping.get(key: String): YAMLValue? = getKeyValueByKey(key)?.value
inline val YAMLSequence.values: Iterable<YAMLValue> get() = this.items.mapNotNull { it.value }

inline fun <T : ObjectPattern<YAMLMapping, T>, reified R : YAMLValue> T.withEntry(
    key: String,
    valuePattern: ElementPattern<R> = PlatformPatterns.not(PlatformPatterns.alwaysFalse()),
): T = with("entries[$key]:${R::class.simpleName}", valuePattern) { getKeyValueByKey(key)?.value as? R }

fun <T : ObjectPattern<YAMLSequence, T>> T.withElements(pattern: ElementPattern<out YAMLValue>): T = with(
    YAMLSequence::getItems,
    collectionPattern(psiElementPattern<YAMLSequenceItem>().with(YAMLSequenceItem::getValue, pattern)),
)

fun yamlTextPattern(regex: Regex, name: String = "matchesR<\"$regex\">") =
    psiElementPattern<YAMLText>(name) { it.textValue.matches(regex) }

sealed interface YamlVersion {
    val boolTruePattern: PsiElementPattern<YAMLText, *>
    val boolFalsePattern: PsiElementPattern<YAMLText, *>
    val boolPattern: PsiElementPattern<YAMLText, *>

    companion object {
        val current: YamlVersion = `1_2`
    }

    private class Impl(
        @Language("RegExp") trueRegexPattern: String,
        @Language("RegExp") falseRegexPattern: String,
    ) : YamlVersion {
        override val boolTruePattern = yamlTextPattern(trueRegexPattern.toRegex())
        override val boolFalsePattern = yamlTextPattern(falseRegexPattern.toRegex())
        override val boolPattern =
            psiElementPattern<YAMLText>().andOr(boolTruePattern, boolFalsePattern)
    }


    @Suppress("ClassName")
    object `1_1` : YamlVersion by Impl(
        """y|Y|yes|Yes|YES|true|True|TRUE|on|On|ON""",
        """n|N|no|No|NO|false|False|FALSE|off|Off|OFF""",
    )

    @Suppress("ClassName")
    object `1_2` : YamlVersion by Impl("""true|True|TRUE""", """false|False|FALSE""")
}