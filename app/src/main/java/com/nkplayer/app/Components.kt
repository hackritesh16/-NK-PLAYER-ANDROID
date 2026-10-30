package com.nkplayer.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val InkButton = Color(0xFF07101C)

@Composable
internal fun AppHeader(accent: Color, onStudio: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).glass(RoundedCornerShape(16.dp)).padding(4.dp)) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "NK PLAYER logo",
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("NK PLAYER", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp, letterSpacing = 1.sp)
            Text("PLAY WITHOUT LIMITS", color = accent, fontSize = 9.sp, letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold)
        }
        GlassIconButton(Icons.Default.GraphicEq, "Open studio", onStudio, tint = accent)
    }
}

@Composable
internal fun GlassNavBar(selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Audio" to Icons.Default.MusicNote,
        "Video" to Icons.Default.VideoLibrary,
        "Studio" to Icons.Default.Tune
    )
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), 1.3f)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val on = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(onClick = { onSelect(index) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label, tint = if (on) accent else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp))
                Text(label, color = if (on) accent else Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun HomeHero(accent: Color, onImport: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), strength = 1.2f) {
        Column(Modifier.padding(22.dp)) {
            GlassChip("YOUR MEDIA. YOUR RULES.", accent, active = true, icon = Icons.Default.GraphicEq)
            Spacer(Modifier.height(18.dp))
            Text("Press play.\nFeel the difference.", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp, lineHeight = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Audio and video in one glass-smooth player. Your phone's music and videos appear here automatically.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(18.dp))
            AccentButton("ADD FILES MANUALLY", Icons.Default.Add, accent, Modifier.fillMaxWidth(), onImport)
        }
    }
}

@Composable
internal fun PermissionCard(accent: Color, denied: Boolean, onAllow: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.3f) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Find my music & videos", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("No manual import needed", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (denied) "Access was denied. Open Settings, allow Music and audio / Photos and videos, then come back."
                else "Allow access to audio and video on this phone and NK PLAYER lists everything automatically. Nothing is uploaded.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))
            AccentButton(
                if (denied) "OPEN SETTINGS" else "ALLOW & SCAN DEVICE",
                Icons.Default.Search, accent, Modifier.fillMaxWidth(), onAllow
            )
        }
    }
}

@Composable
internal fun StatCard(label: String, value: String, modifier: Modifier, accent: Color) {
    GlassCard(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String, accent: Color, onAdd: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.8.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
        }
        GlassIconButton(Icons.Default.Refresh, "Rescan device", onRefresh, tint = accent)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Icons.Default.Add, "Import media", onAdd, tint = accent)
    }
}

@Composable
internal fun LibraryHeader(
    title: String,
    subtitle: String,
    count: Int,
    video: Boolean,
    accent: Color,
    onImport: () -> Unit,
    onRefresh: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.2f) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (video) Icons.Default.VideoLibrary else Icons.Default.Headphones, null,
                    tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text("$count FILE(S)", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            GlassIconButton(Icons.Default.Refresh, "Rescan device", onRefresh, tint = accent)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Default.Add, "Import files", onImport, tint = accent)
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
        placeholder = { Text("Search your media\u2026", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accent
        )
    )
}

@Composable
internal fun EmptyState(accent: Color) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("No media found", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Allow access to scan your phone, or add files manually. Everything stays on this device.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun TrackRow(
    track: Track,
    active: Boolean,
    playing: Boolean,
    accent: Color,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    val video = isVideo(track)
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), strength = if (active) 1.7f else 1f, onClick = onPick) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArt(
                uri = track.uri,
                video = video,
                accent = accent,
                icon = if (video) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (video) "VIDEO" else "AUDIO", color = accent, fontSize = 10.sp, letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold)
                    if (active) {
                        Text("  \u2022  " + (if (playing) "PLAYING" else "PAUSED"), color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
            }
            // Device files are managed by the system scan; only manually added files can be removed here.
            if (!track.device) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${track.name} from library",
                        tint = Color(0xFFFF8A9A))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
internal fun MiniPlayer(
    track: Track,
    snap: PlaybackSnapshot,
    accent: Color,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    val video = isVideo(track)
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strength = 1.4f, onClick = onOpen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArt(
                uri = track.uri,
                video = video,
                accent = accent,
                icon = if (video) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                iconSize = 22.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (snap.isPlaying) "NOW PLAYING" else "PAUSED", color = accent, fontSize = 10.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            }
            GlassIconButton(
                if (snap.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play or pause", onToggle, size = 42.dp, tint = InkButton, filled = accent
            )
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Default.SkipNext, "Next", onNext, size = 42.dp)
        }
        LinearProgressIndicator(
            progress = {
                if (snap.durationMs > 0L) (snap.positionMs.toFloat() / snap.durationMs).coerceIn(0f, 1f) else 0f
            },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = accent,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
    }
}

@Composable
internal fun StudioHeader(accent: Color, themeName: String) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.2f) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tune, null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYER STUDIO", fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.height(3.dp))
                Text("PERSONALISE YOUR PLAYER", color = accent, fontSize = 9.sp, letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Active: $themeName", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun ThemeOptionCard(p: NkPalette, selected: Boolean, onClick: () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        strength = if (selected) 1.7f else 1f,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bgTop, p.accent)))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (selected) "Currently active" else "Tap to apply this look",
                    color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = p.accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
