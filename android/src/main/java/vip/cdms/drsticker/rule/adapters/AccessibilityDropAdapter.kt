package vip.cdms.drsticker.rule.adapters

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetAdapterMetadata
import vip.cdms.drsticker.services.AccessibilityBridge
import vip.cdms.drsticker.services.AccessibilityBridge.GestureResult
import javax.inject.Inject
import kotlin.math.hypot

@Serializable
data class AccessibilityDropAdapter(
    val slowFirstPx: Int = 50,
    val slowDurationMillis: Long = 10L,
    val fastDurationMillis: Long = 40L,
    override val dragTargetXExpression: String = $$"$screenWidth / 2",
    override val dragTargetYExpression: String = $$"$screenHeight / 2",
    override val overlaySizePx: Int = 160,
    override val gestureDelayMillis: Long = 50L,
    override val overlayOffsetYPx: Int = -200,
) : BaseDropAdapter

class AccessibilityDropAdapterMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BaseDropAdapterMetadata<AccessibilityDropAdapter> {
    override val displayName get() = "Drag & Drop (Accessibility)"  // context.getString(R.string.)
    override val description get() = "Drop stickers with Android accessibility gestures."

    override fun createDefault() = AccessibilityDropAdapter()

    @Composable
    override fun Editor(
        config: AccessibilityDropAdapter,
        onConfigChanged: (AccessibilityDropAdapter) -> Unit,
    ) {
        var slowFirstText by remember { mutableStateOf(config.slowFirstPx.toString()) }
        val slowFirst = slowFirstText.toIntOrNull()
        OutlinedTextField(
            value = slowFirstText,
            onValueChange = { text ->
                slowFirstText = text
                text.toIntOrNull()?.takeIf { it >= 0 }
                    ?.let { onConfigChanged(config.copy(slowFirstPx = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Slow initial distance (px)") },
            isError = slowFirst == null || slowFirst < 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        var slowDurationText by remember { mutableStateOf(config.slowDurationMillis.toString()) }
        val slowDuration = slowDurationText.toLongOrNull()
        OutlinedTextField(
            value = slowDurationText,
            onValueChange = { text ->
                slowDurationText = text
                text.toLongOrNull()?.takeIf { it > 0L }
                    ?.let { onConfigChanged(config.copy(slowDurationMillis = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Slow initial duration (ms)") },
            isError = slowDuration == null || slowDuration <= 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        var fastDurationText by remember { mutableStateOf(config.fastDurationMillis.toString()) }
        val fastDuration = fastDurationText.toLongOrNull()
        OutlinedTextField(
            value = fastDurationText,
            onValueChange = { text ->
                fastDurationText = text
                text.toLongOrNull()?.takeIf { it > 0L }
                    ?.let { onConfigChanged(config.copy(fastDurationMillis = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Remaining drag duration (ms)") },
            isError = fastDuration == null || fastDuration <= 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        @Suppress("DuplicatedCode")
        CommonEditor(
            config = config,
            onDragTargetXExpressionChange = { onConfigChanged(config.copy(dragTargetXExpression = it)) },
            onDragTargetYExpressionChange = { onConfigChanged(config.copy(dragTargetYExpression = it)) },
            onOverlaySizePxChange = { onConfigChanged(config.copy(overlaySizePx = it)) },
            onGestureDelayMillisChange = { onConfigChanged(config.copy(gestureDelayMillis = it)) },
            onOverlayOffsetYPxChange = { onConfigChanged(config.copy(overlayOffsetYPx = it)) },
        )
    }
}

class AccessibilityDropAdapterHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val accessibilityBridge: AccessibilityBridge,
) : BaseDropAdapterHandler<AccessibilityDropAdapter>(context) {
    override suspend fun performDragGesture(
        config: AccessibilityDropAdapter,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    ): AdapterResult {
        val totalX = endX - startX
        val totalY = endY - startY
        val totalDistance = hypot(totalX.toDouble(), totalY.toDouble())
        val ratio = if (totalDistance > config.slowFirstPx) {
            config.slowFirstPx / totalDistance
        } else {
            1.0
        }
        val middleX = (startX + totalX * ratio).toFloat()
        val middleY = (startY + totalY * ratio).toFloat()
        val firstPath = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(middleX, middleY)
        }
        val firstStroke = GestureDescription.StrokeDescription(
            firstPath,
            0L,
            config.slowDurationMillis,
            true,
        )
        return when (accessibilityBridge.dispatchGesture(firstStroke)) {
            GestureResult.Completed -> {
                val secondPath = Path().apply {
                    moveTo(middleX, middleY)
                    lineTo(endX.toFloat(), endY.toFloat())
                }
                val secondStroke = firstStroke.continueStroke(
                    secondPath,
                    0L,
                    config.fastDurationMillis,
                    false,
                )
                when (accessibilityBridge.dispatchGesture(secondStroke)) {
                    GestureResult.Completed -> AdapterResult.Completed
                    GestureResult.Cancelled -> AdapterResult.Failed("Accessibility fast gesture was cancelled.")
                    GestureResult.Unavailable -> AdapterResult.Failed("Accessibility fast gesture is unavailable.")
                }
            }

            GestureResult.Cancelled -> AdapterResult.Failed("Accessibility slow gesture was cancelled.")
            GestureResult.Unavailable -> AdapterResult.Failed("Accessibility slow gesture is unavailable.")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface AccessibilityDropAdapterModule {
    @Binds
    @IntoMap
    @ClassKey(AccessibilityDropAdapter::class)
    fun bindMetadata(metadata: AccessibilityDropAdapterMetadata): RulesetAdapterMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(AccessibilityDropAdapter::class)
    fun bindHandler(handler: AccessibilityDropAdapterHandler): AdapterHandler<*>
}
