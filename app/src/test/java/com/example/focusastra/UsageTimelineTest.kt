package com.example.focusastra

import com.example.focusastra.screentime.*
import com.example.focusastra.screentime.UsageSignal.Kind.*
import org.junit.Assert.*
import org.junit.Test

class UsageTimelineTest {
    @Test fun `switches and screen off end foreground sessions`() {
        val result = UsageTimeline.reconstruct(0, 100, null, listOf(
            UsageSignal(10, "a", RESUME), UsageSignal(30, "b", RESUME), UsageSignal(50, null, SCREEN_OFF)))
        assertEquals(listOf(UsageSlice("a", 10, 30), UsageSlice("b", 30, 50)), result.slices)
        assertNull(result.foreground)
    }
    @Test fun `duplicate resumes do not lose or double count usage`() {
        val result = UsageTimeline.reconstruct(0, 100, "a", listOf(UsageSignal(20, "a", RESUME), UsageSignal(40, "a", RESUME)))
        assertEquals(listOf(UsageSlice("a", 0, 100)), result.slices)
    }
    @Test fun `background event from prior activity does not close new app`() {
        val result = UsageTimeline.reconstruct(0, 100, "a", listOf(UsageSignal(20, "b", RESUME), UsageSignal(30, "a", PAUSE)))
        assertEquals(listOf(UsageSlice("a", 0, 20), UsageSlice("b", 20, 100)), result.slices)
    }
    @Test fun `reboot excludes powered off time`() {
        val result = UsageTimeline.reconstruct(0, 100, "a", listOf(UsageSignal(20, null, SHUTDOWN), UsageSignal(80, "a", RESUME)))
        assertEquals(listOf(UsageSlice("a", 0, 20), UsageSlice("a", 80, 100)), result.slices)
    }
    @Test fun `a checkpoint resumes a process killed mid session`() {
        assertEquals(listOf(UsageSlice("a", 30, 100)), UsageTimeline.reconstruct(30, 100, "a", emptyList()).slices)
    }
    @Test fun `screen off time is not usage`() {
        val result = UsageTimeline.reconstruct(0, 100, null, listOf(UsageSignal(10, "a", RESUME), UsageSignal(30, null, SCREEN_OFF), UsageSignal(80, "a", RESUME)))
        assertEquals(40L, result.slices.sumOf { it.end - it.start })
    }
}
