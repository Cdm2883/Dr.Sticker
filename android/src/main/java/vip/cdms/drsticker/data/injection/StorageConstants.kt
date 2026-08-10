package vip.cdms.drsticker.data.injection

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.rule.RulesetId
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageConstants @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val rootDir: File = context.getExternalFilesDir(null)
        ?: context.filesDir

    private val cacheDir: File = context.cacheDir

    private fun getStickerSetsDir() = rootDir.resolve("stickers").apply { mkdirs() }

    fun getStickerSetIndexFile() = getStickerSetsDir().resolve("index.json")

    fun getStickerSourceEnvFile() = getStickerSetsDir().resolve("envs.json")

    fun getStickerSetDir(setId: StickerSetId) =
        getStickerSetsDir().resolve(setId).apply { mkdirs() }

    fun getStickerSetConfigFile(setId: StickerSetId) =
        getStickerSetDir(setId).resolve("metadata.json")

    fun getStickerSetSourceCacheFile(setId: StickerSetId) =
        getStickerSetDir(setId).resolve("cache.json")

    fun getStickerCacheFile(setId: StickerSetId, stickerId: StickerId?, extension: String) =
        getStickerSetDir(setId).resolve("${stickerId ?: ""}.$extension")

    fun getStatisticFile() = getStickerSetsDir().resolve("statistic.json")

    fun getSortsFile() = getStickerSetsDir().resolve("sorts.json")

    private fun getRulesetDir() = rootDir.resolve("ruleset").apply { mkdirs() }

    fun getRulesetIndexFile() = getRulesetDir().resolve("index.json")

    fun getRulesetConfigsDir() = getRulesetDir().resolve("configs").apply { mkdirs() }

    fun getRulesetConfigFile(rulesetId: RulesetId) =
        getRulesetConfigsDir().resolve("$rulesetId.json")

    private fun getPreprocessCacheDir() =
        cacheDir.resolve("preprocess").apply { mkdirs() }

    fun getPreprocessCacheFile(hash: String) =
        getPreprocessCacheDir().resolve(hash)

    fun getPreprocessCacheMimeTypeTextFile(hash: String) =
        getPreprocessCacheDir().resolve("$hash.txt")
}
