package vip.cdms.drsticker.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.negativePadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp
) = layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val topPx = top.roundToPx()
    val endPx = end.roundToPx()
    val bottomPx = bottom.roundToPx()

    val newMinWidth = (constraints.minWidth + startPx + endPx).coerceAtLeast(0)
    val newMaxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + startPx + endPx else constraints.maxWidth

    val newMinHeight = (constraints.minHeight + topPx + bottomPx).coerceAtLeast(0)
    val newMaxHeight =
        if (constraints.hasBoundedHeight) constraints.maxHeight + topPx + bottomPx else constraints.maxHeight

    val placeable = measurable.measure(
        Constraints(minWidth = newMinWidth, maxWidth = newMaxWidth, minHeight = newMinHeight, maxHeight = newMaxHeight)
    )

    val layoutWidth = (placeable.width - startPx - endPx).coerceAtLeast(0)
    val layoutHeight = (placeable.height - topPx - bottomPx).coerceAtLeast(0)
    layout(layoutWidth, layoutHeight) {
        placeable.place(-startPx, -topPx)
    }
}

fun Modifier.negativePadding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp) =
    negativePadding(start = horizontal, top = vertical, end = horizontal, bottom = vertical)

inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier) =
    if (condition) block() else this
