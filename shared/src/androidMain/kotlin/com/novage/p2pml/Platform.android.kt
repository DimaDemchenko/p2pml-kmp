package com.novage.p2pml

import android.os.Build
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.server.ServerModule
import kotlin.system.measureTimeMillis

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform {
    val serverModule = ServerModule(onServerStarted = { println("Server started") })
    val hlsParser = HlsPlaylistParser()
    serverModule.start()
    val manifestUrl =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8"
    val manifest = serverModule.fetchManifest(manifestUrl)

    val parsingTime = measureTimeMillis {
        try {
            val parsedManifest = hlsParser.parse(manifestUrl, manifest)
            if (parsedManifest is HlsMultivariantPlaylist) {
                parsedManifest.variants.forEach { println("Variant: $it") }
            } else if (parsedManifest is HlsMediaPlaylist) {
                parsedManifest.segments.forEach { println("Segment: $it") }
            }
        } catch (e: Exception) {
            println("Error: $e")
        }
    }
    println("Manifest parsing took $parsingTime ms")

    return AndroidPlatform()
}
