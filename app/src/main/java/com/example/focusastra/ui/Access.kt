package com.example.focusastra.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.focusastra.screentime.FocusAccessibilityService
import com.example.focusastra.screentime.UsageHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AccessState(val usage: Boolean = false, val accessibility: Boolean = false, val notifications: Boolean = false)
data class InstalledApp(val packageName: String, val label: String)

fun Context.accessState(): AccessState {
    val component = ComponentName(this, FocusAccessibilityService::class.java)
    val accessibility = getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { ComponentName.unflattenFromString(it.id) == component }
    return AccessState(UsageHistory(this).hasAccess(), accessibility,
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName))
}

fun Context.openSettings(action: String) {
    try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Suppress("DEPRECATION")
suspend fun Context.installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val homePackages = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }.toSet()
    val dialer = packageManager.resolveActivity(Intent(Intent.ACTION_DIAL, "tel:".toUri()), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val excluded = homePackages + setOf(packageName, "com.android.settings", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", dialer)
    packageManager.queryIntentActivities(launcherIntent, 0).map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
        .distinctBy { it.packageName }.filterNot { it.packageName in excluded }.sortedBy { it.label.lowercase() }
}
