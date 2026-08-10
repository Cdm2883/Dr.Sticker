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
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.R
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
            val paddingPx = (sizePx * 0.2f).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC3F6836.toInt())
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
    fun bindHandler(handler: FloatingButtonTriggerHandler): TriggerHandler<*>
}
