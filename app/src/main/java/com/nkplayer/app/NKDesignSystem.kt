package com.nkplayer.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NK PLAYER design tokens and lightweight Compose primitives.
 * Glass effects are intentionally GPU-light: translucent fills, light gradients,
 * gradient borders and soft ambient glows; no runtime blur, shaders or extra libraries.
 */
object NKDesignTokens {
    val canvas = Color(0xFF070A12)
    val surface = Color(0xB8141C2D)
    val surfaceRaised = Color(0xD91B2A40)
    val surfaceMuted = Color(0xA8172131)
    val accentPrimary = Color(0xFF55E8FF)
    val accentSecondary = Color(0xFFAB8AFF)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFCBD5E1)
    val textMuted = Color(0xFF8795A9)
    val border = Color(0x26FFFFFF)
    val borderStrong = Color(0x553F7890)
    val success = Color(0xFF37E6A4)
    val warning = Color(0xFFFFC45C)
    val error = Color(0xFFFF5470)

    object Space {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 20.dp
        val xl = 24.dp
        val xxl = 32.dp
    }

    object Radius {
        val small = 12.dp
        val medium = 18.dp
        val large = 24.dp
        val hero = 30.dp
        val pill = 50.dp
    }

    object Motion {
        const val fastMillis = 150
        const val standardMillis = 220
        const val emphasisMillis = 320
    }
}

/**
 * Frosted-glass card: dark translucent tint, soft light sheen from the top,
 * and a gradient rim (bright on the top-left edge, fading towards the bottom-right).
 */
@Composable
fun NKGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(NKDesignTokens.Radius.large),
    tint: Color = NKDesignTokens.surface,
    stroke: Color = NKDesignTokens.border,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(shape)
            .background(tint)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.025f))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        stroke,
                        stroke.copy(alpha = stroke.alpha * 0.45f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

@Composable
fun NKAuroraBackdrop(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFF0D2335),
        Color(0xFF211B39),
        NKDesignTokens.canvas
    ),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(Brush.linearGradient(colors)),
        content = content
    )
}

/**
 * App-wide background. Glass needs something colourful behind it to look like glass,
 * so a few large soft glows slowly drift behind all screens (draw-only animation, very cheap).
 */
@Composable
fun NKAmbientBackground(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "nk-ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "nk-ambient-drift"
    )
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1220), Color(0xFF0B1022), NKDesignTokens.canvas)
                )
            )
            .drawBehind {
                val d = drift
                val reach = size.maxDimension
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(size.width * (0.10f + 0.16f * d), size.height * (0.08f + 0.06f * d)),
                        radius = reach * 0.55f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(NKDesignTokens.accentSecondary.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * (0.96f - 0.14f * d), size.height * (0.42f + 0.08f * d)),
                        radius = reach * 0.50f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFB347).copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * (0.28f + 0.20f * d), size.height * 0.97f),
                        radius = reach * 0.45f
                    )
                )
            },
        content = content
    )
}

/** Brand mark drawn in code (blue N + gold K) so it never depends on launcher-icon resources. */
@Composable
fun NKLogoMark(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF3AA0FF),
                spotColor = Color(0xFFFFB347)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0A1630), Color(0xFF05070D), Color(0xFF241705)))
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF3AA0FF), Color(0x33FFFFFF), Color(0xFFFFB347))
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(brush = Brush.verticalGradient(listOf(Color(0xFFEAF4FF), Color(0xFF58A8FF))))
                ) { append("N") }
                withStyle(
                    SpanStyle(brush = Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF9F1C))))
                ) { append("K") }
            },
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp
        )
    }
}
