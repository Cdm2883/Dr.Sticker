package vip.cdms.drsticker.services.picker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.ui.components.StickerResourcePreview
import vip.cdms.drsticker.ui.utils.thenIf
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerPickerSheet(
    viewModel: StickerPickerSheetModel,
) = Box(Modifier.fillMaxSize()) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val screenHeightPx = LocalResources.current.displayMetrics.heightPixels.toFloat()
    val collapsedHeightPx = screenHeightPx * 0.6f
    val expandedHeightPx = screenHeightPx * 0.9f

    val sheetHeight = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        sheetHeight.animateTo(
            targetValue = collapsedHeightPx,
            animationSpec = tween(150, easing = FastOutSlowInEasing),
        )
    }

    fun dismiss() = scope.launch {
        sheetHeight.animateTo(0f, tween(200))
        viewModel.close()
    }

    fun collapse() = scope.launch {
        sheetHeight.animateTo(
            collapsedHeightPx,
            tween(250, easing = FastOutSlowInEasing),
        )
    }

    fun expand() = scope.launch {
        sheetHeight.animateTo(
            expandedHeightPx,
            tween(250, easing = FastOutSlowInEasing),
        )
    }

    val currentHeightPx = sheetHeight.value
    Spacer(
        Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = (currentHeightPx / expandedHeightPx).coerceIn(0f, 0.5f)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::dismiss,
            )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (screenHeightPx - currentHeightPx).roundToInt()) }
            .height(with(density) { currentHeightPx.toDp() })
            .shadow(16.dp)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(screenHeightPx) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            sheetHeight.value < screenHeightPx * 0.35f -> dismiss()
                            sheetHeight.value < screenHeightPx * 0.75f -> collapse()
                            else -> expand()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            sheetHeight.snapTo(
                                (sheetHeight.value - dragAmount).coerceIn(50f, expandedHeightPx)
                            )
                        }
                    },
                )
            }
            .clickable(enabled = false) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
//        BottomSheetDefaults.DragHandle()
        StickerPickerSheetContent(viewModel)
    }
}

@Composable
private fun StickerPickerSheetContent(
    viewModel: StickerPickerSheetModel,
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rowState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // FIXME: restoring the first sticker set header anchor to the wrong grid item.
    LaunchedEffect(state.indexEntries) {
        val anchor = viewModel.getSavedGridAnchor()
        if (anchor == null) {
            rowState.scrollToItem(0)
            gridState.scrollToItem(0)
        } else {
            val isPrepared = viewModel.prepareGridThrough(anchor.setId)
            val itemIndex = if (isPrepared) {
                viewModel.state.value.gridItemIndex(anchor.key)
                    ?: viewModel.state.value.gridItemIndex(gridHeaderKey(anchor.setId))
            } else null
            if (itemIndex == null) {
                rowState.scrollToItem(0)
                gridState.scrollToItem(0)
            } else {
                snapshotFlow { gridState.layoutInfo.totalItemsCount > itemIndex }
                    .first { it }
                gridState.scrollToItem(itemIndex, anchor.scrollOffset)
            }
        }
    }

    if (state.indexEntries.isEmpty())
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No sticker sets",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    else {
        StickerSetRow(
            state = state,
            rowState = rowState,
            viewModel = viewModel,
            onStickerSetClick = { setId ->
                scope.launch {
                    if (!viewModel.prepareGridThrough(setId)) return@launch
                    val itemIndex = viewModel.state.value
                        .gridItemIndex(gridHeaderKey(setId)) ?: return@launch
                    gridState.animateScrollToItem(itemIndex)
                }
            },
        )
        HorizontalDivider()
        StickerGrid(
            state = state,
            gridState = gridState,
            rowState = rowState,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun StickerSetRow(
    state: StickerPickerSheetState,
    rowState: LazyListState,
    viewModel: StickerPickerSheetModel,
    onStickerSetClick: (StickerSetId) -> Unit,
) {
    val entries = state.indexEntries
    val itemSize = 40.dp
    val spacing = 4.dp
    val maxGridHeight = (itemSize * 4) + (spacing * 3)
    val catalogState = rememberLazyGridState()

    var isSharedElementsEnabled by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val expansionTransition = updateTransition(isExpanded)
    fun getValidStickerIndex(currentIndex: Int): Int {
        if (entries.isEmpty()) return 0
        val safeIndex = currentIndex.coerceIn(0, entries.lastIndex)
        return if (entries[safeIndex] is StickerPickerIndexEntry.Divider) {
            if (safeIndex + 1 < entries.size) safeIndex + 1
            else (safeIndex - 1).coerceAtLeast(0)
        } else {
            safeIndex
        }
    }
    LaunchedEffect(isSharedElementsEnabled) {
        if (isSharedElementsEnabled) {
            val fromExpanded = isExpanded
            val targetIndex = if (fromExpanded)
                getValidStickerIndex(catalogState.firstVisibleItemIndex)
            else
                getValidStickerIndex(rowState.firstVisibleItemIndex)
            val targetOffset = if (fromExpanded)
                catalogState.firstVisibleItemScrollOffset
            else
                rowState.firstVisibleItemScrollOffset
            isExpanded = !isExpanded
            launch {
                if (fromExpanded) rowState.scrollToItem(targetIndex, targetOffset)
                else catalogState.scrollToItem(targetIndex, targetOffset)
            }
        }
    }
    LaunchedEffect(expansionTransition.targetState) {
        if (!isSharedElementsEnabled) return@LaunchedEffect
        snapshotFlow { expansionTransition.currentState == expansionTransition.targetState }
            .first { it }
        isSharedElementsEnabled = false
    }

    LaunchedEffect(rowState, entries) {
        snapshotFlow {
            preloadSetIds(
                visibleIndexes = rowState.layoutInfo.visibleItemsInfo.map { it.index },
                entries = entries,
            )
        }.distinctUntilChanged().collect(viewModel::requestStickerSets)
    }
    LaunchedEffect(catalogState, entries) {
        snapshotFlow {
            preloadSetIds(
                visibleIndexes = catalogState.layoutInfo.visibleItemsInfo.map { it.index },
                entries = entries,
            )
        }.distinctUntilChanged().collect(viewModel::requestStickerSets)
    }

    Box(Modifier.fillMaxWidth()) {
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            expansionTransition.AnimatedContent(
                modifier = Modifier.fillMaxWidth(),
            ) { expanded ->
                if (expanded) LazyVerticalGrid(
                    state = catalogState,
                    userScrollEnabled = !isSharedElementsEnabled,
                    columns = GridCells.Adaptive(itemSize),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 16.dp - spacing / 2,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.heightIn(max = maxGridHeight),
                ) {
                    items(
                        items = entries,
                        key = StickerPickerIndexEntry::key,
                        span = { entry ->
                            when (entry) {
                                is StickerPickerIndexEntry.StickerSet -> GridItemSpan(1)
                                is StickerPickerIndexEntry.Divider -> GridItemSpan(maxLineSpan)
                            }
                        },
                    ) { entry ->
                        val sharedModifier = Modifier.thenIf(isSharedElementsEnabled) {
                            sharedBounds(
                                sharedContentState = rememberSharedContentState("item_${entry.key}"),
                                animatedVisibilityScope = this@AnimatedContent,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                            )
                        }
                        when (entry) {
                            is StickerPickerIndexEntry.StickerSet -> StickerSetRowItem(
                                setId = entry.setId,
                                setState = state.setStates[entry.setId],
                                modifier = Modifier
                                    .then(sharedModifier)
                                    .padding(vertical = spacing / 2)
                                    .aspectRatio(1f),
                                onClick = { onStickerSetClick(entry.setId) },
                            )

                            is StickerPickerIndexEntry.Divider -> Unit
                        }
                    }
                } else LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        start = 12.dp,
                        bottom = 16.dp,
                        end = 12.dp + itemSize + spacing,
                    ),
                    userScrollEnabled = !isSharedElementsEnabled,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(entries, key = StickerPickerIndexEntry::key) { entry ->
                        val sharedModifier = Modifier.thenIf(isSharedElementsEnabled) {
                            sharedBounds(
                                sharedContentState = rememberSharedContentState("item_${entry.key}"),
                                animatedVisibilityScope = this@AnimatedContent,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                            )
                        }
                        when (entry) {
                            is StickerPickerIndexEntry.StickerSet -> StickerSetRowItem(
                                setId = entry.setId,
                                setState = state.setStates[entry.setId],
                                modifier = Modifier
                                    .then(sharedModifier)
                                    .size(itemSize),
                                onClick = { onStickerSetClick(entry.setId) },
                            )

                            is StickerPickerIndexEntry.Divider -> VerticalDivider(
                                modifier = Modifier
                                    .then(sharedModifier)
                                    .height(itemSize - 16.dp),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 12.dp)
                .size(itemSize)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondary)
                .clickable(enabled = !isSharedElementsEnabled) {
                    isSharedElementsEnabled = true
                },
            contentAlignment = Alignment.Center,
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                tint = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}

@Composable
private fun StickerSetRowItem(
    setId: StickerSetId,
    setState: StickerPickerSetState?,
    modifier: Modifier,
    onClick: () -> Unit,
) = Box(
    modifier = modifier
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    when (setState) {
        is StickerPickerSetState.Loaded -> StickerResourcePreview(
            resource = setState.thumbnail,
            setId = setId,
            stickerId = null,
            modifier = Modifier.fillMaxSize(),
        )

        else -> Unit
    }
}

private const val LOADING_TAIL_KEY = "grid:loading-tail"

private fun gridHeaderKey(setId: StickerSetId) = "grid:header:$setId"
private fun gridStickerKey(sticker: StickerPickerItem) =
    "grid:sticker:${sticker.setId.length}:${sticker.setId}:${sticker.stickerId}"

private fun gridDividerKey(ordinal: Int) = "grid:divider:$ordinal"

private fun gridSetId(key: String): StickerSetId? {
    if (key.startsWith("grid:header:")) return key.removePrefix("grid:header:")
    if (!key.startsWith("grid:sticker:")) return null

    val encoded = key.removePrefix("grid:sticker:")
    val separator = encoded.indexOf(':')
    if (separator <= 0) return null
    val setIdLength = encoded.substring(0, separator).toIntOrNull() ?: return null
    val setIdStart = separator + 1
    val setIdEnd = setIdStart + setIdLength
    if (setIdLength < 0 || setIdEnd > encoded.length) return null
    return encoded.substring(setIdStart, setIdEnd)
}

@Composable
private fun StickerGrid(
    state: StickerPickerSheetState,
    gridState: LazyGridState,
    rowState: LazyListState,
    viewModel: StickerPickerSheetModel,
) {
    val stableEntries = state.indexEntries.take(state.stableGridEntryCount)
    DisposableEffect(gridState) {
        onDispose {
            gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                val key = item.key as? String ?: return@firstNotNullOfOrNull null
                val setId = gridSetId(key) ?: return@firstNotNullOfOrNull null
                Triple(key, setId, (-item.offset.y).coerceAtLeast(0))
            }?.let { (key, setId, scrollOffset) ->
                viewModel.saveGridAnchor(
                    key = key,
                    setId = setId,
                    scrollOffset = scrollOffset,
                )
            }
        }
    }
    LaunchedEffect(gridState, state.indexEntries) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                (item.key as? String)?.let(::gridSetId)
            }
        }.distinctUntilChanged().collect { setId ->
            val index = state.indexEntries.indexOfFirst {
                it is StickerPickerIndexEntry.StickerSet && it.setId == setId
            }
            if (index >= 0 && rowState.firstVisibleItemIndex != index) {
                rowState.animateScrollToItem(index)
            }
        }
    }

    LaunchedEffect(gridState, state.stableGridEntryCount, state.indexEntries) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.any {
                it.key == LOADING_TAIL_KEY
            }
        }.distinctUntilChanged().collect { loadingTailVisible ->
            if (!loadingTailVisible) return@collect
            state.indexEntries.drop(state.stableGridEntryCount)
                .mapNotNull { (it as? StickerPickerIndexEntry.StickerSet)?.setId }
                .take(2)
                .let(viewModel::requestStickerSets)
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        stableEntries.forEach { entry ->
            when (entry) {
                is StickerPickerIndexEntry.Divider -> item(
                    key = gridDividerKey(entry.ordinal),
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .padding(top = 8.dp)
                    )
                }

                is StickerPickerIndexEntry.StickerSet -> when (
                    val setState = state.setStates[entry.setId]
                ) {
                    is StickerPickerSetState.Loaded -> {
                        item(
                            key = gridHeaderKey(entry.setId),
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Text(
                                text = setState.displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(
                            items = setState.stickers,
                            key = ::gridStickerKey,
                        ) { sticker ->
                            StickerResourcePreview(
                                resource = sticker.thumbnail ?: sticker.resource,
                                setId = sticker.setId,
                                stickerId = sticker.stickerId,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { viewModel.selectSticker(sticker) },
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }

        if (state.stableGridEntryCount < state.indexEntries.size) item(
            key = LOADING_TAIL_KEY,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
    }
}

private fun StickerPickerSheetState.gridItemIndex(key: String): Int? {
    var itemIndex = 0
    for (entry in indexEntries.take(stableGridEntryCount)) {
        when (entry) {
            is StickerPickerIndexEntry.Divider -> {
                if (gridDividerKey(entry.ordinal) == key) return itemIndex
                itemIndex++
            }

            is StickerPickerIndexEntry.StickerSet -> when (val setState = setStates[entry.setId]) {
                is StickerPickerSetState.Loaded -> {
                    if (gridHeaderKey(entry.setId) == key) return itemIndex
                    itemIndex++
                    setState.stickers.forEach { sticker ->
                        if (gridStickerKey(sticker) == key) return itemIndex
                        itemIndex++
                    }
                }

                else -> return null
            }
        }
    }
    return null
}

private fun preloadSetIds(
    visibleIndexes: List<Int>,
    entries: List<StickerPickerIndexEntry>,
): List<StickerSetId> {
    if (visibleIndexes.isEmpty() || entries.isEmpty()) return emptyList()
    val extra = visibleIndexes.size
    val start = (visibleIndexes.min() - extra).coerceAtLeast(0)
    val end = (visibleIndexes.max() + extra).coerceAtMost(entries.lastIndex)
    return entries.subList(start, end + 1)
        .mapNotNull { (it as? StickerPickerIndexEntry.StickerSet)?.setId }
}
