package com.nkplayer.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Liquid glass: translucent surfaces with a slowly drifting colour pool, a specular sheen that
// sweeps across, a bright rim, and spring + ripple feedback on press. One shared animation phase
// drives every surface and is only read while drawing, so it never recomposes the UI.
// ---------------------------------------------------------------------------------------------

val LocalLiquidPhase = compositionLocalOf<State<Float>?> { null }

@Composable
fun ProvideLiquid(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "liquid")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "liquidPhase"
    )
    CompositionLocalProvider(LocalLiquidPhase provides phase, content = content)
}

fun Modifier.liquidGlass(
    shape: Shape,
    phase: State<Float>?,
    accent: Color = Color.White,
    press: Float = 0f,
    strength: Float = 1f
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = ((0.20f + 0.12f * press) * strength).coerceAtMost(0.6f)),
                Color.White.copy(alpha = ((0.07f + 0.08f * press) * strength).coerceAtMost(0.4f))
            )
        )
    )
    .drawBehind {
        val p = phase?.value ?: 0f
        val w = size.width
        val h = size.height
        val wave = (sin(p * 6.2831855f) + 1f) / 2f
        val pool = Offset(w * (0.15f + 0.7f * wave), h * 0.2f)
        val radius = max(w, h) * 0.7f
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), center = pool, radius = radius),
            radius = radius,
            center = pool
        )
        val sx = (p * 1.8f - 0.4f) * w
        rotate(degrees = -22f, pivot = Offset(w / 2f, h / 2f)) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                    startX = sx - w * 0.18f,
                    endX = sx + w * 0.18f
                ),
                topLeft = Offset(sx - w * 0.18f, -h),
                size = Size(w * 0.36f, h * 3f)
            )
        }
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.30f))
        ),
        shape
    )

@Composable
fun LiquidPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    accent: Color = Color.White,
    strength: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.liquidGlass(shape, LocalLiquidPhase.current, accent, 0f, strength), content = content)
}

@Composable
fun LiquidButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    tint: Color = Color.White,
    accent: Color = Color.White,
    prominent: Boolean = false
) {
    val phase = LocalLiquidPhase.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
        label = "liquidScale"
    )
    val ripple = remember { Animatable(1f) }
    var rippleKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(rippleKey) {
        if (rippleKey > 0) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(520))
        }
    }
    val look = if (prominent) {
        Modifier.clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent, lerp(accent, Color(0xFFB04CFF), 0.5f))))
    } else {
        Modifier.liquidGlass(CircleShape, phase, accent, if (pressed) 1f else 0f)
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(look)
            .drawWithContent {
                drawContent()
                val v = ripple.value
                if (v < 1f) {
                    drawCircle(
                        Color.White.copy(alpha = (1f - v) * 0.38f),
                        radius = this.size.minDimension / 2f * (0.35f + v * 0.95f)
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null) {
                rippleKey++
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun LiquidChip(
    text: String,
    icon: ImageVector?,
    accent: Color,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val phase = LocalLiquidPhase.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 450f),
        label = "chipScale"
    )
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(RoundedCornerShape(50), phase, if (active) accent else Color.White, if (pressed || active) 1f else 0f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (active) accent else Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (active) accent else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
