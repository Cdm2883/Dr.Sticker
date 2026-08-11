package vip.cdms.drsticker.data.repositories

import kotlinx.serialization.json.Json
import vip.cdms.drsticker.data.injection.RulesetConfigJson
import vip.cdms.drsticker.data.injection.StorageConstants
import vip.cdms.drsticker.rule.Ruleset
import vip.cdms.drsticker.rule.RulesetId
import vip.cdms.drsticker.rule.RulesetIndexEntry
import vip.cdms.drsticker.rule.adapters.AdapterHandler
import vip.cdms.drsticker.rule.adapters.RulesetAdapter
import vip.cdms.drsticker.rule.conditions.RulesetCondition
import vip.cdms.drsticker.rule.preprocess.*
import vip.cdms.drsticker.rule.triggers.RulesetTrigger
import vip.cdms.drsticker.rule.triggers.TriggerHandler
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RulesetRepository @Inject constructor(
    private val storageConstants: StorageConstants,
    @param:RulesetConfigJson private val json: Json,
    private val triggerHandlers: Map<Class<*>, @JvmSuppressWildcards TriggerHandler<*>>,
    private val preprocessHandlers: Map<Class<*>, @JvmSuppressWildcards PreprocessHandler<*>>,
    private val adapterHandlers: Map<Class<*>, @JvmSuppressWildcards AdapterHandler<*>>,
) {
    fun getRulesetIndexes(): List<RulesetIndexEntry> = json.decodeFromString(
        storageConstants.getRulesetIndexFile().takeIf { it.exists() }?.readText() ?: "[]"
    )

    fun setRulesetIndexes(indexes: List<RulesetIndexEntry>) = storageConstants
        .getRulesetIndexFile()
        .writeText(json.encodeToString(indexes))

    fun setRulesetEnabled(rulesetId: RulesetId, enabled: Boolean) {
        val indexes = getRulesetIndexes().toMutableList()
        val index = indexes.indexOfFirst { it.rulesetId == rulesetId }
        require(index != -1)
        if (indexes[index].isEnabled == enabled) return
        indexes[index] = indexes[index].copy(isEnabled = enabled)
        setRulesetIndexes(indexes)
    }

    fun addRuleset(
        displayName: String,
        description: String?,
        condition: RulesetCondition,
        trigger: RulesetTrigger,
        preprocesses: List<RulesetPreprocess>,
        adapter: RulesetAdapter
    ): RulesetId {
        val rulesetId = UUID.randomUUID().toString()
        val ruleset = Ruleset(
            rulesetId, displayName, description,
            condition, trigger, preprocesses, adapter
        )
        val entry = RulesetIndexEntry(rulesetId, isEnabled = true)
        updateRuleset(ruleset)
        setRulesetIndexes(listOf(entry) + getRulesetIndexes())
        return rulesetId
    }

    fun updateRuleset(ruleset: Ruleset) = storageConstants
        .getRulesetConfigFile(ruleset.rulesetId)
        .writeText(json.encodeToString(ruleset))

    fun getRuleset(rulesetId: RulesetId): Ruleset = json.decodeFromString(
        storageConstants.getRulesetConfigFile(rulesetId).readText()
    )

    fun deleteRuleset(rulesetId: RulesetId) {
        setRulesetIndexes(getRulesetIndexes().filterNot { it.rulesetId == rulesetId })
        storageConstants.getRulesetConfigFile(rulesetId).delete()
    }

    fun getPreprocessCache(key: PreprocessCacheKey): File? {
        val hash = key.toHash()
        val extension = storageConstants.getPreprocessCacheExtensionFile(hash)
            .takeIf { it.isFile }
            ?.readText() ?: return null
        return storageConstants.getPreprocessCacheFile(hash, extension)
            .takeIf { it.isFile && it.length() > 0 }
    }

    fun updatePreprocessCache(key: PreprocessCacheKey, sticker: ProcessingSticker): File {
        val hash = key.toHash()
        val cacheFile = storageConstants.getPreprocessCacheFile(hash, sticker.extension)
        val extensionFile = storageConstants.getPreprocessCacheExtensionFile(hash)
        extensionFile.delete()
        cacheFile.delete()
        cacheFile.writeBytes(sticker.bytes)
        extensionFile.writeText(sticker.extension)
        return cacheFile
    }

    @Suppress("UNCHECKED_CAST")
    fun <C : RulesetTrigger> getTriggerHandler(config: C) =
        triggerHandlers[config::class.java] as? TriggerHandler<C>
            ?: error("No trigger handler registered for '${config::class.qualifiedName}'.")

    @Suppress("UNCHECKED_CAST")
    fun <C : RulesetPreprocess> getPreprocessHandler(config: C) =
        preprocessHandlers[config::class.java] as? PreprocessHandler<C>
            ?: error("No preprocess handler registered for '${config::class.qualifiedName}'.")

    @Suppress("UNCHECKED_CAST")
    fun <C : RulesetAdapter> getAdapterHandler(config: C) =
        adapterHandlers[config::class.java] as? AdapterHandler<C>
            ?: error("No adapter handler registered for '${config::class.qualifiedName}'.")
}
