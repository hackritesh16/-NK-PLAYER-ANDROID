package com.nkplayer.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the imported library and the selected accent theme so they survive
 * configuration changes and are persisted in one place instead of inside a composable.
 */
internal class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LibraryStore(application)

    var tracks by mutableStateOf(emptyList<Track>())
        private set
    var themeName by mutableStateOf("Neon Blue")
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // Corrupt or inaccessible preferences must not terminate the player UI.
            val saved = runCatching { store.loadLibrary() }.getOrDefault(emptyList())
            val resolved = withContext(Dispatchers.IO) {
                saved.map { item ->
                    val mimeType = item.mimeType ?: runCatching {
                        getApplication<Application>().contentResolver.getType(Uri.parse(item.uri))
                    }.getOrNull()
                    Track(item.uri, item.name, mimeType)
                }
            }
            tracks = resolved
            themeName = runCatching { store.loadTheme() }.getOrDefault("Neon Blue")
            loaded = true
        }
    }

    fun addTracks(incoming: List<Track>) {
        if (incoming.isEmpty()) return
        tracks = (tracks + incoming).distinctBy { it.uri }
        persistLibrary()
    }

    fun removeTrack(track: Track) {
        tracks = tracks.filterNot { it.uri == track.uri }
        persistLibrary()
    }

    fun selectTheme(name: String) {
        if (name == themeName) return
        themeName = name
        viewModelScope.launch { runCatching { store.saveTheme(name) } }
    }

    private fun persistLibrary() {
        if (!loaded) return
        val snapshot = tracks.map { it.saved() }
        viewModelScope.launch { runCatching { store.saveLibrary(snapshot) } }
    }
}
