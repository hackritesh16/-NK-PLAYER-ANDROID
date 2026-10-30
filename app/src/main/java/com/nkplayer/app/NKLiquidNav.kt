package com.nkplayer.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class NKNavItem(val label: String, val icon: ImageVector)

/**
 * Floating liquid-glass bottom navigation.
 *
 * - Glass pill with a gradient rim and a soft light sheen.
 * - A liquid blob marks the active tab. Touch the bar and the blob swells and follows the finger;
 *   a slower "tail" lags behind, so the blob stretches, pinches in the middle and snaps back like a
 *   water droplet. On release it settles on the nearest tab with a little wobble.
 * - Tabs light up while the blob passes over them, with a light haptic tick on each tab change.
 */
@Composable
fun NKLiquidNavBar(
    items: List<NKNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val barShape = RoundedCornerShape(36.dp)
    val count = items.size.coerceAtLeast(1)
    val padPx = with(density) { 8.dp.toPx() }

    var widthPx by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    val innerWidth = (widthPx - padPx * 2f).coerceAtLeast(1f)
    val slot = innerWidth / count
    val firstCenter = padPx + slot * 0.5f
    val lastCenter = padPx + slot * (count - 0.5f)
    val restingCenter = padPx + slot * (selectedIndex.coerceIn(0, count - 1) + 0.5f)
    val target = if (pressed) fingerX.coerceIn(firstCenter, lastCenter) else restingCenter

    // head = leading edge of the liquid (quick, bouncy); tail = trailing edge (slow, heavy).
    val head = remember { Animatable(0f) }
    val tail = remember { Animatable(0f) }

    LaunchedEffect(widthPx) {
        if (widthPx > 0f) {
            head.snapTo(target)
            tail.snapTo(target)
            ready = true
        }
    }
    LaunchedEffect(target, ready) {
        if (ready) head.animateTo(target, spring(dampingRatio = 0.58f, stiffness = 520f))
    }
    LaunchedEffect(target, ready) {
        if (ready) tail.animateTo(target, spring(dampingRatio = 0.90f, stiffness = 130f))
    }

    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "nk-nav-press"
    )

    val activeIndex by remember(slot, padPx, count, selectedIndex) {
        derivedStateOf {
            if (!ready) selectedIndex.coerceIn(0, count - 1)
            else ((head.value - padPx) / slot).toInt().coerceIn(0, count - 1)
        }
    }
    LaunchedEffect(activeIndex) {
        if (pressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(
                elevation = 22.dp,
                shape = barShape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.35f),
                spotColor = Color.Black
            )
            .clip(barShape)
            .background(Color(0xCC0B1220))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.06f),
                        accent.copy(alpha = 0.45f)
                    )
                ),
                shape = barShape
            )
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx, count) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    try {
                        fingerX = down.position.x
                        pressed = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                val index = ((change.position.x - padPx) / slot).toInt().coerceIn(0, count - 1)
                                pressed = false
                                currentOnSelect(index)
                                break
                            }
                            fingerX = change.position.x
                            change.consume()
                        }
                    } finally {
                        pressed = false
                    }
                }
            }
    ) {
        // Liquid layer (drawn behind the icons).
        Canvas(Modifier.fillMaxSize()) {
            // Thin specular line along the top edge of the glass.
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)
                ),
                start = Offset(size.height * 0.5f, 1.5.dp.toPx()),
                end = Offset(size.width - size.height * 0.5f, 1.5.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            if (!ready) return@Canvas

            val cy = size.height / 2f
            val bulge = 1f + 0.12f * press
            val r = (size.height - 14.dp.toPx()) * bulge / 2f
            val pillW = (slot - 8.dp.toPx()).coerceAtLeast(r * 2f) * (1f + 0.05f * press)
            val half = (pillW / 2f - r).coerceAtLeast(0f)

            val hx = head.value
            val tx = tail.value
            val stretch = (abs(hx - tx) / (slot * 0.9f)).coerceIn(0f, 1f)
            val headIsRight = hx >= tx
            val rHead = r * (1f + 0.04f * stretch)
            val rTail = r * (1f - 0.22f * stretch)
            val rl = if (headIsRight) rTail else rHead
            val rr = if (headIsRight) rHead else rTail
            val left = min(hx, tx) - half
            val right = max(hx, tx) + half
            val d = right - left
            val xm = (left + right) / 2f
            val neck = min(rl, rr) * (1f - 0.38f * stretch)

            val blob = Path().apply {
                moveTo(left, cy - rl)
                cubicTo(left + d * 0.40f, cy - rl, xm - d * 0.18f, cy - neck, xm, cy - neck)
                cubicTo(xm + d * 0.18f, cy - neck, right - d * 0.40f, cy - rr, right, cy - rr)
                arcTo(Rect(right - rr, cy - rr, right + rr, cy + rr), -90f, 180f, false)
                cubicTo(right - d * 0.40f, cy + rr, xm + d * 0.18f, cy + neck, xm, cy + neck)
                cubicTo(xm - d * 0.18f, cy + neck, left + d * 0.40f, cy + rl, left, cy + rl)
                arcTo(Rect(left - rl, cy - rl, left + rl, cy + rl), 90f, 180f, false)
                close()
            }

            // Soft glow under the liquid.
            val glowRadius = pillW * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.30f + 0.22f * press), Color.Transparent),
                    center = Offset(xm, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(xm, cy)
            )
            // Liquid body.
            drawPath(
                path = blob,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f + 0.10f * press),
                        accent.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.10f)
                    ),
                    startY = cy - r,
                    endY = cy + r
                )
            )
            // Rim light: bright on top, accent tinted at the bottom.
            drawPath(
                path = blob,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.80f),
                        Color.White.copy(alpha = 0.08f),
                        accent.copy(alpha = 0.60f)
                    ),
                    startY = cy - r,
                    endY = cy + r
                ),
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Specular highlight sliver.
            val hlLeft = left - r * 0.45f
            val hlWidth = d + r * 0.9f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                    startX = hlLeft,
                    endX = hlLeft + hlWidth
                ),
                topLeft = Offset(hlLeft, cy - r + 4.dp.toPx()),
                size = Size(hlWidth, 2.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }

        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items.forEachIndexed { index, item ->
                val active = index == activeIndex
                val tint by animateColorAsState(
                    targetValue = if (active) accent else Color(0xFF9AA8BC),
                    animationSpec = tween(180),
                    label = "nk-nav-tint"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1.14f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                    label = "nk-nav-icon-scale"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            selected = index == selectedIndex
                            onClick(label = item.label) {
                                currentOnSelect(index)
                                true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
