package vip.cdms.drsticker.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import vip.cdms.drsticker.rule.RulesetAdapterMetadata
import vip.cdms.drsticker.rule.RulesetId
import vip.cdms.drsticker.rule.RulesetPreprocessMetadata
import vip.cdms.drsticker.rule.RulesetTriggerMetadata
import vip.cdms.drsticker.rule.adapters.RulesetAdapter
import vip.cdms.drsticker.rule.preprocess.RulesetPreprocess
import vip.cdms.drsticker.rule.triggers.RulesetTrigger
import vip.cdms.drsticker.ui.components.TwoRowsDropdownMenuItem
import vip.cdms.drsticker.ui.models.RulesetConfigOption
import vip.cdms.drsticker.ui.models.RulesetDetailModel
import vip.cdms.drsticker.ui.models.RulesetSaveState
import vip.cdms.drsticker.ui.utils.readableMessage
import vip.cdms.drsticker.ui.utils.thenIf
import java.util.*

@Serializable
data class RulesetEditRoute(val rulesetId: RulesetId)

@Serializable
data class RulesetAddRoute(val rulesetId: RulesetId? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesetDetail(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    viewModel: RulesetDetailModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var openDiscardDialog by remember { mutableStateOf(false) }
    var openDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveState) {
        when (val saveState = state.saveState) {
            is RulesetSaveState.Failed -> snackbarHostState.showSnackbar(
                message = saveState.error.readableMessage(),
            )

            is RulesetSaveState.Saved -> onBack()
            RulesetSaveState.Idle,
            RulesetSaveState.Saving -> Unit
        }
    }
    fun requestExit() = when {
        state.saveState is RulesetSaveState.Saving -> Unit
        state.isDirty -> openDiscardDialog = true
        else -> onBack()
    }
    BackHandler { requestExit() }

    val tabs = listOf("Information", "Condition", "Trigger", "Preprocesses", "Adapter")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val backgroundColor = lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainer,
                scrollBehavior.state.collapsedFraction
            )
            Column(
                modifier = Modifier.background(backgroundColor)
                    .thenIf(sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope!!) {
                            sharedBounds(
                                rememberSharedContentState(key = "r_bound_" + viewModel.rulesetId),
                                animatedVisibilityScope = animatedVisibilityScope!!,
                            )
                        }
                    }
            ) {
                TopAppBar(
                    title = {
                        Text(
                            state.displayName
                                ?.takeIf { it.isNotBlank() }
                                ?: if (viewModel.isAdding) "New ruleset" else "...",
                            modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null)
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = "r_title_" + viewModel.rulesetId),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                }
                            else Modifier,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = ::requestExit,
                            enabled = state.saveState !is RulesetSaveState.Saving,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (!viewModel.isAdding) IconButton(
                            onClick = { openDeleteDialog = true },
                            enabled = state.saveState !is RulesetSaveState.Saving,
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        }
                        IconButton(
                            onClick = { coroutineScope.launch { viewModel.save() } },
                            enabled = state.isDirty && state.saveState !is RulesetSaveState.Saving,
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                        }
                    },
//                    colors = TopAppBarDefaults.topAppBarColors(
//                        containerColor = MaterialTheme.colorScheme.surface,
//                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
//                    ),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    edgePadding = 20.dp,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .thenIf(animatedVisibilityScope != null) {
                        with(animatedVisibilityScope!!) {
                            animateEnterExit(
                                enter = slideInVertically(),
                                exit = fadeOut() + slideOutVertically { -it / 8 },
                            )
                        }
                    },
            ) { page ->
                when (page) {
                    0 -> InformationTabContent(
                        displayName = state.displayName,
                        description = state.description,
                        enabled = state.saveState !is RulesetSaveState.Saving,
                        onDisplayNameChange = viewModel::updateDisplayName,
                        onDescriptionChange = viewModel::updateDescription,
                    )

                    1 -> ConditionTabContent(
                        condition = state.condition,
                        options = viewModel.conditionOptions,
                        metadataProvider = viewModel::getConditionMetadata,
                        conditionFactory = viewModel::createCondition,
                        onConditionChange = viewModel::updateCondition,
                    )

                    2 -> TriggerTabContent(
                        trigger = state.trigger,
                        options = viewModel.triggerOptions,
                        enabled = state.saveState !is RulesetSaveState.Saving,
                        metadataProvider = viewModel::getTriggerMetadata,
                        onTypeSelected = viewModel::selectTrigger,
                        onTriggerChange = viewModel::updateTrigger,
                    )

                    3 -> PreprocessesTabContent(
                        preprocesses = state.preprocesses,
                        options = viewModel.preprocessOptions,
                        metadataProvider = viewModel::getPreprocessMetadata,
                        preprocessFactory = viewModel::createPreprocess,
                        onPreprocessesChange = viewModel::updatePreprocesses,
                    )

                    4 -> AdapterTabContent(
                        adapter = state.adapter,
                        options = viewModel.adapterOptions,
                        enabled = state.saveState !is RulesetSaveState.Saving,
                        metadataProvider = viewModel::getAdapterMetadata,
                        onTypeSelected = viewModel::selectAdapter,
                        onAdapterChange = viewModel::updateAdapter,
                    )
                }
            }

            // prevent editing
            if (state.saveState is RulesetSaveState.Saving) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(innerPadding),
            )
        }
    }

    if (openDiscardDialog) AlertDialog(
        onDismissRequest = { openDiscardDialog = false },
        title = { Text("Discard changes?") },
        text = { Text("Your changes will be lost.") },
        confirmButton = {
            TextButton(onClick = { openDiscardDialog = false; onBack() }) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { openDiscardDialog = false }) { Text("Cancel") }
        },
    )

    if (openDeleteDialog) AlertDialog(
        onDismissRequest = { openDeleteDialog = false },
        title = { Text("Delete the ruleset?") },
        text = { Text("Are you sure you want to delete? This ruleset will be lost forever! (A long time!)") },
        confirmButton = {
            TextButton(onClick = {
                openDeleteDialog = false
                coroutineScope.launch { viewModel.delete(); onBack() }
            }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { openDeleteDialog = false }) { Text("Cancel") }
        },
    )
}

@Composable
private fun InformationTabContent(
    displayName: String?,
    description: String?,
    enabled: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
) = Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    OutlinedTextField(
        value = displayName.orEmpty(),
        onValueChange = onDisplayNameChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Display name") },
        singleLine = true,
    )
    OutlinedTextField(
        value = description.orEmpty(),
        onValueChange = onDescriptionChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("description") },
        minLines = 2,
        maxLines = 5,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerTabContent(
    trigger: RulesetTrigger?,
    options: List<RulesetConfigOption>,
    enabled: Boolean,
    metadataProvider: (RulesetTrigger) -> RulesetTriggerMetadata<RulesetTrigger>,
    onTypeSelected: (Class<*>) -> Unit,
    onTriggerChange: (RulesetTrigger) -> Unit,
) = Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .padding(bottom = 200.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    RulesetConfigTypeSelector(
        selectedClass = trigger?.javaClass,
        options = options,
        label = "Trigger",
        enabled = enabled,
        onTypeSelected = onTypeSelected,
    )
    if (trigger != null) {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        key(trigger::class.java) {
            metadataProvider(trigger).Editor(
                config = trigger,
                onConfigChanged = onTriggerChange,
            )
        }
    }
}

@Composable
private fun PreprocessesTabContent(
    preprocesses: List<RulesetPreprocess>?,
    options: List<RulesetConfigOption>,
    metadataProvider: (RulesetPreprocess) -> RulesetPreprocessMetadata<RulesetPreprocess>,
    preprocessFactory: (Class<*>) -> RulesetPreprocess,
    onPreprocessesChange: (List<RulesetPreprocess>) -> Unit,
) {
    val currentPreprocesses = preprocesses.orEmpty()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onPreprocessesChange(
            currentPreprocesses.toMutableList().apply {
                add(to.index, removeAt(from.index))
            },
        )
    }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val itemKeys = remember(currentPreprocesses) { preprocessItemKeys(currentPreprocesses) }

    @SuppressLint("ConfigurationScreenWidthHeight")
    val addMenuWidth = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(0).dp

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 200.dp),
    ) {
        itemsIndexed(
            items = currentPreprocesses,
            key = { index, _ -> itemKeys[index] },
        ) { index, preprocess ->
            ReorderableItem(
                state = reorderableState,
                key = itemKeys[index],
            ) {
                val metadata = metadataProvider(preprocess)
                val isConfigurable = metadata.singleton == null
                ListItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                onPreprocessesChange(
                                    currentPreprocesses.toMutableList().apply { removeAt(index) },
                                )
                            },
                            onClick = { if (isConfigurable) editingIndex = index },
                        )
                        .padding(start = 8.dp),
                    headlineContent = { Text(metadata.displayName) },
                    supportingContent = {
                        Text(
                            text = AnnotatedString.fromHtml(metadata.describe(preprocess)),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(),
                        ) {
                            Icon(
                                Icons.Rounded.DragIndicator,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 16.dp),
            ) {
                FilledTonalButton(
                    onClick = { addMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add preprocess")
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                    modifier = Modifier.width(addMenuWidth),
                ) {
                    options.forEach { option ->
                        TwoRowsDropdownMenuItem(
                            title = option.displayName,
                            description = option.description,
                            onClick = {
                                addMenuExpanded = false
                                onPreprocessesChange(
                                    currentPreprocesses + preprocessFactory(option.configClass),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    editingIndex?.let { index ->
        currentPreprocesses.getOrNull(index)
            ?.takeIf { metadataProvider(it).singleton == null }
            ?.let { preprocess ->
                var draft by remember(preprocess) { mutableStateOf(preprocess) }
                AlertDialog(
                    onDismissRequest = { editingIndex = null },
                    title = { Text("Edit preprocess") },
                    text = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            metadataProvider(draft).Editor(draft) { draft = it }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onPreprocessesChange(
                                    currentPreprocesses.toMutableList().apply { this[index] = draft },
                                )
                                editingIndex = null
                            },
                        ) { Text("Done") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingIndex = null }) { Text("Cancel") }
                    },
                )
            } ?: run { editingIndex = null }
    }
}

private fun preprocessItemKeys(preprocesses: List<RulesetPreprocess>): List<String> {
    val occurrences = IdentityHashMap<RulesetPreprocess, Int>()
    return preprocesses.map { preprocess ->
        val occurrence = occurrences[preprocess] ?: 0
        occurrences[preprocess] = occurrence + 1
        "${preprocess::class.java.name}:${System.identityHashCode(preprocess)}:$occurrence"
    }
}

@Composable
private fun AdapterTabContent(
    adapter: RulesetAdapter?,
    options: List<RulesetConfigOption>,
    enabled: Boolean,
    metadataProvider: (RulesetAdapter) -> RulesetAdapterMetadata<RulesetAdapter>,
    onTypeSelected: (Class<*>) -> Unit,
    onAdapterChange: (RulesetAdapter) -> Unit,
) = Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .padding(bottom = 200.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    RulesetConfigTypeSelector(
        selectedClass = adapter?.javaClass,
        options = options,
        label = "Adapter",
        enabled = enabled,
        onTypeSelected = onTypeSelected,
    )
    if (adapter != null) {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        key(adapter::class.java) {
            metadataProvider(adapter).Editor(
                config = adapter,
                onConfigChanged = onAdapterChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesetConfigTypeSelector(
    selectedClass: Class<*>?,
    options: List<RulesetConfigOption>,
    label: String,
    enabled: Boolean,
    onTypeSelected: (Class<*>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.configClass == selectedClass }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedOption?.displayName.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
                ),
            enabled = enabled,
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Not configured") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                TwoRowsDropdownMenuItem(
                    title = option.displayName,
                    description = option.description,
                    onClick = {
                        expanded = false
                        if (option.configClass != selectedClass)
                            onTypeSelected(option.configClass)
                    },
                    contentPadding =
                        ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
