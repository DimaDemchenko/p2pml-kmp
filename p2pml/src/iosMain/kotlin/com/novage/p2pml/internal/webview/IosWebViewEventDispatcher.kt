package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.api.models.DownloadSource
import kotlinx.serialization.json.Json
import platform.Foundation.NSDictionary
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

internal object IosBridgeChannels {
    const val GENERIC_EVENTS = "p2pml"
    const val CHUNK_DOWNLOADED = "p2pml_onChunkDownloaded"
    const val CHUNK_UPLOADED = "p2pml_onChunkUploaded"
    const val BINARY_TEST = "p2pml_binaryTest"

    val all = listOf(GENERIC_EVENTS, CHUNK_DOWNLOADED, CHUNK_UPLOADED, BINARY_TEST)
}

internal class IosWebViewEventDispatcher(
    private val events: P2PEvents,
    json: Json = Json { ignoreUnknownKeys = true },
    onPageReady: (() -> Unit)? = null
) : NSObject(),
    WKScriptMessageHandlerProtocol {

    private val router = WebViewMessageRouter(events, json, onPageReady)

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        when (didReceiveScriptMessage.name) {
            IosBridgeChannels.CHUNK_DOWNLOADED -> handleChunkDownloaded(didReceiveScriptMessage.body)
            IosBridgeChannels.CHUNK_UPLOADED -> handleChunkUploaded(didReceiveScriptMessage.body)
            IosBridgeChannels.GENERIC_EVENTS -> handleGenericMessage(didReceiveScriptMessage.body)
            IosBridgeChannels.BINARY_TEST -> handleBinaryTest(didReceiveScriptMessage.body)
        }
    }

    private fun handleChunkDownloaded(body: Any?) {
        val dict = body as? NSDictionary ?: return
        val bytesLength = (dict.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val downloadSource = dict.objectForKey("downloadSource") as? String ?: return
        val peerId = dict.objectForKey("peerId") as? String
        val source = DownloadSource.fromValue(downloadSource)
        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, source, peerId))
    }

    private fun handleChunkUploaded(body: Any?) {
        val dict = body as? NSDictionary ?: return
        val bytesLength = (dict.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val peerId = dict.objectForKey("peerId") as? String ?: return
        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }

    private fun handleGenericMessage(body: Any?) {
        val messageString = body as? String ?: return
        router.handleMessage(messageString)
    }

    private fun handleBinaryTest(body: Any?) {
        println("[BINARY-TEST] body is null: ${body == null}")
        println("[BINARY-TEST] body type: ${body?.let { it::class.simpleName }}")
        println("[BINARY-TEST] body is NSData: ${body is platform.Foundation.NSData}")
        println("[BINARY-TEST] body is NSArray: ${body is platform.Foundation.NSArray}")
        println("[BINARY-TEST] body is NSString: ${body is String}")
        println("[BINARY-TEST] body is NSDictionary: ${body is NSDictionary}")
        println("[BINARY-TEST] body is NSNumber: ${body is Number}")

        if (body is NSDictionary) {
            println("[BINARY-TEST] NSDictionary key count: ${body.count}")
            val keys = body.allKeys
            println("[BINARY-TEST] keys: $keys")
            for (key in keys) {
                val value = body.objectForKey(key)
                println("[BINARY-TEST]   key='$key' valueType=${value?.let { it::class.simpleName }} value=$value")
                if (value is platform.Foundation.NSData) {
                    println("[BINARY-TEST]   -> NSData length: ${value.length}")
                }
                if (value is platform.Foundation.NSArray) {
                    println("[BINARY-TEST]   -> NSArray count: ${value.count}")
                    if (value.count > 0u) {
                        val first = value.objectAtIndex(0u)
                        println("[BINARY-TEST]   -> first element type: ${first?.let { it::class.simpleName }} value: $first")
                    }
                }
            }
        }

        if (body is platform.Foundation.NSData) {
            println("[BINARY-TEST] SUCCESS! NSData length: ${body.length}")
        }
    }
}
