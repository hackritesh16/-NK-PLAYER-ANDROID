@file:OptIn(ExperimentalTextApi::class)

package com.nkplayer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Fonts: Unbounded (display) + Manrope (text). Downloaded into res/font by the fetchFonts task.
// ---------------------------------------------------------------------------------------------

private fun nkFamily(resId: Int): FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

val DisplayFont: FontFamily = nkFamily(R.font.unbounded)
val BodyFont: FontFamily = nkFamily(R.font.manrope)

private fun TextStyle.on(family: FontFamily): TextStyle = copy(fontFamily = family)

val NkTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.on(DisplayFont),
        displayMedium = t.displayMedium.on(DisplayFont),
        displaySmall = t.displaySmall.on(DisplayFont),
        headlineLarge = t.headlineLarge.on(DisplayFont),
        headlineMedium = t.headlineMedium.on(DisplayFont),
        headlineSmall = t.headlineSmall.on(DisplayFont),
        titleLarge = t.titleLarge.on(DisplayFont),
        titleMedium = t.titleMedium.on(BodyFont),
        titleSmall = t.titleSmall.on(BodyFont),
        bodyLarge = t.bodyLarge.on(BodyFont),
        bodyMedium = t.bodyMedium.on(BodyFont),
        bodySmall = t.bodySmall.on(BodyFont),
        labelLarge = t.labelLarge.on(BodyFont),
        labelMedium = t.labelMedium.on(BodyFont),
        labelSmall = t.labelSmall.on(BodyFont)
    )
}

// ---------------------------------------------------------------------------------------------
// Palettes (names are persisted by LibraryStore, so keep them stable)
// ---------------------------------------------------------------------------------------------

data class NkPalette(
    val name: String,
    val accent: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color
)

val NkPalettes = listOf(
    NkPalette("Neon Blue", Color(0xFF48E5FF), Color(0xFF14307A), Color(0xFF0B1646), Color(0xFF050818)),
    NkPalette("Cyber Purple", Color(0xFFB794FF), Color(0xFF3A1C8F), Color(0xFF1D0F52), Color(0xFF08051E)),
    NkPalette("Emerald Green", Color(0xFF37E6A4), Color(0xFF0E4D55), Color(0xFF082F3B), Color(0xFF030E14)),
    NkPalette("Crimson Red", Color(0xFFFF5470), Color(0xFF6B1238), Color(0xFF3A0B2A), Color(0xFF12050E)),
    NkPalette("Golden Amber", Color(0xFFFFC45C), Color(0xFF6B3A12), Color(0xFF3A1F18), Color(0xFF120A06))
)

fun paletteFor(name: String): NkPalette = NkPalettes.firstOrNull { it.name == name } ?: NkPalettes.first()

private val LocalNkHazeState = staticCompositionLocalOf<HazeState?> { null }
private val LocalNkNavHazeState = staticCompositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.nkBackdropBlur(): Modifier {
    val state = LocalNkHazeState.current ?: return this
    return hazeEffect(state = state, style = HazeMaterials.ultraThin()) {
        // Haze disables blur by default on API 31; NK PLAYER requires Android 12+,
        // so explicitly enable the real blur on the supported minimum API.
        blurEnabled = true
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.nkNavBackdropBlur(): Modifier {
    val state = LocalNkNavHazeState.current ?: return this
    return hazeEffect(state = state, style = HazeMaterials.ultraThin()) {
        // Haze disables blur by default on API 31; NK PLAYER requires Android 12+,
        // so explicitly enable the real blur on the supported minimum API.
        blurEnabled = true
    }
}

@Composable
fun Modifier.nkNavHazeSource(): Modifier {
    val state = LocalNkNavHazeState.current ?: return this
    return hazeSource(state)
}

@Composable
fun NkTheme(palette: NkPalette, content: @Composable () -> Unit) {
    val hazeState = remember { HazeState() }
    val navHazeState = remember { HazeState() }
    CompositionLocalProvider(
        LocalNkHazeState provides hazeState,
        LocalNkNavHazeState provides navHazeState
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                onPrimary = Color(0xFF07101C),
                secondary = palette.accent,
                background = palette.bgBottom,
                surface = Color(0xFF1A1245),
                onSurface = Color.White,
                onBackground = Color.White
            ),
            typography = NkTypography
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Glass building blocks
// ---------------------------------------------------------------------------------------------

/** Slowly drifting gradient with soft colour glows; this is what the glass layers sit on. */
@Composable
fun GlassBackground(p: NkPalette, content: @Composable BoxScope.() -> Unit) {
    val hazeState = LocalNkHazeState.current
    val sourceModifier = hazeState?.let { Modifier.hazeSource(it) } ?: Modifier
    val transition = rememberInfiniteTransition(label = "glow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowShift"
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .then(sourceModifier)
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bgMid, p.bgBottom)))
                .drawBehind {
                val w = size.width
                val h = size.height
                val c1 = Offset(w * (0.95f - 0.25f * t), h * (0.06f + 0.06f * t))
                drawCircle(
                    Brush.radialGradient(listOf(p.accent.copy(alpha = 0.30f), Color.Transparent), center = c1, radius = w * 0.8f),
                    radius = w * 0.8f,
                    center = c1
                )
                val c2 = Offset(w * (0.02f + 0.25f * t), h * (0.58f - 0.08f * t))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFB04CFF).copy(alpha = 0.24f), Color.Transparent), center = c2, radius = w * 0.85f),
                    radius = w * 0.85f,
                    center = c2
                )
                val c3 = Offset(w * 0.9f, h * 0.95f)
                drawCircle(
                    Brush.radialGradient(listOf(p.accent.copy(alpha = 0.16f), Color.Transparent), center = c3, radius = w * 0.7f),
                    radius = w * 0.7f,
                    center = c3
                )
            }
        )
        content()
    }
}

/** Frosted-glass surface: translucent white gradient, bright hairline border on the top-left edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (0.17f * strength).coerceAtMost(0.5f)),
                Color.White.copy(alpha = (0.06f * strength).coerceAtMost(0.3f))
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.22f))
        ),
        shape
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    strength: Float = 1f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.nkBackdropBlur().glass(shape, strength).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(base, content = content)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color.White,
    filled: Color? = null
) {
    val look = if (filled != null) Modifier.clip(CircleShape).background(filled) else Modifier.nkBackdropBlur().glass(CircleShape)
    Box(
        modifier.size(size).then(look).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AccentButton(text: String, icon: ImageVector?, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = Color(0xFF07101C)
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color(0xFFB04CFF), 0.5f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = ink, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp)
    }
}

@Composable
fun GlassChip(
    text: String,
    accent: Color,
    active: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    val look = Modifier
        .clip(shape)
        .background(if (active) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
        .border(1.dp, if (active) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.22f), shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(look.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = if (active) accent else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (active) accent else Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Artwork / thumbnails (embedded cover art for audio, a frame for video)
// ---------------------------------------------------------------------------------------------

private val thumbCache = LruCache<String, ImageBitmap>(64)

@Composable
fun rememberMediaThumb(uri: String, video: Boolean, maxDim: Int = 360): State<ImageBitmap?> {
    val context = LocalContext.current
    val key = "$uri|$video|$maxDim"
    return produceState<ImageBitmap?>(initialValue = thumbCache.get(key), key) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(uri))
                        val raw: Bitmap? = if (video) {
                            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } else {
                            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        }
                        raw?.let { b ->
                            val scale = maxDim.toFloat() / maxOf(b.width, b.height)
                            val out = if (scale < 1f) {
                                Bitmap.createScaledBitmap(
                                    b,
                                    (b.width * scale).toInt().coerceAtLeast(1),
                                    (b.height * scale).toInt().coerceAtLeast(1),
                                    true
                                )
                            } else b
                            out.asImageBitmap()
                        }
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            }
            if (loaded != null) thumbCache.put(key, loaded)
            value = loaded
        }
    }
}

@Composable
fun MediaArt(
    uri: String,
    video: Boolean,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    maxDim: Int = 320,
    iconSize: Dp = 24.dp
) {
    val thumb by rememberMediaThumb(uri, video, maxDim)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF7A4DFF).copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Playback state as Compose state (one polling loop shared by mini player and now-playing)
// ---------------------------------------------------------------------------------------------

data class PlaybackSnapshot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val speed: Float,
    val index: Int,
    val count: Int,
    val revision: Int
)

@Composable
fun rememberPlaybackSnapshot(player: Player): PlaybackSnapshot {
    var revision by remember(player) { mutableIntStateOf(0) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                revision++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            delay(400)
        }
    }
    return PlaybackSnapshot(
        isPlaying = player.isPlaying,
        positionMs = position,
        durationMs = duration,
        repeatMode = player.repeatMode,
        shuffle = player.shuffleModeEnabled,
        speed = player.playbackParameters.speed,
        index = player.currentMediaItemIndex,
        count = player.mediaItemCount,
        revision = revision
    )
}
