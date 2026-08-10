package vip.cdms.drsticker.data.repositories

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import okio.Sink
import okio.sink
import vip.cdms.drsticker.data.*
import vip.cdms.drsticker.data.injection.StickerConfigJson
import vip.cdms.drsticker.data.injection.StorageConstants
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepository @Inject constructor(
    private val storageConstants: StorageConstants,
    @param:StickerConfigJson private val json: Json,
    private val metadataMap: Map<String, @JvmSuppressWildcards StickerSourceMetadata<*>>,
    private val handlers: Map<String, @JvmSuppressWildcards StickerSourceHandler<*, *>>,
    private val downloaders: Map<Class<*>, @JvmSuppressWildcards StickerSourceDownloader<*, *>>,
) {
    fun getStickerSetIndexes(): List<StickerSetId?> = json.decodeFromString(
        storageConstants.getStickerSetIndexFile().takeIf { it.exists() }?.readText() ?: "[]"
    )

    fun setStickerSetIndexes(data: List<StickerSetId?>) {
        val trimmed = data.dropWhile { it == null }.dropLastWhile { it == null }
        val compacted = trimmed.filterIndexed { index, item -> item != null || trimmed[index - 1] != null }
        val text = json.encodeToString(compacted)
        storageConstants.getStickerSetIndexFile().writeText(text)
    }

    fun getDetachedStickerSets(): List<StickerSetId> {
        val indexed = getStickerSetIndexes().filterNotNull().toHashSet()
        return storageConstants.getStickerSetIndexFile().parentFile
            ?.listFiles { file -> file.isDirectory && file.name !in indexed }
            .orEmpty()
            .sortedByDescending { directory ->
                directory.walkTopDown()
                    .filter(File::isFile)
                    .maxOfOrNull(File::lastModified)
                    ?: 0L
            }
            .map(File::getName)
    }

    fun restoreStickerSet(setId: StickerSetId) {
        val indexes = getStickerSetIndexes()
        if (setId in indexes) return
        getMergedStickerSet(setId)  // check for integrity
        setStickerSetIndexes(listOf(setId) + indexes)
    }

    suspend fun addStickerSet(source: StickerSourceConfig, overrides: StickerSetOverrides): StickerSetId {
        val rawSet = fetchSourceStickerSet(source)
        var setId = rawSet.setId + "@" + getSourceMetadata(source).key
        val indexes = getStickerSetIndexes().toMutableList()
        if (indexes.contains(setId)) setId = setId + "_" + UUID.randomUUID().toString().take(7)
        setStickerSet(StickerSetConfig(setId, source, overrides))
        cacheSourceStickerSet(setId, source, rawSet)
        updateStickerSourceEnv(source)
        indexes.add(0, setId)
        setStickerSetIndexes(indexes)
        return setId
    }

    suspend fun syncStickerSet(setId: StickerSetId) {
        val config = getStickerSetConfig(setId)
        val rawSet = fetchSourceStickerSet(config.source)
        cacheSourceStickerSet(setId, config.source, rawSet)
    }

    @Suppress("UNCHECKED_CAST")
    private fun cacheSourceStickerSet(
        setId: StickerSetId,
        config: StickerSourceConfig,
        data: SourceStickerSet<SourceStickerResource>
    ) {
        val serializer = getSourceMetadata(config).value
            .setSerializer as KSerializer<SourceStickerSet<SourceStickerResource>>
        storageConstants
            .getStickerSetSourceCacheFile(setId)
            .writeText(json.encodeToString(serializer, data))
    }

    @Suppress("UNCHECKED_CAST")
    fun getMergedStickerSet(setId: StickerSetId): MergedStickerSet {
        val config = getStickerSetConfig(setId)
        val serializer = getSourceMetadata(config.source).value
            .setSerializer as KSerializer<SourceStickerSet<SourceStickerResource>>
        val file = storageConstants.getStickerSetSourceCacheFile(setId)
        val remote = json.decodeFromString(serializer, file.readText())
        return MergedStickerSet(config, remote)
    }

    fun getStickerSetConfig(setId: StickerSetId): StickerSetConfig = json.decodeFromString(
        storageConstants.getStickerSetConfigFile(setId).readText()
    )

    fun updateStickerSetOverrides(setId: StickerSetId, overrides: StickerSetOverrides) =
        setStickerSet(getStickerSetConfig(setId).copy(overrides = overrides))

    suspend fun updateStickerSetSource(setId: StickerSetId, source: StickerSourceConfig) {
        val config = getStickerSetConfig(setId)
        require(config.source::class == source::class) {
            "Changing sticker set source type is not supported."
        }

        if (hasSameEffectiveSource(config.source, source)) return
        val rawSet = fetchSourceStickerSet(source)
        setStickerSet(config.copy(source = source))
        cacheSourceStickerSet(setId, source, rawSet)
        updateStickerSourceEnv(source)
    }

    @Suppress("UNCHECKED_CAST")
    private fun hasSameEffectiveSource(
        first: StickerSourceConfig,
        second: StickerSourceConfig,
    ): Boolean {
        val serializer = getSourceMetadata(first).value
            .configSerializer as KSerializer<StickerSourceConfig>

        fun effectiveJson(value: StickerSourceConfig) = JsonObject(
            Json.encodeToJsonElement(serializer, value).jsonObject.mapValues { (_, element) ->
                val field = element as? JsonObject ?: return@mapValues element
                val type = (field["type"] as? JsonPrimitive)?.content
                if (type == SourceEnvConfigField.OVERRIDE_KEY) JsonObject(field - "environment")
                else field
            }
        )
        return effectiveJson(first) == effectiveJson(second)
    }

    private fun setStickerSet(config: StickerSetConfig) = storageConstants
        .getStickerSetConfigFile(config.setId)
        .writeText(json.encodeToString(config))

    fun deleteStickerSet(setId: StickerSetId) {
        setStickerSetIndexes(getStickerSetIndexes() - setId)
        storageConstants.getStickerSetDir(setId).deleteRecursively()
    }

    suspend fun fetchStickerResource(
        setId: StickerSetId,
        stickerId: StickerId?,
        resource: SourceStickerResource,
    ): File {
        val cacheFile = storageConstants.getStickerCacheFile(setId, stickerId, resource.extension)
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile
        val config = getStickerSetConfig(setId).source
        try {
            cacheFile.sink().use { downloadSourceResources(config, resource, it) }
        } catch (e: Exception) {
            if (cacheFile.exists()) cacheFile.delete()
            throw e
        }
        return cacheFile
    }

    internal fun getStickerSourceEnv(sourceKey: String, fieldName: String): JsonElement? {
        val file = storageConstants.getStickerSourceEnvFile().takeIf { it.exists() } ?: return null
        val root = json.decodeFromString<JsonObject>(file.readText())
        return root[sourceKey]?.jsonObject[fieldName]
    }

    /** @param value `null` implies deleting, or pass [kotlinx.serialization.json.JsonNull] explicitly. */
    private fun setStickerSourceEnv(sourceKey: String, fieldName: String, value: JsonElement?) {
        val file = storageConstants.getStickerSourceEnvFile()
        val root = json.decodeFromString<JsonObject>(file.takeIf { it.exists() }?.readText() ?: "{}").toMutableMap()
        val envMap = root[sourceKey]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        if (value == null) envMap.remove(fieldName) else envMap[fieldName] = value
        if (envMap.isEmpty()) root.remove(sourceKey)
        else root[sourceKey] = JsonObject(envMap)
        file.writeText(json.encodeToString(JsonObject(root)))
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun getStickerSourceEnvs(sourceKey: String): SourceEnvValueProvider {
        val file = storageConstants.getStickerSourceEnvFile().takeIf(File::exists) ?: return { null }
        val root = json.decodeFromString<JsonObject>(file.readText())
        val envMap = root[sourceKey]?.jsonObject ?: return { null }
        val descriptor = getSourceMetadata(sourceKey).configSerializer.descriptor
        val valuesByPropertyName = buildMap {
            for (index in 0 until descriptor.elementsCount) {
                val propertyName = descriptor.getElementName(index)
                val jsonFieldName = descriptor.getJsonFieldName(index, json.configuration.namingStrategy)
                envMap[jsonFieldName]?.let { put(propertyName, it) }
            }
        }
        return { property -> valuesByPropertyName[property.name] }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateStickerSourceEnv(source: StickerSourceConfig) {
        val (sourceKey, metadata) = getSourceMetadata(source)
        val serializer = metadata.configSerializer
                as KSerializer<StickerSourceConfig>
        val sourceJson = json
            .encodeToJsonElement(serializer, source)
            .jsonObject
        sourceJson.forEach { (fieldName, element) ->
            val field = element as? JsonObject ?: return@forEach
            val type = field["type"]?.jsonPrimitive?.content
            if (type != SourceEnvConfigField.ENVIRONMENT_KEY)
                return@forEach
            setStickerSourceEnv(
                sourceKey = sourceKey,
                fieldName = fieldName,
                value = field["value"] ?: JsonNull,
            )
        }
    }

    fun getSourceMetadataEntries() = metadataMap.entries

    fun getSourceMetadata(config: StickerSourceConfig) = metadataMap.entries
        .firstOrNull { it.value.configClass == config::class }
        ?: error("No metadata registered for '${config::class.qualifiedName}'.")

    fun getSourceMetadata(sourceKey: String): StickerSourceMetadata<*> =
        metadataMap[sourceKey] ?: error("No metadata registered for '$sourceKey'.")

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchSourceStickerSet(config: StickerSourceConfig) =
        (handlers[getSourceMetadata(config).key] as StickerSourceHandler<StickerSourceConfig, SourceStickerResource>)
            .fetchStickerSet(config)

    @Suppress("UNCHECKED_CAST")
    private suspend fun downloadSourceResources(
        config: StickerSourceConfig, resource: SourceStickerResource, out: Sink
    ) = (downloaders[resource::class.java] as? StickerSourceDownloader
    <StickerSourceConfig, SourceStickerResource>
        ?: error("No downloader registered for '${resource::class.java.simpleName}'."))
        .download(config, resource, out)
}
