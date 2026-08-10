package vip.cdms.drsticker.data

import androidx.compose.runtime.Composable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.*
import okio.Sink
import vip.cdms.drsticker.data.repositories.StickerRepository
import javax.inject.Provider
import kotlin.reflect.KClass

typealias StickerId = String

@Serializable
data class SourceStickerSet<T : SourceStickerResource>(
    val setId: StickerSetId,
    val displayName: String,
    val description: String?,
    val thumbnail: T?,
    val stickers: List<SourceSticker<T>>,
)

interface SourceStickerResource {
    val extension: String

    fun getRealExtension() =
        extension.substringAfterLast('.')

    companion object {
        fun thumbnailExtension(extension: String) = "thumbnail.$extension"
    }
}

@Serializable
data class SourceSticker<T : SourceStickerResource>(
    val stickerId: StickerId,
    val tags: List<String>,
    val thumbnail: T?,
    val resource: T,
)

abstract class StickerSourceMetadata<C : StickerSourceConfig>(
    internal val configClass: KClass<out StickerSourceConfig>,
    internal val configSerializer: KSerializer<out StickerSourceConfig>,
    internal val setSerializer: KSerializer<out SourceStickerSet<out SourceStickerResource>>,
) {
    abstract val displayName: String
    abstract val description: String

    @Composable
    abstract fun SourceConfigScope<C>.Settings()
}

interface StickerSourceHandler
<C : StickerSourceConfig, R : SourceStickerResource> {
    suspend fun fetchStickerSet(config: C): SourceStickerSet<R>
}

interface StickerSourceDownloader
<C : StickerSourceConfig, R : SourceStickerResource> {
    suspend fun download(config: C, resource: R, out: Sink)
}

interface StickerSourceConfig

@Serializable
sealed interface SourceEnvConfigField<T> {
    val value: T

    companion object {
        const val ENVIRONMENT_KEY = "@ENVIRONMENT@"
        const val OVERRIDE_KEY = "@OVERRIDE@"
    }

    @Serializable
    @SerialName(ENVIRONMENT_KEY)
    class Environment<T>(override val value: T) : SourceEnvConfigField<T>

    @Serializable
    @SerialName(OVERRIDE_KEY)
    class Override<T>(override val value: T, val environment: T) : SourceEnvConfigField<T>

    fun transform(transform: (T) -> T) = when (this) {
        is Environment<T> -> Environment(transform(value))
        is Override<T> -> Override(transform(value), environment)
    }
}

@OptIn(ExperimentalSerializationApi::class)
class StickerSourceConfigSerializer<T : StickerSourceConfig>(
    private val stickerRepositoryProvider: Provider<StickerRepository>,
    private val sourceKey: String,
    private val delegateSerializer: KSerializer<T>,
    private val namingStrategy: JsonNamingStrategy?,
) : JsonTransformingSerializer<T>(delegateSerializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element

        val result = element.toMutableMap()
        val descriptor = delegateSerializer.descriptor
        for (i in 0 until descriptor.elementsCount) {
            val fieldName = descriptor.getJsonFieldName(i, namingStrategy)
            val fieldDescriptor = descriptor.getElementDescriptor(i)
            if (!fieldDescriptor.isEnvConfigField()) continue
            val envValue = getEnv(fieldName) ?: JsonNull
            result[fieldName] = if (!result.containsKey(fieldName)) buildJsonObject {
                put("type", JsonPrimitive(SourceEnvConfigField.ENVIRONMENT_KEY))
                put("value", envValue)
            } else buildJsonObject {
                put("type", JsonPrimitive(SourceEnvConfigField.OVERRIDE_KEY))
                put("value", result.getValue(fieldName))
                put("environment", envValue)
            }
        }
        return JsonObject(result)
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element

        val result = element.toMutableMap()
        val descriptor = delegateSerializer.descriptor
        for (i in 0 until descriptor.elementsCount) {
            val fieldName = descriptor.getJsonFieldName(i, namingStrategy)
            val fieldDescriptor = descriptor.getElementDescriptor(i)
            if (!fieldDescriptor.isEnvConfigField()) continue

            val value = result[fieldName]
            if (value !is JsonObject) continue
            val type = value["type"]?.jsonPrimitive?.content
            if (type == SourceEnvConfigField.ENVIRONMENT_KEY) result.remove(fieldName)
            else if (type == SourceEnvConfigField.OVERRIDE_KEY) result[fieldName] = value["value"] ?: JsonNull
        }
        return JsonObject(result)
    }

    private fun SerialDescriptor.isEnvConfigField() =
        serialName.contains(SourceEnvConfigField::class.simpleName!!)

    private fun getEnv(jsonFieldName: String) =
        stickerRepositoryProvider.get().getStickerSourceEnv(sourceKey, jsonFieldName)
}

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.getJsonFieldName(
    index: Int,
    namingStrategy: JsonNamingStrategy?
): String {
    val serialName = getElementName(index)
    return namingStrategy
        ?.serialNameForJson(
            descriptor = this,
            elementIndex = index,
            serialName = serialName,
        )
        ?: serialName
}
