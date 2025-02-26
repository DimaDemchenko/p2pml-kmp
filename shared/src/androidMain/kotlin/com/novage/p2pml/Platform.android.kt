package com.novage.p2pml

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform {
    //    val serverModule = ServerModule(onServerStarted = { println("Server started") })
    //    val hlsParser = HlsPlaylistParser()
    //    serverModule.start()
    //    val manifestUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    //    val manifest = serverModule.fetchManifest(manifestUrl)
    //
    //    Log.d("Manifest", "Manifest: $manifest")
    //
    //    val parsingTime = measureTimeMillis {
    //        try {
    //            val parsedManifest = hlsParser.parse(manifestUrl, manifest)
    //            if (parsedManifest is HlsMultivariantPlaylist) {
    //                parsedManifest.variants.forEach { println("Variant: $it") }
    //            } else if (parsedManifest is HlsMediaPlaylist) {
    //                parsedManifest.segments.forEach { println("Segment: $it") }
    //            }
    //        } catch (e: Exception) {
    //            println("Error: $e")
    //        }
    //    }
    //    println("Manifest parsing took $parsingTime ms")

    return AndroidPlatform()
}
