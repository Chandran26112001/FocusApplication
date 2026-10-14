package com.example.focusastra.screentime

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.example.focusastra.FocusApplication
import com.example.focusastra.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** System-bound, event-driven monitor. No foreground service, polling, wake lock or alarm. */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private lateinit var app: FocusApplication
    private lateinit var history: UsageHistory
    private lateinit var overlay: FocusOverlay
    private var deadline: Job? = null
    private var foreground: String? = null
    private var checkpoint = UsageCheckpoint()
    private var overlayPackage: String? = null
    private var initialized = false
    private var registered = false
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> events.trySend(Event.Switch(null))
                else -> events.trySend(Event.Refresh)
            }
        }
    }

    private sealed interface Event {
        data class Switch(val pkg: String?, val wall: Long = System.currentTimeMillis(), val elapsed: Long = SystemClock.elapsedRealtime()) : Event
        data object Refresh : Event
        data object Continue : Event
        data object Home : Event
    }

    override fun onServiceConnected() {
        if (initialized) return
        initialized = true
        app = application as FocusApplication
        history = UsageHistory(this)
        overlay = FocusOverlay(this, app, { events.trySend(Event.Continue) }, { events.trySend(Event.Home) })
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_SHUTDOWN)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        scope.launch {
            app.store.awaitReady()
            if (!app.store.state.value.accessibilityConsent) {
                app.monitorError.value = "Open FocusAstra and accept the accessibility disclosure before enabling enforcement."
                disableSelf()
                return@launch
            }
            try { recover() } catch (e: Exception) { app.monitorError.value = e.message ?: "Usage recovery failed." }
            app.monitorConnected.value = true
            events.trySend(Event.Refresh)
            for (event in events) {
                try { handle(event) } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    app.monitorError.value = e.message ?: "Enforcement needs attention."
                    val limited = app.store.state.value.limits.firstOrNull { it.packageName == foreground }
                    if (limited != null) {
                        overlayPackage = limited.packageName
                        overlay.show(limited, blocked = true, problem = "Enforcement cannot read or save local data. Return Home and reopen FocusAstra.")
                    }
                }
            }
        }
        scope.launch {
            app.store.state.map { s -> s.limits.map { Triple(it.packageName, it.policy, it.pendingPolicy to it.pendingRemoval) } }
                .distinctUntilChanged().collect { events.trySend(Event.Refresh) }
        }
    }

    private fun bootCount() = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun usableScreen() = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private suspend fun recover() {
        val state = app.store.state.value
        val now = System.currentTimeMillis()
        val previous = state.checkpoint
        val oldest = state.limits.minOfOrNull { it.periodStart } ?: now
        val from = maxOf(previous.wallMs, oldest)
        val initial = if (previous.bootCount == bootCount()) previous.activePackage else null
        val recovered = if (previous.wallMs > 0 && from < now && state.limits.isNotEmpty()) history.read(from, now, initial) else UsageResult(emptyList(), null)
        foreground = if (usableScreen()) focusedPackage() ?: recovered.foreground else null
        checkpoint = UsageCheckpoint(now, SystemClock.elapsedRealtime(), bootCount(), foreground?.takeIf { pkg -> state.limits.any { it.packageName == pkg } })
        app.store.update { s ->
            var limits = s.limits
            recovered.slices.forEach { slice -> limits = limits.mapNotNull { l ->
                if (l.packageName == slice.packageName) LimitRules.charge(l, slice.start, slice.end, slice.end - slice.start) else l
            } }
            s.copy(limits = limits.mapNotNull { LimitRules.roll(it, now) }, checkpoint = checkpoint)
        }
    }

    private suspend fun handle(event: Event) {
        val now = if (event is Event.Switch) event.wall else System.currentTimeMillis()
        val elapsed = if (event is Event.Switch) event.elapsed else SystemClock.elapsedRealtime()
        // Events captured during startup recovery may already be included in UsageStats history.
        if (elapsed < checkpoint.elapsedMs) return
        val duration = if (checkpoint.wallMs > 0) (elapsed - checkpoint.elapsedMs).coerceAtLeast(0) else 0
        val from = checkpoint.wallMs
        val prior = checkpoint.activePackage
        var next = foreground
        when (event) {
            is Event.Switch -> next = event.pkg
            Event.Refresh -> next = if (usableScreen()) focusedPackage() ?: foreground else null
            Event.Continue -> { overlay.hide(); overlayPackage = null }
            Event.Home -> { performGlobalAction(GLOBAL_ACTION_HOME); next = null; overlay.hide(); overlayPackage = null }
        }
        if (next != foreground || !usableScreen()) { overlay.hide(); overlayPackage = null }
        foreground = if (usableScreen()) next else null
        val counted = if (overlayPackage == null) foreground?.takeIf { pkg -> app.store.state.value.limits.any { it.packageName == pkg } } else null
        checkpoint = UsageCheckpoint(now, elapsed, bootCount(), counted)
        app.store.update { s ->
            val limits = s.limits.mapNotNull { l ->
                if (l.packageName == prior) LimitRules.charge(l, from, now, duration) else LimitRules.roll(l, now)
            }
            if (prior == null && counted == null && limits == s.limits) s else s.copy(limits = limits, checkpoint = checkpoint)
        }
        enforce()
    }

    private suspend fun enforce() {
        deadline?.cancel()
        val state = app.store.state.value
        val limit = state.limits.firstOrNull { it.packageName == foreground }
        if (limit == null || !usableScreen()) { overlay.hide(); overlayPackage = null; return }
        val blocked = limit.exhausted
        val reminder = !blocked && limit.usedMs >= limit.nextReminderMs && overlayPackage == null
        if (blocked || reminder) {
            val hasTasks = state.tasks.any { !it.completed }
            if (blocked || hasTasks) {
                overlayPackage = limit.packageName
                overlay.show(limit, blocked)
                checkpoint = checkpoint.copy(activePackage = null)
            }
            app.store.update { s -> s.copy(checkpoint = checkpoint, limits = s.limits.map { l ->
                if (reminder && l.packageName == limit.packageName) l.copy(nextReminderMs = l.usedMs + l.policy.reminderMinutes * MINUTE_MS) else l
            }) }
        } else if (overlayPackage != null && overlay.isBlocked) {
            overlay.hide(); overlayPackage = null
            checkpoint = checkpoint.copy(activePackage = foreground)
            app.store.update { it.copy(checkpoint = checkpoint) }
        }
        val current = app.store.state.value.limits.firstOrNull { it.packageName == foreground } ?: return
        val untilReset = (current.periodEnd - System.currentTimeMillis()).coerceAtLeast(1)
        val wait = if (overlayPackage != null) untilReset else minOf(untilReset, current.remainingMs,
            (current.nextReminderMs - current.usedMs).coerceAtLeast(1)).coerceAtLeast(1)
        deadline = scope.launch { delay(wait); events.send(Event.Refresh) }
    }

    @Suppress("DEPRECATION")
    private fun focusedPackage(): String? {
        // Inspect only the package of application windows. Never read text, labels, fields or descendants.
        val candidates = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val window = candidates.firstOrNull { it.isFocused } ?: candidates.firstOrNull { it.isActive }
            ?: candidates.maxByOrNull { it.layer } ?: return null
        val root = window.root ?: return null
        return try { root.packageName?.toString() } finally { root.recycle() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!initialized || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = if (usableScreen()) focusedPackage() else null
        // Our overlay never counts as application usage and must not dismiss itself.
        if (pkg != foreground) events.trySend(Event.Switch(pkg))
    }

    override fun onInterrupt() { if (initialized) events.trySend(Event.Refresh) }
    override fun onDestroy() {
        if (initialized) {
            app.monitorConnected.value = false
            overlay.destroy()
            if (registered) unregisterReceiver(receiver)
        }
        scope.cancel()
        super.onDestroy()
    }
}
