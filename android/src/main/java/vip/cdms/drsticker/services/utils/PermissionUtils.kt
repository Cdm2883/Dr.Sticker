package vip.cdms.drsticker.services.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import rikka.shizuku.Shizuku
import vip.cdms.drsticker.services.AccessibilityService
import vip.cdms.drsticker.utils.vibrate

fun ensureOverlayPermissionGranted(context: Context) =
    isOverlayPermissionGranted(context)
        .also { if (!it) requestOverlayPermission(context) }

fun ensureBatteryOptimizationExemptionGranted(context: Context) =
    isBatteryOptimizationExemptionGranted(context)
        .also { if (!it) requestBatteryOptimizationExemption(context) }

fun ensureAccessibilityEnabled(
    context: Context,
    serviceClass: Class<*> = AccessibilityService::class.java
) = isAccessibilitySettingsOn(context, serviceClass)
    .also { if (!it) openAccessibilitySettings(context) }


fun isShizukuInstalled(context: Context) = try {
    context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

fun hasShizukuPermission() = try {
    Shizuku.pingBinder()
            && !Shizuku.isPreV11()
            && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (_: Exception) {
    false
}

fun requestShizukuPermission(context: Context, requestCode: Int = 1001) = runCatching {
    if (!Shizuku.pingBinder() || Shizuku.shouldShowRequestPermissionRationale())
        vibrate(context)
    else
        Shizuku.requestPermission(requestCode)
}


private fun isOverlayPermissionGranted(context: Context) =
    Settings.canDrawOverlays(context)

private fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri()
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun isBatteryOptimizationExemptionGranted(context: Context) =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife")
private fun requestBatteryOptimizationExemption(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri(),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun isAccessibilitySettingsOn(context: Context, serviceClass: Class<*>): Boolean {
    val resolver = context.applicationContext.contentResolver
    if (Settings.Secure
            .getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1
    ) return false

    val enabledServices = Settings.Secure.getString(
        resolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val targetService = "${context.packageName}/${serviceClass.canonicalName}"
    return enabledServices
        .split(':')
        .any { it.equals(targetService, ignoreCase = true) }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
