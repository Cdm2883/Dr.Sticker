package vip.cdms.drsticker.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.data.SortStrategy
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.ui.components.CombinedClickableIconButton
import vip.cdms.drsticker.ui.components.SortIconButton
import vip.cdms.drsticker.ui.models.StickerSetDetailModel
import vip.cdms.drsticker.ui.utils.rememberDisabledTopOverscrollEffect
import vip.cdms.drsticker.ui.utils.thenIf

@Serializable
data class StickerSetDetailRoute(val setId: StickerSetId)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StickerSetDetail(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: StickerSetDetailModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    var titleValue by remember { mutableStateOf("Sticker Pack Name") }
    var subtitleValue by remember { mutableStateOf("Description text") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val density = LocalDensity.current
    val barCollapsedHeight = 64.dp
    val barMaxExpandedHeight = 232.dp
    val barHeightOffsetLimitPx = with(density) { (barCollapsedHeight - barMaxExpandedHeight).toPx() }
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != barHeightOffsetLimitPx) {
            scrollBehavior.state.heightOffsetLimit = barHeightOffsetLimitPx
        }
    }

    var isManualSorting by remember { mutableStateOf(false) }
    var overrideSortStrategy by remember { mutableStateOf<SortStrategy?>(null) }
    var globalSortStrategy by remember { mutableStateOf(SortStrategy.MANUAL) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val collapseFraction = scrollBehavior.state.collapsedFraction
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .run {
                        with(sharedTransitionScope) {
                            sharedBounds(
                                rememberSharedContentState(key = "bound_" + viewModel.setId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    },
                color = lerp(
                    start = MaterialTheme.colorScheme.surface,
                    stop = MaterialTheme.colorScheme.surfaceContainer,
                    fraction = FastOutLinearInEasing.transform(collapseFraction)
                )
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                        .fillMaxWidth()
                        .height(barMaxExpandedHeight + with(density) { scrollBehavior.state.heightOffset.toDp() })
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barCollapsedHeight)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            titleValue,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).alpha(collapseFraction)
                                .thenIf(collapseFraction >= 0.05f) {
                                    with(sharedTransitionScope) {
                                        sharedBounds(
                                            rememberSharedContentState(key = "title_" + viewModel.setId),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                }
                        )
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            var openResetDialog by remember { mutableStateOf(false) }
                            var openDeleteDialog by remember { mutableStateOf(false) }

                            SortIconButton(
                                sortStrategy = overrideSortStrategy ?: globalSortStrategy,
                                isManualSorting = isManualSorting,
                                onRequestManualSorting = { isManualSorting = true },
                                onSorted = {
                                    isManualSorting = false
                                    if (overrideSortStrategy == null) globalSortStrategy = it
                                    else overrideSortStrategy = it
                                    println("Override SortStrategy: $overrideSortStrategy")
                                    println("Global SortStrategy: $globalSortStrategy")
                                },
                                hasGlobal = true,
                                isGlobal = overrideSortStrategy == null,
                                onUseGlobal = {
                                    overrideSortStrategy =
                                        if (it) null
                                        else globalSortStrategy
                                }
                            )
                            CombinedClickableIconButton(
                                onClick = { /* TODO */ },
                                onLongClick = { openResetDialog = true },
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                            }
                            IconButton(onClick = { openDeleteDialog = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = null)
                            }

                            if (openResetDialog) AlertDialog(
                                onDismissRequest = {
                                    openResetDialog = false
                                },
                                title = { Text("Reset all overrides?") },
                                text = { Text("This will return the sticker set to the same state as the source, all custom names, descriptions, sorting, tags, etc. will be erased") },
                                confirmButton = {
                                    TextButton(onClick = { openResetDialog = false; /* TODO */ }) {
                                        Text(
                                            "Reset",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { openResetDialog = false }) { Text("Cancel") }
                                },
                            )

                            if (openDeleteDialog) AlertDialog(
                                onDismissRequest = {
                                    openDeleteDialog = false
                                },
                                title = { Text("Delete the sticker set?") },
                                text = { Text("All related configurations and caches will be permanently removed from source and your device") },
                                confirmButton = {
                                    TextButton(onClick = { openDeleteDialog = false; /* TODO */ }) {
                                        Text(
                                            "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { openDeleteDialog = false }) { Text("Cancel") }
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-8).dp)
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .alpha(1f - collapseFraction)
                            .graphicsLayer {
                                translationY = -(collapseFraction * 50f)
                            },
                        verticalAlignment = Alignment.Top
                    ) {
                        @SuppressLint("ConfigurationScreenWidthHeight")
                        Spacer(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(((LocalConfiguration.current.screenWidthDp - 8 * (4 - 1) - 16 * 2) / 4).dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                )
                                .run {
                                    with(sharedTransitionScope) {
                                        sharedBounds(
                                            rememberSharedContentState(key = "cover_" + viewModel.setId),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                },
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = titleValue,
                                onValueChange = { titleValue = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                enabled = collapseFraction < 0.05f,
                                modifier = Modifier.fillMaxWidth()
                                    .thenIf(collapseFraction < 0.05f) {
                                        with(sharedTransitionScope) {
                                            sharedBounds(
                                                rememberSharedContentState(key = "title_" + viewModel.setId),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            )
                                        }
                                    }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = subtitleValue,
                                onValueChange = { subtitleValue = it },
                                label = { Text("Description") },
                                singleLine = false,
                                minLines = 2,
                                enabled = collapseFraction < 0.05f,
                                modifier = Modifier.fillMaxWidth()
                                    .run {
                                        with(sharedTransitionScope) {
                                            sharedBounds(
                                                rememberSharedContentState(key = "desc_" + viewModel.setId),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .padding(innerPadding)
                .run {
                    with(animatedVisibilityScope) {
                        animateEnterExit(
                            enter = slideInVertically(),
//                            exit = slideOutVertically()
                        )
                    }
                },
            contentPadding = PaddingValues(horizontal = 16.dp) +
                    PaddingValues(top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = rememberDisabledTopOverscrollEffect(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            }

            val list = (0..75).map { it.toString() }
            items(count = list.size) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}
