package com.nkplayer.app

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private data class Hud(val kind: Int, val value: Float, val tick: Int)
private data class SeekFlash(val side: Int, val seconds: Int, val id: Int)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Immersive video screen: tap to show or hide controls, double-tap left/right to seek 10 s,
 * drag horizontally to scrub, drag the left/right edge for brightness/volume, press and hold for 2x.
 */
@Composable
internal fun VideoPlayerScreen(
    player: Player,
    track: Track,
    palette: NkPalette,
    sleepLabel: String?,
    onClose: () -> Unit,
    onSleep: () -> Unit,
    onLoadSubtitle: () -> Unit
) {
    val accent = palette.accent
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val snap = rememberPlaybackSnapshot(player)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val window = activity?.window

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var orientationMode by remember { mutableIntStateOf(0) }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(false) }
    var subtitleScale by remember { mutableFloatStateOf(1f) }
    var abA by remember { mutableStateOf<Long?>(null) }
    var abB by remember { mutableStateOf<Long?>(null) }
    var tracksRevision by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var boosting by remember { mutableStateOf(false) }
    var boostPrev by remember { mutableFloatStateOf(1f) }
    var resumeNote by remember { mutableStateOf<String?>(null) }
    var seekingBar by remember { mutableStateOf(false) }
    var barPreview by remember { mutableFloatStateOf(0f) }
    var brightness by remember {
        mutableFloatStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    val buffering = player.playbackState == Player.STATE_BUFFERING

    val act: (() -> Unit) -> (() -> Unit) = { block ->
        {
            interaction++
            block()
        }
    }
    val seekBy: (Long) -> Unit = { delta ->
        val dur = player.duration
        val raw = (player.currentPosition + delta).coerceAtLeast(0L)
        player.seekTo(if (dur > 0L) raw.coerceAtMost(dur) else raw)
    }
    val applyBrightness: (Float) -> Unit = { v ->
        brightness = v.coerceIn(0.02f, 1f)
        window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = brightness
            win.attributes = lp
        }
    }
    val applyVolume: (Float) -> Unit = { v ->
        volume = v.coerceIn(0f, 1f)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).toInt().coerceIn(0, maxVolume), 0)
    }
    val saveResume: () -> Unit = {
        val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
        val pos = player.currentPosition
        val dur = player.duration
        if (current == track.uri && dur > 0L) {
            if (pos > 5_000L && pos < dur - 10_000L) ResumeStore.put(context, track.uri, pos)
            else if (pos >= dur - 10_000L) ResumeStore.clear(context, track.uri)
        }
    }
    val enterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val ratio = if (videoSize.width > 0 && videoSize.height > 0) Rational(videoSize.width, videoSize.height) else Rational(16, 9)
                activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
            }
        }
    }

    // Video size and track changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
            override fun onTracksChanged(tracks: Tracks) {
                tracksRevision++
            }
        }
        player.addListener(listener)
        videoSize = player.videoSize
        onDispose { player.removeListener(listener) }
    }

    // Immersive mode, keep screen on, restore everything when leaving
    DisposableEffect(activity) {
        val win = activity?.window
        val controller = win?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            win?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
            player.volume = 1f
        }
    }

    // Picture-in-picture state
    DisposableEffect(activity) {
        val pipActivity = activity as? ComponentActivity
        val consumer = Consumer<PictureInPictureModeChangedInfo> { info -> inPip = info.isInPictureInPictureMode }
        pipActivity?.addOnPictureInPictureModeChangedListener(consumer)
        onDispose { pipActivity?.removeOnPictureInPictureModeChangedListener(consumer) }
    }

    // Orientation: auto follows the video, then landscape, then portrait
    LaunchedEffect(orientationMode, videoSize) {
        activity?.requestedOrientation = when (orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> if (videoSize.width > videoSize.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else if (videoSize.height > videoSize.width) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    // Resume where the video was left
    LaunchedEffect(track.uri) {
        val saved = ResumeStore.get(context, track.uri)
        if (saved > 5_000L) {
            var tries = 0
            while (player.duration <= 0L && tries < 50) {
                delay(100)
                tries++
            }
            val total = player.duration
            if (total > 0L && saved < total - 10_000L && player.currentPosition < 2_000L) {
                player.seekTo(saved)
                resumeNote = "Resumed from ${formatTime(saved)}"
                delay(2500)
                resumeNote = null
            }
        }
    }
    LaunchedEffect(track.uri) {
        while (true) {
            delay(5000)
            saveResume()
        }
    }
    DisposableEffect(track.uri) { onDispose { saveResume() } }

    // A-B loop
    LaunchedEffect(abA, abB) {
        val a = abA
        val b = abB
        if (a != null && b != null) {
            while (true) {
                if (player.currentPosition >= b) player.seekTo(a)
                delay(150)
            }
        }
    }

    // Auto-hide and transient overlays
    LaunchedEffect(controlsVisible, interaction, snap.isPlaying, sheet, locked) {
        if (controlsVisible && snap.isPlaying && sheet == null && !locked) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(2500)
            showUnlock = false
        }
    }
    LaunchedEffect(hud?.tick) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }
    LaunchedEffect(seekFlash?.id) {
        if (seekFlash != null) {
            delay(650)
            seekFlash = null
        }
    }

    BackHandler(enabled = true) {
        when {
            sheet != null -> sheet = null
            locked -> showUnlock = true
            else -> onClose()
        }
    }

    val showUi = !inPip
    val progress = if (seekingBar) barPreview
    else if (snap.durationMs > 0L) (snap.positionMs.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f

    ProvideLiquid {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        subtitleView?.setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.resizeMode = resizeMode
                    pv.subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Gesture layer
            if (showUi) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(locked) {
                            detectTapGestures(
                                onTap = {
                                    if (locked) {
                                        showUnlock = !showUnlock
                                    } else {
                                        controlsVisible = !controlsVisible
                                        interaction++
                                    }
                                },
                                onDoubleTap = { offset ->
                                    if (!locked) {
                                        val left = offset.x < size.width / 2f
                                        seekBy(if (left) -10_000L else 10_000L)
                                        val side = if (left) -1 else 1
                                        val prev = seekFlash
                                        val seconds = if (prev != null && prev.side == side) prev.seconds + 10 else 10
                                        seekFlash = SeekFlash(side, seconds, (prev?.id ?: 0) + 1)
                                    }
                                },
                                onLongPress = {
                                    if (!locked) {
                                        boostPrev = player.playbackParameters.speed
                                        boosting = true
                                        player.setPlaybackSpeed(2f)
                                    }
                                },
                                onPress = {
                                    tryAwaitRelease()
                                    if (boosting) {
                                        boosting = false
                                        player.setPlaybackSpeed(boostPrev)
                                    }
                                }
                            )
                        }
                        .pointerInput(locked) {
                            if (!locked) {
                                var mode = 0
                                var leftSide = false
                                var startPos = 0L
                                var accum = 0f
                                var target = 0L
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        mode = 0
                                        accum = 0f
                                        leftSide = offset.x < size.width / 2f
                                        startPos = player.currentPosition
                                        target = startPos
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        if (mode == 0) {
                                            mode = if (abs(drag.x) > abs(drag.y)) 1 else if (leftSide) 2 else 3
                                        }
                                        when (mode) {
                                            1 -> {
                                                accum += drag.x
                                                val dur = player.duration
                                                val max = if (dur > 0L) dur else Long.MAX_VALUE
                                                target = (startPos + (accum * 120f).toLong()).coerceIn(0L, max)
                                                seekPreview = target
                                            }
                                            2 -> {
                                                applyBrightness(brightness - drag.y / size.height * 1.4f)
                                                hud = Hud(0, brightness, (hud?.tick ?: 0) + 1)
                                            }
                                            3 -> {
                                                applyVolume(volume - drag.y / size.height * 1.4f)
                                                hud = Hud(1, volume, (hud?.tick ?: 0) + 1)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (mode == 1) player.seekTo(target)
                                        seekPreview = null
                                        mode = 0
                                    },
                                    onDragCancel = {
                                        seekPreview = null
                                        mode = 0
                                    }
                                )
                            }
                        }
                )
            }

            if (showUi && buffering && !controlsVisible) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = accent)
            }

            // Brightness / volume HUD
            if (showUi) {
                hud?.let { h ->
                    LevelHud(
                        icon = if (h.kind == 0) Icons.Default.BrightnessMedium else Icons.Default.VolumeUp,
                        value = h.value,
                        accent = accent,
                        modifier = Modifier.align(if (h.kind == 0) Alignment.CenterStart else Alignment.CenterEnd)
                    )
                }
                seekPreview?.let { target ->
                    val delta = target - snap.positionMs
                    LiquidPanel(Modifier.align(Alignment.Center), RoundedCornerShape(26.dp), accent, 1.5f) {
                        Column(Modifier.padding(horizontal = 26.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatTime(target), fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                            Text(
                                (if (delta >= 0L) "+" else "-") + formatTime(abs(delta)),
                                color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                seekFlash?.let { flash ->
                    Box(
                        Modifier
                            .align(if (flash.side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 56.dp)
                    ) {
                        LiquidPanel(shape = RoundedCornerShape(40.dp), accent = accent, strength = 1.5f) {
                            Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (flash.side < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                                    null, modifier = Modifier.size(28.dp)
                                )
                                Text("${flash.seconds}s", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = boosting,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
                ) {
                    LiquidChip("2x  FAST PLAY", Icons.Default.FastForward, accent, active = true, onClick = {})
                }
                resumeNote?.let { note ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)) {
                        LiquidChip(note, Icons.Default.Replay10, accent, active = true, onClick = {})
                    }
                }
            }

            // Unlock button while locked
            AnimatedVisibility(
                visible = showUi && locked && showUnlock,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
            ) {
                LiquidButton(
                    Icons.Default.LockOpen, "Unlock controls",
                    { locked = false; controlsVisible = true; interaction++ },
                    size = 56.dp, accent = accent
                )
            }

            // Top bar
            AnimatedVisibility(
                visible = showUi && controlsVisible && !locked,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LiquidPanel(
                    Modifier.displayCutoutPadding().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                    RoundedCornerShape(26.dp), accent
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiquidButton(Icons.Default.ArrowBack, "Close video", onClose, size = 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                            val dims = if (videoSize.width > 0) "  \u2022  ${videoSize.width}\u00d7${videoSize.height}" else ""
                            Text(
                                "${snap.index + 1} / ${snap.count.coerceAtLeast(1)}$dims",
                                color = accent, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        LiquidButton(Icons.Default.PictureInPictureAlt, "Picture in picture", enterPip, size = 42.dp)
                    }
                }
            }

            // Centre transport
            AnimatedVisibility(
                visible = showUi && controlsVisible && !locked,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiquidButton(Icons.Default.SkipPrevious, "Previous", act { player.seekToPreviousMediaItem() }, size = 46.dp)
                    LiquidButton(Icons.Default.Replay10, "Back 10 seconds", act { seekBy(-10_000L) }, size = 52.dp)
                    LiquidButton(
                        if (snap.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play or pause",
                        act { if (snap.isPlaying) player.pause() else player.play() },
                        size = 76.dp, tint = Color(0xFF07101C), accent = accent, prominent = true
                    )
                    LiquidButton(Icons.Default.Forward10, "Forward 10 seconds", act { seekBy(10_000L) }, size = 52.dp)
                    LiquidButton(Icons.Default.SkipNext, "Next", act { player.seekToNextMediaItem() }, size = 46.dp)
                }
            }

            // Bottom panel
            AnimatedVisibility(
                visible = showUi && controlsVisible && !locked,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LiquidPanel(
                    Modifier.displayCutoutPadding().navigationBarsPadding().padding(14.dp).fillMaxWidth(),
                    RoundedCornerShape(30.dp), accent
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTime(if (seekingBar) (barPreview * snap.durationMs).toLong() else snap.positionMs),
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = progress,
                                onValueChange = {
                                    seekingBar = true
                                    barPreview = it
                                    interaction++
                                },
                                onValueChangeFinished = {
                                    val total = player.duration
                                    if (total > 0L) player.seekTo((barPreview * total).toLong())
                                    seekingBar = false
                                },
                                enabled = snap.durationMs > 0L,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                                )
                            )
                            Text(formatTime(snap.durationMs), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        val a = abA
                        if (a != null) {
                            val b = abB
                            Text(
                                "A\u2013B loop  ${formatTime(a)} \u2192 ${if (b != null) formatTime(b) else "set B"}",
                                color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidChip("Lock", Icons.Default.Lock, accent, onClick = {
                                locked = true
                                controlsVisible = false
                                showUnlock = true
                            })
                            LiquidChip(formatSpeed(snap.speed), Icons.Default.Speed, accent, active = snap.speed != 1f,
                                onClick = act { sheet = "speed" })
                            LiquidChip(
                                when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                    else -> "Zoom"
                                },
                                Icons.Default.AspectRatio, accent,
                                onClick = act {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            )
                            LiquidChip("Subtitles", Icons.Default.Subtitles, accent, onClick = act { sheet = "subs" })
                            LiquidChip("Audio", Icons.Default.Audiotrack, accent, onClick = act { sheet = "audio" })
                            LiquidChip(
                                when (orientationMode) { 1 -> "Landscape"; 2 -> "Portrait"; else -> "Auto rotate" },
                                Icons.Default.ScreenRotation, accent, active = orientationMode != 0,
                                onClick = act { orientationMode = (orientationMode + 1) % 3 }
                            )
                            LiquidChip(
                                when {
                                    abA == null -> "A\u2013B repeat"
                                    abB == null -> "Set B"
                                    else -> "Loop on"
                                },
                                Icons.Default.Repeat, accent, active = abA != null,
                                onClick = act {
                                    val start = abA
                                    if (start == null) {
                                        abA = player.currentPosition
                                    } else if (abB == null) {
                                        val p = player.currentPosition
                                        if (p > start + 500L) abB = p else abA = p
                                    } else {
                                        abA = null
                                        abB = null
                                    }
                                }
                            )
                            LiquidChip(
                                if (muted) "Muted" else "Sound",
                                if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                accent, active = muted, onClick = act { muted = !muted }
                            )
                            LiquidChip(sleepLabel ?: "Sleep", Icons.Default.Bedtime, accent, active = sleepLabel != null,
                                onClick = act { onSleep() })
                        }
                    }
                }
            }

            // Sheets
            AnimatedVisibility(visible = showUi && sheet != null, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x99000000)).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { sheet = null }
                )
            }
            AnimatedVisibility(
                visible = showUi && sheet != null,
                enter = slideInVertically(tween(280)) { it } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LiquidPanel(
                    Modifier.displayCutoutPadding().navigationBarsPadding().padding(12.dp).fillMaxWidth().widthIn(max = 560.dp),
                    RoundedCornerShape(30.dp), accent, 1.6f
                ) {
                    Column(
                        Modifier.padding(18.dp).heightIn(max = 340.dp).verticalScroll(rememberScrollState())
                    ) {
                        when (sheet) {
                            "speed" -> {
                                SheetTitle("PLAYBACK SPEED", accent)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).forEach { s ->
                                        LiquidChip(formatSpeed(s), null, accent, active = abs(snap.speed - s) < 0.01f, onClick = {
                                            player.setPlaybackSpeed(s)
                                            sheet = null
                                        })
                                    }
                                }
                            }
                            "audio" -> {
                                SheetTitle("AUDIO TRACK", accent)
                                val groups = remember(tracksRevision) {
                                    player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                                }
                                var rows = 0
                                groups.forEach { group ->
                                    for (index in 0 until group.length) {
                                        if (group.isTrackSupported(index)) {
                                            rows++
                                            val format = group.getTrackFormat(index)
                                            SheetRow(
                                                trackLabel(format, "Track $rows"),
                                                audioDetail(format),
                                                group.isTrackSelected(index), accent
                                            ) {
                                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                                    .build()
                                                sheet = null
                                            }
                                        }
                                    }
                                }
                                if (rows == 0) Text("This video has a single audio track.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                            "subs" -> {
                                SheetTitle("SUBTITLES", accent)
                                val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                                SheetRow("Off", null, textOff, accent) {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .build()
                                    sheet = null
                                }
                                val groups = remember(tracksRevision, textOff) {
                                    player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                }
                                var rows = 0
                                groups.forEach { group ->
                                    for (index in 0 until group.length) {
                                        if (group.isTrackSupported(index)) {
                                            rows++
                                            val format = group.getTrackFormat(index)
                                            SheetRow(
                                                trackLabel(format, "Subtitle $rows"), null,
                                                group.isTrackSelected(index) && !textOff, accent
                                            ) {
                                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                                    .build()
                                                sheet = null
                                            }
                                        }
                                    }
                                }
                                if (rows == 0) {
                                    Text("No embedded subtitles in this video.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                                LiquidChip("Load .srt / .vtt file", Icons.Default.FileOpen, accent, onClick = {
                                    sheet = null
                                    onLoadSubtitle()
                                })
                                Spacer(Modifier.height(12.dp))
                                Text("TEXT SIZE", color = accent, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Small" to 0.75f, "Normal" to 1f, "Large" to 1.35f, "Huge" to 1.8f).forEach { (label, scale) ->
                                        LiquidChip(label, null, accent, active = subtitleScale == scale, onClick = { subtitleScale = scale })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String, accent: Color) {
    Text(text, color = accent, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.4.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SheetRow(label: String, sub: String?, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
        }
        if (selected) Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LevelHud(icon: ImageVector, value: Float, accent: Color, modifier: Modifier) {
    LiquidPanel(modifier.padding(horizontal = 28.dp), RoundedCornerShape(26.dp), accent, 1.4f) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.width(6.dp).height(110.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(accent))
            }
            Spacer(Modifier.height(8.dp))
            Text("${(value * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun trackLabel(format: Format, fallback: String): String {
    val label = format.label?.takeIf { it.isNotBlank() }
    if (label != null) return label
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
    if (language != null) {
        val name = Locale.forLanguageTag(language).displayLanguage
        return if (name.isNotBlank()) name else language
    }
    return fallback
}

private fun audioDetail(format: Format): String? {
    val parts = ArrayList<String>()
    if (format.channelCount > 0) parts += "${format.channelCount} ch"
    format.sampleMimeType?.substringAfter('/')?.uppercase()?.let { parts += it }
    return if (parts.isEmpty()) null else parts.joinToString("  \u2022  ")
}
