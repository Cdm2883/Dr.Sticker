package vip.cdms.drsticker.services.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vip.cdms.drsticker.data.SourceStickerResource
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.data.repositories.StatisticRepository
import vip.cdms.drsticker.data.repositories.StickerRepository
import javax.inject.Inject

sealed interface StickerPickerIndexEntry {
    val key: String

    data class StickerSet(val setId: StickerSetId) : StickerPickerIndexEntry {
        override val key = "set:$setId"
    }

    data class Divider(val ordinal: Int) : StickerPickerIndexEntry {
        override val key = "divider:$ordinal"
    }
}

data class StickerPickerItem(
    val setId: StickerSetId,
    val stickerId: StickerId,
    val thumbnail: SourceStickerResource?,
    val resource: SourceStickerResource,
    val tags: List<String>,
)

sealed interface StickerPickerSetState {
    data object Unloaded : StickerPickerSetState
    data object Loading : StickerPickerSetState

    data class Loaded(
        val displayName: String,
        val thumbnail: SourceStickerResource?,
        val stickers: List<StickerPickerItem>,
    ) : StickerPickerSetState
}

data class StickerPickerSheetState(
    val indexEntries: List<StickerPickerIndexEntry> = emptyList(),
    val setStates: Map<StickerSetId, StickerPickerSetState> = emptyMap(),
    /** Number of leading [indexEntries] whose Grid height is known. */
    val stableGridEntryCount: Int = 0,
)

class StickerPickerSheetModel @Inject constructor(
    private val stickerRepository: StickerRepository,
    private val statisticRepository: StatisticRepository,
    private val preferences: StickerPickerSheetPreferences,
) : ViewModel() {
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val loadingJobs = mutableMapOf<StickerSetId, Job>()
    private var indexJob: Job? = null

    private val _state = MutableStateFlow(StickerPickerSheetState())
    val state = _state.asStateFlow()

    private var onStickerSelected: (StickerSetId, StickerId, SourceStickerResource) -> Unit =
        { _, _, _ -> }
    private var onClose: () -> Unit = {}

    fun open(
        onStickerSelected: (StickerSetId, StickerId, SourceStickerResource) -> Unit,
        onClose: () -> Unit,
    ) {
        this.onStickerSelected = onStickerSelected
        this.onClose = onClose
        loadIndex()
    }

    fun requestStickerSets(setIds: Collection<StickerSetId>) {
        setIds.forEach { requestStickerSet(it) }
    }

    /**
     * Loads every sticker set preceding [setId], so its Grid header can be assigned a stable
     * lazy-layout position.
     */
    suspend fun prepareGridThrough(setId: StickerSetId): Boolean {
        val entries = _state.value.indexEntries
        val targetIndex = entries.indexOfFirst {
            it is StickerPickerIndexEntry.StickerSet && it.setId == setId
        }
        if (targetIndex < 0) return false

        entries.take(targetIndex + 1)
            .mapNotNull { (it as? StickerPickerIndexEntry.StickerSet)?.setId }
            .mapNotNull(::requestStickerSet)
            .joinAll()

        val state = _state.value
        return state.stableGridEntryCount > targetIndex &&
                state.setStates[setId] is StickerPickerSetState.Loaded
    }

    fun selectSticker(sticker: StickerPickerItem) =
        onStickerSelected(sticker.setId, sticker.stickerId, sticker.resource)

    fun close() = onClose()

    fun getSavedGridAnchor() = preferences.getGridAnchor()

    fun saveGridAnchor(key: String, setId: StickerSetId, scrollOffset: Int = 0) {
        preferences.setGridAnchor(
            StickerPickerSheetPreferences.GridAnchor(
                key = key,
                setId = setId,
                scrollOffset = scrollOffset,
            )
        )
    }

    private fun loadIndex() {
        indexJob?.cancel()
        loadingJobs.values.forEach(Job::cancel)
        loadingJobs.clear()
        _state.value = StickerPickerSheetState()
        indexJob = viewModelScope.launch {
            val indexes = withContext(loadDispatcher) {
                statisticRepository.getSortedStickerSetIndexes()
            }
            var dividerOrdinal = 0
            val entries = indexes.map { setId ->
                if (setId == null) StickerPickerIndexEntry.Divider(dividerOrdinal++)
                else StickerPickerIndexEntry.StickerSet(setId)
            }
            val setStates = entries.mapNotNull {
                (it as? StickerPickerIndexEntry.StickerSet)?.setId
            }.associateWith { StickerPickerSetState.Unloaded as StickerPickerSetState }
            _state.value = advanceStableGridPrefix(
                StickerPickerSheetState(
                    indexEntries = entries,
                    setStates = setStates,
                )
            )
        }
    }

    private fun requestStickerSet(setId: StickerSetId): Job? {
        loadingJobs[setId]?.let { return it }
        val current = _state.value.setStates[setId]
        if (current == null || current is StickerPickerSetState.Loaded) return null

        _state.update { state ->
            state.copy(setStates = state.setStates + (setId to StickerPickerSetState.Loading))
        }
        return viewModelScope.launch {
            val setState = withContext(loadDispatcher) { loadStickerSet(setId) }
            _state.update { state ->
                advanceStableGridPrefix(
                    state.copy(setStates = state.setStates + (setId to setState))
                )
            }
        }.also { job ->
            loadingJobs[setId] = job
            job.invokeOnCompletion { loadingJobs.remove(setId, job) }
        }
    }

    private fun loadStickerSet(setId: StickerSetId): StickerPickerSetState.Loaded {
        val merged = stickerRepository.getMergedStickerSet(setId)
        val sortedStickers = statisticRepository.getSortedStickers(setId, merged.stickers)
        return StickerPickerSetState.Loaded(
            displayName = merged.displayName,
            thumbnail = merged.thumbnail,
            stickers = sortedStickers.map { sticker ->
                StickerPickerItem(
                    setId = setId,
                    stickerId = sticker.stickerId,
                    thumbnail = sticker.thumbnail,
                    resource = sticker.resource,
                    tags = merged.config.overrides.tags[sticker.stickerId] ?: sticker.tags,
                )
            },
        )
    }

    private fun advanceStableGridPrefix(
        state: StickerPickerSheetState,
    ): StickerPickerSheetState {
        var count = state.stableGridEntryCount
        while (count < state.indexEntries.size) {
            val entry = state.indexEntries[count]
            if (entry is StickerPickerIndexEntry.StickerSet &&
                state.setStates[entry.setId] !is StickerPickerSetState.Loaded
            ) break
            count++
        }
        return if (count == state.stableGridEntryCount) state
        else state.copy(stableGridEntryCount = count)
    }

    override fun onCleared() {
        indexJob?.cancel()
        loadingJobs.values.forEach(Job::cancel)
        loadingJobs.clear()
        onStickerSelected = { _, _, _ -> }
        onClose = {}
    }
}
