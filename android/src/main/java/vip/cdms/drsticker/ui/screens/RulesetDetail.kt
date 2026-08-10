package vip.cdms.drsticker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetId
import vip.cdms.drsticker.ui.models.RulesetDetailModel

@Serializable
data class RulesetDetailRoute(val rulesetId: RulesetId)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesetDetail(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    viewModel: RulesetDetailModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                modifier = Modifier
                    .background(backgroundColor)
                    .run {
                        with(sharedTransitionScope ?: return@run this) {
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
                            "Ruleset name",
                            modifier = run {
                                with(sharedTransitionScope ?: return@run Modifier) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "r_title_" + viewModel.rulesetId),
                                        animatedVisibilityScope = animatedVisibilityScope!!,
                                    )
                                }
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        }
                        IconButton(onClick = { }) {
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
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .run {
                    with(animatedVisibilityScope ?: return@run this) {
                        animateEnterExit(
                            enter = slideInVertically(),
                            exit = fadeOut() + slideOutVertically { -it / 8 }
                        )
                    }
                }
        ) { page ->
            when (page) {
                0 -> InformationTabContent(viewModel)
                1 -> ConditionTabContent(viewModel)
                2 -> TriggerTabContent(viewModel)
                3 -> PreprocessesTabContent(viewModel)
                4 -> AdapterTabContent(viewModel)
            }
        }
    }
}

@Composable
fun InformationTabContent(viewModel: RulesetDetailModel) {
    var displayName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("description") },
            minLines = 2,
            maxLines = 5,
        )
    }
}

@Composable
fun ConditionTabContent(viewModel: RulesetDetailModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(50) { index ->
            ListItem(headlineContent = { Text("ConditionTabContent $index") })
        }
    }
}

@Composable
fun TriggerTabContent(viewModel: RulesetDetailModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(50) { index ->
            ListItem(headlineContent = { Text("TriggerTabContent $index") })
        }
    }
}

@Composable
fun PreprocessesTabContent(viewModel: RulesetDetailModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(50) { index ->
            ListItem(headlineContent = { Text("PreprocessesTabContent $index") })
        }
    }
}

@Composable
fun AdapterTabContent(viewModel: RulesetDetailModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(50) { index ->
            ListItem(headlineContent = { Text("AdapterTabContent $index") })
        }
    }
}
