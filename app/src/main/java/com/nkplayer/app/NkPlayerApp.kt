package com.nkplayer.app

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NkPlayerApp(player: Player, onPersistPermission: (Uri) -> Unit) {
    val library: LibraryViewModel = viewModel()
    val tracks = library.tracks
    val themeName = library.themeName
    var selected by remember { mutableStateOf<Track?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
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
    val activity = context as? ComponentActivity
    val previousRequestedOrientation = remember(activity) { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    LaunchedEffect(fullScreenVideo, playbackUiRevision) {
        val host = activity ?: return@LaunchedEffect
        if (fullScreenVideo && selected?.let { isVideo(it) } == true) {
            val videoSize = player.videoSize
            host.requestedOrientation = if (videoSize.width > videoSize.height) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
        } else {
            host.requestedOrientation = previousRequestedOrientation
        }
    }
    DisposableEffect(player, tracks) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playbackUiRevision++ }
            override fun onPlaybackStateChanged(playbackState: Int) { playbackUiRevision++ }
            override fun onRepeatModeChanged(repeatMode: Int) { playbackUiRevision++ }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { playbackUiRevision++ }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) { playbackUiRevision++ }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) { playbackUiRevision++ }
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
    // The countdown itself runs in PlaybackService; the UI only mirrors it.
    LaunchedEffect(player) { requestSleep(player) { sleepEndsAt = it } }
    LaunchedEffect(sleepEndsAt) {
        if (sleepEndsAt > 0L) {
            delay((sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L))
            sleepEndsAt = 0L
            sleepChoice = 0
        }
    }
    val removeTrack: (Track) -> Unit = { track ->
        library.removeTrack(track)
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
        library.addTracks(incoming)
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
            NKGlassCard(
                tint = Color(0xD90B1220),
                shape = RoundedCornerShape(24.dp),
                stroke = GlassStroke,
                elevation = 18.dp
            ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
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
                        label = { Text(label, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            indicatorColor = accent.copy(alpha = 0.13f),
                            unselectedIconColor = Color(0xFF8492A7),
                            unselectedTextColor = Color(0xFF8492A7)
                        )
                    )
                }
            }
            }
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xFF0A1220),
                                0.38f to Color(0xFF101629),
                                0.72f to Color(0xFF0B1220),
                                1.0f to Ink
                            )
                        )
                    )
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(17.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "NK PLAYER logo",
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NK PLAYER", color = Color.White, fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(accent, RoundedCornerShape(50)))
                            Spacer(Modifier.width(6.dp))
                            Text("PLAY WITHOUT LIMITS", color = accent, fontSize = 9.sp,
                                letterSpacing = 1.55.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        color = Color(0xB81A2638),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Audio player",
                            tint = accent, modifier = Modifier.padding(11.dp).size(20.dp))
                    }
                }
                Spacer(Modifier.height(15.dp))
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
                                onClick = { library.selectTheme(name) }
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
                    NKGlassCard(
                        tint = Color(0xC9141C2D),
                        shape = RoundedCornerShape(22.dp),
                        stroke = accent.copy(alpha = 0.22f),
                        elevation = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.background(
                                Brush.linearGradient(listOf(Color(0x241F3C55), Color(0x161A2440), Color.Transparent))
                            ).padding(13.dp)
                        ) {
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
                                    val minutes = sleepChoice
                                    sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
                                    sendSleep(player, minutes) { confirmedEndsAt -> sleepEndsAt = confirmedEndsAt }
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
