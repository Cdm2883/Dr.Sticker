@file:Suppress("PackageDirectoryMismatch")

package vip.cdms.drsticker.rule.adapters

import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.preprocess.StickerFile

@Serializable
sealed interface RulesetAdapter

sealed interface AdapterResult {
    data object Completed : AdapterResult
    data class Failed(val reason: String) : AdapterResult
}

interface AdapterHandler<C : RulesetAdapter> {
    suspend fun send(config: C, sticker: StickerFile): AdapterResult
}
