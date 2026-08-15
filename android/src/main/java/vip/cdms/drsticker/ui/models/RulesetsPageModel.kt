package vip.cdms.drsticker.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vip.cdms.drsticker.data.repositories.RulesetRepository
import vip.cdms.drsticker.rule.RulesetId
import vip.cdms.drsticker.rule.RulesetIndexEntry
import vip.cdms.drsticker.services.StickerServiceController
import javax.inject.Inject

data class RulesetsPageState(
    val isManualSorting: Boolean = false,
    val entries: List<RulesetListEntry> = emptyList(),
)

sealed interface RulesetListEntry {
    val rulesetId: RulesetId
    val isEnabled: Boolean

    data class Ruleset(
        override val rulesetId: RulesetId,
        override val isEnabled: Boolean,
        val displayName: String,
        val description: String?,
    ) : RulesetListEntry

    data class LoadError(
        override val rulesetId: RulesetId,
        override val isEnabled: Boolean,
        val error: Throwable,
    ) : RulesetListEntry
}

@HiltViewModel
class RulesetsPageModel @Inject constructor(
    private val rulesetRepository: RulesetRepository,
    private val stickerServiceController: StickerServiceController,
) : ViewModel() {
    private val repositoryMutex = Mutex()
    private val _state = MutableStateFlow(RulesetsPageState())
    val state = _state.asStateFlow()

    fun beginManualSorting() = _state.update {
        it.copy(isManualSorting = true)
    }

    fun move(fromIndex: Int, toIndex: Int) = _state.update { state ->
        if (!state.isManualSorting || fromIndex !in state.entries.indices ||
            toIndex !in state.entries.indices
        ) return@update state

        state.copy(
            entries = state.entries.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        )
    }

    fun finishManualSorting() = launchRepositoryOperation {
        val state = _state.value
        if (!state.isManualSorting) return@launchRepositoryOperation false
        rulesetRepository.setRulesetIndexes(state.entries.map {
            RulesetIndexEntry(it.rulesetId, it.isEnabled)
        })
        _state.update { it.copy(isManualSorting = false) }
        true
    }

    fun cancelManualSorting() {
        _state.update { it.copy(isManualSorting = false) }
        reloadPage()
    }

    fun setRulesetEnabled(rulesetId: RulesetId, enabled: Boolean) {
        if (_state.value.isManualSorting) return
        launchRepositoryOperation {
            rulesetRepository.setRulesetEnabled(rulesetId, enabled)
            _state.update { state ->
                state.copy(
                    entries = state.entries.map { entry ->
                        if (entry.rulesetId != rulesetId) entry
                        else when (entry) {
                            is RulesetListEntry.Ruleset -> entry.copy(isEnabled = enabled)
                            is RulesetListEntry.LoadError -> entry.copy(isEnabled = enabled)
                        }
                    }
                )
            }
            true
        }
    }

    fun deleteRuleset(rulesetId: RulesetId) {
        if (_state.value.isManualSorting) return
        launchRepositoryOperation {
            rulesetRepository.deleteRuleset(rulesetId)
            _state.update { state ->
                state.copy(entries = state.entries.filterNot { it.rulesetId == rulesetId })
            }
            true
        }
    }

    fun reloadPage() {
        if (_state.value.isManualSorting) return
        launchRepositoryOperation {
            _state.update { it.copy(entries = getPageEntries()) }
            false
        }
    }

    private fun getPageEntries() = rulesetRepository.getRulesetIndexes().map { index ->
        try {
            val ruleset = rulesetRepository.getRuleset(index.rulesetId)
            RulesetListEntry.Ruleset(
                rulesetId = index.rulesetId,
                isEnabled = index.isEnabled,
                displayName = ruleset.displayName,
                description = ruleset.description,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            RulesetListEntry.LoadError(
                rulesetId = index.rulesetId,
                isEnabled = index.isEnabled,
                error = error,
            )
        }
    }

    private fun launchRepositoryOperation(
        operation: suspend () -> Boolean,
    ) = viewModelScope.launch {
        repositoryMutex.withLock {
            withContext(Dispatchers.IO) {
                if (operation()) stickerServiceController.restartIfRunning()
            }
        }
    }
}
