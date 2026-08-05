package vip.cdms.drsticker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import vip.cdms.drsticker.data.*
import vip.cdms.drsticker.ui.components.*
import vip.cdms.drsticker.ui.models.*
import vip.cdms.drsticker.ui.theme.darkTheme
import vip.cdms.drsticker.ui.utils.rememberDisabledTopOverscrollEffect
import vip.cdms.drsticker.ui.utils.thenIf
import vip.cdms.drsticker.utils.vibrate

@Serializable
object StickersRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StickersPage(
    viewModel: StickersPageModel = hiltViewModel(),
    onStickerSetDetail: (StickerSetId) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.index, to.index)
    }

    BackHandler(enabled = state.isManualSorting) {
        viewModel.cancelManualSorting()
    }

//    TODO:
//    LifecycleResumeEffect(Unit) {
//        viewModel.reload()
//        onPauseOrDispose {}
//    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val stickerSetCount = state.entries.count { it is StickerSetListEntry.StickerSet }

            LargeFlexibleTopAppBar(
                title = { Text("Stickers", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text("$stickerSetCount installed.", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    SortIconButton(
                        sortStrategy = state.sortStrategy,
                        isManualSorting = state.isManualSorting,
                        onRequestManualSorting = viewModel::beginManualSorting,
                        onSorted = {
                            if (state.isManualSorting) viewModel.finishManualSorting()
                            else viewModel.selectSortStrategy(it)
                        },
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults
                            .rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = {
                            PlainTooltip {
                                Text("Add sticker set")
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = viewModel::openStickerSetPicker) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 200.dp),
            overscrollEffect = rememberDisabledTopOverscrollEffect(),
        ) {
            if (state.entries.isEmpty()) item {
                Text(
                    "There are no stickers installed yet, go ahead and add one!",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
            }

            itemsIndexed(items = state.entries, key = { _, entry -> entry.key }) { index, entry ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = entry.key,
                ) {
                    val context = LocalContext.current
                    if (entry is StickerSetListEntry.Divider)
                        return@ReorderableItem HorizontalWavyDivider(
                            modifier = if (state.isManualSorting) Modifier
                                .draggableHandle()
                            else Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = {
                                        viewModel.remove(index)
                                        vibrate(context)
                                    }
                                ) {}
                        )

                    if (entry is StickerSetListEntry.LoadError)
                        return@ReorderableItem StickerLoadErrorListItem(
                            entry = entry,
                            isSorting = state.isManualSorting,
                            onRemove = { viewModel.remove(index) },
                        )

                    if (entry !is StickerSetListEntry.StickerSet)
                        throw IllegalStateException()

                    val hasDividerAbove = index == 0
                            || state.entries[index - 1] is StickerSetListEntry.Divider
                    val hasDividerBelow = index == state.entries.lastIndex
                            || state.entries[index + 1] is StickerSetListEntry.Divider
                    var pendingDividerIndex by remember { mutableStateOf<Int?>(null) }
                    StickerListItem(
                        entry = entry,
                        modifier = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "bound_" + entry.setId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        modifierCover = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "cover_" + entry.setId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        modifierTitle = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "title_" + entry.setId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        modifierDescription = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "desc_" + entry.setId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        isSorting = state.isManualSorting,
                        onClick = {
                            onStickerSetDetail(entry.setId)
                        },
                        onDividerAbove = if (hasDividerAbove) null else ({
                            if (state.sortStrategy != SortStrategy.MANUAL) pendingDividerIndex = index
                            else viewModel.insertDivider(index)
                        }),
                        onDividerBelow = if (hasDividerBelow) null else ({
                            if (state.sortStrategy != SortStrategy.MANUAL) pendingDividerIndex = index + 1
                            else viewModel.insertDivider(index + 1)
                        }),
                        onEdit = { viewModel.openEditStickerSetConfig(entry.setId) },
                        onRemove = { viewModel.remove(index) },
                    )

                    pendingDividerIndex?.let { index ->
                        AlertDialog(
                            onDismissRequest = {
                                pendingDividerIndex = null
                            },
                            title = { Text("Insert divider?") },
                            text = { Text("This will save the currently displayed order as the base order before inserting the divider.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingDividerIndex = null
                                    viewModel.insertDivider(index)
                                }) {
                                    Text("Insert", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingDividerIndex = null }) { Text("Cancel") }
                            },
                        )
                    }
                }
            }
        }
    }

    PickStickerSetDialog(
        state = state.pickerState,
        onDismissRequest = viewModel::closeStickerSetPicker,
        onAddRequest = { source ->
            viewModel.closeStickerSetPicker()
            viewModel.openAddStickerSetConfig(source.key)
        },
        onRestore = viewModel::restoreStickerSet,
        onDelete = viewModel::deleteDetachedStickerSet,
    )

    StickerSetConfigDialog(
        state = state.configState,
        metadataProvider = viewModel::getSourceMetadata,
        onDismissRequest = viewModel::closeStickerSetConfig,
        onAdd = viewModel::addStickerSet,
        onUpdate = viewModel::updateStickerSet,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderableCollectionItemScope.StickerListItem(
    entry: StickerSetListEntry.StickerSet,
    modifier: Modifier,
    modifierCover: Modifier,
    modifierTitle: Modifier,
    modifierDescription: Modifier,
    isSorting: Boolean,
    onClick: () -> Unit,
    onDividerAbove: (() -> Unit)?,
    onDividerBelow: (() -> Unit)?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) = ListItem(
    modifier = modifier.thenIf(!isSorting) {
        horizontalWavyRipple(
            hasDividerAbove = onDividerAbove == null,
            hasDividerBelow = onDividerBelow == null,
            onClick = onClick
        )
    }
        .height(IntrinsicSize.Min),
    leadingContent = {
        StickerResourcePreview(
            resource = entry.thumbnail,
            setId = entry.setId,
            stickerId = null,
            modifier = modifierCover
                .fillMaxHeight()
                .padding(bottom = 2.dp)
                .width(64.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (MaterialTheme.darkTheme)
                        MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surfaceDim
                ),
        )
    },
    overlineContent = {
        val metadataText = "${entry.sourceName} / ${entry.stickerCount}"
        Text(metadataText)
    },
    headlineContent = {
        Text(
            entry.displayName,
            modifier = modifierTitle
                .thenIf(entry.description == null) { padding(bottom = 6.dp) }
                .thenIf(entry.description != null) { basicMarquee() },
            lineHeight = 1.2.em,
            maxLines = if (entry.description == null) 2 else 1,
        )
    },
    supportingContent = entry.description?.let { { Text(it, modifierDescription) } },
    trailingContent = {
        var menuExpanded by remember { mutableStateOf(false) }
        IconButton(
            onClick = {
                if (isSorting) return@IconButton
                menuExpanded = true
            },
            modifier = Modifier
                .then(if (isSorting) Modifier.draggableHandle() else Modifier),
        ) {
            Icon(
                if (isSorting) Icons.Rounded.DragIndicator else Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = MaterialTheme.shapes.small,
        ) {
            onDividerAbove?.let {
                DropdownMenuItem(
                    text = { Text("Divider above", modifier = Modifier.padding(end = 6.dp)) },
                    onClick = { menuExpanded = false; it() },
                    leadingIcon = { Icon(Icons.Rounded.VerticalAlignTop, contentDescription = null) },
                )
            }
            onDividerBelow?.let {
                DropdownMenuItem(
                    text = { Text("Divider below", modifier = Modifier.padding(end = 6.dp)) },
                    onClick = { menuExpanded = false; it() },
                    leadingIcon = { Icon(Icons.Rounded.VerticalAlignBottom, contentDescription = null) },
                )
            }
            DropdownMenuItem(
                text = { Text("Edit", modifier = Modifier.padding(end = 6.dp)) },
                onClick = { menuExpanded = false; onEdit() },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Remove", modifier = Modifier.padding(end = 6.dp)) },
                onClick = { menuExpanded = false; onRemove() },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircle, contentDescription = null) },
            )
        }
    }
)

@Composable
fun ReorderableCollectionItemScope.StickerLoadErrorListItem(
    entry: StickerSetListEntry.LoadError,
    isSorting: Boolean,
    onRemove: () -> Unit,
) = ListItem(
    headlineContent = {
        Text(
            text = "Unable to load sticker set: ${entry.setId}",
            color = MaterialTheme.colorScheme.error,
        )
    },
    supportingContent = {
        Text(entry.error.readableMessage())
    },
    trailingContent = {
        IconButton(
            onClick = {
                if (isSorting) return@IconButton
                onRemove()
            },
            modifier = Modifier
                .then(if (isSorting) Modifier.draggableHandle() else Modifier),
        ) {
            Icon(
                if (isSorting) Icons.Rounded.DragIndicator else Icons.Rounded.Remove,
                contentDescription = null
            )
        }
    }
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PickStickerSetDialog(
    state: StickerSetPickerState,
    onDismissRequest: () -> Unit,
    onAddRequest: (StickerSourceOption) -> Unit,
    onRestore: (StickerSetId) -> Unit,
    onDelete: (StickerSetId) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val hiddenDetachedIds = remember { mutableStateListOf<StickerSetId>() }
    val pendingDeleteJobs = remember { mutableMapOf<StickerSetId, Job>() }
    val deleteRequestedIds = remember { mutableSetOf<StickerSetId>() }
    val openState = state as? StickerSetPickerState.Open

    FullScreenDialog(
        visible = openState != null,
        onDismissRequest = onDismissRequest,
        title = { Text("Add sticker set to top") },
    ) { innerPadding ->
        LaunchedEffect(openState) {
            if (openState == null) {
                val pendingIds = hiddenDetachedIds.filterNot(deleteRequestedIds::contains)

                pendingDeleteJobs.values
                    .toList()
                    .forEach { it.cancel() }
                pendingDeleteJobs.clear()
                deleteRequestedIds.clear()

                snackbarHostState.currentSnackbarData?.dismiss()
                hiddenDetachedIds.clear()

                pendingIds.forEach(onDelete)
            }
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 200.dp),
            overscrollEffect = rememberDisabledTopOverscrollEffect(),
        ) {
            item {
                Text(
                    "New source",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            items(
                items = openState?.sourceOptions.orEmpty(),
                key = StickerSourceOption::key,
            ) { source ->
                ListItem(
                    modifier = Modifier
                        .clickable { onAddRequest(source) }
                        .padding(horizontal = 4.dp),
                    headlineContent = { Text(source.displayName) },
                    supportingContent = { Text(source.description) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowRight, contentDescription = null)
                    },
                )
            }

            val detachedEntries = openState?.detachedEntries.orEmpty()
            if (detachedEntries.isNotEmpty()) item {
                Text(
                    "From cache",
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            items(
                items = detachedEntries,
                key = StickerSetListEntry::key,
            ) { entry ->
                val dismissState = rememberSwipeToDismissBoxState()
                val isHidden = entry.key in hiddenDetachedIds
                LaunchedEffect(dismissState.currentValue, isHidden) {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.EndToStart)
                        return@LaunchedEffect
                    if (isHidden) return@LaunchedEffect
                    hiddenDetachedIds.add(entry.key)
                    pendingDeleteJobs[entry.key] = coroutineScope.launch {
                        try {
                            val result = snackbarHostState.showSnackbar(
                                message = "Sticker set deleted from cache",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long,
                            )

                            if (result == SnackbarResult.ActionPerformed) {
                                dismissState.reset()
                                hiddenDetachedIds.remove(entry.key)
                            } else if (entry.key in hiddenDetachedIds
                                && deleteRequestedIds.add(entry.key)
                            ) {
                                onDelete(entry.key)
                            }
                        } finally {
                            pendingDeleteJobs.remove(entry.key)
                        }
                    }
                }
                if (isHidden) return@items

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 4.dp)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false,
                ) {
                    if (entry is StickerSetListEntry.LoadError)
                        return@SwipeToDismissBox ListItem(
                            modifier = Modifier.padding(start = 4.dp),
                            headlineContent = {
                                Text(
                                    text = "Unable to load sticker set: ${entry.setId}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            supportingContent = {
                                Text(entry.error.readableMessage())
                            },
                        )

                    if (entry !is StickerSetListEntry.StickerSet)
                        throw IllegalStateException()
                    ListItem(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .clickable { onRestore(entry.setId) }
                            .padding(start = 4.dp),
                        headlineContent = {
                            Text(
                                entry.displayName,
                                modifier = Modifier.basicMarquee(),
                                maxLines = 1,
                            )
                        },
                        supportingContent = {
                            Text(entry.description ?: "${entry.sourceName} / ${entry.stickerCount}")
                        },
                        trailingContent = {
                            StickerResourcePreview(
                                setId = entry.setId,
                                stickerId = null,
                                resource = entry.thumbnail,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(bottom = 2.dp)
                                    .width(56.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (MaterialTheme.darkTheme)
                                            MaterialTheme.colorScheme.surfaceBright
                                        else MaterialTheme.colorScheme.surfaceDim
                                    ),
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StickerSetConfigDialog(
    state: StickerSetConfigState,
    metadataProvider: (String) -> StickerSourceMetadata<*>,
    onDismissRequest: () -> Unit,
    onAdd: (StickerSourceConfig, StickerSetOverrides) -> Unit,
    onUpdate: (StickerSetId, StickerSourceConfig, StickerSetOverrides) -> Unit,
) {
    val formKey = when (state) {  // compose key
        is StickerSetConfigState.Add -> "add:${state.sourceKey}"
        is StickerSetConfigState.Edit -> "edit:${state.setId}"
        StickerSetConfigState.Closed -> null
    }

    val addState = state as? StickerSetConfigState.Add
    val editState = state as? StickerSetConfigState.Edit
    val sourceConfigScope = key(formKey) {
        rememberSourceConfigScope(editState?.source, addState?.envProvider)
    }

    val initialOverrides = editState?.overrides ?: StickerSetOverrides()
    var displayName by remember(formKey) {
        mutableStateOf(initialOverrides.displayName.orEmpty())
    }
    var description by remember(formKey) {
        mutableStateOf(initialOverrides.description.orEmpty())
    }

    val saveState = when (state) {
        is StickerSetConfigState.Add -> state.saveState
        is StickerSetConfigState.Edit -> state.saveState
        StickerSetConfigState.Closed -> StickerSetConfigSaveState.Idle
    }

    FullScreenDialog(
        visible = state !is StickerSetConfigState.Closed,
        onDismissRequest = {
            if (saveState !is StickerSetConfigSaveState.Saving)
                onDismissRequest()
        },
        title = {
            val title = if (addState != null) "Add sticker set"
            else if (editState != null) "Edit sticker set"
            else return@FullScreenDialog
            Text(
                title,
                modifier = Modifier.basicMarquee(),
                maxLines = 1,
            )
        },
        actions = {
            TextButton(
                enabled = state !is StickerSetConfigState.Closed
                        && saveState !is StickerSetConfigSaveState.Saving,
                onClick = {
                    val source = try {
                        sourceConfigScope.submit()
                    } catch (_: SourceConfigValidationException) {
                        return@TextButton
                    }
                    val overrides = initialOverrides.copy(
                        displayName = displayName.ifBlank { null },
                        description = description.ifBlank { null },
                    )
                    if (editState == null) {
                        onAdd(source, overrides)
                    } else {
                        onUpdate(editState.setId, source, overrides)
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Save")
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (saveState is StickerSetConfigSaveState.Error) Text(
                    text = saveState.error.readableMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name override") },
                    placeholder = editState
                        ?.previousDisplayName
                        ?.let { { Text(it) } },
                    singleLine = true,
                    enabled = saveState !is StickerSetConfigSaveState.Saving,
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description override") },
                    placeholder = editState
                        ?.previousDescription
                        ?.let { previous -> { Text(previous) } },
                    minLines = 2,
                    maxLines = 5,
                    enabled = saveState !is StickerSetConfigSaveState.Saving,
                )

                val sourceMetadata = (addState?.sourceKey ?: editState?.sourceKey)?.let {
                    @Suppress("UNCHECKED_CAST")
                    metadataProvider(it) as StickerSourceMetadata<StickerSourceConfig>
                }
                if (sourceMetadata != null) with(sourceMetadata) {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    sourceConfigScope.Settings()
                }
            }

            // prevent editing
            if (state is StickerSetConfigState.Closed
                || saveState is StickerSetConfigSaveState.Saving
            ) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
        }
    }
}

private fun Throwable.readableMessage() =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "Unknown error"
