package vip.cdms.drsticker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import vip.cdms.drsticker.ui.theme.darkTheme

class FullScreenDialogScope(
    val snackbarHostState: SnackbarHostState
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullScreenDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    progress: (() -> Float)? = null,
    title: @Composable () -> Unit,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable FullScreenDialogScope.(PaddingValues) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = remember { FullScreenDialogScope(snackbarHostState) }

    FullScreenOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp
                                ),
                                content = title
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismissRequest) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        },
                        actions = {
                            actions?.let {
                                it()
                                Spacer(Modifier.size(8.dp))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                    progress?.let {
                        LinearProgressIndicator(
                            progress = it,
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Butt,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) {
            with(scope) {
                content(
                    it + if (progress == null)
                        PaddingValues.Zero else PaddingValues(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun FullScreenOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        ApplyFullScreenDialogWindowStyle()
        AnimatedVisibility(
            visibleState = transitionState,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            content = content
        )
    }
}

@Composable
internal fun ApplyFullScreenDialogWindowStyle(
    dimAmount: Float = 0.4f
) {
    val view = LocalView.current
    val isDarkTheme = MaterialTheme.darkTheme
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setDimAmount(dimAmount)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
