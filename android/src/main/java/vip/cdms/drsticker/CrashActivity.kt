package vip.cdms.drsticker

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cat.ereza.customactivityoncrash.CustomActivityOnCrash
import vip.cdms.drsticker.ui.components.StaticLargeTopAppBar
import vip.cdms.drsticker.ui.components.getAppVersion
import vip.cdms.drsticker.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*
import cat.ereza.customactivityoncrash.R as CR

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Content()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() = Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        val config = CustomActivityOnCrash.getConfigFromIntent(intent)!!
        val errors = remember { getAllErrorDetails() }

        StaticLargeTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            subtitle = { Text(stringResource(CR.string.customactivityoncrash_error_activity_error_occurred_explanation)) },
            actions = {
                TooltipBox(
                    positionProvider = TooltipDefaults
                        .rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = {
                        PlainTooltip { Text(stringResource(CR.string.customactivityoncrash_error_activity_restart_app)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { CustomActivityOnCrash.restartApplication(this@CrashActivity, config) }) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                        )
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults
                        .rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = {
                        PlainTooltip { Text(stringResource(CR.string.customactivityoncrash_error_activity_error_details_copy)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(
                            getString(CR.string.customactivityoncrash_error_activity_error_details_clipboard_label),
                            errors
                        )
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(
                            this@CrashActivity,
                            CR.string.customactivityoncrash_error_activity_error_details_copied,
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                        )
                    }
                }
            }
        )

        OutlinedTextField(
            value = errors,
            onValueChange = {},
            modifier = Modifier
                .padding(top = 24.dp, bottom = 80.dp)
                .padding(horizontal = 16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.error,
            ),
            isError = true,
        )
    }

    private fun getAllErrorDetails() = buildString {
        val appVersion = getAppVersion(this@CrashActivity)
        appendLine("Build version: ${appVersion.first} ${BuildConfig.GIT_COMMIT_ID} (${appVersion.second})")
        appendLine("Current date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine(
            "Device: ${
                Build.MODEL.takeIf { it.startsWith(Build.MANUFACTURER, ignoreCase = true) }
                    ?.replaceFirstChar { it.titlecase() }
                    ?: "${Build.MANUFACTURER.replaceFirstChar { it.titlecase() }} ${Build.MODEL}"
            }"
        )
        appendLine("OS version: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")

        appendLine("Stack trace:")
        append(CustomActivityOnCrash.getStackTraceFromIntent(intent))
    }
}
