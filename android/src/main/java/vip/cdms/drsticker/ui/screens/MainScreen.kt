package vip.cdms.drsticker.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vip.cdms.drsticker.services.StickerServiceState
import vip.cdms.drsticker.ui.components.AboutBottomSheet
import vip.cdms.drsticker.ui.models.MainScreenModel
import vip.cdms.drsticker.ui.utils.rememberScrollToHideBottomBarState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainScreenModel = hiltViewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isRootDestination = currentDestination.navBarIndex() != -1

    val scrollToHideBottomBarState = rememberScrollToHideBottomBarState(initialHeight = 80.dp)
    LaunchedEffect(currentDestination) {
//        scrollToHideBottomBarState.reset()
        if (isRootDestination) scrollToHideBottomBarState.unlockAndShow()
        else scrollToHideBottomBarState.lockAndHide()
    }

    var showAboutBottomSheet by remember { mutableStateOf(false) }
    if (showAboutBottomSheet) AboutBottomSheet(
        onDismissRequest = { showAboutBottomSheet = false },
    )

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        modifier = Modifier.nestedScroll(scrollToHideBottomBarState.nestedScrollConnection),
        bottomBar = {
            MainNavigationBar(
                navController = navController,
                modifier = Modifier.offset { scrollToHideBottomBarState.bottomBarOffset }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MainFloatingActionButton(
                viewModel = viewModel,
                modifier = Modifier.offset { scrollToHideBottomBarState.bottomBarOffset },
                onClickAbout = { showAboutBottomSheet = true },
            )
        },
    ) {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                modifier = Modifier.padding(
//                    bottom = if (isRootDestination) scrollToHideBottomBarState.bottomPadding else 0.dp
                    bottom = scrollToHideBottomBarState.bottomPadding
                )
            ) {
                composable<DashboardRoute>(
                    enterTransition = navBarEnterTransition,
                    exitTransition = navBarExitTransition,
                    popEnterTransition = navBarEnterTransition,
                    popExitTransition = navBarExitTransition
                ) {
                    DashboardPage()
                }

                composable<SettingsRoute>(
                    enterTransition = { slideInVertically { it } },
                    exitTransition = { slideOutVertically { it } },
                    popEnterTransition = { slideInVertically { -it } },
                    popExitTransition = { slideOutVertically { it } },
                ) {
                    SettingsPage()
                }

                composable<StickerSetsRoute>(
                    enterTransition = navBarEnterTransition,
                    exitTransition = navBarExitTransition,
                    popEnterTransition = navBarEnterTransition,
                    popExitTransition = navBarExitTransition
                ) {
                    StickerSetsPage(
                        onStickerSetDetail = { navController.navigate(StickerSetDetailRoute(it)) },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                    )
                }
                composable<StickerSetDetailRoute> {
                    StickerSetDetail(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<RulesetsRoute>(
                    enterTransition = navBarEnterTransition,
                    exitTransition = navBarExitTransition,
                    popEnterTransition = navBarEnterTransition,
                    popExitTransition = navBarExitTransition
                ) {
                    RulesetsPage(
                        onAddRuleset = { navController.navigate(RulesetAddRoute()) },
                        onEditRuleset = { navController.navigate(RulesetEditRoute(it)) },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                    )
                }
                composable<RulesetEditRoute> {
                    RulesetDetail(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<RulesetAddRoute>(
                    enterTransition = { slideInVertically { it } },
                    exitTransition = { slideOutVertically { it } },
                    popEnterTransition = { slideInVertically { -it } },
                    popExitTransition = { slideOutVertically { it } },
                ) {
                    RulesetDetail(
                        sharedTransitionScope = null,
                        animatedVisibilityScope = null,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainFloatingActionButton(
    viewModel: MainScreenModel,
    modifier: Modifier = Modifier,
    onClickAbout: () -> Unit,
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val activated = serviceState == StickerServiceState.Running
    var expanded by rememberSaveable { mutableStateOf(false) }

    val items = listOf(
        Icons.Rounded.Layers to "Open Sheet" to viewModel::openPickerSheet,
        Icons.Rounded.Settings to "Settings" to { TODO() },
        Icons.Rounded.Info to "About" to onClickAbout,
    )

    BackHandler(expanded) { expanded = false }

    FloatingActionButtonMenu(
        modifier = modifier.offset(x = 16.dp, y = 16.dp),
        expanded = expanded,
        button = {
            val containerColor by animateColorAsState(if (activated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            val contentColor by animateColorAsState(if (activated) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary)
            val buttonColors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = viewModel::toggleStickerService,
                        colors = buttonColors,
                    ) {
                        Icon(
                            imageVector = when (serviceState) {
                                StickerServiceState.Stopped -> Icons.Rounded.PlayArrow
                                StickerServiceState.Starting -> Icons.Rounded.PlayArrow
                                StickerServiceState.Running -> Icons.Rounded.Pause
                                StickerServiceState.Stopping -> Icons.Rounded.Pause
                                is StickerServiceState.Failed -> Icons.Rounded.Error
                            },
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                        AnimatedVisibility(
                            visible = !activated || expanded,
                            enter = expandHorizontally(expandFrom = Alignment.Start),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
                        ) {
                            Row {
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Sticker Service", maxLines = 1)
                            }
                        }
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        checked = expanded,
                        onCheckedChange = { expanded = it },
                        colors = buttonColors,
                    ) {
                        val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            modifier = Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer { this.rotationZ = rotation },
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                modifier = Modifier
                    .offset(y = 2.dp)  // - 12 dp -> no gap
                    .height(40.dp),
                onClick = {
                    expanded = false
                    item.second()
                },
                icon = {
                    Icon(
                        imageVector = item.first.first,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val startPx = 8.dp.roundToPx()
                                layout(placeable.width - startPx, placeable.height) {
                                    placeable.place(-startPx, 0)
                                }
                            }
                    )
                },
                text = {
                    Text(
                        item.first.second,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val endPx = 8.dp.roundToPx()
                            layout(placeable.width - endPx, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun MainNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentId = currentDestination?.id
    fun navigateTo(route: Any) = navController.navigate(route) {
        if (currentId != null) popUpTo(currentId) { inclusive = true; saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentDestination?.hasRoute<DashboardRoute>() == true,
            onClick = { navigateTo(DashboardRoute) },
            icon = { Icon(Icons.Rounded.Dashboard, contentDescription = null) },
            label = { Text("Dashboard") }
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<StickerSetsRoute>() == true,
            onClick = { navigateTo(StickerSetsRoute) },
            icon = { Icon(Icons.Rounded.EmojiSymbols, contentDescription = null) },
            label = { Text("Stickers") }
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<RulesetsRoute>() == true,
            onClick = { navigateTo(RulesetsRoute) },
            icon = { Icon(Icons.Rounded.DesignServices, contentDescription = null) },
            label = { Text("Ruleset") }
        )
    }
}

private fun NavDestination?.navBarIndex(): Int {
    if (this == null) return -1
    return when {
        hasRoute<DashboardRoute>() -> 0
        hasRoute<StickerSetsRoute>() -> 1
        hasRoute<RulesetsRoute>() -> 2
        else -> -1
    }
}

private val navBarEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
    val initialIndex = initialState.destination.navBarIndex()
    val targetIndex = targetState.destination.navBarIndex()
    if (initialIndex != -1 && targetIndex != -1) {
        val slideDirection = if (targetIndex > initialIndex) 1 else -1
        slideInHorizontally(animationSpec = tween(250)) { width -> width * slideDirection } +
                fadeIn(animationSpec = tween(250))
    } else {
        null
    }
}

private val navBarExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
    val initialIndex = initialState.destination.navBarIndex()
    val targetIndex = targetState.destination.navBarIndex()
    if (initialIndex != -1 && targetIndex != -1) {
        val slideDirection = if (targetIndex > initialIndex) -1 else 1
        slideOutHorizontally(animationSpec = tween(250)) { width -> width * slideDirection } +
                fadeOut(animationSpec = tween(250))
    } else {
        null
    }
}
