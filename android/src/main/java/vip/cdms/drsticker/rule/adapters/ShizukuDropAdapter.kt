package vip.cdms.drsticker.rule.adapters

import android.content.Context
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
import vip.cdms.drsticker.services.shizuku.ShizukuBridge
import javax.inject.Inject

@Serializable
data class ShizukuDropAdapter(
    val gestureDurationMillis: Long = 40,
    override val dragTargetXExpression: String = $$"$screenWidth / 2",
    override val dragTargetYExpression: String = $$"$screenHeight / 2",
    override val overlaySizePx: Int = 160,
    override val gestureDelayMillis: Long = 50,
    override val overlayOffsetYPx: Int = -200,
    override val useMediaStore: Boolean = false,
) : BaseDropAdapter

class ShizukuDropAdapterMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseDropAdapterMetadata<ShizukuDropAdapter> {
    override val displayName get() = "Drag & Drop (Shizuku)"  // context.getString(R.string.)
    override val description get() = "Drop stickers with a Shizuku-powered input gesture."

    override fun createDefault() = ShizukuDropAdapter()

    @Composable
    override fun Editor(
        config: ShizukuDropAdapter,
        onConfigChanged: (ShizukuDropAdapter) -> Unit,
    ) {
        var gestureDurationText by remember { mutableStateOf(config.gestureDurationMillis.toString()) }
        val gestureDuration = gestureDurationText.toLongOrNull()
        OutlinedTextField(
            value = gestureDurationText,
            onValueChange = { text ->
                gestureDurationText = text
                text.toLongOrNull()?.takeIf { it > 0L }
                    ?.let { onConfigChanged(config.copy(gestureDurationMillis = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Drag gesture duration (ms)") },
            isError = gestureDuration == null || gestureDuration <= 0L,
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
            onUseMediaStoreChange = { onConfigChanged(config.copy(useMediaStore = it)) },
        )
    }
}

class ShizukuDropAdapterHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val shizukuBridge: ShizukuBridge,
) : BaseDropAdapterHandler<ShizukuDropAdapter>(context) {
    override suspend fun performDragGesture(
        config: ShizukuDropAdapter,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    ) {
        val ok = shizukuBridge.swipe(startX, startY, endX, endY, config.gestureDurationMillis)
        if (!ok) error("Shizuku swipe failed or is unavailable.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface ShizukuDropAdapterModule {
    @Binds
    @IntoMap
    @ClassKey(ShizukuDropAdapter::class)
    fun bindMetadata(metadata: ShizukuDropAdapterMetadata): RulesetAdapterMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(ShizukuDropAdapter::class)
    fun bindHandler(handler: ShizukuDropAdapterHandler): AdapterHandler<*>
}
