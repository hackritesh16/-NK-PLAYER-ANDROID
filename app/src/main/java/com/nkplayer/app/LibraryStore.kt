package com.nkplayer.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.nkPlayerStore by preferencesDataStore(name = "nk_player_settings")

data class SavedTrack(val uri: String, val name: String, val mimeType: String? = null)

/**
 * A media item remembered by the Home dashboard: last played (with resume position), play history,
 * favourites and playlist entries. [positionMs]/[durationMs] are only meaningful for the last-played item;
 * [at] is the time it was played (history) or 0.
 */
data class StoredItem(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
    val video: Boolean? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val at: Long = 0L
)

data class NkPlaylist(val id: String, val name: String, val tracks: List<StoredItem> = emptyList())

class LibraryStore(private val context: Context) {
    private val libraryKey = stringPreferencesKey("library_json")
    private val themeKey = stringPreferencesKey("theme_name")
    private val lastPlayedKey = stringPreferencesKey("last_played_json")
    private val historyKey = stringPreferencesKey("history_json")
    private val favoritesKey = stringPreferencesKey("favorites_json")
    private val playlistsKey = stringPreferencesKey("playlists_json")

    suspend fun loadLibrary(): List<SavedTrack> {
        val raw = context.nkPlayerStore.data.first()[libraryKey] ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(SavedTrack(item.getString("uri"), item.getString("name"), item.optString("mimeType").takeIf { it.isNotBlank() }))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveLibrary(items: List<SavedTrack>) {
        val array = JSONArray()
        items.distinctBy { it.uri }.forEach { track ->
            val item = JSONObject().put("uri", track.uri).put("name", track.name)
            track.mimeType?.let { item.put("mimeType", it) }
            array.put(item)
        }
        context.nkPlayerStore.edit { it[libraryKey] = array.toString() }
    }

    suspend fun loadTheme(): String =
        context.nkPlayerStore.data.first()[themeKey] ?: "Neon Blue"

    suspend fun saveTheme(theme: String) {
        context.nkPlayerStore.edit { it[themeKey] = theme }
    }

    // ---- Home dashboard data -------------------------------------------------------------

    suspend fun loadLastPlayed(): StoredItem? =
        parseItems("[" + (context.nkPlayerStore.data.first()[lastPlayedKey] ?: "") + "]").firstOrNull()

    suspend fun saveLastPlayed(item: StoredItem) {
        context.nkPlayerStore.edit { it[lastPlayedKey] = itemToJson(item).toString() }
    }

    suspend fun loadHistory(): List<StoredItem> =
        parseItems(context.nkPlayerStore.data.first()[historyKey])

    suspend fun saveHistory(items: List<StoredItem>) {
        context.nkPlayerStore.edit { it[historyKey] = itemsToJson(items).toString() }
    }

    suspend fun loadFavorites(): List<StoredItem> =
        parseItems(context.nkPlayerStore.data.first()[favoritesKey])

    suspend fun saveFavorites(items: List<StoredItem>) {
        context.nkPlayerStore.edit { it[favoritesKey] = itemsToJson(items).toString() }
    }

    suspend fun loadPlaylists(): List<NkPlaylist> {
        val raw = context.nkPlayerStore.data.first()[playlistsKey] ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        NkPlaylist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            tracks = parseItems(obj.optJSONArray("tracks")?.toString())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun savePlaylists(playlists: List<NkPlaylist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", itemsToJson(p.tracks)))
        }
        context.nkPlayerStore.edit { it[playlistsKey] = array.toString() }
    }
}

private fun itemToJson(item: StoredItem): JSONObject {
    val obj = JSONObject().put("uri", item.uri).put("name", item.name)
    item.mimeType?.let { obj.put("mimeType", it) }
    item.video?.let { obj.put("video", it) }
    obj.put("pos", item.positionMs).put("dur", item.durationMs).put("at", item.at)
    return obj
}

private fun itemsToJson(items: List<StoredItem>): JSONArray {
    val array = JSONArray()
    items.forEach { array.put(itemToJson(it)) }
    return array
}

private fun parseItems(raw: String?): List<StoredItem> = runCatching {
    val array = JSONArray(raw ?: "[]")
    buildList {
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            add(
                StoredItem(
                    uri = obj.getString("uri"),
                    name = obj.getString("name"),
                    mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() },
                    video = if (obj.has("video")) obj.optBoolean("video") else null,
                    positionMs = obj.optLong("pos", 0L),
                    durationMs = obj.optLong("dur", 0L),
                    at = obj.optLong("at", 0L)
                )
            )
        }
    }
}.getOrDefault(emptyList())
