package com.diav.yoruplay

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private const val INVIDIOUS_API =
    "https://yt.chocolatemoo53.com/api/v1/search"

private val OnlinePurple = Color(0xFFBB86FC)
private val OnlinePanel = Color(0xFF201A2C)

private data class OnlineVideoResult(
    val id: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val duration: Int
)

@Composable
internal fun OnlineVideoPage() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OnlineVideoResult>>(emptyList()) }
    var selected by remember { mutableStateOf<OnlineVideoResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchKey) {
        if (searchKey == 0) return@LaunchedEffect
        if (query.isBlank()) {
            error = "Masukkan kata kunci pencarian."
            return@LaunchedEffect
        }

        loading = true
        error = null
        selected = null

        try {
            results = withContext(Dispatchers.IO) {
                val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                val connection = (URL("$INVIDIOUS_API?q=$encoded&type=video")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "YoruPlay/0.1 Android")
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException(
                            "Server pencarian membalas HTTP ${connection.responseCode}."
                        )
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONArray(body)
                    buildList {
                        for (i in 0 until json.length()) {
                            val item = json.optJSONObject(i) ?: continue
                            val id = item.optString("videoId", "")
                            val title = item.optString("title", "")
                            if (id.isBlank() || title.isBlank() || title == "null") continue

                            val thumbnails = item.optJSONArray("videoThumbnails")
                            var thumbnail = ""
                            if (thumbnails != null) {
                                for (j in 0 until thumbnails.length()) {
                                    val image = thumbnails.optJSONObject(j) ?: continue
                                    val candidate = image.optString("url", "")
                                    if (candidate.isNotBlank()) {
                                        thumbnail = candidate
                                        break
                                    }
                                }
                            }

                            add(
                                OnlineVideoResult(
                                    id = id,
                                    title = title,
                                    author = item.optString("author", "Channel tidak diketahui"),
                                    thumbnail = thumbnail,
                                    duration = item.optInt("lengthSeconds", 0)
                                )
                            )
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (results.isEmpty()) error = "Tidak ada hasil yang cocok."
        } catch (e: Exception) {
            error = "Pencarian gagal: ${e.message ?: "server tidak tersedia"}"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Online Search",
            color = OnlinePurple,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Cari video online tanpa meninggalkan YoruPlay.",
            color = Color.LightGray,
            fontSize = 13.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Cari video") }
            )
            Button(
                onClick = { searchKey++ },
                colors = ButtonDefaults.buttonColors(containerColor = OnlinePurple)
            ) {
                Text("Cari", color = Color.Black)
            }
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = OnlinePurple
            )
            Text("Sedang mencari...", color = Color.LightGray)
        }

        error?.let {
            Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
        }

        selected?.let { video ->
            Text(
                text = video.title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Channel: ${video.author}",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            OnlineYouTubePlayer(
                videoId = video.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            TextButton(onClick = { selected = null }) {
                Text("Kembali ke hasil pencarian", color = OnlinePurple)
            }
        } ?: LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(results) { video ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnlinePanel, RoundedCornerShape(12.dp))
                        .clickable { selected = video }
                        .padding(12.dp)
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = video.author +
                            if (video.duration > 0) " • ${formatOnlineDuration(video.duration)}"
                            else " • Live / durasi tidak tersedia",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ketuk untuk memutar",
                        color = OnlinePurple,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Text(
            text = "Hasil berasal dari instance publik; ketersediaan bisa berubah.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OnlineYouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(
                        consoleMessage: android.webkit.ConsoleMessage
                    ): Boolean {
                        android.util.Log.d(
                            "YoruPlayWeb",
                            "JS: ${consoleMessage.message()} " +
                                "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                        )
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        error: android.webkit.WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            android.util.Log.e(
                                "YoruPlayWeb",
                                "Main-frame error: ${error.errorCode} ${error.description}"
                            )
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        response: android.webkit.WebResourceResponse
                    ) {
                        super.onReceivedHttpError(view, request, response)
                        android.util.Log.w(
                            "YoruPlayWeb",
                            "HTTP ${response.statusCode}: ${request.url}"
                        )
                    }
                }
            }
        },
        update = { webView ->
            val marker = "yoruplay-video:$videoId"
            if (webView.tag != marker) {
                webView.tag = marker

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                      html, body {
                        margin: 0; padding: 0; width: 100%; height: 100%;
                        background: #000; overflow: hidden;
                      }
                      iframe {
                        position: absolute; width: 100%; height: 100%;
                        border: 0; left: 0; top: 0;
                      }
                    </style>
                    </head>
                    <body>
                    <iframe
                      src="https://www.youtube.com/embed/$videoId?playsinline=1"
                      title="YoruPlay video player"
                      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                      allowfullscreen>
                    </iframe>
                    </body>
                    </html>
                """.trimIndent()

                webView.loadDataWithBaseURL(
                    "https://www.youtube.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private fun formatOnlineDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remaining)
    } else {
        "%d:%02d".format(minutes, remaining)
    }
}
