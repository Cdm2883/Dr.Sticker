package vip.cdms.drsticker.services

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConditionContext @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val selfPackageName = context.packageName
    private val packageManager = context.packageManager

    var packageName: String? = null
        private set

    var activityName: String? = null
        private set

    internal fun update(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return false
        val packageName = event.packageName?.toString() ?: return false
        val className = event.className?.toString() ?: return false
        if (packageName == selfPackageName || packageName == "android") return false

        try {
            val componentName = ComponentName(packageName, className)
            packageManager.getActivityInfo(componentName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }

        this.packageName = packageName
        this.activityName = className
        Log.i("ConditionContext", "packageName = $packageName; activityName = $activityName;")
        return true
    }

    internal fun reset() {
        packageName = null
        activityName = null
    }
}
