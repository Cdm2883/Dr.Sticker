package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import vip.cdms.drsticker.data.SortStrategy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StickerSortIconButton(
    sortStrategy: SortStrategy,
    isManualSorting: Boolean,
    onRequestManualSorting: () -> Unit,
    onSorted: (SortStrategy) -> Unit,

    hasGlobal: Boolean = false,
    isGlobal: Boolean = false,
    onUseGlobal: (Boolean) -> Unit = {},
) {
    var openSortStrategyDialog by remember { mutableStateOf(false) }

    val button = @Composable {
        val defaultIconButtonColors = IconButtonDefaults.iconButtonColors()
        CombinedClickableIconButton(
            modifier = Modifier
                .background(if (isManualSorting) MaterialTheme.colorScheme.secondary else defaultIconButtonColors.containerColor),
            onClick = {
                if (sortStrategy != SortStrategy.MANUAL) return@CombinedClickableIconButton
                if (!isManualSorting) onRequestManualSorting()
                else onSorted(SortStrategy.MANUAL)
            },
            onLongClick = {
                if (isManualSorting) return@CombinedClickableIconButton
                openSortStrategyDialog = true
            },
        ) {
            Icon(
                imageVector = if (isManualSorting) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Sort,
                contentDescription = null,
                tint = if (isManualSorting) MaterialTheme.colorScheme.onSecondary else defaultIconButtonColors.contentColor,
            )
        }
    }
    if (sortStrategy != SortStrategy.MANUAL)
        BadgedBox(
            badge = {
                Badge(modifier = Modifier.offset(x = (-8).dp, y = 8.dp)) {
                    Text(
                        when (sortStrategy) {
                            SortStrategy.MANUAL -> throw IllegalStateException()
                            SortStrategy.SMART -> "SMART"
                            SortStrategy.RECENCY -> "RECENCY"
                            SortStrategy.FREQUENCY -> "FREQUENCY"
                        },
                        modifier = Modifier
                            .widthIn(max = 32.dp)
                            .basicMarquee()
                    )
                }
            }
        ) {
            button()
        }
    else
        button()

    val strategies = mapOf(
        SortStrategy.MANUAL to "Manual order",  // 手动排序
        SortStrategy.SMART to "Smart (Recommended)",  // 智能排序（推荐）
        SortStrategy.RECENCY to "Recently used",  // 最近使用优先
        SortStrategy.FREQUENCY to "Most frequently used",  // 高频使用优先
    )
    var selectedStrategy by remember(sortStrategy) { mutableStateOf(sortStrategy) }
    var selectedGlobal by remember(isGlobal) { mutableStateOf(isGlobal) }
    if (openSortStrategyDialog) AlertDialog(
        onDismissRequest = {
            openSortStrategyDialog = false
        },
        title = { Text("Sort strategy") },
        text = {
            Column(Modifier.selectableGroup()) {
                if (hasGlobal) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .offset(x = (-2).dp)
                            .selectable(
                                selected = selectedGlobal,
                                onClick = { selectedGlobal = !selectedGlobal; onUseGlobal(selectedGlobal) },
                                role = Role.RadioButton,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedGlobal,
                            onCheckedChange = null,
                        )
                        Text(
                            "Use global configuration",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                strategies.forEach { (strategy, text) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .offset(x = (-2).dp)
                            .selectable(
                                selected = selectedStrategy == strategy,
                                onClick = { selectedStrategy = strategy },
                                role = Role.RadioButton,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStrategy == strategy,
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openSortStrategyDialog = false
                onSorted(selectedStrategy)
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = { openSortStrategyDialog = false }) {
                Text("Cancel")
            }
        },
    )
}
