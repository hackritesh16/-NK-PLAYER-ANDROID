package com.nkplayer.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StudioHeader(accent: Color, themeName: String) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(Color(0xFF1A2940), Color(0xFF211B39), Color(0xFF111827))))
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp), modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYER STUDIO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.height(3.dp))
                Text("PERSONALISE YOUR PLAYER", color = accent, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent, shape = RoundedCornerShape(50), modifier = Modifier.size(7.dp)) {}
                    Spacer(Modifier.width(6.dp))
                    Text("Active: $themeName", color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
internal fun ThemeOptionCard(name: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    val cardTint by animateColorAsState(
        targetValue = if (selected) swatch.copy(alpha = 0.12f) else Panel,
        animationSpec = tween(NKDesignTokens.Motion.standardMillis),
        label = "theme-card-tint"
    )
    val cardStroke by animateColorAsState(
        targetValue = if (selected) swatch.copy(alpha = 0.85f) else Color(0xFF263247),
        animationSpec = tween(NKDesignTokens.Motion.standardMillis),
        label = "theme-card-stroke"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (selected) 8.dp else 2.dp,
        animationSpec = tween(NKDesignTokens.Motion.standardMillis),
        label = "theme-card-elevation"
    )
    NKGlassCard(
        tint = cardTint,
        shape = RoundedCornerShape(NKDesignTokens.Radius.medium),
        stroke = cardStroke,
        elevation = cardElevation,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = swatch, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Waves, null, tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                Text(if (selected) "Currently active" else "Tap to apply this accent", color = Color.Gray, fontSize = 10.sp)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = swatch, modifier = Modifier.size(22.dp))
            else Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}

@Composable
internal fun LibraryTabHeader(
    title: String,
    subtitle: String,
    count: Int,
    video: Boolean,
    accent: Color,
    onImport: () -> Unit
) {
    NKGlassCard(
        tint = Panel,
        shape = RoundedCornerShape(NKDesignTokens.Radius.large),
        elevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF17283B), Color(0xFF17172B))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(15.dp), modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (video) Icons.Default.VideoLibrary else Icons.Default.Headphones,
                        contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color.LightGray, fontSize = 10.sp, maxLines = 2)
                Spacer(Modifier.height(7.dp))
                Text("$count FILE(S)", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Default.AddCircle, contentDescription = "Import files", tint = accent, modifier = Modifier.size(29.dp))
            }
        }
    }
}

@Composable
internal fun HomeHero(accent: Color, onImport: () -> Unit) {
    NKGlassCard(
        shape = RoundedCornerShape(NKDesignTokens.Radius.hero),
        tint = Color(0xB8172336),
        stroke = accent.copy(alpha = 0.30f),
        elevation = 20.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        NKAuroraBackdrop(
            colors = listOf(Color(0xFF163A51), Color(0xFF292044), Color(0xFF111827))
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(27.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("YOUR MEDIA. YOUR RULES.", color = accent, fontSize = 10.sp,
                            letterSpacing = 1.25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("One home for every track.", color = Color(0xFFD3DCE8), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(50)) {
                        Text("MEDIA HUB", color = Color(0xFFD7E2F0), fontSize = 8.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("Press play.\nFeel the difference.", color = Color.White, fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(8.dp))
                Text("Your audio and videos, organised in one clean player.",
                    color = Color(0xFFC1CAD8), fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(19.dp))
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("IMPORT FROM DEVICE", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.65.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun StatCard(label: String, value: String, modifier: Modifier, accent: Color) {
    NKGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(NKDesignTokens.Radius.medium),
        tint = NKDesignTokens.surfaceMuted,
        elevation = 8.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(label, color = Color(0xFFA9B6C9), fontSize = 9.sp,
                letterSpacing = 0.75.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun SearchBox(value: String, onValue: (String) -> Unit, accent: Color) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search imported media…", color = Color(0xFF8290A5), fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
        shape = RoundedCornerShape(NKDesignTokens.Radius.medium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = NKDesignTokens.borderStrong,
            focusedContainerColor = NKDesignTokens.surfaceRaised,
            unfocusedContainerColor = NKDesignTokens.surfaceMuted,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accent
        )
    )
}

@Composable
internal fun MediaList(
    items: List<Track>,
    selected: Track?,
    accent: Color,
    modifier: Modifier = Modifier,
    onRemove: (Track) -> Unit,
    onPick: (Track) -> Unit
) {
    if (items.isEmpty()) {
        Column(
            modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("No media here yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Import files to build your library.", color = Color(0xFF9AA8BC), fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.uri }) { track ->
                val active = track == selected
                val rowTint by animateColorAsState(
                    targetValue = if (active) Color(0xD91B3546) else Color(0xA8172131),
                    animationSpec = tween(NKDesignTokens.Motion.standardMillis),
                    label = "media-row-tint"
                )
                val rowStroke by animateColorAsState(
                    targetValue = if (active) accent.copy(alpha = 0.58f) else GlassStroke,
                    animationSpec = tween(NKDesignTokens.Motion.standardMillis),
                    label = "media-row-stroke"
                )
                val rowElevation by animateDpAsState(
                    targetValue = if (active) 8.dp else 2.dp,
                    animationSpec = tween(NKDesignTokens.Motion.standardMillis),
                    label = "media-row-elevation"
                )
                NKGlassCard(
                    tint = rowTint,
                    shape = RoundedCornerShape(NKDesignTokens.Radius.medium),
                    stroke = rowStroke,
                    elevation = rowElevation,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(track) }
                ) {
                    Row(
                        Modifier.padding(start = 11.dp, end = 5.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = accent.copy(alpha = if (active) 0.20f else 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(43.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isVideo(track)) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                                    null, tint = accent, modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.name, color = Color.White, maxLines = 1,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (active) "NOW SELECTED" else if (isVideo(track)) "VIDEO FILE" else "AUDIO FILE",
                                color = if (active) accent else Color(0xFF8795A9),
                                fontSize = 9.sp, letterSpacing = 0.75.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play ${track.name}",
                            tint = if (active) accent else Color(0xFFAEBACB),
                            modifier = Modifier.size(22.dp)
                        )
                        IconButton(onClick = { onRemove(track) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove ${track.name} from library",
                                tint = Color(0xFFFF7A8A)
                            )
                        }
                    }
                }
            }
        }
    }
}
