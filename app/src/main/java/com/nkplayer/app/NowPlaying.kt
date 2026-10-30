package com.nkplayer.app

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

/** Audio now-playing screen. Video has its own immersive screen (VideoPlayerScreen). */
@Composable
internal fun NowPlayingScreen(
    player: Player,
    track: Track,
    snap: PlaybackSnapshot,
    palette: NkPalette,
    sleepLabel: String?,
    onClose: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit
) {
    val accent = palette.accent
    val art by rememberMediaThumb(track.uri, false, 720)
    var seeking by remember { mutableStateOf(false) }
    var preview by remember { mutableFloatStateOf(0f) }
    val progress = if (seeking) preview
    else if (snap.durationMs > 0L) (snap.positionMs.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f

    ProvideLiquid {
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
                    LiquidButton(Icons.Default.KeyboardArrowDown, "Minimise player", onClose, size = 44.dp)
                    Text("NOW PLAYING", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = DisplayFont,
                        fontSize = 12.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.size(44.dp))
                }
                Spacer(Modifier.weight(0.5f))
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
                Spacer(Modifier.weight(0.4f))
                Text(track.name, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "AUDIO  \u2022  ${snap.index + 1} / ${snap.count.coerceAtLeast(1)}",
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
                    LiquidButton(Icons.Default.Shuffle, "Shuffle", { player.shuffleModeEnabled = !snap.shuffle },
                        size = 44.dp, tint = if (snap.shuffle) accent else Color.White, accent = accent)
                    LiquidButton(Icons.Default.SkipPrevious, "Previous", { player.seekToPreviousMediaItem() }, size = 52.dp)
                    LiquidButton(
                        if (snap.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play or pause",
                        { if (snap.isPlaying) player.pause() else player.play() },
                        size = 76.dp, tint = InkButton, accent = accent, prominent = true
                    )
                    LiquidButton(Icons.Default.SkipNext, "Next", { player.seekToNextMediaItem() }, size = 52.dp)
                    LiquidButton(
                        if (snap.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat mode",
                        {
                            player.repeatMode = when (snap.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        },
                        size = 44.dp,
                        tint = if (snap.repeatMode == Player.REPEAT_MODE_OFF) Color.White else accent,
                        accent = accent
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidChip(formatSpeed(snap.speed), Icons.Default.Speed, accent, active = snap.speed != 1f, onClick = onSpeed)
                    LiquidChip(sleepLabel ?: "Sleep", Icons.Default.Bedtime, accent, active = sleepLabel != null, onClick = onSleep)
                }
                Spacer(Modifier.weight(0.3f))
            }
        }
    }
}
