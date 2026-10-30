package com.nkplayer.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DialogGlass = Color(0xFF1A1245)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val controllerError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Show the Compose shell even if a device rejects MediaSession initialization.
        val startup = runCatching {
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            MediaController.Builder(this, token).buildAsync()
        }
        val future = startup.getOrNull()
        controllerFuture = future
        controllerError.value = startup.exceptionOrNull()?.let { error ->
            error.cause?.localizedMessage ?: error.localizedMessage ?: "Unable to initialize media controller"
        }

        setContent {
            val player = controllerState.value
            if (player == null) {
                StartupScreen(controllerError.value)
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

        future?.addListener({
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

@Composable
private fun StartupScreen(error: String?) {
    val palette = paletteFor("Cyber Purple")
    NkTheme(palette) {
        GlassBackground(palette) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), strength = 1.2f) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error == null) {
                            CircularProgressIndicator(color = palette.accent)
                            Spacer(Modifier.height(14.dp))
                            Text("Connecting NK PLAYER\u2026", fontFamily = DisplayFont, fontSize = 14.sp)
                        } else {
                            Text("Player engine could not start", fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = Color.LightGray, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Please send this message to support: $error", color = palette.accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NkPlayerApp(player: Player, store: LibraryStore, onPersistPermission: (Uri) -> Unit) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var deviceTracks by remember { mutableStateOf(emptyList<Track>()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var themeName by remember { mutableStateOf("Neon Blue") }
    var libraryLoaded by remember { mutableStateOf(false) }
    var videoOpen by remember { mutableStateOf(false) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap = rememberPlaybackSnapshot(player)

    // Manually imported files (persisted) plus everything found on the device (re-scanned each time).
    val libraryTracks = remember(tracks, deviceTracks) { (tracks + deviceTracks).distinctBy { it.uri } }
    var mediaAccess by remember { mutableStateOf(DeviceMedia.hasPermission(context)) }
    var mediaDenied by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val rescan: () -> Unit = {
        mediaAccess = DeviceMedia.hasPermission(context)
        if (mediaAccess && !scanning) {
            scanning = true
            scope.launch {
                val found = runCatching { DeviceMedia.scan(context) }.getOrDefault(emptyList())
                deviceTracks = found.map { Track(it.uri, it.name, it.mimeType, it.video, true) }
                scanning = false
            }
        }
    }

    DisposableEffect(player, libraryTracks) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentUri = mediaItem?.localConfiguration?.uri?.toString()
                selected = libraryTracks.firstOrNull { it.uri == currentUri }
                playbackError = null
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "This file could not be played. Try another media file or format."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // The video screen only makes sense while a video is the current item.
    LaunchedEffect(selected) {
        val current = selected
        if (current != null && !isVideo(current)) videoOpen = false
    }

    var sleepEndsAt by remember { mutableLongStateOf(0L) }
    var sleepChoice by remember { mutableIntStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        if (sleepEndsAt > 0L) {
            while (System.currentTimeMillis() < sleepEndsAt) {
                nowMs = System.currentTimeMillis()
                delay(1000)
            }
            player.pause()
            sleepEndsAt = 0L
        }
    }
    val sleepLabel: String? =
        if (sleepEndsAt > 0L) "${((sleepEndsAt - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L}m" else null
    val cycleSleep: () -> Unit = {
        val options = listOf(0, 15, 30, 45, 60)
        sleepChoice = options[(options.indexOf(sleepChoice) + 1) % options.size]
        nowMs = System.currentTimeMillis()
        sleepEndsAt = if (sleepChoice > 0) nowMs + sleepChoice * 60_000L else 0L
    }
    val cycleSpeed: () -> Unit = {
        val steps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        player.setPlaybackSpeed(steps.firstOrNull { it > snap.speed + 0.01f } ?: steps.first())
    }
    val removeTrack: (Track) -> Unit = { track ->
        tracks = tracks.filterNot { it.uri == track.uri }
        if (selected?.uri == track.uri) {
            player.stop()
            player.clearMediaItems()
            selected = null
            nowPlayingOpen = false
            videoOpen = false
        }
    }

    // One request covers media access and (Android 13+) notifications; the system shows them in turn.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaAccess = DeviceMedia.hasPermission(context)
        if (mediaAccess) rescan() else mediaDenied = true
    }
    LaunchedEffect(Unit) {
        val wanted = DeviceMedia.requiredPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    val requestAccess: () -> Unit = {
        if (mediaDenied) {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } else {
            permissionLauncher.launch(DeviceMedia.requiredPermissions())
        }
    }

    // Scan when the app opens and whenever the user returns to it (new files, permission granted in Settings).
    val lifecycleOwner = context as? ComponentActivity
    DisposableEffect(lifecycleOwner) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) rescan()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(store) {
        // Corrupt or inaccessible preferences must not terminate the player UI.
        tracks = runCatching { store.loadLibrary() }.getOrDefault(emptyList()).map { saved ->
            val uri = Uri.parse(saved.uri)
            val mimeType = saved.mimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
            Track(saved.uri, saved.name, mimeType)
        }
        themeName = runCatching { store.loadTheme() }.getOrDefault("Neon Blue")
        libraryLoaded = true
    }
    LaunchedEffect(tracks, libraryLoaded) {
        if (libraryLoaded) runCatching { store.saveLibrary(tracks.map { it.saved() }) }
    }
    LaunchedEffect(themeName) { runCatching { store.saveTheme(themeName) } }

    val palette = paletteFor(themeName)
    val accent = palette.accent

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
                val position = player.currentPosition
                val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(mimeType)
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                val updated = current.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build()
                player.replaceMediaItem(index, updated)
                player.seekTo(index, position)
                Toast.makeText(context, "Subtitle loaded: $name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unsupported subtitle: use .srt or .vtt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importAll = { picker.launch(arrayOf("audio/*", "video/*")) }
    val visibleTracks = libraryTracks.filter { it.name.contains(query, true) }
    val pick: (Track, List<Track>) -> Unit = { track, queue ->
        playbackError = null
        playTrack(track, queue, player)
        selected = track
        if (isVideo(track)) videoOpen = true
    }

    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }

    NkTheme(palette) {
        GlassBackground(palette) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                bottomBar = { GlassNavBar(tab, accent) { tab = it } }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(8.dp))
                    AppHeader(accent) { tab = 3 }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                item { HomeHero(accent, importAll) }
                                if (!mediaAccess) item { PermissionCard(accent, mediaDenied, requestAccess) }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatCard("FILES", libraryTracks.size.toString(), Modifier.weight(1f), accent)
                                        StatCard("AUDIO", libraryTracks.count { !isVideo(it) }.toString(), Modifier.weight(1f), accent)
                                        StatCard("VIDEO", libraryTracks.count { isVideo(it) }.toString(), Modifier.weight(1f), accent)
                                    }
                                }
                                item {
                                    SectionHeader(
                                        "YOUR LIBRARY",
                                        when {
                                            scanning -> "Scanning your device\u2026"
                                            libraryTracks.isEmpty() -> "Allow access or import files to begin"
                                            else -> "${libraryTracks.size} item(s) ready to play"
                                        },
                                        accent, importAll, rescan
                                    )
                                }
                                item { SearchBox(query, { query = it }, accent) }
                                if (visibleTracks.isEmpty() && !scanning) item { EmptyState(accent) }
                                items(visibleTracks, key = { it.uri }) { track ->
                                    TrackRow(track, track == selected, snap.isPlaying, accent,
                                        onPick = { pick(track, visibleTracks) }, onRemove = { removeTrack(track) })
                                }
                            }
                            1, 2 -> {
                                val videoTab = tab == 2
                                val filtered = libraryTracks.filter { isVideo(it) == videoTab && it.name.contains(query, true) }
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    item {
                                        LibraryHeader(
                                            title = if (videoTab) "VIDEO LIBRARY" else "AUDIO LIBRARY",
                                            subtitle = if (scanning) "Scanning your device\u2026"
                                            else if (videoTab) "Your clips, films and saved videos" else "Your music, podcasts and audio files",
                                            count = filtered.size,
                                            video = videoTab,
                                            accent = accent,
                                            onImport = { picker.launch(arrayOf(if (videoTab) "video/*" else "audio/*")) },
                                            onRefresh = rescan
                                        )
                                    }
                                    if (!mediaAccess) item { PermissionCard(accent, mediaDenied, requestAccess) }
                                    item { SearchBox(query, { query = it }, accent) }
                                    if (filtered.isEmpty() && !scanning) item { EmptyState(accent) }
                                    items(filtered, key = { it.uri }) { track ->
                                        TrackRow(track, track == selected, snap.isPlaying, accent,
                                            onPick = { pick(track, filtered) }, onRemove = { removeTrack(track) })
                                    }
                                }
                            }
                            else -> Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StudioHeader(accent, themeName)
                                Text("ACCENT THEMES", fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, letterSpacing = 0.8.sp)
                                Text("Choose a look that feels like yours.", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                                NkPalettes.forEach { p ->
                                    ThemeOptionCard(p, themeName == p.name) { themeName = p.name }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("SOUND", fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, letterSpacing = 0.8.sp)
                                Text("Equalizer, bass boost and virtualizer for all playback.",
                                    color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                                EqualizerSection(player = player, accent = accent)
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                    selected?.let { track ->
                        Spacer(Modifier.height(8.dp))
                        MiniPlayer(
                            track, snap, accent,
                            onOpen = { if (isVideo(track)) videoOpen = true else nowPlayingOpen = true },
                            onToggle = { if (snap.isPlaying) player.pause() else player.play() },
                            onNext = { player.seekToNextMediaItem() }
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            AnimatedVisibility(
                visible = nowPlayingOpen && selected?.let { !isVideo(it) } == true,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                selected?.let { track ->
                    NowPlayingScreen(
                        player = player,
                        track = track,
                        snap = snap,
                        palette = palette,
                        sleepLabel = sleepLabel,
                        onClose = { nowPlayingOpen = false },
                        onSpeed = cycleSpeed,
                        onSleep = cycleSleep
                    )
                }
            }
            AnimatedVisibility(
                visible = videoOpen && selected?.let { isVideo(it) } == true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selected?.let { track ->
                    VideoPlayerScreen(
                        player = player,
                        track = track,
                        palette = palette,
                        sleepLabel = sleepLabel,
                        onClose = { videoOpen = false },
                        onSleep = cycleSleep,
                        onLoadSubtitle = { subtitlePicker.launch(arrayOf("*/*")) }
                    )
                }
            }
        }

        if (playbackError != null) {
            AlertDialog(
                onDismissRequest = { playbackError = null },
                containerColor = DialogGlass,
                title = { Text("Playback error", color = Color.White, fontFamily = DisplayFont) },
                text = { Text(playbackError.orEmpty(), color = Color.LightGray) },
                confirmButton = { TextButton(onClick = { playbackError = null }) { Text("OK", color = accent) } }
            )
        }
    }
}
