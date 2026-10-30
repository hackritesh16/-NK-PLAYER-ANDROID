package com.nkplayer.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the playback engine so playback can continue when the Activity is not visible.
 * The app connects to this service through a MediaController. The service also owns the
 * audio effects (equalizer, bass boost, virtualizer) and the sleep timer, and exposes them
 * as custom session commands.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var fx: AudioFxController? = null
    private var sleepTimer: SleepTimerController? = null

    override fun onCreate() {
        super.onCreate()
        // Optional vendor audio effects are attached when a real audio session
        // appears, rather than being created during cold app launch.
        var createdPlayer: ExoPlayer? = null
        try {
            val player = ExoPlayer.Builder(this).build()
            createdPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            player.setHandleAudioBecomingNoisy(true)
            player.setWakeMode(C.WAKE_MODE_LOCAL)

            val effects = AudioFxController(this)
            fx = effects
            val sleep = SleepTimerController { mediaSession?.player?.pause() }
            sleepTimer = sleep
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    runCatching { effects.attach(audioSessionId) }
                        .onFailure { android.util.Log.w("NKPlayer", "Audio effects unavailable", it) }
                }
            })

            val openApp = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(this, player)
                .setCallback(FxCallback(effects, sleep))
                .setSessionActivity(openApp)
                .build()
        } catch (error: Exception) {
            // Exceptions in Service.onCreate can kill the process before the
            // launcher can render a useful error. Keep the cause in logcat.
            android.util.Log.e("NKPlayer", "Playback service startup failed", error)
            runCatching { mediaSession?.release() }
            mediaSession = null
            runCatching { createdPlayer?.release() }
            runCatching { fx?.release() }
            fx = null
            sleepTimer?.cancel()
            sleepTimer = null
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        sleepTimer = null
        fx?.release()
        fx = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private class FxCallback(
        private val effects: AudioFxController,
        private val sleep: SleepTimerController
    ) : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply {
                    FxCommands.all.forEach { add(it) }
                    SleepCommands.all.forEach { add(it) }
                }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                SleepCommands.SET -> {
                    sleep.set(args.getInt("minutes"))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, sleep.state()))
                }
                SleepCommands.GET ->
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, sleep.state()))
            }
            when (customCommand.customAction) {
                FxCommands.ENABLE -> effects.setEnabled(args.getBoolean("enabled"))
                FxCommands.BAND -> effects.setBand(args.getInt("band"), args.getInt("level"))
                FxCommands.PRESET -> effects.usePreset(args.getInt("preset"))
                FxCommands.BASS -> effects.setBass(args.getInt("strength"))
                FxCommands.VIRTUALIZER -> effects.setVirtualizer(args.getInt("strength"))
                FxCommands.RESET -> effects.reset()
                FxCommands.GET -> Unit
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, effects.state()))
        }
    }
}
