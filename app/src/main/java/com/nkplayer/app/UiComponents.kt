package com.nkplayer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StudioHeader(accent: Color, themeName: String) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp, accent = accent, emphasis = 0.6f) {
        Row(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.20f), NkViolet.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTile(size = 54.dp, cornerRadius = 16.dp, accent = accent) {
                Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYER STUDIO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.height(3.dp))
                Text("PERSONALISE YOUR PLAYER", color = accent, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(accent, RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text("Active: $themeName", color = Color(0xFFD5DEEB), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun ThemeOptionCard(name: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        accent = swatch,
        emphasis = if (selected) 1f else 0f,
        baseAlpha = 0.40f,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val tileShape = RoundedCornerShape(11.dp)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(tileShape)
                    .background(Brush.linearGradient(listOf(swatch, swatch.copy(alpha = 0.55f))))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), tileShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Waves, null, tint = NkInk, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                Text(if (selected) "Currently active" else "Tap to apply this accent", color = NkTextMuted, fontSize = 11.sp)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = swatch, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = NkTextMuted)
            }
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
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp, accent = accent, emphasis = 0.5f) {
        Row(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.16f), NkViolet.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTile(size = 50.dp, cornerRadius = 15.dp, accent = accent) {
                Icon(
                    if (video) Icons.Default.VideoLibrary else Icons.Default.Headphones,
                    contentDescription = null, tint = accent, modifier = Modifier.size(27.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color(0xFFD5DEEB), fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(7.dp))
                Text("$count FILE(S)", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Default.AddCircle, contentDescription = "Import files", tint = accent, modifier = Modifier.size(29.dp))
            }
        }
    }
}

@Composable
internal fun HomeHero(accent: Color, onImport: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 30.dp,
        accent = accent,
        emphasis = 0.8f,
        baseAlpha = 0.46f
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.26f), NkViolet.copy(alpha = 0.20f), Color.Transparent)
                )
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassTile(size = 46.dp, cornerRadius = 14.dp, accent = accent) {
                        Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("YOUR MEDIA. YOUR RULES.", color = accent, fontSize = 11.sp,
                            letterSpacing = 1.25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("One home for every track.", color = Color(0xFFD3DCE8), fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    ) {
                        Text("MEDIA HUB", color = Color(0xFFD7E2F0), fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("Press play.\nFeel the difference.", color = Color.White, fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(8.dp))
                Text("Your audio and videos, organised in one clean player.",
                    color = Color(0xFFC9D3E1), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(19.dp))
                Button(
                    onClick = onImport,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = NkInk),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
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
    GlassCard(modifier, cornerRadius = 18.dp, baseAlpha = 0.38f) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(label, color = NkTextMuted, fontSize = 10.sp,
                letterSpacing = 0.75.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
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
        placeholder = { Text("Search imported media…", color = NkTextMuted, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accent
        )
    )
}

/**
 * Scrollable media list. [bottomPadding] lets the last row scroll clear of the floating
 * navigation bar while the list itself extends underneath it (so the bar can blur real content).
 */
@Composable
internal fun MediaList(
    items: List<Track>,
    selected: Track?,
    accent: Color,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onRemove: (Track) -> Unit,
    onPick: (Track) -> Unit
) {
    if (items.isEmpty()) {
        Column(
            modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassTile(size = 60.dp, cornerRadius = 20.dp, accent = accent) {
                Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("No media here yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Import files to build your library.", color = NkTextMuted, fontSize = 13.sp)
        }
    } else {
        LazyColumn(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp + bottomPadding)
        ) {
            items(items, key = { it.uri }) { track ->
                val active = track == selected
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    accent = accent,
                    emphasis = if (active) 1f else 0f,
                    baseAlpha = if (active) 0.52f else 0.38f,
                    onClick = { onPick(track) }
                ) {
                    Row(
                        Modifier.padding(start = 11.dp, end = 5.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassTile(
                            size = 44.dp,
                            cornerRadius = 13.dp,
                            accent = accent,
                            strength = if (active) 1f else 0.55f
                        ) {
                            Icon(
                                if (isVideo(track)) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                                null, tint = accent, modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.name, color = Color.White, maxLines = 1,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (active) "NOW SELECTED" else if (isVideo(track)) "VIDEO FILE" else "AUDIO FILE",
                                color = if (active) accent else NkTextMuted,
                                fontSize = 10.sp, letterSpacing = 0.75.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play ${track.name}",
                            tint = if (active) accent else Color(0xFFC3CEDD),
                            modifier = Modifier.size(22.dp)
                        )
                        IconButton(onClick = { onRemove(track) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remove ${track.name} from library",
                                tint = Color(0xFFFF8A98)
                            )
                        }
                    }
                }
            }
        }
    }
}
