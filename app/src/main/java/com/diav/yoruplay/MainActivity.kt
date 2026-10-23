package com.diav.yoruplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val YoruColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF9B72CF),
    background = Color(0xFF0B0B10),
    surface = Color(0xFF17151F)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = YoruColors) {
                YoruPlayHome()
            }
        }
    }
}

@Composable
private fun YoruPlayHome() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0B10)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "YoruPlay",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFBB86FC)
            )

            Text(
                text = "Your media. Your night.",
                modifier = Modifier.padding(top = 8.dp),
                color = Color(0xFFB9B5C7),
                fontSize = 14.sp
            )

            Box(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .background(
                        color = Color(0xFF201A2C),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your library is waiting",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            Text(
                text = "Local audio and video player",
                modifier = Modifier.padding(top = 16.dp),
                color = Color(0xFF858091),
                fontSize = 12.sp
            )
        }
    }
}
