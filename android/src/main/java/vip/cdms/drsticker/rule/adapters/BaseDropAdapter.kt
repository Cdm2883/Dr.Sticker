package vip.cdms.drsticker.rule.adapters

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetAdapterMetadata
import vip.cdms.drsticker.rule.utils.getMimeTypeFromExtension
import vip.cdms.drsticker.utils.evalExpr
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Serializable
sealed interface BaseDropAdapter : RulesetAdapter {
    val dragTargetXExpression: String
    val dragTargetYExpression: String
    val overlaySizePx: Int
    val gestureDelayMillis: Long
    val overlayOffsetYPx: Int
    val useMediaStore: Boolean
}

interface BaseDropAdapterMetadata<C : BaseDropAdapter> : RulesetAdapterMetadata<C> {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun CommonEditor(
        config: C,
        onDragTargetXExpressionChange: (String) -> Unit,
        onDragTargetYExpressionChange: (String) -> Unit,
        onOverlaySizePxChange: (Int) -> Unit,
        onGestureDelayMillisChange: (Long) -> Unit,
        onOverlayOffsetYPxChange: (Int) -> Unit,
        onUseMediaStoreChange: (Boolean) -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MediaStore compatibility",
                style = MaterialTheme.typography.bodyLargeEmphasized,
            )
            Switch(
                checked = config.useMediaStore,
                onCheckedChange = onUseMediaStoreChange,
            )
        }
    }
}

abstract class BaseDropAdapterHandler<C : BaseDropAdapter>(
    private val context: Context,
) : AdapterHandler<C> {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var dropOverlay: DropOverlayWindow? = null
    private var isSending = false
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("UnnecessaryVariable")
    final override suspend fun send(config: C, file: File) = withContext(Dispatchers.Main.immediate) {
        check(Settings.canDrawOverlays(context)) { "Overlay permission is not granted." }
        check(!isSending) { "A sticker handoff is already active." }
        isSending = true

        var window: DropOverlayWindow? = null
        var temporaryMediaUri: Uri? = null
        try {
            val mimeType = file.extension.getMimeTypeFromExtension()
            val contentUri = if (config.useMediaStore)
                withContext(Dispatchers.IO) {
                    createMediaStoreUri(file, mimeType).also { temporaryMediaUri = it }
                }
            else
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val metrics = context.resources.displayMetrics
            val variables = mapOf(
                "screenWidth" to metrics.widthPixels.toDouble(),
                "screenHeight" to metrics.heightPixels.toDouble(),
            )
            val targetX = config.dragTargetXExpression.evalExpr(variables).roundToInt()
            val targetY = config.dragTargetYExpression.evalExpr(variables).roundToInt()
            val sourceCenterX = targetX
            val sourceCenterY = targetY + config.overlayOffsetYPx
            window = createDropOverlay(
                contentUri = contentUri,
                mimeType = mimeType,
                sizePx = config.overlaySizePx,
                centerX = sourceCenterX,
                centerY = sourceCenterY,
            )
            dropOverlay = window
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
            isSending = false
            temporaryMediaUri?.let(::scheduleMediaStoreDeletion)
            window?.view?.takeIf { it.isAttachedToWindow }?.let(windowManager::removeView)
        }
    }

    protected abstract suspend fun performDragGesture(
        config: C,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    )

    private class DropOverlayWindow(val view: View)

    private fun createMediaStoreUri(file: File, mimeType: String): Uri {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "MediaStore requires Android 10 or newer."
        }
        val resolver = context.contentResolver
        val extension = file.extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        val (collection, relativePath) = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                    "${Environment.DIRECTORY_PICTURES}/Dr.Sticker/.drop"

            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                    "${Environment.DIRECTORY_MOVIES}/Dr.Sticker/.drop"

            else -> error("Unsupported MediaStore MIME type: $mimeType")
        }
        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "drsticker_drop_${System.currentTimeMillis()}$extension",
            )
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("Failed to create MediaStore item.")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Failed to open MediaStore item for writing.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) == 1) {
                "Failed to publish MediaStore item."
            }
            return uri
        } catch (cause: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw cause
        }
    }

    private fun scheduleMediaStoreDeletion(uri: Uri) = cleanupScope.launch {
        delay(MEDIA_STORE_RETENTION)
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Log.w(TAG, "Failed to delete temporary MediaStore item.", it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDropOverlay(
        contentUri: Uri,
        mimeType: String,
        sizePx: Int,
        centerX: Int,
        centerY: Int,
    ): DropOverlayWindow {
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

    private companion object {
        const val TAG = "BaseDropAdapter"
        val MEDIA_STORE_RETENTION = 3.seconds
    }
}
