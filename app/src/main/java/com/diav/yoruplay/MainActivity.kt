package com.diav.yoruplay

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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView

private val Purple = Color(0xFFBB86FC)
private val Background = Color(0xFF0B0B10)
private val Panel = Color(0xFF201A2C)

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var selectedUri by mutableStateOf<Uri?>(null)
    private var selectedName by mutableStateOf("Belum ada media dipilih")
    private var isVideo by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var duration by mutableLongStateOf(0L)
    private var position by mutableLongStateOf(0L)

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openMedia(it, false) }
        }

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openMedia(it, true) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MainActivity.isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    this@MainActivity.duration = this@MainActivity.player?.duration?.coerceAtLeast(0L) ?: 0L
                }
            })
        }

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
                    player = player,
                    onPickAudio = {
                        audioPicker.launch(arrayOf("audio/*"))
                    },
                    onPickVideo = {
                        videoPicker.launch(arrayOf("video/*"))
                    },
                    onPlayPause = {
                        player?.let {
                            if (it.isPlaying) it.pause() else it.play()
                        }
                    },
                    onSeek = { value ->
                        player?.seekTo(value)
                    },
                    onPosition = { value ->
                        position = value
                    }
                )
            }
        }
    }

    private fun openMedia(uri: Uri, video: Boolean) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Playback can still work while the current URI grant is valid.
        }

        selectedUri = uri
        selectedName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: if (video) "Video lokal" else "Audio lokal"
        isVideo = video

        player?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
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
    player: ExoPlayer?,
    onPickAudio: () -> Unit,
    onPickVideo: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPosition: (Long) -> Unit
) {
    var sliderPosition by remember(position) {
        mutableFloatStateOf(position.toFloat())
    }

    LaunchedEffect(player, uri) {
        while (true) {
            if (player != null) {
                onPosition(player.currentPosition.coerceAtLeast(0L))
            }
            kotlinx.coroutines.delay(500)
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
                    update = { it.player = player },
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
                    value = sliderPosition.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        onSeek(sliderPosition.toLong())
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
                    enabled = uri != null,
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
