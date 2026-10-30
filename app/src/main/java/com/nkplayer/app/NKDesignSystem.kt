package com.nkplayer.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NK PLAYER design tokens and lightweight Compose primitives.
 * Effects are intentionally GPU-light: translucent fills, gradients and borders;
 * no runtime blur, WebGL, shaders or third-party UI dependencies.
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

@Composable
fun NKGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(NKDesignTokens.Radius.large),
    tint: Color = NKDesignTokens.surface,
    stroke: Color = NKDesignTokens.border,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = tint,
        shape = shape,
        border = BorderStroke(1.dp, stroke),
        shadowElevation = elevation
    ) {
        Box(content = content)
    }
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
