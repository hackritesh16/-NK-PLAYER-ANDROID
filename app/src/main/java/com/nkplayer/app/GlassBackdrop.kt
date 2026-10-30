package com.nkplayer.app

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/*
 * Optional real backdrop blur for the floating liquid-glass navigation bar.
 *
 * - Everything inside a `glassSource` node is recorded into a layer.
 * - The bar (`backdropBlur`) draws a blurred copy of the part of that layer that lies under it, and the
 *   source stops drawing the sharp copy inside the bar's rounded outline, so there is no double image.
 * - Needs Android 12+ (API 31) for RenderEffect blur. On older devices `enabled` is false, nothing here
 *   changes how the app draws, and the app keeps the plain build #70 look and layout.
 * - Only one blur consumer (the bar) is supported per backdrop. The bar must NOT be inside the source.
 */

/** Flip to false to switch the blur off everywhere (the app then looks exactly like build #70). */
const val ENABLE_BACKDROP_BLUR = true

@Stable
class GlassBackdrop internal constructor(
    internal val content: GraphicsLayer,
    val enabled: Boolean
) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    internal var consumerRect by mutableStateOf<Rect?>(null)
    internal var consumerRadius by mutableFloatStateOf(0f)
    internal val revision = mutableIntStateOf(0)
    private var counter = 0

    /** Called by the source after every re-record so the consumer redraws with fresh pixels. */
    internal fun bump() {
        counter += 1
        revision.intValue = counter // write-only: the source must never observe this state.
    }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        GlassBackdrop(layer, ENABLE_BACKDROP_BLUR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }
}

/** Marks everything drawn by this node as the content the glass bar blurs. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    if (!backdrop.enabled) return this
    return this
        .onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
        .drawWithContent {
            val layer = backdrop.content
            layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            val hole = backdrop.consumerRect
            if (hole == null) {
                drawLayer(layer)
            } else {
                // Don't draw the sharp copy where the glass bar sits; the bar shows the blurred copy.
                val local = hole.translate(-backdrop.sourceOrigin)
                val r = backdrop.consumerRadius
                val outline = Path().apply { addRoundRect(RoundRect(local, CornerRadius(r, r))) }
                clipPath(outline, clipOp = ClipOp.Difference) { drawLayer(layer) }
            }
            backdrop.bump()
        }
}

/**
 * Draws a blurred copy of the source content behind this node, clipped to a rounded rectangle.
 * No-op when [backdrop] is null or blur is unavailable.
 */
@Composable
fun Modifier.backdropBlur(
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
    val radiusPx = with(density) { cornerRadius.toPx() }
    return this
        .onGloballyPositioned { coords ->
            origin = coords.positionInRoot()
            backdrop.consumerRect = Rect(origin, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
            backdrop.consumerRadius = radiusPx
        }
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
            val clip = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx, radiusPx)))
            }
            clipPath(clip) { drawLayer(layer) }
        }
}

/** Soft shadow + accent aura drawn only OUTSIDE the pill, so a see-through bar stays clear inside. */
fun Modifier.glassBarShadow(cornerRadius: Dp, accent: Color): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    val pill = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius)))
    }
    clipPath(pill, clipOp = ClipOp.Difference) {
        for (i in 1..7) {
            val spread = i * 3.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.075f),
                topLeft = Offset(-spread * 0.6f, -spread * 0.2f + 8.dp.toPx()),
                size = Size(size.width + spread * 1.2f, size.height + spread * 0.9f),
                cornerRadius = CornerRadius(radius + spread * 0.6f)
            )
        }
        drawRoundRect(
            color = accent.copy(alpha = 0.16f),
            topLeft = Offset(-6.dp.toPx(), -4.dp.toPx()),
            size = Size(size.width + 12.dp.toPx(), size.height + 8.dp.toPx()),
            cornerRadius = CornerRadius(radius + 6.dp.toPx())
        )
    }
}
