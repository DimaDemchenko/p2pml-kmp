package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.ChunkDownloadedDetails
import com.novage.p2pml.api.events.ChunkUploadedDetails
import com.novage.p2pml.api.events.DownloadSource
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.internal.utils.CoreLogger
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

    val all = listOf(GENERIC_EVENTS, CHUNK_DOWNLOADED, CHUNK_UPLOADED)
}

internal class IosWebViewEventDispatcher(
    private val events: P2PEvents,
    json: Json = Json { ignoreUnknownKeys = true },
    onPageReady: () -> Unit,
    onCoreInitResult: (errorMessage: String?) -> Unit
) : NSObject(),
    WKScriptMessageHandlerProtocol {

    private val logger = CoreLogger("IosWebViewEventDispatcher")
    private val router = WebViewMessageRouter(events, json, onPageReady, onCoreInitResult)

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

    private class ChunkFields(val bytesLength: Int, val streamType: String, val infoHash: String)

    private fun NSDictionary.chunkFields(): ChunkFields? {
        val bytesLength = (objectForKey("bytesLength") as? Number)?.toInt() ?: return null
        val streamType = objectForKey("streamType") as? String ?: return null
        val infoHash = objectForKey("infoHash") as? String ?: return null
        return ChunkFields(bytesLength, streamType, infoHash)
    }

    private fun handleChunkDownloaded(body: Any?) {
        val dict = body as? NSDictionary ?: return
        val fields = dict.chunkFields() ?: return
        val downloadSource = dict.objectForKey("downloadSource") as? String ?: return

        val source = DownloadSource.fromValue(downloadSource) ?: run {
            logger.w { "Dropping chunk event with unknown download source: $downloadSource" }
            return
        }
        val peerId = dict.objectForKey("peerId") as? String
        events.emitChunkDownloaded(
            ChunkDownloadedDetails(fields.bytesLength, source, peerId, fields.streamType, fields.infoHash)
        )
    }

    private fun handleChunkUploaded(body: Any?) {
        val dict = body as? NSDictionary ?: return
        val fields = dict.chunkFields() ?: return
        val peerId = dict.objectForKey("peerId") as? String ?: return
        events.emitChunkUploaded(
            ChunkUploadedDetails(fields.bytesLength, peerId, fields.streamType, fields.infoHash)
        )
    }

    private fun handleGenericMessage(body: Any?) {
        val messageString = body as? String ?: return
        router.handleMessage(messageString)
    }
}
