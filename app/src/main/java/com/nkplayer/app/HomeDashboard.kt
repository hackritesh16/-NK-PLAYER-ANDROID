package com.nkplayer.app

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
// Home dashboard: welcome header, Continue Playing, Your Activity, Quick Settings and the
// activity screens (history, favourites, playlists, recently added). Built only from the existing
// Glass.kt building blocks so it matches the rest of the app.
// ---------------------------------------------------------------------------------------------

enum class ActivityKind { History, Favorites, Playlists, Recent }

private val DashInk = Color(0xFF07101C)
private val DashDialogGlass = Color(0xFF1A1245)
private val DashDanger = Color(0xFFFF8A9A)

private fun StoredItem.isVideoItem(): Boolean = video ?: (mimeType?.startsWith("video/") == true)

private fun dashTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/** Gentle fade + rise when a section first appears. */
@Composable
fun DashFadeIn(delayMs: Int = 0, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(420, delayMillis = delayMs)) +
            slideInVertically(tween(420, delayMillis = delayMs)) { it / 8 },
        exit = ExitTransition.None
    ) { content() }
}

@Composable
private fun DashHeading(title: String, subtitle: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(3.dp).height(28.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.15f))))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

// ---- Welcome header -----------------------------------------------------------------------

@Composable
fun WelcomeHeader(accent: Color, summary: String, onImport: () -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            in 17..21 -> "GOOD EVENING"
            else -> "LATE NIGHT"
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                val centre = Offset(size.width * 0.2f, size.height * 0.45f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                        center = centre,
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = centre
                )
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = accent, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.W400,
                            brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFDCE6FF)))
                        )
                    ) { append("Welcome ") }
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.W800,
                            brush = Brush.horizontalGradient(listOf(lerp(accent, Color.White, 0.4f), accent))
                        )
                    ) { append("Back") }
                },
                fontFamily = DisplayFont,
                fontSize = 24.sp,
                letterSpacing = (-0.2).sp,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.width(64.dp).height(2.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent, Color.Transparent)))
            )
            Spacer(Modifier.height(8.dp))
            Text(summary, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        GlassIconButton(Icons.Default.Add, "Add files", onImport, tint = accent)
    }
}

// ---- Continue Playing ---------------------------------------------------------------------

@Composable
fun ContinuePlayingCard(
    item: StoredItem?,
    accent: Color,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onResume: () -> Unit,
    onBrowseAudio: () -> Unit,
    onBrowseVideo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashHeading("CONTINUE PLAYING", "Pick up right where you left off", accent)
        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            strength = 1.3f,
            onClick = if (item != null) onResume else null
        ) {
            if (item == null) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.PlayCircle, null, tint = accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing played yet", fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Play a song or video and it will wait for you here.",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Browse audio", accent, icon = Icons.Default.MusicNote, onClick = onBrowseAudio)
                        GlassChip("Browse video", accent, icon = Icons.Default.VideoLibrary, onClick = onBrowseVideo)
                    }
                }
            } else {
                val video = item.isVideoItem()
                val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaArt(
                        uri = item.uri,
                        video = video,
                        accent = accent,
                        icon = if (video) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                        modifier = Modifier.size(84.dp),
                        shape = RoundedCornerShape(22.dp),
                        maxDim = 400,
                        iconSize = 34.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (playing) "NOW PLAYING" else "RESUME FROM ${dashTime(positionMs)}",
                            color = accent, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            (if (video) "VIDEO" else "AUDIO") + if (durationMs > 0L) "  •  ${dashTime(durationMs)}" else "",
                            color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassIconButton(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Resume playback",
                        onResume, size = 54.dp, tint = DashInk, filled = accent
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        }
    }
}

// ---- Your Activity ------------------------------------------------------------------------

@Composable
private fun ActivityTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun YourActivitySection(
    historyCount: Int,
    favoritesCount: Int,
    playlistCount: Int,
    recentCount: Int,
    hasMediaAccess: Boolean,
    accent: Color,
    onOpen: (ActivityKind) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashHeading("YOUR ACTIVITY", "Everything you listen to and watch", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityTile(
                Icons.Default.History, "Play History",
                if (historyCount == 0) "Nothing yet" else "$historyCount played",
                accent, Modifier.weight(1f)
            ) { onOpen(ActivityKind.History) }
            ActivityTile(
                Icons.Default.Favorite, "Favorites",
                if (favoritesCount == 0) "Nothing saved" else "$favoritesCount saved",
                accent, Modifier.weight(1f)
            ) { onOpen(ActivityKind.Favorites) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityTile(
                Icons.Default.QueueMusic, "Playlists",
                if (playlistCount == 0) "Create one" else "$playlistCount created",
                accent, Modifier.weight(1f)
            ) { onOpen(ActivityKind.Playlists) }
            ActivityTile(
                Icons.Default.NewReleases, "Recently Added",
                if (!hasMediaAccess) "Allow access" else if (recentCount == 0) "No files yet" else "$recentCount newest",
                accent, Modifier.weight(1f)
            ) { onOpen(ActivityKind.Recent) }
        }
    }
}

// ---- Quick Settings -----------------------------------------------------------------------

@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    GlassCard(modifier, shape = RoundedCornerShape(20.dp), strength = if (active) 1.7f else 1f, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = if (active) 0.30f else 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    color = if (active) accent else Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QuickSettingsSection(
    accent: Color,
    sleepLabel: String?,
    speedLabel: String,
    speedActive: Boolean,
    onEqualizer: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashHeading("QUICK SETTINGS", "Tap to change instantly", accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(Icons.Default.Equalizer, "Equalizer", "Open sound lab", false, accent, Modifier.weight(1f), onEqualizer)
            QuickTile(
                Icons.Default.Bedtime, "Sleep Timer",
                if (sleepLabel != null) "$sleepLabel left" else "Off",
                sleepLabel != null, accent, Modifier.weight(1f), onSleep
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(Icons.Default.Speed, "Playback Speed", speedLabel, speedActive, accent, Modifier.weight(1f), onSpeed)
            QuickTile(Icons.Default.Settings, "Settings", "Themes & player", false, accent, Modifier.weight(1f), onSettings)
        }
    }
}

// ---- Activity screens (History / Favorites / Playlists / Recently Added) ---------------------

@Composable
private fun ActivityEmpty(
    icon: ImageVector,
    title: String,
    text: String,
    accent: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                AccentButton(actionLabel, Icons.Default.Search, accent, Modifier.fillMaxWidth(), onAction)
            }
        }
    }
}

@Composable
private fun ActivityTrackRow(
    item: StoredItem,
    active: Boolean,
    playing: Boolean,
    accent: Color,
    favorite: Boolean,
    showTime: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val video = item.isVideoItem()
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), strength = if (active) 1.7f else 1f, onClick = onPlay) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArt(
                uri = item.uri,
                video = video,
                accent = accent,
                icon = if (video) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                val whenText = if (showTime && item.at > 0L) {
                    "  •  " + DateUtils.getRelativeTimeSpanString(
                        item.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    ).toString().uppercase()
                } else if (active) "  •  " + (if (playing) "PLAYING" else "PAUSED") else ""
                Text(
                    (if (video) "VIDEO" else "AUDIO") + whenText,
                    color = accent, fontSize = 10.sp, letterSpacing = 0.9.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            GlassIconButton(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (favorite) "Remove from favourites" else "Add to favourites",
                onFavorite, size = 38.dp, tint = if (favorite) accent else Color.White
            )
            if (onRemove != null) {
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Default.DeleteOutline, "Remove from playlist", onRemove, size = 38.dp, tint = DashDanger)
            }
        }
    }
}

@Composable
fun ActivityPane(
    kind: ActivityKind,
    accent: Color,
    items: List<StoredItem>,
    playlists: List<NkPlaylist>,
    openPlaylist: NkPlaylist?,
    currentUri: String?,
    playing: Boolean,
    favoriteUris: Set<String>,
    hasMediaAccess: Boolean,
    onBack: () -> Unit,
    onPlay: (StoredItem, List<StoredItem>) -> Unit,
    onToggleFavorite: (StoredItem) -> Unit,
    onClearHistory: () -> Unit,
    onOpenPlaylist: (String?) -> Unit,
    onNewPlaylist: () -> Unit,
    onDeletePlaylist: (NkPlaylist) -> Unit,
    onRemoveFromPlaylist: (NkPlaylist, StoredItem) -> Unit,
    onRequestAccess: () -> Unit
) {
    val inPlaylist = kind == ActivityKind.Playlists && openPlaylist != null
    val listing = kind == ActivityKind.Playlists && openPlaylist == null
    val tracks: List<StoredItem> = if (kind == ActivityKind.Playlists) openPlaylist?.tracks.orEmpty() else items
    val icon = when (kind) {
        ActivityKind.History -> Icons.Default.History
        ActivityKind.Favorites -> Icons.Default.Favorite
        ActivityKind.Playlists -> Icons.Default.QueueMusic
        ActivityKind.Recent -> Icons.Default.NewReleases
    }
    val title = when (kind) {
        ActivityKind.History -> "PLAY HISTORY"
        ActivityKind.Favorites -> "FAVORITES"
        ActivityKind.Playlists -> openPlaylist?.name?.uppercase() ?: "PLAYLISTS"
        ActivityKind.Recent -> "RECENTLY ADDED"
    }
    val subtitle = if (listing) {
        if (playlists.isEmpty()) "Create your first playlist" else "${playlists.size} playlist(s)"
    } else "${tracks.size} item(s)"

    Column(Modifier.fillMaxSize()) {
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), strength = 1.2f) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Default.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }
                if (listing) {
                    GlassIconButton(Icons.Default.Add, "New playlist", onNewPlaylist, tint = accent)
                } else if (inPlaylist) {
                    GlassIconButton(
                        Icons.Default.DeleteOutline, "Delete playlist",
                        { openPlaylist?.let(onDeletePlaylist) }, tint = DashDanger
                    )
                } else if (kind == ActivityKind.History && tracks.isNotEmpty()) {
                    GlassIconButton(Icons.Default.DeleteOutline, "Clear history", onClearHistory, tint = DashDanger)
                }
            }
        }
        if (!listing && tracks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            AccentButton("PLAY ALL", Icons.Default.PlayArrow, accent, Modifier.fillMaxWidth()) {
                onPlay(tracks.first(), tracks)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (listing) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                if (playlists.isEmpty()) {
                    item {
                        ActivityEmpty(
                            Icons.Default.QueueMusic, "No playlists yet",
                            "Create a playlist, then add songs or videos from the player with the Playlist button.",
                            accent, "NEW PLAYLIST", onNewPlaylist
                        )
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = { onOpenPlaylist(p.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.QueueMusic, null, tint = accent, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.tracks.size} track(s)", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        } else if (tracks.isEmpty()) {
            when (kind) {
                ActivityKind.History -> ActivityEmpty(
                    Icons.Default.History, "No history yet",
                    "Everything you play shows up here.", accent
                )
                ActivityKind.Favorites -> ActivityEmpty(
                    Icons.Default.FavoriteBorder, "No favorites yet",
                    "Tap the Favorite button in the player to keep songs and videos here.", accent
                )
                ActivityKind.Playlists -> ActivityEmpty(
                    Icons.Default.QueueMusic, "This playlist is empty",
                    "Open a song or video in the player and tap Playlist to add it.", accent
                )
                ActivityKind.Recent -> if (!hasMediaAccess) {
                    ActivityEmpty(
                        Icons.Default.FolderOpen, "Allow access to see new files",
                        "NK PLAYER lists the newest audio and video on your phone. Nothing is uploaded.",
                        accent, "ALLOW & SCAN DEVICE", onRequestAccess
                    )
                } else {
                    ActivityEmpty(
                        Icons.Default.NewReleases, "No recent files",
                        "New songs and videos on your phone will appear here.", accent
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(tracks, key = { it.uri }) { t ->
                    ActivityTrackRow(
                        item = t,
                        active = t.uri == currentUri,
                        playing = playing,
                        accent = accent,
                        favorite = t.uri in favoriteUris,
                        showTime = kind == ActivityKind.History,
                        onPlay = { onPlay(t, tracks) },
                        onFavorite = { onToggleFavorite(t) },
                        onRemove = if (inPlaylist && openPlaylist != null) {
                            { onRemoveFromPlaylist(openPlaylist, t) }
                        } else null
                    )
                }
            }
        }
    }
}

// ---- Playlist dialogs ---------------------------------------------------------------------

@Composable
private fun dashFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    focusedContainerColor = Color.White.copy(alpha = 0.10f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = accent
)

@Composable
fun NewPlaylistDialog(accent: Color, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DashDialogGlass,
        title = { Text("New playlist", color = Color.White, fontFamily = DisplayFont) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                placeholder = { Text("Playlist name", color = Color.White.copy(alpha = 0.55f)) },
                shape = RoundedCornerShape(16.dp),
                colors = dashFieldColors(accent)
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("CREATE", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White.copy(alpha = 0.7f)) }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<NkPlaylist>,
    trackUri: String,
    trackName: String,
    accent: Color,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DashDialogGlass,
        title = { Text("Add to playlist", color = Color.White, fontFamily = DisplayFont) },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                Text(trackName, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                playlists.forEach { p ->
                    val already = p.tracks.any { it.uri == trackUri }
                    TextButton(onClick = { onPick(p.id) }, enabled = !already, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (already) "${p.name}  ✓" else "${p.name}  (${p.tracks.size})",
                            color = if (already) Color.White.copy(alpha = 0.5f) else Color.White,
                            modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("New playlist name", color = Color.White.copy(alpha = 0.55f)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = dashFieldColors(accent)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("CREATE & ADD", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = Color.White.copy(alpha = 0.7f)) }
        }
    )
}
