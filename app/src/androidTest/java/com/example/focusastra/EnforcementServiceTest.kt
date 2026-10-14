package com.example.focusastra

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focusastra.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on a disposable emulator: these tests temporarily enable the app's special access. */
@RunWith(AndroidJUnit4::class)
class EnforcementServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as FocusApplication
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val target = "com.google.android.deskclock"
    private var oldServices = ""

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }

    @Before fun setup(): Unit = runBlocking {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        app.store.awaitReady()
        app.store.update { it.copy(accessibilityConsent = true, limits = emptyList(), tasks = emptyList()) }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("appops set com.example.focusastra GET_USAGE_STATS allow")
        oldServices = shell("settings get secure enabled_accessibility_services").let { if (it == "null") "" else it }
        shell("settings put secure enabled_accessibility_services com.example.focusastra/com.example.focusastra.screentime.FocusAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        await { app.monitorConnected.value }
    }

    @After fun cleanup(): Unit = runBlocking {
        shell("input keyevent KEYCODE_HOME")
        app.store.update { it.copy(limits = emptyList(), tasks = emptyList()) }
        if (oldServices.isBlank()) shell("settings delete secure enabled_accessibility_services")
        else shell("settings put secure enabled_accessibility_services $oldServices")
        shell("appops set com.example.focusastra GET_USAGE_STATS default")
        Unit
    }

    @Test fun limitExpiresWhileAppOpenAndBlocksReentry() = runBlocking {
        val (start, end) = Periods.containing(System.currentTimeMillis(), 0)
        app.store.update { it.copy(limits = listOf(AppLimit(target, "Clock", LimitPolicy(1, 10), start, end, 59_000))) }
        openTarget()
        await { overlayNode("Daily limit reached") != null }
        assertTrue(app.store.state.value.limits.single().exhausted)
        assertNull(overlayNode("Continue"))
        clickOverlay("Go Home")
        await { overlayNode("Daily limit reached") == null }
        openTarget()
        await { overlayNode("Daily limit reached") != null }
        assertNull(app.monitorError.value)
    }

    @Test fun reminderContinuesWithoutResettingUsage() = runBlocking {
        val (start, end) = Periods.containing(System.currentTimeMillis(), 0)
        app.store.update { it.copy(tasks = listOf(FocusTask("reminder", "Prepare interview")),
            limits = listOf(AppLimit(target, "Clock", LimitPolicy(2, 1), start, end, 59_000))) }
        openTarget()
        await { overlayNode("Before you keep scrolling…") != null }
        assertNotNull(overlayNode("Prepare interview"))
        val usedAtReminder = app.store.state.value.limits.single().usedMs
        assertTrue(usedAtReminder >= 60_000)
        SystemClock.sleep(500)
        assertEquals(usedAtReminder, app.store.state.value.limits.single().usedMs)
        clickOverlay("Continue")
        await { overlayNode("Before you keep scrolling…") == null && app.store.state.value.checkpoint.activePackage == target }
        shell("input keyevent KEYCODE_HOME")
        await { app.store.state.value.checkpoint.activePackage == null }
        assertTrue(app.store.state.value.limits.single().usedMs >= usedAtReminder)
        assertNull(app.monitorError.value)
    }

    private fun openTarget() {
        val intent = app.packageManager.getLaunchIntentForPackage(target) ?: error("Install Google Clock in the disposable emulator")
        shell("am start -n ${intent.component!!.flattenToString()}")
    }

    private fun overlayNode(text: String): AccessibilityNodeInfo? {
        return automation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == app.packageName }
            .flatMap { it.findAccessibilityNodeInfosByText(text).asSequence() }
            .firstOrNull { it.text?.toString() == text }
    }
    private fun clickOverlay(text: String) {
        var node = overlayNode(text) ?: error("Missing overlay button: $text")
        while (!node.isClickable) node = node.parent ?: error("No clickable parent")
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        automation.takeScreenshot()?.let { screenshot ->
            java.io.File(app.getExternalFilesDir(null), "test-failure.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        error("Timed out; windows: ${automation.windows.map { it.title.toString() + ":" + it.root?.packageName }}; monitor error: ${app.monitorError.value}; state: ${app.store.state.value}")
    }
}
