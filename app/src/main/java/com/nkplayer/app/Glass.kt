@file:OptIn(ExperimentalTextApi::class)

package com.nkplayer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

@Composable
fun NkTheme(palette: NkPalette, content: @Composable () -> Unit) {
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

// ---------------------------------------------------------------------------------------------
// Backdrop engine: real blur of what sits behind a floating glass surface (Android 12+).
//
// How it works (no third-party library):
//  1. GlassBackground owns a GlassBackdrop and re-draws the animated gradient on demand.
//  2. The scrolling content area is tagged with Modifier.glassSource(backdrop); it records itself
//     into a GraphicsLayer.
//  3. A floating surface (nav bar, mini player) draws the gradient + that layer, shifted to its own
//     position, into its own layer with a RenderEffect blur, then paints the glass material on top.
// Below Android 12 the blur is skipped and a darker translucent fill keeps text readable.
// ---------------------------------------------------------------------------------------------

private val BlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Blur radius of floating glass. Lower = list shapes stay recognisable underneath. */
private val FloatingBlurRadius: Dp = 14.dp

/** With live blur the dark tint is scaled down so the blurred list actually shows through. */
private const val LiveTintScale = 0.3f

/** With live blur the white wash of the glass material is scaled down for the same reason. */
private const val LiveWashScale = 0.6f

@Stable
class GlassBackdrop internal constructor(internal val contentLayer: GraphicsLayer) {
    var palette by mutableStateOf(NkPalettes.first())
    internal var shiftState: State<Float> = mutableFloatStateOf(0f)
    var contentOrigin by mutableStateOf(Offset.Zero)
    var rootOrigin by mutableStateOf(Offset.Zero)
    var rootSize by mutableStateOf(IntSize.Zero)
    internal val version = mutableIntStateOf(0)
    internal var counter = 0
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Paints the animated gradient + colour glows for a canvas of [w] x [h] pixels. */
internal fun DrawScope.drawGlassBackdrop(p: NkPalette, t: Float, w: Float, h: Float) {
    drawRect(
        Brush.verticalGradient(listOf(p.bgTop, p.bgMid, p.bgBottom), startY = 0f, endY = h),
        size = Size(w, h)
    )
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

/** Slowly drifting gradient with soft colour glows; this is what the glass layers sit on. */
@Composable
fun GlassBackground(p: NkPalette, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "glow")
    val tState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowShift"
    )
    val backdrop = rememberGlassBackdrop()
    backdrop.shiftState = tState
    SideEffect { backdrop.palette = p }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    backdrop.rootOrigin = it.positionInRoot()
                    backdrop.rootSize = it.size
                }
                .drawBehind { drawGlassBackdrop(p, tState.value, size.width, size.height) },
            content = content
        )
    }
}

/** Tag the scrolling content that floating glass should blur (list area, page body). */
fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this else this
        .onGloballyPositioned { backdrop.contentOrigin = it.positionInRoot() }
        .drawWithContent {
            val layer = backdrop.contentLayer
            val w = size.width.roundToInt().coerceAtLeast(1)
            val h = size.height.roundToInt().coerceAtLeast(1)
            layer.record(this, layoutDirection, IntSize(w, h)) {
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
            // Tell blurred surfaces that the content changed (scrolling) so they refresh.
            backdrop.counter += 1
            backdrop.version.intValue = backdrop.counter
        }

/** Blurred copy of gradient + source content behind this node. No-op below Android 12. */
private fun Modifier.backdropBlur(radius: Dp = FloatingBlurRadius): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    if (backdrop == null || !BlurSupported) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                backdrop.version.intValue // subscribe: redraw when the source content changes
                val t = backdrop.shiftState.value
                val rootW = backdrop.rootSize.width.toFloat()
                val rootH = backdrop.rootSize.height.toFloat()
                if (rootW > 0f && rootH > 0f && size.width >= 1f && size.height >= 1f) {
                    val rel = origin - backdrop.rootOrigin
                    val contentRel = backdrop.contentOrigin - backdrop.rootOrigin
                    val px = radius.toPx()
                    layer.record(
                        this,
                        layoutDirection,
                        IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
                    ) {
                        translate(-rel.x, -rel.y) {
                            drawGlassBackdrop(backdrop.palette, t, rootW, rootH)
                            translate(contentRel.x, contentRel.y) { drawLayer(backdrop.contentLayer) }
                        }
                    }
                    layer.renderEffect = BlurEffect(px, px, TileMode.Clamp)
                    drawLayer(layer)
                }
            }
    }
}

/**
 * Fill for a floating glass surface: real backdrop blur plus [tint] when available,
 * otherwise a darker translucent [fallback] so text stays readable.
 */
fun Modifier.glassBlurFill(
    tint: Color = Color(0x990B1220),
    fallback: Color = Color(0xCC0B1220),
    enabled: Boolean = true
): Modifier = composed {
    val live = enabled && BlurSupported && LocalGlassBackdrop.current != null
    when {
        !enabled -> Modifier
        live -> Modifier.backdropBlur().background(tint.copy(alpha = tint.alpha * LiveTintScale))
        else -> Modifier.background(fallback)
    }
}

// ---------------------------------------------------------------------------------------------
// Glass material + jelly press
// ---------------------------------------------------------------------------------------------

/** Squashes slightly while pressed and springs back with a small overshoot (jelly feel). */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.955f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 380f),
        label = "nk-press-scale"
    )
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Frosted-glass surface: translucent gradient, soft diagonal sheen, a bright specular line on the top
 * edge and a rim that fades from white to the accent colour. With [blur] = true the surface also
 * blurs whatever is behind it (use for floating bars; plain cards keep blur = false for speed).
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    strength: Float = 1f,
    blur: Boolean = false
): Modifier = composed {
    val accent = MaterialTheme.colorScheme.primary
    val live = blur && BlurSupported && LocalGlassBackdrop.current != null
    val wash = if (live) LiveWashScale else 1f
    val topAlpha = (0.17f * strength * wash).coerceAtMost(0.5f)
    val bottomAlpha = (0.06f * strength * wash).coerceAtMost(0.3f)
    val sheenAlpha = (0.10f * strength).coerceAtMost(0.22f)
    Modifier
        .clip(shape)
        .glassBlurFill(tint = Color(0x400B1220), fallback = Color(0xB30B1220), enabled = blur)
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = topAlpha), Color.White.copy(alpha = bottomAlpha))
            )
        )
        .drawBehind {
            // Soft diagonal sheen across the upper-left of the surface.
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = sheenAlpha), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.65f, size.height * 0.65f)
                )
            )
            // Specular line hugging the top edge.
            val inset = minOf(size.minDimension * 0.3f, 24.dp.toPx())
            if (size.width > inset * 2f + 8f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.50f), Color.Transparent),
                        startX = inset,
                        endX = size.width - inset
                    ),
                    start = Offset(inset, 1.dp.toPx()),
                    end = Offset(size.width - inset, 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.05f),
                    accent.copy(alpha = 0.32f)
                )
            ),
            shape
        )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    strength: Float = 1f,
    blur: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .let { if (onClick != null) it.pressScale(interaction, 0.975f) else it }
        .glass(shape, strength, blur)
        .let {
            if (onClick != null) {
                it.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            } else it
        }
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
    val interaction = remember { MutableInteractionSource() }
    val look = if (filled != null) Modifier.clip(CircleShape).background(filled) else Modifier.glass(CircleShape)
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.90f)
            .then(look)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AccentButton(text: String, icon: ImageVector?, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = Color(0xFF07101C)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(52.dp)
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color(0xFFB04CFF), 0.5f))))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        endY = size.height * 0.55f
                    )
                )
            }
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
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
    val interaction = remember { MutableInteractionSource() }
    val look = Modifier
        .let { if (onClick != null) it.pressScale(interaction, 0.93f) else it }
        .clip(shape)
        .background(if (active) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
        .border(1.dp, if (active) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.22f), shape)
        .let {
            if (onClick != null) {
                it.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            } else it
        }
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
