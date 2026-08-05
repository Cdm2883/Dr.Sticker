package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import kotlin.math.max

/**
 * @see androidx.compose.material3.LargeTopAppBar
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StaticLargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    val collapsedHeight = 64.dp
    val totalHeight = if (subtitle != null) 120.0.dp else 152.0.dp
    val titleBottomPadding = 28.dp

    Surface(modifier) {
        Layout(
            content = {
                Box(Modifier.layoutId("actions").padding(end = 4.dp)) {
//                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .layoutId("title")
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    CompositionLocalProvider(
//                        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        LocalContentColor provides colors.titleContentColor,
                        LocalTextStyle provides MaterialTheme.typography.displaySmallEmphasized,
                        content = title
                    )
                    subtitle?.let {
                        CompositionLocalProvider(
//                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                            LocalContentColor provides colors.subtitleContentColor,
                            LocalTextStyle provides MaterialTheme.typography.titleMediumEmphasized,
                            content = it
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
        ) { measurables, constraints ->
            val actionsPlaceable = measurables
                .fastFirst { it.layoutId == "actions" }
                .measure(constraints.copy(minWidth = 0))

            val titlePlaceable = measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0))

            val requestedHeight = totalHeight.roundToPx()
            val minRequiredHeight = collapsedHeight.roundToPx() + titlePlaceable.height
            val finalLayoutHeight = max(requestedHeight, minRequiredHeight)

            layout(constraints.maxWidth, finalLayoutHeight) {
                val actionsY = (collapsedHeight.roundToPx() - actionsPlaceable.height) / 2
                actionsPlaceable.placeRelative(
                    x = constraints.maxWidth - actionsPlaceable.width,
                    y = actionsY
                )

                val titleBaseline =
                    if (titlePlaceable[LastBaseline] != AlignmentLine.Unspecified) {
                        titlePlaceable[LastBaseline]
                    } else {
                        0
                    }
                val expandedBlockHeight = totalHeight - collapsedHeight
                val maxLayoutHeight = max(expandedBlockHeight.roundToPx(), titlePlaceable.height)

                val paddingFromBottom =
                    titleBottomPadding.roundToPx() - (titlePlaceable.height - titleBaseline)
                val heightWithPadding = paddingFromBottom + titlePlaceable.height
                val adjustedBottomPadding =
                    if (heightWithPadding > maxLayoutHeight) {
                        paddingFromBottom - (heightWithPadding - maxLayoutHeight)
                    } else {
                        paddingFromBottom
                    }

                val localTitleY = maxLayoutHeight - titlePlaceable.height - max(0, adjustedBottomPadding)
                val titleY = collapsedHeight.roundToPx() + localTitleY
                titlePlaceable.placeRelative(
                    x = 0,
                    y = max(0, titleY)
                )
            }
        }
    }
}
