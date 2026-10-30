package com.nkplayer.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val InkButton = Color(0xFF07101C)
private val DialogGlass = Color(0xFF1A1245)

/** [video] is set for files found on the device; [device] items are re-scanned, never saved. */
private data class Track(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
    val video: Boolean? = null,
    val device: Boolean = false
) {
    fun saved() = SavedTrack(uri, name, mimeType)
}

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val controllerError = mutableStateOf<String?>(null)
    private val pipState = mutableStateOf(false)

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
                    inPip = pipState.value,
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipState.value = isInPictureInPictureMode
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
                // Window shape follows the video's real size (Android only accepts roughly 1:2.39 .. 2.39:1).
                val size = player.videoSize
                val swapped = size.unappliedRotationDegrees == 90 || size.unappliedRotationDegrees == 270
                val rawWidth = if (swapped) size.height.toFloat() else size.width * size.pixelWidthHeightRatio
                val rawHeight = if (swapped) size.width * size.pixelWidthHeightRatio else size.height.toFloat()
                val ratio = if (rawWidth > 0f && rawHeight > 0f) (rawWidth / rawHeight).coerceIn(0.5f, 2.3f) else 16f / 9f
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational((ratio * 1000f).toInt(), 1000))
                        .build()
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
                            Text("Connecting NK PLAYER…", fontFamily = DisplayFont, fontSize = 14.sp)
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
private fun NkPlayerApp(player: Player, store: LibraryStore, inPip: Boolean, onPersistPermission: (Uri) -> Unit) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var deviceTracks by remember { mutableStateOf(emptyList<Track>()) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var themeName by remember { mutableStateOf("Neon Blue") }
    var libraryLoaded by remember { mutableStateOf(false) }
    var fullScreenVideo by remember { mutableStateOf(false) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var subtitleDialog by remember { mutableStateOf(false) }
    var externalSubtitleName by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap = rememberPlaybackSnapshot(player)

    // In the small floating (picture-in-picture) window only the video is shown.
    val pipVideo = inPip && selected?.let { isVideo(it) } == true

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

    val importAll = { picker.launch(arrayOf("audio/*", "video/*")) }
    val visibleTracks = libraryTracks.filter { it.name.contains(query, true) }
    val pick: (Track, List<Track>) -> Unit = { track, queue ->
        playbackError = null
        playTrack(track, queue, player)
        selected = track
        if (isVideo(track)) nowPlayingOpen = true
    }

    BackHandler(enabled = nowPlayingOpen && !fullScreenVideo) { nowPlayingOpen = false }

    NkTheme(palette) {
        GlassBackground(palette) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                bottomBar = {
                    NKLiquidNavBar(
                        items = listOf(
                            NKNavItem("Home", Icons.Default.Home),
                            NKNavItem("Audio", Icons.Default.MusicNote),
                            NKNavItem("Video", Icons.Default.VideoLibrary),
                            NKNavItem("Studio", Icons.Default.Tune)
                        ),
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        accent = accent,
                        modifier = Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(8.dp))
                    AppHeader(accent, themeName) { tab = 3 }
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
                                            scanning -> "Scanning your device…"
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
                                            subtitle = if (scanning) "Scanning your device…"
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
                            onOpen = { nowPlayingOpen = true },
                            onToggle = { if (snap.isPlaying) player.pause() else player.play() },
                            onNext = { player.seekToNextMediaItem() }
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            AnimatedVisibility(
                visible = nowPlayingOpen && selected != null,
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
                        subtitleName = externalSubtitleName,
                        showInlineVideo = !fullScreenVideo && !pipVideo,
                        onClose = { nowPlayingOpen = false },
                        onSpeed = cycleSpeed,
                        onSleep = cycleSleep,
                        onSubtitles = { subtitleDialog = true },
                        onLoadSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
                        onFullscreen = { fullScreenVideo = true }
                    )
                }
            }
        }

        if (subtitleDialog && selected?.let { isVideo(it) } == true) {
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            AlertDialog(
                onDismissRequest = { subtitleDialog = false },
                containerColor = DialogGlass,
                title = { Text("Subtitle track", color = Color.White, fontFamily = DisplayFont) },
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
                            Text("Off", color = accent)
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
                        Text("CLOSE", color = accent)
                    }
                }
            )
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
        if (fullScreenVideo && !pipVideo && selected?.let { isVideo(it) } == true) {
            NKFullScreenVideo(
                player = player,
                title = selected?.name.orEmpty(),
                accent = accent,
                onClose = { fullScreenVideo = false }
            )
        }
        // Floating window (picture-in-picture): nothing but the video, filling the whole small window.
        if (pipVideo) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun playTrack(track: Track, queue: List<Track>, player: Player) {
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

// ---------------------------------------------------------------------------------------------
// Screens and components
// ---------------------------------------------------------------------------------------------

/** In-app logo: one PNG per theme (res/drawable-nodpi/nk_logo_*.png, installed from "NK PLAYER ASSETS"). */
private fun logoFor(themeName: String): Int = when (themeName) {
    "Cyber Purple" -> R.drawable.nk_logo_cyber_purple
    "Emerald Green" -> R.drawable.nk_logo_emerald_green
    "Crimson Red" -> R.drawable.nk_logo_crimson_red
    "Golden Amber" -> R.drawable.nk_logo_golden_amber
    else -> R.drawable.nk_logo_neon_blue
}

@Composable
private fun AppHeader(accent: Color, themeName: String, onStudio: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).glass(RoundedCornerShape(16.dp)).padding(4.dp)) {
            Image(
                painter = painterResource(id = logoFor(themeName)),
                contentDescription = "NK PLAYER logo",
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("NK PLAYER", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp, letterSpacing = 1.sp)
            Text("PLAY WITHOUT LIMITS", color = accent, fontSize = 9.sp, letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold)
        }
        GlassIconButton(Icons.Default.GraphicEq, "Open studio", onStudio, tint = accent)
    }
}

@Composable
private fun HomeHero(accent: Color, onImport: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), strength = 1.2f) {
        Column(Modifier.padding(22.dp)) {
            GlassChip("YOUR MEDIA. YOUR RULES.", accent, active = true, icon = Icons.Default.GraphicEq)
            Spacer(Modifier.height(18.dp))
            Text("Press play.\nFeel the difference.", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Audio and video in one glass-smooth player. Your phone's music and videos appear here automatically.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))
            AccentButton("ADD FILES MANUALLY", Icons.Default.Add, accent, Modifier.fillMaxWidth(), onImport)
        }
    }
}

@Composable
private fun PermissionCard(accent: Color, denied: Boolean, onAllow: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.3f) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Find my music & videos", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("No manual import needed", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (denied) "Access was denied. Open Settings, allow Music and audio / Photos and videos, then come back."
                else "Allow access to audio and video on this phone and NK PLAYER lists everything automatically. Nothing is uploaded.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))
            AccentButton(
                if (denied) "OPEN SETTINGS" else "ALLOW & SCAN DEVICE",
                Icons.Default.Search, accent, Modifier.fillMaxWidth(), onAllow
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, accent: Color) {
    GlassCard(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, accent: Color, onAdd: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.8.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
        }
        GlassIconButton(Icons.Default.Refresh, "Rescan device", onRefresh, tint = accent)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Icons.Default.Add, "Import media", onAdd, tint = accent)
    }
}

@Composable
private fun LibraryHeader(
    title: String,
    subtitle: String,
    count: Int,
    video: Boolean,
    accent: Color,
    onImport: () -> Unit,
    onRefresh: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.2f) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (video) Icons.Default.VideoLibrary else Icons.Default.Headphones, null,
                    tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text("$count FILE(S)", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            GlassIconButton(Icons.Default.Refresh, "Rescan device", onRefresh, tint = accent)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Default.Add, "Import files", onImport, tint = accent)
        }
    }
}

@Composable
private fun SearchBox(value: String, onValue: (String) -> Unit, accent: Color) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search your media…", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accent
        )
    )
}

@Composable
private fun EmptyState(accent: Color) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("No media found", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Allow access to scan your phone, or add files manually. Everything stays on this device.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    active: Boolean,
    playing: Boolean,
    accent: Color,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    val video = isVideo(track)
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), strength = if (active) 1.7f else 1f, onClick = onPick) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArt(
                uri = track.uri,
                video = video,
                accent = accent,
                icon = if (video) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (video) "VIDEO" else "AUDIO", color = accent, fontSize = 10.sp, letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold)
                    if (active) {
                        Text("  •  " + (if (playing) "PLAYING" else "PAUSED"), color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
            }
            // Device files are managed by the system scan; only manually added files can be removed here.
            if (!track.device) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${track.name} from library",
                        tint = Color(0xFFFF8A9A))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    snap: PlaybackSnapshot,
    accent: Color,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    val video = isVideo(track)
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strength = 1.4f, onClick = onOpen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArt(
                uri = track.uri,
                video = video,
                accent = accent,
                icon = if (video) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                iconSize = 22.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (snap.isPlaying) "NOW PLAYING" else "PAUSED", color = accent, fontSize = 10.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            }
            GlassIconButton(
                if (snap.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play or pause", onToggle, size = 42.dp, tint = InkButton, filled = accent
            )
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Default.SkipNext, "Next", onNext, size = 42.dp)
        }
        LinearProgressIndicator(
            progress = {
                if (snap.durationMs > 0L) (snap.positionMs.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f
            },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = accent,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
    }
}

@Composable
private fun NowPlayingScreen(
    player: Player,
    track: Track,
    snap: PlaybackSnapshot,
    palette: NkPalette,
    sleepLabel: String?,
    subtitleName: String?,
    showInlineVideo: Boolean,
    onClose: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onSubtitles: () -> Unit,
    onLoadSubtitle: () -> Unit,
    onFullscreen: () -> Unit
) {
    val accent = palette.accent
    val video = isVideo(track)
    val art by rememberMediaThumb(track.uri, video, 720)
    var seeking by remember { mutableStateOf(false) }
    var preview by remember { mutableFloatStateOf(0f) }
    val progress = if (seeking) preview
    else if (snap.durationMs > 0L) (snap.positionMs.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f

    GlassBackground(palette) {
        val backdrop = art
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp).alpha(if (Build.VERSION.SDK_INT >= 31) 0.55f else 0.25f)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66130B3A), Color(0xD9070A1E)))))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Default.KeyboardArrowDown, "Minimise player", onClose)
                Text("NOW PLAYING", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = DisplayFont,
                    fontSize = 12.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(0.5f))
            if (video) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).glass(RoundedCornerShape(24.dp)).padding(5.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color.Black)
                ) {
                    if (showInlineVideo) {
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(0.78f).aspectRatio(1f).glass(RoundedCornerShape(36.dp), 1.2f).padding(10.dp)) {
                    MediaArt(
                        uri = track.uri,
                        video = false,
                        accent = accent,
                        icon = Icons.Default.MusicNote,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(28.dp),
                        maxDim = 720,
                        iconSize = 72.dp
                    )
                }
            }
            Spacer(Modifier.weight(0.4f))
            Text(track.name, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "${if (video) "VIDEO" else "AUDIO"}  •  ${snap.index + 1} / ${snap.count.coerceAtLeast(1)}",
                color = accent, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            Slider(
                value = progress,
                onValueChange = {
                    seeking = true
                    preview = it
                },
                onValueChangeFinished = {
                    val total = player.duration
                    if (total > 0L) player.seekTo((preview * total).toLong())
                    seeking = false
                },
                enabled = snap.durationMs > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (seeking) (preview * snap.durationMs).toLong() else snap.positionMs),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                Text(formatTime(snap.durationMs), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Default.Shuffle, "Shuffle", { player.shuffleModeEnabled = !snap.shuffle },
                    tint = if (snap.shuffle) accent else Color.White)
                GlassIconButton(Icons.Default.SkipPrevious, "Previous", { player.seekToPreviousMediaItem() }, size = 52.dp)
                GlassIconButton(
                    if (snap.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play or pause",
                    { if (snap.isPlaying) player.pause() else player.play() },
                    size = 76.dp, tint = InkButton, filled = accent
                )
                GlassIconButton(Icons.Default.SkipNext, "Next", { player.seekToNextMediaItem() }, size = 52.dp)
                GlassIconButton(
                    if (snap.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat mode",
                    {
                        player.repeatMode = when (snap.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },
                    tint = if (snap.repeatMode == Player.REPEAT_MODE_OFF) Color.White else accent
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(formatSpeed(snap.speed), accent, active = snap.speed != 1f, icon = Icons.Default.Speed, onClick = onSpeed)
                GlassChip(sleepLabel ?: "Sleep", accent, active = sleepLabel != null, icon = Icons.Default.Bedtime, onClick = onSleep)
                if (video) {
                    GlassChip("Subtitles", accent, icon = Icons.Default.Subtitles, onClick = onSubtitles)
                    GlassChip("Load .srt/.vtt", accent, icon = Icons.Default.FileOpen, onClick = onLoadSubtitle)
                    GlassChip("Full screen", accent, icon = Icons.Default.Fullscreen, onClick = onFullscreen)
                }
            }
            subtitleName?.let {
                Spacer(Modifier.height(8.dp))
                Text("Subtitle: $it", color = accent, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(0.3f))
        }
    }
}

@Composable
private fun StudioHeader(accent: Color, themeName: String) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.2f) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYER STUDIO", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.height(3.dp))
                Text("PERSONALISE YOUR PLAYER", color = accent, fontSize = 9.sp, letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Active: $themeName", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(p: NkPalette, selected: Boolean, onClick: () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        strength = if (selected) 1.7f else 1f,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bgTop, p.accent)))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (selected) "Currently active" else "Tap to apply this look",
                    color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = p.accent, modifier = Modifier.size(24.dp))
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
    track.video ?: (track.mimeType?.startsWith("video/") == true || isVideo(track.uri))

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
