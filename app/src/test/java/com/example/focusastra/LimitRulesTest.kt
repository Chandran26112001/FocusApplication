package com.example.focusastra

import com.example.focusastra.data.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.TimeZone

class LimitRulesTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(text: String) = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = zone }.parse(text)!!.time
    private fun limit(minutes: Int = 30) = AppLimit("social", "Social", LimitPolicy(minutes), at("2026-10-02 00:00"), at("2026-10-03 00:00"))

    @Test fun `foreground sessions accumulate independently`() {
        var l = limit()
        for ((start, minutes) in listOf("2026-10-02 08:00" to 10, "2026-10-02 12:00" to 8, "2026-10-02 18:00" to 12)) {
            l = LimitRules.charge(l, at(start), at(start) + minutes * MINUTE_MS, minutes * MINUTE_MS, zone)!!
        }
        assertTrue(l.exhausted)
        assertEquals(30 * MINUTE_MS, l.usedMs)
    }
    @Test fun `usage never exceeds allowance`() {
        assertEquals(30 * MINUTE_MS, LimitRules.charge(limit(), at("2026-10-02 12:00"), at("2026-10-02 13:00"), 60 * MINUTE_MS, zone)!!.usedMs)
    }
    @Test fun `midnight splits active foreground usage`() {
        val l = LimitRules.charge(limit(), at("2026-10-02 23:58"), at("2026-10-03 00:03"), 5 * MINUTE_MS, zone)!!
        assertEquals(3 * MINUTE_MS, l.usedMs)
        assertEquals(at("2026-10-03 00:00"), l.periodStart)
    }
    @Test fun `current period settings cannot be changed`() {
        val queued = LimitRules.queue(limit().copy(usedMs = 10 * MINUTE_MS), LimitPolicy(60, 5, 360), false)
        assertEquals(30, queued.policy.minutes)
        assertEquals(10 * MINUTE_MS, queued.usedMs)
        assertEquals(60, queued.pendingPolicy!!.minutes)
    }
    @Test(expected = IllegalArgumentException::class) fun `cannot queue changes after reaching limit`() {
        LimitRules.queue(limit().copy(usedMs = 30 * MINUTE_MS), LimitPolicy(180), false)
    }
    @Test(expected = IllegalArgumentException::class) fun `cannot remove exhausted restriction`() {
        LimitRules.queue(limit().copy(usedMs = 30 * MINUTE_MS), null, true)
    }
    @Test fun `pending policy applies at original reset`() {
        val queued = LimitRules.queue(limit(), LimitPolicy(60, 5, 360), false)
        assertEquals(30, LimitRules.roll(queued, at("2026-10-02 23:59"), zone)!!.policy.minutes)
        val next = LimitRules.roll(queued, at("2026-10-03 00:00"), zone)!!
        assertEquals(60, next.policy.minutes)
        assertEquals(at("2026-10-03 06:00"), next.periodEnd)
        assertEquals(5 * MINUTE_MS, next.nextReminderMs)
        assertNull(next.pendingPolicy)
    }
    @Test fun `scheduled removal happens at reset only`() {
        val l = LimitRules.queue(limit(), null, true)
        assertNotNull(LimitRules.roll(l, at("2026-10-02 23:59"), zone))
        assertNull(LimitRules.roll(l, at("2026-10-03 00:00"), zone))
    }
    @Test fun `many missed days produce latest period`() {
        val l = LimitRules.roll(limit().copy(usedMs = 30 * MINUTE_MS), at("2026-10-20 14:00"), zone)!!
        assertEquals(0, l.usedMs)
        assertEquals(at("2026-10-20 00:00"), l.periodStart)
    }
    @Test fun `backward clock never replenishes time`() {
        val l = limit().copy(usedMs = 29 * MINUTE_MS)
        val charged = LimitRules.charge(l, at("2026-10-02 18:00"), at("2026-10-02 10:00"), MINUTE_MS, zone)!!
        assertTrue(charged.exhausted)
        assertEquals(l.periodEnd, charged.periodEnd)
    }
    @Test fun `timezone edit keeps existing reset instant`() {
        val l = limit()
        assertEquals(l.periodEnd, LimitRules.roll(l, at("2026-10-02 10:00"), TimeZone.getTimeZone("America/New_York"))!!.periodEnd)
    }
    @Test fun `custom reset before boundary belongs to previous day`() {
        assertEquals(at("2026-10-01 06:00"), Periods.containing(at("2026-10-02 05:00"), 360, zone).first)
    }
    @Test fun `spring DST period has 23 hours`() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = ny }.parse("2026-03-08 12:00")!!.time
        val (start, end) = Periods.containing(now, 0, ny)
        assertEquals(23 * 60 * MINUTE_MS, end - start)
    }
    @Test fun `fall DST period has 25 hours`() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = ny }.parse("2026-11-01 12:00")!!.time
        val (start, end) = Periods.containing(now, 0, ny)
        assertEquals(25 * 60 * MINUTE_MS, end - start)
    }
}
