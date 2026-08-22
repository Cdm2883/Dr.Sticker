package vip.cdms.drsticker.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vip.cdms.drsticker.data.*
import vip.cdms.drsticker.data.repositories.RulesetRepository
import vip.cdms.drsticker.data.repositories.StatisticRepository
import vip.cdms.drsticker.data.repositories.StickerRepository
import vip.cdms.drsticker.data.utils.ProgressableController
import javax.inject.Inject

data class StickersPageState(
    val sortStrategy: SortStrategy = SortStrategy.MANUAL,
    val isManualSorting: Boolean = false,
    val entries: List<StickerSetListEntry> = emptyList(),
    val pickerState: StickerSetPickerState = StickerSetPickerState.Closed,
    val configState: StickerSetConfigState = StickerSetConfigState.Closed(),
)

sealed interface StickerSetPickerState {
    data object Closed : StickerSetPickerState

    data class Open(
        val sourceOptions: List<StickerSourceOption>,
        val detachedEntries: List<StickerSetListEntry>,
    ) : StickerSetPickerState
}

data class StickerSourceOption(
    val key: String,
    val displayName: String,
    val description: String,
)

sealed interface StickerSetConfigSaveState {
    data object Idle : StickerSetConfigSaveState
    data object Saving : StickerSetConfigSaveState
    data class Error(val error: Throwable) : StickerSetConfigSaveState
}

sealed interface StickerSetConfigState {
    data class Closed(
        val scrollToSetId: StickerSetId? = null,
    ) : StickerSetConfigState

    data class Add(
        val sourceKey: String,
        val envProvider: SourceEnvValueProvider,
        val saveState: StickerSetConfigSaveState = StickerSetConfigSaveState.Idle,
    ) : StickerSetConfigState

    data class Edit(
        val setId: StickerSetId,
        val sourceKey: String,
        val source: StickerSourceConfig,
        val overrides: StickerSetOverrides,
        val previousDisplayName: String,
        val previousDescription: String?,
        val saveState: StickerSetConfigSaveState = StickerSetConfigSaveState.Idle,
    ) : StickerSetConfigState
}

sealed interface StickerSetListEntry {
    val key: String

    data class StickerSet(
        val setId: StickerSetId,
        val thumbnail: SourceStickerResource?,
        val displayName: String,
        val description: String?,
        val sourceName: String,
        val stickerCount: Int,
    ) : StickerSetListEntry {
        override val key = setId
    }

    data class LoadError(
        val setId: StickerSetId,
        val error: Throwable,
    ) : StickerSetListEntry {
        override val key = setId
    }

    data class Divider(
        override val key: String,
    ) : StickerSetListEntry
}

@HiltViewModel
class StickerSetsPageModel @Inject constructor(
    private val stickerRepository: StickerRepository,
    private val rulesetRepository: RulesetRepository,
    private val statisticRepository: StatisticRepository,
    private val progressableController: ProgressableController,
) : ViewModel() {
    private val repositoryMutex = Mutex()
    private var dividerKey = 0
    private val _state = MutableStateFlow(StickersPageState())
    val state = _state.asStateFlow()
    val progressState = progressableController.state

    init {
        reloadPage()
    }

    fun selectSortStrategy(strategy: SortStrategy) = launchRepositoryOperation {
        statisticRepository.setStickerSetSortStrategy(strategy)
        _state.update {
            it.copy(
                sortStrategy = strategy,
                isManualSorting = false,
                entries = getPageEntries(),
            )
        }
    }

    fun beginManualSorting() = _state.update {
        if (it.sortStrategy == SortStrategy.MANUAL) it.copy(isManualSorting = true) else it
    }

    fun move(fromIndex: Int, toIndex: Int) = _state.update { state ->
        if (!state.isManualSorting || fromIndex !in state.entries.indices || toIndex !in state.entries.indices)
            return@update state
        val entries = state.entries.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        if (entries.hasValidDividers()) state.copy(entries = entries) else state
    }

    fun finishManualSorting() = launchRepositoryOperation {
        if (!_state.value.isManualSorting) return@launchRepositoryOperation
        stickerRepository.setStickerSetIndexes(_state.value.entries.toIndexes())
        _state.update { it.copy(isManualSorting = false) }
    }

    fun cancelManualSorting() {
        _state.update { it.copy(isManualSorting = false) }
        reloadPage()
    }

    fun insertDivider(index: Int) {
        val state = _state.value
        if (index !in 1 until state.entries.size ||
            state.entries[index - 1] is StickerSetListEntry.Divider ||
            state.entries[index] is StickerSetListEntry.Divider
        ) return

        _state.update {
            it.copy(entries = it.entries.toMutableList().apply {
                add(index, StickerSetListEntry.Divider("new-divider:${dividerKey++}"))
            })
        }
        if (!state.isManualSorting) launchRepositoryOperation {
            stickerRepository.setStickerSetIndexes(_state.value.entries.toIndexes())
        }
    }

    fun remove(index: Int) {
        val state = _state.value
        val entry = state.entries.getOrNull(index) ?: return
        val dividerOrdinal = state.entries.take(index).count { it is StickerSetListEntry.Divider }
        _state.update { it.copy(entries = it.entries.toMutableList().apply { removeAt(index) }) }

        launchRepositoryOperation {
            val indexes = stickerRepository.getStickerSetIndexes().toMutableList()
            when (entry) {
                is StickerSetListEntry.StickerSet -> indexes.remove(entry.setId)
                is StickerSetListEntry.LoadError -> indexes.remove(entry.setId)
                is StickerSetListEntry.Divider -> {
                    var remaining = dividerOrdinal
                    val rawIndex = indexes.indexOfFirst {
                        it == null && remaining-- == 0
                    }
                    if (rawIndex >= 0) indexes.removeAt(rawIndex)
                }
            }
            stickerRepository.setStickerSetIndexes(indexes)
            if (!_state.value.isManualSorting)
                _state.update { it.copy(entries = getPageEntries()) }
        }
    }

    fun reloadPage() = launchRepositoryOperation {
        val sortStrategy = statisticRepository.getStickerSetSortStrategy()
        val entries = getPageEntries()
        _state.update {
            it.copy(
                sortStrategy = sortStrategy,
                entries = entries,
            )
        }
    }

    // === PickStickerSetDialog ===

    fun openStickerSetPicker() = launchRepositoryOperation {
        val sourceOptions = stickerRepository.getSourceMetadataEntries()
            .sortedBy { it.key }
            .map { (key, metadata) ->
                StickerSourceOption(
                    key = key,
                    displayName = metadata.displayName,
                    description = metadata.description,
                )
            }
        val detachedEntries = stickerRepository.getDetachedStickerSets()
            .map(::getStickerSetEntry)
        _state.update {
            it.copy(
                pickerState = StickerSetPickerState.Open(sourceOptions, detachedEntries),
            )
        }
    }

    fun closeStickerSetPicker() = _state.update {
        it.copy(pickerState = StickerSetPickerState.Closed)
    }

    fun getSourceMetadata(sourceKey: String) =
        stickerRepository.getSourceMetadata(sourceKey)

    fun restoreStickerSet(setId: StickerSetId) = launchRepositoryOperation {
        stickerRepository.restoreStickerSet(setId)
        _state.update { state ->
            val pickerState = state.pickerState
            state.copy(
                entries = getPageEntries(),
                pickerState = if (pickerState is StickerSetPickerState.Open) {
                    pickerState.copy(
                        detachedEntries = stickerRepository.getDetachedStickerSets()
                            .map(::getStickerSetEntry)
                    )
                } else {
                    pickerState
                },
            )
        }
    }

    fun deleteDetachedStickerSet(setId: StickerSetId) = launchRepositoryOperation {
        stickerRepository.deleteStickerSet(setId)
        _state.update { state ->
            val pickerState = state.pickerState
            if (pickerState !is StickerSetPickerState.Open) return@update state
            state.copy(
                pickerState = pickerState.copy(
                    detachedEntries = pickerState.detachedEntries.filterNot {
                        it.key == setId
                    },
                ),
            )
        }
    }

    // === StickerSetSourceConfigDialog ===

    fun openAddStickerSetConfig(sourceKey: String) = launchRepositoryOperation {
        val envProvider =
            stickerRepository.getStickerSourceEnvs(sourceKey)
        _state.update {
            it.copy(configState = StickerSetConfigState.Add(sourceKey, envProvider))
        }
    }

    fun openEditStickerSetConfig(setId: StickerSetId) = launchRepositoryOperation {
        val stickerSet = stickerRepository.getMergedStickerSet(setId)
        val metadata = stickerRepository.getSourceMetadata(stickerSet.config.source)
        _state.update {
            it.copy(
                configState = StickerSetConfigState.Edit(
                    setId = setId,
                    sourceKey = metadata.key,
                    source = stickerSet.config.source,
                    overrides = stickerSet.config.overrides,
                    previousDisplayName = stickerSet.displayName,
                    previousDescription = stickerSet.description,
                ),
            )
        }
    }

    fun closeStickerSetConfig() = _state.update {
        check(it.configState.saveStateOrNull() !is StickerSetConfigSaveState.Saving) {
            "Cannot close sticker set config while saving."
        }
        it.copy(configState = StickerSetConfigState.Closed())
    }

    fun consumeScrollToStickerSet() = _state.update { state ->
        if (state.configState is StickerSetConfigState.Closed &&
            state.configState.scrollToSetId != null
        ) {
            state.copy(configState = StickerSetConfigState.Closed())
        } else {
            state
        }
    }

    fun addStickerSet(
        source: StickerSourceConfig,
        overrides: StickerSetOverrides,
        preDownload: Boolean,
        prePreprocess: Boolean,
    ) = saveStickerSetConfig(preDownload, prePreprocess) {
        stickerRepository.addStickerSet(source, overrides)
    }

    fun updateStickerSet(
        setId: StickerSetId,
        source: StickerSourceConfig,
        overrides: StickerSetOverrides,
        preDownload: Boolean,
        prePreprocess: Boolean,
    ) = saveStickerSetConfig(preDownload, prePreprocess) {
        stickerRepository.updateStickerSetSource(setId, source)
        stickerRepository.updateStickerSetOverrides(setId, overrides)
        setId
    }

    private fun saveStickerSetConfig(
        preDownload: Boolean,
        prePreprocess: Boolean,
        operation: suspend () -> StickerSetId,
    ) {
        val target = _state.value.configState
        check(target is StickerSetConfigState.Add || target is StickerSetConfigState.Edit) {
            "Sticker set config is not open."
        }
        check(target.saveStateOrNull() !is StickerSetConfigSaveState.Saving) {
            "Sticker set config is already saving."
        }
        progressableController.reset()
        _state.update { it.copy(configState = target.withSaveState(StickerSetConfigSaveState.Saving)) }
        launchRepositoryOperation {
            try {
                progressableController.collect {
                    val setId = operation()
                    if (preDownload)
                        stickerRepository.preDownloadStickerSetStickers(setId)
                    if (prePreprocess)
                        rulesetRepository.prePreprocessStickerSetStickers(setId)
                    val entries = getPageEntries()
                    _state.update {
                        it.copy(
                            entries = entries,
                            configState = StickerSetConfigState.Closed(
                                scrollToSetId = if (target is StickerSetConfigState.Add) setId else null,
                            ),
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(
                        configState = target.withSaveState(
                            StickerSetConfigSaveState.Error(error)
                        )
                    )
                }
            }
        }
    }

    private fun StickerSetConfigState.saveStateOrNull() = when (this) {
        is StickerSetConfigState.Add -> saveState
        is StickerSetConfigState.Edit -> saveState
        is StickerSetConfigState.Closed -> null
    }

    private fun StickerSetConfigState.withSaveState(saveState: StickerSetConfigSaveState) = when (this) {
        is StickerSetConfigState.Add -> copy(saveState = saveState)
        is StickerSetConfigState.Edit -> copy(saveState = saveState)
        is StickerSetConfigState.Closed -> error("Sticker set config is not open.")
    }

    private fun getPageEntries() =
        statisticRepository.getSortedStickerSetIndexes().mapIndexed { index, setId ->
            if (setId == null) {
                StickerSetListEntry.Divider("divider:$index")
            } else {
                getStickerSetEntry(setId)
            }
        }

    private fun getStickerSetEntry(setId: StickerSetId): StickerSetListEntry {
        return try {
            val stickerSet = stickerRepository.getMergedStickerSet(setId)
            val sourceMeta = stickerRepository.getSourceMetadata(stickerSet.config.source).value
            StickerSetListEntry.StickerSet(
                setId = setId,
                thumbnail = stickerSet.thumbnail,
                displayName = stickerSet.displayName,
                description = stickerSet.description,
                sourceName = sourceMeta.displayName,
                stickerCount = stickerSet.stickers.count(),
            )
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            StickerSetListEntry.LoadError(setId, error)
        }
    }

    private fun launchRepositoryOperation(operation: suspend () -> Unit) = viewModelScope.launch {
        repositoryMutex.withLock {
            withContext(Dispatchers.IO) { operation() }
        }
    }

    private fun List<StickerSetListEntry>.toIndexes() = map {
        when (it) {
            is StickerSetListEntry.StickerSet -> it.setId
            is StickerSetListEntry.LoadError -> it.setId
            is StickerSetListEntry.Divider -> null
        }
    }

    private fun List<StickerSetListEntry>.hasValidDividers() =
        firstOrNull() !is StickerSetListEntry.Divider &&
                lastOrNull() !is StickerSetListEntry.Divider &&
                zipWithNext().none { (first, second) ->
                    first is StickerSetListEntry.Divider && second is StickerSetListEntry.Divider
                }
}
