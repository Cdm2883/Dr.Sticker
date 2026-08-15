package vip.cdms.drsticker.rule.adapters

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetAdapterMetadata
import vip.cdms.drsticker.rule.utils.getMimeTypeFromExtension
import vip.cdms.drsticker.utils.evalExpr
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
sealed interface BaseDropAdapter : RulesetAdapter {
    val dragTargetXExpression: String
    val dragTargetYExpression: String
    val overlaySizePx: Int
    val gestureDelayMillis: Long
    val overlayOffsetYPx: Int
}

interface BaseDropAdapterMetadata<C : BaseDropAdapter> : RulesetAdapterMetadata<C> {
    @Composable
    fun CommonEditor(
        config: C,
        onDragTargetXExpressionChange: (String) -> Unit,
        onDragTargetYExpressionChange: (String) -> Unit,
        onOverlaySizePxChange: (Int) -> Unit,
        onGestureDelayMillisChange: (Long) -> Unit,
        onOverlayOffsetYPxChange: (Int) -> Unit,
    ) {
        OutlinedTextField(
            value = config.dragTargetXExpression,
            onValueChange = onDragTargetXExpressionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Drop target X") },
            supportingText = { Text($$"Variables: $screenWidth, $screenHeight") },
            singleLine = true,
        )
        OutlinedTextField(
            value = config.dragTargetYExpression,
            onValueChange = onDragTargetYExpressionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Drop target Y") },
            supportingText = { Text($$"Variables: $screenWidth, $screenHeight") },
            singleLine = true,
        )

        var overlaySizeText by remember { mutableStateOf(config.overlaySizePx.toString()) }
        val overlaySize = overlaySizeText.toIntOrNull()
        OutlinedTextField(
            value = overlaySizeText,
            onValueChange = { text ->
                overlaySizeText = text
                text.toIntOrNull()?.takeIf { it > 0 }?.let(onOverlaySizePxChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Drag overlay size (px)") },
            isError = overlaySize == null || overlaySize <= 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        var gestureDelayText by remember { mutableStateOf(config.gestureDelayMillis.toString()) }
        val gestureDelay = gestureDelayText.toLongOrNull()
        OutlinedTextField(
            value = gestureDelayText,
            onValueChange = { text ->
                gestureDelayText = text
                text.toLongOrNull()?.takeIf { it >= 0L }?.let(onGestureDelayMillisChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Delay before drag (ms)") },
            isError = gestureDelay == null || gestureDelay < 0L,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        var overlayOffsetText by remember { mutableStateOf(config.overlayOffsetYPx.toString()) }
        val overlayOffset = overlayOffsetText.toIntOrNull()
        OutlinedTextField(
            value = overlayOffsetText,
            onValueChange = { text ->
                overlayOffsetText = text
                text.toIntOrNull()?.let(onOverlayOffsetYPxChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Drag start Y offset (px)") },
            isError = overlayOffset == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
        )
    }
}

abstract class BaseDropAdapterHandler<C : BaseDropAdapter>(
    private val context: Context,
) : AdapterHandler<C> {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var dropOverlay: DropOverlayWindow? = null

    @Suppress("UnnecessaryVariable")
    final override suspend fun send(config: C, file: File): AdapterResult =
        withContext(Dispatchers.Main.immediate) {
            check(Settings.canDrawOverlays(context)) { "Overlay permission is not granted." }
            check(dropOverlay == null) { "A drop overlay is already active." }

            val metrics = context.resources.displayMetrics
            val variables = mapOf(
                "screenWidth" to metrics.widthPixels.toDouble(),
                "screenHeight" to metrics.heightPixels.toDouble(),
            )
            val targetX = config.dragTargetXExpression.evalExpr(variables).roundToInt()
            val targetY = config.dragTargetYExpression.evalExpr(variables).roundToInt()
            val sourceCenterX = targetX
            val sourceCenterY = targetY + config.overlayOffsetYPx
            val window = createDropOverlay(
                file = file,
                sizePx = config.overlaySizePx,
                centerX = sourceCenterX,
                centerY = sourceCenterY,
            )
            dropOverlay = window
            try {
                @Suppress("ConvertLongToDuration")
                delay(config.gestureDelayMillis)
                performDragGesture(
                    config = config,
                    startX = sourceCenterX,
                    startY = sourceCenterY,
                    endX = targetX,
                    endY = targetY,
                )
            } finally {
                if (dropOverlay === window) dropOverlay = null
                if (window.view.isAttachedToWindow) windowManager.removeView(window.view)
            }
        }

    protected abstract suspend fun performDragGesture(
        config: C,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    ): AdapterResult

    private class DropOverlayWindow(val view: View)

    @SuppressLint("ClickableViewAccessibility")
    private fun createDropOverlay(
        file: File,
        sizePx: Int,
        centerX: Int,
        centerY: Int,
    ): DropOverlayWindow {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val view = ImageView(context).apply {
            setImageDrawable(0x00000000.toDrawable())
        }
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = centerX - sizePx / 2
            y = centerY - sizePx / 2
        }
        val mimeType = file.extension.getMimeTypeFromExtension()
        view.setOnTouchListener(DragTouchHandler(view, contentUri, mimeType))
        windowManager.addView(view, params)
        return DropOverlayWindow(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    private class DragTouchHandler(
        private val view: View,
        private val contentUri: Uri,
        private val mimeType: String,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var started = false

        override fun onTouch(ignored: View, event: MotionEvent) = true.also {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    started = false
                }

                MotionEvent.ACTION_MOVE -> if (!started &&
                    (abs(event.x - downX) > 5f || abs(event.y - downY) > 5f)
                ) {
                    started = true
                    val clipData = ClipData(
                        "Sticker",
                        arrayOf(mimeType.ifBlank { ClipDescription.MIMETYPE_TEXT_PLAIN }),
                        ClipData.Item(contentUri),
                    )
                    view.startDragAndDrop(
                        clipData,
//                        View.DragShadowBuilder(view),
                        EmptyDragShadowBuilder(),
                        null,
                        View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ,
                    )
                }
            }
        }
    }

    class EmptyDragShadowBuilder : View.DragShadowBuilder() {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(1, 1)
            outShadowTouchPoint.set(0, 0)
        }

        override fun onDrawShadow(canvas: Canvas) {}
    }
}
