package com.lostf1sh.pixelplayerytm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lostf1sh.pixelplayerytm.ui.AppRoot
import com.lostf1sh.pixelplayerytm.ui.theme.PixelPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayerTheme {
                AppRoot()
            }
        }
    }
}
