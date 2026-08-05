package vip.cdms.drsticker.data.injection

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val rootDir: File = context.getExternalFilesDir(null)
        ?: context.filesDir

    private val stickerSetsDir = rootDir.resolve("stickers").apply { mkdirs() }

    fun getStickerSetIndexFile() = stickerSetsDir.resolve("index.json")

    fun getStickerSourceEnvFile() = stickerSetsDir.resolve("envs.json")

    fun getStickerSetDir(setId: StickerSetId) =
        stickerSetsDir.resolve(setId).apply { mkdirs() }

    fun getStickerSetConfigFile(setId: StickerSetId) =
        getStickerSetDir(setId).resolve("metadata.json")

    fun getStickerSetSourceCacheFile(setId: StickerSetId) =
        getStickerSetDir(setId).resolve("cache.json")

    fun getStickerCacheFile(setId: StickerSetId, stickerId: StickerId?, extension: String) =
        getStickerSetDir(setId).resolve("${stickerId ?: ""}.$extension")

    fun getStatisticFile() =
        stickerSetsDir.resolve("statistic.json")

    fun getSortsFile() =
        stickerSetsDir.resolve("sorts.json")

    private val rulesetsDir = rootDir.resolve("rulesets").apply { mkdirs() }
}
