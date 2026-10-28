package com.nkplayer.app

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.nkPlayerStore by preferencesDataStore(name = "nk_player_settings")

data class SavedTrack(val uri: String, val name: String, val mimeType: String? = null)

class LibraryStore(private val context: Context) {
    private val libraryKey = stringPreferencesKey("library_json")
    private val themeKey = stringPreferencesKey("theme_name")

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
}
