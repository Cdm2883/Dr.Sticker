package vip.cdms.drsticker.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

typealias StickerSetId = String

@Serializable
data class StickerSetOverrides(
    val displayName: String? = null,
    val description: String? = null,
    val tags: Map<StickerId, List<String>> = emptyMap(),
)

@Serializable
data class StickerSetConfig(
    val setId: StickerSetId,
    @Contextual val source: StickerSourceConfig,
    val overrides: StickerSetOverrides,
)

class MergedStickerSet(
    val config: StickerSetConfig,
    val remote: SourceStickerSet<SourceStickerResource>
) {
    val displayName: String
        get() = config.overrides.displayName ?: remote.displayName
    val description: String?
        get() = config.overrides.description ?: remote.description
    val stickers: List<SourceSticker<SourceStickerResource>>
        get() = remote.stickers

    val thumbnail: SourceStickerResource?
        get() = remote.thumbnail
            ?: remote.stickers.firstOrNull()?.let { it.thumbnail ?: it.resource }
}
