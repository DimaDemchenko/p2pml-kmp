package com.novage.p2pml.parser.hlsPlaylistParser

internal fun isLinebreak(c: Int): Boolean = c == '\n'.code || c == '\r'.code

internal fun skipIgnorableWhitespace(reader: Reader, skipLinebreaks: Boolean, c: Int): Int {
    var ch = c
    while (ch != -1 && ch.toChar().isWhitespace() && (skipLinebreaks || !isLinebreak(ch))) {
        ch = reader.read()
    }
    return ch
}

internal fun splitString(value: String, delimiter: String): List<String> = value.split(delimiter)
