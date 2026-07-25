package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.ChunkDownloadedDetails
import com.novage.p2pml.api.events.ChunkUploadedDetails
import com.novage.p2pml.api.events.DownloadSource
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.internal.utils.CoreLogger
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
    onPageReady: () -> Unit,
    onCoreInitResult: (errorMessage: String?) -> Unit
) : NSObject(),
    WKScriptMessageHandlerProtocol {

    private val logger = CoreLogger("IosWebViewEventDispatcher")
    private val router = WebViewMessageRouter(events, onPageReady = onPageReady, onCoreInitResult = onCoreInitResult)

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

    private fun logDrop(event: String, reason: String): Nothing? {
        logger.w { "Dropping $event: $reason" }
        return null
    }

    private fun NSDictionary.chunkFields(event: String): ChunkFields? {
        val bytesLength = (objectForKey("bytesLength") as? Number)?.toInt()
            ?: return logDrop(event, "missing or invalid 'bytesLength'")
        val streamType = objectForKey("streamType") as? String
            ?: return logDrop(event, "missing or invalid 'streamType'")
        val infoHash = objectForKey("infoHash") as? String
            ?: return logDrop(event, "missing or invalid 'infoHash'")
        return ChunkFields(bytesLength, streamType, infoHash)
    }

    private fun handleChunkDownloaded(body: Any?) {
        val details = buildChunkDownloaded(body) ?: return
        events.emitChunkDownloaded(details)
    }

    private fun buildChunkDownloaded(body: Any?): ChunkDownloadedDetails? {
        val dict = body as? NSDictionary
            ?: return logDrop("onChunkDownloaded", "message body is not a dictionary")
        val fields = dict.chunkFields("onChunkDownloaded") ?: return null
        val rawSource = dict.objectForKey("downloadSource") as? String
        val source = rawSource?.let { DownloadSource.fromValue(it) }
            ?: return logDrop("onChunkDownloaded", "missing or unknown download source '$rawSource'")
        val peerId = dict.objectForKey("peerId") as? String
        return ChunkDownloadedDetails(fields.bytesLength, source, peerId, fields.streamType, fields.infoHash)
    }

    private fun handleChunkUploaded(body: Any?) {
        val details = buildChunkUploaded(body) ?: return
        events.emitChunkUploaded(details)
    }

    private fun buildChunkUploaded(body: Any?): ChunkUploadedDetails? {
        val dict = body as? NSDictionary
            ?: return logDrop("onChunkUploaded", "message body is not a dictionary")
        val fields = dict.chunkFields("onChunkUploaded") ?: return null
        val peerId = dict.objectForKey("peerId") as? String
            ?: return logDrop("onChunkUploaded", "missing 'peerId'")
        return ChunkUploadedDetails(fields.bytesLength, peerId, fields.streamType, fields.infoHash)
    }

    private fun handleGenericMessage(body: Any?) {
        val messageString = body as? String
        if (messageString == null) {
            logDrop("generic message", "message body is not a string")
            return
        }
        router.handleMessage(messageString)
    }
}
