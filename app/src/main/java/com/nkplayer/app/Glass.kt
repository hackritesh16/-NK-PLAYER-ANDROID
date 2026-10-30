package com.nkplayer.app

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/*
 * NK PLAYER glass kit.
 *
 * - GlassCard / GlassTile: translucent "frosted" surfaces (tint + sheen + gradient rim).
 * - AuroraBackground: soft colour orbs that give the glass something to show through.
 * - GlassBackdrop: real backdrop blur (Android 12+/API 31+) for surfaces that float OVER the
 *   app content (the liquid navigation bar). Older devices fall back to a denser tint.
 */

/** Flip to false to disable the real backdrop blur everywhere and use the tint-only fallback. */
internal const val ENABLE_BACKDROP_BLUR = true

internal val NkInk = Color(0xFF070A12)
internal val NkViolet = Color(0xFFAB8AFF)
internal val NkGlassBase = Color(0xFF0B1424)
internal val NkTextMuted = Color(0xFF9DAEC5)

// ---------------------------------------------------------------------------------------------
// Backdrop blur
// ---------------------------------------------------------------------------------------------

/**
 * Shared state between the app content (the "source") and glass surfaces that blur what is
 * behind them. The source records itself into [content]; a consumer draws a blurred copy of the
 * part of [content] that lies under it.
 */
@Stable
internal class GlassBackdrop internal constructor(
    internal val content: GraphicsLayer,
    val enabled: Boolean
) {
    var sourceOrigin by mutableStateOf(Offset.Zero)
    internal val revision = mutableIntStateOf(0)
    private var counter = 0

    /** Called by the source after every re-record so consumers redraw with fresh pixels. */
    internal fun bump() {
        counter += 1
        revision.intValue = counter // write-only: the source must never observe this state.
    }
}

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        GlassBackdrop(layer, ENABLE_BACKDROP_BLUR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }
}

/** Marks everything drawn by this node as the content that glass surfaces blur. */
internal fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    if (!backdrop.enabled) return this
    return this
        .onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
        .drawWithContent {
            val layer = backdrop.content
            layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
            backdrop.bump()
        }
}

/**
 * Draws a blurred copy of the source content behind this node, clipped to a rounded rectangle.
 * No-op when [backdrop] is null or blur is unavailable (callers then use a denser tint).
 */
@Composable
internal fun Modifier.backdropBlur(
    backdrop: GlassBackdrop?,
    cornerRadius: Dp,
    blurRadius: Dp = 24.dp
): Modifier {
    if (backdrop == null || !backdrop.enabled) return this
    val density = LocalDensity.current
    val layer = rememberGraphicsLayer()
    remember(layer, density, blurRadius) {
        val px = with(density) { blurRadius.toPx() }
        layer.renderEffect = BlurEffect(px, px, TileMode.Clamp)
        layer.clip = true
        true
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val revision = backdrop.revision.intValue // subscribe: redraw when the source changes
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (revision < 0 || w <= 0 || h <= 0) return@drawBehind
            val dx = backdrop.sourceOrigin.x - origin.x
            val dy = backdrop.sourceOrigin.y - origin.y
            layer.record(this, layoutDirection, IntSize(w, h)) {
                translate(dx, dy) { drawLayer(backdrop.content) }
            }
            val r = cornerRadius.toPx()
            val clip = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
            }
            clipPath(clip) { drawLayer(layer) }
        }
}

// ---------------------------------------------------------------------------------------------
// Glass surfaces
// ---------------------------------------------------------------------------------------------

/**
 * Frosted-glass card: dark tint + white sheen + gradient rim. [emphasis] (0..1) strengthens the
 * accent glow and rim for active / selected states.
 */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    accent: Color = Color.Unspecified,
    emphasis: Float = 0f,
    baseAlpha: Float = 0.44f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val rimAccent = if (accent.isSpecified) accent else Color.White
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.40f + 0.22f * emphasis),
            Color.White.copy(alpha = 0.06f),
            rimAccent.copy(alpha = 0.10f + 0.34f * emphasis)
        )
    )
    Box(
        modifier
            .clip(shape)
            .background(NkGlassBase.copy(alpha = baseAlpha))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
                )
            )
            .then(
                if (accent.isSpecified && emphasis > 0f)
                    Modifier.background(accent.copy(alpha = 0.10f * emphasis))
                else Modifier
            )
            .border(1.dp, rim, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        propagateMinConstraints = true,
        content = content
    )
}

/** Small square glass tile used for icon buttons and logo plates. */
@Composable
internal fun GlassTile(
    size: Dp,
    cornerRadius: Dp,
    accent: Color,
    modifier: Modifier = Modifier,
    strength: Float = 1f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.28f * strength),
                        accent.copy(alpha = 0.08f * strength)
                    )
                )
            )
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), accent.copy(alpha = 0.28f))),
                shape
            )
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ---------------------------------------------------------------------------------------------
// Background
// ---------------------------------------------------------------------------------------------

private fun DrawScope.orb(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Deep canvas with soft accent orbs; this is what the glass surfaces pick up and blur. */
@Composable
internal fun AuroraBackground(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color(0xFF0A1220),
                0.45f to Color(0xFF0E1530),
                1.0f to NkInk
            )
        )
        orb(accent, size.width * 0.10f, size.height * 0.08f, size.width * 0.80f, 0.30f)
        orb(NkViolet, size.width * 0.98f, size.height * 0.36f, size.width * 0.72f, 0.24f)
        orb(Color(0xFF2F6BFF), size.width * 0.18f, size.height * 0.74f, size.width * 0.82f, 0.20f)
        orb(accent, size.width * 0.88f, size.height * 0.96f, size.width * 0.62f, 0.18f)
    }
}
