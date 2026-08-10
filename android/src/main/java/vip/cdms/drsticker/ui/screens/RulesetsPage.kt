package vip.cdms.drsticker.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import vip.cdms.drsticker.rule.RulesetId
import vip.cdms.drsticker.ui.components.FullScreenOverlay
import vip.cdms.drsticker.ui.models.RulesetsPageModel
import vip.cdms.drsticker.ui.utils.rememberDisabledTopOverscrollEffect

@Serializable
object RulesetsRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulesetsPage(
    viewModel: RulesetsPageModel = hiltViewModel(),
    onRulesetDetail: (RulesetId) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    var isManualSorting by remember { mutableStateOf(false) }

    var openAddOverlay by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        TODO()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Ruleset", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text("114514 installed.", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        IconButton(
                            onClick = {
                                isManualSorting = !isManualSorting
                            },
                            modifier = Modifier
                                .clip(IconButtonDefaults.standardShape)
                                .background(if (isManualSorting) MaterialTheme.colorScheme.secondary else defaultIconButtonColors.containerColor),
                        ) {
                            Icon(
                                imageVector = if (isManualSorting) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                tint = if (isManualSorting) MaterialTheme.colorScheme.onSecondary else defaultIconButtonColors.contentColor,
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
                        IconButton(onClick = { openAddOverlay = true }) {
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
            val list = (0..16).map { it.toString() }
            itemsIndexed(
                items = list,
                key = { _, v -> v }
            ) { _, mockId ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = mockId,
                ) {
                    var isEnabled by remember { mutableStateOf(false) }
                    RulesetListItem(
                        modifier = with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "r_bound_$mockId"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        modifierTitle = with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "r_title_$mockId"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                        displayName = "Ruleset name",
                        description = "Description text",
                        isSorting = isManualSorting,
                        isEnabled = isEnabled,
                        onClick = {
                            onRulesetDetail(mockId)
                        },
                        onEnabledChange = { isEnabled = it },
                    )
                }
            }
        }
    }

    RulesetAddOverlay(
        visible = openAddOverlay,
        onDismissRequest = { openAddOverlay = false }
    )
}

@Composable
private fun ReorderableCollectionItemScope.RulesetListItem(
    modifier: Modifier,
    modifierTitle: Modifier,
    displayName: String,
    description: String?,
    isSorting: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) = ListItem(
    modifier = modifier
        .clickable(onClick = onClick),
    headlineContent = { Text(displayName, modifier = modifierTitle) },
    supportingContent = description?.let { { Text(it) } },
    trailingContent = {
        if (isSorting) IconButton(
            onClick = {},
            modifier = Modifier.draggableHandle(),
        ) {
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = null
            )
        } else Switch(
            checked = isEnabled,
            onCheckedChange = onEnabledChange,
        )
    },
)

@Composable
private fun RulesetAddOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    FullScreenOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        RulesetDetail(
            sharedTransitionScope = null,
            animatedVisibilityScope = null,
            onBack = onDismissRequest,
        )
    }
}
