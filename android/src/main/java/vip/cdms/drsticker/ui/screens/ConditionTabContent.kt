package vip.cdms.drsticker.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import vip.cdms.drsticker.rule.RulesetConditionMetadata
import vip.cdms.drsticker.rule.conditions.*
import vip.cdms.drsticker.ui.components.TwoRowsDropdownMenuItem
import vip.cdms.drsticker.ui.models.RulesetConfigOption

private typealias ConditionPath = List<Int>

private enum class ConditionGroupType {
    All,
    Any,
    None,
}

private sealed interface ConditionTreeRow {
    val key: String
    val depth: Int

    data class Group(
        val path: ConditionPath,
        override val depth: Int,
        val type: ConditionGroupType,
    ) : ConditionTreeRow {
        override val key = "group:${path.key()}"
    }

    data class Leaf(
        val path: ConditionPath,
        override val depth: Int,
        val condition: RulesetCondition,
        val avoided: Boolean,
        val editable: Boolean = true,
    ) : ConditionTreeRow {
        override val key = "leaf:${path.key()}"
    }

    data class Actions(
        val parentPath: ConditionPath?,
        override val depth: Int,
    ) : ConditionTreeRow {
        override val key = parentPath?.let { "actions:group:${it.key()}" } ?: "actions:root"
    }
}

private data class EditingLeaf(
    val path: ConditionPath,
    val condition: RulesetCondition,
    val avoided: Boolean,
)

@Composable
internal fun ConditionTabContent(
    condition: RulesetCondition?,
    options: List<RulesetConfigOption>,
    metadataProvider: (RulesetCondition) -> RulesetConditionMetadata<RulesetCondition>,
    conditionFactory: (Class<*>) -> RulesetCondition,
    onConditionChange: (RulesetCondition?) -> Unit,
) {
    var editingLeaf by remember { mutableStateOf<EditingLeaf?>(null) }
    val rows = remember(condition) { flattenConditionTree(condition) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 200.dp)
                + PaddingValues(horizontal = ListHorizontalPadding),
    ) {
        items(rows, key = ConditionTreeRow::key) { row ->
            when (row) {
                is ConditionTreeRow.Group -> ConditionRowIndent(row.depth) {
                    GroupRow(
                        type = row.type,
                        onTypeChange = { type ->
                            onConditionChange(condition?.changeGroupType(row.path, type))
                        },
                        onLongClick = {
                            onConditionChange(condition?.removeAt(row.path))
                        },
                    )
                }

                is ConditionTreeRow.Leaf -> ConditionRowIndent(row.depth) {
                    LeafRow(
                        condition = row.condition,
                        avoided = row.avoided,
                        editable = row.editable,
                        metadataProvider = metadataProvider,
                        onAvoidedChange = { avoided ->
                            val replacement = if (avoided) Not(row.condition) else row.condition
                            onConditionChange(condition?.replaceAt(row.path) { replacement })
                        },
                        onClick = {
                            editingLeaf = EditingLeaf(
                                path = row.path,
                                condition = row.condition,
                                avoided = row.avoided,
                            )
                        },
                        onLongClick = {
                            onConditionChange(condition?.removeAt(row.path))
                        },
                    )
                }

                is ConditionTreeRow.Actions -> ConditionRowIndent(
                    depth = row.depth,
                    terminal = row.parentPath != null,
                ) {
                    AddButtonsRow(
                        depth = row.depth,
                        conditionOptions = options,
                        conditionFactory = conditionFactory,
                        onAddCondition = { added ->
                            onConditionChange(condition.addAt(row.parentPath, added))
                        },
                        onAddGroup = { type ->
                            onConditionChange(condition.addAt(row.parentPath, type.emptyCondition()))
                        },
                    )
                }
            }
        }
    }

    editingLeaf?.let { editing ->
        var draft by remember(editing.condition) { mutableStateOf(editing.condition) }
        AlertDialog(
            onDismissRequest = { editingLeaf = null },
            title = { Text("Edit condition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metadataProvider(draft).Editor(draft) { draft = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val replacement = if (editing.avoided) Not(draft) else draft
                        onConditionChange(condition?.replaceAt(editing.path) { replacement })
                        editingLeaf = null
                    },
                ) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { editingLeaf = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConditionRowIndent(
    depth: Int,
    terminal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val guideColor = DividerDefaults.color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val verticalGuideCount = if (terminal) (depth - 1).coerceAtLeast(0) else depth

                fun guideX(level: Int) = 32.dp * level
                repeat(verticalGuideCount) { level ->
                    val x = guideX(level).toPx()
                    drawLine(guideColor, start = Offset(x, 0f), end = Offset(x, size.height))
                }
                if (terminal && depth > 0) {
                    val x = guideX(depth - 1).toPx()
                    val centerY = size.height / 2f
                    val contentStartX = x + 16.dp.toPx()
                    drawLine(guideColor, start = Offset(x, 0f), end = Offset(x, centerY))
                    drawLine(guideColor, start = Offset(x, centerY), end = Offset(contentStartX, centerY))
                }
            }
            .padding(start = 32.dp * depth),
    ) {
        content()
    }
}


@Composable
private fun GroupRow(
    type: ConditionGroupType,
    onTypeChange: (ConditionGroupType) -> Unit,
    onLongClick: () -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClick = onLongClick,
            onClick = {},
        )
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    Text("Match")
    Text(" ")
    Box {
        Text(
            text = conditionGroupLabel(type),
            modifier = Modifier.clickable { typeMenuExpanded = true },
            color = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(
            expanded = typeMenuExpanded,
            onDismissRequest = { typeMenuExpanded = false },
        ) {
            ConditionGroupType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(conditionGroupLabel(option)) },
                    onClick = {
                        typeMenuExpanded = false
                        onTypeChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LeafRow(
    condition: RulesetCondition,
    avoided: Boolean,
    editable: Boolean,
    metadataProvider: (RulesetCondition) -> RulesetConditionMetadata<RulesetCondition>,
    onAvoidedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var polarityMenuExpanded by remember { mutableStateOf(false) }
    val isConstant = condition === Always || condition === Never
    val description = when {
        condition === Always -> AnnotatedString("Always")
        condition === Never -> AnnotatedString("Never")
        !editable -> AnnotatedString("Unsupported negated condition")
        else -> AnnotatedString.fromHtml(metadataProvider(condition).describe(condition))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = if (editable && !isConstant) onClick else ({}),
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (!isConstant && editable) {
            Box {
                Text(
                    text = if (avoided) "Avoid" else "Require",
                    modifier = Modifier.clickable { polarityMenuExpanded = true },
                    color = MaterialTheme.colorScheme.primary,
                )
                DropdownMenu(
                    expanded = polarityMenuExpanded,
                    onDismissRequest = { polarityMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Require") },
                        onClick = {
                            polarityMenuExpanded = false
                            onAvoidedChange(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Avoid") },
                        onClick = {
                            polarityMenuExpanded = false
                            onAvoidedChange(true)
                        },
                    )
                }
            }
            Text(" ")
        }
        Text(description)
    }
}

@Composable
private fun AddButtonsRow(
    depth: Int,
    conditionOptions: List<RulesetConfigOption>,
    conditionFactory: (Class<*>) -> RulesetCondition,
    onAddCondition: (RulesetCondition) -> Unit,
    onAddGroup: (ConditionGroupType) -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    var conditionMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "Add condition",
            modifier = Modifier.clickable { conditionMenuExpanded = true },
            color = MaterialTheme.colorScheme.secondary,
        )
        DropdownMenu(
            expanded = conditionMenuExpanded,
            onDismissRequest = { conditionMenuExpanded = false },
            offset = DpOffset(
                x = ConditionMenuHorizontalMargin - ListHorizontalPadding - 32.dp * depth,
                y = 0.dp,
            ),
            modifier = Modifier.width(
                @SuppressLint("ConfigurationScreenWidthHeight")
                LocalConfiguration.current.screenWidthDp.dp - ConditionMenuHorizontalMargin * 2
            ),
        ) {
            conditionOptions.forEach { option ->
                TwoRowsDropdownMenuItem(
                    title = option.displayName,
                    description = option.description,
                    onClick = {
                        conditionMenuExpanded = false
                        onAddCondition(conditionFactory(option.configClass))
                    },
                )
            }
            TwoRowsDropdownMenuItem(
                title = "Always",
                description = "Always match this ruleset.",
                onClick = {
                    conditionMenuExpanded = false
                    onAddCondition(Always)
                },
            )
            TwoRowsDropdownMenuItem(
                title = "Never",
                description = "Never match this ruleset.",
                onClick = {
                    conditionMenuExpanded = false
                    onAddCondition(Never)
                },
            )
        }
    }
    Box {
        Text(
            text = "Add group",
            modifier = Modifier.clickable { groupMenuExpanded = true },
            color = MaterialTheme.colorScheme.secondary,
        )
        DropdownMenu(
            expanded = groupMenuExpanded,
            onDismissRequest = { groupMenuExpanded = false },
        ) {
            ConditionGroupType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(conditionGroupLabel(type)) },
                    onClick = {
                        groupMenuExpanded = false
                        onAddGroup(type)
                    },
                )
            }
        }
    }
}

private fun conditionGroupLabel(type: ConditionGroupType) = when (type) {
    ConditionGroupType.All -> "all of the following"
    ConditionGroupType.Any -> "any of the following"
    ConditionGroupType.None -> "none of the following"
}

private fun flattenConditionTree(condition: RulesetCondition?): List<ConditionTreeRow> = buildList {
    fun addNode(node: RulesetCondition, path: ConditionPath, depth: Int) {
        when (node) {
            is AllOf -> {
                add(ConditionTreeRow.Group(path, depth, ConditionGroupType.All))
                node.children.forEachIndexed { index, child -> addNode(child, path + index, depth + 1) }
                add(ConditionTreeRow.Actions(path, depth + 1))
            }

            is AnyOf -> {
                add(ConditionTreeRow.Group(path, depth, ConditionGroupType.Any))
                node.children.forEachIndexed { index, child -> addNode(child, path + index, depth + 1) }
                add(ConditionTreeRow.Actions(path, depth + 1))
            }

            is Not -> when (val child = node.child) {
                is AnyOf -> {
                    add(ConditionTreeRow.Group(path, depth, ConditionGroupType.None))
                    child.children.forEachIndexed { index, grandchild ->
                        addNode(grandchild, path + index, depth + 1)
                    }
                    add(ConditionTreeRow.Actions(path, depth + 1))
                }

                is AllOf -> add(ConditionTreeRow.Leaf(path, depth, node, false, editable = false))
                is Not -> add(ConditionTreeRow.Leaf(path, depth, node, false, editable = false))
                Always -> add(ConditionTreeRow.Leaf(path, depth, Never, false))
                Never -> add(ConditionTreeRow.Leaf(path, depth, Always, false))
                else -> add(ConditionTreeRow.Leaf(path, depth, child, true))
            }

            else -> add(ConditionTreeRow.Leaf(path, depth, node, false))
        }
    }

    condition?.let { addNode(it, emptyList(), 0) }
    add(ConditionTreeRow.Actions(parentPath = null, depth = 0))
}

private fun ConditionGroupType.emptyCondition(): RulesetCondition = when (this) {
    ConditionGroupType.All -> AllOf(emptyList())
    ConditionGroupType.Any -> AnyOf(emptyList())
    ConditionGroupType.None -> Not(AnyOf(emptyList()))
}

private fun RulesetCondition?.addAt(
    parentPath: ConditionPath?,
    added: RulesetCondition,
): RulesetCondition = when {
    this == null -> added
    parentPath == null && this is AllOf -> AllOf(children + added)
    parentPath == null -> AllOf(listOf(this, added))
    else -> replaceAt(parentPath) { parent -> parent.appendChild(added) }
}

private fun RulesetCondition.appendChild(child: RulesetCondition): RulesetCondition = when (this) {
    is AllOf -> AllOf(children + child)
    is AnyOf -> AnyOf(children + child)
    is Not -> if (this.child is AnyOf) Not(AnyOf(this.child.children + child)) else this
    else -> this
}

private fun RulesetCondition.changeGroupType(
    path: ConditionPath,
    type: ConditionGroupType,
): RulesetCondition = replaceAt(path) { group ->
    val children = group.groupChildren() ?: return@replaceAt group
    when (type) {
        ConditionGroupType.All -> AllOf(children)
        ConditionGroupType.Any -> AnyOf(children)
        ConditionGroupType.None -> Not(AnyOf(children))
    }
}

private fun RulesetCondition.removeAt(path: ConditionPath): RulesetCondition? {
    if (path.isEmpty()) return null
    val parentPath = path.dropLast(1)
    val childIndex = path.last()
    return replaceAt(parentPath) { parent -> parent.removeChild(childIndex) }
}

private fun RulesetCondition.removeChild(index: Int): RulesetCondition = when (this) {
    is AllOf -> AllOf(children.filterIndexed { childIndex, _ -> childIndex != index })
    is AnyOf -> AnyOf(children.filterIndexed { childIndex, _ -> childIndex != index })
    is Not -> if (child is AnyOf) {
        Not(AnyOf(child.children.filterIndexed { childIndex, _ -> childIndex != index }))
    } else this

    else -> this
}

private fun RulesetCondition.replaceAt(
    path: ConditionPath,
    transform: (RulesetCondition) -> RulesetCondition,
): RulesetCondition {
    if (path.isEmpty()) return transform(this)
    val childIndex = path.first()
    val remainingPath = path.drop(1)
    return when (this) {
        is AllOf -> AllOf(children.replaceChild(childIndex, remainingPath, transform))
        is AnyOf -> AnyOf(children.replaceChild(childIndex, remainingPath, transform))
        is Not -> if (child is AnyOf) {
            Not(AnyOf(child.children.replaceChild(childIndex, remainingPath, transform)))
        } else this

        else -> this
    }
}

private fun List<RulesetCondition>.replaceChild(
    index: Int,
    remainingPath: ConditionPath,
    transform: (RulesetCondition) -> RulesetCondition,
) = mapIndexed { childIndex, child ->
    if (childIndex == index) child.replaceAt(remainingPath, transform) else child
}

private fun RulesetCondition.groupChildren(): List<RulesetCondition>? = when (this) {
    is AllOf -> children
    is AnyOf -> children
    is Not -> (child as? AnyOf)?.children
    else -> null
}

private fun ConditionPath.key() = if (isEmpty()) "root" else joinToString(".")

private val ListHorizontalPadding = 20.dp
private val ConditionMenuHorizontalMargin = 16.dp
