package com.centronias.tiner.robustyaml

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.RobustYaml"

object Bundle : DynamicBundle(BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)

    @Suppress("unused")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getLazyMessage(key, *params)
}

//todo
//   FUNCTIONALLY:
//     Move entire file to $fork
//       /repos/ss14/Resources/Subresource/path/to/file/filename.ext
//       should become
//       /repos/ss14/Resources/Subresource/$fork/path/to/file/filename.ext
//         Needs special casing for localization strings since their subresource is different
//     Move selection to _moffstation
//       same as above, but instead of moving the whole file content, just a selection is moved
//todo
//  CONCRETE:
//    - actions for ( https://github.com/JetBrains/intellij-sdk-code-samples/tree/main/action_basics )
//      - move file
//      - move selection
//    - intentions for ( https://github.com/JetBrains/intellij-sdk-code-samples/tree/main/conditional_operator_intention )
//      - move file
//      - move selection
//todo
//  IMPL:
//  - Basically all of these will just invoke the same function which takes some text and puts it into a new file :shrug: