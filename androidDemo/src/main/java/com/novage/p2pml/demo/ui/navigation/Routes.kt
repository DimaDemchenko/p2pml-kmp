package com.novage.p2pml.demo.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object VideoList

@Serializable
data class Player(val videoUrl: String)
