package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.api.models.DownloadSource
import kotlinx.serialization.json.Json
import platform.Foundation.NSDictionary
import platform.WebKit.WKContentWorld
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKScriptMessageHandlerWithReplyProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

internal object IosBridgeChannels {
    const val GENERIC_EVENTS = "p2pml"
    const val CHUNK_DOWNLOADED = "p2pml_onChunkDownloaded"
    const val CHUNK_UPLOADED = "p2pml_onChunkUploaded"
    const val BINARY_TEST = "p2pml_binaryTest"

    /** Channels registered with the standard WKScriptMessageHandler */
    val standard = listOf(GENERIC_EVENTS, CHUNK_DOWNLOADED, CHUNK_UPLOADED)

    /** Channels registered with WKScriptMessageHandlerWithReply (iOS 14+) */
    val withReply = listOf(BINARY_TEST)

    val all = standard + withReply
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
}

/**
 * Binary test handler using WKScriptMessageHandlerWithReplyProtocol (iOS 14+).
 * This protocol may handle ArrayBuffer differently than the standard handler.
 */
internal class BinaryTestHandler : NSObject(), WKScriptMessageHandlerWithReplyProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
        replyHandler: (Any?, String?) -> Unit
    ) {
        val body = didReceiveScriptMessage.body

        println("[BINARY-TEST-v2] === WithReply handler received ===")
        println("[BINARY-TEST-v2] body is null: ${body == null}")
        println("[BINARY-TEST-v2] body type: ${body?.let { it::class.simpleName }}")
        println("[BINARY-TEST-v2] body is NSData: ${body is platform.Foundation.NSData}")
        println("[BINARY-TEST-v2] body is NSArray: ${body is platform.Foundation.NSArray}")
        println("[BINARY-TEST-v2] body is NSString: ${body is String}")
        println("[BINARY-TEST-v2] body is NSDictionary: ${body is NSDictionary}")
        println("[BINARY-TEST-v2] body is NSNumber: ${body is Number}")

        if (body is NSDictionary) {
            println("[BINARY-TEST-v2] NSDictionary key count: ${body.count}")
            val map = body as Map<*, *>
            println("[BINARY-TEST-v2] keys: ${map.keys}")
            for ((key, value) in map) {
                println("[BINARY-TEST-v2]   key='$key' valueType=${value?.let { it::class.simpleName }} value=$value")
                if (value is platform.Foundation.NSData) {
                    println("[BINARY-TEST-v2]   -> NSData length: ${value.length}")
                }
            }
        }

        if (body is platform.Foundation.NSData) {
            println("[BINARY-TEST-v2] SUCCESS! NSData length: ${body.length}")
        }

        replyHandler("received", null)
    }
}
