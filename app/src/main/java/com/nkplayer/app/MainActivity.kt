package com.nkplayer.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay

private val Ink = Color(0xFF080B14)
private val Panel = Color(0xFF131A2A)
private val Cyan = Color(0xFF48E5FF)
private val Violet = Color(0xFF9B72FF)
private data class Track(val uri: String, val name: String, val mimeType: String? = null) {
    fun saved() = SavedTrack(uri, name, mimeType)
}

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val controllerError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        setContent {
            val player = controllerState.value
            if (player == null) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Ink, surface = Panel)) {
                    Surface(Modifier.fillMaxSize(), color = Ink) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (controllerError.value == null) {
                                    CircularProgressIndicator(color = Cyan)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Connecting NK PLAYER…", color = Color.White)
                                } else {
                                    Text("Player engine could not start", color = Color.White, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(controllerError.value.orEmpty(), color = Color.LightGray)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Please send this message to support: " + controllerError.value.orEmpty(), color = Cyan)
                                }
                            }
                        }
                    }
                }
            } else {
                NkPlayerApp(
                    player = player,
                    store = remember { LibraryStore(this) },
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

        future.addListener({
            runOnUiThread {
                val connected = runCatching { future.get() }
                controllerState.value = connected.getOrNull()
                controllerError.value = connected.exceptionOrNull()?.let { error ->
                    error.cause?.localizedMessage ?: error.localizedMessage ?: "Unknown media service error"
                }
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = controllerState.value ?: return
        val configuration = player.currentMediaItem?.localConfiguration
        val path = configuration?.uri?.lastPathSegment.orEmpty().lowercase()
        val isVideo = configuration?.mimeType?.startsWith("video/") == true || isVideo(path)
        if (isVideo && player.isPlaying && android.os.Build.VERSION.SDK_INT >= 26 &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onDestroy() {
        controllerState.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NkPlayerApp(player: Player, store: LibraryStore, onPersistPermission: (Uri) -> Unit) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var themeName by remember { mutableStateOf("Neon Blue") }
    var libraryLoaded by remember { mutableStateOf(false) }
    var fullScreenVideo by remember { mutableStateOf(false) }
    var subtitleDialog by remember { mutableStateOf(false) }
    var externalSubtitleName by remember { mutableStateOf<String?>(null) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var playbackUiRevision by remember(player) { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var seekPreview by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    DisposableEffect(player, tracks) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playbackUiRevision++ }
            override fun onPlaybackStateChanged(playbackState: Int) { playbackUiRevision++ }
            override fun onRepeatModeChanged(repeatMode: Int) { playbackUiRevision++ }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { playbackUiRevision++ }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) { playbackUiRevision++ }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentUri = mediaItem?.localConfiguration?.uri?.toString()
                selected = tracks.firstOrNull { it.uri == currentUri }
                playbackError = null
                playbackUiRevision++
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "This file could not be played. Try another media file or format."
                playbackUiRevision++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val isPlaying = remember(playbackUiRevision, player) { player.isPlaying }
    val repeatMode = remember(playbackUiRevision, player) { player.repeatMode }
    val shuffleOn = remember(playbackUiRevision, player) { player.shuffleModeEnabled }
    val speed = remember(playbackUiRevision, player) { player.playbackParameters.speed }
    var sleepEndsAt by remember { mutableLongStateOf(0L) }
    var sleepChoice by remember { mutableIntStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        if (sleepEndsAt > 0L) {
            delay((sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L))
            player.pause()
            sleepEndsAt = 0L
        }
    }
    val removeTrack: (Track) -> Unit = { track ->
        tracks = tracks.filterNot { it.uri == track.uri }
        if (selected?.uri == track.uri) {
            player.stop()
            player.clearMediaItems()
            selected = null
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Playback remains usable if notifications are declined. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(store) {
        tracks = store.loadLibrary().map { saved ->
            val uri = Uri.parse(saved.uri)
            val mimeType = saved.mimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
            Track(saved.uri, saved.name, mimeType)
        }
        themeName = store.loadTheme()
        libraryLoaded = true
    }
    LaunchedEffect(tracks, libraryLoaded) {
        if (libraryLoaded) store.saveLibrary(tracks.map { it.saved() })
    }
    LaunchedEffect(player, isSeeking) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (!isSeeking) {
                playbackPosition = player.currentPosition.coerceAtLeast(0L)
                playbackDuration = player.duration.takeIf { it > 0L } ?: 0L
                seekPreview = if (playbackDuration > 0L) playbackPosition.toFloat() / playbackDuration else 0f
            }
            delay(500)
        }
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
            Track(uri.toString(), queryDisplayName(context, uri), context.contentResolver.getType(uri))
        }
        tracks = (tracks + incoming).distinctBy { it.uri }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri)
            val extension = name.substringAfterLast('.', "").lowercase()
            val providerMimeType = context.contentResolver.getType(uri)
            val mimeType = when (extension) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                else -> when (providerMimeType) {
                    MimeTypes.APPLICATION_SUBRIP, "application/x-subrip" -> MimeTypes.APPLICATION_SUBRIP
                    MimeTypes.TEXT_VTT, "text/vtt" -> MimeTypes.TEXT_VTT
                    else -> null
                }
            }
            val current = player.currentMediaItem
            val index = player.currentMediaItemIndex
            if (current != null && index >= 0 && mimeType != null) {
                onPersistPermission(uri)
                val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(mimeType)
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                val updated = current.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build()
                player.replaceMediaItem(index, updated)
                externalSubtitleName = name
            } else {
                externalSubtitleName = if (mimeType == null) "Unsupported subtitle: use .srt or .vtt" else null
            }
        }
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
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "NK PLAYER logo",
                        modifier = Modifier.size(46.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    0 -> {
                        HomeHero(accent = accent, onImport = {
                            picker.launch(arrayOf("audio/*", "video/*"))
                        })
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("YOUR FILES", tracks.size.toString(), Modifier.weight(1f), accent)
                            StatCard("PLAYER", "MEDIA3", Modifier.weight(1f), accent)
                            StatCard("STYLE", themeName.substringBefore(' '), Modifier.weight(1f), accent)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("YOUR LIBRARY", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(if (tracks.isEmpty()) "Start by importing your favourite files" else "${tracks.size} item(s) ready to play",
                                    color = Color.Gray, fontSize = 11.sp)
                            }
                            Surface(color = Panel, shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp).clickable { picker.launch(arrayOf("audio/*", "video/*")) }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "Import media", tint = accent)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SearchBox(query, { query = it }, accent)
                        MediaList(tracks.filter { it.name.contains(query, true) }, selected, accent, modifier = Modifier.weight(1f), onRemove = removeTrack) {
                            playbackError = null
                            playTrack(it, tracks.filter { item -> item.name.contains(query, true) }, player); selected = it
                        }
                        if (tracks.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(color = Color(0xFF101B2B), shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TipsAndUpdates, null, tint = accent, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text("Tip: import audio or video from your phone. Your library stays on this device.",
                                        color = Color.LightGray, fontSize = 11.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                    1, 2 -> {
                        val videoTab = tab == 2
                        val filtered = tracks.filter { isVideo(it) == videoTab && it.name.contains(query, true) }
                        LibraryTabHeader(
                            title = if (videoTab) "VIDEO LIBRARY" else "AUDIO LIBRARY",
                            subtitle = if (videoTab) "Your clips, films and saved videos" else "Your music, podcasts and audio files",
                            count = filtered.size,
                            video = videoTab,
                            accent = accent,
                            onImport = { picker.launch(arrayOf(if (videoTab) "video/*" else "audio/*")) }
                        )
                        Spacer(Modifier.height(12.dp))
                        SearchBox(query, { query = it }, accent)
                        Spacer(Modifier.height(4.dp))
                        MediaList(filtered, selected, accent, modifier = Modifier.weight(1f), onRemove = removeTrack) {
                            playbackError = null
                            playTrack(it, filtered, player); selected = it
                        }
                    }
                    else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        StudioHeader(accent = accent, themeName = themeName)
                        Spacer(Modifier.height(14.dp))
                        Text("ACCENT THEMES", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text("Choose a look that feels like yours.", color = Color.Gray, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        listOf("Neon Blue", "Cyber Purple", "Emerald Green", "Crimson Red", "Golden Amber").forEach { name ->
                            val swatch = when (name) {
                                "Cyber Purple" -> Violet
                                "Emerald Green" -> Color(0xFF37E6A4)
                                "Crimson Red" -> Color(0xFFFF5470)
                                "Golden Amber" -> Color(0xFFFFC45C)
                                else -> Cyan
                            }
                            ThemeOptionCard(
                                name = name,
                                swatch = swatch,
                                selected = themeName == name,
                                onClick = { themeName = name }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("SOUND", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text("Equalizer, bass boost and virtualizer for all playback.", color = Color.Gray, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        EqualizerSection(player = player, accent = accent)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                selected?.let { track ->
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isVideo(track)) Icons.Default.Movie else Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.name, color = Color.White, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    Text(if (isPlaying) "NOW PLAYING" else "PLAYER READY", color = accent, fontSize = 9.sp, letterSpacing = 1.2.sp)
                                }
                                IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White)
                                }
                                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = accent)
                                }
                                IconButton(onClick = { player.seekToNextMediaItem() }) {
                                    Icon(Icons.Default.SkipNext, null, tint = Color.White)
                                }
                            }
                            Slider(
                                value = seekPreview.coerceIn(0f, 1f),
                                onValueChange = {
                                    isSeeking = true
                                    seekPreview = it
                                },
                                onValueChangeFinished = {
                                    if (playbackDuration > 0L) player.seekTo((seekPreview * playbackDuration).toLong())
                                    isSeeking = false
                                },
                                enabled = playbackDuration > 0L,
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xFF344055))
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(if (isSeeking) (seekPreview * playbackDuration).toLong() else playbackPosition), color = Color.LightGray, fontSize = 10.sp)
                                Text(formatTime(playbackDuration), color = Color.LightGray, fontSize = 10.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { player.shuffleModeEnabled = !shuffleOn }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle",
                                        tint = if (shuffleOn) accent else Color.Gray)
                                }
                                IconButton(onClick = {
                                    player.repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }) {
                                    Icon(
                                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = "Repeat mode",
                                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else accent
                                    )
                                }
                                TextButton(onClick = {
                                    val steps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                    player.setPlaybackSpeed(steps.firstOrNull { it > speed + 0.01f } ?: steps.first())
                                }) {
                                    Text(formatSpeed(speed), color = if (speed != 1f) accent else Color.LightGray, fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    val options = listOf(0, 15, 30, 45, 60)
                                    sleepChoice = options[(options.indexOf(sleepChoice) + 1) % options.size]
                                    sleepEndsAt = if (sleepChoice > 0) System.currentTimeMillis() + sleepChoice * 60_000L else 0L
                                }) {
                                    Icon(Icons.Default.Bedtime, contentDescription = "Sleep timer",
                                        tint = if (sleepEndsAt > 0L) accent else Color.Gray, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (sleepEndsAt > 0L) "${((sleepEndsAt - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L}m" else "SLEEP",
                                        color = if (sleepEndsAt > 0L) accent else Color.LightGray, fontSize = 12.sp
                                    )
                                }
                            }
                            if (isVideo(track)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { subtitleDialog = true }) {
                                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = accent)
                                        Spacer(Modifier.width(4.dp))
                                        Text("SUBTITLES", color = accent)
                                    }
                                    TextButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = accent)
                                        Spacer(Modifier.width(4.dp))
                                        Text("LOAD .SRT/.VTT", color = accent)
                                    }
                                    TextButton(onClick = { fullScreenVideo = true }) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = accent)
                                        Spacer(Modifier.width(4.dp))
                                        Text("FULL SCREEN", color = accent)
                                    }
                                }
                                externalSubtitleName?.let { subtitleName ->
                                    Text("Subtitle: $subtitleName", color = accent, fontSize = 11.sp, maxLines = 1)
                                }
                                if (!fullScreenVideo) {
                                    AndroidView(
                                        factory = { context -> PlayerView(context).apply { this.player = player; useController = true } },
                                        update = { it.player = player },
                                        modifier = Modifier.fillMaxWidth().height(190.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (subtitleDialog && selected?.let { isVideo(it) } == true) {
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            AlertDialog(
                onDismissRequest = { subtitleDialog = false },
                containerColor = Panel,
                title = { Text("Subtitle track", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (textGroups.isEmpty()) "No embedded subtitles found in this video." else "Choose an available embedded subtitle track.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        TextButton(onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .build()
                            subtitleDialog = false
                        }) {
                            Text("Off", color = Cyan)
                        }
                        textGroups.forEach { group ->
                            for (index in 0 until group.length) {
                                if (group.isTrackSupported(index)) {
                                    val format = group.getTrackFormat(index)
                                    val label = format.label?.takeIf { it.isNotBlank() }
                                        ?: format.language?.takeIf { it.isNotBlank() }
                                        ?: "Subtitle ${index + 1}"
                                    TextButton(onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                            .build()
                                        subtitleDialog = false
                                    }) {
                                        Text(label, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { subtitleDialog = false }) {
                        Text("CLOSE", color = Cyan)
                    }
                }
            )
        }
        if (playbackError != null) {
            AlertDialog(
                onDismissRequest = { playbackError = null },
                containerColor = Panel,
                title = { Text("Playback error", color = Color.White) },
                text = { Text(playbackError.orEmpty(), color = Color.LightGray) },
                confirmButton = { TextButton(onClick = { playbackError = null }) { Text("OK", color = Cyan) } }
            )
        }
        if (fullScreenVideo && selected?.let { isVideo(it) } == true) {
            Dialog(
                onDismissRequest = { fullScreenVideo = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { context -> PlayerView(context).apply { this.player = player; useController = true } },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { fullScreenVideo = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Exit full screen", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun playTrack(track: Track, queue: List<Track>, player: Player) {
    val playableQueue = queue.ifEmpty { listOf(track) }
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




@Composable
private fun StudioHeader(accent: Color, themeName: String) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(Color(0xFF1A2940), Color(0xFF211B39), Color(0xFF111827))))
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp), modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYER STUDIO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.height(3.dp))
                Text("PERSONALISE YOUR PLAYER", color = accent, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent, shape = RoundedCornerShape(50), modifier = Modifier.size(7.dp)) {}
                    Spacer(Modifier.width(6.dp))
                    Text("Active: $themeName", color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(name: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) swatch.copy(alpha = 0.12f) else Panel,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) swatch.copy(alpha = 0.85f) else Color(0xFF263247)
        ),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = swatch, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Waves, null, tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                Text(if (selected) "Currently active" else "Tap to apply this accent", color = Color.Gray, fontSize = 10.sp)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = swatch, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun LibraryTabHeader(
    title: String,
    subtitle: String,
    count: Int,
    video: Boolean,
    accent: Color,
    onImport: () -> Unit
) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF17283B), Color(0xFF17172B))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(15.dp), modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (video) Icons.Default.VideoLibrary else Icons.Default.Headphones,
                        contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color.LightGray, fontSize = 10.sp, maxLines = 2)
                Spacer(Modifier.height(7.dp))
                Text("$count FILE(S)", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Default.AddCircle, contentDescription = "Import files", tint = accent, modifier = Modifier.size(29.dp))
            }
        }
    }
}

@Composable
private fun HomeHero(accent: Color, onImport: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Panel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(Color(0xFF182C43), Color(0xFF211B3D), Color(0xFF101827))
                )
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(25.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("YOUR MEDIA. YOUR RULES.", color = accent, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                        Text("One home for every track.", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Press play.\nFeel the difference.", color = Color.White, fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(7.dp))
                Text("Your audio and videos, organised in one clean player.",
                    color = Color(0xFFC1CAD8), fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("IMPORT FROM DEVICE", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
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
private fun MediaList(
    items: List<Track>,
    selected: Track?,
    accent: Color,
    modifier: Modifier = Modifier,
    onRemove: (Track) -> Unit,
    onPick: (Track) -> Unit
) {
    if (items.isEmpty()) {
        Column(modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = Violet, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(6.dp))
            Text("No media here yet", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Import files to build your library.", color = Color.Gray, fontSize = 12.sp)
        }
    } else {
        LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(items, key = { it.uri }) { track ->
                Surface(color = if (track == selected) Color(0xFF172C3A) else Panel,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onPick(track) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isVideo(track)) Icons.Default.PlayCircle else Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(track.name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play ${track.name}", tint = Color.LightGray)
                        IconButton(
                            onClick = { onRemove(track) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove ${track.name} from library",
                                tint = Color(0xFFFF7A8A)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    "%.2f".format(speed).trimEnd('0').trimEnd('.').trimEnd(',') + "x"

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun isVideo(track: Track): Boolean =
    track.mimeType?.startsWith("video/") == true || isVideo(track.uri)

private fun isVideo(uri: String): Boolean {
    val path = Uri.parse(uri).lastPathSegment.orEmpty().lowercase()
    return listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp", ".m4v", ".mpeg", ".mpg", ".ts", ".ogv").any(path::endsWith)
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    val providerName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return providerName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        ?: "Media file"
}
