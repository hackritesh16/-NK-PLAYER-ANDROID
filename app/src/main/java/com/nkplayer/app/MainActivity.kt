package com.nkplayer.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Ink = Color(0xFF080B14)
private val Panel = Color(0xFF131A2A)
private val Cyan = Color(0xFF48E5FF)
private val Violet = Color(0xFF9B72FF)
private data class Track(val uri: String, val name: String) {
    fun saved() = SavedTrack(uri, name)
}

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            NkPlayerApp(
                player = player,
                store = LibraryStore(this),
                onPersistPermission = { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NkPlayerApp(player: ExoPlayer, store: LibraryStore, onPersistPermission: (Uri) -> Unit) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var themeName by remember { mutableStateOf("Neon Blue") }
    var libraryLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        tracks = store.loadLibrary().map { Track(it.uri, it.name) }
        themeName = store.loadTheme()
        libraryLoaded = true
    }
    LaunchedEffect(tracks, libraryLoaded) {
        if (libraryLoaded) store.saveLibrary(tracks.map { it.saved() })
    }
    LaunchedEffect(themeName) { store.saveTheme(themeName) }

    val accent = when (themeName) {
        "Cyber Purple" -> Violet
        "Emerald Green" -> Color(0xFF37E6A4)
        "Crimson Red" -> Color(0xFFFF5470)
        "Golden Amber" -> Color(0xFFFFC45C)
        else -> Cyan
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val incoming = uris.orEmpty().map { uri ->
            onPersistPermission(uri)
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() } ?: "Media file"
            Track(uri.toString(), name)
        }
        tracks = (tracks + incoming).distinctBy { it.uri }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, secondary = Violet, background = Ink, surface = Panel)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("Home", "Audio", "Video", "Studio").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(when (index) {
                                0 -> Icons.Default.Home
                                1 -> Icons.Default.MusicNote
                                2 -> Icons.Default.VideoLibrary
                                else -> Icons.Default.Tune
                            }, contentDescription = label)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .background(Brush.verticalGradient(listOf(Ink, Color(0xFF0C1424), Ink)))
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NK PLAYER", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Text("PLAY WITHOUT LIMITS", color = accent, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(28.dp)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    0 -> {
                        Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("YOUR MEDIA. YOUR VIBE.", color = Color.LightGray, fontSize = 11.sp, letterSpacing = 1.2.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Make every play\nfeel cinematic.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink),
                                    modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("IMPORT MEDIA", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("LIBRARY", tracks.size.toString(), Modifier.weight(1f), accent)
                            StatCard("ENGINE", "MEDIA3", Modifier.weight(1f), accent)
                            StatCard("THEME", themeName.substringBefore(' '), Modifier.weight(1f), accent)
                        }
                        Spacer(Modifier.height(12.dp))
                        SearchBox(query, { query = it }, accent)
                        MediaList(tracks.filter { it.name.contains(query, true) }, selected, accent) {
                            playTrack(it, player); selected = it
                        }
                    }
                    1, 2 -> {
                        Text(if (tab == 1) "AUDIO LIBRARY" else "VIDEO LIBRARY", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { picker.launch(arrayOf(if (tab == 1) "audio/*" else "video/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (tab == 1) "Import audio files" else "Import video files")
                        }
                        SearchBox(query, { query = it }, accent)
                        val filtered = tracks.filter { isVideo(it.uri) == (tab == 2) && it.name.contains(query, true) }
                        MediaList(filtered, selected, accent) { playTrack(it, player); selected = it }
                    }
                    else -> {
                        Text("PLAYER STUDIO", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Choose an accent theme. Your choice is saved.", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        listOf("Neon Blue", "Cyber Purple", "Emerald Green", "Crimson Red", "Golden Amber").forEach { name ->
                            val swatch = when (name) {
                                "Cyber Purple" -> Violet
                                "Emerald Green" -> Color(0xFF37E6A4)
                                "Crimson Red" -> Color(0xFFFF5470)
                                "Golden Amber" -> Color(0xFFFFC45C)
                                else -> Cyan
                            }
                            Surface(color = if (themeName == name) Color(0xFF1C2B3D) else Panel,
                                shape = RoundedCornerShape(15.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { themeName = name }) {
                                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = swatch, shape = RoundedCornerShape(50), modifier = Modifier.size(14.dp)) {}
                                    Spacer(Modifier.width(12.dp))
                                    Text(name, color = Color.White, modifier = Modifier.weight(1f))
                                    if (themeName == name) Icon(Icons.Default.CheckCircle, null, tint = swatch)
                                }
                            }
                        }
                        Text("Background playback, equalizer and subtitle timing are still planned.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
                selected?.let { track ->
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isVideo(track.uri)) Icons.Default.Movie else Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.name, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    Text("SELECTED MEDIA", color = accent, fontSize = 9.sp, letterSpacing = 1.2.sp)
                                }
                                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                                    Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = accent)
                                }
                                IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                            }
                            if (isVideo(track.uri)) {
                                AndroidView(factory = { context -> PlayerView(context).apply { this.player = player; useController = true } },
                                    update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(190.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun playTrack(track: Track, player: ExoPlayer) {
    player.setMediaItem(MediaItem.fromUri(track.uri))
    player.prepare()
    player.play()
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, accent: Color) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Color.LightGray, fontSize = 9.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SearchBox(value: String, onValue: (String) -> Unit, accent: Color) {
    OutlinedTextField(value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("Search imported media…", color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF29344A),
            focusedTextColor = Color.White, unfocusedTextColor = Color.White))
}

@Composable
private fun MediaList(items: List<Track>, selected: Track?, accent: Color, onPick: (Track) -> Unit) {
    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = Violet, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(6.dp))
            Text("No media here yet", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Import files to build your library.", color = Color.Gray, fontSize = 12.sp)
        }
    } else {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(items, key = { it.uri }) { track ->
                Surface(color = if (track == selected) Color(0xFF172C3A) else Panel,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onPick(track) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isVideo(track.uri)) Icons.Default.PlayCircle else Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(track.name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.PlayArrow, null, tint = Color.LightGray)
                    }
                }
            }
        }
    }
}

private fun isVideo(uri: String): Boolean {
    val path = Uri.parse(uri).lastPathSegment.orEmpty().lowercase()
    return listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp", ".m4v").any(path::endsWith)
}
