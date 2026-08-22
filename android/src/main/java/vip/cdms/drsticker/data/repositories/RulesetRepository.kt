package vip.cdms.drsticker.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.data.injection.RulesetConfigJson
import vip.cdms.drsticker.data.injection.StorageConstants
import vip.cdms.drsticker.data.utils.progressable
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
    private val stickerRepository: StickerRepository,
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

    fun getEnabledRulesets() = getRulesetIndexes()
        .asSequence()
        .filter { it.isEnabled }
        .map { getRuleset(it.rulesetId) }
        .toList()

    suspend fun prePreprocessStickerSetStickers(setId: StickerSetId) {
        val rulesets = getEnabledRulesets()
            .filter { it.preprocesses.isNotEmpty() }
        if (rulesets.isEmpty()) return

        val stickers = stickerRepository.getMergedStickerSet(setId).stickers
        if (stickers.isEmpty()) return

        progressable(total = stickers.size * (rulesets.size + 1)) {
            for (sticker in stickers) {
                val stickerName = sticker.tags.firstOrNull() ?: sticker.stickerId
                val original = progressable {
                    stickerRepository.fetchStickerResource(
                        setId = setId,
                        stickerId = sticker.stickerId,
                        resource = sticker.resource,
                    )
                }
                for (ruleset in rulesets) progressable {
                    report(null, label = "Processing $stickerName")
                    preprocessSticker(
                        setId = setId,
                        stickerId = sticker.stickerId,
                        ruleset = ruleset,
                        original = original,
                        originalExtension = sticker.resource.getRealExtension(),
                    )
                }
            }
        }
    }

    suspend fun preprocessSticker(
        setId: StickerSetId,
        stickerId: StickerId,
        ruleset: Ruleset,
        original: File,
        originalExtension: String,
    ): File = progressable {
        report(null, label = "Preprocessing ${ruleset.displayName}")
        val preprocesses = ruleset.preprocesses
        if (preprocesses.isEmpty()) return@progressable original

        val cacheKey = PreprocessCacheKey(setId, stickerId, preprocesses)
        withContext(Dispatchers.IO) {
            getPreprocessCache(cacheKey)
        }?.let { return@progressable it }

        val initial = ProcessingSticker(original.readBytes(), originalExtension)
        var processed = initial
        for ((index, config) in preprocesses.withIndex()) {
            val output = getPreprocessHandler(config)
                .process(config, processed)
            if (output == null) {
                if (processed === initial) return@progressable original
                break
            }
            processed = output
            report((index + 1f) / preprocesses.size)
        }
        withContext(Dispatchers.IO) {
            updatePreprocessCache(cacheKey, processed)
        }
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
