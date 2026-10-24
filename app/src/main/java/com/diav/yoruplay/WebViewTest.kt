package com.diav.yoruplay

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewTestPage() {
    var status by remember { mutableStateOf("Menunggu tes WebView...") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "YoruPlay • WebView Test",
            style = MaterialTheme.typography.titleLarge
        )

        Text(text = status)

        Button(onClick = {
            status = "Tombol Compose berfungsi!"
        }) {
            Text("Tes tombol aplikasi")
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String
                        ) {
                            status = "HTML berhasil dimuat"
                        }
                    }

                    loadDataWithBaseURL(
                        null,
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <meta name="viewport"
                                content="width=device-width, initial-scale=1">
                          <style>
                            body {
                              background: #090510;
                              color: #c4a1ff;
                              font-family: sans-serif;
                              text-align: center;
                              padding: 24px;
                            }
                            button {
                              background: #6d28d9;
                              color: white;
                              border: 0;
                              padding: 14px 20px;
                              border-radius: 12px;
                              font-size: 16px;
                            }
                          </style>
                        </head>
                        <body>
                          <h2>YoruPlay WebView</h2>
                          <p id="result">HTML berhasil ditampilkan.</p>
                          <button onclick="
                            document.getElementById('result').innerText =
                            'JavaScript berjalan normal!';
                          ">Tes JavaScript</button>
                        </body>
                        </html>
                        """.trimIndent(),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )
    }
}
