package vip.cdms.drsticker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.ui.theme.darkTheme
import vip.cdms.drsticker.ui.utils.negativePadding
import kotlin.random.Random

@Serializable
object DashboardRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp) + PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HorizontalMultiBrowseCarousel(
                state = rememberCarouselState { 6 },
                modifier = Modifier
                    .negativePadding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(top = 32.dp),
                preferredItemWidth = 186.dp,
                itemSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { _ ->
                repeat(6) {
                    Spacer(
                        modifier = Modifier
                            .width(186.dp)
                            .height(205.dp)
                            .maskClip(MaterialTheme.shapes.extraLarge)
                            .background(
                                if (MaterialTheme.darkTheme)
                                    MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surfaceDim
                            )
                    )
                }
            }
        }

        item {
            Text(
                "Show all",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelMediumEmphasized
            )
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Dashboard",
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        modifier = Modifier.alignByBaseline()
                    )

                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).alignByBaseline()
                            )
                        }
                    }
                }

                Text(
                    "Sticker activity in last 22 weeks.",
                    modifier = Modifier.offset(y = (-4).dp),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ActivityCard(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCard(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    // Generate deterministic activity data (22 weeks, 7 days per week)
    val activityData = remember {
        List(22) { week ->
            List(7) { day ->
                val seed = week * 7 + day
                val rand = Random(seed).nextFloat()
                when {
                    rand < 0.45f -> 0
                    rand < 0.75f -> 1
                    rand < 0.88f -> 2
                    rand < 0.96f -> 3
                    else -> 4
                }
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {} // Sunday
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Mon",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                )
            }
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {} // Tuesday
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Wed",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                )
            }
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {} // Thursday
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Fri",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                )
            }
            Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {} // Saturday
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f)
        ) {
            activityData.forEach { weekData ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    weekData.forEach { level ->
                        val cellColor = remember(level, colorScheme) {
                            when (level) {
                                0 -> colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                1 -> colorScheme.primary.copy(alpha = 0.2f)
                                2 -> colorScheme.primary.copy(alpha = 0.45f)
                                3 -> colorScheme.primary.copy(alpha = 0.75f)
                                else -> colorScheme.primary
                            }
                        }
                        TooltipBox(
                            positionProvider =
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Below
                                ),
                            tooltip = {
                                PlainTooltip(
                                    caretShape = TooltipDefaults.caretShape()
                                ) {
                                    Text("1919/08/10 - 114514 times")
                                }
                            },
                            state = rememberTooltipState(isPersistent = true),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cellColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
