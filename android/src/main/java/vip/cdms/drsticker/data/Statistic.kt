package vip.cdms.drsticker.data

import kotlinx.serialization.Serializable

typealias Statistics = Map<StickerSetId, StickerSetStatistic>

@Serializable
data class StickerSetStatistic(
    val counts: Long = 0,
    val last: Long = 0,
    val stickers: Map<StickerId, StickerStatistic> = emptyMap(),
)

@Serializable
data class StickerStatistic(
    val counts: Long = 0,
    val last: Long = 0,
)


@Serializable
data class SortsConfig(
    val setsStrategy: SortStrategy = SortStrategy.MANUAL,
    val stickersStrategy: SortStrategy = SortStrategy.MANUAL,
    val stickersOverrides: Map<StickerSetId, StickerSortOverride> = emptyMap(),
)

@Serializable
data class StickerSortOverride(
    /** `null` inherits [SortsConfig.stickersStrategy]. */
    val strategy: SortStrategy? = null,
    val manual: List<StickerId> = emptyList(),
)

@Serializable
enum class SortStrategy {
    MANUAL,
    SMART,
    RECENCY,
    FREQUENCY,
}
