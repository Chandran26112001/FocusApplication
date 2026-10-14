package com.example.focusastra.data

import java.util.Calendar
import java.util.TimeZone

const val MINUTE_MS = 60_000L

data class LimitPolicy(val minutes: Int = 30, val reminderMinutes: Int = 10, val resetMinute: Int = 0) {
    fun valid() = minutes in 1..1440 && reminderMinutes in 1..1440 && resetMinute in 0..1439
}

data class AppLimit(
    val packageName: String,
    val label: String,
    val policy: LimitPolicy,
    val periodStart: Long,
    val periodEnd: Long,
    val usedMs: Long = 0,
    val nextReminderMs: Long = policy.reminderMinutes * MINUTE_MS,
    val pendingPolicy: LimitPolicy? = null,
    val pendingRemoval: Boolean = false,
) {
    val allowanceMs get() = policy.minutes * MINUTE_MS
    val remainingMs get() = (allowanceMs - usedMs).coerceAtLeast(0)
    val exhausted get() = remainingMs == 0L
}

data class FocusTask(val id: String, val title: String, val completed: Boolean = false)
data class UsageCheckpoint(val wallMs: Long = 0, val elapsedMs: Long = 0, val bootCount: Int = -1, val activePackage: String? = null)
data class AppState(
    val limits: List<AppLimit> = emptyList(),
    val tasks: List<FocusTask> = emptyList(),
    val musicEnabled: Boolean = false,
    val accessibilityConsent: Boolean = false,
    val checkpoint: UsageCheckpoint = UsageCheckpoint(),
    val loaded: Boolean = false,
    val error: String? = null,
)

object Periods {
    fun containing(now: Long, resetMinute: Int, zone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, resetMinute / 60)
            set(Calendar.MINUTE, resetMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis > now) calendar.add(Calendar.DATE, -1)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DATE, 1)
        return start to calendar.timeInMillis
    }

    fun next(now: Long, resetMinute: Int, zone: TimeZone = TimeZone.getDefault()): Long = containing(now, resetMinute, zone).second
}

/** Pure timing rules, shared by the live monitor and recovery. No wall-clock rollback grants time. */
object LimitRules {
    fun roll(limit: AppLimit, now: Long, zone: TimeZone = TimeZone.getDefault()): AppLimit? {
        if (now < limit.periodEnd) return limit
        if (limit.pendingRemoval) return null
        val policy = limit.pendingPolicy ?: limit.policy
        val firstStart = limit.periodEnd
        val firstEnd = Periods.next(firstStart, policy.resetMinute, zone)
        val (start, end) = if (now < firstEnd) firstStart to firstEnd else Periods.containing(now, policy.resetMinute, zone)
        return limit.copy(policy = policy, periodStart = start, periodEnd = end, usedMs = 0,
            nextReminderMs = policy.reminderMinutes * MINUTE_MS, pendingPolicy = null, pendingRemoval = false)
    }

    fun charge(limit: AppLimit, from: Long, to: Long, duration: Long, zone: TimeZone = TimeZone.getDefault()): AppLimit? {
        if (duration <= 0) return roll(limit, to, zone)
        // Monotonic time is authoritative during a live session. A clock edit cannot add usage.
        if (to <= from || kotlin.math.abs((to - from) - duration) > 5_000) {
            return roll(limit.copy(usedMs = (limit.usedMs + duration).coerceAtMost(limit.allowanceMs)), to, zone)
        }
        var current = roll(limit, from, zone) ?: return null
        var cursor = from
        while (cursor < to) {
            val end = minOf(to, current.periodEnd)
            current = current.copy(usedMs = (current.usedMs + (end - cursor).coerceAtLeast(0)).coerceAtMost(current.allowanceMs))
            cursor = end
            if (cursor >= current.periodEnd) current = roll(current, cursor, zone) ?: return null
        }
        return current
    }

    fun queue(limit: AppLimit, policy: LimitPolicy?, remove: Boolean): AppLimit {
        require(!limit.exhausted) { "This limit is reached. Changes are locked until reset." }
        require(remove || policy?.valid() == true) { "Enter a valid allowance, reminder and reset time." }
        return limit.copy(pendingPolicy = if (remove) null else policy, pendingRemoval = remove)
    }
}
