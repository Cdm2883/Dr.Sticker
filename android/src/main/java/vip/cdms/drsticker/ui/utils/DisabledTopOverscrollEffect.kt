package vip.cdms.drsticker.ui.utils

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

@Composable
fun rememberDisabledTopOverscrollEffect(): OverscrollEffect? {
    val defaultEffect = rememberOverscrollEffect() ?: return null

    return remember(defaultEffect) {
        object : OverscrollEffect by defaultEffect {
            override fun applyToScroll(
                delta: Offset,
                source: NestedScrollSource,
                performScroll: (Offset) -> Offset
            ) =
                if (delta.y > 0) performScroll(delta)
                else defaultEffect.applyToScroll(delta, source, performScroll)

            override suspend fun applyToFling(
                velocity: Velocity,
                performFling: suspend (Velocity) -> Velocity
            ) {
                if (velocity.y > 0) performFling(velocity)
                else defaultEffect.applyToFling(velocity, performFling)
            }
        }
    }
}
