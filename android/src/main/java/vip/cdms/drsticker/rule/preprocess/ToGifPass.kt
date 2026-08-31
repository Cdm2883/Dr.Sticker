package vip.cdms.drsticker.rule.preprocess

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetPreprocessMetadata
import vip.cdms.drsticker.rule.utils.FFmpegCli
import java.io.File
import javax.inject.Inject

@Serializable
object ToGifPass : RulesetPreprocess

class ToGifPassMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RulesetPreprocessMetadata<ToGifPass> {
    override val displayName get() = "Convert to GIF"  // context.getString(R.string.)
    override val description get() = "Convert the sticker media to GIF."
    override val singleton = ToGifPass
}

class ToGifPassHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PreprocessHandler<ToGifPass> {
    override suspend fun process(config: ToGifPass, input: ProcessingSticker) =
        withContext(Dispatchers.IO) {
            if (input.extension == "gif") return@withContext input
            if (input.extension == "tgs") TODO()

            val filterComplex = "split[s0][s1];" +
                    "[s0]palettegen=stats_mode=single:reserve_transparent=1[p];" +
                    "[s1][p]paletteuse=dither=bayer:bayer_scale=5:alpha_threshold=128"

            val tempIn = createTempFile(input.extension)
            val tempOut = createTempFile("gif")
            try {
                tempIn.writeBytes(input.bytes)
                val code = FFmpegCli.run(
                    "-nostdin",
                    "-y",
                    "-i", tempIn.absolutePath,
                    "-filter_complex", filterComplex,
                    tempOut.absolutePath,
                )
                if (code != 0) error("Ffmpeg exited with $code.")
                ProcessingSticker(tempOut.readBytes(), "gif")
            } finally {
                tempIn.delete()
                tempOut.delete()
            }
        }

    private fun createTempFile(extension: String) =
        File.createTempFile("to_gif_", ".$extension", context.cacheDir)
}

@Module
@InstallIn(SingletonComponent::class)
interface ToGifPassModule {
    @Binds
    @IntoMap
    @ClassKey(ToGifPass::class)
    fun bindMetadata(metadata: ToGifPassMetadata): RulesetPreprocessMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(ToGifPass::class)
    fun bindHandler(handler: ToGifPassHandler): PreprocessHandler<*>
}
