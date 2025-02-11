package com.novage.p2pml

interface Platform {
  val name: String
}

expect fun getPlatform(): Platform
