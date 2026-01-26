package com.novage.p2pml.demo.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun PlayerLifecycleObserver(viewModel: PlayerViewModel) {
    val appLifecycle = ProcessLifecycleOwner.get()

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE, appLifecycle) {
        viewModel.pause()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, appLifecycle) {
        viewModel.play()
    }
}
