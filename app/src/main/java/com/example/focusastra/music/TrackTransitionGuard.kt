package com.example.focusastra.music

data class MusicStatus(val title: String = "", val artist: String = "", val status: String = "Off", val connected: Boolean = false)
data class Track(val id: String, val title: String, val artist: String, val durationMs: Long)
data class PlaybackSample(val positionMs: Long, val sampledAt: Long, val playing: Boolean, val speed: Float = 1f) {
    fun positionAt(now: Long): Long = if (positionMs < 0) -1 else positionMs +
        if (playing) ((now - sampledAt).coerceAtLeast(0) * speed.coerceAtLeast(0f)).toLong() else 0L
}

/** Android exposes no public transition reason for another app's media session. This is a heuristic. */
class TrackTransitionGuard {
    var track: Track? = null
        private set
    private var sample: PlaybackSample? = null
    private var recentEndAt: Long? = null

    fun playback(next: PlaybackSample, now: Long) {
        val old = sample
        val duration = track?.durationMs ?: 0
        if (old != null && nearEnd(old, duration, now) && next.positionMs in 0..2_000 && next.positionMs < old.positionAt(now) - 2_000) {
            // Some players deliver the new playback position before the new metadata.
            recentEndAt = now
        } else if (next.positionMs > 2_000 || !next.playing) recentEndAt = null
        sample = next
    }

    fun metadata(next: Track, now: Long): Boolean {
        val old = track
        if (old?.id == next.id) { track = next; return false }
        val recentlyEnded = recentEndAt?.let { now - it in 0..1_500 } == true
        val pause = old != null && (sample?.let { nearEnd(it, old.durationMs, now) } == true || recentlyEnded)
        track = next
        sample = null
        recentEndAt = null
        return pause
    }

    private fun nearEnd(p: PlaybackSample, duration: Long, now: Long): Boolean =
        p.playing && duration > 0 && p.positionMs >= 0 && p.positionAt(now) >= (duration - 1_500).coerceAtLeast(0)
}
