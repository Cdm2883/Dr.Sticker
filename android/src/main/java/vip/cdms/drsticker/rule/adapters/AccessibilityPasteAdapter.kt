package vip.cdms.drsticker.rule.adapters

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.Composable
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

@Serializable
data class AccessibilityPasteAdapter(
    override val focusDelayMillis: Long = 0L,
    override val focusClickXExpression: String = $$"$screenWidth / 2",
    override val focusClickYExpression: String = $$"$screenHeight - 100",
    override val pasteDelayMillis: Long = 0L,
    override val postClickDelayMillis: Long = 300L,
    override val postClickXExpression: String = "",
    override val postClickYExpression: String = "",
    override val clickDurationMillis: Long = 1L,
    override val pastePath: Boolean = false,
) : BasePasteAdapter

class AccessibilityPasteAdapterMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BasePasteAdapterMetadata<AccessibilityPasteAdapter> {
    override val displayName get() = "Copy & Paste (Accessibility)"  // context.getString(R.string.)
    override val description get() = "Paste stickers with Android accessibility actions."

    override fun createDefault() = AccessibilityPasteAdapter()

    @Composable
    override fun Editor(
        config: AccessibilityPasteAdapter,
        onConfigChanged: (AccessibilityPasteAdapter) -> Unit,
    ) {
        @Suppress("DuplicatedCode")
        CommonEditor(
            config = config,
            onFocusDelayMillisChange = { onConfigChanged(config.copy(focusDelayMillis = it)) },
            onFocusClickXExpressionChange = { onConfigChanged(config.copy(focusClickXExpression = it)) },
            onFocusClickYExpressionChange = { onConfigChanged(config.copy(focusClickYExpression = it)) },
            onPasteDelayMillisChange = { onConfigChanged(config.copy(pasteDelayMillis = it)) },
            onPostClickDelayMillisChange = { onConfigChanged(config.copy(postClickDelayMillis = it)) },
            onPostClickXExpressionChange = { onConfigChanged(config.copy(postClickXExpression = it)) },
            onPostClickYExpressionChange = { onConfigChanged(config.copy(postClickYExpression = it)) },
            onClickDurationMillisChange = { onConfigChanged(config.copy(clickDurationMillis = it)) },
            onPastePathChange = { onConfigChanged(config.copy(pastePath = it)) },
        )
    }
}

class AccessibilityPasteAdapterHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val accessibilityBridge: AccessibilityBridge,
) : BasePasteAdapterHandler<AccessibilityPasteAdapter>(context) {
    override suspend fun click(x: Int, y: Int, durationMillis: Long) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMillis)
        when (accessibilityBridge.dispatchGesture(stroke)) {
            GestureResult.Completed -> Unit
            GestureResult.Cancelled -> error("Accessibility click gesture was cancelled.")
            GestureResult.Unavailable -> error("Accessibility click gesture is unavailable.")
        }
    }

    override suspend fun performPaste() {
        val node = accessibilityBridge.getFocusedNode()
            ?: error("No input-focused node is available for the accessibility paste.")
        try {
            check(node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                "The focused node rejected the accessibility paste action (class=${node.className})."
            }
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface AccessibilityPasteAdapterModule {
    @Binds
    @IntoMap
    @ClassKey(AccessibilityPasteAdapter::class)
    fun bindMetadata(metadata: AccessibilityPasteAdapterMetadata): RulesetAdapterMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(AccessibilityPasteAdapter::class)
    fun bindHandler(handler: AccessibilityPasteAdapterHandler): AdapterHandler<*>
}
