package vip.cdms.drsticker.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.*
import vip.cdms.drsticker.data.SortStrategy
import vip.cdms.drsticker.data.utils.state
import vip.cdms.drsticker.services.utils.ensureAccessibilityEnabled
import vip.cdms.drsticker.services.utils.isBatteryOptimizationExemptionGranted
import vip.cdms.drsticker.services.utils.requestBatteryOptimizationExemption
import vip.cdms.drsticker.ui.components.ListPreference
import vip.cdms.drsticker.ui.components.SliderPreference
import vip.cdms.drsticker.ui.models.SettingsModel
import vip.cdms.drsticker.ui.utils.rememberDisabledTopOverscrollEffect
import java.io.File

@Serializable
object SettingsRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsPage(
    viewModel: SettingsModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val settingsRepository = viewModel.settingsRepository
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceTheme {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 200.dp),
                overscrollEffect = rememberDisabledTopOverscrollEffect(),
            ) {
                preferenceCategory(
                    key = "permission",
                    title = { Text("Permissions") },
                )

                item(contentType = "SwitchPreference") {
                    val context = LocalContext.current
                    val state = settingsRepository.preferAccessibilityService.state()
                    SwitchPreference(
                        value = state.value,
                        onValueChange = { if (!it || ensureAccessibilityEnabled(context)) state.value = it },
                        title = { Text("Prefer Accessibility Service") },
                        summary = { Text("Controls whether Accessibility or Shizuku handles the condition system and related features.") },
                    )
                }

                item(contentType = "SwitchPreference") {
                    val context = LocalContext.current
                    var exempt by remember { mutableStateOf(isBatteryOptimizationExemptionGranted(context)) }
                    LifecycleResumeEffect(Unit) {
                        exempt = isBatteryOptimizationExemptionGranted(context)
                        onPauseOrDispose {}
                    }
                    SwitchPreference(
                        value = exempt,
                        onValueChange = { value ->
                            if (value) exempt = isBatteryOptimizationExemptionGranted(context)
                                .also { if (!it) requestBatteryOptimizationExemption(context) }
                        },
                        title = { Text("Ignore Battery Optimization") },
                        summary = { Text("May help prevent services from being terminated.") },
                    )
                }

                item(contentType = "SwitchPreference") {
                    SwitchPreference(
                        value = false,
                        onValueChange = {},
                        title = { Text("Silent Audio Keep-Alive") },
                        summary = { Text("Play silent audio to prevent process termination. Significantly increases battery usage.") },
                    )
                }

                preferenceCategory(
                    key = "preference",
                    title = { Text("Preferences") },
                )

                item(contentType = "SwitchPreference") {
                    SwitchPreference(
                        // state = settingsRepository.suggestedStickers.state(),
                        value = false,
                        onValueChange = {},
                        title = { Text("Suggested Stickers") },
                        summary = { Text("Show a dedicated suggestion section in the sticker picker.") },
                    )
                }

                item(contentType = "ListPreference") {
                    var value by remember { mutableStateOf(SortStrategy.SMART) }
                    ListPreference(
                        value = value,
                        onValueChange = { value = it },
                        values = listOf(
                            SortStrategy.SMART,
                            SortStrategy.RECENCY,
                            SortStrategy.FREQUENCY,
                        ),
                        title = { Text("Suggestion Strategy") },
                        summary = { Text("$it - Choose how stickers are ranked and ordered in the suggestion section.") },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = {
                            AnnotatedString(
                                when (it) {
                                    SortStrategy.SMART -> "Smart (Recommended)"
                                    SortStrategy.RECENCY -> "Recently used"
                                    SortStrategy.FREQUENCY -> "Most frequently used"
                                    else -> throw IllegalStateException()
                                }
                            )
                        },
                    )
                }

                item(contentType = "SliderPreference") {
                    var value by remember { mutableFloatStateOf(3f) }
                    SliderPreference(
                        value = value,
                        onValueChange = { value = it },
                        title = { Text("Suggestion Rows") },
                        valueRange = 2f..8f,
                        valueSteps = 5,
                        summary = { Text("Number of rows dedicated to the suggested stickers section.") },
                        valueText = { Text("${it.toInt()}") }
                    )
                }

                item(contentType = "SliderPreference") {
                    var value by remember { mutableFloatStateOf(5f) }
                    SliderPreference(
                        value = value,
                        onValueChange = { value = it },
                        title = { Text("Picker Columns") },
                        valueRange = 4f..6f,
                        valueSteps = 1,
                        summary = { Text("Number of sticker columns in the picker sheet grid.") },
                        valueText = { Text("${it.toInt()}") }
                    )
                }

                item(contentType = "SliderPreference") {
                    var value by remember { mutableFloatStateOf(4f) }
                    SliderPreference(
                        value = value,
                        onValueChange = { value = it },
                        title = { Text("Set Details Columns") },
                        valueRange = 4f..6f,
                        valueSteps = 1,
                        summary = { Text("Number of columns shown on the sticker set details page.") },
                        valueText = { Text("${it.toInt()}") }
                    )
                }

                preferenceCategory(
                    key = "search",
                    title = { Text("Sticker Search") },
                )

                item(contentType = "SwitchPreference") {
                    SwitchPreference(
                        value = false,
                        onValueChange = {},
                        title = { Text("Inline Quick Search") },
                        summary = { Text("Automatically suggest matching stickers while typing in any text field.") },
                    )
                }

                item(contentType = "ListPreference") {
                    var value by remember { mutableStateOf(SortStrategy.SMART) }
                    ListPreference(
                        value = value,
                        onValueChange = { value = it },
                        values = listOf(
                            SortStrategy.SMART,
                            SortStrategy.RECENCY,
                            SortStrategy.FREQUENCY,
                        ),
                        title = { Text("Ranking Strategy") },
                        summary = { Text("$it - Choose how search results are ranked and ordered.") },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = {
                            AnnotatedString(
                                when (it) {
                                    SortStrategy.SMART -> "Smart (Recommended)"
                                    SortStrategy.RECENCY -> "Recently used"
                                    SortStrategy.FREQUENCY -> "Most frequently used"
                                    else -> throw IllegalStateException()
                                }
                            )
                        },
                    )
                }

                item {
                    // TODO: dialog
                    TwoTargetIconButtonPreference(
                        title = { Text("AI Auto-Tagging") },
                        summary = { Text("Use multimodal vision models to generate searchable tags for stickers.") },
                        iconButtonIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        onClick = {},
                        onIconButtonClick = {},
                    )
                }

                preferenceCategory(
                    key = "data",
                    title = { Text("Data Management") },
                )

                item(contentType = "Preference") {
                    Preference(
                        title = { Text("Configure Data Sources") },
                        summary = { Text("0 repositories, 0 services running.") },
                        onClick = {},
                    )
                }

                item(contentType = "Preference") {
                    Preference(
                        title = { Text("Sync Now") },
                        onClick = {},
                    )
                }

                item(contentType = "Preference") {
                    Preference(
                        title = { Text("Import Data") },
                        onClick = {},
                    )
                }

                item(contentType = "Preference") {
                    Preference(
                        title = { Text("Export Data") },
                        onClick = {},
                    )
                }

                preferenceCategory(
                    key = "debug",
                    title = { Text("Debugging Options") },
                )

                item(contentType = "Preference") {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var result by remember { mutableStateOf<String?>(null) }
                    Preference(
                        title = { Text("Migrate cache.json to source.json") },
                        summary = { Text(result ?: "To adapt a breaking update.") },
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val rootDir = context.getExternalFilesDir(null) ?: context.filesDir
                                val stickerSetsDir = rootDir.resolve("stickers")
                                val migratedCount = stickerSetsDir.listFiles()
                                    .orEmpty()
                                    .filter(File::isDirectory)
                                    .count { setDir ->
                                        val oldFile = setDir.resolve("cache.json")
                                        val newFile = setDir.resolve("source.json")
                                        oldFile.exists() && !newFile.exists() && oldFile.renameTo(newFile)
                                    }
                                result = "Migrated $migratedCount cache file(s)"
                            }
                        },
                    )
                }
            }
        }
    }
}
