package com.example.focusastra

import com.example.focusastra.music.*
import org.junit.Assert.*
import org.junit.Test

class MusicTransitionTest {
    private val a = Track("a", "Song A", "Artist", 180_000)
    private val b = Track("b", "Song B", "Artist", 240_000)
    private fun guard(position: Long, playing: Boolean = true) = TrackTransitionGuard().apply {
        metadata(a, 1_000); playback(PlaybackSample(position, 1_000, playing), 1_000)
    }
    @Test fun `first song always allowed`() { assertFalse(TrackTransitionGuard().metadata(a, 1_000)) }
    @Test fun `manual early selection allowed`() { assertFalse(guard(60_000).metadata(b, 1_100)) }
    @Test fun `natural end uses extrapolated playback position`() { assertTrue(guard(170_000).metadata(b, 11_000)) }
    @Test fun `unknown duration does not guess`() {
        val g = TrackTransitionGuard(); g.metadata(a.copy(durationMs = 0), 1_000)
        g.playback(PlaybackSample(170_000, 1_000, true), 1_000)
        assertFalse(g.metadata(b, 11_000))
    }
    @Test fun `paused track does not advance with clock`() { assertFalse(guard(30_000, false).metadata(b, 300_000)) }
    @Test fun `seek near ending arms new end`() {
        val g = guard(60_000)
        g.playback(PlaybackSample(178_000, 2_000, true), 2_000)
        assertTrue(g.metadata(b, 4_000))
    }
    @Test fun `position callback arriving before metadata preserves ending`() {
        val g = guard(178_000)
        g.playback(PlaybackSample(0, 3_000, true), 3_000)
        assertTrue(g.metadata(b, 3_100))
    }
    @Test fun `same ID metadata updates never cause pause`() { assertFalse(guard(179_000).metadata(a.copy(title = "Updated title"), 2_000)) }
    @Test fun `unknown position is never treated as ending`() { assertFalse(guard(-1).metadata(b, 900_000)) }
    @Test fun `seek back evidence expires before later manual change`() {
        val g = guard(179_000)
        g.playback(PlaybackSample(0, 2_000, true), 2_000)
        assertFalse(g.metadata(b, 5_000))
    }
    @Test fun `final second manual change is known ambiguity`() { assertTrue(guard(179_000).metadata(b, 1_100)) }
}
