package com.nkplayer.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class HudKind { Brightness, Volume }

/**
 * Fullscreen video screen (self-contained: does not depend on the app theme/design-system files).
 *
 * Gestures (disabled while locked):
 *  - swipe up/down on the LEFT half  : brightness
 *  - swipe up/down on the RIGHT half : volume
 *  - double tap left / right half    : 10 s backward / forward
 *  - pinch                           : zoom in / out (two-finger drag pans while zoomed)
 *  - single tap                      : show / hide controls
 * Lock button: ignores every touch except revealing the unlock button.
 * Screen orientation is handled by MainActivity (unchanged).
 */
@Composable
fun NKFullScreenVideo(
    player: Player,
    title: String,
    accent: Color,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var locked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var lockHintTick by remember { mutableIntStateOf(0) }
    var lockHintVisible by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    var brightness by remember { mutableFloatStateOf(readBrightness(context, activity)) }
    var volume by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var hud by remember { mutableStateOf<HudKind?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var seekFlash by remember { mutableIntStateOf(0) }
    var seekFlashTick by remember { mutableIntStateOf(0) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Put the window brightness back when leaving fullscreen.
    DisposableEffect(activity) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = original
                w.attributes = lp
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            playing = player.isPlaying
            delay(250)
        }
    }
    LaunchedEffect(controlsVisible, interactionTick, locked) {
        if (controlsVisible && !locked) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(lockHintTick) {
        if (lockHintTick > 0) {
            lockHintVisible = true
            delay(2500)
            lockHintVisible = false
        }
    }
    LaunchedEffect(hudTick) {
        if (hudTick > 0) {
            delay(900)
            hud = null
        }
    }
    LaunchedEffect(seekFlashTick) {
        if (seekFlashTick > 0) {
            delay(650)
            seekFlash = 0
        }
    }

    fun seekBy(deltaMs: Long) {
        val total = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, total))
    }

    val adjust: (Boolean, Float) -> Unit = { leftSide, fraction ->
        if (leftSide) {
            brightness = (brightness + fraction * 1.3f).coerceIn(0.02f, 1f)
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = brightness
                w.attributes = lp
            }
            hud = HudKind.Brightness
        } else {
            volume = (volume + fraction * 1.3f).coerceIn(0f, 1f)
            runCatching {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
            }
            hud = HudKind.Volume
        }
        hudTick++
    }

    Dialog(
        onDismissRequest = { if (!locked) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Immersive: hide system bars, draw under the notch, keep the screen awake.
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (Build.VERSION.SDK_INT >= 28) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = lp
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // 1) Video (zoom / pan applied here)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panX
                    translationY = panY
                }
            ) {
                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.nk_texture_player_view, null, false) as PlayerView)
                            .also { it.player = player }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2) Gesture layer
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = {
                                if (locked) lockHintTick++ else controlsVisible = !controlsVisible
                            },
                            onDoubleTap = { offset ->
                                if (!locked) {
                                    val left = offset.x < size.width / 2f
                                    seekBy(if (left) -10_000L else 10_000L)
                                    seekFlash = if (left) -1 else 1
                                    seekFlashTick++
                                }
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val leftSide = down.position.x < size.width / 2f
                            var mode = 0 // 0 undecided, 1 vertical adjust, 2 pinch, 3 ignore
                            var totalDx = 0f
                            var totalDy = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }
                                if (pressedChanges.size >= 2) {
                                    mode = 2
                                    val a = pressedChanges[0]
                                    val b = pressedChanges[1]
                                    val dist = (a.position - b.position).getDistance()
                                    val prevDist = (a.previousPosition - b.previousPosition).getDistance()
                                    val centroid = (a.position + b.position) / 2f
                                    val prevCentroid = (a.previousPosition + b.previousPosition) / 2f
                                    if (prevDist > 0f) zoom = (zoom * dist / prevDist).coerceIn(1f, 5f)
                                    val maxX = (zoom - 1f) * size.width / 2f
                                    val maxY = (zoom - 1f) * size.height / 2f
                                    panX = (panX + centroid.x - prevCentroid.x).coerceIn(-maxX, maxX)
                                    panY = (panY + centroid.y - prevCentroid.y).coerceIn(-maxY, maxY)
                                    event.changes.forEach { it.consume() }
                                } else if (pressedChanges.size == 1 && mode != 2) {
                                    val c = pressedChanges[0]
                                    val dx = c.position.x - c.previousPosition.x
                                    val dy = c.position.y - c.previousPosition.y
                                    totalDx += dx
                                    totalDy += dy
                                    if (mode == 0) {
                                        if (abs(totalDy) > slop && abs(totalDy) > abs(totalDx)) mode = 1
                                        else if (abs(totalDx) > slop) mode = 3
                                    }
                                    if (mode == 1) {
                                        adjust(leftSide, -dy / size.height)
                                        c.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )

            // 3) Brightness / volume indicator
            hud?.let { kind ->
                val value = if (kind == HudKind.Brightness) brightness else volume
                FsGlass(modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 24.dp)) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                kind == HudKind.Brightness -> Icons.Default.BrightnessMedium
                                volume <= 0.001f -> Icons.Default.VolumeOff
                                else -> Icons.Default.VolumeUp
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        LinearProgressIndicator(
                            progress = { value },
                            modifier = Modifier.width(120.dp),
                            color = accent,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${(value * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4) Double-tap seek feedback
            if (seekFlash != 0) {
                FsGlass(
                    modifier = Modifier
                        .align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 44.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (seekFlash < 0) Icons.Default.Replay10 else Icons.Default.Forward10,
                            contentDescription = null, tint = accent, modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("10 sec", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5) Controls
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color(0xB3000000))))
                        .safeDrawingPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )
                        if (zoom > 1.02f) {
                            NKLiquidGlassButton(
                                onClick = {
                                    zoom = 1f
                                    panX = 0f
                                    panY = 0f
                                    interactionTick++
                                },
                                accent = accent,
                                size = 46.dp,
                                highlighted = true,
                                contentDescription = "Reset zoom"
                            ) {
                                Text("%.1fx".format(zoom), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        NKLiquidGlassButton(
                            onClick = onClose,
                            accent = accent,
                            size = 46.dp,
                            contentDescription = "Exit full screen"
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }

                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        NKLiquidGlassButton(
                            onClick = { seekBy(-10_000L); interactionTick++ },
                            accent = accent,
                            size = 56.dp,
                            contentDescription = "Back 10 seconds"
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        NKLiquidGlassButton(
                            onClick = {
                                if (playing) player.pause() else player.play()
                                interactionTick++
                            },
                            accent = accent,
                            size = 78.dp,
                            highlighted = true,
                            contentDescription = if (playing) "Pause" else "Play"
                        ) {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null, tint = accent, modifier = Modifier.size(42.dp)
                            )
                        }
                        NKLiquidGlassButton(
                            onClick = { seekBy(10_000L); interactionTick++ },
                            accent = accent,
                            size = 56.dp,
                            contentDescription = "Forward 10 seconds"
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FsGlass(modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp)) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                Text(
                                    fsTime(if (scrubbing) (scrubFraction * duration).toLong() else position),
                                    color = Color.White, fontSize = 11.sp
                                )
                                Slider(
                                    value = if (scrubbing) scrubFraction else fraction,
                                    onValueChange = {
                                        scrubbing = true
                                        scrubFraction = it
                                        interactionTick++
                                    },
                                    onValueChangeFinished = {
                                        if (duration > 0L) player.seekTo((scrubFraction * duration).toLong())
                                        scrubbing = false
                                    },
                                    enabled = duration > 0L,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = Color(0x44FFFFFF)
                                    )
                                )
                                Text(fsTime(duration), color = Color.White, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        NKLiquidGlassButton(
                            onClick = {
                                locked = true
                                controlsVisible = false
                                lockHintTick++
                            },
                            accent = accent,
                            size = 52.dp,
                            contentDescription = "Lock controls"
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // 6) Unlock button (only while locked, appears after a touch)
            AnimatedVisibility(
                visible = locked && lockHintVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(260)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    NKLiquidGlassButton(
                        onClick = {
                            locked = false
                            controlsVisible = true
                            interactionTick++
                        },
                        accent = accent,
                        size = 56.dp,
                        highlighted = true,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        contentDescription = "Unlock controls"
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

/** Small frosted panel used only inside the fullscreen player. */
@Composable
private fun FsGlass(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(8.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xB30B1220))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.025f))))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.32f), Color(0x26FFFFFF), Color(0x10FFFFFF))
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * Liquid-glass round button: translucent glass body, gradient rim, specular highlight,
 * and a squash-and-stretch wobble (underdamped spring) when pressed.
 */
@Composable
fun NKLiquidGlassButton(
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    highlighted: Boolean = false,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 450f),
        label = "nk-glass-button-press"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = 1f + 0.16f * press
                scaleY = 1f - 0.08f * press
            }
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.40f),
                spotColor = Color.Black
            )
            .clip(CircleShape)
            .background(Color(0x990B1220))
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))
            )
            .drawBehind {
                val r = this.size.minDimension / 2f
                val glow = 0.10f + 0.40f * press.coerceIn(0f, 1f) + (if (highlighted) 0.20f else 0f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glow), Color.Transparent),
                        center = Offset(this.size.width * 0.5f, this.size.height * 0.88f),
                        radius = r * 1.15f
                    ),
                    radius = r
                )
                drawOval(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)),
                    topLeft = Offset(this.size.width * 0.18f, this.size.height * 0.07f),
                    size = Size(this.size.width * 0.64f, this.size.height * 0.34f)
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.08f), accent.copy(alpha = 0.50f))
                ),
                shape = CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content
    )
}

private fun fsTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun readBrightness(context: Context, activity: Activity?): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue >= 0f) return windowValue.coerceIn(0.02f, 1f)
    val system = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    return (system / 255f).coerceIn(0.02f, 1f)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
