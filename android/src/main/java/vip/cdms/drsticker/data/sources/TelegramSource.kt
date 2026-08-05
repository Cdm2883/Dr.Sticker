package vip.cdms.drsticker.data.sources

import android.content.Context
import androidx.compose.runtime.Composable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.Sink
import vip.cdms.drsticker.data.*
import vip.cdms.drsticker.data.sources.TelegramSourceHandler.RawFile
import vip.cdms.drsticker.data.sources.TelegramSourceHandler.RawResponse
import vip.cdms.drsticker.data.utils.RateLimiter
import vip.cdms.drsticker.data.utils.fetchAsString
import vip.cdms.drsticker.data.utils.fetchToSink
import vip.cdms.drsticker.ui.components.AutoSourceConfigField
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private const val SOURCE_KEY = "telegram"
private val rateLimiter = RateLimiter(limit = 20, interval = 1.seconds)

class TelegramSourceMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StickerSourceMetadata<TelegramSourceConfig>(
    configClass = TelegramSourceConfig::class,
    configSerializer = TelegramSourceConfig.serializer(),
    setSerializer = SourceStickerSet.serializer(TelegramStickerResource.serializer()),
) {
    override val displayName get() = "Telegram"  // context.getString(R.string.)
    override val description get() = "Get telegram sticker set via Bot API"

    @Composable
    override fun SourceConfigScope<TelegramSourceConfig>.Settings() {
        val botTokenState = rememberEnvConfigState(
            property = TelegramSourceConfig::botToken,
            initialValue = "",
            validator = { it.isNotBlank() },
        )

        val nameState = rememberConfigState(
            property = TelegramSourceConfig::name,
            initialValue = "",
            validator = { it.isNotBlank() },
        )

        AutoSourceConfigField(
            state = botTokenState,
            label = "Bot token",
            secret = true,
        )

        AutoSourceConfigField(
            state = nameState,
            label = "Sticker set name",
        )

        RegisterSubmit {
            TelegramSourceConfig(
                botToken = botTokenState.sourceValue
                    .transform { it.trim() },
                name = nameState.value.trim().let { input ->
                    when {
                        "addstickers/" in input -> input  // https://t.me/addstickers/<NAME>
                            .substringAfter("addstickers/")
                            .substringBefore("?")
                            .substringBefore("/")
                        "set=" in input -> input  // tg://addstickers?set=<NAME>
                            .substringAfter("set=")
                            .substringBefore("&")
                            .substringBefore("/")
                        else -> input.removePrefix("@")
                    }
                },
            )
        }
    }
}

@Serializable
@SerialName(SOURCE_KEY)
data class TelegramSourceConfig(
    val botToken: SourceEnvConfigField<String>,
    val name: String,
) : StickerSourceConfig

@Serializable
data class TelegramStickerResource(
    val fileId: String,
    override val extension: String,
) : SourceStickerResource

@Suppress("PropertyName")
class TelegramSourceHandler @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) : StickerSourceHandler<TelegramSourceConfig, TelegramStickerResource> {
    @Serializable
    data class RawResponse<T>(val result: T)

    @Serializable
    data class RawFile(val file_path: String)

    @Serializable
    private data class RawPhotoSize(val file_id: String)

    @Serializable
    private data class RawStickerSet(
        val title: String,
        val thumbnail: RawPhotoSize?,
        val stickers: List<RawSticker>
    )

    @Serializable
    private data class RawSticker(
        val emoji: String?,
        val thumbnail: RawPhotoSize?,
        val file_id: String,
        val is_animated: Boolean,
        val is_video: Boolean,
    ) {
        fun toResource() = TelegramStickerResource(
            fileId = file_id,
            extension = when {
                is_animated -> "tgs"
                is_video -> "webm"
                else -> "webp"
            }
        )
    }

    override suspend fun fetchStickerSet(
        config: TelegramSourceConfig
    ): SourceStickerSet<TelegramStickerResource> = withContext(Dispatchers.IO) {
        rateLimiter.wait()
        val getStickerSetUrl = "https://api.telegram.org/bot${config.botToken.value}/getStickerSet?name=${config.name}"
        val rawStickerSet =
            json.decodeFromString<RawResponse<RawStickerSet>>(httpClient.fetchAsString(getStickerSetUrl)).result

        suspend fun RawPhotoSize.toResource() = TelegramStickerResource(
            fileId = file_id,
            extension = SourceStickerResource
                .thumbnailExtension(fetchFileExtension(config.botToken.value, file_id)),
        )

        val stickers = rawStickerSet.stickers.map {
            async {
                SourceSticker(
                    stickerId = it.file_id,
                    tags = listOfNotNull(it.emoji),
                    thumbnail = it.thumbnail?.toResource(),
                    resource = it.toResource()
                )
            }
        }.awaitAll()
        SourceStickerSet(
            setId = config.name,
            displayName = rawStickerSet.title,
            description = null,
            thumbnail = rawStickerSet.thumbnail?.toResource(),
            stickers = stickers,
        )
    }

    private suspend fun fetchFileExtension(botToken: String, fileId: String): String {
        rateLimiter.wait()
        val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
        val file = json.decodeFromString<RawResponse<RawFile>>(httpClient.fetchAsString(url)).result
        return file.file_path.substringAfterLast('.', "")
    }
}

class TelegramStickerDownloader @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) : StickerSourceDownloader<TelegramSourceConfig, TelegramStickerResource> {
    override suspend fun download(
        config: TelegramSourceConfig,
        resource: TelegramStickerResource,
        out: Sink
    ) = withContext(Dispatchers.IO) {
        val botToken = config.botToken.value
        val getFileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=${resource.fileId}"
        rateLimiter.wait()
        val filePath = json.decodeFromString<RawResponse<RawFile>>(httpClient.fetchAsString(getFileUrl))
            .result.file_path

        val downloadUrl = "https://api.telegram.org/file/bot$botToken/$filePath"
        rateLimiter.wait()
        httpClient.fetchToSink(downloadUrl, out)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface TelegramSourceModule {
    @Binds
    @IntoMap
    @StringKey(SOURCE_KEY)
    fun bindMetadata(factory: TelegramSourceMetadata): StickerSourceMetadata<*>

    @Binds
    @IntoMap
    @StringKey(SOURCE_KEY)
    fun bindHandler(handler: TelegramSourceHandler): StickerSourceHandler<*, *>

    @Binds
    @IntoMap
    @ClassKey(TelegramStickerResource::class)
    fun bindDownloader(downloader: TelegramStickerDownloader): StickerSourceDownloader<*, *>
}
