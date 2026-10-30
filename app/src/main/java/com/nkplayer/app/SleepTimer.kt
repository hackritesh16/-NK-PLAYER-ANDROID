package com.nkplayer.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.MoreExecutors

/** Custom session commands for the sleep timer, shared by the playback service and the UI. */
object SleepCommands {
    const val SET = "nk.sleep.set"
    const val GET = "nk.sleep.get"

    val all: List<SessionCommand> = listOf(SET, GET).map { SessionCommand(it, Bundle.EMPTY) }
}

/**
 * Countdown that pauses playback when it fires. It lives in [PlaybackService], not in the UI,
 * so it keeps working after the Activity is closed or recreated.
 */
internal class SleepTimerController(private val onFire: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var endsAtMillis = 0L
    private val fire = Runnable {
        endsAtMillis = 0L
        onFire()
    }

    /** Starts (or restarts) the timer; 0 or less cancels it. Returns the wall-clock end time or 0. */
    fun set(minutes: Int): Long {
        handler.removeCallbacks(fire)
        if (minutes <= 0) {
            endsAtMillis = 0L
            return 0L
        }
        val delayMillis = minutes * 60_000L
        endsAtMillis = System.currentTimeMillis() + delayMillis
        handler.postDelayed(fire, delayMillis)
        return endsAtMillis
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        endsAtMillis = 0L
    }

    fun state(): Bundle = Bundle().apply { putLong(KEY_ENDS_AT, endsAtMillis) }

    companion object {
        const val KEY_ENDS_AT = "endsAt"
    }
}

private fun requestSleepCommand(player: Player, action: String, extras: Bundle, onResult: (Long) -> Unit) {
    val controller = player as? MediaController ?: return
    val future = controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), extras)
    future.addListener({
        val endsAt = runCatching { future.get() }.getOrNull()
            ?.takeIf { it.resultCode == SessionResult.RESULT_SUCCESS }
            ?.extras?.getLong(SleepTimerController.KEY_ENDS_AT, 0L)
        if (endsAt != null) onResult(endsAt)
    }, MoreExecutors.directExecutor())
}

/** Asks the service for the current timer end time (0 = no timer). */
internal fun requestSleep(player: Player, onResult: (Long) -> Unit) =
    requestSleepCommand(player, SleepCommands.GET, Bundle.EMPTY, onResult)

/** Starts the sleep timer in the service; minutes <= 0 cancels it. */
internal fun sendSleep(player: Player, minutes: Int, onResult: (Long) -> Unit) =
    requestSleepCommand(player, SleepCommands.SET, Bundle().apply { putInt("minutes", minutes) }, onResult)
