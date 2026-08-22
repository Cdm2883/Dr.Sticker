package vip.cdms.drsticker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import vip.cdms.drsticker.rule.RulesetId
import vip.cdms.drsticker.ui.models.RulesetListEntry
import vip.cdms.drsticker.ui.models.RulesetsPageModel
import vip.cdms.drsticker.ui.utils.negativePadding
import vip.cdms.drsticker.ui.utils.readableMessage
import vip.cdms.drsticker.ui.utils.rememberDisabledTopOverscrollEffect

@Serializable
object RulesetsRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulesetsPage(
    viewModel: RulesetsPageModel = hiltViewModel(),
    onAddRuleset: () -> Unit,
    onEditRuleset: (RulesetId) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.index, to.index)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.reloadPage()
        onPauseOrDispose {}
    }

    BackHandler(enabled = state.isManualSorting) {
        viewModel.cancelManualSorting()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val rulesetCount = state.entries.count { it is RulesetListEntry.Ruleset }
            LargeFlexibleTopAppBar(
                title = { Text("Ruleset", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text("$rulesetCount installed.", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    val defaultIconButtonColors = IconButtonDefaults.iconButtonColors()
                    TooltipBox(
                        positionProvider = TooltipDefaults
                            .rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = {
                            PlainTooltip { Text("Sort rulesets") }
                        },
                        state = rememberTooltipState(),
                    ) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(IconButtonDefaults.smallContainerSize())
                                .clip(IconButtonDefaults.standardShape)
                                .background(if (state.isManualSorting) MaterialTheme.colorScheme.secondary else defaultIconButtonColors.containerColor)
                                .clickable {
                                    if (state.isManualSorting)
                                        viewModel.finishManualSorting()
                                    else
                                        viewModel.beginManualSorting()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (state.isManualSorting) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                tint = if (state.isManualSorting) MaterialTheme.colorScheme.onSecondary else defaultIconButtonColors.contentColor,
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults
                            .rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = {
                            PlainTooltip { Text("Add a ruleset") }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = onAddRuleset,
                            enabled = !state.isManualSorting,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
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
                    text = "There are no rulesets installed yet, go ahead and add one!",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                )
            }

            itemsIndexed(items = state.entries, key = { _, entry -> entry.rulesetId }) { _, entry ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = entry.rulesetId,
                ) {
                    if (entry is RulesetListEntry.LoadError)
                        return@ReorderableItem RulesetLoadErrorListItem(
                            entry = entry,
                            isSorting = state.isManualSorting,
                            onDelete = {
                                viewModel.deleteRuleset(entry.rulesetId)
                            },
                        )

                    RulesetListItem(
                        entry = entry as RulesetListEntry.Ruleset,
                        modifier = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "r_bound_" + entry.rulesetId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        modifierTitle = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "r_title_" + entry.rulesetId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        isSorting = state.isManualSorting,
                        onClick = {
                            if (!state.isManualSorting) onEditRuleset(entry.rulesetId)
                        },
                        onEnabledChange = {
                            viewModel.setRulesetEnabled(
                                rulesetId = entry.rulesetId,
                                enabled = it,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.RulesetListItem(
    entry: RulesetListEntry.Ruleset,
    modifier: Modifier,
    modifierTitle: Modifier,
    isSorting: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) = ListItem(
    modifier = modifier
        .clickable(onClick = onClick),
    headlineContent = {
        Text(
            entry.displayName,
            modifier = modifierTitle
                .negativePadding(end = 12.dp),
        )
    },
    supportingContent = entry.description?.let {
        {
            Text(
                it,
                modifier = Modifier
                    .negativePadding(end = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    trailingContent = {
        if (isSorting) IconButton(
            onClick = {},
            modifier = Modifier
                .offset(x = 12.dp)
                .draggableHandle(),
        ) {
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = null
            )
        } else Switch(
            checked = entry.isEnabled,
            onCheckedChange = onEnabledChange,
        )
    },
)

@Composable
private fun ReorderableCollectionItemScope.RulesetLoadErrorListItem(
    entry: RulesetListEntry.LoadError,
    isSorting: Boolean,
    onDelete: () -> Unit
) = ListItem(
    headlineContent = {
        Text(
            text = "Unable to load ruleset: ${entry.rulesetId}",
            modifier = Modifier.negativePadding(end = 12.dp),
            color = MaterialTheme.colorScheme.error,
        )
    },
    supportingContent = {
        Text(
            entry.error.readableMessage(),
            modifier = Modifier.negativePadding(end = 12.dp),
        )
    },
    trailingContent = {
        var openDeleteDialog by remember { mutableStateOf(false) }
        IconButton(
            onClick = {
                if (isSorting) return@IconButton
                openDeleteDialog = true
            },
            modifier = Modifier
                .offset(x = 12.dp)
                .then(if (isSorting) Modifier.draggableHandle() else Modifier),
        ) {
            Icon(
                if (isSorting) Icons.Rounded.DragIndicator else Icons.Rounded.Delete,
                contentDescription = null
            )
        }
        if (openDeleteDialog) AlertDialog(
            onDismissRequest = { openDeleteDialog = false },
            title = { Text("Delete the ruleset?") },
            text = { Text("Are you sure you want to delete? This ruleset will be lost forever! (A long time!)") },
            confirmButton = {
                TextButton(onClick = { openDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { openDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
)
