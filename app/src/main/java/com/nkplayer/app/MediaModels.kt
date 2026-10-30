package com.nkplayer.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

internal data class Track(val uri: String, val name: String, val mimeType: String? = null) {
    fun saved() = SavedTrack(uri, name, mimeType)

    /** File name without a short trailing extension, e.g. "Song.mp3" -> "Song". */
    val title: String
        get() {
            val dot = name.lastIndexOf('.')
            return if (dot > 0 && name.length - dot <= 5) name.substring(0, dot) else name
        }
}

internal fun playTrack(track: Track, queue: List<Track>, player: Player) {
    val playableQueue = queue.ifEmpty { listOf(track) }
    val startIndex = playableQueue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
    val mediaItems = playableQueue.map { item ->
        MediaItem.Builder()
            .setUri(item.uri)
            .setMediaId(item.uri)
            // Title metadata is what the notification, lock screen and Bluetooth displays show.
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setDisplayTitle(item.title)
                    .setIsPlayable(true)
                    .build()
            )
            .apply { item.mimeType?.let { setMimeType(it) } }
            .build()
    }
    player.setMediaItems(mediaItems, startIndex, 0L)
    player.prepare()
    player.play()
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

internal fun isVideo(track: Track): Boolean =
    track.mimeType?.startsWith("video/") == true || isVideo(track.uri)

internal fun isVideo(uri: String): Boolean {
    val path = Uri.parse(uri).lastPathSegment.orEmpty().lowercase()
    return listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp", ".m4v", ".mpeg", ".mpg", ".ts", ".ogv").any(path::endsWith)
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
