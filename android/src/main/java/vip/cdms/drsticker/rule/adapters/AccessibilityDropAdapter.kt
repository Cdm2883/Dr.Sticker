package vip.cdms.drsticker.rule.adapters

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
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
    fun bindHandler(handler: AccessibilityDropAdapterHandler): AdapterHandler<*>
}
