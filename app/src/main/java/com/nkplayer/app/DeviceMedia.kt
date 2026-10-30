package com.nkplayer.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One audio or video file found on the device through MediaStore. */
data class DeviceItem(val uri: String, val name: String, val mimeType: String?, val video: Boolean)

/**
 * Finds every audio and video file on the phone via MediaStore. This only needs the media
 * permissions (READ_MEDIA_AUDIO / READ_MEDIA_VIDEO, or READ_EXTERNAL_STORAGE on Android 12 and
 * older); the broad "all files access" permission is not required.
 */
object DeviceMedia {
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(context: Context): Boolean =
        requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    suspend fun scan(context: Context): List<DeviceItem> = withContext(Dispatchers.IO) {
        val found = ArrayList<DeviceItem>()
        runCatching { found += query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, video = false) }
        runCatching { found += query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true) }
        found.sortedBy { it.name.lowercase() }
    }

    private fun query(context: Context, collection: Uri, video: Boolean): List<DeviceItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        // Skip ringtones, notification sounds, alarms and clips shorter than 10 seconds.
        val selection = if (video) null else {
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
                "${MediaStore.Audio.Media.IS_ALARM} = 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        }
        val items = ArrayList<DeviceItem>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                items += DeviceItem(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: "Media file",
                    mimeType = cursor.getString(mimeColumn),
                    video = video
                )
            }
        }
        return items
    }
}
