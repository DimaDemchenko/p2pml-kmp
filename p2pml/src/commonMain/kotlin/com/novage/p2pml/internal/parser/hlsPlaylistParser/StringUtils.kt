package com.novage.p2pml.internal.parser.hlsPlaylistParser

internal fun isLinebreak(c: Int): Boolean = c == '\n'.code || c == '\r'.code

internal fun skipIgnorableWhitespace(reader: Reader, skipLinebreak: Boolean, c: Int): Int {
    var ch = c
    while (ch != -1) {
        val isWs = ch.toChar().isWhitespace()
        val shouldSkip = skipLinebreak || !isLinebreak(ch)

        if (isWs && shouldSkip) {
            ch = reader.read()
        } else {
            break
        }
    }
    return ch
}

internal fun splitString(value: String, delimiter: String): List<String> = value.split(delimiter)
