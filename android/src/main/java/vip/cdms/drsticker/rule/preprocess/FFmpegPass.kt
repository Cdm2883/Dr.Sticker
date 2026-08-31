package vip.cdms.drsticker.rule.preprocess

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
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
data class FFmpegPass(
    val args: List<String> = listOf(
        "-i", PLACEHOLDER_INPUT,
        "$PLACEHOLDER_OUTPUT.$PLACEHOLDER_EXTENSION"
    ),
) : RulesetPreprocess {
    companion object {
        const val PLACEHOLDER_INPUT = $$"$INPUT"
        const val PLACEHOLDER_OUTPUT = $$"$OUTPUT"
        const val PLACEHOLDER_EXTENSION = $$"$EXTENSION"
    }
}

class FFmpegPassMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RulesetPreprocessMetadata<FFmpegPass> {
    override val displayName get() = "FFmpeg command"  // context.getString(R.string.)
    override val description get() = "Process sticker using custom FFmpeg CLI arguments."

    override fun describe(config: FFmpegPass) =
        "ffmpeg ${config.args.joinToString(" ")}"

    override fun createDefault() = FFmpegPass()

    @Composable
    override fun Editor(config: FFmpegPass, onConfigChanged: (FFmpegPass) -> Unit) {
        val currentArgs = config.args.ifEmpty { listOf("") }
        var focusTargetIndex by remember { mutableStateOf<Int?>(null) }
        val focusRequesters = remember(currentArgs.size) {
            List(currentArgs.size) { FocusRequester() }
        }
        LaunchedEffect(focusTargetIndex, currentArgs.size) {
            if (focusTargetIndex == null) return@LaunchedEffect
            if (focusTargetIndex!! in focusRequesters.indices) focusRequesters[focusTargetIndex!!].requestFocus()
            focusTargetIndex = null
        }
        currentArgs.forEachIndexed { index, arg ->
            OutlinedTextField(
                value = arg,
                onValueChange = {
                    val sanitized = it
                        .replace("\n", "")
                        .replace("\r", "")
                    val updatedList = currentArgs
                        .toMutableList()
                        .apply { this[index] = sanitized }
                    onConfigChanged(config.copy(args = updatedList))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onAny = {
                        val updatedList = currentArgs
                            .toMutableList()
                            .apply { add(index + 1, "") }
                        onConfigChanged(config.copy(args = updatedList))
                        focusTargetIndex = index + 1
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
                    .onPreviewKeyEvent {
                        val isDeletingEmptyArg = it.type == KeyEventType.KeyDown
                                && it.key == Key.Backspace
                                && arg.isEmpty()
                        if (!isDeletingEmptyArg || currentArgs.size <= 1) {
                            return@onPreviewKeyEvent false
                        }

                        val updatedList = currentArgs
                            .toMutableList()
                            .apply { removeAt(index) }
                        onConfigChanged(config.copy(args = updatedList))
                        focusTargetIndex = (index - 1).coerceAtLeast(0)
                        true
                    }
            )
        }
    }
}

class FFmpegPassHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PreprocessHandler<FFmpegPass> {
    override suspend fun process(config: FFmpegPass, input: ProcessingSticker) = withContext(Dispatchers.IO) {
        val outputExtension = (config.args.firstOrNull { it.contains(FFmpegPass.PLACEHOLDER_OUTPUT) }
            ?: error($$"FFmpeg args must contain $OUTPUT placeholder."))
            .replace(FFmpegPass.PLACEHOLDER_EXTENSION, input.extension)
            .substringAfter(FFmpegPass.PLACEHOLDER_OUTPUT)
            .removePrefix(".")
            .ifEmpty { input.extension }
        val tempIn = File.createTempFile("ffmpeg_in_", ".${input.extension}", context.cacheDir)
        val tempOut = File.createTempFile("ffmpeg_out_", ".$outputExtension", context.cacheDir)
        val outputPrefix = tempOut.absolutePath.removeSuffix(".$outputExtension")
        try {
            tempIn.writeBytes(input.bytes)
            val processedArgs = config.args.map { arg ->
                arg
                    .replace(FFmpegPass.PLACEHOLDER_INPUT, tempIn.absolutePath)
                    .replace(FFmpegPass.PLACEHOLDER_EXTENSION, input.extension)
                    .replace(FFmpegPass.PLACEHOLDER_OUTPUT, outputPrefix)
            }
            val code = FFmpegCli.run("-nostdin", "-y", *processedArgs.toTypedArray())
            if (code != 0) error("Ffmpeg exited with $code.")
            if (!tempOut.exists() || tempOut.length() == 0L)
                error("FFmpeg did not produce an output file.")
            ProcessingSticker(bytes = tempOut.readBytes(), extension = outputExtension)
        } finally {
            tempIn.delete()
            tempOut.delete()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface FFmpegPassModule {
    @Binds
    @IntoMap
    @ClassKey(FFmpegPass::class)
    fun bindMetadata(metadata: FFmpegPassMetadata): RulesetPreprocessMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(FFmpegPass::class)
    fun bindHandler(handler: FFmpegPassHandler): PreprocessHandler<*>
}
