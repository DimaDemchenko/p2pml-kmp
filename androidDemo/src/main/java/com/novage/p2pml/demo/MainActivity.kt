package com.novage.p2pml.demo

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novage.p2pml.demo.ui.navigation.Player
import com.novage.p2pml.demo.ui.navigation.VideoList
import com.novage.p2pml.demo.ui.screens.list.VideoListScreen
import com.novage.p2pml.demo.ui.screens.player.PlayerScreen
import com.novage.p2pml.demo.ui.theme.P2PDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm the native Chromium engine during Activity startup (behind the Splash Screen).
        // Initializing a WebView for the first time usually takes 500-1000ms natively.
        // We do this proactively here to prevent the UI thread from freezing and dropping frames
        // during subsequent screen navigation when P2PMediaLoader needs to instantiate its
        // headless WebView for the P2P engine.
        runCatching {
            CookieManager.getInstance()
            WebView(this).apply {
                loadData("", "text/html", null)
            }
        }.onFailure {
            Log.e("MainActivity", "WebView warmup failed", it)
        }

        setContent {
            P2PDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = VideoList) {
                        composable<VideoList> {
                            VideoListScreen(
                                onVideoSelected = { url, customEngineUrl ->
                                    navController.navigate(Player(videoUrl = url, customEngineUrl = customEngineUrl))
                                }
                            )
                        }

                        composable<Player> {
                            PlayerScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
