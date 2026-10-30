package com.nkplayer.app

import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.ui.Modifier

/** Same-package `clickable` so MainActivity's navigation bar compiles without its own import. */
fun Modifier.clickable(onClick: () -> Unit): Modifier = foundationClickable(onClick = onClick)
