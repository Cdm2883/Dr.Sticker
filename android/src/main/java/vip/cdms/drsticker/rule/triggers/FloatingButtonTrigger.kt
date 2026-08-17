package vip.cdms.drsticker.rule.triggers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.R
import vip.cdms.drsticker.rule.RulesetTriggerMetadata
import vip.cdms.drsticker.utils.evalExpr
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
data class FloatingButtonTrigger(
    val centerXExpression: String = $$"$screenWidth * 0.9",
    val centerYExpression: String = $$"$screenHeight * 0.8",
    val sizeDp: Float = 40f,
) : RulesetTrigger

class FloatingButtonTriggerMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RulesetTriggerMetadata<FloatingButtonTrigger> {
    override val displayName get() = "Floating button"  // context.getString(R.string.)
    override val description get() = "Open the sticker picker from a movable floating button."

    override fun createDefault() = FloatingButtonTrigger()

    @Composable
    override fun Editor(
        config: FloatingButtonTrigger,
        onConfigChanged: (FloatingButtonTrigger) -> Unit,
    ) {
        OutlinedTextField(
            value = config.centerXExpression,
            onValueChange = { onConfigChanged(config.copy(centerXExpression = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Initial center X") },
            supportingText = { Text($$"Variables: $screenWidth, $screenHeight") },
            singleLine = true,
        )
        OutlinedTextField(
            value = config.centerYExpression,
            onValueChange = { onConfigChanged(config.copy(centerYExpression = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Initial center Y") },
            supportingText = { Text($$"Variables: $screenWidth, $screenHeight") },
            singleLine = true,
        )

        var sizeText by remember { mutableStateOf(config.sizeDp.toString()) }
        val size = sizeText.toFloatOrNull()
        OutlinedTextField(
            value = sizeText,
            onValueChange = { updated ->
                sizeText = updated
                updated.toFloatOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.let { onConfigChanged(config.copy(sizeDp = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Button size (dp)") },
            isError = size == null || !size.isFinite() || size <= 0f,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
    }
}

class FloatingButtonTriggerHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TriggerHandler<FloatingButtonTrigger> {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var activeWindow: FloatingButtonWindow? = null

    private data class FloatingButtonWindow(val view: View)

    override fun activate(
        config: FloatingButtonTrigger,
        onOpenPicker: () -> Unit
    ): TriggerSession {
        check(Settings.canDrawOverlays(context)) { "Overlay permission is not granted." }
        closeActiveWindow()

        val metrics = context.resources.displayMetrics
        val variables = mapOf(
            "screenWidth" to metrics.widthPixels.toDouble(),
            "screenHeight" to metrics.heightPixels.toDouble(),
        )
        val sizePx = (config.sizeDp * metrics.density).roundToInt()
        val centerX = config.centerXExpression.evalExpr(variables).roundToInt()
        val centerY = config.centerYExpression.evalExpr(variables).roundToInt()
        val view = ImageButton(context).apply {
            setImageResource(R.drawable.ic_drsticker)
            val backgroundColor = ContextCompat.getColor(context, R.color.ic_launcher_background_color)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(backgroundColor, (255 * 0.8f).toInt()))
            }
        }
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = centerX - sizePx / 2
            y = centerY - sizePx / 2
        }
        installTouchHandler(view, params, onOpenPicker)
        windowManager.addView(view, params)
        val window = FloatingButtonWindow(view)
        activeWindow = window

        return TriggerSession {
            if (activeWindow === window) {
                activeWindow = null
                removeView(window.view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installTouchHandler(
        view: View,
        params: WindowManager.LayoutParams,
        onOpenPicker: () -> Unit,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        val threshold = 8f * context.resources.displayMetrics.density
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (!dragging && (abs(deltaX) > threshold || abs(deltaY) > threshold)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = initialX + deltaX.roundToInt()
                        params.y = initialY + deltaY.roundToInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) onOpenPicker()
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun closeActiveWindow() {
        val window = activeWindow ?: return
        activeWindow = null
        removeView(window.view)
    }

    private fun removeView(view: View) {
        if (view.isAttachedToWindow) windowManager.removeView(view)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface FloatingButtonTriggerModule {
    @Binds
    @IntoMap
    @ClassKey(FloatingButtonTrigger::class)
    fun bindMetadata(metadata: FloatingButtonTriggerMetadata): RulesetTriggerMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(FloatingButtonTrigger::class)
    fun bindHandler(handler: FloatingButtonTriggerHandler): TriggerHandler<*>
}
