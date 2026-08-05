package vip.cdms.drsticker.data.repositories

import kotlinx.serialization.json.Json
import vip.cdms.drsticker.data.*
import vip.cdms.drsticker.data.injection.ConfigJson
import vip.cdms.drsticker.data.injection.StorageManager
import vip.cdms.drsticker.data.utils.BufferedFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class StatisticRepository @Inject constructor(
    private val storageManager: StorageManager,
    @param:ConfigJson private val json: Json,
    private val stickerRepository: StickerRepository,
) {
    private val statistics = BufferedFile(
        file = storageManager.getStatisticFile(),
        defaultValue = ::emptyMap,
        decode = { json.decodeFromString<Statistics>(it) },
        encode = { json.encodeToString(it) },
        writeDelay = 2.seconds,
    )

    fun getSortedStickerSetIndexes(): List<StickerSetId?> {
        val indexes = stickerRepository.getStickerSetIndexes()
        val strategy = getStickerSetSortStrategy()
        if (strategy == SortStrategy.MANUAL) return indexes

        val statistics = statistics.get()
        val result = mutableListOf<StickerSetId?>()
        indexes.splitByNull().forEachIndexed { index, region ->
            if (index > 0) result += null
            result += when (strategy) {
                SortStrategy.RECENCY -> region.sortedByDescending { statistics[it]?.last ?: 0L }
                SortStrategy.FREQUENCY -> region.sortedByDescending { statistics[it]?.counts ?: 0L }
                SortStrategy.SMART -> TODO()
                else -> throw IllegalStateException()
            }
        }
        return result
    }

    fun getSortedStickers(setId: StickerSetId): List<StickerId> {
        val sourceOrder = stickerRepository.getMergedStickerSet(setId).stickers.map { it.stickerId }
        val sorts = getSortsConfig()
        val override = sorts.stickersOverrides[setId]
        val strategy = override?.strategy ?: sorts.stickersStrategy
        if (strategy == SortStrategy.MANUAL) {
            val existing = sourceOrder.toHashSet()
            val manual = override?.manual.orEmpty().filter { it in existing }.distinct()
            return manual + sourceOrder.filterNot { it in manual }
        }
        val statistics = statistics.get()[setId]?.stickers.orEmpty()
        return when (strategy) {
            SortStrategy.RECENCY -> sourceOrder.sortedByDescending { statistics[it]?.last ?: 0L }
            SortStrategy.FREQUENCY -> sourceOrder.sortedByDescending { statistics[it]?.counts ?: 0L }
            SortStrategy.SMART -> TODO()
            else -> throw IllegalStateException()
        }
    }

    fun trackStickerUsage(setId: StickerSetId, stickerId: StickerId) = statistics.update { current ->
        val setStatistic = current[setId] ?: StickerSetStatistic()
        val stickerStatistic = setStatistic.stickers[stickerId] ?: StickerStatistic()
        val now = System.currentTimeMillis()
        current + (setId to setStatistic.copy(
            counts = setStatistic.counts + 1,
            last = now,
            stickers = setStatistic.stickers + (stickerId to stickerStatistic.copy(
                counts = stickerStatistic.counts + 1,
                last = now,
            )),
        ))
    }

    fun setStickerSetSortStrategy(strategy: SortStrategy) =
        updateSortsConfig { it.copy(setsStrategy = strategy) }

    fun getStickerSetSortStrategy() =
        getSortsConfig().setsStrategy

    fun setGlobalStickerSortStrategy(strategy: SortStrategy) =
        updateSortsConfig { it.copy(stickersStrategy = strategy) }

    fun getGlobalStickerSortStrategy() =
        getSortsConfig().stickersStrategy

    fun getStickerSortStrategy(setId: StickerSetId): SortStrategy {
        val sorts = getSortsConfig()
        return sorts.stickersOverrides[setId]?.strategy ?: sorts.stickersStrategy
    }

    fun setStickerSortOverride(setId: StickerSetId, strategy: SortStrategy?) =
        updateStickerOverride(setId) { it.copy(strategy = strategy) }

    fun setManualStickerOrder(setId: StickerSetId, manual: List<StickerId>) =
        updateStickerOverride(setId) { it.copy(manual = manual.distinct()) }

    private fun getSortsConfig(): SortsConfig {
        val file = storageManager.getSortsFile()
        return json.decodeFromString(file.takeIf { it.exists() }?.readText() ?: "{}")
    }

    private fun updateSortsConfig(transform: (SortsConfig) -> SortsConfig) =
        storageManager.getSortsFile().writeText(json.encodeToString(transform(getSortsConfig())))

    private fun updateStickerOverride(
        setId: StickerSetId,
        transform: (StickerSortOverride) -> StickerSortOverride,
    ) = updateSortsConfig { sorts ->
        val current = sorts.stickersOverrides[setId] ?: StickerSortOverride()
        sorts.copy(stickersOverrides = sorts.stickersOverrides + (setId to transform(current)))
    }

    private fun List<StickerSetId?>.splitByNull(): List<List<StickerSetId>> {
        val regions = mutableListOf(mutableListOf<StickerSetId>())
        forEach { setId ->
            if (setId == null) regions += mutableListOf<StickerSetId>()
            else regions.last() += setId
        }
        return regions
    }
}
