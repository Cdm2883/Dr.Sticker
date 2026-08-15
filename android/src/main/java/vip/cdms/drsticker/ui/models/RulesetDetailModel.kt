package vip.cdms.drsticker.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import vip.cdms.drsticker.data.injection.RulesetConfigJson
import vip.cdms.drsticker.data.repositories.RulesetRepository
import vip.cdms.drsticker.rule.*
import vip.cdms.drsticker.rule.adapters.RulesetAdapter
import vip.cdms.drsticker.rule.conditions.AllOf
import vip.cdms.drsticker.rule.conditions.AnyOf
import vip.cdms.drsticker.rule.conditions.Not
import vip.cdms.drsticker.rule.conditions.RulesetCondition
import vip.cdms.drsticker.rule.preprocess.RulesetPreprocess
import vip.cdms.drsticker.rule.triggers.RulesetTrigger
import vip.cdms.drsticker.services.StickerServiceController
import javax.inject.Inject

data class RulesetDetailUiState(
    val rulesetId: RulesetId?,
    val displayName: String? = "",
    val description: String? = "",
    val condition: RulesetCondition? = null,
    val trigger: RulesetTrigger? = null,
    val preprocesses: List<RulesetPreprocess>? = emptyList(),
    val adapter: RulesetAdapter? = null,
    val isDirty: Boolean = false,
    val saveState: RulesetSaveState = RulesetSaveState.Idle,
)

data class RulesetConfigOption(
    val configClass: Class<*>,
    val displayName: String,
    val description: String,
)

sealed interface RulesetSaveState {
    data object Idle : RulesetSaveState
    data object Saving : RulesetSaveState
    data class Saved(val rulesetId: RulesetId) : RulesetSaveState
    data class Failed(val error: Throwable) : RulesetSaveState
}

private data class RulesetDraftSnapshot(
    val displayName: String?,
    val description: String?,
    val conditionJson: String?,
    val triggerJson: String?,
    val preprocessesJson: String?,
    val adapterJson: String?,
)

@HiltViewModel
class RulesetDetailModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:RulesetConfigJson private val json: Json,
    private val rulesetRepository: RulesetRepository,
    private val stickerServiceController: StickerServiceController,
    private val conditions: Map<Class<*>, @JvmSuppressWildcards RulesetConditionMetadata<*>>,
    private val triggers: Map<Class<*>, @JvmSuppressWildcards RulesetTriggerMetadata<*>>,
    private val preprocesses: Map<Class<*>, @JvmSuppressWildcards RulesetPreprocessMetadata<*>>,
    private val adapters: Map<Class<*>, @JvmSuppressWildcards RulesetAdapterMetadata<*>>,
) : ViewModel() {
    val rulesetId: RulesetId? = savedStateHandle["rulesetId"]
    val isAdding get() = rulesetId == null

    private val saveMutex = Mutex()
    private val _state = MutableStateFlow(RulesetDetailUiState(rulesetId = rulesetId))
    val state = _state.asStateFlow()

    private var initialDraft = if (isAdding) _state.value.toDraftSnapshot() else null

    val conditionOptions = conditions
        .map { (configClass, metadata) ->
            RulesetConfigOption(
                configClass = configClass,
                displayName = metadata.displayName,
                description = metadata.description,
            )
        }
        .sortedBy { it.displayName }

    val triggerOptions = triggers
        .map { (configClass, metadata) ->
            RulesetConfigOption(
                configClass = configClass,
                displayName = metadata.displayName,
                description = metadata.description,
            )
        }
        .sortedBy { it.displayName }
    val preprocessOptions = preprocesses
        .map { (configClass, metadata) ->
            RulesetConfigOption(
                configClass = configClass,
                displayName = metadata.displayName,
                description = metadata.description,
            )
        }
        .sortedBy { it.displayName }


    val adapterOptions = adapters
        .map { (configClass, metadata) ->
            RulesetConfigOption(
                configClass = configClass,
                displayName = metadata.displayName,
                description = metadata.description,
            )
        }
        .sortedBy { it.displayName }

    init {
        if (!isAdding) loadRuleset()
    }

    private fun loadRuleset() {
        val ruleset = rulesetRepository
            .getRuleset(requireNotNull(rulesetId))
        val loadedState = RulesetDetailUiState(
            rulesetId = ruleset.rulesetId,
            displayName = ruleset.displayName,
            description = ruleset.description,
            condition = ruleset.condition,
            trigger = ruleset.trigger,
            preprocesses = ruleset.preprocesses.toList(),
            adapter = ruleset.adapter,
        )
        initialDraft = loadedState.toDraftSnapshot()
        _state.value = loadedState
    }

    fun updateDisplayName(displayName: String?) = updateDraft {
        copy(displayName = displayName)
    }

    fun updateDescription(description: String?) = updateDraft {
        copy(description = description)
    }

    @Suppress("UNCHECKED_CAST")
    fun getConditionMetadata(config: RulesetCondition) =
        conditions[config::class.java] as? RulesetConditionMetadata<RulesetCondition>
            ?: error("No condition metadata registered for '${config::class.qualifiedName}'.")

    fun createCondition(configClass: Class<*>): RulesetCondition {
        val metadata = conditions[configClass]
            ?: error("No condition metadata registered for '${configClass.name}'.")
        return metadata.createDefault()
    }

    fun updateCondition(condition: RulesetCondition?) = updateDraft {
        copy(condition = condition)
    }

    // ===

    @Suppress("UNCHECKED_CAST")
    fun getTriggerMetadata(config: RulesetTrigger) =
        triggers[config::class.java] as? RulesetTriggerMetadata<RulesetTrigger>
            ?: error("No trigger metadata registered for '${config::class.qualifiedName}'.")

    fun selectTrigger(configClass: Class<*>) {
        val metadata = triggers[configClass]
            ?: error("No trigger metadata registered for '${configClass.name}'.")
        updateTrigger(metadata.createDefault())
    }

    fun updateTrigger(trigger: RulesetTrigger?) = updateDraft {
        copy(trigger = trigger)
    }

    // ===

    @Suppress("UNCHECKED_CAST")
    fun getPreprocessMetadata(config: RulesetPreprocess) =
        preprocesses[config::class.java] as? RulesetPreprocessMetadata<RulesetPreprocess>
            ?: error("No preprocess metadata registered for '${config::class.qualifiedName}'.")

    fun createPreprocess(configClass: Class<*>): RulesetPreprocess {
        val metadata = preprocesses[configClass]
            ?: error("No preprocess metadata registered for '${configClass.name}'.")
        return metadata.singleton ?: metadata.createDefault()
    }

    fun updatePreprocesses(preprocesses: List<RulesetPreprocess>?) = updateDraft {
        copy(preprocesses = preprocesses?.toList())
    }

    // ===

    @Suppress("UNCHECKED_CAST")
    fun getAdapterMetadata(config: RulesetAdapter) =
        adapters[config::class.java] as? RulesetAdapterMetadata<RulesetAdapter>
            ?: error("No adapter metadata registered for '${config::class.qualifiedName}'.")

    fun selectAdapter(configClass: Class<*>) {
        val metadata = adapters[configClass]
            ?: error("No adapter metadata registered for '${configClass.name}'.")
        updateAdapter(metadata.createDefault())
    }

    fun updateAdapter(adapter: RulesetAdapter?) = updateDraft {
        copy(adapter = adapter)
    }

    // ===

    private inline fun updateDraft(
        transform: RulesetDetailUiState.() -> RulesetDetailUiState,
    ) = _state.update { current ->
        val baseline = initialDraft ?: return@update current
        if (current.saveState is RulesetSaveState.Saving || saveMutex.isLocked) {
            return@update current
        }
        val updated = current.transform().copy(saveState = RulesetSaveState.Idle)
        updated.copy(isDirty = updated.toDraftSnapshot() != baseline)
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        rulesetRepository.deleteRuleset(rulesetId!!)
        stickerServiceController.restartIfRunning()
    }

    suspend fun save() {
        val currentState = _state.value
        checkNotNull(initialDraft) { "Ruleset draft has not been loaded." }
        if (currentState.saveState is RulesetSaveState.Saving || !saveMutex.tryLock()) return

        try {
            val draft = _state.value
            _state.update { it.copy(saveState = RulesetSaveState.Saving) }
            val savedRulesetId = withContext(Dispatchers.IO) {
                persist(draft).also { stickerServiceController.restartIfRunning() }
            }
            _state.update {
                it.copy(saveState = RulesetSaveState.Saved(savedRulesetId))
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                _state.update { it.copy(saveState = RulesetSaveState.Idle) }
                throw error
            }
            _state.update {
                it.copy(saveState = RulesetSaveState.Failed(error))
            }
        } finally {
            saveMutex.unlock()
        }
    }

    private fun persist(draft: RulesetDetailUiState): RulesetId {
        val displayName = requireNotNull(draft.displayName) { "Display name is required." }.trim()
        require(displayName.isNotEmpty()) { "Display name must not be blank." }
        val description = draft.description?.trim()?.takeIf(String::isNotEmpty)
        val condition = requireNotNull(draft.condition) { "Condition is required." }
        require(!condition.containsEmptyGroup()) { "Condition must not contain an empty group." }
        val trigger = requireNotNull(draft.trigger) { "Trigger is required." }
        val preprocesses = requireNotNull(draft.preprocesses) { "Preprocesses must be initialized." }
        val adapter = requireNotNull(draft.adapter) { "Adapter is required." }
        return this.rulesetId?.let { id ->
            rulesetRepository.updateRuleset(
                Ruleset(
                    rulesetId = id,
                    displayName = displayName,
                    description = description,
                    condition = condition,
                    trigger = trigger,
                    preprocesses = preprocesses,
                    adapter = adapter,
                )
            )
            id
        } ?: rulesetRepository.addRuleset(
            displayName = displayName,
            description = description,
            condition = condition,
            trigger = trigger,
            preprocesses = preprocesses,
            adapter = adapter,
        )
    }

    private fun RulesetCondition.containsEmptyGroup(): Boolean = when (this) {
        is AllOf -> children.isEmpty() || children.any { it.containsEmptyGroup() }
        is AnyOf -> children.isEmpty() || children.any { it.containsEmptyGroup() }
        is Not -> child.containsEmptyGroup()
        else -> false
    }

    private fun RulesetDetailUiState.toDraftSnapshot() = RulesetDraftSnapshot(
        displayName = displayName,
        description = description,
        conditionJson = condition?.let { json.encodeToString<RulesetCondition>(it) },
        triggerJson = trigger?.let { json.encodeToString<RulesetTrigger>(it) },
        preprocessesJson = preprocesses?.let { json.encodeToString(it) },
        adapterJson = adapter?.let { json.encodeToString<RulesetAdapter>(it) },
    )
}
