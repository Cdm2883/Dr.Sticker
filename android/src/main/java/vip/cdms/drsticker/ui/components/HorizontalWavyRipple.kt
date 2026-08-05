package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.horizontalWavyRipple(
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
    onClick: () -> Unit,
    amplitude: Dp = WavyDividerDefaults.Amplitude,
    wavelength: Dp = WavyDividerDefaults.Wavelength,
    thickness: Dp = WavyDividerDefaults.Thickness
) = this
    .layout { measurable, constraints ->
        val targetThicknessPx = if (thickness == Dp.Hairline) 1f else thickness.toPx()
        val requiredHeightPx = (amplitude.toPx() * 2) + targetThicknessPx
        val topOverlapPx = if (hasDividerAbove) requiredHeightPx else 0f
        val bottomOverlapPx = if (hasDividerBelow) requiredHeightPx else 0f

        val looseConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeable = measurable.measure(looseConstraints)

        val topPx = topOverlapPx.roundToInt()
        val bottomPx = bottomOverlapPx.roundToInt()

        val reportedHeight = (placeable.height - topPx - bottomPx).coerceAtLeast(0)
        layout(placeable.width, reportedHeight) {
            placeable.place(0, -topPx)
        }
    }
    .clip(
        WavyRippleShape(
            hasDividerAbove = hasDividerAbove,
            hasDividerBelow = hasDividerBelow,
            amplitude = amplitude,
            wavelength = wavelength,
            thickness = thickness
        )
    )
    .clickable(onClick = onClick)
    .layout { measurable, constraints ->
        val targetThicknessPx = if (thickness == Dp.Hairline) 1f else thickness.toPx()
        val requiredHeightPx = (amplitude.toPx() * 2) + targetThicknessPx
        val topOverlapPx = if (hasDividerAbove) requiredHeightPx else 0f
        val bottomOverlapPx = if (hasDividerBelow) requiredHeightPx else 0f

        val placeable = measurable.measure(constraints)
        val topPx = topOverlapPx.roundToInt()
        val bottomPx = bottomOverlapPx.roundToInt()

        layout(placeable.width, placeable.height + topPx + bottomPx) {
            placeable.place(0, topPx)
        }
    }

data class WavyRippleShape(
    private val hasDividerAbove: Boolean,
    private val hasDividerBelow: Boolean,
    private val amplitude: Dp,
    private val wavelength: Dp,
    private val thickness: Dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val amplitudePx = with(density) { amplitude.toPx() }
        val wavelengthPx = with(density) { wavelength.toPx() }
        val strokeWidthPx = if (thickness == Dp.Hairline) 1f else with(density) { thickness.toPx() }
        val requiredHeightPx = (amplitudePx * 2) + strokeWidthPx

        val path = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        if (amplitudePx == 0f || wavelengthPx == 0f) return Outline.Generic(path)

        val halfWavelengthPx = wavelengthPx / 2f

        fun Path.drawWave(): Float {
            moveTo(0f, 0f)
            var anchorX = halfWavelengthPx
            var controlX = halfWavelengthPx / 2f
            var controlY = amplitudePx
            while (anchorX - halfWavelengthPx <= size.width + wavelengthPx) {
                quadraticTo(controlX, controlY, anchorX, 0f)
                anchorX += halfWavelengthPx
                controlX += halfWavelengthPx
                controlY *= -1f
            }
            return anchorX
        }

        if (hasDividerAbove) {
            val topCutout = Path().apply {
                val finalX = drawWave()
                lineTo(finalX, -100f)
                lineTo(0f, -100f)
                close()
            }
            val waveCenterY = requiredHeightPx / 2f
            topCutout.translate(Offset(0f, waveCenterY + strokeWidthPx / 2f))
            path.op(path, topCutout, PathOperation.Difference)
        }

        if (hasDividerBelow) {
            val bottomCutout = Path().apply {
                val finalX = drawWave()
                lineTo(finalX, size.height + 100f)
                lineTo(0f, size.height + 100f)
                close()
            }
            val waveCenterY = size.height - requiredHeightPx / 2f
            bottomCutout.translate(Offset(0f, waveCenterY - strokeWidthPx / 2f))
            path.op(path, bottomCutout, PathOperation.Difference)
        }

        return Outline.Generic(path)
    }
}
