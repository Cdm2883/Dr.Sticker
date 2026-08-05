package vip.cdms.drsticker.ui.utils

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun rememberScrollToHideBottomBarState(
    initialHeight: Dp = 80.dp
): ScrollToHideBottomBarState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    return remember(initialHeight, density, coroutineScope) {
        ScrollToHideBottomBarState(initialHeight, density, coroutineScope)
    }
}

class ScrollToHideBottomBarState(
    val bottomBarHeight: Dp,
    private val density: Density,
    private val coroutineScope: CoroutineScope
) {
    val bottomBarHeightPx = with(density) { bottomBarHeight.toPx() }

    var bottomBarOffsetHeightPx by mutableFloatStateOf(0f)
        private set

    var isLocked by mutableStateOf(false)
        private set

    private var snapJob: Job? = null

    val bottomPadding: Dp
        get() = with(density) { (bottomBarHeightPx + bottomBarOffsetHeightPx).coerceAtLeast(0f).toDp() }

    val bottomBarOffset: IntOffset
        get() = IntOffset(x = 0, y = -bottomBarOffsetHeightPx.roundToInt())

    @Suppress("SameReturnValue")
    val nestedScrollConnection = object : NestedScrollConnection {
        @Suppress("SameReturnValue")
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (isLocked) return Offset.Zero

            if (source == NestedScrollSource.UserInput) {
                snapJob?.cancel()
            }
            if (snapJob?.isActive != true) {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx + delta
                bottomBarOffsetHeightPx = newOffset.coerceIn(-bottomBarHeightPx, 0f)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (isLocked) return Velocity.Zero

            snapJob?.cancel()
            val velocityY = consumed.y + available.y
            val targetOffset = when {
                velocityY < -150f -> -bottomBarHeightPx
                velocityY > 150f -> 0f
                bottomBarOffsetHeightPx < -bottomBarHeightPx / 2f -> -bottomBarHeightPx
                else -> 0f
            }
            animateTo(targetOffset, true)
            return Velocity.Zero
        }
    }

    fun lockAndHide() {
        isLocked = true
        hide()
    }
    fun unlockAndShow() {
        isLocked = false
        show()
    }

    fun show() = animateTo(0f, false)
    fun hide() = animateTo(-bottomBarHeightPx, false)

//    fun reset() {
//        snapJob?.cancel()
//        isLocked = false
//        bottomBarOffsetHeightPx = 0f
//    }

    private fun animateTo(targetOffset: Float, fromGesture: Boolean) {
        snapJob?.cancel()
        snapJob = coroutineScope.launch {
            animate(
                initialValue = bottomBarOffsetHeightPx,
                targetValue = targetOffset,
                animationSpec = tween(if (fromGesture) 200 else 500)
            ) { value, _ ->
                bottomBarOffsetHeightPx = value
            }
        }
    }
}
