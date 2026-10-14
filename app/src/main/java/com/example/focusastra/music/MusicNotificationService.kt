package com.example.focusastra.music

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.example.focusastra.FocusApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MusicNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val app get() = application as FocusApplication
    private val manager get() = getSystemService(MediaSessionManager::class.java)
    private var controller: MediaController? = null
    private var guard = TrackTransitionGuard()
    private var observing = false
    private var connectedJob: Job? = null
    private var confirmation: Job? = null
    private var pausePending = false
    private var pauseRetried = false
    private var stoppedByUs = false
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { onMetadata(metadata) }
        override fun onPlaybackStateChanged(state: PlaybackState?) { onPlayback(state) }
        override fun onSessionDestroyed() {
            detach()
            if (observing) refreshSessions()
        }
    }

    override fun onListenerConnected() {
        connectedJob?.cancel()
        connectedJob = scope.launch {
            app.store.state.map { it.loaded && it.musicEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) startObserving() else stopObserving()
            }
        }
    }

    private fun startObserving() {
        if (observing) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionListener, ComponentName(this, javaClass), handler)
            observing = true
            refreshSessions()
        } catch (_: SecurityException) { publish("Enable notification access") }
    }

    private fun refreshSessions() {
        try { attach(manager.getActiveSessions(ComponentName(this, javaClass))) }
        catch (_: SecurityException) { detach(); publish("Notification access is unavailable") }
    }

    private fun attach(controllers: List<MediaController>) {
        val youtubeMusic = controllers.filter { it.packageName == "com.google.android.apps.youtube.music" }
        val next = youtubeMusic.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: youtubeMusic.firstOrNull()
        if (next != null && next.sessionToken == controller?.sessionToken) return
        detach()
        controller = next
        if (next == null) { publish("Waiting for YouTube Music"); return }
        next.registerCallback(callback, handler)
        onMetadata(next.metadata)
        onPlayback(next.playbackState)
    }

    private fun onMetadata(metadata: MediaMetadata?) {
        metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank() && metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).isNullOrBlank()) return
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }
            ?: listOf(title, artist, metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()).joinToString("\u001f")
        if (title.isBlank() && artist.isBlank() && id.isBlank()) return
        val next = Track(id, title, artist, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
        val changed = guard.track?.id != next.id
        val shouldPause = guard.metadata(next, SystemClock.elapsedRealtime())
        if (changed) { confirmation?.cancel(); pausePending = false; stoppedByUs = false }
        if (shouldPause) pauseAtTransition()
        else {
            // Initialize position only after deciding using the previous track's sample.
            onPlayback(controller?.playbackState)
        }
    }

    private fun onPlayback(state: PlaybackState?) {
        if (state == null) { publish("Waiting for playback position"); return }
        val playing = state.state == PlaybackState.STATE_PLAYING
        guard.playback(PlaybackSample(state.position, state.lastPositionUpdateTime.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            playing, state.playbackSpeed), SystemClock.elapsedRealtime())
        if (pausePending) {
            if (state.state == PlaybackState.STATE_PAUSED || state.state == PlaybackState.STATE_STOPPED) {
                pausePending = false; stoppedByUs = true; confirmation?.cancel(); publish("Stopped after song")
            } else if (playing && !pauseRetried) {
                pauseRetried = true
                sendPause()
            }
            return
        }
        if (playing) stoppedByUs = false // Explicit resume arms the newly selected/current track.
        publish(when {
            stoppedByUs -> "Stopped after song"
            guard.track == null -> "Waiting for track details"
            guard.track!!.durationMs <= 0 -> "Duration unavailable · cannot detect ending"
            playing -> "Armed · best effort"
            state.state == PlaybackState.STATE_BUFFERING -> "Buffering"
            else -> "Paused"
        })
    }

    private fun pauseAtTransition() {
        pausePending = true; pauseRetried = false
        publish("Pausing next song…")
        sendPause()
        confirmation = scope.launch {
            delay(2_000)
            if (pausePending) { pausePending = false; publish("Pause not confirmed · check YouTube Music") }
        }
    }

    private fun sendPause() {
        try { controller?.transportControls?.pause() }
        catch (_: RuntimeException) { pausePending = false; publish("YouTube Music could not be paused") }
    }

    private fun publish(status: String) {
        app.musicStatus.value = MusicStatus(guard.track?.title.orEmpty(), guard.track?.artist.orEmpty(), status, observing)
    }

    private fun detach() {
        controller?.unregisterCallback(callback); controller = null
        confirmation?.cancel(); pausePending = false; stoppedByUs = false; guard = TrackTransitionGuard()
    }

    private fun stopObserving() {
        if (observing) manager.removeOnActiveSessionsChangedListener(sessionListener)
        observing = false; detach(); publish("Off")
    }

    override fun onListenerDisconnected() {
        connectedJob?.cancel(); stopObserving()
        if (app.store.state.value.musicEnabled) publish("Notification access disconnected")
        super.onListenerDisconnected()
    }
    override fun onDestroy() { connectedJob?.cancel(); stopObserving(); scope.cancel(); super.onDestroy() }
}
