package com.novage.p2pml.demo

import android.os.Bundle
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
