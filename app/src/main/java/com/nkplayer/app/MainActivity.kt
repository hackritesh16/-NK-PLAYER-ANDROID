package com.nkplayer.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.app.PictureInPictureParams
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val controllerError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show the Compose shell even if a device rejects MediaSession initialization.
        val startup = runCatching {
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            MediaController.Builder(this, token).buildAsync()
        }
        val future = startup.getOrNull()
        controllerFuture = future
        controllerError.value = startup.exceptionOrNull()?.let { error ->
            android.util.Log.e("NKPlayer", "Unable to initialize media controller", error)
            error.cause?.localizedMessage ?: error.localizedMessage ?: "Unable to initialize media controller"
        }

        setContent {
            var previousCrash by remember {
                mutableStateOf(
                    getSharedPreferences(NKPlayerApplication.PREFS, MODE_PRIVATE)
                        .getString(NKPlayerApplication.KEY_LAST_CRASH, null)
                )
            }
            val player = controllerState.value
            if (player == null) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Ink, surface = Panel)) {
                    Surface(Modifier.fillMaxSize(), color = Ink) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (controllerError.value == null) {
                                    CircularProgressIndicator(color = Cyan)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Connecting NK PLAYER…", color = Color.White)
                                } else {
                                    Text("Player engine could not start", color = Color.White, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(controllerError.value.orEmpty(), color = Color.LightGray)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Please send this message to support: " + controllerError.value.orEmpty(), color = Cyan)
                                }
                            }
                        }
                    }
                }
            } else {
                NkPlayerApp(
                    player = player,
                    onPersistPermission = { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    }
                )
            }

            previousCrash?.let { report ->
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("NK PLAYER crash report") },
                    text = {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                "A previous launch crashed. Copy this report and send it for diagnosis.",
                                color = Color(0xFFB7C5D8)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(report.takeLast(7000), fontSize = 11.sp, color = Color.White)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NK PLAYER crash log", report))
                            getSharedPreferences(NKPlayerApplication.PREFS, MODE_PRIVATE).edit()
                                .remove(NKPlayerApplication.KEY_LAST_CRASH).apply()
                            previousCrash = null
                        }) { Text("COPY LOG") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            getSharedPreferences(NKPlayerApplication.PREFS, MODE_PRIVATE).edit()
                                .remove(NKPlayerApplication.KEY_LAST_CRASH).apply()
                            previousCrash = null
                        }) { Text("CLOSE") }
                    },
                    containerColor = Panel,
                    titleContentColor = Color.White,
                    textContentColor = Color(0xFFD5DEEB)
                )
            }
        }

        future?.addListener({
            runOnUiThread {
                val connected = runCatching { future.get() }
                controllerState.value = connected.getOrNull()
                controllerError.value = connected.exceptionOrNull()?.let { error ->
                    android.util.Log.e("NKPlayer", "Media controller connection failed", error)
                    error.cause?.localizedMessage ?: error.localizedMessage ?: "Unknown media service error"
                }
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = controllerState.value ?: return
        val configuration = player.currentMediaItem?.localConfiguration
        val path = configuration?.uri?.lastPathSegment.orEmpty().lowercase()
        val isVideo = configuration?.mimeType?.startsWith("video/") == true || isVideo(path)
        if (isVideo && player.isPlaying && android.os.Build.VERSION.SDK_INT >= 26 &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onDestroy() {
        controllerState.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}
