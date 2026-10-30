package com.nkplayer.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class LiquidNavItem(val label: String, val icon: ImageVector)

internal val LiquidNavBarHeight = 68.dp
internal val LiquidNavBarMargin = 12.dp

/** Vertical space the floating bar occupies at the bottom (bar + margin + system nav inset). */
@Composable
internal fun liquidNavBarInset(): Dp =
    LiquidNavBarHeight + LiquidNavBarMargin +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp

/**
 * Floating liquid-glass bottom navigation.
 *
 * - Press anywhere on the bar and the glass "droplet" grabs your finger; slide to move it.
 * - The droplet's two edges are separate springs: the leading edge races ahead and the trailing
 *   edge lags, so it stretches (and thins) while moving and jiggles back to shape when it stops.
 * - Releasing selects the tab under the droplet. A plain tap works the same way.
 * - Icons near the droplet are magnified and crossfade to the accent colour (lens effect).
 */
@Composable
internal fun LiquidNavBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier
) {
    val count = items.size
    if (count == 0) return

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val shape = RoundedCornerShape(LiquidNavBarHeight / 2)
    val blurOn = backdrop?.enabled == true

    val leftEdge = remember { Animatable(0f) }
    val rightEdge = remember { Animatable(0f) }
    var ready by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "liquid-lift"
    )

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = LiquidNavBarMargin)
            .navigationBarsPadding()
            // Soft shadow + accent aura, drawn only OUTSIDE the pill so the glass stays clear.
            .drawBehind {
                val radius = size.height / 2f
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
                        color = accent.copy(alpha = 0.10f),
                        topLeft = Offset(-6.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width + 12.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(radius + 6.dp.toPx())
                    )
                }
            }
            // Own render layer: the bar's animation must never re-record the app content.
            .graphicsLayer { }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(LiquidNavBarHeight)
                .clip(shape)
                .backdropBlur(backdrop, LiquidNavBarHeight / 2)
                .background(NkGlassBase.copy(alpha = if (blurOn) 0.30f else 0.80f))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.35f)
                        )
                    ),
                    shape
                )
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val padPx = with(density) { 6.dp.toPx() }
            val itemW = ((widthPx - 2f * padPx) / count).coerceAtLeast(1f)
            val blobW = (itemW - with(density) { 4.dp.toPx() }).coerceAtLeast(1f)
            val blobH = with(density) { (LiquidNavBarHeight - 12.dp).toPx() }

            fun centerOf(index: Int): Float = padPx + itemW * (index + 0.5f)
            fun nearestIndex(x: Float): Int = ((x - padPx) / itemW).toInt().coerceIn(0, count - 1)
            // Slight elastic overshoot past the first / last item instead of a hard stop.
            fun rubber(x: Float): Float {
                val lo = centerOf(0)
                val hi = centerOf(count - 1)
                return when {
                    x < lo -> lo - ((lo - x) * 0.22f).coerceAtMost(itemW * 0.18f)
                    x > hi -> hi + ((x - hi) * 0.22f).coerceAtMost(itemW * 0.18f)
                    else -> x
                }
            }

            // Leading edge: stiff + bouncy. Trailing edge: soft + lagging. That gap IS the liquid.
            suspend fun animateBlobTo(center: Float) = coroutineScope {
                val l = center - blobW / 2f
                val r = center + blobW / 2f
                val movingRight = r >= rightEdge.value
                val lead = spring<Float>(dampingRatio = 0.55f, stiffness = 560f)
                val trail = spring<Float>(dampingRatio = 0.70f, stiffness = 150f)
                launch { leftEdge.animateTo(l, if (movingRight) trail else lead) }
                launch { rightEdge.animateTo(r, if (movingRight) lead else trail) }
            }

            LaunchedEffect(selectedIndex, itemW, blobW) {
                val target = centerOf(selectedIndex)
                if (!ready) {
                    leftEdge.snapTo(target - blobW / 2f)
                    rightEdge.snapTo(target + blobW / 2f)
                    ready = true
                } else if (!pressed) {
                    animateBlobTo(target)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count, itemW, blobW, padPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            var hover = nearestIndex(down.position.x)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { animateBlobTo(rubber(down.position.x)) }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    scope.launch { animateBlobTo(rubber(change.position.x)) }
                                    val index = nearestIndex(change.position.x)
                                    if (index != hover) {
                                        hover = index
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                }
                            } finally {
                                pressed = false
                            }
                            scope.launch { animateBlobTo(centerOf(hover)) }
                            if (hover != currentSelected) currentOnSelect(hover)
                        }
                    }
            ) {
                // The liquid droplet itself, drawn behind the icons.
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.5f
                        )
                    )
                    if (ready) {
                        drawLiquidBlob(
                            l = leftEdge.value,
                            r = rightEdge.value,
                            baseW = blobW,
                            baseH = blobH,
                            lift = lift,
                            accent = accent
                        )
                    }
                }

                Row(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    items.forEachIndexed { index, item ->
                        val proximity: () -> Float = {
                            if (!ready) {
                                if (index == currentSelected) 1f else 0f
                            } else {
                                val c = (leftEdge.value + rightEdge.value) / 2f
                                val t = (1f - abs(c - centerOf(index)) / itemW).coerceIn(0f, 1f)
                                t * t * (3f - 2f * t)
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    selected = index == selectedIndex
                                    onClick(label = item.label) {
                                        currentOnSelect(index)
                                        true
                                    }
                                }
                        ) {
                            NavItemContent(
                                item = item,
                                accent = accent,
                                proximity = proximity,
                                lift = { lift }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItemContent(
    item: LiquidNavItem,
    accent: Color,
    proximity: () -> Float,
    lift: () -> Float
) {
    val muted = Color(0xFFB9C6D8)
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = proximity()
                val s = 1f + 0.14f * p + 0.06f * p * lift()
                scaleX = s
                scaleY = s
                translationY = -2.dp.toPx() * p
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(26.dp)) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - proximity() }
            )
            Icon(
                item.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = proximity() }
            )
        }
        Spacer(Modifier.height(2.dp))
        Box {
            Text(
                item.label,
                color = muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = 1f - proximity() }
            )
            Text(
                item.label,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { alpha = proximity() }
                    .clearAndSetSemantics { }
            )
        }
    }
}

/**
 * Draws one glass droplet between horizontal edges [l] and [r].
 * Width above [baseW] (stretch) thins the droplet; width below it (overshoot) swells it, which
 * keeps its apparent volume roughly constant like a real liquid.
 */
private fun DrawScope.drawLiquidBlob(
    l: Float,
    r: Float,
    baseW: Float,
    baseH: Float,
    lift: Float,
    accent: Color
) {
    val rawW = (r - l).coerceAtLeast(1f)
    val delta = ((rawW - baseW) / baseW).coerceIn(-0.45f, 0.9f)
    val w = rawW * (1f + 0.04f * lift)
    val h = baseH * (1f + 0.10f * lift) * (1f - 0.22f * delta)
    val cx = (l + r) / 2f
    val cy = size.height / 2f
    val left = cx - w / 2f
    val top = cy - h / 2f
    val topLeft = Offset(left, top)
    val blobSize = Size(w, h)
    val radius = CornerRadius(h / 2f, h / 2f)

    // Ambient glow under the droplet.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.34f + 0.18f * lift), Color.Transparent),
            center = Offset(cx, cy),
            radius = w * 0.95f
        ),
        radius = w * 0.95f,
        center = Offset(cx, cy)
    )
    // Glass body + accent tint.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.12f)),
            startY = top,
            endY = top + h
        ),
        topLeft = topLeft,
        size = blobSize,
        cornerRadius = radius
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.14f + 0.10f * lift),
        topLeft = topLeft,
        size = blobSize,
        cornerRadius = radius
    )
    // Lens rim: bright top-left, accent bottom-right.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.90f),
                Color.White.copy(alpha = 0.10f),
                accent.copy(alpha = 0.65f)
            ),
            start = topLeft,
            end = Offset(left + w, top + h)
        ),
        topLeft = topLeft,
        size = blobSize,
        cornerRadius = radius,
        style = Stroke(width = 1.4.dp.toPx())
    )
    // Specular highlight along the top.
    val hlH = h * 0.16f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.65f), Color.Transparent),
            startX = left + w * 0.12f,
            endX = left + w * 0.88f
        ),
        topLeft = Offset(left + w * 0.12f, top + h * 0.09f),
        size = Size(w * 0.76f, hlH),
        cornerRadius = CornerRadius(hlH / 2f, hlH / 2f)
    )
    // Refracted caustic along the bottom.
    val causticH = h * 0.12f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, accent.copy(alpha = 0.55f), Color.Transparent),
            startX = left + w * 0.2f,
            endX = left + w * 0.8f
        ),
        topLeft = Offset(left + w * 0.2f, top + h * 0.80f),
        size = Size(w * 0.6f, causticH),
        cornerRadius = CornerRadius(causticH / 2f, causticH / 2f)
    )
    // Extra halo while the finger is holding the droplet.
    if (lift > 0.02f) {
        val grow = 3.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.28f * lift),
            topLeft = Offset(left - grow, top - grow),
            size = Size(w + grow * 2f, h + grow * 2f),
            cornerRadius = CornerRadius(h / 2f + grow),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
