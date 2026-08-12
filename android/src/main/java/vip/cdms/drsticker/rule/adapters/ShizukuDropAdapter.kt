package vip.cdms.drsticker.rule.adapters

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
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
) : BaseDropAdapter

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
    ): AdapterResult {
        val ok = shizukuBridge.swipe(startX, startY, endX, endY, config.gestureDurationMillis)
        return if (ok) AdapterResult.Completed
        else AdapterResult.Failed("Shizuku swipe failed or is unavailable.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface ShizukuDropAdapterModule {
    @Binds
    @IntoMap
    @ClassKey(ShizukuDropAdapter::class)
    fun bindHandler(handler: ShizukuDropAdapterHandler): AdapterHandler<*>
}
