
package com.diav.yoruplay

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Purple = Color(0xFFBB86FC)
private val Background = Color(0xFF0B0B10)
private val Panel = Color(0xFF201A2C)

class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var selectedUri by mutableStateOf<Uri?>(null)
    private var selectedName by mutableStateOf("Belum ada media dipilih")
    private var isVideo by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var duration by mutableLongStateOf(0L)
    private var position by mutableLongStateOf(0L)
    private var controllerReady by mutableStateOf(false)
    private var pendingMedia: Pair<Uri, Boolean>? = null

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openMedia(it, false) }
        }

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openMedia(it, true) }
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            updatePlayerInfo()
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int
        ) {
            updatePlayerInfo()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            position = newPosition.positionMs.coerceAtLeast(0L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                if (isFinishing || isDestroyed || controllerFuture !== future) {
                    return@addListener
                }

                try {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    controllerReady = true
                    updatePlayerInfo()

                    pendingMedia?.let { (uri, video) ->
                        pendingMedia = null
                        playMedia(uri, video)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            ContextCompat.getMainExecutor(this)
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    background = Background,
                    surface = Panel
                )
            ) {
                MediaHome(
                    title = selectedName,
                    uri = selectedUri,
                    video = isVideo,
                    playing = isPlaying,
                    duration = duration,
                    position = position,
                    player = controller,
                    controllerReady = controllerReady,
                    onPickAudio = {
                        audioPicker.launch(arrayOf("audio/*"))
                    },
                    onPickVideo = {
                        videoPicker.launch(arrayOf("video/*"))
                    },
                    onPlayPause = {
                        controller?.let {
                            if (it.isPlaying) it.pause() else it.play()
                        }
                    },
                    onSeek = { value ->
                        controller?.seekTo(value)
                        position = value
                    }
                )
            }
        }
    }

    private fun updatePlayerInfo() {
        controller?.let { player ->
            duration = player.duration.coerceAtLeast(0L)
            position = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
        }
    }

    private fun openMedia(uri: Uri, video: Boolean) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Beberapa penyedia dokumen tidak mendukung izin persisten.
        }

        selectedUri = uri
        selectedName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: if (video) "Video lokal" else "Audio lokal"

        isVideo = video

        if (controllerReady && controller != null) {
            playMedia(uri, video)
        } else {
            pendingMedia = uri to video
        }
    }

    private fun playMedia(uri: Uri, video: Boolean) {
        controller?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        position = 0L
        duration = 0L
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controller = null
        controllerReady = false

        controllerFuture?.let { future ->
            if (future.isDone && !future.isCancelled) {
                try {
                    future.get().release()
                } catch (_: Exception) {
                    future.cancel(false)
                }
            } else {
                future.cancel(false)
            }
        }

        controllerFuture = null
        super.onDestroy()
    }
}

@Composable
private fun MediaHome(
    title: String,
    uri: Uri?,
    video: Boolean,
    playing: Boolean,
    duration: Long,
    position: Long,
    player: Player?,
    controllerReady: Boolean,
    onPickAudio: () -> Unit,
    onPickVideo: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(position, isSeeking) {
        if (!isSeeking) sliderPosition = position.toFloat()
    }

    LaunchedEffect(player) {
        while (isActive) {
            if (player != null && !isSeeking) {
                sliderPosition =
                    player.currentPosition.coerceAtLeast(0L).toFloat()
            }
            delay(500)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "YoruPlay",
                color = Purple,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Your media. Your night.",
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            if (video && uri != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    update = { view ->
                        view.player = player
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Panel, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (uri == null) "♫" else "♫  ♪  ♫",
                        color = Purple,
                        fontSize = 48.sp
                    )
                }
            }

            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickAudio,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pilih audio")
                }

                Button(
                    onClick = onPickVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pilih video")
                }
            }

            if (!video) {
                Slider(
                    value = sliderPosition.coerceIn(
                        0f,
                        duration.coerceAtLeast(1L).toFloat()
                    ),
                    onValueChange = {
                        isSeeking = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        onSeek(sliderPosition.toLong())
                        isSeeking = false
                    },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    enabled = uri != null && duration > 0L,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Button(
                    onClick = onPlayPause,
                    enabled = uri != null && controllerReady,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(if (playing) "Pause Ⅱ" else "Play ▶")
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Local audio & video player",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
