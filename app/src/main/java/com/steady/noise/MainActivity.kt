package com.steady.noise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.steady.noise.ui.NoiseScreen
import com.steady.noise.ui.SteadyNoiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SteadyNoiseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoiseScreen()
                }
            }
        }
    }
}
