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
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.preprocess.StickerFile
import vip.cdms.drsticker.utils.evalExpr
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

abstract class BaseDropAdapterHandler<C : BaseDropAdapter>(
    private val context: Context,
) : AdapterHandler<C> {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var dropOverlay: DropOverlayWindow? = null

    @Suppress("UnnecessaryVariable")
    final override suspend fun send(config: C, sticker: StickerFile): AdapterResult =
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
                sticker = sticker,
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
        sticker: StickerFile,
        sizePx: Int,
        centerX: Int,
        centerY: Int,
    ): DropOverlayWindow {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            sticker.file,
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
        view.setOnTouchListener(DragTouchHandler(view, contentUri, sticker.mimeType))
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
            outShadowSize.set(1, 1) // 尺寸设为最小值
            outShadowTouchPoint.set(0, 0)
        }

        override fun onDrawShadow(canvas: Canvas) {
            // 不绘制任何内容
        }
    }
}
