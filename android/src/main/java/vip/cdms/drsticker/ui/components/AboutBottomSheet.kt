package vip.cdms.drsticker.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import vip.cdms.drsticker.BuildConfig
import vip.cdms.drsticker.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutBottomSheet(
    onDismissRequest: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Sending memes everywhere.",
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                painterResource(R.drawable.ic_drsticker),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .padding(horizontal = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current
        val appVersion = getAppVersion(context)
        val versionText = "${appVersion.first} ${BuildConfig.GIT_COMMIT_ID} (${appVersion.second})"

        val coroutineScope = rememberCoroutineScope()
        val iconRotation = remember { Animatable(0f) }

        ListItem(
            modifier = Modifier.clip(
                MaterialTheme.shapes.extraSmall.copy(
                    topStart = MaterialTheme.shapes.medium.topStart,
                    topEnd = MaterialTheme.shapes.medium.topEnd
                )
            ),
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                )
            },
            headlineContent = { Text("Version") },
            supportingContent = { Text(versionText) },
            trailingContent = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        iconRotation.animateTo(
                            targetValue = iconRotation.value + 360f,
                            animationSpec = tween(500)
                        )
                    }
                }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.rotate(iconRotation.value),
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(2.dp))

        val repositoryUrl = "https://github.com/Cdm2883/Dr.Sticker"
        ListItem(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, repositoryUrl.toUri())
                    context.startActivity(intent)
                },
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    modifier = Modifier.fillMaxHeight()
                )
            },
            headlineContent = { Text("Source") },
            supportingContent = { Text(repositoryUrl) },
        )

        Spacer(modifier = Modifier.height(2.dp))

        ListItem(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .clip(
                    MaterialTheme.shapes.extraSmall.copy(
                        bottomStart = MaterialTheme.shapes.medium.topStart,
                        bottomEnd = MaterialTheme.shapes.medium.topEnd
                    )
                ),
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Copyright,
                    contentDescription = null,
                    modifier = Modifier.fillMaxHeight()
                )
            },
            headlineContent = { Text("License") },
            supportingContent = { Text("GNU Affero General Public License v3.0") },
        )

        Spacer(modifier = Modifier.height(96.dp))
    }
}

fun getAppVersion(context: Context): Pair<String, Long> {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    else
        context.packageManager.getPackageInfo(context.packageName, 0)

    val versionName = packageInfo.versionName ?: "unknown"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        packageInfo.longVersionCode
    else
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    return versionName to versionCode
}
