package com.example.focusastra.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsageSlice(val packageName: String, val start: Long, val end: Long)
data class UsageSignal(val time: Long, val packageName: String?, val kind: Kind) {
    enum class Kind { RESUME, PAUSE, SCREEN_OFF, SHUTDOWN }
}
data class UsageResult(val slices: List<UsageSlice>, val foreground: String?)

object UsageTimeline {
    fun reconstruct(from: Long, to: Long, initial: String?, signals: List<UsageSignal>): UsageResult {
        var active = initial
        var start = from
        val slices = mutableListOf<UsageSlice>()
        fun close(at: Long) { active?.let { if (at > start) slices += UsageSlice(it, start, at) }; start = at }
        signals.sortedBy { it.time }.filter { it.time in from..to }.forEach { event ->
            when (event.kind) {
                UsageSignal.Kind.RESUME -> if (event.packageName != active) { close(event.time); active = event.packageName }
                UsageSignal.Kind.PAUSE -> if (active == event.packageName) { close(event.time); active = null }
                UsageSignal.Kind.SCREEN_OFF, UsageSignal.Kind.SHUTDOWN -> { close(event.time); active = null }
            }
        }
        close(to)
        return UsageResult(slices, active)
    }
}

class UsageHistory(private val context: Context) {
    fun hasAccess(): Boolean {
        val manager = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        return manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    suspend fun read(from: Long, to: Long, initial: String? = null): UsageResult = withContext(Dispatchers.IO) {
        check(hasAccess()) { "Usage access is off. Restore it in Settings for restart recovery." }
        if (from >= to) return@withContext UsageResult(emptyList(), initial)
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, to)
            ?: error("Android usage history is unavailable while the device is locked.")
        val event = UsageEvents.Event()
        val signals = mutableListOf<UsageSignal>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageSignal.Kind.RESUME
                UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageSignal.Kind.PAUSE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> UsageSignal.Kind.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> UsageSignal.Kind.SHUTDOWN
                else -> null
            }
            if (kind != null) signals += UsageSignal(event.timeStamp, event.packageName, kind)
        }
        UsageTimeline.reconstruct(from, to, initial, signals)
    }
}
