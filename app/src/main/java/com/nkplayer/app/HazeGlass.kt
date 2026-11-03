package com.nkplayer.app

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * Set only around UI that floats ABOVE content (bottom bar, mini player, now-playing controls).
 * Surfaces drawn with Modifier.glass() / GlassChip / the liquid nav bar then blur whatever is behind
 * them. It stays null for the rest of the app so cards inside the blurred content never try to blur
 * themselves.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }
