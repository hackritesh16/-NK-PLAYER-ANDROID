package com.nkplayer.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** [video] is set for files found on the device; [device] items are re-scanned, never saved. */
internal data class Track(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
    val video: Boolean? = null,
    val device: Boolean = false
) {
    fun saved() = SavedTrack(uri, name, mimeType)
}

internal fun isVideo(track: Track): Boolean =
    track.video ?: (track.mimeType?.startsWith("video/") == true || isVideo(track.uri))

internal fun isVideo(uri: String): Boolean {
    val path = Uri.parse(uri).lastPathSegment.orEmpty().lowercase()
    return listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp", ".m4v", ".mpeg", ".mpg", ".ts", ".ogv").any(path::endsWith)
}

internal fun formatSpeed(speed: Float): String =
    "%.2f".format(speed).trimEnd('0').trimEnd('.').trimEnd(',') + "x"

internal fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

internal fun queryDisplayName(context: Context, uri: Uri): String {
    val providerName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return providerName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        ?: "Media file"
}

internal fun playTrack(track: Track, queue: List<Track>, player: Player) {
    val full = queue.ifEmpty { listOf(track) }
    val fullIndex = full.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
    // With the whole device library listed, queue a window around the chosen item instead of thousands.
    val from = if (full.size > 500) (fullIndex - 100).coerceAtLeast(0) else 0
    val playableQueue = full.subList(from, minOf(full.size, from + 500))
    val startIndex = playableQueue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
    val mediaItems = playableQueue.map { item ->
        MediaItem.Builder().setUri(item.uri).apply {
            item.mimeType?.let { setMimeType(it) }
        }.build()
    }
    player.setMediaItems(mediaItems, startIndex, 0L)
    player.prepare()
    player.play()
}

/** Remembers where each video was left so it can resume. */
internal object ResumeStore {
    private fun prefs(context: Context) = context.getSharedPreferences("nk_resume", Context.MODE_PRIVATE)
    fun get(context: Context, uri: String): Long = prefs(context).getLong(uri, 0L)
    fun put(context: Context, uri: String, positionMs: Long) {
        prefs(context).edit().putLong(uri, positionMs).apply()
    }
    fun clear(context: Context, uri: String) {
        prefs(context).edit().remove(uri).apply()
    }
}
