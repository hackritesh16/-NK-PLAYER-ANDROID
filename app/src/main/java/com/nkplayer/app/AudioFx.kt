package com.nkplayer.app

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.MoreExecutors

private val FxPanel = Color(0xFF131A2A)

/** Custom session commands shared by the playback service and the UI. */
object FxCommands {
    const val GET = "nk.fx.get"
    const val ENABLE = "nk.fx.enable"
    const val BAND = "nk.fx.band"
    const val PRESET = "nk.fx.preset"
    const val BASS = "nk.fx.bass"
    const val VIRTUALIZER = "nk.fx.virtualizer"
    const val RESET = "nk.fx.reset"

    val all: List<SessionCommand> = listOf(GET, ENABLE, BAND, PRESET, BASS, VIRTUALIZER, RESET)
        .map { SessionCommand(it, Bundle.EMPTY) }
}

/**
 * Owns the platform audio effects that are attached to the player's audio session.
 * Lives in the playback service so effects keep working when the Activity is closed.
 * Settings are persisted in SharedPreferences and re-applied whenever effects are re-created.
 */
class AudioFxController(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nk_audio_fx", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        applySaved()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun applySaved() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val eq = equalizer
        if (eq != null) runCatching {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val saved = prefs.getString(KEY_LEVELS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
            if (saved.size == bands) {
                saved.forEachIndexed { index, level ->
                    eq.setBandLevel(index.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
            }
        }
        val bass = bassBoost
        if (bass != null && bass.strengthSupported) {
            runCatching { bass.setStrength(prefs.getInt(KEY_BASS, 0).coerceIn(0, 1000).toShort()) }
        }
        val virt = virtualizer
        if (virt != null && virt.strengthSupported) {
            runCatching { virt.setStrength(prefs.getInt(KEY_VIRT, 0).coerceIn(0, 1000).toShort()) }
        }
        setEffectsEnabled(enabled)
    }

    private fun setEffectsEnabled(enabled: Boolean) {
        runCatching { equalizer?.setEnabled(enabled) }
        runCatching { bassBoost?.setEnabled(enabled) }
        runCatching { virtualizer?.setEnabled(enabled) }
    }

    private fun saveLevels() {
        val eq = equalizer ?: return
        runCatching {
            val levels = (0 until eq.numberOfBands.toInt()).joinToString(",") { eq.getBandLevel(it.toShort()).toString() }
            prefs.edit().putString(KEY_LEVELS, levels).apply()
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        setEffectsEnabled(enabled)
    }

    fun setBand(band: Int, level: Int) {
        val eq = equalizer ?: return
        runCatching {
            val range = eq.bandLevelRange
            eq.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
        }
        saveLevels()
    }

    fun usePreset(preset: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(preset.toShort()) }
        saveLevels()
    }

    fun setBass(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        prefs.edit().putInt(KEY_BASS, clamped).apply()
        val bass = bassBoost ?: return
        if (bass.strengthSupported) runCatching { bass.setStrength(clamped.toShort()) }
    }

    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        prefs.edit().putInt(KEY_VIRT, clamped).apply()
        val virt = virtualizer ?: return
        if (virt.strengthSupported) runCatching { virt.setStrength(clamped.toShort()) }
    }

    fun reset() {
        val eq = equalizer
        if (eq != null) runCatching {
            for (band in 0 until eq.numberOfBands.toInt()) eq.setBandLevel(band.toShort(), 0)
        }
        saveLevels()
        setBass(0)
        setVirtualizer(0)
    }

    fun state(): Bundle {
        val result = Bundle()
        val eq = equalizer
        result.putBoolean("available", eq != null)
        result.putBoolean("enabled", prefs.getBoolean(KEY_ENABLED, false))
        if (eq != null) runCatching {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            result.putInt("minLevel", range[0].toInt())
            result.putInt("maxLevel", range[1].toInt())
            result.putIntArray("freqs", IntArray(bands) { eq.getCenterFreq(it.toShort()) / 1000 })
            result.putIntArray("levels", IntArray(bands) { eq.getBandLevel(it.toShort()).toInt() })
            result.putStringArray("presets", Array(eq.numberOfPresets.toInt()) { eq.getPresetName(it.toShort()) })
        }
        result.putBoolean("bassSupported", bassBoost?.strengthSupported == true)
        result.putInt("bass", prefs.getInt(KEY_BASS, 0))
        result.putBoolean("virtSupported", virtualizer?.strengthSupported == true)
        result.putInt("virt", prefs.getInt(KEY_VIRT, 0))
        return result
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LEVELS = "levels"
        const val KEY_BASS = "bass"
        const val KEY_VIRT = "virt"
    }
}

/** Snapshot of the audio effect state as seen from the UI. */
data class FxState(
    val available: Boolean,
    val enabled: Boolean,
    val minLevel: Int,
    val maxLevel: Int,
    val freqsHz: List<Int>,
    val levels: List<Int>,
    val presets: List<String>,
    val bassSupported: Boolean,
    val bass: Int,
    val virtSupported: Boolean,
    val virt: Int
)

private fun fxStateFrom(b: Bundle) = FxState(
    available = b.getBoolean("available"),
    enabled = b.getBoolean("enabled"),
    minLevel = b.getInt("minLevel", -1500),
    maxLevel = b.getInt("maxLevel", 1500),
    freqsHz = b.getIntArray("freqs")?.toList().orEmpty(),
    levels = b.getIntArray("levels")?.toList().orEmpty(),
    presets = b.getStringArray("presets")?.toList().orEmpty(),
    bassSupported = b.getBoolean("bassSupported"),
    bass = b.getInt("bass"),
    virtSupported = b.getBoolean("virtSupported"),
    virt = b.getInt("virt")
)

private fun requestFx(player: Player, onResult: (FxState?) -> Unit) {
    val controller = player as? MediaController
    if (controller == null) {
        onResult(null)
        return
    }
    val future = controller.sendCustomCommand(SessionCommand(FxCommands.GET, Bundle.EMPTY), Bundle.EMPTY)
    future.addListener({
        val state = runCatching { future.get() }.getOrNull()
            ?.takeIf { it.resultCode == SessionResult.RESULT_SUCCESS }
            ?.let { fxStateFrom(it.extras) }
        onResult(state)
    }, MoreExecutors.directExecutor())
}

private fun sendFx(player: Player, action: String, vararg args: Pair<String, Any>) {
    val controller = player as? MediaController ?: return
    val extras = Bundle()
    args.forEach { (key, value) ->
        when (value) {
            is Int -> extras.putInt(key, value)
            is Boolean -> extras.putBoolean(key, value)
        }
    }
    controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), extras)
}

private fun formatHz(hz: Int): String = if (hz >= 1000) {
    val k = hz / 1000f
    if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(k)
} else "$hz Hz"

@Composable
internal fun EqualizerSection(player: Player, accent: Color) {
    var fx by remember(player) { mutableStateOf<FxState?>(null) }
    var loaded by remember(player) { mutableStateOf(false) }
    var activePreset by remember(player) { mutableIntStateOf(-1) }
    val refresh = { requestFx(player) { fx = it; loaded = true } }
    LaunchedEffect(player) { refresh() }

    Surface(
        color = FxPanel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF263247)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Equalizer, null, tint = accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("EQUALIZER", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        when {
                            !loaded -> "Connecting to the audio engine…"
                            fx?.available != true -> "Equalizer is not supported on this device."
                            fx?.enabled == true -> "On. Applies to all audio and video playback."
                            else -> "Off. Turn on to shape the sound."
                        },
                        color = Color.Gray, fontSize = 10.sp, lineHeight = 14.sp
                    )
                }
                val state = fx
                if (state != null && state.available) {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { on ->
                            fx = state.copy(enabled = on)
                            sendFx(player, FxCommands.ENABLE, "enabled" to on)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White)
                    )
                }
            }

            val state = fx
            if (state != null && state.available) {
                if (state.presets.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("PRESETS", color = Color.LightGray, fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.presets.forEachIndexed { index, name ->
                            FilterChip(
                                selected = activePreset == index,
                                onClick = {
                                    activePreset = index
                                    sendFx(player, FxCommands.PRESET, "preset" to index)
                                    if (!state.enabled) sendFx(player, FxCommands.ENABLE, "enabled" to true)
                                    refresh()
                                },
                                label = { Text(name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent,
                                    labelColor = Color.LightGray
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                state.levels.forEachIndexed { band, level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatHz(state.freqsHz.getOrElse(band) { 0 }),
                            color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.width(56.dp)
                        )
                        Slider(
                            value = level.toFloat().coerceIn(state.minLevel.toFloat(), state.maxLevel.toFloat()),
                            onValueChange = { value ->
                                activePreset = -1
                                val newLevel = value.toInt()
                                fx = state.copy(levels = state.levels.toMutableList().also { it[band] = newLevel })
                                sendFx(player, FxCommands.BAND, "band" to band, "level" to newLevel)
                            },
                            valueRange = state.minLevel.toFloat()..state.maxLevel.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xFF344055)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%+.1f dB".format(level / 100f),
                            color = accent, fontSize = 10.sp, modifier = Modifier.width(54.dp)
                        )
                    }
                }

                if (state.bassSupported) {
                    Spacer(Modifier.height(6.dp))
                    StrengthRow("BASS BOOST", state.bass, accent) { value ->
                        fx = state.copy(bass = value)
                        sendFx(player, FxCommands.BASS, "strength" to value)
                    }
                }
                if (state.virtSupported) {
                    StrengthRow("VIRTUALIZER", state.virt, accent) { value ->
                        fx = state.copy(virt = value)
                        sendFx(player, FxCommands.VIRTUALIZER, "strength" to value)
                    }
                }

                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    activePreset = -1
                    sendFx(player, FxCommands.RESET)
                    refresh()
                }) {
                    Text("RESET ALL", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StrengthRow(label: String, value: Int, accent: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.width(92.dp))
        Slider(
            value = value.toFloat().coerceIn(0f, 1000f),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..1000f,
            colors = SliderDefaults.colors(
                thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xFF344055)
            ),
            modifier = Modifier.weight(1f)
        )
        Text("${value / 10}%", color = accent, fontSize = 10.sp, modifier = Modifier.width(38.dp))
    }
}
