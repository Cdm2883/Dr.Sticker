package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@ExperimentalMaterial3ExpressiveApi
@Composable
fun HorizontalWavyDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = WavyDividerDefaults.Thickness,
    color: Color = WavyDividerDefaults.color,
    amplitude: Dp = WavyDividerDefaults.Amplitude,
    wavelength: Dp = WavyDividerDefaults.Wavelength,
) {
    val targetThickness =
        if (thickness == Dp.Hairline) {
            (1f / LocalDensity.current.density).dp
        } else {
            thickness
        }

    val requiredHeight = (amplitude * 2) + targetThickness

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(requiredHeight)
            .drawWithCache {
                val strokeWidthPx = targetThickness.toPx()
                val wavelengthPx = wavelength.toPx()
                val amplitudePx = amplitude.toPx()
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                val strokeStyle = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )

                val wavePath = Path()

                if (amplitudePx == 0f || wavelengthPx == 0f) {
                    // Just draw a straight line.
                    wavePath.moveTo(0f, centerY)
                    wavePath.lineTo(width, centerY)
                } else {
                    val halfWavelengthPx = wavelengthPx / 2f
                    var anchorX = halfWavelengthPx
                    val anchorY = 0f
                    var controlX = halfWavelengthPx / 2f
                    var controlY = amplitudePx

                    wavePath.moveTo(0f, 0f)
                    while (anchorX - halfWavelengthPx <= width) {
                        wavePath.quadraticTo(controlX, controlY, anchorX, anchorY)
                        anchorX += halfWavelengthPx
                        controlX += halfWavelengthPx
                        controlY *= -1f // Flip the control point vertically for the next curve.
                    }

                    wavePath.translate(Offset(x = 0f, y = centerY))
                }

                onDrawBehind {
                    drawPath(
                        path = wavePath,
                        color = color,
                        style = strokeStyle
                    )
                }
            }
    )
}

@ExperimentalMaterial3ExpressiveApi
object WavyDividerDefaults {
    val Thickness: Dp = DividerDefaults.Thickness

    val color: Color
        @Composable get() = DividerDefaults.color

    val Wavelength: Dp = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength

    val Amplitude: Dp = 4.dp
}
