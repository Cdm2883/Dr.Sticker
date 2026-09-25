package vip.cdms.drsticker.rule.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetAdapterMetadata
import vip.cdms.drsticker.utils.evalExpr
import java.io.File
import kotlin.math.roundToInt

@Serializable
sealed interface BasePasteAdapter : RulesetAdapter {
    val focusDelayMillis: Long
    val focusClickXExpression: String
    val focusClickYExpression: String
    val pasteDelayMillis: Long
    val postClickDelayMillis: Long
    val postClickXExpression: String
    val postClickYExpression: String
    val clickDurationMillis: Long
    val pastePath: Boolean
}

interface BasePasteAdapterMetadata<C : BasePasteAdapter> : RulesetAdapterMetadata<C> {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun CommonEditor(
        config: C,
        onFocusDelayMillisChange: (Long) -> Unit,
        onFocusClickXExpressionChange: (String) -> Unit,
        onFocusClickYExpressionChange: (String) -> Unit,
        onPasteDelayMillisChange: (Long) -> Unit,
        onPostClickDelayMillisChange: (Long) -> Unit,
        onPostClickXExpressionChange: (String) -> Unit,
        onPostClickYExpressionChange: (String) -> Unit,
        onClickDurationMillisChange: (Long) -> Unit,
        onPastePathChange: (Boolean) -> Unit,
    ) {
        var focusDelayText by remember { mutableStateOf(config.focusDelayMillis.toString()) }
        val focusDelay = focusDelayText.toLongOrNull()
        OutlinedTextField(
            value = focusDelayText,
            onValueChange = { text ->
                focusDelayText = text
                text.toLongOrNull()?.takeIf { it >= 0L }?.let(onFocusDelayMillisChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Delay before focus click (ms)") },
            isError = focusDelay == null || focusDelay < 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        OutlinedTextField(
            value = config.focusClickXExpression,
            onValueChange = onFocusClickXExpressionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Focus click X") },
            supportingText = { Text($$"Variables: $screenWidth, $screenHeight") },
            singleLine = true,
        )
        OutlinedTextField(
            value = config.focusClickYExpression,
            onValueChange = onFocusClickYExpressionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Focus click Y") },
            supportingText = { Text($$"Variables: $screenWidth, $screenHeight") },
            singleLine = true,
        )

        var pasteDelayText by remember { mutableStateOf(config.pasteDelayMillis.toString()) }
        val pasteDelay = pasteDelayText.toLongOrNull()
        OutlinedTextField(
            value = pasteDelayText,
            onValueChange = { text ->
                pasteDelayText = text
                text.toLongOrNull()?.takeIf { it >= 0L }?.let(onPasteDelayMillisChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Delay before paste (ms)") },
            isError = pasteDelay == null || pasteDelay < 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        var postClickDelayText by remember { mutableStateOf(config.postClickDelayMillis.toString()) }
        val postClickDelay = postClickDelayText.toLongOrNull()
        OutlinedTextField(
            value = postClickDelayText,
            onValueChange = { text ->
                postClickDelayText = text
                text.toLongOrNull()?.takeIf { it >= 0L }?.let(onPostClickDelayMillisChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Delay before post-paste click (ms)") },
            isError = postClickDelay == null || postClickDelay < 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        OutlinedTextField(
            value = config.postClickXExpression,
            onValueChange = onPostClickXExpressionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Post-paste click X") },
            supportingText = { Text("Leave both empty to skip the post-paste click.") },
            singleLine = true,
        )
        OutlinedTextField(
            value = config.postClickYExpression,
            onValueChange = onPostClickYExpressionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Post-paste click Y") },
            supportingText = { Text("Leave both empty to skip the post-paste click.") },
            singleLine = true,
        )

        var clickDurationText by remember { mutableStateOf(config.clickDurationMillis.toString()) }
        val clickDuration = clickDurationText.toLongOrNull()
        OutlinedTextField(
            value = clickDurationText,
            onValueChange = { text ->
                clickDurationText = text
                text.toLongOrNull()?.takeIf { it > 0L }?.let(onClickDurationMillisChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Click duration (ms)") },
            isError = clickDuration == null || clickDuration <= 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Copy file path instead of image",
                style = MaterialTheme.typography.bodyLargeEmphasized,
            )
            Switch(
                checked = config.pastePath,
                onCheckedChange = onPastePathChange,
            )
        }
    }
}

abstract class BasePasteAdapterHandler<C : BasePasteAdapter>(
    private val context: Context,
) : AdapterHandler<C> {
    @Suppress("ConvertLongToDuration")
    final override suspend fun send(config: C, file: File) {
        val postClickX = config.postClickXExpression
        val postClickY = config.postClickYExpression
        require(postClickX.isBlank() == postClickY.isBlank()) {
            "The post-paste click X and Y expressions must be set together."
        }

        val metrics = context.resources.displayMetrics
        val variables = mapOf(
            "screenWidth" to metrics.widthPixels.toDouble(),
            "screenHeight" to metrics.heightPixels.toDouble(),
        )

        if (config.pastePath) copyPathToClipboard(context, file.resolvedFile())
        else copyImageToClipboard(context, file)

        delay(config.focusDelayMillis)
        click(
            x = config.focusClickXExpression.evalExpr(variables).roundToInt(),
            y = config.focusClickYExpression.evalExpr(variables).roundToInt(),
            durationMillis = config.clickDurationMillis,
        )
        delay(config.pasteDelayMillis)
        performPaste()
        if (postClickX.isNotBlank()) {
            delay(config.postClickDelayMillis)
            click(
                x = postClickX.evalExpr(variables).roundToInt(),
                y = postClickY.evalExpr(variables).roundToInt(),
                durationMillis = config.clickDurationMillis,
            )
        }
    }

    protected abstract suspend fun click(x: Int, y: Int, durationMillis: Long)

    protected abstract suspend fun performPaste()
}

private fun File.resolvedFile(): File =
    runCatching { canonicalFile }.getOrDefault(absoluteFile)

private fun copyPathToClipboard(context: Context, file: File) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Sticker path", file.path))
}

private fun copyImageToClipboard(context: Context, file: File) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Sticker", uri))
}
