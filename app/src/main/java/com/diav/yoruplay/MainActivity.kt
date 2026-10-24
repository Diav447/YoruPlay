package com.diav.yoruplay

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
    private var shuffleEnabled by mutableStateOf(false)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var playlistCount by mutableIntStateOf(0)

    private var pendingMedia: Pair<Uri, Boolean>? = null
    private var pendingPlaylist: List<Uri>? = null

    private val audioPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                openAudioPlaylist(uris)
            }
        }

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
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
            updateCurrentMediaInfo()
            updatePlayerInfo()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            position = newPosition.positionMs.coerceAtLeast(0L)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleEnabled = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(newRepeatMode: Int) {
            repeatMode = newRepeatMode
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

                    shuffleEnabled = mediaController.shuffleModeEnabled
                    repeatMode = mediaController.repeatMode
                    updateCurrentMediaInfo()
                    updatePlayerInfo()

                    pendingPlaylist?.let { uris ->
                        pendingPlaylist = null
                        playAudioPlaylist(uris)
                    } ?: pendingMedia?.let { (uri, video) ->
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
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    playlistCount = playlistCount,
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
                    onPrevious = {
                        controller?.let {
                            if (it.hasPreviousMediaItem()) {
                                it.seekToPreviousMediaItem()
                            } else {
                                it.seekTo(0L)
                            }
                        }
                    },
                    onNext = {
                        controller?.let {
                            if (it.hasNextMediaItem()) {
                                it.seekToNextMediaItem()
                            } else if (it.mediaItemCount > 0) {
                                it.seekTo(0L)
                            }
                        }
                    },
                    onToggleShuffle = {
                        controller?.let {
                            it.shuffleModeEnabled = !it.shuffleModeEnabled
                        }
                    },
                    onCycleRepeat = {
                        controller?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
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

    private fun displayName(uri: Uri): String {
        var name: String? = null

        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
            ?: "Media lokal"
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Sebagian penyedia dokumen tidak mendukung izin persisten.
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun openAudioPlaylist(uris: List<Uri>) {
        if (uris.isEmpty()) return

        uris.forEach { persistReadPermission(it) }

        selectedUri = uris.first()
        selectedName = displayName(uris.first())
        isVideo = false
        playlistCount = uris.size

        if (controllerReady && controller != null) {
            playAudioPlaylist(uris)
        } else {
            pendingPlaylist = uris.toList()
            pendingMedia = null
        }
    }

    private fun playAudioPlaylist(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val mediaItems = uris.map { uri ->
            MediaItem.Builder()
                .setMediaId(uri.toString())
                .setUri(uri)
                .build()
        }

        controller?.apply {
            setMediaItems(mediaItems, 0, 0L)
            prepare()
            playWhenReady = true
        }

        selectedUri = uris.first()
        selectedName = displayName(uris.first())
        isVideo = false
        playlistCount = uris.size
        position = 0L
        duration = 0L
    }

    private fun openMedia(uri: Uri, video: Boolean) {
        persistReadPermission(uri)

        selectedUri = uri
        selectedName = displayName(uri)
        isVideo = video
        playlistCount = 1

        if (controllerReady && controller != null) {
            playMedia(uri, video)
        } else {
            pendingMedia = uri to video
            pendingPlaylist = null
        }
    }

    private fun playMedia(uri: Uri, video: Boolean) {
        controller?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }

        selectedUri = uri
        selectedName = displayName(uri)
        isVideo = video
        playlistCount = 1
        position = 0L
        duration = 0L
    }

    private fun updateCurrentMediaInfo() {
        val player = controller ?: return
        val uri = player.currentMediaItem?.localConfiguration?.uri ?: return

        selectedUri = uri
        selectedName = displayName(uri)
    }

    private fun updatePlayerInfo() {
        controller?.let { player ->
            duration = player.duration.coerceAtLeast(0L)
            position = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            shuffleEnabled = player.shuffleModeEnabled
            repeatMode = player.repeatMode
        }
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
    shuffleEnabled: Boolean,
    repeatMode: Int,
    playlistCount: Int,
    onPickAudio: () -> Unit,
    onPickVideo: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
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

    val repeatLabel = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> "Repeat: ONE"
        Player.REPEAT_MODE_ALL -> "Repeat: ALL"
        else -> "Repeat: OFF"
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
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )

            Text(
                if (playlistCount > 1) {
                    "Playlist • $playlistCount lagu"
                } else {
                    "Local media"
                },
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
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
                    modifier = Modifier.padding(top = 12.dp)
                )

                Text(
                    "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = uri != null && controllerReady,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⏮")
                    }

                    Button(
                        onClick = onPlayPause,
                        enabled = uri != null && controllerReady,
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(if (playing) "Pause Ⅱ" else "Play ▶")
                    }

                    OutlinedButton(
                        onClick = onNext,
                        enabled = uri != null && controllerReady,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⏭")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleShuffle,
                        enabled = uri != null && controllerReady,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (shuffleEnabled) "Shuffle ON" else "Shuffle OFF")
                    }

                    OutlinedButton(
                        onClick = onCycleRepeat,
                        enabled = uri != null && controllerReady,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(repeatLabel)
                    }
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
