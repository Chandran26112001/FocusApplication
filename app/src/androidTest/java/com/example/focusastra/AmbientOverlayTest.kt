package com.example.focusastra

import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focusastra.data.AppLimit
import com.example.focusastra.data.AppState
import com.example.focusastra.data.LimitPolicy
import com.example.focusastra.data.Periods
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses and restores the emulator's existing app state and special-access settings. */
@RunWith(AndroidJUnit4::class)
class AmbientOverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as FocusApplication
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val target = "com.google.android.deskclock"
    private var original: AppState? = null
    private var oldServices = ""
    private var oldAppOp = "default"
    private var oldNight = ""

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }

    @Before fun setup(): Unit = runBlocking {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        app.store.awaitReady()
        original = app.store.state.value
        oldServices = shell("settings get secure enabled_accessibility_services").takeUnless { it == "null" } ?: ""
        oldAppOp = Regex("GET_USAGE_STATS: (allow|ignore|deny|default|foreground)")
            .find(shell("appops get com.example.focusastra GET_USAGE_STATS"))?.groupValues?.get(1) ?: "default"
        oldNight = shell("cmd uimode night").substringAfter("Night mode: ").trim()
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val (start, end) = Periods.containing(System.currentTimeMillis(), 0)
        app.store.update { it.copy(accessibilityConsent = true,
            limits = listOf(AppLimit(target, "Clock", LimitPolicy(2, 10), start, end, 59_000))) }
        shell("appops set com.example.focusastra GET_USAGE_STATS allow")
        val service = "com.example.focusastra/com.example.focusastra.screentime.FocusAccessibilityService"
        shell("settings put secure enabled_accessibility_services ${listOf(oldServices, service).filter { it.isNotBlank() }.joinToString(":")}")
        shell("settings put secure accessibility_enabled 1")
        await { app.monitorConnected.value }
    }

    @After fun cleanup(): Unit = runBlocking {
        shell("input keyevent KEYCODE_HOME")
        original?.let { saved -> app.store.update { saved } }
        if (oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
        else shell("settings put secure enabled_accessibility_services $oldServices")
        shell("appops set com.example.focusastra GET_USAGE_STATS $oldAppOp")
        if (oldNight == "yes" || oldNight == "no") shell("cmd uimode night $oldNight")
        Unit
    }

    @Test fun progressAndPillAppearOnlyOnRestrictedAppAndPillStaysInBounds() {
        val intent = app.packageManager.getLaunchIntentForPackage(target) ?: error("Clock is required on this emulator")
        shell("am start -n ${intent.component!!.flattenToString()}")
        await { pillNode() != null }
        await { pillNode()?.text?.toString() == "1M LEFT" }
        val before = bounds(pillNode()!!)
        assertTrue(before.width() > 0 && before.height() > 0)
        assertTrue(shell("dumpsys window windows").contains("ClutchState app time progress"))
        capture("ambient-before.png")
        shell("input swipe ${before.centerX()} ${before.centerY()} 0 0 450")
        val leftInset = (16 * app.resources.displayMetrics.density + 0.5f).toInt()
        await { pillNode()?.let { bounds(it).left == leftInset } == true }
        val dragged = bounds(pillNode()!!)
        assertTrue(dragged.top >= 0)
        assertTrue(dragged.right <= app.resources.displayMetrics.widthPixels)
        capture("ambient-dragged.png")

        shell("cmd uimode night yes")
        await { pillNode() != null }
        SystemClock.sleep(4_000)
        capture("ambient-dark.png")
        shell("cmd uimode night no")
        await { pillNode() != null }
        SystemClock.sleep(2_000)

        shell("input keyevent KEYCODE_HOME")
        await { pillNode() == null && !shell("dumpsys window windows").contains("ClutchState app time progress") }
    }

    private fun pillNode(): AccessibilityNodeInfo? = automation.windows.asSequence()
        .filter { it.title?.toString() == "ClutchState time left, drag to move" }
        .mapNotNull { it.root }
        .firstOrNull()

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun capture(name: String) {
        automation.takeScreenshot()?.let { bitmap ->
            java.io.File(app.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Timed out; windows: ${automation.windows.map { it.title }}; monitor: ${app.monitorError.value}")
    }
}
